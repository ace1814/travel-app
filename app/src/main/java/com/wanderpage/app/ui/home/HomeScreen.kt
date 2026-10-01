package com.wanderpage.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wanderpage.app.R
import com.wanderpage.app.ui.map.DiaryMap
import com.wanderpage.app.ui.theme.InkSoft
import com.wanderpage.app.ui.theme.LocalReducedMotion
import com.wanderpage.app.ui.theme.Wood
import com.wanderpage.app.ui.theme.WoodDark
import com.wanderpage.app.ui.theme.paperBackground
import kotlin.math.abs

private const val BOOKS_PER_SHELF = 2

/** Home in shelf view (PRD §6.1) with the create-diary sheet (§6.3). */
@Composable
fun HomeScreen(
    onOpenDiary: (Long, Rect?) -> Unit,
    /** The diary that is open as a book. Its cover is hidden on the shelf, since the book view is showing it. */
    openDiaryId: Long?,
    onSettings: () -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val justCreated by vm.justCreated.collectAsStateWithLifecycle()
    val pendingDelete by vm.pendingDelete.collectAsStateWithLifecycle()

    var creating by rememberSaveable { mutableStateOf(false) }
    var showMap by rememberSaveable { mutableStateOf(false) }
    var renamingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var recoveringId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val reducedMotion = LocalReducedMotion.current
    val context = LocalContext.current
    // Where each cover is on screen, so an opening book can grow out of its own cover.
    val coverBounds = remember { mutableMapOf<Long, Rect>() }
    val open: (Long) -> Unit = { id -> onOpenDiary(id, coverBounds[id]) }

    LaunchedEffect(pendingDelete?.id) {
        val card = pendingDelete ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = context.getString(R.string.deleted_message, card.title),
            actionLabel = context.getString(R.string.undo),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.commitDelete(card.id)
    }
    LaunchedEffect(justCreated) {
        if (justCreated != null) listState.animateScrollToItem(0)
    }
    BackHandler(enabled = creating) { creating = false }

    // Creating resets the draft at once; the sheet keeps showing the last one while it animates away.
    val lastOpenDraft = remember { arrayOf(draft) }
    if (creating) lastOpenDraft[0] = draft
    val focus = LocalFocusManager.current

    Box(Modifier.fillMaxSize().paperBackground()) {
        val shelf = cards
        // M-1: the shelf and the map cross-fade.
        Crossfade(showMap, animationSpec = tween(if (reducedMotion) 0 else 450), label = "home-tab") { map ->
            if (map) DiaryMap(shelf.orEmpty(), onOpen = onOpenDiary, modifier = Modifier.fillMaxSize())
        }
        if (!showMap && !shelf.isNullOrEmpty()) {
            val duplicateTitle = stringResource(R.string.duplicate_title)
            Shelf(
                cards = shelf,
                justCreated = justCreated,
                listState = listState,
                hiddenId = openDiaryId,
                onBounds = { id, bounds -> coverBounds[id] = bounds },
                onOpen = open,
                onRename = { renamingId = it.id },
                onChangeCover = { recoveringId = it.id },
                onDuplicate = { vm.duplicate(it.id, duplicateTitle.format(it.title)) },
                onDelete = vm::requestDelete,
                onDropDone = { id -> if (vm.onDropAnimationDone(id)) open(id) },
            )
        }

        SharedTransitionLayout(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = creating,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "create-diary",
            ) { open ->
                // H-4: the button grows into the sheet. With reduced motion it's only the cross-fade.
                val morph = if (reducedMotion) {
                    Modifier
                } else {
                    Modifier.sharedBounds(
                        rememberSharedContentState("create-diary"),
                        this@AnimatedContent,
                        boundsTransform = { _, _ -> spring(dampingRatio = 0.86f, stiffness = 420f) },
                    )
                }
                Box(Modifier.fillMaxSize()) {
                    when {
                        open -> {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color(0x66000000))
                                    .pointerInput(Unit) { detectTapGestures { creating = false } },
                            )
                            CreateDiarySheet(
                                draft = lastOpenDraft[0],
                                vm = vm,
                                onClose = { creating = false },
                                onCreate = {
                                    focus.clearFocus()
                                    vm.create()
                                    creating = false
                                },
                                onOpenExisting = {
                                    creating = false
                                    open(it)
                                },
                                modifier = Modifier.align(Alignment.BottomCenter).statusBarsPadding().padding(top = 24.dp).then(morph),
                            )
                        }
                        shelf == null -> Unit
                        shelf.isEmpty() && pendingDelete == null && !showMap -> EmptyShelf(onStart = { creating = true }, buttonModifier = morph)
                        else -> ExtendedFloatingActionButton(
                            onClick = { creating = true },
                            icon = { Icon(Icons.Default.Add, contentDescription = null) },
                            text = { Text(stringResource(R.string.new_diary)) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp).then(morph),
                        )
                    }
                }
            }
        }

        if (!creating) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 56.dp, end = 4.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f).padding(horizontal = 24.dp)) {
                    val colours = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveContainerColor = MaterialTheme.colorScheme.surface,
                    )
                    SegmentedButton(!showMap, { showMap = false }, SegmentedButtonDefaults.itemShape(0, 2), colors = colours, icon = {}) {
                        Text(stringResource(R.string.tab_diaries))
                    }
                    SegmentedButton(showMap, { showMap = true }, SegmentedButtonDefaults.itemShape(1, 2), colors = colours, icon = {}) {
                        Text(stringResource(R.string.tab_map))
                    }
                }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp))
    }

    shelfCard(cards, renamingId)?.let { card ->
        RenameDialog(card.title, onDismiss = { renamingId = null }, onSave = {
            vm.rename(card.id, it)
            renamingId = null
        })
    }
    shelfCard(cards, recoveringId)?.let { card ->
        val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) vm.setCoverPhoto(card.id, uri)
        }
        ModalBottomSheet(onDismissRequest = { recoveringId = null }, containerColor = MaterialTheme.colorScheme.surface) {
            Text(
                stringResource(R.string.change_cover_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))
            CoverPicker(
                selected = card.coverStyle,
                photoUri = card.coverImageUri,
                previewTitle = card.title,
                onSelect = { vm.setCover(card.id, it) },
                onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentPadding = PaddingValues(horizontal = 16.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun shelfCard(cards: List<DiaryCard>?, id: Long?): DiaryCard? = id?.let { wanted -> cards?.firstOrNull { it.id == wanted } }

@Composable
private fun Shelf(
    cards: List<DiaryCard>,
    justCreated: Long?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    hiddenId: Long?,
    onBounds: (Long, Rect) -> Unit,
    onOpen: (Long) -> Unit,
    onRename: (DiaryCard) -> Unit,
    onChangeCover: (DiaryCard) -> Unit,
    onDuplicate: (DiaryCard) -> Unit,
    onDelete: (DiaryCard) -> Unit,
    onDropDone: (Long) -> Unit,
) {
    val rows = remember(cards) { cards.chunked(BOOKS_PER_SHELF) }
    val insets = WindowInsets.statusBars.asPaddingValues()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = insets.calculateTopPadding() + 68.dp, bottom = bottom + 104.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            Column(Modifier.padding(horizontal = 28.dp).padding(bottom = 20.dp)) {
                Text(stringResource(R.string.shelf_title), style = MaterialTheme.typography.displaySmall)
                Text(
                    pluralStringResource(R.plurals.shelf_count, cards.size, cards.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                )
            }
        }
        items(rows, key = { it.first().id }) { row ->
            Column(Modifier.animateItem()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 36.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    for (card in row) {
                        ShelfBook(
                            card = card,
                            isNew = card.id == justCreated,
                            hidden = card.id == hiddenId,
                            onBounds = { onBounds(card.id, it) },
                            onOpen = { onOpen(card.id) },
                            onRename = { onRename(card) },
                            onChangeCover = { onChangeCover(card) },
                            onDuplicate = { onDuplicate(card) },
                            onDelete = { onDelete(card) },
                            onDropDone = { onDropDone(card.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(BOOKS_PER_SHELF - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Plank(Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun ShelfBook(
    card: DiaryCard,
    isNew: Boolean,
    hidden: Boolean,
    onBounds: (Rect) -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onChangeCover: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDropDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }

    // C-7: a new book falls onto the shelf and bounces. 0 is above the shelf, 1 is at rest.
    val drop = remember { Animatable(if (isNew) 0f else 1f) }
    LaunchedEffect(isNew) {
        if (isNew) {
            drop.animateTo(1f, if (reducedMotion) tween(200) else spring(dampingRatio = 0.42f, stiffness = 260f))
            onDropDone()
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "press")

    val description = stringResource(R.string.cover_description, card.title)
    Box(modifier) {
        BookCover(
            title = card.title,
            placeLine = card.placeLine,
            dateLine = card.dateLine,
            style = card.coverStyle,
            imageUri = card.coverImageUri,
            modifier = Modifier
                .onGloballyPositioned { onBounds(it.boundsInRoot()) }
                .graphicsLayer {
                    val progress = drop.value
                    alpha = if (hidden) 0f else (progress * 3f).coerceIn(0f, 1f)
                    if (!reducedMotion) {
                        // The spring overshoots past 1; mirroring it keeps the book bouncing on the shelf, not through it.
                        translationY = -abs(1f - progress) * size.height * 0.9f
                        rotationY = -7f
                        cameraDistance = 14f * density
                    }
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .semantics { contentDescription = description }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onOpen,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                ),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            val items = listOf(
                R.string.menu_rename to onRename,
                R.string.menu_change_cover to onChangeCover,
                R.string.menu_duplicate to onDuplicate,
                R.string.menu_delete to onDelete,
            )
            for ((label, action) in items) {
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        menuOpen = false
                        action()
                    },
                )
            }
        }
    }
}

/** A wooden shelf board seen slightly from above: a lit top face, a darker front edge, and a shadow under it. */
@Composable
private fun Plank(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(26.dp)) {
        val top = 6.dp.toPx()
        val front = 10.dp.toPx()
        drawRect(Brush.verticalGradient(listOf(Color(0xFFB98F68), Wood), endY = top), size = Size(size.width, top))
        drawRect(Brush.verticalGradient(listOf(Wood, WoodDark), startY = top, endY = top + front), Offset(0f, top), Size(size.width, front))
        drawRect(
            Brush.verticalGradient(listOf(Color(0x40000000), Color.Transparent), startY = top + front, endY = size.height),
            Offset(0f, top + front),
            Size(size.width, size.height - top - front),
        )
    }
}

/** H-2: an empty shelf with the outlines of books that aren't there yet. */
@Composable
private fun EmptyShelf(onStart: () -> Unit, buttonModifier: Modifier) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val outline = MaterialTheme.colorScheme.outline
        Canvas(Modifier.fillMaxWidth(0.8f).height(150.dp)) {
            val dashed = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())))
            val book = Size(size.height * 0.62f, size.height * 0.84f)
            val floor = size.height
            val corner = CornerRadius(6.dp.toPx())
            val left = size.width * 0.5f - book.width * 1.15f
            drawRoundRect(outline, Offset(left, floor - book.height), book, corner, style = dashed)
            // The second book leans against the first.
            val leaning = left + book.width * 1.3f
            rotate(-10f, pivot = Offset(leaning, floor)) {
                drawRoundRect(outline, Offset(leaning, floor - book.height), book, corner, style = dashed)
            }
        }
        Plank()
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = InkSoft,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onStart, modifier = buttonModifier.heightIn(min = 52.dp), contentPadding = PaddingValues(horizontal = 28.dp)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.padding(4.dp))
            Text(stringResource(R.string.empty_cta))
        }
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.title_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(title) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
