package com.wanderpage.app.ui.page

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.random.Random

enum class TapeKind { SOLID, DIAGONAL, BARS, DOTS, GINGHAM, CHEVRON, FLORAL }

/** A washi tape pattern (S-1). */
class TapePattern(val id: String, val label: String, val base: Color, val accent: Color, val kind: TapeKind)

val TapePatterns: List<TapePattern> = listOf(
    TapePattern("kraft", "Kraft", Color(0xFFCFAE82), Color(0xFFB8935F), TapeKind.SOLID),
    TapePattern("cream", "Cream", Color(0xFFF2E6CC), Color(0xFFE2D2AE), TapeKind.SOLID),
    TapePattern("blush_stripe", "Blush stripe", Color(0xFFF4D4CC), Color(0xFFE2A097), TapeKind.DIAGONAL),
    TapePattern("mint_stripe", "Mint stripe", Color(0xFFD6E8DA), Color(0xFF9CC2A6), TapeKind.DIAGONAL),
    TapePattern("navy_stripe", "Navy stripe", Color(0xFFEDE6D6), Color(0xFF3B4F6B), TapeKind.DIAGONAL),
    TapePattern("mustard_bars", "Mustard bars", Color(0xFFF6E7B4), Color(0xFFDDB04A), TapeKind.BARS),
    TapePattern("sky_bars", "Sky bars", Color(0xFFDCEAF2), Color(0xFF8FB5CC), TapeKind.BARS),
    TapePattern("terracotta_dots", "Terracotta dots", Color(0xFFF1DACB), Color(0xFFB5533C), TapeKind.DOTS),
    TapePattern("white_dots", "White dots", Color(0xFF9DB4A0), Color(0xFFF8F4EA), TapeKind.DOTS),
    TapePattern("gold_dots", "Gold dots", Color(0xFF2F3B4C), Color(0xFFE2C275), TapeKind.DOTS),
    TapePattern("red_gingham", "Red gingham", Color(0xFFF8EFE6), Color(0xFFC96A5A), TapeKind.GINGHAM),
    TapePattern("blue_gingham", "Blue gingham", Color(0xFFF2F4F5), Color(0xFF7FA0BF), TapeKind.GINGHAM),
    TapePattern("green_gingham", "Green gingham", Color(0xFFF3F4EA), Color(0xFF8FA97C), TapeKind.GINGHAM),
    TapePattern("coral_chevron", "Coral chevron", Color(0xFFFBE9DF), Color(0xFFE38D75), TapeKind.CHEVRON),
    TapePattern("lilac_chevron", "Lilac chevron", Color(0xFFEFE8F3), Color(0xFFAF98C8), TapeKind.CHEVRON),
    TapePattern("pink_floral", "Pink floral", Color(0xFFFBEDEA), Color(0xFFE39AA3), TapeKind.FLORAL),
    TapePattern("yellow_floral", "Yellow floral", Color(0xFFEAF0E0), Color(0xFFE5BC4F), TapeKind.FLORAL),
)

fun tapePattern(id: String): TapePattern = TapePatterns.firstOrNull { it.id == id } ?: TapePatterns.first()

/** Draws a strip filling the scope, with torn short ends. Slightly see-through, like real washi. */
fun DrawScope.drawTape(pattern: TapePattern) {
    val h = size.height
    val w = size.width
    val random = Random(pattern.id.hashCode())
    val tooth = h / 7f
    val outline = Path().apply {
        moveTo(tooth, 0f)
        lineTo(w - tooth, 0f)
        var y = 0f
        while (y < h) {
            y = (y + tooth).coerceAtMost(h)
            lineTo(w - random.nextFloat() * tooth * 1.4f, y)
        }
        lineTo(tooth, h)
        while (y > 0f) {
            y = (y - tooth).coerceAtLeast(0f)
            lineTo(random.nextFloat() * tooth * 1.4f, y)
        }
        close()
    }
    clipPath(outline) {
        drawRect(pattern.base.copy(alpha = 0.9f))
        val accent = pattern.accent.copy(alpha = 0.9f)
        when (pattern.kind) {
            TapeKind.SOLID -> {
                var x = 0f
                while (x < w) {
                    drawLine(pattern.accent.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, h), strokeWidth = h * 0.02f)
                    x += h * 0.12f
                }
            }
            TapeKind.DIAGONAL -> {
                var x = -h
                while (x < w + h) {
                    drawLine(accent, Offset(x, h), Offset(x + h, 0f), strokeWidth = h * 0.16f)
                    x += h * 0.5f
                }
            }
            TapeKind.BARS -> {
                var x = h * 0.2f
                while (x < w) {
                    drawRect(accent, Offset(x, 0f), Size(h * 0.22f, h))
                    x += h * 0.6f
                }
            }
            TapeKind.DOTS -> {
                var column = 0
                var x = h * 0.3f
                while (x < w) {
                    val shift = if (column % 2 == 0) 0.3f else 0.7f
                    drawCircle(accent, h * 0.11f, Offset(x, h * shift))
                    x += h * 0.36f
                    column++
                }
            }
            TapeKind.GINGHAM -> {
                val band = h / 4f
                val soft = pattern.accent.copy(alpha = 0.45f)
                drawRect(soft, Offset(0f, band), Size(w, band))
                drawRect(soft, Offset(0f, band * 3), Size(w, band))
                var x = band
                while (x < w) {
                    drawRect(soft, Offset(x, 0f), Size(band, h))
                    x += band * 2
                }
            }
            TapeKind.CHEVRON -> {
                val step = h * 0.5f
                var x = -step
                while (x < w + step) {
                    val zig = Path().apply {
                        moveTo(x, 0f)
                        lineTo(x + step, h / 2f)
                        lineTo(x, h)
                    }
                    drawPath(zig, accent, style = Stroke(width = h * 0.13f))
                    x += step
                }
            }
            TapeKind.FLORAL -> {
                var x = h * 0.45f
                var up = true
                while (x < w) {
                    val centre = Offset(x, h * if (up) 0.36f else 0.64f)
                    for (petal in 0 until 5) {
                        val angle = petal * (2 * Math.PI / 5)
                        drawCircle(
                            accent,
                            h * 0.085f,
                            centre + Offset((Math.cos(angle) * h * 0.11f).toFloat(), (Math.sin(angle) * h * 0.11f).toFloat()),
                        )
                    }
                    drawCircle(pattern.base, h * 0.05f, centre)
                    x += h * 0.62f
                    up = !up
                }
            }
        }
        // A faint sheen along the top, so the strip reads as glossy paper tape and not a flat rectangle.
        drawRect(Color.White.copy(alpha = 0.10f), size = Size(w, h * 0.35f))
    }
}
