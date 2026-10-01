package com.wanderpage.app.ui.page

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.random.Random

enum class PaperKind { GRAIN, KRAFT, WATERCOLOUR, GRID, DOT, LINED }

/** A bundled page background (B-1). All of them are drawn, not stored, so they stay sharp at any export size. */
class PaperPreset(val id: String, val label: String, val base: Color, val kind: PaperKind, val accent: Color = Color(0xFF8A7458))

val PaperPresets: List<PaperPreset> = listOf(
    PaperPreset("cream", "Cream", Color(0xFFF6EEDD), PaperKind.GRAIN),
    PaperPreset("ivory", "Ivory", Color(0xFFFBF8F0), PaperKind.GRAIN),
    PaperPreset("kraft", "Kraft", Color(0xFFCBAA7E), PaperKind.KRAFT, Color(0xFF6B4E2C)),
    PaperPreset("recycled", "Recycled", Color(0xFFDDD8CC), PaperKind.KRAFT, Color(0xFF5E5A50)),
    PaperPreset("blush", "Blush", Color(0xFFF3DDD6), PaperKind.GRAIN),
    PaperPreset("sage", "Sage", Color(0xFFDCE3D2), PaperKind.GRAIN),
    PaperPreset("sky", "Sky", Color(0xFFDCE8EE), PaperKind.GRAIN),
    PaperPreset("butter", "Butter", Color(0xFFF7EBC0), PaperKind.GRAIN),
    PaperPreset("wash_peach", "Peach wash", Color(0xFFFBF3E8), PaperKind.WATERCOLOUR, Color(0xFFF0A98A)),
    PaperPreset("wash_blue", "Blue wash", Color(0xFFF7F6F0), PaperKind.WATERCOLOUR, Color(0xFF8DB4CF)),
    PaperPreset("wash_green", "Green wash", Color(0xFFF8F6EC), PaperKind.WATERCOLOUR, Color(0xFF9DBB8F)),
    PaperPreset("wash_lilac", "Lilac wash", Color(0xFFF9F5F2), PaperKind.WATERCOLOUR, Color(0xFFB7A3D0)),
    PaperPreset("grid_cream", "Cream grid", Color(0xFFF6EEDD), PaperKind.GRID, Color(0xFFC9B99C)),
    PaperPreset("grid_white", "White grid", Color(0xFFFCFBF7), PaperKind.GRID, Color(0xFFC7D3DE)),
    PaperPreset("grid_kraft", "Kraft grid", Color(0xFFD2B48A), PaperKind.GRID, Color(0xFFA98658)),
    PaperPreset("dot_cream", "Cream dots", Color(0xFFF6EEDD), PaperKind.DOT, Color(0xFFB3A081)),
    PaperPreset("dot_white", "White dots", Color(0xFFFCFBF7), PaperKind.DOT, Color(0xFFAEB7C0)),
    PaperPreset("lined_cream", "Cream lined", Color(0xFFF6EEDD), PaperKind.LINED, Color(0xFFBFAE90)),
    PaperPreset("lined_white", "White lined", Color(0xFFFCFBF7), PaperKind.LINED, Color(0xFFA9C0D6)),
    PaperPreset("lined_legal", "Legal pad", Color(0xFFF8EFA8), PaperKind.LINED, Color(0xFF9FB3A0)),
)

val SolidColours: List<Long> = listOf(
    0xFFFFFFFF, 0xFF1F1B18, 0xFFE9D8C3, 0xFFD98E73, 0xFF9DB4A0, 0xFF7E9BB5, 0xFFE8C96A, 0xFFC9A6C4, 0xFF2C3F58, 0xFF6B3A2A,
)

fun paperPreset(id: String): PaperPreset = PaperPresets.firstOrNull { it.id == id } ?: PaperPresets.first()

/**
 * Draws the preset over the whole scope. [unit] is one page unit in pixels, so line spacing and grain
 * stay in proportion from a thumbnail up to the export.
 */
fun DrawScope.drawPaper(preset: PaperPreset, unit: Float) {
    drawRect(preset.base)
    val random = Random(preset.id.hashCode())
    when (preset.kind) {
        PaperKind.GRAIN -> drawSpecks(random, 260, preset.accent, 0.05f, 0.07f, unit)
        PaperKind.KRAFT -> {
            drawSpecks(random, 520, preset.accent, 0.10f, 0.20f, unit)
            // Long faint fibres, the way kraft and recycled paper show them.
            repeat(60) {
                val start = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
                val length = (20 + random.nextFloat() * 60) * unit
                val tilt = (random.nextFloat() - 0.5f) * 0.6f
                drawLine(preset.accent.copy(alpha = 0.10f), start, start + Offset(length, length * tilt), strokeWidth = unit)
            }
        }
        PaperKind.WATERCOLOUR -> {
            repeat(7) {
                val centre = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
                val radius = (260 + random.nextFloat() * 420) * unit
                drawCircle(
                    Brush.radialGradient(listOf(preset.accent.copy(alpha = 0.34f), preset.accent.copy(alpha = 0f)), centre, radius),
                    radius,
                    centre,
                )
            }
            drawSpecks(random, 200, Color(0xFF8A7458), 0.03f, 0.05f, unit)
        }
        PaperKind.GRID -> {
            val gap = 45 * unit
            var x = gap
            while (x < size.width) {
                drawLine(preset.accent, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5f * unit)
                x += gap
            }
            var y = gap
            while (y < size.height) {
                drawLine(preset.accent, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f * unit)
                y += gap
            }
        }
        PaperKind.DOT -> {
            val gap = 45 * unit
            var y = gap
            while (y < size.height) {
                var x = gap
                while (x < size.width) {
                    drawCircle(preset.accent, 3f * unit, Offset(x, y))
                    x += gap
                }
                y += gap
            }
        }
        PaperKind.LINED -> {
            val gap = 64 * unit
            var y = 150 * unit
            while (y < size.height) {
                drawLine(preset.accent, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f * unit)
                y += gap
            }
            drawLine(Color(0xFFD98A7E), Offset(110 * unit, 0f), Offset(110 * unit, size.height), strokeWidth = 2f * unit)
        }
    }
}

private fun DrawScope.drawSpecks(random: Random, count: Int, colour: Color, minAlpha: Float, spread: Float, unit: Float) {
    repeat(count) {
        val centre = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
        drawCircle(colour.copy(alpha = minAlpha + random.nextFloat() * spread), (0.8f + random.nextFloat() * 1.8f) * unit, centre)
    }
}

/** A small preview of a paper for the picker; the same drawing at a coarser unit. */
fun DrawScope.drawPaperSwatch(preset: PaperPreset) = drawPaper(preset, size.width / 360f)
