package com.wanderpage.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A page's background. Colours are ARGB. Stored as JSON on the page row. */
@Serializable
sealed interface Background {
    @Serializable
    @SerialName("paper")
    data class Paper(val textureId: String = "cream", val tint: Long? = null) : Background

    @Serializable
    @SerialName("solid")
    data class Solid(val colour: Long) : Background

    /** offsetX / offsetY are in page units, like all other geometry. */
    @Serializable
    @SerialName("photo")
    data class Photo(
        val uri: String,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val zoom: Float = 1f,
        val blur: Float = 0f,
        val dim: Float = 0f,
    ) : Background
}
