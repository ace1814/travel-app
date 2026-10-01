package com.wanderpage.app.ui.book

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.material3.LocalContentColor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wanderpage.app.R
import com.wanderpage.app.data.Book
import com.wanderpage.app.data.db.PageWithElements
import com.wanderpage.app.ui.editor.EditorScreen
import com.wanderpage.app.ui.home.BookCover
import com.wanderpage.app.ui.home.boardColour
import com.wanderpage.app.ui.home.formatDateRange
import com.wanderpage.app.ui.page.PageSurface
import com.wanderpage.app.ui.theme.Handwriting
import com.wanderpage.app.ui.theme.LocalReducedMotion
import com.wanderpage.app.ui.theme.PreviewBackHandler
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

private val BookPaper = Color(0xFFF7F0E1)
private val DeskTop = Color(0xFF55443A)
private val DeskBottom = Color(0xFF2F2620)
private val DeskInk = Color(0xFFF4EBDD)

/** What one side of a spread shows. */
private sealed interface Slot {
    /** The plain sheet inside the front and back covers. The front one carries the title. */
    data class Endpaper(val front: Boolean) : Slot
    data class Page(val page: PageWithElements, val index: Int) : Slot
    data object Add : Slot
}

/** The book's sides in order: inside cover, every page, the "+" page, and a closing sheet if that leaves a gap. */
private fun slotsOf(pages: List<PageWithElements>): List<Slot> = buildList {
    add(Slot.Endpaper(front = true))
    pages.forEachIndexed { index, page -> add(Slot.Page(page, index)) }
    add(Slot.Add)
    if (size % 2 == 1) add(Slot.Endpaper(front = false))
}

/** The spread a page sits on. Page 0 is slot 1, on the right of spread 0. */
private fun spreadOfPage(index: Int) = (index + 1) / 2

private fun pageIsOnLeft(index: Int) = (index + 1) % 2 == 0

/**
 * A diary as an open book (PRD §6.4), plus the screens that grow out of it: the zoomed page, the page
 * overview and the editor.
 *
 * @param origin where the book's cover was on the shelf or map, in root coordinates. The cover grows from
 * there as it opens and shrinks back to it on close.
 */
