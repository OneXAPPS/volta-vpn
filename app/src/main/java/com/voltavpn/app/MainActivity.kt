package com.voltavpn.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voltavpn.app.ads.AdsManager
import com.voltavpn.app.ads.MidnightReceiver
import com.voltavpn.app.data.Server
import com.voltavpn.app.pass.DailyPass
import com.voltavpn.app.ui.HomeScreen
import com.voltavpn.app.ui.ServersScreen
import com.voltavpn.app.ui.SettingsScreen
import com.voltavpn.app.ui.VoltaColors
import com.voltavpn.app.ui.VoltaTheme
import com.voltavpn.app.vpn.VpnState

private enum class Screen { HOME, SERVERS, SETTINGS }

/** A country choice waiting on a notice or an ad; [server] null means automatic. */
private data class Pick(val server: Server?)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    /** Dialog explaining the one-ad-per-day deal before the ad is shown. */
    private var showAdPrompt by mutableStateOf(false)

    /** One-time dialog saying what volunteer servers can see, before the first connection to one. */
    private var showVolunteerNotice by mutableStateOf(false)

    /** The pick to apply once the dialogs above are answered; null keeps the current one. */
    private var pendingPick: Pick? = null

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) vm.connect()
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark UI: light status/navigation bar icons.
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        AdsManager.start(this)
        if (savedInstanceState == null) handleConnectAction(intent)

        setContent {
            VoltaTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                val state by vm.vpnState.collectAsStateWithLifecycle()
                val servers by vm.servers.collectAsStateWithLifecycle()
                val selected by vm.selected.collectAsStateWithLifecycle()
                val current by vm.current.collectAsStateWithLifecycle()
                val traffic by vm.traffic.collectAsStateWithLifecycle()
                val since by vm.connectedSince.collectAsStateWithLifecycle()
                val loading by vm.loading.collectAsStateWithLifecycle()
                val failure by vm.failure.collectAsStateWithLifecycle()
                val progress by vm.progress.collectAsStateWithLifecycle()
                val cooled by vm.cooledCountries.collectAsStateWithLifecycle()
                val exit by vm.exit.collectAsStateWithLifecycle()
                val dnsEncrypted by vm.dnsEncrypted.collectAsStateWithLifecycle()
                val lockdownTip by vm.lockdownTip.collectAsStateWithLifecycle()
                val active = progress != null || state == VpnState.CONNECTED || state == VpnState.CONNECTING

                BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

                // Surface sets the default (light) text colour for everything inside.
                Surface(Modifier.fillMaxSize(), color = VoltaColors.Background) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    when (screen) {
                        Screen.HOME -> HomeScreen(
                            state = state, selected = selected, current = current,
                            hasServers = servers.isNotEmpty(), loading = loading,
                            traffic = traffic, exit = exit, dnsEncrypted = dnsEncrypted, connectedSince = since,
                            progress = progress, failure = failure,
                            passActive = vm::passActive,
                            onToggle = {
                                when {
                                    progress != null -> vm.stopConnecting()
                                    active -> vm.disconnect()
                                    else -> startConnect(null)
                                }
                            },
                            onPickServer = { vm.refreshCooldowns(); screen = Screen.SERVERS },
                            onRetryServers = { vm.refresh() },
                            onSettings = { screen = Screen.SETTINGS },
                            onRetryConnect = { startConnect(null) },
                            onUseBestServer = { startConnect(Pick(null)) },
                            onDismissFailure = vm::clearFailure,
                        )
                        Screen.SERVERS -> ServersScreen(
                            servers = servers, selected = selected, loading = loading,
                            cooledCountries = cooled,
                            onSelect = { server ->
                                screen = Screen.HOME
                                when {
                                    vm.isCurrentChoice(server) -> Unit
                                    // Changing country while connected is a new connection.
                                    active -> startConnect(Pick(server))
                                    else -> vm.setSelected(server)
                                }
                            },
                            onRefresh = { vm.refresh() },
                            onBack = { screen = Screen.HOME },
                        )
                        Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.HOME })
                    }
                }
                }

                // Only Android itself can block traffic whenever the VPN is down; point the way once.
                if (lockdownTip && !showVolunteerNotice) AlertDialog(
                    onDismissRequest = vm::dismissLockdownTip,
                    title = { Text(stringResource(R.string.killswitch_title)) },
                    text = { Text(stringResource(R.string.lockdown_tip)) },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.dismissLockdownTip()
                            runCatching { startActivity(Intent(android.provider.Settings.ACTION_VPN_SETTINGS)) }
                        }) { Text(stringResource(R.string.open_settings)) }
                    },
                    dismissButton = {
                        TextButton(onClick = vm::dismissLockdownTip) { Text(stringResource(R.string.close)) }
                    },
                )

                if (showVolunteerNotice) AlertDialog(
                    onDismissRequest = { showVolunteerNotice = false; pendingPick = null },
                    title = { Text(stringResource(R.string.volunteer_title)) },
                    text = { Text(stringResource(R.string.volunteer_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showVolunteerNotice = false
                            vm.acknowledgeVolunteerNotice()
                            connectIfPassAllows()
                        }) { Text(stringResource(R.string.volunteer_continue)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showVolunteerNotice = false; pendingPick = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )

                if (showAdPrompt) AlertDialog(
                    onDismissRequest = { showAdPrompt = false; pendingPick = null },
                    title = { Text(stringResource(R.string.ad_prompt_title)) },
                    text = { Text(stringResource(R.string.ad_prompt_body)) },
                    confirmButton = {
                        TextButton(onClick = { showAdPrompt = false; watchAdThenConnect() }) {
                            Text(stringResource(R.string.ad_prompt_watch))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAdPrompt = false; pendingPick = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AdsManager.preload(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleConnectAction(intent)
    }

    /** The quick-settings tile asks to connect: same path as tapping the big button. */
    private fun handleConnectAction(intent: Intent?) {
        if (intent?.action != ACTION_CONNECT) return
        intent.action = null // a rotation or process restart must not connect again
        val idle = vm.progress.value == null &&
            vm.vpnState.value.let { it == VpnState.DISCONNECTED || it == VpnState.FAILED }
        if (idle) startConnect(null)
    }

    companion object {
        const val ACTION_CONNECT = "com.voltavpn.app.action.CONNECT"
    }

    /** Every user-started connection comes through here: notice first, then the daily ad. */
    private fun startConnect(pick: Pick?) {
        pendingPick = pick
        val country = if (pick != null) pick.server?.countryCode else vm.selected.value?.countryCode
        if (vm.needsVolunteerNotice(country)) showVolunteerNotice = true
        else connectIfPassAllows()
    }

    private fun connectIfPassAllows() {
        if (vm.passActive()) requestVpnThenConnect() else showAdPrompt = true
    }

    private fun watchAdThenConnect() {
        AdsManager.showRewarded(this) { earned ->
            when (earned) {
                true -> {
                    DailyPass.grant(this)
                    MidnightReceiver.schedule(this, DailyPass.validUntil(this))
                    requestVpnThenConnect()
                }
                // No ad available (no fill / offline): don't punish the user, let this connect through.
                null -> requestVpnThenConnect()
                false -> {
                    pendingPick = null
                    Toast.makeText(this, R.string.ad_not_finished, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun requestVpnThenConnect() {
        pendingPick?.let { vm.setSelected(it.server) }
        pendingPick = null

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent) else vm.connect()
    }
}
