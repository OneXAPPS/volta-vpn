package com.voltavpn.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp

/**
 * The Volta "V", redrawn as vector from the logo's spirit: a broad white-lavender left
 * stroke, a slanted violet right stroke, and an electric bolt crossing the bottom.
 *
 * @param energy 0 = dormant (greyed, no bolt), 1 = fully lit.
 * @param bolt   how much of the bolt is drawn (0..1), for draw-on animations.
 */
@Composable
fun VMark(modifier: Modifier = Modifier, energy: Float = 1f, bolt: Float = 1f, glow: Boolean = true) {
    val shapes = remember { VShapes() }
    Canvas(modifier) {
        val s = minOf(size.width, size.height) / 100f
        val dx = (size.width - 100f * s) / 2
        val dy = (size.height - 100f * s) / 2
        translateScaled(dx, dy, s) { drawV(shapes, energy, bolt, glow) }
    }
}

private inline fun DrawScope.translateScaled(dx: Float, dy: Float, s: Float, block: DrawScope.() -> Unit) {
    drawContext.transform.translate(dx, dy)
    scale(s, pivot = Offset.Zero) { block() }
    drawContext.transform.translate(-dx, -dy)
}

/** Shapes on a 100 x 100 grid. */
private class VShapes {
    val left = Path().apply {
        moveTo(10f, 16f)
        lineTo(34f, 16f)
        lineTo(58f, 74f)
        lineTo(50f, 90f)
        quadraticTo(47f, 95f, 43f, 89f)
        close()
    }
    val right = Path().apply {
        moveTo(52f, 90f)
        lineTo(80f, 14f)
        quadraticTo(82f, 9f, 87f, 9f)
        lineTo(96f, 9f)
        lineTo(64f, 90f)
        quadraticTo(61f, 96f, 56f, 95f)
        close()
    }
    val bolt = Path().apply {
        moveTo(4f, 74f)
        lineTo(40f, 63f)
        lineTo(36f, 70f)
        lineTo(70f, 59f)
        lineTo(66f, 66f)
        lineTo(97f, 56f)
    }
    val measure = PathMeasure().apply { setPath(bolt, false) }
}

private val Dormant = Color(0xFF5B6694)

private fun DrawScope.drawV(p: VShapes, energy: Float, bolt: Float, glow: Boolean) {
    val e = energy.coerceIn(0f, 1f)
    val leftBrush = Brush.linearGradient(
        listOf(lerp(Dormant, Color.White, e), lerp(Dormant, VoltaColors.VioletLight, e)),
        start = Offset(10f, 16f), end = Offset(55f, 90f),
    )
    val rightBrush = Brush.linearGradient(
        listOf(lerp(Dormant, VoltaColors.Violet, e), lerp(Dormant, Color(0xFF6A3BFF), e)),
        start = Offset(90f, 9f), end = Offset(58f, 95f),
    )
    drawPath(p.left, leftBrush)
    drawPath(p.right, rightBrush)

    if (bolt <= 0f || e <= 0f) return
    val segment = Path()
    p.measure.getSegment(0f, p.measure.length * bolt.coerceIn(0f, 1f), segment, true)
    if (glow) drawPath(
        segment, VoltaColors.Bolt.copy(alpha = 0.35f * e),
        style = Stroke(width = 9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    drawPath(
        segment, VoltaColors.Bolt.copy(alpha = e),
        style = Stroke(width = 4.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    drawPath(
        segment, Color.White.copy(alpha = e),
        style = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