@Composable
fun BookScreen(diaryId: Long, origin: Rect?, onClosed: () -> Unit) {
    val vm: BookViewModel = viewModel(key = "book-$diaryId", factory = BookViewModel.factory(diaryId))
    val book by vm.book.collectAsStateWithLifecycle()
    val reducedMotion = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // 0 is the closed book sitting at `origin`; 1 is the open book.
    val open = remember { Animatable(0f) }
    // Which spread is showing. Fractions are a page in mid-turn.
    val position = remember { Animatable(0f) }

    var showOverview by rememberSaveable { mutableStateOf(false) }
    var viewerPage by rememberSaveable { mutableStateOf<Int?>(null) }
    var editingPageId by rememberSaveable { mutableStateOf<Long?>(null) }
    var addingCity by rememberSaveable { mutableStateOf(false) }

    val loaded = book
    LaunchedEffect(loaded != null) {
        if (loaded != null) open.animateTo(1f, if (reducedMotion) spring(stiffness = 2000f) else spring(dampingRatio = 0.82f, stiffness = 90f))
    }
    suspend fun close() {
        open.animateTo(0f, if (reducedMotion) spring(stiffness = 2000f) else spring(dampingRatio = 1f, stiffness = 130f))
        onClosed()
    }
    PreviewBackHandler(
        onProgress = { open.snapTo(1f - it * 0.3f) },
        onCancel = { open.animateTo(1f) },
        onCommit = { close() },
    )

    if (loaded == null) return
    val slots = remember(loaded.pages) { slotsOf(loaded.pages) }
    val lastSpread = slots.size / 2 - 1
    LaunchedEffect(lastSpread) {
        position.updateBounds(0f, lastSpread.toFloat())
        if (position.value > lastSpread) position.snapTo(lastSpread.toFloat())
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val p = open.value
        val statusBar = WindowInsets.statusBars.getTop(density)
        val navigationBar = WindowInsets.navigationBars.getBottom(density)
        val geometry = remember(constraints, loaded.diary.defaultFormat, density, statusBar, navigationBar) {
            with(density) {
                val margin = 14.dp.toPx()
                val top = statusBar + 72.dp.toPx()
                val bottom = navigationBar + 64.dp.toPx()
                val width = constraints.maxWidth.toFloat()
                val height = constraints.maxHeight.toFloat()
                val aspect = loaded.diary.defaultFormat.aspectRatio
                val pageWidth = minOf((width - margin * 2) / 2, (height - top - bottom) * aspect)
                val pageHeight = pageWidth / aspect
                val centre = Offset(width / 2, top + (height - top - bottom) / 2)
                BookGeometry(
                    left = Rect(centre.x - pageWidth, centre.y - pageHeight / 2, centre.x, centre.y + pageHeight / 2),
                    right = Rect(centre.x, centre.y - pageHeight / 2, centre.x + pageWidth, centre.y + pageHeight / 2),
                )
            }
        }
        // With no known origin (opened from a notice, say) the book grows from a smaller copy of itself.
        val start = origin ?: geometry.right.deflate(geometry.right.width * 0.2f)
        val right = lerp(start, geometry.right, p)
        val left = right.translate(-right.width, 0f)
        val chrome = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)

        val viewerZoom = remember { Animatable(0f) }
        val blurBook = viewerZoom.value

        // The desk the book lies on.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = p.coerceIn(0f, 1f) }
                .background(Brush.verticalGradient(listOf(DeskTop, DeskBottom)))
                // Swallows touches so the shelf underneath can't be tapped through the book.
                .pointerInput(Unit) { awaitEachGesture { awaitPointerEvent().changes.forEach { it.consume() } } },
        )

        Box(
            Modifier
                .fillMaxSize()
                .then(if (blurBook > 0f) Modifier.blur((blurBook * 10).dp) else Modifier)
                .pointerInput(Unit) {
                    // D-6: pinching the book together opens the overview. Watched on the initial pass and
                    // never consumed, so page turns and taps still work.
                    awaitEachGesture {
                        var zoom = 1f
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.size > 1) zoom *= event.calculateZoom()
                            if (zoom < 0.75f) {
                                showOverview = true
                                zoom = 1f
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .draggable(
                    state = rememberDraggableState { delta ->
                        scope.launch { position.snapTo(position.value - delta / (geometry.right.width * 1.5f)) }
                    },
                    orientation = Orientation.Horizontal,
                    enabled = p == 1f && viewerPage == null,
                    onDragStopped = { velocity ->
                        // A fling finishes the turn in its direction; a slow release goes to the nearer spread.
                        val current = position.value
                        val target = when {
                            velocity < -600f -> floor(current) + 1
                            velocity > 600f -> floor(current)
                            else -> current.roundToInt().toFloat()
                        }.coerceIn(0f, lastSpread.toFloat())
                        if (reducedMotion) {
                            position.snapTo(target)
                        } else {
                            position.animateTo(target, spring(dampingRatio = 0.9f, stiffness = 180f), -velocity / (geometry.right.width * 1.5f))
                        }
                        if (target != floor(current) || current != target) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                ),
        ) {
            val turn = position.value.coerceIn(0f, lastSpread.toFloat())
            val spread = floor(turn).toInt().coerceAtMost(lastSpread)
            val t = turn - spread
            val hiddenPageId = viewerPage?.let { loaded.pages.getOrNull(it)?.page?.id }

            @Composable
            fun slot(index: Int, isLeft: Boolean, modifier: Modifier) {
                SlotView(
                    slot = slots[index],
                    isLeft = isLeft,
                    book = loaded,
                    hiddenPageId = hiddenPageId,
                    onPage = { viewerPage = it },
                    onAdd = { vm.addPage() },
                    modifier = modifier,
                )
            }

            // The cover boards and page block showing around the open pages (D-4).
            if (p > 0.5f) {
                val board = loaded.diary.coverStyle.boardColour()
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        val rim = 7.dp.toPx()
                        val alpha = ((p - 0.5f) * 2).coerceIn(0f, 1f)
                        drawRoundRect(
                            Color.Black.copy(alpha = 0.35f * alpha),
                            Offset(left.left - rim + 6.dp.toPx(), left.top - rim + 10.dp.toPx()),
                            Size(left.width * 2 + rim * 2, left.height + rim * 2),
                            CornerRadius(10.dp.toPx()),
                        )
                        drawRoundRect(
                            board.copy(alpha = alpha),
                            Offset(left.left - rim, left.top - rim),
                            Size(left.width * 2 + rim * 2, left.height + rim * 2),
                            CornerRadius(8.dp.toPx()),
                        )
                        // A few page edges peeking out past each side.
                        for (i in 1..3) {
                            val step = i * 1.5.dp.toPx()
                            drawRect(Color(0xFFE6DBC4).copy(alpha = alpha), Offset(left.left - step, left.top + step), Size(step, left.height - step * 2))
                            drawRect(Color(0xFFE6DBC4).copy(alpha = alpha), Offset(right.right, right.top + step), Size(step, right.height - step * 2))
                        }
                    },
                )
            }

            if (p < 1f) {
                // Opening or closing: the cover swings on its spine (D-1). Past 90° its back face is the left page.
                val angle = 180f * p.coerceIn(0f, 1f)
                slot(spread * 2 + 1, false, Modifier.placed(geometry.right, right))
                if (angle > 90f) {
                    slot(spread * 2, true, Modifier.placed(geometry.left, left, rotationY = 180f - angle, hinge = 1f).shade(angle))
                } else {
                    Box(
                        Modifier
                            .offset { IntOffset(right.left.roundToInt(), right.top.roundToInt()) }
                            .size(with(density) { right.width.toDp() }, with(density) { right.height.toDp() })
                            .graphicsLayer {
                                rotationY = -angle
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                cameraDistance = 40f * this.density
                            }
                            .shade(angle),
                    ) {
                        BookCover(
                            title = loaded.diary.title,
                            placeLine = loaded.places.joinToString(" · ") { it.name },
                            dateLine = formatDateRange(loaded.diary.startDate, loaded.diary.endDate),
                            style = loaded.diary.coverStyle,
                            imageUri = loaded.diary.coverImageUri,
                            modifier = Modifier.fillMaxSize(),
                            lockAspect = false,
                        )
                    }
                }
            } else if (t == 0f) {
                slot(spread * 2, true, Modifier.placed(geometry.left, left))
                slot(spread * 2 + 1, false, Modifier.placed(geometry.right, right))
            } else {
                // A page in mid-turn (D-3): the leaf between this spread and the next lifts off the right
                // side, stands on the gutter at 90°, and comes down on the left showing its back.
                val angle = 180f * t
                slot(spread * 2, true, Modifier.placed(geometry.left, left).dimmed(if (angle > 90f) sin(Math.toRadians(angle.toDouble())).toFloat() * 0.25f else 0f))
                slot(spread * 2 + 3, false, Modifier.placed(geometry.right, right).dimmed(if (angle < 90f) sin(Math.toRadians(angle.toDouble())).toFloat() * 0.25f else 0f))
                if (angle < 90f) {
                    slot(spread * 2 + 1, false, Modifier.placed(geometry.right, right, rotationY = -angle, hinge = 0f).shade(angle))
                } else {
                    slot(spread * 2 + 2, true, Modifier.placed(geometry.left, left, rotationY = 180f - angle, hinge = 1f).shade(angle))
                }
            }
        }

        // Top bar and page counter, fading in once the cover is most of the way open.
        if (chrome > 0f) {
            CompositionLocalProvider(LocalContentColor provides DeskInk) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp).graphicsLayer { alpha = chrome * (1f - blurBook) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { scope.launch { close() } }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                    Text(
                        loaded.diary.title,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                    IconButton(onClick = { addingCity = true }) { Icon(Icons.Default.AddLocationAlt, stringResource(R.string.book_add_city)) }
                    IconButton(onClick = { showOverview = true }) { Icon(Icons.Default.GridView, stringResource(R.string.book_overview)) }
                }
                val spreadNow = position.value.roundToInt().coerceIn(0, lastSpread)
                val shown = listOf(slots[spreadNow * 2], slots[spreadNow * 2 + 1]).filterIsInstance<Slot.Page>().map { it.index + 1 }
                val counter = when (shown.size) {
                    2 -> stringResource(R.string.book_pages_pair, shown[0], shown[1], loaded.pages.size)
                    1 -> stringResource(R.string.book_pages_single, shown[0], loaded.pages.size)
                    else -> stringResource(R.string.book_empty_hint)
                }
                Text(
                    counter,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DeskInk.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp).graphicsLayer { alpha = chrome * (1f - blurBook) },
                )
            }
        }

        viewerPage?.let { startIndex ->
            if (loaded.pages.isEmpty()) {
                viewerPage = null
            } else {
                PageViewer(
                    book = loaded,
                    startIndex = startIndex.coerceIn(0, loaded.pages.lastIndex),
                    zoom = viewerZoom,
                    slotRect = { index -> if (pageIsOnLeft(index)) geometry.left else geometry.right },
                    onPageChanged = { index ->
                        viewerPage = index
                        scope.launch { position.snapTo(spreadOfPage(index).toFloat().coerceAtMost(lastSpread.toFloat())) }
                    },
                    onClosed = { viewerPage = null },
                    onEdit = { editingPageId = it },
                    onDuplicate = vm::duplicatePage,
                    onDelete = vm::deletePage,
                    onChangeFormat = vm::setPageFormat,
                )
            }
        }

        AnimatedVisibility(showOverview, enter = fadeIn() + scaleIn(initialScale = 1.08f), exit = fadeOut() + scaleOut(targetScale = 1.08f)) {
            PageOverview(
                pages = loaded.pages,
                onClose = { showOverview = false },
                onOpenPage = { index ->
                    showOverview = false
                    scope.launch { position.snapTo(spreadOfPage(index).toFloat().coerceAtMost(lastSpread.toFloat())) }
                },
                onReorder = vm::reorderPages,
                onDuplicate = vm::duplicatePage,
                onDelete = vm::deletePage,
            )
        }

        AnimatedVisibility(editingPageId != null, enter = fadeIn(), exit = fadeOut()) {
            // Keeps the last id while the editor fades out.
            val pageId = remember { mutableStateOf(editingPageId) }
            editingPageId?.let { pageId.value = it }
            pageId.value?.let { EditorScreen(pageId = it, onDone = { editingPageId = null }) }
        }
    }

    if (addingCity) AddCityDialog(vm, onDismiss = { addingCity = false })
}

