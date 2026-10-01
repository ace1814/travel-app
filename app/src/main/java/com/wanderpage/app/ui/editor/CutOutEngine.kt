package com.wanderpage.app.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** One subject found in a photo: its pixels (transparent elsewhere) and where they sit in the photo. */
class SubjectPiece(val bitmap: Bitmap, val x: Int, val y: Int) {
    /** True if the photo point lands on this subject and not on the clear space around it. */
    fun covers(px: Int, py: Int): Boolean {
        val lx = px - x
        val ly = py - y
        return lx in 0 until bitmap.width && ly in 0 until bitmap.height && (bitmap.getPixel(lx, ly) ushr 24) > 40
    }
}

/** On-device background removal with ML Kit Subject Segmentation (I-2, I-3). */
object CutOutEngine {

    /** Finds every subject in the photo. Throws if the ML Kit module can't be used yet. */
    suspend fun findSubjects(photo: Bitmap): List<SubjectPiece> {
        val options = SubjectSegmenterOptions.Builder()
            .enableMultipleSubjects(SubjectSegmenterOptions.SubjectResultOptions.Builder().enableSubjectBitmap().build())
            .build()
        val segmenter = SubjectSegmentation.getClient(options)
        try {
            val result = segmenter.process(InputImage.fromBitmap(photo, 0)).await()
            return result.subjects.mapNotNull { subject -> subject.bitmap?.let { SubjectPiece(it, subject.startX, subject.startY) } }
        } finally {
            segmenter.close()
        }
    }

    /** The chosen subjects on a clear canvas the size of the photo. */
    suspend fun compose(width: Int, height: Int, pieces: List<SubjectPiece>): Bitmap = withContext(Dispatchers.Default) {
        val full = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(full)
        for (piece in pieces) canvas.drawBitmap(piece.bitmap, piece.x.toFloat(), piece.y.toFloat(), null)
        full
    }

    /** Trims the clear border away. Null if nothing is left of the cut-out. */
    suspend fun cropToContent(full: Bitmap): Bitmap? = withContext(Dispatchers.Default) {
        val width = full.width
        val height = full.height
        val pixels = IntArray(width * height)
        full.getPixels(pixels, 0, width, 0, 0, width, height)
        val bounds = Rect(width, height, -1, -1)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if ((pixels[row + x] ushr 24) > 12) {
                    if (x < bounds.left) bounds.left = x
                    if (x > bounds.right) bounds.right = x
                    if (y < bounds.top) bounds.top = y
                    if (y > bounds.bottom) bounds.bottom = y
                }
            }
        }
        if (bounds.right < bounds.left) return@withContext null
        Bitmap.createBitmap(full, bounds.left, bounds.top, bounds.width() + 1, bounds.height() + 1)
    }
}
