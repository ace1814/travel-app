package com.wanderpage.app.ui.export

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wanderpage.app.R
import com.wanderpage.app.container
import com.wanderpage.app.data.SettingsState
import com.wanderpage.app.data.db.PageWithElements
import com.wanderpage.app.data.model.PAGE_WIDTH_UNITS
import com.wanderpage.app.ui.page.DEFAULT_FONT_ID
import com.wanderpage.app.ui.page.ImageLoadTracker
import com.wanderpage.app.ui.page.LocalImageLoadTracker
import com.wanderpage.app.ui.page.PageSurface
import com.wanderpage.app.ui.page.pageFont
import com.wanderpage.app.ui.page.paperShadow
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val INSTAGRAM = "com.instagram.android"
private const val ALBUM = "Travel Diary"

/**
 * Share and export (PRD §6.7). The sheet shows the page as it will be exported, and holds a second,
 * unseen copy laid out at exactly 1080 pixels wide. That copy is drawn by the same [PageSurface] as
 * everywhere else, so the image matches the editor.
 */
@Composable
fun ExportSheet(page: PageWithElements, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings by context.container.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val tracker = remember { ImageLoadTracker() }
    var busy by remember { mutableStateOf(false) }
    val format = page.page.format

    fun export(deliver: suspend (Bitmap) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                deliver(capture(layer, tracker))
            } catch (e: Exception) {
                Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }
    val saveToGallery: suspend (Bitmap) -> Unit = { bitmap ->
        withContext(Dispatchers.IO) { saveToGallery(context, bitmap, settings) }
        Toast.makeText(context, R.string.export_saved, Toast.LENGTH_SHORT).show()
    }
    val askStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) export(saveToGallery)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.export_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Box(contentAlignment = Alignment.Center) {
                ExportPage(page, settings.watermark, Modifier.height(300.dp).paperShadow(strength = 1.6f))
                // The capture copy. It takes no room and paints nothing on screen; it only records into `layer`.
                CompositionLocalProvider(LocalImageLoadTracker provides tracker) {
                    Box(Modifier.size(0.dp)) {
                        ExportPage(
                            page,
                            settings.watermark,
                            Modifier
                                .layout { measurable, _ ->
                                    val placeable = measurable.measure(Constraints.fixed(PAGE_WIDTH_UNITS, format.heightUnits))
                                    layout(0, 0) { placeable.place(0, 0) }
                                }
                                .drawWithContent { layer.record { this@drawWithContent.drawContent() } },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.export_info, PAGE_WIDTH_UNITS, format.heightUnits, if (settings.exportPng) "PNG" else "JPEG"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { export { shareTo(context, it, settings, instagram = true) } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.size(8.dp))
                Text(stringResource(if (busy) R.string.export_preparing else R.string.export_instagram))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { export { shareTo(context, it, settings, instagram = false) } },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.export_more))
                }
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) askStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else export(saveToGallery)
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.export_save))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ExportPage(page: PageWithElements, watermark: Boolean, modifier: Modifier) {
    val mark = stringResource(R.string.watermark_text)
    PageSurface(page.page.format, page.page.background, page.elements, modifier) {
        if (watermark) {
            BasicText(
                mark,
                Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 22.dp),
                style = TextStyle(
                    fontFamily = pageFont(DEFAULT_FONT_ID).family,
                    fontSize = 34.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 8f),
                ),
            )
        }
    }
}

/** Waits for every picture on the page to finish loading at export size, then reads the layer back. */
private suspend fun capture(layer: GraphicsLayer, tracker: ImageLoadTracker): Bitmap {
    snapshotFlow { tracker.pending }.first { it == 0 }
    // Two frames: one for the last image to be drawn, one for the layer to be recorded with it.
    repeat(2) { withFrameNanos { } }
    val hardware = layer.toImageBitmap().asAndroidBitmap()
    return withContext(Dispatchers.Default) { hardware.copy(Bitmap.Config.ARGB_8888, false) }
}

private fun Bitmap.writeTo(stream: java.io.OutputStream, settings: SettingsState) {
    if (settings.exportPng) compress(Bitmap.CompressFormat.PNG, 100, stream) else compress(Bitmap.CompressFormat.JPEG, settings.jpegQuality, stream)
}

private val SettingsState.extension get() = if (exportPng) "png" else "jpg"
private val SettingsState.mimeType get() = if (exportPng) "image/png" else "image/jpeg"

private suspend fun shareTo(context: Context, bitmap: Bitmap, settings: SettingsState, instagram: Boolean) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // Old exports have been handed over by now; keeping them would only grow the cache.
        dir.listFiles()?.forEach { if (it.name.startsWith("wanderpage-")) it.delete() }
        val file = File(dir, "wanderpage-${System.currentTimeMillis()}.${settings.extension}")
        file.outputStream().use { bitmap.writeTo(it, settings) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = settings.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.export_chooser))
    if (instagram) {
        // Instagram shows its own Story / Feed / Reel choice. The Stories-only intent needs a Facebook App ID.
        val direct = Intent(send).setPackage(INSTAGRAM)
        if (direct.resolveActivity(context.packageManager) != null) {
            context.startActivity(direct)
            return
        }
        Toast.makeText(context, R.string.export_no_instagram, Toast.LENGTH_SHORT).show()
    }
    context.startActivity(chooser)
}

/** E-3: saves into a "Travel Diary" album in the gallery. */
private fun saveToGallery(context: Context, bitmap: Bitmap, settings: SettingsState) {
    val name = "wanderpage-${System.currentTimeMillis()}.${settings.extension}"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, settings.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore refused the image")
        resolver.openOutputStream(uri)!!.use { bitmap.writeTo(it, settings) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    } else {
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM).apply { mkdirs() }
        val file = File(dir, name)
        file.outputStream().use { bitmap.writeTo(it, settings) }
        android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(settings.mimeType), null)
    }
}
