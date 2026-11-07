package com.voltavpn.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voltavpn.app.data.FailedServers
import com.voltavpn.app.data.Pinger
import com.voltavpn.app.data.Server
import com.voltavpn.app.data.ServerRepository
import com.voltavpn.app.data.serverRanking
import com.voltavpn.app.pass.DailyPass
import com.voltavpn.app.vpn.ExitCheck
import com.voltavpn.app.vpn.ExitInfo
import com.voltavpn.app.vpn.VpnController
import com.voltavpn.app.vpn.VpnState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Why the last connect attempt ended without a connection. */
enum class ConnectError { NO_SERVERS, COUNTRY_UNAVAILABLE, ALL_FAILED }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServerRepository(app)
    private val failedServers = FailedServers(app)

    private val _servers = MutableStateFlow<List<Server>>(emptyList())
    val servers: StateFlow<List<Server>> = _servers.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** null = automatic "fastest server". */
    private val _selected = MutableStateFlow<Server?>(null)
    val selected: StateFlow<Server?> = _selected.asStateFlow()

    /** Server actually in use (differs from [selected] when on automatic). */
    private val _current = MutableStateFlow<Server?>(null)
    val current: StateFlow<Server?> = _current.asStateFlow()

    private val _error = MutableStateFlow<ConnectError?>(null)
    val error: StateFlow<ConnectError?> = _error.asStateFlow()

    val vpnState = VpnController.state
    val traffic = VpnController.traffic
    val connectedSince = VpnController.connectedSince

    /** What websites see once connected (checked through the tunnel); null while unknown. */
    private val _exit = MutableStateFlow<ExitInfo?>(null)
    val exit: StateFlow<ExitInfo?> = _exit.asStateFlow()

    private var connectJob: Job? = null

    init {
        refresh(force = false)
        viewModelScope.launch {
            vpnState.collectLatest { state ->
                _exit.value = null
                if (state == VpnState.CONNECTED) {
                    delay(1200) // let routes and DNS settle first
                    repeat(3) {
                        ExitCheck.fetch()?.let { _exit.value = it; return@collectLatest }
                        delay(2000)
                    }
                }
            }
        }
    }

    /** Loads the list and measures every server from this phone (only while the VPN is down). */
    fun refresh(force: Boolean = true) {
        viewModelScope.launch { reload(force) }
    }

    private suspend fun reload(force: Boolean) {
        _loading.value = true
        val list = repo.load(forceRefresh = force)
        _servers.value = list.sortedWith(serverRanking)
        if (vpnState.value == VpnState.DISCONNECTED) {
            _servers.value = Pinger.measure(list).sortedWith(serverRanking)
        }
        _loading.value = false
    }

    /** Picking another country while connected switches to it right away. */
    fun select(server: Server?) {
        _selected.value = server
        if (vpnState.value == VpnState.CONNECTED || vpnState.value == VpnState.CONNECTING) connect()
    }

    fun passActive() = DailyPass.isActive(getApplication())

    fun passRemainingMs() = DailyPass.remainingMs(getApplication())

    fun clearError() {
        _error.value = null
    }

    /**
     * Tries the best reachable servers of the chosen country (or of all countries on
     * automatic). If they all fail, the list is re-downloaded and re-measured once, since
     * public servers come and go every hour.
     */
    fun connect() {
        connectJob?.cancel()
        _error.value = null
        connectJob = viewModelScope.launch {
            val wasConnected = vpnState.value != VpnState.DISCONNECTED
            if (wasConnected) {
                VpnController.disconnect(getApplication())
                delay(800)
            }
            if (_servers.value.isEmpty() || _servers.value.all { it.pingMs == null }) reload(force = _servers.value.isEmpty())
            if (tryCandidates()) return@launch

            // Everything we had failed: the list is probably stale. Fetch a fresh one and retry.
            reload(force = true)
            if (tryCandidates()) return@launch

            _current.value = null
            VpnController.disconnect(getApplication())
            _error.value = when {
                _servers.value.isEmpty() -> ConnectError.NO_SERVERS
                _selected.value != null -> ConnectError.COUNTRY_UNAVAILABLE
                else -> ConnectError.ALL_FAILED
            }
        }
    }

    private suspend fun tryCandidates(): Boolean {
        val chosen = _selected.value
        val candidates = _servers.value
            .filter { chosen == null || it.countryCode == chosen.countryCode }
            .filter { it.pingMs != -1 && !failedServers.isRecentlyFailed(it.id) }
            .sortedWith(serverRanking)
            .take(MAX_ATTEMPTS)
        for (server in candidates) {
            _current.value = server
            if (runCatching { VpnController.connect(getApplication(), server) }.isFailure) {
                failedServers.mark(server.id)
                continue
            }
            val result = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                vpnState.first { it == VpnState.CONNECTED || it == VpnState.FAILED }
            }
            if (result == VpnState.CONNECTED) {
                failedServers.clear(server.id)
                return true
            }
            failedServers.mark(server.id)
            VpnController.disconnect(getApplication())
            delay(500)
        }
        return false
    }

    fun disconnect() {
        connectJob?.cancel()
        VpnController.disconnect(getApplication())
        _current.value = null
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val MAX_ATTEMPTS = 4
    }
}
