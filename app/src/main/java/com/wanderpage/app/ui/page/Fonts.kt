package com.wanderpage.app.ui.page

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.wanderpage.app.R

/**
 * A bundled face a text element can use (T-2). [sizeFactor] evens out how big each face looks at the
 * same font size, so switching fonts doesn't make the text jump.
 */
class PageFont(val id: String, val label: String, val family: FontFamily, val sizeFactor: Float = 1f)

val PageFonts: List<PageFont> = listOf(
    PageFont("caveat", "Caveat", FontFamily(Font(R.font.caveat)), 1.1f),
    PageFont("dancing_script", "Dancing Script", FontFamily(Font(R.font.dancing_script))),
    PageFont("homemade_apple", "Homemade Apple", FontFamily(Font(R.font.homemade_apple)), 0.72f),
    PageFont("sacramento", "Sacramento", FontFamily(Font(R.font.sacramento)), 1.1f),
    PageFont("great_vibes", "Great Vibes", FontFamily(Font(R.font.great_vibes)), 1.05f),
    PageFont("la_belle_aurore", "La Belle Aurore", FontFamily(Font(R.font.la_belle_aurore)), 0.9f),
    PageFont("nothing_you_could_do", "Nothing You Could Do", FontFamily(Font(R.font.nothing_you_could_do)), 0.85f),
    PageFont("reenie_beanie", "Reenie Beanie", FontFamily(Font(R.font.reenie_beanie)), 1.2f),
    PageFont("shadows_into_light", "Shadows Into Light", FontFamily(Font(R.font.shadows_into_light)), 0.95f),
    PageFont("kalam", "Kalam", FontFamily(Font(R.font.kalam)), 0.85f),
    PageFont("special_elite", "Special Elite", FontFamily(Font(R.font.special_elite)), 0.78f),
    PageFont("playfair_italic", "Playfair Italic", FontFamily(Font(R.font.playfair_display_italic)), 0.82f),
)

const val DEFAULT_FONT_ID = "caveat"

fun pageFont(id: String): PageFont = PageFonts.firstOrNull { it.id == id } ?: PageFonts.first()
