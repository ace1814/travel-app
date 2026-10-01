package com.wanderpage.app.ui.page

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.wanderpage.app.data.model.ElementPayload
import com.wanderpage.app.data.model.FrameStyle
import com.wanderpage.app.data.model.ReceiptEdge
import com.wanderpage.app.data.model.StickerOutline
import com.wanderpage.app.data.model.TextAlignment
import com.wanderpage.app.data.model.TextBoxStyle
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

// Everything in this file runs inside PageSurface, where 1.dp and 1.sp are one page unit.

private val CardPaper = Color(0xFFFDFBF4)
private val NotePaper = Color(0xFFFAF1DC)

@Composable
fun ElementContent(payload: ElementPayload) {
    when (payload) {
        is ElementPayload.Text -> TextElement(payload) { BasicText(payload.text, style = payload.textStyle()) }
        is ElementPayload.Photo -> PhotoElement(payload)
        is ElementPayload.CutOut -> CutOutElement(payload)
        is ElementPayload.Receipt -> ReceiptElement(payload)
        is ElementPayload.Tape -> Canvas(Modifier.size(payload.length.dp, payload.width.dp)) { drawTape(tapePattern(payload.patternId)) }
    }
}

fun ElementPayload.Text.textStyle(): TextStyle {
    val font = pageFont(fontId)
    return TextStyle(
        fontFamily = font.family,
        color = Color(colour),
        fontSize = (size * font.sizeFactor).sp,
        lineHeight = (size * lineSpacing).sp,
        letterSpacing = letterSpacing.sp,
        textAlign = when (align) {
            TextAlignment.START -> TextAlign.Start
            TextAlignment.CENTER -> TextAlign.Center
            TextAlignment.END -> TextAlign.End
        },
    )
}

