package com.voltavpn.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voltavpn.app.ConnectFailure
import com.voltavpn.app.ConnectProgress
import com.voltavpn.app.FailureKind
import com.voltavpn.app.R
import com.voltavpn.app.data.Server
import com.voltavpn.app.data.countryDisplayName
import com.voltavpn.app.vpn.ExitCheckState
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
    exit: ExitCheckState,
    dnsEncrypted: Boolean,
    connectedSince: Long,
    progress: ConnectProgress?,
    failure: ConnectFailure?,
    passActive: () -> Boolean,
    onToggle: () -> Unit,
    onPickServer: () -> Unit,
    onRetryServers: () -> Unit,
    onSettings: () -> Unit,
    onRetryConnect: () -> Unit,
    onUseBestServer: () -> Unit,
    onDismissFailure: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis(); delay(1000)
        }
    }
    var buttonCenter by remember { mutableStateOf<Offset?>(null) }
    var cardTop by remember { mutableStateOf<Float?>(null) }
    var contentBottom by remember { mutableStateOf<Float?>(null) }
    // A connect job spans moments where the tunnel is down; to the user it's one "connecting".
    val visual = when {
        progress != null || state == VpnState.CONNECTING -> ConnectVisual.CONNECTING
        state == VpnState.CONNECTED -> ConnectVisual.CONNECTED
        state == VpnState.FAILED || failure != null -> ConnectVisual.FAILED
        else -> ConnectVisual.IDLE
    }
    val connected = visual == ConnectVisual.CONNECTED
    val live = if (visual == ConnectVisual.CONNECTED || visual == ConnectVisual.CONNECTING) current else null
    // The country we are (or are getting) in: the live server if any, else the user's pick.
    val place = live ?: selected

    Box(Modifier.fillMaxSize()) {
        val theme = remember(place?.countryCode) { themeFor(place?.countryCode) }
        CountryBackdrop(place?.countryCode, connected && place != null, buttonCenter)
        LandmarkLayer(place?.countryCode, theme.tint, connected && place != null, cardTop, contentBottom)
        MotifLayer(theme, connected && place != null, key = place?.countryCode, quietBottomInRoot = contentBottom)

        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val pill by animateColorAsState(if (connected) theme.accent else VoltaColors.Connected, label = "pill")
                VoltaLogo(Modifier.weight(1f), pillColor = pill)
                IconButton(onClick = onSettings) {
                    Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings), tint = VoltaColors.Muted)
                }
            }

            // The status scrolls on small screens or big fonts, so the server card below stays reachable.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val buttonSize = (maxHeight * 0.42f).coerceIn(128.dp, 210.dp)
                // Connected in a country with a landmark, the status rises to leave it the horizon.
                val hasLandmark = place?.countryCode?.uppercase() in LANDMARK_SHAPES
                val bias by animateFloatAsState(if (connected && hasLandmark) -0.55f else 0f, tween(600), label = "bias")
                Box(Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(rememberScrollState())) {
                Column(
                    Modifier.fillMaxWidth().align(BiasAlignment(0f, bias)).onGloballyPositioned {
                        contentBottom = it.positionInRoot().y + it.size.height
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val elapsed = if (connected) ltr(formatDuration(if (connectedSince > 0) now - connectedSince else 0)) else null
                    ConnectButton(visual, theme.accent, buttonSize, elapsed, traffic, onToggle, Modifier.onGloballyPositioned {
                        val b = it.boundsInRoot()
                        buttonCenter = Offset((b.left + b.right) / 2, (b.top + b.bottom) / 2)
                    })
                    Spacer(Modifier.height(16.dp))
                    StatusLines(visual, progress, live, theme.accent)
                    if (connected) ProtectionLine(exit, place, dnsEncrypted)
                    if (failure != null) {
                        Spacer(Modifier.height(14.dp))
                        FailureCard(failure, onRetryConnect, onUseBestServer, onDismissFailure)
                    }
                }
                }
            }

            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().onGloballyPositioned { cardTop = it.positionInRoot().y }) {
                if (!hasServers && !loading) NoServersCard(onRetryServers)
                else ServerCard(selected, live, onPickServer)
            }
            Spacer(Modifier.height(10.dp))
            // Checked once a minute: the pass only ever changes at midnight or on connect.
            PassLine(remember(now / 60_000, state) { passActive() })
        }
    }
}

