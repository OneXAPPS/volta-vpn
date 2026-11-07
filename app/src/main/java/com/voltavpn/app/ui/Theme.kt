package com.voltavpn.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Brand palette, taken from the Volta logo: night-blue field, electric-blue glow,
 * white-to-violet V, lime "Free" ribbon.
 */
object VoltaColors {
    val Background = Color(0xFF040A26)
    val Surface = Color(0xFF0C1546)
    val SurfaceHigh = Color(0xFF16215E)
    /** Electric blue: the orbit glow. Used for "connecting" and accents. */
    val Bolt = Color(0xFF3D8BFF)
    /** Violet end of the V. */
    val Violet = Color(0xFFA35BFF)
    val VioletLight = Color(0xFFE6C9FF)
    /** Lime of the Free ribbon: "connected / free / good". */
    val Connected = Color(0xFF8BE31F)
    val Muted = Color(0xFF8E9BC9)
    val Text = Color(0xFFF3F5FF)
    val Danger = Color(0xFFFF6B6B)

    /** The V's white-to-violet sweep, for brand text and highlights. */
    val VGradient = Brush.linearGradient(listOf(Color(0xFFFFFFFF), VioletLight, Violet))
}

private val scheme = darkColorScheme(
    primary = VoltaColors.Violet,
    onPrimary = Color.White,
    secondary = VoltaColors.Bolt,
    tertiary = VoltaColors.Connected,
    background = VoltaColors.Background,
    onBackground = VoltaColors.Text,
    surface = VoltaColors.Surface,
    onSurface = VoltaColors.Text,
    surfaceVariant = VoltaColors.SurfaceHigh,
    onSurfaceVariant = VoltaColors.Muted,
    error = VoltaColors.Danger,
)

@Composable
fun VoltaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

/** "Volta VPN" set like the logo: white Volta, violet VPN. */
@Composable
fun Wordmark(size: Int = 22, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = VoltaColors.Text)) { append("Volta ") }
            withStyle(SpanStyle(brush = Brush.linearGradient(listOf(VoltaColors.VioletLight, VoltaColors.Violet)))) {
                append("VPN")
            }
        },
        style = TextStyle(fontSize = size.sp, fontWeight = FontWeight.ExtraBold),
        modifier = modifier,
    )
}

/** Header logo from the kit: V app tile + "Volta VPN" wordmark + the lime Free leaf. */
@Composable
fun VoltaLogo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AppTile(34.dp)
        Spacer(Modifier.width(10.dp))
        Wordmark()
        Spacer(Modifier.width(8.dp))
        FreeLeaf(height = 22.dp)
    }
}
