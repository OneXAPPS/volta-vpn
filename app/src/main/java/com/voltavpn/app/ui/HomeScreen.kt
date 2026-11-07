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
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.luminance
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
import com.voltavpn.app.vpn.ExitInfo
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
    exit: ExitInfo?,
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
                ConnectButton(state, onToggle, Modifier.onGloballyPositioned {
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
                    color = readable(theme.accent), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (connected && connectedSince > 0) {
                Text(formatDuration(now - connectedSince), color = VoltaColors.Muted, fontSize = 15.sp)
                ProtectionLine(exit, place)
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

/** Live proof of protection: the country websites see now (the IP itself is never shown). */
@Composable
private fun ProtectionLine(exit: ExitInfo?, place: Server?) {
    val (text, color) = when {
        exit == null -> stringResource(R.string.exit_checking) to VoltaColors.Muted
        place == null || exit.country == place.countryCode ->
            stringResource(R.string.exit_ok, place?.displayName() ?: exit.country) to VoltaColors.Connected
        else -> stringResource(R.string.exit_mismatch, exit.country) to VoltaColors.Danger
    }
    GlowDivider(Modifier.width(220.dp).height(10.dp).padding(top = 4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        if (exit != null) {
            KitLock(Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text.removePrefix("🛡").trim(), color = color, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
    if (place != null && place.unlocks.isNotEmpty()) {
        Text(
            stringResource(R.string.unlocks_line, place.unlocks.joinToString(", ")),
            color = VoltaColors.Bolt, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
    }
}

/**
 * The power button, built on the kit's state orbs:
 * ready = cyan ring + cyan V; connecting = same with an orbiting electric arc;
 * connected = lime ring + lime V with signal waves pulsing out; failed = grey.
 */
@Composable
private fun ConnectButton(state: VpnState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val on = state == VpnState.CONNECTED
    val connecting = state == VpnState.CONNECTING
    val failed = state == VpnState.FAILED
    val ringTop by animateColorAsState(
        when { on -> Color(0xFFB8FF4A); failed -> Color(0xFFB9BECB); else -> Color(0xFF5BDCFF) }, tween(600), label = "rt",
    )
    val ringBottom by animateColorAsState(
        when { on -> Color(0xFF39C92A); failed -> Color(0xFF7D8396); else -> Color(0xFF2E6BFF) }, tween(600), label = "rb",
    )
    val disc by animateColorAsState(if (failed) Color(0xFF2A3150) else Color(0xFF0E1A5C), tween(600), label = "disc")
    val loop = rememberInfiniteTransition(label = "loop")
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "spin")
    val wave by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "wave")
    val breathe by loop.animateFloat(0.97f, 1.03f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "br")

    Box(modifier.size(270.dp), contentAlignment = Alignment.Center) {
        // Glow + (connected) signal waves on both sides, like the kit's "((V))".
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = 98.dp.toPx()
            drawCircle(
                Brush.radialGradient(listOf(ringTop.copy(alpha = 0.40f), Color.Transparent), center = c, radius = r * 1.35f),
                radius = r * 1.35f,
            )
            if (on) for (k in 0..1) {
                val t = (wave + k * 0.5f) % 1f
                val rr = r + 14.dp.toPx() + t * 26.dp.toPx()
                val a = (1f - t) * 0.9f
                for (start in listOf(140f, -40f)) drawArc(
                    ringTop.copy(alpha = a), start, 80f, false,
                    Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2),
                    style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Box(
            Modifier.size(196.dp)
                .scale(if (connecting) breathe else 1f)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(if (failed) Color(0xFF3A4160) else Color(0xFF1B2C8A), disc)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = ringTop),
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2 + 2.dp.toPx()
                // Glossy top highlight.
                drawCircle(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent), endY = size.height * 0.5f),
                    radius = size.minDimension / 2 - stroke,
                )
                drawCircle(
                    Brush.verticalGradient(listOf(ringTop, ringBottom)),
                    radius = size.minDimension / 2 - inset, style = Stroke(stroke),
                )
                if (connecting) drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, Color.White, Color.Transparent)),
                    startAngle = spin, sweepAngle = 100f, useCenter = false,
                    topLeft = Offset(inset, inset), size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    style = Stroke(stroke * 0.7f, cap = StrokeCap.Round),
                )
            }
            VMark(
                Modifier.size(112.dp),
                style = when {
                    on -> VStyle.CONNECTED
                    failed -> VStyle.OFF
                    else -> VStyle.READY
                },
            )
        }
    }
}

/** Shows the user's choice; on automatic, the server actually in use once connecting. */
@Composable
private fun ServerCard(selected: Server?, live: Server?, onClick: () -> Unit) {
    val shown = selected ?: live
    GlowPill(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF1E3399), Color(0xFF0A1242))))
                    .border(2.dp, Brush.linearGradient(KitBorders.Blue), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (shown != null) Text(shown.flag, fontSize = 26.sp) else KitBolt(Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
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
            Text("›", fontSize = 30.sp, color = VoltaColors.Violet, modifier = Modifier.padding(end = 16.dp))
        }
    }
}

/** The kit's small cyan lightning bolt. */
@Composable
private fun KitBolt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.62f, 0f); lineTo(w * 0.18f, h * 0.58f); lineTo(w * 0.48f, h * 0.58f)
            lineTo(w * 0.36f, h); lineTo(w * 0.84f, h * 0.38f); lineTo(w * 0.54f, h * 0.38f); close()
        }
        drawPath(path, Brush.verticalGradient(listOf(Color(0xFF8FF0FF), Color(0xFF2F9BFF))))
    }
}

@Composable
private fun NoServersCard(onRetry: () -> Unit) {
    GlowPill(Modifier.fillMaxWidth(), border = KitBorders.Violet, radius = 24.dp, onClick = onRetry) {
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

/** Flag colours like France's navy are too dark on our night-blue: lift them toward white. */
private fun readable(c: Color): Color =
    if (c.luminance() < 0.35f) androidx.compose.ui.graphics.lerp(c, Color.White, 0.55f) else c
