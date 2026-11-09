package com.voltavpn.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import kotlin.math.min

private const val MAX_WIDTH_OF_SCREEN = 0.86f

/**
 * The destination's landmark, standing on the server card as if on the horizon. It only ever
 * uses the free space under the status text ([contentBottomInRoot]) and steps aside when there's
 * too little, so it never competes with what the user needs to read. It's still art rather than
 * motion, so it stays on screen under "reduce motion" too.
 */
@Composable
fun LandmarkLayer(
    countryCode: String?, tint: Color, active: Boolean,
    groundInRoot: Float?, contentBottomInRoot: Float?, modifier: Modifier = Modifier,
) {
    val shape = remember(countryCode) { countryCode?.uppercase()?.let { LANDMARK_SHAPES[it] } }
    val path = remember(shape) {
        shape?.let { PathParser().parsePathString(it.path).toPath().apply { fillType = PathFillType.EvenOdd } }
    }
    val reveal = remember { Animatable(0f) }
    val reducedMotion = rememberReducedMotion()
    var originY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(active, countryCode) {
        when {
            !active -> reveal.animateTo(0f, tween(300))
            reducedMotion -> reveal.snapTo(1f)
            else -> {
                reveal.snapTo(0f)
                reveal.animateTo(1f, tween(1000, delayMillis = 450, easing = FastOutSlowInEasing))
            }
        }
    }

    val rim = remember(tint) { tint.copy(alpha = 0.6f) }
    val fillTop = remember(tint) { lerp(VoltaColors.Background, tint, 0.16f) }

    Canvas(modifier.fillMaxSize().onGloballyPositioned { originY = it.positionInRoot().y }) {
        val ground = groundInRoot ?: return@Canvas
        val contentBottom = contentBottomInRoot ?: return@Canvas
        if (shape == null || path == null || reveal.value <= 0f) return@Canvas
        // A little below the card's top edge, so the card hides the base line.
        val groundY = ground - originY + 8.dp.toPx()
        val room = groundY - (contentBottom - originY) - 14.dp.toPx()
        if (room < 72.dp.toPx()) return@Canvas
        val factor = min(size.width * MAX_WIDTH_OF_SCREEN / shape.width, room / shape.height)
        val width = shape.width * factor
        val height = shape.height * factor
        val rise = (1f - reveal.value) * 28.dp.toPx()
        val left = (size.width - width) / 2
        val top = groundY - height + rise

        // Soft light behind it, so it reads as a place on a horizon rather than a sticker.
        val glowCentre = Offset(size.width / 2, top + height * 0.55f)
        val glowRadius = (width.coerceAtLeast(height)) * 0.95f
        drawCircle(
            Brush.radialGradient(
                listOf(tint.copy(alpha = 0.16f * reveal.value), Color.Transparent),
                center = glowCentre, radius = glowRadius,
            ),
            radius = glowRadius, center = glowCentre,
        )

        translate(left, top) {
            scale(factor, pivot = Offset.Zero) {
                drawPath(
                    path,
                    Brush.verticalGradient(listOf(fillTop, VoltaColors.Background), startY = 0f, endY = shape.height),
                    alpha = reveal.value,
                )
                drawPath(path, rim, alpha = reveal.value, style = Stroke(width = 1.1.dp.toPx() / factor))
            }
        }
    }
}
