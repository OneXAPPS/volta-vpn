package com.voltavpn.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voltavpn.app.data.Server
import com.voltavpn.app.data.ServerRepository
import com.voltavpn.app.pass.DailyPass
import com.voltavpn.app.vpn.VpnController
import com.voltavpn.app.vpn.VpnState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServerRepository(app)

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

    val vpnState = VpnController.state
    val traffic = VpnController.traffic
    val connectedSince = VpnController.connectedSince

    /** Set when every candidate server failed; the UI shows a short message. */
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    private var connectJob: Job? = null

    init {
        refresh(force = false)
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            _loading.value = true
            _servers.value = repo.load(forceRefresh = force)
            _loading.value = false
        }
    }

    fun select(server: Server?) {
        _selected.value = server
    }

    fun passActive() = DailyPass.isActive(getApplication())

    fun passRemainingMs() = DailyPass.remainingMs(getApplication())

    /**
     * Connects to the chosen server; if it doesn't come up in time, falls back to
     * the next best server in the same country (or overall, on automatic).
     */
    fun connect() {
        connectJob?.cancel()
        _failed.value = false
        connectJob = viewModelScope.launch {
            if (_servers.value.isEmpty()) {
                _loading.value = true
                _servers.value = repo.load(forceRefresh = true)
                _loading.value = false
            }
            val chosen = _selected.value
            val candidates = _servers.value
                .filter { chosen == null || it.countryCode == chosen.countryCode }
                .let { list -> if (chosen != null) listOf(chosen) + list.filter { it.id != chosen.id } else list }
                .take(MAX_ATTEMPTS)
            for (server in candidates) {
                _current.value = server
                runCatching { VpnController.connect(getApplication(), server) }.onFailure { continue }
                val result = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                    vpnState.first { it == VpnState.CONNECTED || it == VpnState.FAILED }
                }
                if (result == VpnState.CONNECTED) return@launch
                VpnController.disconnect(getApplication())
                delay(500)
            }
            _current.value = null
            VpnController.disconnect(getApplication())
            _failed.value = true
        }
    }

    fun clearFailed() {
        _failed.value = false
    }

    fun disconnect() {
        connectJob?.cancel()
        VpnController.disconnect(getApplication())
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 20_000L
        private const val MAX_ATTEMPTS = 3
    }
}
