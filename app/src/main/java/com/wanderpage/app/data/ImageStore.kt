package com.wanderpage.app.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A file in app-private storage, with the picture's width over height. */
data class StoredImage(val path: String, val aspect: Float)

/** Keeps every picture a page uses inside the app, so pages never depend on the gallery. */
class ImageStore(private val context: Context) {
    private val dir = File(context.filesDir, "images")

    /** Copies a picked or scanned picture in, upright and no larger than [maxSide] on its long edge. */
    suspend fun import(source: Uri, maxSide: Int = 2560): StoredImage? {
        val bitmap = decode(source, maxSide) ?: return null
        return save(bitmap, png = false)
    }

    /** Decodes through Coil, which applies EXIF rotation and downsamples while reading. */
    suspend fun decode(source: Any, maxSide: Int): Bitmap? {
        val request = ImageRequest.Builder(context).data(source).size(maxSide).allowHardware(false).build()
        return runCatching { SingletonImageLoader.get(context).execute(request).image?.toBitmap() }.getOrNull()
    }

    suspend fun save(bitmap: Bitmap, png: Boolean): StoredImage = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val file = File(dir, "${UUID.randomUUID()}.${if (png) "png" else "jpg"}")
        file.outputStream().use {
            bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it)
        }
        StoredImage(file.absolutePath, bitmap.width.toFloat() / bitmap.height)
    }
}
