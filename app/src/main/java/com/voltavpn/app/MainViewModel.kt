package com.voltavpn.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voltavpn.app.data.ClientCert
import com.voltavpn.app.data.FailedServers
import com.voltavpn.app.data.Pinger
import com.voltavpn.app.data.SelectionStore
import com.voltavpn.app.data.Server
import com.voltavpn.app.data.ServerProbe
import com.voltavpn.app.data.ServerRepository
import com.voltavpn.app.data.Tier
import com.voltavpn.app.data.bestOfCountry
import com.voltavpn.app.data.serverRanking
import com.voltavpn.app.pass.DailyPass
import com.voltavpn.app.security.Integrity
import com.voltavpn.app.vpn.ExitCheck
import com.voltavpn.app.vpn.ExitCheckState
import com.voltavpn.app.vpn.VpnController
import com.voltavpn.app.vpn.VpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Why the last connect attempt didn't reach the place the user asked for. */
enum class FailureKind { NO_INTERNET, NO_SERVERS, COUNTRY_UNAVAILABLE, ALL_FAILED, SWITCH_FAILED, TAMPERED }

/** [keptOn] is set when a switch failed but the user is still protected by that server. */
data class ConnectFailure(val kind: FailureKind, val target: Server? = null, val keptOn: Server? = null)

