package com.wanderpage.app.data.model

/** Width of the reference page. All element geometry is stored in these units, never in pixels. */
const val PAGE_WIDTH_UNITS = 1080

/** Page formats and their export sizes (PRD §7). The height is in page units, which equal export pixels at 1×. */
enum class PageFormat(val heightUnits: Int, val ratioLabel: String) {
    POST_PORTRAIT(1350, "4:5"),
    POST_SQUARE(1080, "1:1"),
    POST_GRID(1440, "3:4"),
    STORY(1920, "9:16");

    val aspectRatio: Float get() = PAGE_WIDTH_UNITS.toFloat() / heightUnits
}