/** The text block's box (T-4) around [content], which is the text itself or the editor's text field. */
@Composable
fun TextElement(payload: ElementPayload.Text, content: @Composable () -> Unit) {
    val line = payload.size * payload.lineSpacing
    val box = when (payload.boxStyle) {
        TextBoxStyle.NONE -> Modifier
        TextBoxStyle.HIGHLIGHTER -> Modifier
            .drawBehind {
                drawRoundRect(
                    Color(0xB3FFE66B),
                    Offset(0f, size.height * 0.12f),
                    Size(size.width, size.height * 0.80f),
                    CornerRadius(6.dp.toPx()),
                )
            }
            .padding(horizontal = 18.dp)
        TextBoxStyle.TORN_NOTE -> {
            val shape = TornShape(sides = true)
            Modifier.paperShadow(shape).clip(shape).background(NotePaper).padding(horizontal = 44.dp, vertical = 36.dp)
        }
        TextBoxStyle.GRID_CARD -> Modifier
            .paperShadow()
            .background(CardPaper)
            .drawBehind {
                val gap = 36.dp.toPx()
                val ink = Color(0xFFCBD8E3)
                var x = gap
                while (x < size.width) {
                    drawLine(ink, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
                    x += gap
                }
                var y = gap
                while (y < size.height) {
                    drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
                    y += gap
                }
            }
            .padding(36.dp)
        TextBoxStyle.LINED_CARD -> Modifier
            .paperShadow()
            .background(CardPaper)
            .drawBehind {
                // Rules sit under each line of text: the block starts 30 units down and lines are `line` apart.
                var y = 30.dp.toPx() + line.dp.toPx() * 0.9f
                while (y < size.height - 8.dp.toPx()) {
                    drawLine(Color(0xFFB9CCDD), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                    y += line.dp.toPx()
                }
                drawLine(Color(0xFFE39A8F), Offset(52.dp.toPx(), 0f), Offset(52.dp.toPx(), size.height), strokeWidth = 2.dp.toPx())
            }
            .padding(start = 72.dp, end = 32.dp, top = 30.dp, bottom = 30.dp)
    }
    Box(Modifier.widthIn(max = payload.width.dp).then(box)) { content() }
}

@Composable
private fun PhotoElement(payload: ElementPayload.Photo) {
    val w = payload.width
    when (payload.frameStyle) {
        FrameStyle.PLAIN -> PageImage(payload.uri, Modifier.paperShadow().size(w.dp, (w / payload.aspect).dp))
        FrameStyle.POLAROID -> {
            val border = w * 0.06f
            val inner = w - border * 2
            Column(Modifier.paperShadow().background(Color(0xFFFCFAF5)).padding(start = border.dp, end = border.dp, top = border.dp)) {
                PageImage(payload.uri, Modifier.size(inner.dp, (inner / payload.aspect).dp))
                Box(Modifier.width(inner.dp).height((w * 0.2f).dp), contentAlignment = Alignment.Center) {
                    if (!payload.caption.isNullOrBlank()) {
                        BasicText(
                            payload.caption,
                            style = TextStyle(
                                fontFamily = pageFont(DEFAULT_FONT_ID).family,
                                fontSize = (w * 0.085f).sp,
                                color = Color(0xFF3A3028),
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        FrameStyle.FILM_STRIP -> {
            val band = w * 0.11f
            val inner = w - w * 0.06f
            Box(
                Modifier
                    .paperShadow()
                    .background(Color(0xFF191817))
                    .drawWithContent {
                        drawContent()
                        // Sprocket holes along the top and bottom bands.
                        val hole = Size(band.dp.toPx() * 0.42f, band.dp.toPx() * 0.5f)
                        val gap = hole.width * 1.9f
                        var x = gap / 2
                        while (x + hole.width < size.width) {
                            val radius = CornerRadius(hole.width * 0.2f)
                            drawRoundRect(Color(0xFFF3EDE0), Offset(x, band.dp.toPx() * 0.25f), hole, radius)
                            drawRoundRect(Color(0xFFF3EDE0), Offset(x, size.height - band.dp.toPx() * 0.75f), hole, radius)
                            x += gap
                        }
                    }
                    .padding(horizontal = (w * 0.03f).dp, vertical = band.dp),
            ) {
                PageImage(payload.uri, Modifier.size(inner.dp, (inner / payload.aspect).dp))
            }
        }
        FrameStyle.TORN -> {
            val shape = TornShape(sides = true, depth = 12.dp)
            PageImage(payload.uri, Modifier.paperShadow(shape).clip(shape).size(w.dp, (w / payload.aspect).dp))
        }
        FrameStyle.STAMP -> {
            val border = w * 0.07f
            val inner = w - border * 2
            val shape = StampShape((w * 0.045f).dp)
            Box(Modifier.paperShadow(shape).clip(shape).background(Color(0xFFFCFAF5)).padding(border.dp)) {
                PageImage(payload.uri, Modifier.size(inner.dp, (inner / payload.aspect).dp))
            }
        }
    }
}

/**
 * A die-cut sticker (I-5). The white border is the image itself, tinted white and stamped in a ring
 * around the subject; the shadow is the same trick in black, offset down and to the right.
 */
@Composable
private fun CutOutElement(payload: ElementPayload.CutOut) {
    val settle = rememberLoadReporter(payload.imageUri)
    val painter = rememberAsyncImagePainter(
        model = File(payload.imageUri),
        onState = { if (it is AsyncImagePainter.State.Success || it is AsyncImagePainter.State.Error) settle() },
    )
    val ring = when (payload.outline) {
        StickerOutline.NONE -> 0f
        StickerOutline.THIN -> payload.width * 0.018f
        StickerOutline.THICK -> payload.width * 0.04f
    }
    Canvas(Modifier.size(payload.width.dp, (payload.width / payload.aspect).dp)) {
        with(painter) {
            if (payload.shadow) {
                val black = ColorFilter.tint(Color.Black)
                for (i in 1..3) {
                    val reach = (ring + i * 5f).dp.toPx()
                    translate(reach * 0.5f, reach) { draw(size, alpha = 0.09f, colorFilter = black) }
                }
            }
            if (ring > 0f) {
                val white = ColorFilter.tint(Color.White)
                val steps = 20
                for (i in 0 until steps) {
                    val angle = i * (2 * Math.PI / steps)
                    translate((cos(angle) * ring.dp.toPx()).toFloat(), (sin(angle) * ring.dp.toPx()).toFloat()) {
                        draw(size, colorFilter = white)
                    }
                }
            }
            draw(size)
        }
    }
}

/** A scanned receipt as a slip of thermal paper (R-2): warm off-white tone, a faint curl, a torn-off bottom. */
@Composable
private fun ReceiptElement(payload: ElementPayload.Receipt) {
    val shape = when (payload.edge) {
        ReceiptEdge.ZIGZAG -> ZigZagShape((payload.width * 0.04f).dp)
        ReceiptEdge.TORN -> TornShape(top = false, bottom = true, depth = (payload.width * 0.03f).dp)
        ReceiptEdge.STRAIGHT -> null
    }
    Box(
        Modifier
            .paperShadow(shape)
            .then(if (shape != null) Modifier.clip(shape) else Modifier)
            .drawWithContent {
                drawContent()
                // The curl: paper lifts a little at both long edges, so they catch less light.
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color(0x24000000),
                        0.12f to Color(0x00000000),
                        0.88f to Color(0x00000000),
                        1f to Color(0x1F000000),
                    ),
                )
            },
    ) {
        PageImage(
            payload.uri,
            Modifier.size(payload.width.dp, (payload.width / payload.aspect).dp),
            colorFilter = ColorFilter.tint(Color(0xFFFFF4DE), BlendMode.Multiply),
        )
    }
}