/** A connect in flight. [attempt] is 0 until the first server is actually being tried. */
data class ConnectProgress(val target: Server?, val attempt: Int, val total: Int, val switching: Boolean)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServerRepository(app)
    private val failedServers = FailedServers(app)
    private val store = SelectionStore(app)

    private val _servers = MutableStateFlow<List<Server>>(emptyList())
    val servers: StateFlow<List<Server>> = _servers.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** null = automatic "best server". */
    private val _selected = MutableStateFlow<Server?>(null)
    val selected: StateFlow<Server?> = _selected.asStateFlow()

    /** Server actually in use (differs from [selected] when on automatic). */
    private val _current = MutableStateFlow<Server?>(null)
    val current: StateFlow<Server?> = _current.asStateFlow()

    private val _failure = MutableStateFlow<ConnectFailure?>(null)
    val failure: StateFlow<ConnectFailure?> = _failure.asStateFlow()

    /** Non-null for the whole connect job, including the gaps where the tunnel is down. */
    private val _progress = MutableStateFlow<ConnectProgress?>(null)
    val progress: StateFlow<ConnectProgress?> = _progress.asStateFlow()

    /** Countries whose reachable servers all failed recently. */
    private val _cooledCountries = MutableStateFlow<Set<String>>(emptySet())
    val cooledCountries: StateFlow<Set<String>> = _cooledCountries.asStateFlow()

    val vpnState = VpnController.state
    val traffic = VpnController.traffic
    val connectedSince = VpnController.connectedSince

    /** Shown once, after the first successful connection. */
    private val _lockdownTip = MutableStateFlow(false)
    val lockdownTip: StateFlow<Boolean> = _lockdownTip.asStateFlow()

    /** DNS inside the tunnel goes over encrypted DNS-over-TLS (Android's Private DNS confirmed it). */
    private val _dnsEncrypted = MutableStateFlow(false)
    val dnsEncrypted: StateFlow<Boolean> = _dnsEncrypted.asStateFlow()

    private val _exit = MutableStateFlow<ExitCheckState>(ExitCheckState.Checking)
    val exit: StateFlow<ExitCheckState> = _exit.asStateFlow()

    private var connectJob: Job? = null
    private var awaitingSavedSelection = true

    /** The working connection to go back to if a switch fails. */
    private var rollback: Rollback? = null

    /** The selection that produced the current connection. */
    private var connectedSelection: Server? = null

    private data class Rollback(val server: Server, val selection: Server?)

    init {
        refresh(force = false)
        viewModelScope.launch {
            vpnState.collectLatest { state ->
                _exit.value = ExitCheckState.Checking
                _dnsEncrypted.value = false
                if (state == VpnState.DISCONNECTED && _progress.value == null) {
                    _current.value = null
                    // "You're still connected" must not outlive the connection it describes.
                    if (_failure.value?.kind == FailureKind.SWITCH_FAILED) _failure.value = null
                }
                if (state == VpnState.CONNECTED) {
                    restoreCurrent()
                    if (!store.lockdownTipSeen) _lockdownTip.value = true
                    delay(1200) // let routes and DNS settle first
                    repeat(3) {
                        _dnsEncrypted.value = tunnelDnsEncrypted()
                        ExitCheck.fetch()?.let { _exit.value = ExitCheckState.Result(it); return@collectLatest }
                        delay(2000)
                    }
                    _exit.value = ExitCheckState.Unverified
                }
            }
        }
    }

    /** Loads the list and measures every server from this phone (only while the VPN is down). */
    fun refresh(force: Boolean = true) {
        viewModelScope.launch { reload(force) }
    }

    fun refreshCooldowns() {
        _cooledCountries.value = _servers.value.filter { it.pingMs != -1 }
            .groupBy { it.countryCode }
            .filterValues { list -> list.all { failedServers.isRecentlyFailed(it.id) } }
            .keys
    }

    private suspend fun reload(force: Boolean) {
        _loading.value = true
        try {
            // Round trips can't be measured through the tunnel, so keep what we already know,
            // except "unreachable": that may no longer hold on this network.
            val known = _servers.value.associate { it.id to it.pingMs?.takeIf { p -> p != -1 } }
            val list = repo.load(forceRefresh = force).map { it.copy(pingMs = known[it.id]) }
            publish(list)
            if (vpnState.value == VpnState.DISCONNECTED) publish(Pinger.measure(list))
        } finally {
            _loading.value = false
        }
    }

    private fun publish(list: List<Server>) {
        _servers.value = list.sortedWith(serverRanking)
        if (awaitingSavedSelection) {
            // A saved country missing from this list stays saved: the next list may have it.
            val saved = store.country?.let { bestIn(it) }
            if (saved != null || store.country == null) awaitingSavedSelection = false
            _selected.value = saved
        } else {
            // Keep the pick pointing at fresh data (its ping and tier may have changed).
            _selected.value?.let { pick -> _selected.value = bestIn(pick.countryCode) ?: pick }
        }
        restoreCurrent()
        refreshCooldowns()
    }

    private fun bestIn(countryCode: String): Server? =
        _servers.value.filter { it.countryCode == countryCode }.bestOfCountry()

    /** After the UI process restarts mid-session: name the server we're still connected to. */
    private fun restoreCurrent() {
        if (_current.value != null || vpnState.value != VpnState.CONNECTED) return
        _current.value = store.lastServer ?: return
        connectedSelection = _selected.value
    }

    /** Sets the pick without connecting. */
    fun setSelected(server: Server?) {
        awaitingSavedSelection = false
        _selected.value = server
        store.country = server?.countryCode
        _failure.value = null
    }

    /** True when picking [server] changes nothing about the connection we already have. */
    fun isCurrentChoice(server: Server?): Boolean =
        vpnState.value == VpnState.CONNECTED && _progress.value == null &&
            server?.countryCode == _selected.value?.countryCode

    /** True when connecting to [countryCode] (null = automatic) could land on a volunteer server. */
    fun needsVolunteerNotice(countryCode: String?): Boolean {
        if (store.volunteerNoticeSeen) return false
        // Before the list arrives we can't rule it out. Automatic can fall back to any tier.
        if (_servers.value.isEmpty()) return true
        val pool = _servers.value.filter { countryCode == null || it.countryCode == countryCode }
        return pool.any { it.tier.isVolunteer }
    }

    fun dismissLockdownTip() {
        store.lockdownTipSeen = true
        _lockdownTip.value = false
    }

    fun acknowledgeVolunteerNotice() {
        store.volunteerNoticeSeen = true
    }

    fun passActive() = DailyPass.isActive(getApplication())

    fun clearFailure() {
        _failure.value = null
    }

    /**
     * Connects to the current pick. A working connection is never given up blindly: if
     * there's nothing to try it stays up, and if every new server fails it's restored.
     */
    fun connect() {
        connectJob?.cancel()
        _failure.value = null
        if (vpnState.value == VpnState.CONNECTED) {
            rollback = _current.value?.let { Rollback(it, connectedSelection) }
        } else if (_progress.value == null) {
            rollback = null
        }
        val back = rollback
        val pick = _selected.value
        val switching = back != null
        _progress.value = ConnectProgress(pick, attempt = 0, total = 0, switching)

        connectJob = viewModelScope.launch {
            // A modified or hooked copy of the app can't be trusted with the user's traffic.
            if (withContext(Dispatchers.IO) { Integrity.isCompromised(getApplication()) }) {
                rollback = null
                _progress.value = null
                if (vpnState.value != VpnState.DISCONNECTED) VpnController.disconnect(getApplication())
                _failure.value = ConnectFailure(FailureKind.TAMPERED)
                return@launch
            }
            // Only a tunnel that's still up can be kept; mid-switch it's already gone.
            if (back != null && vpnState.value == VpnState.CONNECTED && !hasCandidatesThroughTunnel(pick)) {
                keepConnection(back, pick)
                return@launch
            }
            // Switching away from a live tunnel: no disconnect. Starting the next server replaces
            // the old one while the engine keeps the tunnel open, so traffic is blocked meanwhile,
            // never sent outside it. Anything else half-open is stopped first.
            if (back == null && vpnState.value != VpnState.DISCONNECTED) {
                VpnController.disconnect(getApplication())
                delay(800)
            }
            if (_servers.value.isEmpty() || _servers.value.all { it.pingMs == null }) reload(force = _servers.value.isEmpty())

            val tried = mutableSetOf<String>()
            if (tryCandidates(tried, switching)) return@launch

            if (back != null) {
                // The user was protected a moment ago: get that back before anything else.
                _progress.value = ConnectProgress(back.server, attempt = 0, total = 0, switching = true)
                _current.value = back.server
                if (attempt(back.server)) {
                    keepConnection(back, pick)
                    return@launch
                }
            } else if (isOnline()) {
                // Everything we had failed: the list is probably stale. Fetch a fresh one and retry.
                reload(force = true)
                if (tryCandidates(tried, switching)) return@launch
            }

            giveUp()
        }
    }

    /**
     * Cancelling a switch means "stay where I was", so it goes back to the previous server.
     * Anything else, including a second tap while going back, simply disconnects.
     */
    fun stopConnecting() {
        val back = rollback ?: return disconnect()
        connectJob?.cancel()
        rollback = null
        setSelected(back.selection)
        _progress.value = ConnectProgress(back.server, attempt = 0, total = 0, switching = true)
        _current.value = back.server
        connectJob = viewModelScope.launch {
            // Replaces the attempt in flight; the held tunnel keeps traffic blocked meanwhile.
            if (attempt(back.server)) _progress.value = null else giveUp()
        }
    }

    /** Ends a connect job that got nowhere: nothing is up, and the card says why. */
    private fun giveUp() {
        _current.value = null
        rollback = null
        VpnController.disconnect(getApplication())
        _failure.value = ConnectFailure(failureKind(), _selected.value)
        _progress.value = null
    }

    /**
     * Whether a switch has anything to try. Marks measured before this tunnel came up can be
     * stale, so "unreachable" is re-checked through it: fine for yes/no, though not for timing.
     */
    private suspend fun hasCandidatesThroughTunnel(pick: Server?): Boolean {
        if (candidates(pick?.countryCode).isNotEmpty()) return true
        val code = pick?.countryCode ?: return false
        val rechecked = Pinger.measure(_servers.value.filter { it.countryCode == code })
            .associate { it.id to it.pingMs }
        publish(_servers.value.map { s ->
            if (rechecked[s.id].let { it != null && it != -1 }) s.copy(pingMs = null) else s
        })
        return candidates(code).isNotEmpty()
    }

    private fun keepConnection(back: Rollback, wanted: Server?) {
        setSelected(back.selection)
        _current.value = back.server
        rollback = null
        _failure.value = ConnectFailure(FailureKind.SWITCH_FAILED, wanted, back.server)
        _progress.value = null
    }

    private fun failureKind() = when {
        !isOnline() -> FailureKind.NO_INTERNET
        _servers.value.isEmpty() -> FailureKind.NO_SERVERS
        _selected.value != null -> FailureKind.COUNTRY_UNAVAILABLE
        else -> FailureKind.ALL_FAILED
    }

    /** Servers to try, best first; recently failed ones are the last resort, not excluded. */
    private fun candidates(countryCode: String?): List<Server> {
        val (cooled, fresh) = _servers.value
            .filter { countryCode == null || it.countryCode == countryCode }
            .filter { it.pingMs != -1 }
            // Riseup is a donation-funded nonprofit: "best server" never sends people to it;
            // they get there only by picking one of its countries themselves.
            .filter { countryCode != null || it.tier != Tier.NOLOG }
            // Never reach a volunteer server unannounced, even if the list changed since the check.
            .filter { store.volunteerNoticeSeen || !it.tier.isVolunteer }
            .partition { failedServers.isRecentlyFailed(it.id) }
        return (spread(fresh) + cooled.sortedWith(attemptOrder)).take(MAX_ATTEMPTS)
    }

    /**
     * Trust tier always comes first (speed never makes up for it). Within a tier, a server
     * that often fails on this phone counts as slower than its ping says.
     */
    private val attemptOrder: Comparator<Server> =
        compareBy<Server> { it.tier.ordinal }
            .thenBy { effectivePing(it) }
            .thenByDescending { it.score }

    /** Ping, made "slower" by failures on this phone and by how many people already use it. */
    private fun effectivePing(s: Server): Double =
        (s.pingMs?.takeIf { it >= 0 }?.toDouble() ?: UNKNOWN_PING_MS) / failedServers.reliability(s.id) *
            (1 + (s.sessions ?: 0) / SESSIONS_PER_STEP)

    /**
     * Load spreading: the first attempt goes to a random one of the few servers that are about
     * as good as the best, instead of every phone piling onto the same one.
     */
    private fun spread(list: List<Server>): List<Server> {
        val ranked = list.sortedWith(attemptOrder)
        val first = ranked.firstOrNull() ?: return ranked
        val limit = effectivePing(first) * 1.5 + 30
        val peers = ranked.takeWhile { it.tier == first.tier && effectivePing(it) <= limit }.take(SPREAD_ACROSS)
        return peers.shuffled() + ranked.drop(peers.size)
    }

    private suspend fun tryCandidates(tried: MutableSet<String>, switching: Boolean): Boolean {
        val picked = candidates(_selected.value?.countryCode).filter { it.id !in tried }
        // Check all candidates at once (a few seconds) so dead or impostor addresses go last.
        // Not mid-switch: the held tunnel blocks all traffic until the new server is up.
        val list = if (switching) picked else ServerProbe.order(picked)
        for ((index, server) in list.withIndex()) {
            if (!isOnline()) return false
            tried += server.id
            _current.value = server
            _progress.value = ConnectProgress(server, index + 1, list.size, switching)
            if (attempt(server)) {
                connectedSelection = _selected.value
                store.lastServer = server
                rollback = null
                _progress.value = null
                return true
            }
            // Mid-switch the old tunnel is still held (fail-closed), so don't release it.
            if (!switching) VpnController.disconnect(getApplication())
            delay(500)
        }
        return false
    }

    private suspend fun attempt(server: Server): Boolean {
        val ready = runCatching { ClientCert.attach(getApplication(), server) }.getOrNull()
        // runCatching also swallows cancellation: a stale job must not touch the newer connection.
        currentCoroutineContext().ensureActive()
        val started = ready != null && runCatching { VpnController.connect(getApplication(), ready) }.isSuccess
        val result = if (!started) null else withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            vpnState.first { it == VpnState.CONNECTED || it == VpnState.FAILED }
        }
        if (result == VpnState.CONNECTED) {
            failedServers.clear(server.id)
            failedServers.recordSuccess(server.id)
            refreshCooldowns()
            return true
        }
        // Losing the phone's own network says nothing about the server.
        if (isOnline()) {
            failedServers.mark(server.id)
            refreshCooldowns()
        }
        return false
    }

    /**
     * Android upgrades the tunnel's DNS (1.1.1.2 etc.) to DNS-over-TLS on its own when Private DNS
     * is "Automatic" (its default) and the resolver supports it, which all of ours do. Then even
     * the VPN server's operator can't read or alter lookups. Reported only once Android says so.
     */
    private fun tunnelDnsEncrypted(): Boolean {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        return cm.allNetworks.any { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true &&
                cm.getLinkProperties(n)?.isPrivateDnsActive == true
        }
    }

    private fun isOnline(): Boolean {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /** Stops everything, including a connect or switch in flight. */
    fun disconnect() {
        connectJob?.cancel()
        rollback = null
        _progress.value = null
        _failure.value = null
        _current.value = null
        VpnController.disconnect(getApplication())
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val MAX_ATTEMPTS = 4
        private const val SPREAD_ACROSS = 3
        private const val UNKNOWN_PING_MS = 2000.0
        // Every 100 users already on a server counts like doubling its ping.
        private const val SESSIONS_PER_STEP = 100.0
    }
}
