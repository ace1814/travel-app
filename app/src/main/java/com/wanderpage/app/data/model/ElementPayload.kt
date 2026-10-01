package com.wanderpage.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class TextAlignment { START, CENTER, END }
enum class TextBoxStyle { NONE, HIGHLIGHTER, TORN_NOTE, GRID_CARD, LINED_CARD }
enum class StickerOutline { NONE, THIN, THICK }
enum class FrameStyle { PLAIN, POLAROID, FILM_STRIP, TORN, STAMP }
enum class ReceiptEdge { ZIGZAG, TORN, STRAIGHT }

/**
 * What an element is, apart from its placement. Stored as JSON on the element row.
 * Every size here (font size, widths, lengths) is in page units on the 1080-wide reference page.
 */
@Serializable
sealed interface ElementPayload {
    @Serializable
    @SerialName("text")
    data class Text(
        val text: String,
        val fontId: String,
        val colour: Long,
        val size: Float,
        val width: Float,
        val align: TextAlignment = TextAlignment.START,
        val lineSpacing: Float = 1.2f,
        val letterSpacing: Float = 0f,
        val boxStyle: TextBoxStyle = TextBoxStyle.NONE,
    ) : ElementPayload

    @Serializable
    @SerialName("cutout")
    data class CutOut(
        val originalUri: String,
        val maskUri: String,
        val width: Float,
        val outline: StickerOutline = StickerOutline.THIN,
        val shadow: Boolean = true,
    ) : ElementPayload

    @Serializable
    @SerialName("photo")
    data class Photo(
        val uri: String,
        val width: Float,
        val frameStyle: FrameStyle = FrameStyle.PLAIN,
        val caption: String? = null,
    ) : ElementPayload

    @Serializable
    @SerialName("receipt")
    data class Receipt(
        val uri: String,
        val width: Float,
        val edge: ReceiptEdge = ReceiptEdge.ZIGZAG,
    ) : ElementPayload

    @Serializable
    @SerialName("tape")
    data class Tape(
        val patternId: String,
        val length: Float,
        val width: Float,
    ) : ElementPayload
}
