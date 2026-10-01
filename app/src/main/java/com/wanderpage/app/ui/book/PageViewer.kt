package com.wanderpage.app.ui.book

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.wanderpage.app.R
import com.wanderpage.app.data.Book
import com.wanderpage.app.data.model.PageFormat
import com.wanderpage.app.ui.export.ExportSheet
import com.wanderpage.app.ui.page.PageSurface
import com.wanderpage.app.ui.theme.LocalReducedMotion
import com.wanderpage.app.ui.theme.PreviewBackHandler
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * A page lifted out of the book to fill the screen (PRD §6.5). [zoom] runs 0 (the page in its slot in the
 * book) to 1 (full screen); the book screen reads it to blur itself behind.
 */
@Composable
fun PageViewer(
    book: Book,
    startIndex: Int,
    zoom: Animatable<Float, AnimationVector1D>,
    slotRect: (Int) -> Rect,
    onPageChanged: (Int) -> Unit,
    onClosed: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onChangeFormat: (Long, PageFormat) -> Unit,
) {
    val pages = book.pages
    val pager = rememberPagerState(startIndex) { pages.size }
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    val settle = if (reducedMotion) spring<Float>(stiffness = 3000f) else spring(dampingRatio = 0.86f, stiffness = 260f)
    LaunchedEffect(Unit) { zoom.animateTo(1f, settle) }
    LaunchedEffect(pager.settledPage) { onPageChanged(pager.settledPage) }
    suspend fun close() {
        zoom.animateTo(0f, settle)
        onClosed()
    }
    PreviewBackHandler(
        onProgress = { zoom.snapTo(1f - it * 0.4f) },
        onCancel = { zoom.animateTo(1f) },
        onCommit = { close() },
    )

    val current = pager.currentPage.coerceIn(0, pages.lastIndex)
    val page = pages[current]

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val z = zoom.value.coerceIn(0f, 1f)
        val area = with(density) {
            Rect(
                left = 20.dp.toPx(),
                top = WindowInsets.statusBars.getTop(this) + 68.dp.toPx(),
                right = constraints.maxWidth - 20.dp.toPx(),
                bottom = constraints.maxHeight - WindowInsets.navigationBars.getBottom(this) - 104.dp.toPx(),
            )
        }
        fun fit(format: PageFormat, within: Rect): Rect {
            val width = minOf(within.width, within.height * format.aspectRatio)
            val height = width / format.aspectRatio
            return Rect(within.center.x - width / 2, within.center.y - height / 2, within.center.x + width / 2, within.center.y + height / 2)
        }
        val target = fit(page.page.format, area)
        val origin = fit(page.page.format, slotRect(current))

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * z))
                .pointerInput(Unit) { awaitEachGesture { awaitPointerEvent().changes.forEach { it.consume() } } },
        )

        HorizontalPager(
            state = pager,
            userScrollEnabled = z == 1f,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                // Maps the full-screen page back onto its slot in the book at z = 0 (P-1).
                val startScale = origin.width / target.width
                val scale = lerp(startScale, 1f, z)
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = scale
                scaleY = scale
                translationX = lerp(origin.left - target.left * startScale, 0f, z)
                translationY = lerp(origin.top - target.top * startScale, 0f, z)
            },
        ) { index ->
            val item = pages[index]
            val rect = fit(item.page.format, area)
            Zoomable {
                PageSurface(
                    item.page.format,
                    item.page.background,
                    item.elements,
                    Modifier
                        .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                        .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                        // Neighbouring pages stay hidden until the zoom has finished, or they'd fly in beside the book.
                        .graphicsLayer { alpha = if (index == pager.currentPage || zoom.value >= 1f) 1f else 0f },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp).graphicsLayer { alpha = z },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { scope.launch { close() } }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.page_counter, current + 1, pages.size),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 16.dp),
            )
        }

        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 20.dp).graphicsLayer { alpha = z },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { onEdit(page.page.id) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.page_edit))
            }
            FilledTonalButton(
                onClick = { sharing = true },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFF4EBDD)),
            ) {
                Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.page_share))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.page_options), tint = Color.White) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.page_duplicate)) },
                        onClick = {
                            menuOpen = false
                            onDuplicate(page.page.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.page_delete)) },
                        onClick = {
                            menuOpen = false
                            confirmDelete = true
                        },
                    )
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.page_change_format),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    for (format in PageFormat.entries) {
                        DropdownMenuItem(
                            text = { Text(format.ratioLabel) },
                            enabled = format != page.page.format,
                            onClick = {
                                menuOpen = false
                                onChangeFormat(page.page.id, format)
                            },
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        DeletePageDialog(
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                val id = page.page.id
                scope.launch {
                    close()
                    onDelete(id)
                }
            },
        )
    }
    if (sharing) ExportSheet(page, onDismiss = { sharing = false })
}

@Composable
fun DeletePageDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.page_delete_title)) },
        text = { Text(stringResource(R.string.page_delete_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.page_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Read-mode zoom (P-2): pinch to look closer, drag to move around while zoomed, double-tap to reset.
 * One-finger drags at normal size are left alone so the pager can swipe between pages.
 */
@Composable
private fun Zoomable(content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = if (scale > 1f) 1f else 2.5f
                    offset = Offset.Zero
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                            val reachX = size.width * (scale - 1f) / 2f
                            val reachY = size.height * (scale - 1f) / 2f
                            val moved = offset + event.calculatePan()
                            offset = Offset(moved.x.coerceIn(-reachX, reachX), moved.y.coerceIn(-reachY, reachY))
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    ) {
        content()
    }
}
