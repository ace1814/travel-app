package com.wanderpage.app.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wanderpage.app.R
import com.wanderpage.app.container
import com.wanderpage.app.data.model.ElementPayload
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val Desk = Brush.verticalGradient(listOf(Color(0xFF55443A), Color(0xFF2F2620)))
private val DeskInk = Color(0xFFF4EBDD)

/**
 * The erase / restore brush for a cut-out (I-4). Erasing clears pixels from the cut-out; restoring paints
 * the original photo back in. A loupe shows what is under the finger.
 */
@Composable
fun RefineScreen(payload: ElementPayload.CutOut, onDone: (ElementPayload.CutOut) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val images = context.container.images
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var working by remember { mutableStateOf<Bitmap?>(null) }
    // Bumped after every stroke segment: the bitmap is edited in place, so Compose needs telling to redraw.
    var revision by remember { mutableIntStateOf(0) }
    var erasing by remember { mutableStateOf(true) }
    var brush by remember { mutableFloatStateOf(0.06f) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var saving by remember { mutableStateOf(false) }

    BackHandler(onBack = onCancel)

    LaunchedEffect(payload.maskUri) {
        val photo = images.decode(File(payload.originalUri), maxSide = 1600) ?: return@LaunchedEffect onCancel()
        val cut = images.decode(File(payload.maskUri), maxSide = 1600) ?: return@LaunchedEffect onCancel()
        // The two files were saved at the same size; scaling guards against a decoder rounding one differently.
        original = if (photo.width == cut.width && photo.height == cut.height) photo else Bitmap.createScaledBitmap(photo, cut.width, cut.height, true)
        working = cut.copy(Bitmap.Config.ARGB_8888, true)
    }

    Column(Modifier.fillMaxSize().background(Desk).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, stringResource(R.string.cancel), tint = DeskInk) }
            Text(stringResource(R.string.refine_title), style = MaterialTheme.typography.headlineMedium, color = DeskInk, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            IconButton(
                enabled = !saving && working != null,
                onClick = {
                    val result = working ?: return@IconButton
                    val photo = original ?: return@IconButton
                    saving = true
                    scope.launch {
                        val cropped = CutOutEngine.cropToContent(result)
                        if (cropped == null) {
                            onCancel()
                        } else {
                            val mask = images.save(result, png = true)
                            val image = images.save(cropped, png = true)
                            // Keeps the subject the same size on the page even though its crop changed:
                            // the element's width follows the crop's share of the photo.
                            val scaleOnPage = payload.width / payload.cropWidth()
                            onDone(
                                payload.copy(
                                    maskUri = mask.path,
                                    imageUri = image.path,
                                    aspect = image.aspect,
                                    width = cropped.width * scaleOnPage * (payload.photoWidth() / photo.width.toFloat()),
                                ),
                            )
                        }
                    }
                },
            ) {
                if (saving) CircularProgressIndicator(Modifier.padding(4.dp), color = DeskInk, strokeWidth = 2.dp) else Icon(Icons.Default.Check, stringResource(R.string.done), tint = DeskInk)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            val photo = original
            val cut = working
            if (photo == null || cut == null) {
                CircularProgressIndicator(color = DeskInk)
            } else {
                val photoImage = remember(photo) { photo.asImageBitmap() }
                val cutImage = remember(cut) { cut.asImageBitmap() }
                val erase = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND } }
                val restore = remember(photo) { Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND } }
                val canvas = remember(cut) { android.graphics.Canvas(cut) }
                val brushPixels = brush * cut.width

                Canvas(
                    Modifier.fillMaxSize().pointerInput(cut) {
                        awaitEachGesture {
                            val fit = fitInside(Size(cut.width.toFloat(), cut.height.toFloat()), Size(size.width.toFloat(), size.height.toFloat()))
                            fun toBitmap(point: Offset) = (point - fit.offset) / fit.scale
                            val down = awaitFirstDown()
                            var last = toBitmap(down.position)
                            finger = down.position
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.first()
                                val now = toBitmap(change.position)
                                val paint = if (erasing) erase else restore
                                paint.strokeWidth = brush * cut.width
                                canvas.drawLine(last.x, last.y, now.x, now.y, paint)
                                last = now
                                finger = change.position
                                revision++
                                change.consume()
                            } while (event.changes.any { it.pressed })
                            finger = null
                        }
                    },
                ) {
                    @Suppress("UNUSED_EXPRESSION") revision
                    val fit = fitInside(Size(cut.width.toFloat(), cut.height.toFloat()), size)
                    val shown = IntSize((cut.width * fit.scale).roundToInt(), (cut.height * fit.scale).roundToInt())
                    val at = IntOffset(fit.offset.x.roundToInt(), fit.offset.y.roundToInt())
                    fun DrawScope.picture(offset: IntOffset, extent: IntSize) {
                        // The original shows faintly underneath, so it's clear what "restore" would bring back.
                        drawImage(photoImage, dstOffset = offset, dstSize = extent, alpha = 0.28f)
                        drawImage(cutImage, dstOffset = offset, dstSize = extent)
                    }
                    picture(at, shown)

                    val touch = finger
                    if (touch != null) {
                        drawCircle(Color.White, brushPixels * fit.scale / 2, touch, style = Stroke(1.5.dp.toPx()))
                        // The loupe sits in whichever top corner the finger isn't in.
                        val radius = 58.dp.toPx()
                        val centre = Offset(if (touch.x < size.width / 2) size.width - radius - 4.dp.toPx() else radius + 4.dp.toPx(), radius + 4.dp.toPx())
                        val lens = Path().apply { addOval(Rect(centre, radius)) }
                        clipPath(lens) {
                            drawCircle(Color(0xFF2A221C), radius, centre)
                            val magnify = 2.2f
                            val zoomed = IntSize((shown.width * magnify).roundToInt(), (shown.height * magnify).roundToInt())
                            val zoomedAt = IntOffset(
                                (centre.x - (touch.x - at.x) * magnify).roundToInt(),
                                (centre.y - (touch.y - at.y) * magnify).roundToInt(),
                            )
                            picture(zoomedAt, zoomed)
                            drawCircle(Color.White, brushPixels * fit.scale * magnify / 2, centre, style = Stroke(1.5.dp.toPx()))
                        }
                        drawCircle(Color.White, radius, centre, style = Stroke(3.dp.toPx()))
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val colours = FilterChipDefaults.filterChipColors(labelColor = DeskInk, selectedContainerColor = DeskInk, selectedLabelColor = Color(0xFF2F2620))
                FilterChip(selected = erasing, onClick = { erasing = true }, label = { Text(stringResource(R.string.refine_erase)) }, colors = colours)
                FilterChip(selected = !erasing, onClick = { erasing = false }, label = { Text(stringResource(R.string.refine_restore)) }, colors = colours)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.refine_size), color = DeskInk, modifier = Modifier.padding(end = 12.dp))
                Slider(value = brush, onValueChange = { brush = it }, valueRange = 0.015f..0.2f, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun imageWidth(path: String): Float {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)
    return options.outWidth.coerceAtLeast(1).toFloat()
}

/** Pixel width of the cropped cut-out as stored. */
private fun ElementPayload.CutOut.cropWidth(): Float = imageWidth(imageUri)

/** Pixel width of the full-size cut-out as stored, which may be larger than the copy loaded for editing. */
private fun ElementPayload.CutOut.photoWidth(): Float = imageWidth(maskUri)
