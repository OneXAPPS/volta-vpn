package com.voltavpn.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/** Colourways of the V from the Volta UI kit. */
enum class VStyle {
    /** Silver left stroke, violet blade: the brand mark itself. */
    BRAND,
    /** Cyan-to-violet: ready / connecting. */
    READY,
    /** Lime: connected. */
    CONNECTED,
    /** Grey: unavailable. */
    OFF,
}

/**
 * The Volta V, traced from the brand artwork: a broad left stroke with a rounded foot and a
 * sharp blade rising to the right. Optionally wrapped in the electric orbit with its bolt.
 *
 * @param orbit how much of the orbit + bolt is drawn (0..1); 0 hides it.
 */
@Composable
fun VMark(modifier: Modifier = Modifier, style: VStyle = VStyle.BRAND, orbit: Float = 0f) {
    val shapes = remember { VShapes() }
    Canvas(modifier) {
        // Artwork grid: V spans x 0..100, y 0..70; the orbit reaches a bit beyond.
        val boxW = if (orbit > 0f) 112f else 100f
        val boxH = if (orbit > 0f) 76f else 70f
        val s = minOf(size.width / boxW, size.height / boxH)
        val ox = (size.width - 100f * s) / 2
        val oy = (size.height - 70f * s) / 2
        translate(ox, oy) {
            scale(s, pivot = Offset.Zero) { drawV(shapes, style, orbit) }
        }
    }
}

private class VShapes {
    val left = Path().apply {
        moveTo(0f, 9f)
        lineTo(21f, 9f)
        quadraticTo(24f, 9f, 25.6f, 11.2f)
        lineTo(51.5f, 43.5f)
        lineTo(47.8f, 62f)
        quadraticTo(45f, 69.5f, 38.5f, 64.5f)
        close()
    }
    val right = Path().apply {
        moveTo(47.5f, 39f)
        lineTo(64f, 13.5f)
        quadraticTo(70f, 4f, 80f, 2.5f)
        lineTo(100f, 0f)
        lineTo(76.5f, 35f)
        lineTo(56f, 66f)
        quadraticTo(51f, 72f, 45f, 66f)
        close()
    }
    // The orbit is an ellipse tilted like the artwork's; its front half crosses the V.
    val orbitRect = Rect(Offset(-3f, 26f), Offset(103f, 56f))
    val bolt = Path().apply {
        moveTo(8f, 58f)
        lineTo(42f, 50f)
        lineTo(38f, 56f)
        lineTo(76f, 42f)
        lineTo(71f, 49f)
        lineTo(97f, 36f)
    }
    val boltMeasure = PathMeasure().apply { setPath(bolt, false) }
}

private val Outline = Color(0xFF14164A)

private fun DrawScope.drawV(p: VShapes, style: VStyle, orbit: Float) {
    val (l1, l2, r1, r2) = when (style) {
        VStyle.BRAND -> listOf(Color(0xFFF2F4F8), Color(0xFF9AA8D2), Color(0xFFD27BFF), Color(0xFF5530F8))
        VStyle.READY -> listOf(Color(0xFF7FE6FF), Color(0xFF2F7BFF), Color(0xFF5A8CFF), Color(0xFF9B4DFF))
        VStyle.CONNECTED -> listOf(Color(0xFFD4FF7A), Color(0xFF4FD62A), Color(0xFFA6F45C), Color(0xFF2DB52A))
        VStyle.OFF -> listOf(Color(0xFFD7DAE3), Color(0xFF9096A8), Color(0xFFBFC3CF), Color(0xFF7E8496))
    }
    val rot = -9f
    if (orbit > 0f) rotate(rot, pivot = p.orbitRect.center) { drawOrbitBack(p, orbit) }
    drawPath(p.right, Brush.linearGradient(listOf(r1, r2), start = Offset(95f, 0f), end = Offset(52f, 68f)))
    drawPath(p.right, Outline, style = Stroke(0.7f, join = StrokeJoin.Round))
    drawPath(p.left, Brush.linearGradient(listOf(l1, l2), start = Offset(2f, 9f), end = Offset(46f, 66f)))
    drawPath(p.left, Outline, style = Stroke(0.7f, join = StrokeJoin.Round))
    // Gloss on the left stroke, like the artwork's highlight.
    drawLine(Color.White.copy(alpha = 0.55f), Offset(3f, 10.5f), Offset(37f, 58f), strokeWidth = 0.8f)
    if (orbit > 0f) rotate(rot, pivot = p.orbitRect.center) { drawOrbitFront(p, orbit) }
}

private val Electric = Color(0xFF3DB8FF)

private fun DrawScope.drawOrbitBack(p: VShapes, orbit: Float) {
    drawArc(Electric.copy(alpha = 0.35f), 180f, 180f * orbit, false, p.orbitRect.topLeft, p.orbitRect.size,
        style = Stroke(5f, cap = StrokeCap.Round))
    drawArc(Color(0xFF9FE6FF), 180f, 180f * orbit, false, p.orbitRect.topLeft, p.orbitRect.size,
        style = Stroke(1.6f, cap = StrokeCap.Round))
}

private fun DrawScope.drawOrbitFront(p: VShapes, orbit: Float) {
    drawArc(Electric.copy(alpha = 0.35f), 0f, 180f * orbit, false, p.orbitRect.topLeft, p.orbitRect.size,
        style = Stroke(6f, cap = StrokeCap.Round))
    drawArc(Color(0xFFBFF0FF), 0f, 180f * orbit, false, p.orbitRect.topLeft, p.orbitRect.size,
        style = Stroke(2f, cap = StrokeCap.Round))
    val seg = Path()
    p.boltMeasure.getSegment(0f, p.boltMeasure.length * orbit.coerceIn(0f, 1f), seg, true)
    drawPath(seg, Electric.copy(alpha = 0.45f), style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(seg, Color.White, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