/** The status headline, announced by screen readers as it changes, plus how to act on it. */
@Composable
private fun StatusLines(visual: ConnectVisual, progress: ConnectProgress?, live: Server?, accent: Color) {
    val target = progress?.target ?: live
    val headline = when (visual) {
        ConnectVisual.CONNECTED -> stringResource(R.string.state_connected)
        ConnectVisual.FAILED -> stringResource(R.string.state_failed)
        ConnectVisual.IDLE -> stringResource(R.string.state_tap)
        ConnectVisual.CONNECTING -> when {
            target == null -> stringResource(R.string.state_connecting)
            progress?.switching == true -> stringResource(R.string.state_switching_to, target.displayName())
            else -> stringResource(R.string.state_connecting_to, target.displayName())
        }
    }
    Text(
        headline, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (visual == ConnectVisual.CONNECTED && live != null) {
        Text(
            stringResource(R.string.now_in, live.displayName(), live.flag),
            color = accent, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    val hint = when (visual) {
        ConnectVisual.CONNECTED -> stringResource(R.string.hint_disconnect)
        ConnectVisual.CONNECTING ->
            if (progress != null && progress.attempt > 1) {
                stringResource(R.string.trying_other, progress.attempt.toString(), progress.total.toString())
            } else stringResource(R.string.hint_cancel)
        else -> null
    }
    if (hint != null) {
        Text(hint, color = VoltaColors.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Stays silent when all is well: it only speaks up if the location is wrong or couldn't be confirmed. */
@Composable
private fun ProtectionLine(exit: ExitCheckState, place: Server?, dnsEncrypted: Boolean) {
    val warning = when (exit) {
        ExitCheckState.Checking -> null
        ExitCheckState.Unverified -> stringResource(R.string.exit_unknown) to VoltaColors.Muted
        is ExitCheckState.Result ->
            if (place == null || exit.info.country == place.countryCode) null
            else stringResource(R.string.exit_mismatch, countryDisplayName(exit.info.country) ?: exit.info.country) to VoltaColors.Danger
    }
    if (warning != null) {
        Text(warning.first, color = warning.second, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
    if (dnsEncrypted) {
        Text(stringResource(R.string.dns_encrypted), color = VoltaColors.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
    if (place != null && place.unlocks.isNotEmpty()) {
        Text(
            stringResource(R.string.unlocks_line, place.unlocks.joinToString(", ")),
            color = VoltaColors.Bolt, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ConnectButton(
    visual: ConnectVisual, accent: Color, size: Dp, elapsed: String?, traffic: Traffic,
    onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val connected = visual == ConnectVisual.CONNECTED
    val fill by animateColorAsState(visual.fill, label = "fill")
    val ring by animateColorAsState(if (connected) accent else visual.ring, label = "ring")
    val icon by animateColorAsState(if (connected) accent else visual.icon, label = "icon")
    val pulsing = visual == ConnectVisual.CONNECTING && !rememberReducedMotion()
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f, targetValue = if (pulsing) 1.06f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale",
    )
    val action = stringResource(
        when (visual) {
            ConnectVisual.CONNECTED -> R.string.disconnect
            ConnectVisual.CONNECTING -> R.string.cancel_connection
            else -> R.string.connect
        }
    )
    Box(
        modifier.size(size).scale(scale)
            .border(BorderStroke(6.dp, ring), CircleShape)
            .padding(14.dp)
            // Clip first so the touch ripple is a soft circle, not a square.
            .clip(CircleShape)
            .background(fill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = ring),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = action },
        contentAlignment = Alignment.Center,
    ) {
        if (elapsed != null) ConnectionGauge(elapsed, traffic, icon, size)
        else Icon(painterResource(R.drawable.ic_bolt), contentDescription = null, tint = icon, modifier = Modifier.size(size * 0.4f))
    }
}

/** Bolt, connection time and speed, sized to stay inside the circle at any screen or font size. */
@Composable
private fun ConnectionGauge(elapsed: String, traffic: Traffic, color: Color, size: Dp) {
    val fontScale = LocalDensity.current.fontScale
    val roomy = size >= 170.dp && fontScale <= 1.3f
    // The circle can't grow with the font: past this it would clip, and screen readers get it all anyway.
    val textScale = minOf(fontScale, 1.3f) / fontScale
    val down = formatSpeed(traffic.downPerSec)
    val up = formatSpeed(traffic.upPerSec)
    val downLabel = stringResource(R.string.stat_down, down)
    val upLabel = stringResource(R.string.stat_up, up)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (roomy) Icon(painterResource(R.drawable.ic_bolt), contentDescription = null, tint = color, modifier = Modifier.size(size * 0.2f))
        Text(
            elapsed, color = VoltaColors.Text, fontWeight = FontWeight.SemiBold, maxLines = 1,
            fontSize = (if (roomy) 22.sp else 13.sp) * textScale,
        )
        val speedSize = (if (roomy) 11.sp else 10.sp) * textScale
        if (roomy) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("↓", down, downLabel, color, speedSize)
                Stat("↑", up, upLabel, color, speedSize)
            }
        } else {
            Stat("↓", down, downLabel, color, speedSize)
            Stat("↑", up, upLabel, color, speedSize)
        }
    }
}

/** A failure stays on screen, with its cause and a way forward, until the user acts on it. */
@Composable
private fun FailureCard(failure: ConnectFailure, onRetry: () -> Unit, onUseBest: () -> Unit, onDismiss: () -> Unit) {
    val targetName = failure.target?.displayName() ?: stringResource(R.string.best_server)
    val message = when (failure.kind) {
        FailureKind.NO_INTERNET -> stringResource(R.string.no_internet)
        FailureKind.NO_SERVERS -> stringResource(R.string.no_servers_fail)
        FailureKind.COUNTRY_UNAVAILABLE -> stringResource(R.string.country_unavailable, targetName)
        FailureKind.ALL_FAILED -> stringResource(R.string.all_failed)
        FailureKind.SWITCH_FAILED -> stringResource(R.string.switch_kept, targetName, failure.keptOn?.displayName() ?: "")
        FailureKind.TAMPERED -> stringResource(R.string.tampered)
    }
    val stillConnected = failure.kind == FailureKind.SWITCH_FAILED
    Surface(
        color = VoltaColors.Surface, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (stillConnected) VoltaColors.Bolt else VoltaColors.Danger),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 2.dp)) {
            Text(message, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close), color = VoltaColors.Muted) }
                when {
                    stillConnected || failure.kind == FailureKind.TAMPERED -> Unit
                    failure.kind == FailureKind.COUNTRY_UNAVAILABLE ->
                        TextButton(onClick = onUseBest) { Text(stringResource(R.string.action_best), color = VoltaColors.Bolt) }
                    else -> TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry), color = VoltaColors.Bolt) }
                }
            }
        }
    }
}

