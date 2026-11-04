package com.voltavpn.app

import android.Manifest
import android.app.Activity
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voltavpn.app.ads.AdsManager
import com.voltavpn.app.ads.MidnightReceiver
import com.voltavpn.app.pass.DailyPass
import com.voltavpn.app.ui.HomeScreen
import com.voltavpn.app.ui.ServersScreen
import com.voltavpn.app.ui.SettingsScreen
import com.voltavpn.app.ui.VoltaColors
import com.voltavpn.app.ui.VoltaTheme
import com.voltavpn.app.vpn.VpnState

private enum class Screen { HOME, SERVERS, SETTINGS }

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    /** Dialog explaining the one-ad-per-day deal before the ad is shown. */
    private var showAdPrompt by mutableStateOf(false)

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
                val failed by vm.failed.collectAsStateWithLifecycle()

                BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

                LaunchedEffect(failed) {
                    if (failed) {
                        Toast.makeText(this@MainActivity, R.string.all_failed, Toast.LENGTH_LONG).show()
                        vm.clearFailed()
                    }
                }

                // Surface sets the default (light) text colour for everything inside.
                Surface(Modifier.fillMaxSize(), color = VoltaColors.Background) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    when (screen) {
                        Screen.HOME -> HomeScreen(
                            state = state, selected = selected, current = current,
                            traffic = traffic, connectedSince = since,
                            passRemainingMs = vm::passRemainingMs,
                            onToggle = { onToggle(state) },
                            onPickServer = { screen = Screen.SERVERS },
                            onSettings = { screen = Screen.SETTINGS },
                        )
                        Screen.SERVERS -> ServersScreen(
                            servers = servers, selected = selected, loading = loading,
                            onSelect = { vm.select(it); screen = Screen.HOME },
                            onRefresh = { vm.refresh() },
                            onBack = { screen = Screen.HOME },
                        )
                        Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.HOME })
                    }
                }
                }

                if (showAdPrompt) AlertDialog(
                    onDismissRequest = { showAdPrompt = false },
                    title = { Text(stringResource(R.string.ad_prompt_title)) },
                    text = { Text(stringResource(R.string.ad_prompt_body)) },
                    confirmButton = {
                        TextButton(onClick = { showAdPrompt = false; watchAdThenConnect() }) {
                            Text(stringResource(R.string.ad_prompt_watch))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAdPrompt = false }) { Text(stringResource(R.string.cancel)) }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AdsManager.preload(this)
    }

    private fun onToggle(state: VpnState) {
        when {
            state == VpnState.CONNECTED || state == VpnState.CONNECTING -> vm.disconnect()
            vm.passActive() -> requestVpnThenConnect()
            else -> showAdPrompt = true
        }
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
                false -> Toast.makeText(this, R.string.ad_not_finished, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestVpnThenConnect() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent) else vm.connect()
    }
}
