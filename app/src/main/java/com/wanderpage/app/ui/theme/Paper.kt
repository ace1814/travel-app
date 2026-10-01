package com.wanderpage.app.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** Cream paper with a light grain of fibre specks. The specks are seeded so they don't move between frames. */
fun Modifier.paperBackground(): Modifier = drawWithCache {
    val random = Random(7)
    val specks = List(420) {
        Triple(Offset(random.nextFloat() * size.width, random.nextFloat() * size.height), random.nextFloat(), random.nextFloat())
    }
    val wash = Brush.verticalGradient(listOf(Paper, PaperDeep))
    val fibre = Color(0xFF7A6248)
    onDrawBehind {
        drawRect(wash)
        for ((centre, strength, size) in specks) {
            drawCircle(fibre.copy(alpha = 0.03f + strength * 0.06f), radius = 0.5.dp.toPx() + size * 1.dp.toPx(), center = centre)
        }
    }
}