private class BookGeometry(val left: Rect, val right: Rect)

private fun lerp(a: Rect, b: Rect, t: Float) =
    Rect(lerp(a.left, b.left, t), lerp(a.top, b.top, t), lerp(a.right, b.right, t), lerp(a.bottom, b.bottom, t))

/**
 * Lays the content out at [layout] and shows it at [target], optionally turned about its left ([hinge] 0)
 * or right ([hinge] 1) edge. Scaling a finished layout is far cheaper than re-laying a page out every frame.
 */
private fun Modifier.placed(layout: Rect, target: Rect, rotationY: Float = 0f, hinge: Float = 0f): Modifier = composed {
    val density = LocalDensity.current
    this
        .offset { IntOffset(layout.left.roundToInt(), layout.top.roundToInt()) }
        .size(with(density) { layout.width.toDp() }, with(density) { layout.height.toDp() })
        .graphicsLayer {
            transformOrigin = TransformOrigin(hinge, 0.5f)
            scaleX = target.width / layout.width
            scaleY = target.height / layout.height
            translationX = lerp(target.left, target.right, hinge) - lerp(layout.left, layout.right, hinge)
            translationY = target.center.y - layout.center.y
            this.rotationY = rotationY
            cameraDistance = 40f * this.density
        }
}

/** Darkens a turning leaf: it catches least light when it stands edge-on at 90°. */
private fun Modifier.shade(angle: Float): Modifier = dimmed(sin(Math.toRadians(angle.toDouble())).toFloat() * 0.3f)

