package com.wanderpage.app.ui.page

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Paper with ragged edges. Each flag turns a straight edge into a torn one. The tear is seeded from the
 * size, so the same element always tears the same way.
 */
class TornShape(
    private val top: Boolean = true,
    private val bottom: Boolean = true,
    private val sides: Boolean = false,
    private val depth: Dp = 9.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val d = with(density) { depth.toPx() }
        val step = d * 1.6f
        val random = Random((size.width * 31 + size.height).toInt())
        fun wobble() = random.nextFloat() * d
        val path = Path().apply {
            moveTo(if (sides) wobble() else 0f, if (top) wobble() else 0f)
            var x = 0f
            while (x < size.width) {
                x = (x + step).coerceAtMost(size.width)
                lineTo(x, if (top) wobble() else 0f)
            }
            var y = 0f
            while (y < size.height) {
                y = (y + step).coerceAtMost(size.height)
                lineTo(size.width - if (sides) wobble() else 0f, y)
            }
            while (x > 0f) {
                x = (x - step).coerceAtLeast(0f)
                lineTo(x, size.height - if (bottom) wobble() else 0f)
            }
            while (y > 0f) {
                y = (y - step).coerceAtLeast(0f)
                lineTo(if (sides) wobble() else 0f, y)
            }
            close()
        }
        return Outline.Generic(path)
    }
}

/** A till receipt: straight sides and top, saw-tooth bottom where it was torn off the roll. */
class ZigZagShape(private val tooth: Dp = 14.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val t = with(density) { tooth.toPx() }
        val count = (size.width / t).toInt().coerceAtLeast(1)
        val step = size.width / count
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - t * 0.6f)
            for (i in count - 1 downTo 0) {
                lineTo(i * step + step / 2, size.height)
                lineTo(i * step, size.height - t * 0.6f)
            }
            close()
        }
        return Outline.Generic(path)
    }
}

/** A postage stamp: a rectangle with a row of half-circle bites along every edge. */
class StampShape(private val bite: Dp = 12.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { bite.toPx() } / 2
        val path = Path().apply { addRect(Rect(Offset.Zero, size)) }
        val holes = Path()
        fun row(length: Float, place: (Float) -> Offset) {
            val count = (length / (r * 3.2f)).toInt().coerceAtLeast(2)
            val gap = length / count
            for (i in 0 until count) holes.addOval(Rect(place(gap * (i + 0.5f)), r))
        }
        row(size.width) { Offset(it, 0f) }
        row(size.width) { Offset(it, size.height) }
        row(size.height) { Offset(0f, it) }
        row(size.height) { Offset(size.width, it) }
        return Outline.Generic(Path.combine(PathOperation.Difference, path, holes))
    }
}

/**
 * A soft shadow under a piece of paper, drawn as a few stacked translucent shapes.
 * Drawn by hand (not with elevation) so the on-screen page and the exported image match exactly.
 */
fun Modifier.paperShadow(shape: Shape? = null, strength: Float = 1f): Modifier = drawBehind {
    val layers = 4
    for (i in layers downTo 1) {
        val spread = i * 2.5.dp.toPx()
        val colour = Color.Black.copy(alpha = 0.055f * strength)
        translate(spread * 0.5f, spread) {
            if (shape == null) {
                drawRoundRect(colour, Offset(-spread / 2, -spread / 2), Size(size.width + spread, size.height + spread), CornerRadius(spread))
            } else {
                when (val outline = shape.createOutline(size, layoutDirection, this)) {
                    is Outline.Generic -> drawPath(outline.path, colour)
                    is Outline.Rectangle -> drawRect(colour)
                    is Outline.Rounded -> drawRect(colour)
                }
            }
        }
    }
}
