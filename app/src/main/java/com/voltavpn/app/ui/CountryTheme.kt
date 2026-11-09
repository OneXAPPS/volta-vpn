package com.voltavpn.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What drifts across the screen in a country's theme: its national symbol where it has one. */
enum class Motif {
    SAKURA, MAPLE, SNOW, STARS, PETALS, LEAVES, SAND, LANTERNS, CONFETTI,
    TULIP, LOTUS, SHAMROCK, FERN, ROSE, HIBISCUS, EDELWEISS, OLIVE, DAISY, POPPY,
    SUNFLOWER, JASMINE, WATTLE, ORCHID, FLEUR, GRAPE,
}

/**
 * A country's look while connected: its national symbol and colours, with its landmark
 * (see [LandmarkLayer]) on the horizon, and [accent] on the controls while connected.
 * Everything is drawn from vector paths, with no image files.
 */
data class CountryTheme(val motif: Motif, val colors: List<Color>, val rises: Boolean = false) {
    /** The colour that outlines the landmark. */
    val tint: Color get() = colors.first()

    /** The country's colour for the connected button, status text and badge: readable on its own backdrop. */
    val accent: Color = colors.first().withContrastOn(lerp(VoltaColors.Background, colors.first(), 0.3f), 4.5f)
}

fun themeFor(cc: String?): CountryTheme {
    fun c(vararg hex: Long) = hex.map { Color(0xFF000000 or it).forDarkBackdrop() }
    fun t(motif: Motif, vararg hex: Long) = CountryTheme(motif, c(*hex))
    fun up(motif: Motif, vararg hex: Long) = CountryTheme(motif, c(*hex), rises = true)
    return when (cc?.uppercase()) {
        // East Asia
        "JP" -> t(Motif.SAKURA, 0xFFC1D9, 0xFFB0CC, 0xFFE4EE)
        "KR" -> t(Motif.HIBISCUS, 0xE89BD0, 0xD77FC4, 0xF6C6E6) // mugunghwa
        "CN" -> up(Motif.LANTERNS, 0xFF5A4E, 0xFF8A3C)
        "TW" -> t(Motif.JASMINE, 0xFFB7C5, 0xFF9DB2, 0xFFE3EA)  // plum blossom
        "HK" -> t(Motif.ORCHID, 0xE06BA8, 0xF08FC0, 0xFFD1E8)   // bauhinia
        // South-East Asia
        "SG" -> t(Motif.ORCHID, 0xC77DDB, 0xE3A5F0, 0xF5D9FA)   // Vanda Miss Joaquim
        "MY" -> t(Motif.HIBISCUS, 0xFF5252, 0xFF7B7B, 0xFFB3B3) // bunga raya
        "ID" -> t(Motif.JASMINE, 0xFFFFFF, 0xFFF8E7, 0xE8F5E9)  // melati
        "PH" -> t(Motif.JASMINE, 0xFFFFFF, 0xFFFDE7, 0xE8F5E9)  // sampaguita
        "TH" -> t(Motif.PETALS, 0xFFD54F, 0xFFC107, 0xFFE57F)   // golden shower
        "VN" -> t(Motif.LOTUS, 0xFFD54F, 0xFFB7CF, 0xFFE0EB)   // the flag's gold star, with lotus pink
        // South Asia
        "IN" -> t(Motif.LOTUS, 0xFFA7C4, 0xFFC4D8, 0xFFE3EC)
        "PK" -> t(Motif.JASMINE, 0xFFFFFF, 0xC8E6C9, 0x81C784)
        "BD" -> t(Motif.LOTUS, 0xFFFFFF, 0xF8D7E3, 0xC8E6C9)    // shapla water lily
        "KZ" -> t(Motif.SNOW, 0x5CD6E8, 0xBFEFF5, 0xFFE27A)
        // Oceania
        "AU" -> t(Motif.WATTLE, 0xFFD54F, 0xFFC107, 0xA5D6A7)   // golden wattle
        "NZ" -> t(Motif.FERN, 0xA5D6A7, 0xC8E6C9, 0xE8F5E9)     // silver fern
        // North America
        "CA" -> t(Motif.MAPLE, 0xFF5252, 0xFF7043, 0xE53935)
        "US" -> up(Motif.STARS, 0xFF6B7A, 0x8FA8FF, 0xFFF4C2)
        "MX" -> t(Motif.DAISY, 0xFF8FB5, 0xFF9800, 0xFFCC80)    // dahlia
        // South America
        "BR" -> t(Motif.PETALS, 0xFFDF00, 0xFFE970, 0x7BD88F)   // ipe-amarelo
        "AR" -> t(Motif.PETALS, 0x74ACDF, 0xFF6B6B, 0xF6B40E)   // ceibo on the flag's sky blue
        "PE" -> t(Motif.PETALS, 0xFF5C6C, 0xFF8A95, 0xFFD1D6)   // cantuta
        "VE" -> t(Motif.ORCHID, 0xE3A5F0, 0xC77DDB, 0xFFCC00)   // flor de mayo
        "CL" -> t(Motif.PETALS, 0xFF5252, 0xFF8A80, 0xFFFFFF)   // copihue
        "CO" -> t(Motif.ORCHID, 0xD9A0E8, 0xF0C4F7, 0xFCD116)   // Cattleya trianae
        // Western Europe
        "GB" -> t(Motif.ROSE, 0xFF5C6C, 0xFFFFFF, 0xFFC1CC)     // Tudor rose
        "IE" -> t(Motif.SHAMROCK, 0x4CCB8A, 0x7FDDA8, 0xB9EBCD)
        "FR" -> t(Motif.FLEUR, 0x8FB4FF, 0xFFFFFF, 0xFF8A80)    // fleur-de-lis
        "DE" -> t(Motif.DAISY, 0x7FA6FF, 0xA9C4FF, 0xFFCE00)    // cornflower
        "NL" -> t(Motif.TULIP, 0xFF7043, 0xFF5C6C, 0xFFD54F)
        "BE" -> t(Motif.POPPY, 0xFF5C5C, 0xFF8A80, 0xFDDA24)
        "CH", "AT" -> t(Motif.EDELWEISS, 0xFFFFFF, 0xF1F3E8, 0xD8E3C8)
        // Southern Europe
        "ES" -> t(Motif.ROSE, 0xFF5252, 0xFF8A80, 0xF1BF00)     // carnation
        "PT" -> t(Motif.ROSE, 0xFF5252, 0x66BB6A, 0xFFCDD2)     // carnation
        "IT" -> t(Motif.JASMINE, 0xFFFFFF, 0x66BB6A, 0xFF8A80)  // strawberry tree flower
        "GR" -> t(Motif.OLIVE, 0x9CCC65, 0xC5E1A5, 0x8FB4FF)
        "HR" -> t(Motif.FLEUR, 0xB39DDB, 0xD1C4E9, 0xFF8A80)    // Croatian iris
        "TR" -> t(Motif.TULIP, 0xFF5252, 0xFF8A80, 0xFFFFFF)
        // Northern Europe
        "SE", "NO", "FI", "IS" -> t(Motif.SNOW, 0xFFFFFF, 0xDDEBFF, 0xBFD9FF)
        "DK" -> t(Motif.DAISY, 0xFFFFFF, 0xFFF4C2, 0xFF8A80)    // marguerite
        "EE" -> t(Motif.DAISY, 0x7FA6FF, 0xA9C4FF, 0xFFFFFF)    // cornflower
        "LV" -> t(Motif.DAISY, 0xFFFFFF, 0xFFE9A8, 0xE08A95)    // oxeye daisy
        "LT" -> t(Motif.LEAVES, 0xC5D86D, 0x9CCC65, 0xFDB913)   // rue
        // Central and Eastern Europe
        "PL" -> t(Motif.POPPY, 0xFF4D5E, 0xFF8A95, 0xFFFFFF)
        "CZ" -> t(Motif.LEAVES, 0x9CCC65, 0xC5E1A5, 0x7CB342)   // linden
        "HU" -> t(Motif.TULIP, 0xFF5252, 0xFFFFFF, 0x81C784)
        "RO" -> t(Motif.ROSE, 0xFF7FA8, 0xFFB0CC, 0xFCD116)     // peony
        "BG" -> t(Motif.ROSE, 0xFF7FA8, 0xFFB0CC, 0x66BB6A)     // Kazanlak rose
        "RS" -> t(Motif.PETALS, 0xB39DDB, 0xD1C4E9, 0xFFFFFF)   // Natalie's ramonda
        "RU" -> t(Motif.SNOW, 0xFFFFFF, 0xDDEBFF, 0xBFD9FF)
        "BY" -> t(Motif.DAISY, 0x8FB4FF, 0xB8CFFF, 0xFFFFFF)    // flax
        "UA" -> t(Motif.SUNFLOWER, 0xFFD600, 0xFFC107, 0xFFE57F)
        "MD", "GE" -> t(Motif.GRAPE, 0xB28DFF, 0x9575CD, 0x9CCC65)
        // Middle East and North Africa
        "AE", "QA", "KW", "BH", "OM", "YE", "DZ", "LY" -> up(Motif.SAND, 0xFFD54F, 0xFFE082, 0xFFF8E1)
        "SA" -> t(Motif.JASMINE, 0xFFFFFF, 0xFFF8E7, 0x81C784)
        "IQ" -> up(Motif.SAND, 0xE8C27A, 0xFFE082, 0xFFF8E1)
        "EG" -> t(Motif.LOTUS, 0x8FB4FF, 0xFFFFFF, 0xFFD54F)    // blue and white lotus
        "JO" -> t(Motif.FLEUR, 0xB28DFF, 0x9575CD, 0xE1D5FF)    // black iris
        "MA" -> t(Motif.ROSE, 0xFF7FA8, 0xFFB0CC, 0x81C784)     // Damask rose of Kelaat M'Gouna
        "TN", "SY" -> t(Motif.JASMINE, 0xFFFFFF, 0xFFF8E7, 0xF3E5F5)
        "PS" -> t(Motif.OLIVE, 0x9CCC65, 0xC5E1A5, 0xFFFFFF)
        "IL" -> t(Motif.POPPY, 0xFF5252, 0xFF8A80, 0x8FB4FF)    // poppy anemone
        "LB" -> t(Motif.LEAVES, 0x66BB6A, 0x81C784, 0xA5D6A7)   // cedar
        // Africa
        "ZA" -> t(Motif.DAISY, 0xFF9EBB, 0xFFC4D6, 0xFFB612)    // king protea
        // No landmark or symbol researched: the flag's own colours.
        else -> CountryTheme(Motif.CONFETTI, flagColors(cc))
    }
}

