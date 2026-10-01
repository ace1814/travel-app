package com.wanderpage.app.ui.editor

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BorderStyle
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterFrames
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.wanderpage.app.R
import com.wanderpage.app.data.db.PageEntity
import com.wanderpage.app.data.db.PageWithElements
import com.wanderpage.app.data.model.Background
import com.wanderpage.app.data.model.ElementPayload
import com.wanderpage.app.data.model.FrameStyle
import com.wanderpage.app.data.model.ReceiptEdge
import com.wanderpage.app.data.model.StickerOutline
import com.wanderpage.app.data.model.TextAlignment
import com.wanderpage.app.ui.export.ExportSheet
import java.io.File

private enum class Panel { FONT, COLOUR, SIZE, SPACING, STYLE, FRAME, CAPTION, OPACITY, BACKGROUND, TAPE }

/** What a picked or captured picture is for. */
private enum class PictureUse { PHOTO, CUTOUT, BACKGROUND, RECEIPT }

private val Desk = Brush.verticalGradient(listOf(Color(0xFF55443A), Color(0xFF2F2620)))
private val DeskInk = Color(0xFFF4EBDD)

/** The page editor (PRD §6.6). */
@Composable
fun EditorScreen(pageId: Long, onDone: () -> Unit) {
    val vm: EditorViewModel = viewModel(key = "editor-$pageId", factory = EditorViewModel.factory(pageId))
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var panel by remember { mutableStateOf<Panel?>(null) }
    var tapePattern by remember { mutableStateOf<String?>(null) }
    var pictureUse by rememberSaveable { mutableStateOf(PictureUse.PHOTO) }
    var sourceMenu by remember { mutableStateOf<PictureUse?>(null) }
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    var cutOutSource by remember { mutableStateOf<Uri?>(null) }
    var refining by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    LaunchedEffect(pageId) { vm.load() }
    // Panels belong to what is selected, so a new selection starts with them closed.
    LaunchedEffect(state.selectedId) {
        if (state.selectedId != null || (panel != Panel.BACKGROUND && panel != Panel.TAPE)) panel = null
        if (state.selectedId != null) tapePattern = null
    }

    fun usePicture(uri: Uri) {
        when (pictureUse) {
            PictureUse.PHOTO -> vm.addPhoto(uri)
            PictureUse.CUTOUT -> cutOutSource = uri
            PictureUse.BACKGROUND -> vm.setBackgroundPhoto(uri)
            PictureUse.RECEIPT -> vm.addReceipt(uri)
        }
    }
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::usePicture) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val path = cameraFile
        if (taken && path != null) usePicture(Uri.fromFile(File(path)))
    }
    val scanReceipt = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.firstOrNull()?.imageUri?.let(vm::addReceipt)
        }
    }
    fun fromGallery(use: PictureUse) {
        pictureUse = use
        pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    fun fromCamera(use: PictureUse) {
        pictureUse = use
        val file = File(File(context.cacheDir, "shared").apply { mkdirs() }, "capture-${System.currentTimeMillis()}.jpg")
        cameraFile = file.absolutePath
        runCatching { takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file)) }
    }
    fun startScan() {
        // R-1 and R-3: the scanner finds the edges, straightens and crops, and can also import from the gallery.
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        val activity = context as? Activity ?: return
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { scanReceipt.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener {
                // Without the scanner a receipt can still come from a photo; it just isn't straightened or cropped.
                Toast.makeText(context, R.string.receipt_unavailable, Toast.LENGTH_LONG).show()
                fromGallery(PictureUse.RECEIPT)
            }
    }

    fun done() {
        vm.finishTextEdit()
        onDone()
    }
    BackHandler {
        when {
            state.editingTextId != null -> vm.finishTextEdit()
            tapePattern != null -> tapePattern = null
            panel != null -> panel = null
            state.selectedId != null -> vm.select(null)
            else -> done()
        }
    }

    val tool = when {
        tapePattern != null -> CanvasTool.Tape(tapePattern!!)
        panel == Panel.BACKGROUND && state.background is Background.Photo -> CanvasTool.MoveBackground
        else -> CanvasTool.Select
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Desk)
            .pointerInput(Unit) { awaitEachGesture { awaitPointerEvent().changes.forEach { it.consume() } } }
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::done) { Icon(Icons.Default.Check, stringResource(R.string.editor_done), tint = DeskInk) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = vm::undo, enabled = state.canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.editor_undo), tint = DeskInk.copy(alpha = if (state.canUndo) 1f else 0.35f))
            }
            IconButton(onClick = vm::redo, enabled = state.canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.editor_redo), tint = DeskInk.copy(alpha = if (state.canRedo) 1f else 0.35f))
            }
            IconButton(onClick = {
                vm.finishTextEdit()
                sharing = true
            }) { Icon(Icons.Default.IosShare, stringResource(R.string.page_share), tint = DeskInk) }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.loaded) {
                EditorCanvas(
                    state = state,
                    vm = vm,
                    tool = tool,
                    onToolDone = { tapePattern = null },
                    onTapEmpty = { if (panel != Panel.BACKGROUND && panel != Panel.TAPE) panel = null },
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            if (tapePattern != null) {
                Text(
                    stringResource(R.string.tape_active_hint),
                    color = DeskInk,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                )
            }
            if (state.importing) {
                Row(Modifier.align(Alignment.Center).background(Color(0xCC000000), RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Text(stringResource(R.string.importing), color = Color.White, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }

        // While typing, the keyboard takes the bottom of the screen; the tools come back when it closes.
        if (state.editingTextId == null) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    val selected = state.selected
                    AnimatedContent(panel, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "panel") { open ->
                        Column(Modifier.fillMaxWidth()) {
                            val payload = selected?.payload
                            if (open != null) Spacer(Modifier.size(12.dp))
                            when (open) {
                                Panel.FONT -> (payload as? ElementPayload.Text)?.let { FontPanel(it, vm) }
                                Panel.COLOUR -> (payload as? ElementPayload.Text)?.let { ColourPanel(it, vm) }
                                Panel.SIZE -> (payload as? ElementPayload.Text)?.let { SizePanel(it, vm) }
                                Panel.SPACING -> (payload as? ElementPayload.Text)?.let { SpacingPanel(it, vm) }
                                Panel.STYLE -> (payload as? ElementPayload.Text)?.let { StylePanel(it, vm) }
                                Panel.FRAME -> (payload as? ElementPayload.Photo)?.let { FramePanel(it, vm) }
                                Panel.CAPTION -> (payload as? ElementPayload.Photo)?.let { CaptionPanel(it, vm) }
                                Panel.OPACITY -> selected?.let { OpacityPanel(it.opacity, vm) }
                                Panel.BACKGROUND -> BackgroundPanel(state.background, vm, onPickPhoto = { fromGallery(PictureUse.BACKGROUND) })
                                Panel.TAPE -> TapePanel(tapePattern, onPick = { tapePattern = it })
                                null -> Unit
                            }
                            if (open != null) {
                                Spacer(Modifier.size(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        fun toggle(target: Panel) {
                            panel = if (panel == target) null else target
                        }
                        if (selected == null) {
                            ToolButton(Icons.Default.TextFields, stringResource(R.string.add_text), onClick = {
                                panel = null
                                vm.addText()
                            })
                            Box {
                                ToolButton(Icons.Default.Image, stringResource(R.string.add_photo), onClick = { sourceMenu = PictureUse.PHOTO })
                                SourceMenu(sourceMenu == PictureUse.PHOTO, { sourceMenu = null }, { fromGallery(PictureUse.PHOTO) }, { fromCamera(PictureUse.PHOTO) })
                            }
                            Box {
                                ToolButton(Icons.Default.ContentCut, stringResource(R.string.add_cutout), onClick = { sourceMenu = PictureUse.CUTOUT })
                                SourceMenu(sourceMenu == PictureUse.CUTOUT, { sourceMenu = null }, { fromGallery(PictureUse.CUTOUT) }, { fromCamera(PictureUse.CUTOUT) })
                            }
                            ToolButton(Icons.AutoMirrored.Filled.ReceiptLong, stringResource(R.string.add_receipt), onClick = ::startScan)
                            ToolButton(Icons.Default.Texture, stringResource(R.string.add_background), selected = panel == Panel.BACKGROUND, onClick = {
                                tapePattern = null
                                toggle(Panel.BACKGROUND)
                            })
                            ToolButton(Icons.Default.Straighten, stringResource(R.string.add_tape), selected = panel == Panel.TAPE, onClick = {
                                if (panel == Panel.TAPE) tapePattern = null
                                toggle(Panel.TAPE)
                            })
                        } else {
                            when (val payload = selected.payload) {
                                is ElementPayload.Text -> {
                                    ToolButton(Icons.Default.Keyboard, stringResource(R.string.tool_edit_text), enabled = !selected.locked, onClick = { vm.startTextEdit(selected.id) })
                                    ToolButton(Icons.Default.FontDownload, stringResource(R.string.tool_font), selected = panel == Panel.FONT, onClick = { toggle(Panel.FONT) })
                                    ToolButton(Icons.Default.Palette, stringResource(R.string.tool_colour), selected = panel == Panel.COLOUR, onClick = { toggle(Panel.COLOUR) })
                                    ToolButton(Icons.Default.FormatSize, stringResource(R.string.tool_size), selected = panel == Panel.SIZE, onClick = { toggle(Panel.SIZE) })
                                    val alignIcon = when (payload.align) {
                                        TextAlignment.START -> Icons.AutoMirrored.Filled.FormatAlignLeft
                                        TextAlignment.CENTER -> Icons.Default.FormatAlignCenter
                                        TextAlignment.END -> Icons.Default.FormatAlignRight
                                    }
                                    ToolButton(alignIcon, stringResource(R.string.tool_align), onClick = {
                                        vm.changePayload<ElementPayload.Text> { it.copy(align = TextAlignment.entries[(it.align.ordinal + 1) % 3]) }
                                    })
                                    ToolButton(Icons.Default.FormatLineSpacing, stringResource(R.string.tool_spacing), selected = panel == Panel.SPACING, onClick = { toggle(Panel.SPACING) })
                                    ToolButton(Icons.Default.StickyNote2, stringResource(R.string.tool_style), selected = panel == Panel.STYLE, onClick = { toggle(Panel.STYLE) })
                                }
                                is ElementPayload.Photo -> {
                                    ToolButton(Icons.Default.FilterFrames, stringResource(R.string.tool_frame), selected = panel == Panel.FRAME, onClick = { toggle(Panel.FRAME) })
                                    if (payload.frameStyle == FrameStyle.POLAROID) {
                                        ToolButton(Icons.Default.Subtitles, stringResource(R.string.tool_caption), selected = panel == Panel.CAPTION, onClick = { toggle(Panel.CAPTION) })
                                    }
                                }
                                is ElementPayload.CutOut -> {
                                    ToolButton(Icons.Default.BorderStyle, stringResource(R.string.tool_outline), selected = payload.outline != StickerOutline.NONE, onClick = {
                                        vm.changePayload<ElementPayload.CutOut> { it.copy(outline = StickerOutline.entries[(it.outline.ordinal + 1) % 3]) }
                                    })
                                    ToolButton(Icons.Default.Tonality, stringResource(R.string.tool_shadow), selected = payload.shadow, onClick = {
                                        vm.changePayload<ElementPayload.CutOut> { it.copy(shadow = !it.shadow) }
                                    })
                                    ToolButton(Icons.Default.Brush, stringResource(R.string.tool_refine), onClick = { refining = true })
                                }
                                is ElementPayload.Receipt -> ToolButton(Icons.Default.AutoFixHigh, stringResource(R.string.tool_edge), onClick = {
                                    vm.changePayload<ElementPayload.Receipt> { it.copy(edge = ReceiptEdge.entries[(it.edge.ordinal + 1) % 3]) }
                                })
                                is ElementPayload.Tape -> Unit
                            }
                            ToolButton(Icons.Default.Opacity, stringResource(R.string.tool_opacity), selected = panel == Panel.OPACITY, onClick = { toggle(Panel.OPACITY) })
                            ToolButton(Icons.Default.FlipToFront, stringResource(R.string.tool_forward), onClick = { vm.reorderSelected(1) })
                            ToolButton(Icons.Default.FlipToBack, stringResource(R.string.tool_backward), onClick = { vm.reorderSelected(-1) })
                            ToolButton(Icons.Default.ContentCopy, stringResource(R.string.tool_duplicate), onClick = vm::duplicateSelected)
                            ToolButton(Icons.Default.Flip, stringResource(R.string.tool_flip), onClick = { vm.changeSelected { it.copy(flipped = !it.flipped) } })
                            ToolButton(
                                if (selected.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                                stringResource(if (selected.locked) R.string.tool_unlock else R.string.tool_lock),
                                selected = selected.locked,
                                onClick = { vm.changeSelected { it.copy(locked = !it.locked) } },
                            )
                            ToolButton(Icons.Default.Delete, stringResource(R.string.tool_delete), onClick = vm::deleteSelected)
                        }
                    }
                }
            }
        }
    }

    cutOutSource?.let { source ->
        CutOutFlow(
            source = source,
            onCutOut = {
                cutOutSource = null
                vm.add(it)
            },
            onKeepPhoto = {
                cutOutSource = null
                vm.addPhoto(source)
            },
            onCancel = { cutOutSource = null },
        )
    }
    val cutOut = state.selected?.payload as? ElementPayload.CutOut
    if (refining && cutOut != null) {
        RefineScreen(
            payload = cutOut,
            onDone = { refined ->
                refining = false
                vm.changePayload<ElementPayload.CutOut> { refined }
            },
            onCancel = { refining = false },
        )
    }
    if (sharing) {
        ExportSheet(
            PageWithElements(PageEntity(pageId, 0, 0, state.format, state.background, null, 0), state.elements),
            onDismiss = { sharing = false },
        )
    }
}

@Composable
private fun SourceMenu(expanded: Boolean, onDismiss: () -> Unit, onGallery: () -> Unit, onCamera: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.source_gallery)) },
            onClick = {
                onDismiss()
                onGallery()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.source_camera)) },
            onClick = {
                onDismiss()
                onCamera()
            },
        )
    }
}
