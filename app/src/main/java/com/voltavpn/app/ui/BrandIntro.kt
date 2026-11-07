package com.voltavpn.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animated logo shown once per launch (~1.3 s): the V springs in and lights up, its bolt
 * draws itself while an electric orbit sweeps around it, then the wordmark and Free tag appear.
 */
@Composable
fun BrandIntro(onFinished: () -> Unit) {
    val emblem = remember { Animatable(0f) }
    val orbit = remember { Animatable(0f) }
    val words = remember { Animatable(0f) }
    val free = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            listOf(
                async { emblem.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) },
                async { delay(150); orbit.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) },
                async { delay(450); words.animateTo(1f, tween(450)) },
                async { delay(750); free.animateTo(1f, spring(dampingRatio = 0.4f)) },
            ).awaitAll()
        }
        delay(250)
        onFinished()
    }

    Box(Modifier.fillMaxSize().background(VoltaColors.Background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // Soft electric glow behind the emblem.
                Canvas(Modifier.size(260.dp).alpha(emblem.value.coerceIn(0f, 1f))) {
                    drawCircle(Brush.radialGradient(listOf(VoltaColors.Bolt.copy(alpha = 0.45f), Color.Transparent)))
                }
                // The V lights up, then its bolt draws itself across.
                VMark(
                    Modifier.size(width = 230.dp, height = 160.dp)
                        .scale(0.7f + 0.3f * emblem.value).alpha(emblem.value.coerceIn(0f, 1f)),
                    style = VStyle.BRAND,
                    orbit = orbit.value.coerceAtLeast(0.001f),
                )
                // One orbit of an electric arc, fading as it completes.
                Canvas(Modifier.size(230.dp).graphicsLayer { rotationX = 62f }) {
                    val p = orbit.value
                    if (p in 0.001f..0.999f) drawArc(
                        brush = Brush.sweepGradient(listOf(Color.Transparent, VoltaColors.Bolt, Color.White)),
                        startAngle = -90f + 360f * p, sweepAngle = 120f, useCenter = false,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
                        alpha = 1f - p * 0.6f,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Wordmark(
                size = 34,
                modifier = Modifier.alpha(words.value).graphicsLayer { translationY = (1f - words.value) * 40f },
            )
            Spacer(Modifier.height(10.dp))
            FreeLeaf(
                Modifier.scale(free.value).alpha(free.value.coerceIn(0f, 1f)),
                height = 34.dp,
            )
        }
    }
}