private class Particle(val x: Float, val phase: Float, val speed: Float, val size: Float, val spin: Float, val sway: Float, val color: Int)

/** Drifting motif particles; fades in on arrival, out on disconnect. */
@Composable
fun MotifLayer(theme: CountryTheme, active: Boolean, key: Any?, quietBottomInRoot: Float?, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    val alpha = remember { Animatable(0f) }
    var time by remember { mutableFloatStateOf(0f) }
    var originY by remember { mutableFloatStateOf(0f) }
    val particles = remember(key) {
        val rnd = java.util.Random(key.hashCode().toLong())
        List(PARTICLES) {
            Particle(
                x = rnd.nextFloat(), phase = rnd.nextFloat(), speed = 0.035f + rnd.nextFloat() * 0.05f,
                size = 0.6f + rnd.nextFloat() * 0.8f, spin = (rnd.nextFloat() - 0.5f) * 140f,
                sway = 0.02f + rnd.nextFloat() * 0.05f, color = rnd.nextInt(16),
            )
        }
    }
    LaunchedEffect(active, key) {
        if (active) {
            alpha.snapTo(0f)
            alpha.animateTo(1f, tween(1400, delayMillis = 400))
        } else alpha.animateTo(0f, tween(500))
    }
    LaunchedEffect(active) {
        if (!active || reducedMotion) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1e9f }
    }
    if (alpha.value <= 0f || reducedMotion) return
    val paths = remember { MotifPaths() }
    Canvas(modifier.fillMaxSize().onGloballyPositioned { originY = it.positionInRoot().y }) {
        // Detailed national symbols need more room than a snowflake to read as what they are.
        val unit = size.minDimension / (if (theme.motif in SYMBOL_PATHS) 17f else 26f)
        // Particles fade while passing behind the status text, so they never fight it.
        val quietBottom = (quietBottomInRoot ?: 0f) - originY
        val fadeBand = size.height * 0.06f
        for (p in particles) {
            val travel = ((p.phase + time * p.speed) % 1f)
            val yFrac = if (theme.rises) 1.1f - travel * 1.2f else travel * 1.2f - 0.1f
            val x = (p.x + sin((time * 0.8f + p.phase * 6f).toDouble()).toFloat() * p.sway) * size.width
            val y = yFrac * size.height
            // Fade near the edges so particles never pop in or out.
            val edge = (minOf(travel, 1f - travel) * 8f).coerceIn(0f, 1f)
            val quiet = 0.25f + 0.75f * ((y - quietBottom) / fadeBand).coerceIn(0f, 1f)
            val color = theme.colors[p.color % theme.colors.size].copy(alpha = 0.6f * alpha.value * edge * quiet)
            translate(x, y) {
                rotate(p.phase * 360f + time * p.spin, pivot = Offset.Zero) {
                    scale(unit * p.size, pivot = Offset.Zero) { drawMotif(theme.motif, paths, color, time + p.phase * 10f) }
                }
            }
        }
    }
}

