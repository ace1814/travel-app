package com.wanderpage.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The app's handwriting face. The system cursive for now; the bundled fonts (T-2) replace it. */
val Handwriting = FontFamily.Cursive

private val base = Typography()

val WanderTypography = Typography(
    displaySmall = TextStyle(fontFamily = Handwriting, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontFamily = Handwriting, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = FontFamily.Serif),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    labelLarge = base.labelLarge,
    labelMedium = base.labelMedium.copy(letterSpacing = 1.2.sp),
    labelSmall = base.labelSmall.copy(letterSpacing = 1.2.sp),
)