/** Shows the user's choice, and always the trust tier of the server actually in use. */
@Composable
private fun ServerCard(selected: Server?, live: Server?, onClick: () -> Unit) {
    val shown = live ?: selected
    Surface(
        onClick = onClick, color = VoltaColors.Surface, shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(shown?.flag ?: "⚡", fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics { })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    selected?.displayName() ?: stringResource(R.string.best_server),
                    fontWeight = FontWeight.SemiBold,
                )
                if (shown == null) {
                    Text(stringResource(R.string.auto_pick), color = VoltaColors.Muted, fontSize = 13.sp)
                } else {
                    val tier = tierLabel(shown)
                    Text(
                        buildAnnotatedString {
                            if (selected == null) append(shown.displayName() + " · ")
                            withStyle(SpanStyle(color = tierColor(shown.tier), fontWeight = FontWeight.Bold)) { append(tier) }
                            append(pingSuffix(shown.pingMs))
                        },
                        color = VoltaColors.Muted, fontSize = 13.sp,
                    )
                }
            }
            Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = VoltaColors.Muted)
        }
    }
}

@Composable
private fun NoServersCard(onRetry: () -> Unit) {
    Surface(
        onClick = onRetry, color = VoltaColors.Surface, shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.no_servers_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.no_servers), color = VoltaColors.Muted, fontSize = 13.sp)
        }
    }
}

private fun pingSuffix(ping: Int?) = if (ping != null && ping >= 0) " · " + formatPing(ping) else ""

fun formatPing(ms: Int) = ltr("$ms ms")

@Composable
private fun PassLine(active: Boolean) {
    Text(
        stringResource(if (active) R.string.pass_active else R.string.pass_needed),
        color = VoltaColors.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Stat(arrow: String, value: String, description: String, arrowColor: Color, fontSize: TextUnit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Text(arrow, color = arrowColor, fontWeight = FontWeight.Bold, fontSize = fontSize, maxLines = 1)
        Spacer(Modifier.width(2.dp))
        Text(value, color = VoltaColors.Text, fontSize = fontSize, maxLines = 1)
    }
}

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

fun formatSpeed(bytesPerSec: Long): String = ltr(
    if (bytesPerSec >= 1L shl 20) "%.1f MB/s".format(bytesPerSec / (1L shl 20).toDouble())
    else "%d KB/s".format(bytesPerSec / 1024)
)