/** Unit-size shapes (about 1 x 1, centred on 0,0), built once and scaled per particle. */
private class MotifPaths {
    private val symbols = mutableMapOf<Motif, Path>()

    /** A national symbol, parsed the first time its country is shown. */
    fun symbol(m: Motif): Path? = symbols[m] ?: SYMBOL_PATHS[m]?.let { data ->
        PathParser().parsePathString(data).toPath().apply { fillType = PathFillType.EvenOdd }.also { symbols[m] = it }
    }

    val petal = Path().apply {
        // Cherry-blossom petal with the characteristic notch at its tip.
        moveTo(-0.5f, 0f)
        cubicTo(-0.3f, -0.45f, 0.3f, -0.45f, 0.5f, -0.15f)
        lineTo(0.36f, 0f)
        lineTo(0.5f, 0.15f)
        cubicTo(0.3f, 0.45f, -0.3f, 0.45f, -0.5f, 0f)
        close()
    }
    val roundPetal = Path().apply {
        moveTo(0f, -0.5f)
        cubicTo(0.45f, -0.35f, 0.45f, 0.35f, 0f, 0.55f)
        cubicTo(-0.45f, 0.35f, -0.45f, -0.35f, 0f, -0.5f)
        close()
    }
    val maple = Path().apply {
        // Simplified 5-lobed maple leaf with a short stem.
        val pts = floatArrayOf(
            0f, -0.62f, 0.12f, -0.34f, 0.3f, -0.42f, 0.24f, -0.12f, 0.58f, -0.2f, 0.48f, 0.02f,
            0.6f, 0.1f, 0.26f, 0.3f, 0.3f, 0.44f, 0.04f, 0.38f, 0.04f, 0.62f, -0.04f, 0.62f,
            -0.04f, 0.38f, -0.3f, 0.44f, -0.26f, 0.3f, -0.6f, 0.1f, -0.48f, 0.02f, -0.58f, -0.2f,
            -0.24f, -0.12f, -0.3f, -0.42f, -0.12f, -0.34f,
        )
        moveTo(pts[0], pts[1])
        for (i in 2 until pts.size step 2) lineTo(pts[i], pts[i + 1])
        close()
    }
    val leaf = Path().apply {
        // Slender evergreen leaf (cedar sprig) with a midrib cut by the background.
        moveTo(0f, -0.55f)
        cubicTo(0.28f, -0.25f, 0.28f, 0.25f, 0f, 0.55f)
        cubicTo(-0.28f, 0.25f, -0.28f, -0.25f, 0f, -0.55f)
        close()
    }
    val star = Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) 0.5f else 0.21f
            val a = -PI / 2 + i * PI / 5
            val px = (cos(a) * r).toFloat()
            val py = (sin(a) * r).toFloat()
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    val sparkle = Path().apply {
        moveTo(0f, -0.5f); lineTo(0.1f, -0.1f); lineTo(0.5f, 0f); lineTo(0.1f, 0.1f)
        lineTo(0f, 0.5f); lineTo(-0.1f, 0.1f); lineTo(-0.5f, 0f); lineTo(-0.1f, -0.1f); close()
    }
    val lantern = Path().apply {
        moveTo(-0.32f, -0.3f)
        cubicTo(-0.5f, -0.1f, -0.5f, 0.2f, -0.32f, 0.36f)
        lineTo(0.32f, 0.36f)
        cubicTo(0.5f, 0.2f, 0.5f, -0.1f, 0.32f, -0.3f)
        close()
    }
}

