package com.wanderpage.app.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wanderpage.app.R
import com.wanderpage.app.container
import com.wanderpage.app.data.model.ElementPayload
import com.wanderpage.app.ui.theme.LocalReducedMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private sealed interface CutOutStage {
    data object Working : CutOutStage
    class Choose(val photo: Bitmap, val pieces: List<SubjectPiece>) : CutOutStage
    class Failed(val messageRes: Int) : CutOutStage
}

private val Desk = Brush.verticalGradient(listOf(Color(0xFF55443A), Color(0xFF2F2620)))
private val DeskInk = Color(0xFFF4EBDD)

/**
 * Turns a photo into a sticker (PRD §6.6.2): finds the subjects, lets the user pick which to keep when
 * there are several, then peels the subject off the photo before handing back the finished element.
 */
@Composable
fun CutOutFlow(
    source: Uri,
    onCutOut: (ElementPayload.CutOut) -> Unit,
    onKeepPhoto: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val images = context.container.images
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    var stage by remember { mutableStateOf<CutOutStage>(CutOutStage.Working) }
    var kept by remember { mutableStateOf(emptySet<Int>()) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    // 0 while choosing; runs to 1 as the subject lifts off and the photo falls away.
    val peel = remember { Animatable(0f) }
    var finishing by remember { mutableStateOf(false) }

    BackHandler(onBack = onCancel)

    LaunchedEffect(source) {
        val photo = images.decode(source, maxSide = 1600)
        if (photo == null) {
            stage = CutOutStage.Failed(R.string.cutout_none)
            return@LaunchedEffect
        }
        stage = try {
            val pieces = CutOutEngine.findSubjects(photo)
            if (pieces.isEmpty()) {
                CutOutStage.Failed(R.string.cutout_none)
            } else {
                kept = pieces.indices.toSet()
                CutOutStage.Choose(photo, pieces)
            }
        } catch (e: Exception) {
            CutOutStage.Failed(R.string.cutout_unavailable)
        }
    }
    val choosing = stage as? CutOutStage.Choose
    LaunchedEffect(choosing, kept) {
        if (choosing != null) {
            preview = CutOutEngine.compose(choosing.photo.width, choosing.photo.height, choosing.pieces.filterIndexed { i, _ -> i in kept }).asImageBitmap()
        }
    }

    fun finish(choice: CutOutStage.Choose) {
        if (finishing || kept.isEmpty()) return
        finishing = true
        scope.launch {
            // Save while the peel plays, so the animation is the wait.
            var result: ElementPayload.CutOut? = null
            val saving = launch {
                val full = CutOutEngine.compose(choice.photo.width, choice.photo.height, choice.pieces.filterIndexed { i, _ -> i in kept })
                val cropped = CutOutEngine.cropToContent(full) ?: return@launch
                val original = images.save(choice.photo, png = false)
                val mask = images.save(full, png = true)
                val image = images.save(cropped, png = true)
                val share = cropped.width.toFloat() / choice.photo.width
                result = ElementPayload.CutOut(
                    originalUri = original.path,
                    maskUri = mask.path,
                    imageUri = image.path,
                    width = (760f * share).coerceIn(260f, 760f),
                    aspect = image.aspect,
                )
            }
            if (!reducedMotion) peel.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
            saving.join()
            result?.let(onCutOut) ?: onCancel()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Desk)
            .pointerInput(Unit) { awaitEachGesture { awaitPointerEvent().changes.forEach { it.consume() } } }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val title = when {
                choosing == null -> ""
                choosing.pieces.size > 1 -> stringResource(R.string.cutout_pick_title)
                else -> stringResource(R.string.cutout_pick_one)
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, color = DeskInk, modifier = Modifier.weight(1f))
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, stringResource(R.string.close), tint = DeskInk) }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            when (val current = stage) {
                CutOutStage.Working -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = DeskInk)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.cutout_working), color = DeskInk)
                }
                is CutOutStage.Failed -> Text(stringResource(current.messageRes), color = DeskInk, textAlign = TextAlign.Center)
                is CutOutStage.Choose -> {
                    val photo = remember(current) { current.photo.asImageBitmap() }
                    val cut = preview
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(current) {
                            detectTapGestures { tap ->
                                if (finishing) return@detectTapGestures
                                val fit = fitInside(Size(current.photo.width.toFloat(), current.photo.height.toFloat()), Size(size.width.toFloat(), size.height.toFloat()))
                                val px = ((tap.x - fit.offset.x) / fit.scale).roundToInt()
                                val py = ((tap.y - fit.offset.y) / fit.scale).roundToInt()
                                val index = current.pieces.indexOfLast { it.covers(px, py) }
                                if (index >= 0) kept = if (index in kept) kept - index else kept + index
                            }
                        },
                    ) {
                        val fit = fitInside(Size(photo.width.toFloat(), photo.height.toFloat()), size)
                        val shown = IntSize((photo.width * fit.scale).roundToInt(), (photo.height * fit.scale).roundToInt())
                        val at = IntOffset(fit.offset.x.roundToInt(), fit.offset.y.roundToInt())
                        val t = peel.value
                        // The photo left behind: faded so the subject stands out, then falling away during the peel.
                        translate(0f, t * size.height * 0.12f) {
                            drawImage(photo, dstOffset = at, dstSize = shown, alpha = 0.35f * (1f - t))
                        }
                        if (cut != null) {
                            // The subject lifts: it grows a little and its shadow spreads out beneath it.
                            scale(1f + 0.06f * t) {
                                if (t > 0f) {
                                    translate(10.dp.toPx() * t, 18.dp.toPx() * t) {
                                        drawImage(cut, dstOffset = at, dstSize = shown, alpha = 0.3f * t, colorFilter = ColorFilter.tint(Color.Black))
                                    }
                                }
                                drawImage(cut, dstOffset = at, dstSize = shown)
                            }
                        }
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (choosing != null) {
                if (choosing.pieces.size > 1) {
                    TextButton(onClick = { kept = choosing.pieces.indices.toSet() }, enabled = !finishing, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(stringResource(R.string.cutout_keep_all), color = DeskInk)
                    }
                }
                Button(onClick = { finish(choosing) }, enabled = kept.isNotEmpty() && !finishing, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.cutout_use))
                }
            }
            if (stage !is CutOutStage.Working) {
                // I-6: the photo can always go on the page whole instead.
                OutlinedButton(onClick = onKeepPhoto, enabled = !finishing, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.cutout_keep_photo), color = DeskInk)
                }
            }
        }
    }
}

class Fit(val scale: Float, val offset: Offset)

/** How to show [content] as large as possible, centred, inside [area]. */
fun fitInside(content: Size, area: Size): Fit {
    val scale = minOf(area.width / content.width, area.height / content.height)
    return Fit(scale, Offset((area.width - content.width * scale) / 2, (area.height - content.height * scale) / 2))
}
