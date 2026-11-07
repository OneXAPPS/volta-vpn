package com.voltavpn.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Border colours of the kit's glowing pills. */
object KitBorders {
    val Blue = listOf(Color(0xFF59D6FF), Color(0xFF2E7BFF))
    val Violet = listOf(Color(0xFFC07BFF), Color(0xFF7B3BFF))
}

private val PillFill = Brush.verticalGradient(listOf(Color(0xFF16247A), Color(0xFF0A1242)))

/**
 * The kit's pill: deep navy body, bright gradient rim and a soft outer glow.
 */
@Composable
fun GlowPill(
    modifier: Modifier = Modifier,
    border: List<Color> = KitBorders.Blue,
    radius: Dp = 50.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .drawBehind {
                // Outer glow: a few widening, fading rims.
                val r = radius.toPx().coerceAtMost(size.height / 2)
                for (i in 1..4) {
                    val g = i * 2.5.dp.toPx()
                    drawRoundRect(
                        border.first().copy(alpha = 0.10f), Offset(-g, -g), Size(size.width + 2 * g, size.height + 2 * g),
                        CornerRadius(r + g), style = Stroke(2.dp.toPx()),
                    )
                }
            }
            .clip(shape)
            .background(PillFill)
            .border(2.dp, Brush.linearGradient(border), shape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = border.first()), onClick = onClick,
                ) else Modifier
            ),
        content = content,
    )
}

/** The kit's text button ("Connect" style). */
@Composable
fun KitButton(text: String, modifier: Modifier = Modifier, violet: Boolean = false, onClick: () -> Unit) {
    GlowPill(modifier, if (violet) KitBorders.Violet else KitBorders.Blue, onClick = onClick) {
        Text(
            text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 22.dp, vertical = 10.dp),
        )
    }
}

/** App tile from the kit: the brand V on a glossy blue rounded square. */
@Composable
fun AppTile(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size)
            .clip(RoundedCornerShape(size * 0.26f))
            .background(Brush.linearGradient(listOf(Color(0xFF2F8BFF), Color(0xFF1B3FD6), Color(0xFF13238F))))
            .border(1.dp, Color(0x6699DDFF), RoundedCornerShape(size * 0.26f)),
        contentAlignment = Alignment.Center,
    ) {
        VMark(Modifier.size(size * 0.72f), VStyle.BRAND)
    }
}

/** The lime "Free" leaf from the kit, with its little spark lines. */
@Composable
fun FreeLeaf(modifier: Modifier = Modifier, height: Dp = 24.dp) {
    val leaf = remember {
        GenericShape { s, _ ->
            moveTo(0f, s.height * 0.35f)
            cubicTo(s.width * 0.35f, -s.height * 0.05f, s.width * 0.75f, s.height * 0.05f, s.width, s.height * 0.42f)
            cubicTo(s.width * 0.82f, s.height * 0.95f, s.width * 0.35f, s.height * 1.05f, s.width * 0.08f, s.height * 0.8f)
            close()
        }
    }
    Row(modifier.rotate(-8f), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(width = height * 2.6f, height = height)
                .clip(leaf)
                .background(Brush.linearGradient(listOf(Color(0xFFC6FF3D), Color(0xFF5BD400)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Free", color = Color(0xFF0B2A05), fontWeight = FontWeight.Black,
                fontSize = (height.value * 0.52f).sp, modifier = Modifier.padding(start = height * 0.2f),
            )
        }
        // Spark lines just outside the leaf's tip, as in the kit.
        Canvas(Modifier.size(height * 0.6f).offset(x = -height * 0.12f, y = -height * 0.22f)) {
            val c = Offset(0f, size.height)
            for (a in listOf(-80f, -50f, -20f)) {
                val rad = Math.toRadians(a.toDouble())
                val dir = Offset(kotlin.math.cos(rad).toFloat(), kotlin.math.sin(rad).toFloat())
                drawLine(Color(0xFF9BFF2E), c + dir * (size.width * 0.45f), c + dir * (size.width * 0.95f),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}

/** Small glossy lock from the kit. */
@Composable
fun KitLock(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val brush = Brush.verticalGradient(listOf(Color(0xFF7FE0FF), Color(0xFF2F7BFF)))
        drawArc(brush, 180f, 180f, false, Offset(w * 0.25f, h * 0.06f), Size(w * 0.5f, h * 0.55f),
            style = Stroke(w * 0.11f, cap = StrokeCap.Round))
        drawRoundRect(brush, Offset(w * 0.12f, h * 0.38f), Size(w * 0.76f, h * 0.58f), CornerRadius(w * 0.14f))
        drawCircle(Color(0xFF0A1242), radius = w * 0.08f, center = Offset(w / 2, h * 0.62f))
    }
}

/** The kit's glowing divider line. */
@Composable
fun GlowDivider(modifier: Modifier = Modifier, color: Color = Color(0xFF3DB8FF)) {
    Canvas(modifier) {
        val y = size.height / 2
        val brush = Brush.horizontalGradient(listOf(Color.Transparent, color, Color.White, color, Color.Transparent))
        drawLine(brush, Offset(0f, y), Offset(size.width, y), strokeWidth = 6.dp.toPx(), alpha = 0.25f)
        drawLine(brush, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
    }
}