private fun DrawScope.drawMotif(m: Motif, p: MotifPaths, color: Color, t: Float) {
    when (m) {
        Motif.TULIP, Motif.LOTUS, Motif.SHAMROCK, Motif.FERN, Motif.ROSE, Motif.HIBISCUS, Motif.EDELWEISS, Motif.OLIVE,
        Motif.DAISY, Motif.POPPY, Motif.SUNFLOWER, Motif.JASMINE, Motif.WATTLE, Motif.ORCHID, Motif.FLEUR, Motif.GRAPE ->
            p.symbol(m)?.let { drawPath(it, color) }
        Motif.SAKURA -> drawPath(p.petal, color)
        Motif.PETALS -> drawPath(p.roundPetal, color)
        Motif.MAPLE -> drawPath(p.maple, color)
        Motif.LEAVES -> drawPath(p.leaf, color)
        Motif.STARS -> drawPath(p.star, color.copy(alpha = color.alpha * (0.6f + 0.4f * sin(t * 3f))))
        Motif.SAND -> drawPath(p.sparkle, color.copy(alpha = color.alpha * (0.5f + 0.5f * sin(t * 4f))))
        Motif.LANTERNS -> {
            drawCircle(color.copy(alpha = color.alpha * 0.35f), radius = 0.75f)
            drawPath(p.lantern, color)
            drawRect(Color(0xFFFFD54F).copy(alpha = color.alpha), Offset(-0.18f, -0.42f), androidx.compose.ui.geometry.Size(0.36f, 0.1f))
        }
        Motif.SNOW -> {
            val s = Stroke(width = 0.07f)
            for (i in 0 until 3) {
                val a = i * PI / 3
                val dx = (cos(a) * 0.45f).toFloat()
                val dy = (sin(a) * 0.45f).toFloat()
                drawLine(color, Offset(-dx, -dy), Offset(dx, dy), strokeWidth = s.width)
            }
            drawCircle(color, radius = 0.09f)
        }
        Motif.CONFETTI -> drawRect(
            color, Offset(-0.3f, -0.12f),
            androidx.compose.ui.geometry.Size(0.6f, 0.24f * (0.3f + 0.7f * kotlin.math.abs(sin(t * 3f)))),
        )
    }
}

private const val PARTICLES = 26
