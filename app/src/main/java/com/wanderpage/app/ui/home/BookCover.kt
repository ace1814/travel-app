package com.wanderpage.app.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.wanderpage.app.R
import com.wanderpage.app.data.model.CoverStyle
import com.wanderpage.app.ui.theme.Handwriting
import kotlin.random.Random

/** Width over height of every cover on the shelf. */
const val COVER_ASPECT = 0.74f

private enum class CoverTexture { LEATHER, SPECKLE, WEAVE, PLAIN, FILM }

private class CoverLook(val base: Color, val shade: Color, val ink: Color, val texture: CoverTexture)

/** The cover's main colour, for the board that shows around an open book. */
fun CoverStyle.boardColour(): Color = look().shade

private fun CoverStyle.look(): CoverLook = when (this) {
    CoverStyle.LEATHER -> CoverLook(Color(0xFF7A4330), Color(0xFF4F281A), Color(0xFFEBCB8F), CoverTexture.LEATHER)
    CoverStyle.KRAFT -> CoverLook(Color(0xFFCDAC80), Color(0xFFB08D5E), Color(0xFF3A2A1A), CoverTexture.SPECKLE)
    CoverStyle.LINEN -> CoverLook(Color(0xFFE6DECD), Color(0xFFCFC3AB), Color(0xFF4A4034), CoverTexture.WEAVE)
    CoverStyle.BLUSH -> CoverLook(Color(0xFFEBC9C2), Color(0xFFD7A69F), Color(0xFF5A2E2A), CoverTexture.PLAIN)
    CoverStyle.SAGE -> CoverLook(Color(0xFFAEBC9F), Color(0xFF8A9D7A), Color(0xFF26331F), CoverTexture.WEAVE)
    CoverStyle.NAVY -> CoverLook(Color(0xFF2C3F58), Color(0xFF17243A), Color(0xFFEADFC8), CoverTexture.LEATHER)
    CoverStyle.FILM -> CoverLook(Color(0xFF262422), Color(0xFF0F0E0D), Color(0xFFF1E9D8), CoverTexture.FILM)
    CoverStyle.PHOTO -> CoverLook(Color(0xFF5B5148), Color(0xFF2E2823), Color(0xFFFFFFFF), CoverTexture.PLAIN)
}

@get:StringRes
val CoverStyle.labelRes: Int
    get() = when (this) {
        CoverStyle.LEATHER -> R.string.cover_leather
        CoverStyle.KRAFT -> R.string.cover_kraft
        CoverStyle.LINEN -> R.string.cover_linen
        CoverStyle.BLUSH -> R.string.cover_blush
        CoverStyle.SAGE -> R.string.cover_sage
        CoverStyle.NAVY -> R.string.cover_navy
        CoverStyle.FILM -> R.string.cover_film
        CoverStyle.PHOTO -> R.string.cover_photo
    }

/**
 * A closed book seen from the front: the cover with its spine on the left, and the page block
 * showing along the right and bottom edges. Sizes itself from its width.
 */
@Composable
fun BookCover(
    title: String,
    placeLine: String,
    dateLine: String?,
    style: CoverStyle,
    imageUri: String?,
    modifier: Modifier = Modifier,
    /** False lets the caller size the cover freely, as the book view does while the cover swings open. */
    lockAspect: Boolean = true,
) {
    val look = style.look()
    BoxWithConstraints(if (lockAspect) modifier.aspectRatio(COVER_ASPECT) else modifier) {
        val unit = maxWidth / 100
        val pages = unit * 3.5f
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawPageBlock(pages.toPx(), unit.toPx()) }
                .padding(end = pages, bottom = pages)
                .clip(RoundedCornerShape(topStart = unit * 1.5f, bottomStart = unit * 1.5f, topEnd = unit * 4, bottomEnd = unit * 4))
                .background(Brush.linearGradient(listOf(look.base, look.shade))),
        ) {
            if (style == CoverStyle.PHOTO && imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0x99000000)))))
            }
            Box(Modifier.fillMaxSize().coverTexture(look.texture, look.ink))
            CoverLabel(title, placeLine, dateLine, look.ink, unit)
        }
    }
}

