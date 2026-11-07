package com.voltavpn.app.vpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.voltavpn.app.data.Server
import de.blinkt.openvpn.core.ConfigParser
import de.blinkt.openvpn.core.ConnectionStatus
import de.blinkt.openvpn.core.IOpenVPNServiceInternal
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.ProfileManager
import de.blinkt.openvpn.core.VPNLaunchHelper
import de.blinkt.openvpn.core.VpnStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.StringReader

enum class VpnState { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

data class Traffic(val downBytes: Long = 0, val upBytes: Long = 0)

/** Thin wrapper over the ics-openvpn engine, exposing its status as flows. */
object VpnController {
    private val _state = MutableStateFlow(VpnState.DISCONNECTED)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    private val _traffic = MutableStateFlow(Traffic())
    val traffic: StateFlow<Traffic> = _traffic.asStateFlow()

    private val _connectedSince = MutableStateFlow(0L)
    val connectedSince: StateFlow<Long> = _connectedSince.asStateFlow()

    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        VpnStatus.addStateListener(object : VpnStatus.StateListener {
            override fun updateState(
                state: String?, logmessage: String?, localizedResId: Int,
                level: ConnectionStatus?, intent: Intent?,
            ) {
                val mapped = when (level) {
                    ConnectionStatus.LEVEL_CONNECTED -> VpnState.CONNECTED
                    ConnectionStatus.LEVEL_START,
                    ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED,
                    ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET,
                    ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT -> VpnState.CONNECTING
                    ConnectionStatus.LEVEL_AUTH_FAILED -> VpnState.FAILED
                    else -> VpnState.DISCONNECTED
                }
                if (mapped == VpnState.CONNECTED && _state.value != VpnState.CONNECTED) {
                    _connectedSince.value = System.currentTimeMillis()
                }
                if (mapped != VpnState.CONNECTED) _connectedSince.value = 0
                _state.value = mapped
            }

            override fun setConnectedVPN(uuid: String?) = Unit
        })
        VpnStatus.addByteCountListener { inBytes, outBytes, _, _ ->
            _traffic.value = Traffic(inBytes, outBytes)
        }
    }

    /** Caller must already hold VPN permission (VpnService.prepare returned null). */
    fun connect(context: Context, server: Server) {
        val parser = ConfigParser()
        parser.parseConfig(StringReader(server.config))
        val dns = ProtectionPrefs.dnsMode(context)
        val profile = parser.convertProfile().apply {
            // Shown in the VPN notification: the country only, never the server IP.
            mName = server.displayName()
            // Leak protection: all IPv4 + IPv6 through the tunnel, unused families blocked.
            mUseDefaultRoute = true
            mUseDefaultRoutev6 = true
            mBlockUnusedAddressFamilies = true
            mAllowLocalLAN = false
            // DNS always resolved inside the tunnel, by the resolver the user picked
            // (default: Cloudflare's malware & phishing blocking).
            mOverrideDNS = true
            mDNS1 = dns.primary
            mDNS2 = dns.secondary
            // Keep the tunnel interface up across reconnects, so traffic is never sent
            // outside the VPN while it re-establishes (no leak window).
            mPersistTun = true
            // VPN Gate servers still negotiate AES-128-CBC; prefer AEAD when offered.
            mDataCiphers = "AES-256-GCM:AES-128-GCM:CHACHA20-POLY1305:AES-128-CBC"
            // Give up quickly on dead public servers so we can try the next one.
            mConnectRetryMax = "1"
            mConnectRetry = "2"
        }
        _state.value = VpnState.CONNECTING
        ProfileManager.setTemporaryProfile(context, profile)
        VPNLaunchHelper.startOpenVpn(profile, context.applicationContext, "user", true)
    }

    fun disconnect(context: Context) {
        val app = context.applicationContext
        val intent = Intent(app, OpenVPNService::class.java).setAction(OpenVPNService.START_SERVICE)
        app.bindService(intent, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                runCatching { IOpenVPNServiceInternal.Stub.asInterface(binder).stopVPN(false) }
                app.unbindService(this)
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }, Context.BIND_AUTO_CREATE)
        _state.value = VpnState.DISCONNECTED
    }
}