private fun Modifier.dimmed(amount: Float): Modifier =
    if (amount <= 0f) this else drawWithContent {
        drawContent()
        drawRect(Color.Black.copy(alpha = amount.coerceIn(0f, 1f)))
    }

@Composable
private fun SlotView(
    slot: Slot,
    isLeft: Boolean,
    book: Book,
    hiddenPageId: Long?,
    onPage: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier,
) {
    val outer = 5.dp
    val shape = if (isLeft) RoundedCornerShape(topStart = outer, bottomStart = outer) else RoundedCornerShape(topEnd = outer, bottomEnd = outer)
    Box(modifier.clip(shape).background(BookPaper), contentAlignment = Alignment.Center) {
        when (slot) {
            is Slot.Page -> {
                val page = slot.page.page
                val description = stringResource(R.string.book_page_description, slot.index + 1)
                Box(
                    Modifier.fillMaxSize().semantics { contentDescription = description }.clickable { onPage(slot.index) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (page.id != hiddenPageId) {
                        // A page in another size sits centred on the book's page with a paper mat around it (§7).
                        val matted = page.format != book.diary.defaultFormat
                        PageSurface(page.format, page.background, slot.page.elements, if (matted) Modifier.padding(8.dp) else Modifier)
                    }
                }
            }
            Slot.Add -> Column(
                Modifier.fillMaxSize().clickable(onClick = onAdd),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val ink = Color(0xFFA89782)
                Box(
                    Modifier.size(56.dp).drawBehind {
                        drawCircle(ink, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))))
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = ink)
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.book_add_page), color = Color(0xFF8A7A66), style = MaterialTheme.typography.bodyMedium)
            }
            is Slot.Endpaper -> if (slot.front) FrontEndpaper(book)
        }
        // The gutter: pages dip towards the spine, so the inner edge sits in shadow.
        Box(
            Modifier.fillMaxSize().drawBehind {
                val reach = size.width * 0.14f
                val brush = if (isLeft) {
                    Brush.horizontalGradient(listOf(Color.Transparent, Color(0x38000000)), startX = size.width - reach, endX = size.width)
                } else {
                    Brush.horizontalGradient(listOf(Color(0x38000000), Color.Transparent), startX = 0f, endX = reach)
                }
                drawRect(brush)
            },
        )
    }
}