@Composable
private fun CoverLabel(title: String, placeLine: String, dateLine: String?, ink: Color, unit: Dp) {
    // Type scales with the cover so the same composable works on the shelf and in the small pickers.
    val scale = unit.value
    Column(
        modifier = Modifier.fillMaxSize().padding(start = unit * 17, end = unit * 9, top = unit * 12, bottom = unit * 10),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = ink,
            fontFamily = Handwriting,
            fontWeight = FontWeight.Bold,
            fontSize = (scale * 16).sp,
            lineHeight = (scale * 18).sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (placeLine.isNotEmpty()) {
            Spacer(Modifier.height(unit * 5))
            Box(Modifier.width(unit * 18).height(1.dp).background(ink.copy(alpha = 0.55f)))
            Spacer(Modifier.height(unit * 5))
            Text(
                text = placeLine.uppercase(),
                color = ink.copy(alpha = 0.9f),
                fontFamily = FontFamily.Serif,
                fontSize = (scale * 5.6f).sp,
                lineHeight = (scale * 7.5f).sp,
                letterSpacing = (scale * 0.9f).sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (dateLine != null) {
            Spacer(Modifier.height(unit * 3))
            Text(
                text = dateLine,
                color = ink.copy(alpha = 0.75f),
                fontFamily = FontFamily.Serif,
                fontSize = (scale * 5.2f).sp,
                lineHeight = (scale * 7).sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The cream page block peeking out past the cover's right and bottom edges. */
private fun DrawScope.drawPageBlock(depth: Float, unit: Float) {
    val inset = unit * 2
    val block = Size(size.width - inset, size.height - inset)
    drawRoundRect(Color(0x33000000), Offset(inset + unit, inset + unit * 1.5f), block, CornerRadius(unit * 3))
    drawRoundRect(Color(0xFFF1E7D3), Offset(inset, inset), block, CornerRadius(unit * 3))
    val line = Color(0x26000000)
    for (i in 1..3) {
        val step = depth * i / 4
        drawLine(line, Offset(size.width - step, inset + unit * 4), Offset(size.width - step, size.height - depth), strokeWidth = 1f)
        drawLine(line, Offset(inset + unit * 4, size.height - step), Offset(size.width - depth, size.height - step), strokeWidth = 1f)
    }
}

/** Spine shading plus the material's surface detail. Seeded, so the grain is stable between frames. */
private fun Modifier.coverTexture(texture: CoverTexture, ink: Color): Modifier = drawWithCache {
    val spine = size.width * 0.11f
    val random = Random(11)
    val specks = if (texture == CoverTexture.SPECKLE || texture == CoverTexture.LEATHER) {
        List(160) { Offset(random.nextFloat() * size.width, random.nextFloat() * size.height) to random.nextFloat() }
    } else {
        emptyList()
    }
    val spineBrush = Brush.horizontalGradient(
        0f to Color(0x59000000),
        0.6f to Color(0x26000000),
        1f to Color(0x00000000),
        endX = spine,
    )
    val sheen = Brush.linearGradient(listOf(Color(0x24FFFFFF), Color(0x00FFFFFF), Color(0x1A000000)))
    onDrawBehind {
        when (texture) {
            CoverTexture.LEATHER -> specks.forEach { (centre, strength) ->
                drawCircle(Color.Black.copy(alpha = 0.05f + strength * 0.08f), radius = size.width * (0.004f + strength * 0.006f), center = centre)
            }
            CoverTexture.SPECKLE -> specks.forEach { (centre, strength) ->
                drawCircle(Color(0xFF5A4126).copy(alpha = 0.10f + strength * 0.18f), radius = size.width * (0.003f + strength * 0.005f), center = centre)
            }
            CoverTexture.WEAVE -> {
                val gap = size.width / 34
                val thread = Color(0x14000000)
                var x = 0f
                while (x < size.width) {
                    drawLine(thread, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += gap
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(thread, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += gap
                }
            }
            CoverTexture.FILM -> {
                // Sprocket holes down both long edges, like a strip of 35 mm film.
                val hole = Size(size.width * 0.05f, size.width * 0.07f)
                val gap = hole.height * 1.9f
                var y = gap / 2
                while (y + hole.height < size.height) {
                    drawRoundRect(ink.copy(alpha = 0.85f), Offset(spine + size.width * 0.03f, y), hole, CornerRadius(hole.width * 0.25f))
                    drawRoundRect(ink.copy(alpha = 0.85f), Offset(size.width - hole.width - size.width * 0.04f, y), hole, CornerRadius(hole.width * 0.25f))
                    y += gap
                }
            }
            CoverTexture.PLAIN -> Unit
        }
        drawRect(sheen)
        drawRect(spineBrush, size = Size(spine, size.height))
        drawLine(Color(0x33FFFFFF), Offset(spine, 0f), Offset(spine, size.height), strokeWidth = 1.5f)
        drawLine(Color(0x40000000), Offset(spine + 2f, 0f), Offset(spine + 2f, size.height), strokeWidth = 1f)
    }
}
