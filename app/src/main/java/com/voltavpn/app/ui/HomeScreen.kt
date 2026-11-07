package com.voltavpn.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voltavpn.app.R
import com.voltavpn.app.data.Server
import com.voltavpn.app.vpn.Traffic
import com.voltavpn.app.vpn.VpnState
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    state: VpnState,
    selected: Server?,
    current: Server?,
    hasServers: Boolean,
    loading: Boolean,
    traffic: Traffic,
    connectedSince: Long,
    passRemainingMs: () -> Long,
    onToggle: () -> Unit,
    onPickServer: () -> Unit,
    onRetryServers: () -> Unit,
    onSettings: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis(); delay(1000)
        }
    }
    var buttonCenter by remember { mutableStateOf<Offset?>(null) }
    val connected = state == VpnState.CONNECTED
    // The country we are (or are getting) in: the live server if any, else the user's pick.
    val place = (if (state != VpnState.DISCONNECTED) current else null) ?: selected

    Box(Modifier.fillMaxSize()) {
        val theme = remember(place?.countryCode) { themeFor(place?.countryCode) }
        CountryBackdrop(place?.countryCode, connected && place != null, buttonCenter)
        MotifLayer(theme, connected && place != null, key = place?.countryCode)

        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                VoltaLogo(Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings), tint = VoltaColors.Muted)
                }
            }

            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center) {
                ConnectButton(state, theme.accent.takeIf { place != null }, onToggle, Modifier.onGloballyPositioned {
                    val b = it.boundsInRoot()
                    buttonCenter = Offset((b.left + b.right) / 2, (b.top + b.bottom) / 2)
                })
            }
            Spacer(Modifier.height(20.dp))

            Text(
                text = when (state) {
                    VpnState.CONNECTED -> stringResource(R.string.state_connected)
                    VpnState.CONNECTING -> stringResource(R.string.state_connecting)
                    VpnState.FAILED -> stringResource(R.string.state_failed)
                    VpnState.DISCONNECTED -> stringResource(R.string.state_tap)
                },
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            )
            AnimatedVisibility(
                visible = connected && place != null,
                enter = fadeIn(tween(500, delayMillis = 600)) + slideInVertically(tween(500, delayMillis = 600)) { it / 2 },
                exit = fadeOut(),
            ) {
                Text(
                    stringResource(R.string.now_in, place?.displayName() ?: "", place?.flag ?: ""),
                    color = theme.accent, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (connected && connectedSince > 0) {
                Text(formatDuration(now - connectedSince), color = VoltaColors.Muted, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Stat("↓", formatBytes(traffic.downBytes))
                    Stat("↑", formatBytes(traffic.upBytes))
                }
            }

            Spacer(Modifier.weight(1f))
            if (!hasServers && !loading) NoServersCard(onRetryServers)
            else ServerCard(selected, if (state != VpnState.DISCONNECTED) current else null, onPickServer)
            Spacer(Modifier.height(12.dp))
            PassLine(remember(now) { passRemainingMs() })
        }
    }
}

@Composable
private fun ConnectButton(state: VpnState, accent: Color?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ring by animateColorAsState(
        when (state) {
            VpnState.CONNECTED -> accent ?: VoltaColors.Connected
            VpnState.CONNECTING -> VoltaColors.Bolt
            else -> VoltaColors.SurfaceHigh
        },
        label = "ring",
    )
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f, targetValue = if (state == VpnState.CONNECTING) 1.06f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale",
    )
    Box(
        modifier.size(210.dp).scale(scale)
            .border(BorderStroke(6.dp, ring), CircleShape)
            .padding(14.dp)
            .background(VoltaColors.Surface, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_bolt),
            contentDescription = stringResource(R.string.connect),
            tint = if (state == VpnState.DISCONNECTED || state == VpnState.FAILED) VoltaColors.Muted else ring,
            modifier = Modifier.size(84.dp),
        )
    }
}

/** Shows the user's choice; on automatic, the server actually in use once connecting. */
@Composable
private fun ServerCard(selected: Server?, live: Server?, onClick: () -> Unit) {
    val shown = selected ?: live
    Surface(
        color = VoltaColors.Surface, shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(shown?.flag ?: "⚡", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    selected?.displayName() ?: stringResource(R.string.fastest_server),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when {
                        shown == null -> stringResource(R.string.auto_pick)
                        selected == null -> shown.displayName() + pingSuffix(live?.pingMs)
                        else -> tierLabel(shown) + pingSuffix((live ?: shown).pingMs)
                    },
                    color = VoltaColors.Muted, fontSize = 13.sp,
                )
            }
            Text("›", fontSize = 26.sp, color = VoltaColors.Muted)
        }
    }
}

@Composable
private fun NoServersCard(onRetry: () -> Unit) {
    Surface(
        color = VoltaColors.Surface, shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onRetry),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.no_servers_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.no_servers), color = VoltaColors.Muted, fontSize = 13.sp)
        }
    }
}

private fun pingSuffix(ping: Int?) = if (ping != null && ping >= 0) " · $ping ms" else ""

@Composable
private fun PassLine(remainingMs: Long) {
    Text(
        if (remainingMs > 0) stringResource(R.string.pass_active, formatDuration(remainingMs))
        else stringResource(R.string.pass_needed),
        color = if (remainingMs > 0) VoltaColors.Connected else VoltaColors.Muted,
        fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Stat(arrow: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(arrow, color = VoltaColors.Bolt, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Text(value, color = VoltaColors.Text)
    }
}

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    else -> "%d KB".format(b / 1024)
}