/** The inside of the front cover: the trip's name, its cities and dates, written like a title page. */
@Composable
private fun FrontEndpaper(book: Book) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val unit = maxWidth.value / 100f
        val ink = Color(0xFF4A3B2E)
        Column(
            Modifier.fillMaxSize().padding(horizontal = (unit * 10).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                book.diary.title,
                color = ink,
                fontFamily = Handwriting,
                fontSize = (unit * 17).sp,
                lineHeight = (unit * 19).sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height((unit * 6).dp))
            Box(Modifier.size((unit * 22).dp, 1.dp).background(ink.copy(alpha = 0.5f)))
            Spacer(Modifier.height((unit * 6).dp))
            for (place in book.places) {
                Text(
                    place.name.uppercase(),
                    color = ink.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Serif,
                    fontSize = (unit * 5.4f).sp,
                    lineHeight = (unit * 9).sp,
                    letterSpacing = (unit * 0.8f).sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            formatDateRange(book.diary.startDate, book.diary.endDate)?.let { dates ->
                Spacer(Modifier.height((unit * 4).dp))
                Text(dates, color = ink.copy(alpha = 0.7f), fontFamily = FontFamily.Serif, fontSize = (unit * 5).sp, textAlign = TextAlign.Center)
            }
        }
    }
}
