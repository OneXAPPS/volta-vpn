package com.voltavpn.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.hypot

/**
 * "You've arrived" effect: on connect, a wave in the destination flag's colours spreads
 * from the connect button, then settles into a soft tint of that flag. Pure Canvas
 * drawing (no images), so it adds almost nothing to the app size.
 */
@Composable
fun CountryBackdrop(countryCode: String?, active: Boolean, center: Offset?, modifier: Modifier = Modifier) {
    val colors = remember(countryCode) { flagColors(countryCode) }
    val wave = remember { Animatable(0f) }
    val tint = remember { Animatable(0f) }
    val reducedMotion = rememberReducedMotion()

    // Replays whenever we land in a new country.
    LaunchedEffect(active, countryCode) {
        if (active && reducedMotion) {
            tint.snapTo(1f)
        } else if (active) {
            wave.snapTo(0f)
            tint.snapTo(0f)
            wave.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
            tint.animateTo(1f, tween(500))
        } else {
            tint.animateTo(0f, tween(400))
            wave.snapTo(0f)
        }
    }

    Canvas(modifier.fillMaxSize()) {
        val c = center ?: Offset(size.width / 2, size.height * 0.42f)
        val maxR = hypot(size.width, size.height)
        // Settled state: soft vertical blend of the flag colours behind everything.
        if (tint.value > 0f) {
            drawRect(
                Brush.verticalGradient(
                    colors.map { it.copy(alpha = 0.22f * tint.value) } + Color.Transparent
                )
            )
        }
        // Soft glow of the flag's main colour around the connect button.
        if (tint.value > 0f) {
            val glowR = size.minDimension * 0.62f
            drawCircle(
                Brush.radialGradient(
                    listOf(colors.first().copy(alpha = 0.38f * tint.value), Color.Transparent),
                    center = c, radius = glowR,
                ),
                radius = glowR, center = c,
            )
        }
        // Expanding wave: a ring of flag colours fading as it travels outwards.
        val p = wave.value
        if (p in 0.001f..0.999f) {
            val r = maxR * p
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.Transparent,
                    0.7f to colors.first().copy(alpha = 0.10f * (1 - p)),
                    0.92f to colors.last().copy(alpha = 0.45f * (1 - p)),
                    1f to Color.Transparent,
                    center = c, radius = r,
                ),
                radius = r, center = c,
            )
        }
    }
}

/** WCAG's minimum for graphics; country colours are decoration, so this is enough. */
private const val MIN_DECOR_CONTRAST = 3f

/** Lifts a dark country colour (Germany's black, Australia's navy) so it shows on the dark UI. */
fun Color.forDarkBackdrop(): Color = withContrastOn(VoltaColors.Background, MIN_DECOR_CONTRAST)

/** Main colours of each flag (2-3 per country), legible as decoration. Unknown countries get the brand colours. */
fun flagColors(cc: String?): List<Color> {
    val hex = FLAGS[cc?.uppercase()] ?: return listOf(VoltaColors.Connected, VoltaColors.Bolt)
    return hex.split(',').map { Color(0xFF000000 or it.toLong(16)).forDarkBackdrop() }
}

private val FLAGS = mapOf(
    "JP" to "BC002D,FFFFFF", "KR" to "CD2E3A,FFFFFF,0047A0", "US" to "B22234,FFFFFF,3C3B6E",
    "GB" to "C8102E,FFFFFF,012169", "DE" to "000000,DD0000,FFCE00", "FR" to "0055A4,FFFFFF,EF4135",
    "NL" to "AE1C28,FFFFFF,21468B", "CA" to "FF0000,FFFFFF", "AU" to "012169,FFFFFF,E4002B",
    "RU" to "FFFFFF,0039A6,D52B1E", "UA" to "0057B7,FFD700", "TH" to "A51931,F4F5F8,2D2A4A",
    "VN" to "DA251D,8E1B14", "TW" to "FE0000,000095", "HK" to "DE2910,FFFFFF", "SG" to "EF3340,FFFFFF",
    "IN" to "FF9933,FFFFFF,138808", "ID" to "FF0000,FFFFFF", "MY" to "CC0001,010066,FFCC00",
    "PH" to "0038A8,CE1126,FCD116", "CN" to "DE2910,FFDE00", "BR" to "009C3B,FFDF00,002776",
    "AR" to "74ACDF,FFFFFF,F6B40E", "PE" to "D91023,FFFFFF", "VE" to "FFCC00,00247D,CF142B",
    "MX" to "006847,FFFFFF,CE1126", "CL" to "0039A6,FFFFFF,D52B1E", "CO" to "FCD116,003893,CE1126",
    "ES" to "AA151B,F1BF00", "IT" to "009246,FFFFFF,CE2B37", "PL" to "FFFFFF,DC143C",
    "RO" to "002B7F,FCD116,CE1126", "HU" to "CE2939,FFFFFF,477050", "AT" to "ED2939,FFFFFF",
    "CH" to "FF0000,FFFFFF", "SE" to "006AA7,FECC00", "NO" to "BA0C2F,FFFFFF,00205B",
    "FI" to "FFFFFF,002F6C", "DK" to "C8102E,FFFFFF", "IE" to "169B62,FFFFFF,FF883E",
    "BE" to "000000,FDDA24,EF3340", "CZ" to "FFFFFF,D7141A,11457E", "HR" to "FF0000,FFFFFF,171796",
    "TR" to "E30A17,FFFFFF", "AE" to "00732F,FFFFFF,000000,FF0000", "SA" to "006C35,FFFFFF",
    "EG" to "CE1126,FFFFFF,000000", "ZA" to "007A4D,FFB612,DE3831", "KZ" to "00AFCA,FEC50C",
    "IL" to "FFFFFF,0038B8", "PT" to "006600,FF0000", "GR" to "0D5EAF,FFFFFF", "BG" to "FFFFFF,00966E,D62612",
    "RS" to "C6363C,0C4076,FFFFFF", "LT" to "FDB913,006A44,C1272D", "LV" to "9E3039,FFFFFF",
    "EE" to "0072CE,000000,FFFFFF", "MD" to "003DA5,FFD200,CC092F", "GE" to "FFFFFF,FF0000",
    "PK" to "01411C,FFFFFF", "BD" to "006A4E,F42A41", "NZ" to "012169,FFFFFF,C8102E",
)
