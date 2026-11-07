package com.voltavpn.app.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
}

private val scheme = darkColorScheme(
    primary = VoltaColors.Bolt,
    onPrimary = VoltaColors.Background,
    secondary = VoltaColors.Connected,
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

/** Wordmark: bolt + "Volta" + small FREE pill, mirroring the launcher icon. */
@Composable
fun VoltaLogo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_logo_mark), contentDescription = null, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(6.dp))
        Text("Volta", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = VoltaColors.Text)
        Spacer(Modifier.width(8.dp))
        Surface(color = VoltaColors.Connected, shape = RoundedCornerShape(50)) {
            Text(
                "FREE", fontSize = 10.sp, fontWeight = FontWeight.Black,
                color = VoltaColors.Background,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}
