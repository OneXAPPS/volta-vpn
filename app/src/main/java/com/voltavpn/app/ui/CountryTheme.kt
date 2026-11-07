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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What drifts across the screen in a country's theme. */
enum class Motif { SAKURA, MAPLE, SNOW, STARS, PETALS, SAND, LANTERNS, CONFETTI }

/**
 * A country's look while connected: the motif (sakura for Japan, maple leaves for
 * Canada, snow for the Nordics...), its colours, and the accent used for the
 * connect ring. Everything is drawn from small vector paths: no images, ~0 KB.
 */
data class CountryTheme(val motif: Motif, val colors: List<Color>, val accent: Color, val rises: Boolean = false)

fun themeFor(cc: String?): CountryTheme {
    val flag = flagColors(cc)
    fun c(vararg hex: Long) = hex.map { Color(0xFF000000 or it) }
    return when (cc?.uppercase()) {
        "JP" -> CountryTheme(Motif.SAKURA, c(0xFFC1D9, 0xFFB0CC, 0xFFE4EE), Color(0xFFFF8FB5))
        "KR" -> CountryTheme(Motif.PETALS, c(0xE89BD0, 0xD77FC4, 0xF6C6E6), Color(0xFFE48BCB)) // mugunghwa
        "TW", "CN", "HK" -> CountryTheme(Motif.LANTERNS, c(0xE53935, 0xFF6F3C), Color(0xFFFF5A4E), rises = true)
        "CA" -> CountryTheme(Motif.MAPLE, c(0xE53935, 0xFF7043, 0xC62828), Color(0xFFFF5252))
        "RU", "FI", "NO", "SE", "IS", "EE", "LV", "LT", "KZ", "CH", "AT" ->
            CountryTheme(Motif.SNOW, c(0xFFFFFF, 0xDDEBFF, 0xBFD9FF), Color(0xFFBFD9FF))
        "US", "VN", "AU", "NZ", "SG", "TR", "CL" ->
            CountryTheme(Motif.STARS, flag.filter { it != Color.White }.ifEmpty { flag } + Color(0xFFFFF4C2), flag.first(), rises = true)
        "TH", "IN", "MY", "ID", "PH", "NL" ->
            CountryTheme(Motif.PETALS, if (cc.uppercase() == "IN") c(0xFFA726, 0xFFCC80, 0xFFF3E0) else c(0xFF8A80, 0xFFD180, 0xFFFFFF), Color(0xFFFFB74D))
        "AE", "SA", "EG", "QA", "KW", "BH", "OM", "JO", "MA" ->
            CountryTheme(Motif.SAND, c(0xFFD54F, 0xFFE082, 0xFFF8E1), Color(0xFFFFD54F), rises = true)
        else -> CountryTheme(Motif.CONFETTI, flag, flag.firstOrNull { it != Color.White } ?: VoltaColors.Connected)
    }
}

private class Particle(val x: Float, val phase: Float, val speed: Float, val size: Float, val spin: Float, val sway: Float, val color: Int)

/** Drifting motif particles; fades in on arrival, out on disconnect. */
@Composable
fun MotifLayer(theme: CountryTheme, active: Boolean, key: Any?, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(0f) }
    var time by remember { mutableFloatStateOf(0f) }
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
        if (!active) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1e9f }
    }
    if (alpha.value <= 0f) return
    val paths = remember { MotifPaths() }
    Canvas(modifier.fillMaxSize()) {
        val unit = size.minDimension / 26f
        for (p in particles) {
            val travel = ((p.phase + time * p.speed) % 1f)
            val yFrac = if (theme.rises) 1.1f - travel * 1.2f else travel * 1.2f - 0.1f
            val x = (p.x + sin((time * 0.8f + p.phase * 6f).toDouble()).toFloat() * p.sway) * size.width
            val y = yFrac * size.height
            // Fade near the edges so particles never pop in or out.
            val edge = (minOf(travel, 1f - travel) * 8f).coerceIn(0f, 1f)
            val color = theme.colors[p.color % theme.colors.size].copy(alpha = 0.85f * alpha.value * edge)
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
        Motif.SAKURA -> drawPath(p.petal, color)
        Motif.PETALS -> drawPath(p.roundPetal, color)
        Motif.MAPLE -> drawPath(p.maple, color)
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
