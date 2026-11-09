package com.voltavpn.app.ui

import android.provider.Settings
import android.text.BidiFormatter
import android.text.TextDirectionHeuristics
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voltavpn.app.R

object VoltaColors {
    val Background = Color(0xFF0B1020)
    val Surface = Color(0xFF151B2E)
    val SurfaceHigh = Color(0xFF1E2640)
    val Bolt = Color(0xFFFFC83D)
    val Connected = Color(0xFF00E5A8)
    val Muted = Color(0xFF8A93AD)
    val Text = Color(0xFFF2F4FA)
    val Danger = Color(0xFFFF6B6B)

    // Trust tiers: one distinct colour each, always paired with the tier's name.
    val TierVolta = Bolt
    val TierNoLog = Color(0xFF5BF0C9)
    val TierTested = Color(0xFF8CC4FF)
    val TierUntested = Muted
}

/** The connect button's colours in each state. When connected, the country's accent replaces these. */
enum class ConnectVisual(val fill: Color, val ring: Color, val icon: Color) {
    IDLE(VoltaColors.Bolt, VoltaColors.Bolt, VoltaColors.Background),
    CONNECTING(VoltaColors.Surface, VoltaColors.Bolt, VoltaColors.Bolt),
    CONNECTED(VoltaColors.Surface, VoltaColors.Connected, VoltaColors.Connected),
    FAILED(VoltaColors.Surface, VoltaColors.Danger, VoltaColors.Danger),
}

/** Mixes towards white until this colour stands out enough from [background]. */
fun Color.withContrastOn(background: Color, minRatio: Float): Color {
    val bg = background.luminance()
    fun ratio(c: Color): Float {
        val l = c.luminance()
        return (maxOf(l, bg) + 0.05f) / (minOf(l, bg) + 0.05f)
    }
    var result = this
    var mix = 0f
    while (ratio(result) < minRatio && mix < 1f) {
        mix += 0.05f
        result = lerp(this, Color.White, mix)
    }
    return result
}

/** True when the user turned animations off in Android's accessibility settings. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Keeps a number and its unit in reading order ("181 ms") inside right-to-left text. */
fun ltr(text: String): String = BidiFormatter.getInstance().unicodeWrap(text, TextDirectionHeuristics.LTR)

private val scheme = darkColorScheme(
    primary = VoltaColors.Bolt,
    onPrimary = VoltaColors.Background,
    secondary = VoltaColors.Connected,
    background = VoltaColors.Background,
    onBackground = VoltaColors.Text,
    surface = VoltaColors.Surface,
    onSurface = VoltaColors.Text,
    surfaceVariant = VoltaColors.SurfaceHigh,
    surfaceContainerHigh = VoltaColors.SurfaceHigh, // dialogs
    onSurfaceVariant = VoltaColors.Muted,
    error = VoltaColors.Danger,
)

@Composable
fun VoltaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Wordmark: bolt + "Volta" + small translated "free" pill, mirroring the launcher icon. */
@Composable
fun VoltaLogo(modifier: Modifier = Modifier, pillColor: Color = VoltaColors.Connected) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_logo_mark), contentDescription = null, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(6.dp))
        Text("Volta", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = VoltaColors.Text)
        Spacer(Modifier.width(8.dp))
        Surface(color = pillColor, shape = RoundedCornerShape(50)) {
            Text(
                stringResource(R.string.free_badge), fontSize = 12.sp, fontWeight = FontWeight.Black,
                color = VoltaColors.Background,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}
