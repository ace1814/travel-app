package com.wanderpage.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanderpage.app.R
import com.wanderpage.app.data.model.Background
import com.wanderpage.app.data.model.ElementPayload
import com.wanderpage.app.data.model.FrameStyle
import com.wanderpage.app.data.model.TextBoxStyle
import com.wanderpage.app.ui.page.PageFonts
import com.wanderpage.app.ui.page.PaperPresets
import com.wanderpage.app.ui.page.SolidColours
import com.wanderpage.app.ui.page.TapePatterns
import com.wanderpage.app.ui.page.drawPaperSwatch
import com.wanderpage.app.ui.page.drawTape

/** T-3: the ink presets. */
private val Inks: List<Long> = listOf(0xFF1F1B18, 0xFF6B4A2F, 0xFF23395B, 0xFF2F5D3A, 0xFFB3392F, 0xFFFFFFFF, 0xFFB5533C, 0xFFC99A2E, 0xFF7A4E8C)

private val PanelPadding = PaddingValues(horizontal = 16.dp)

/** One button in the editor's bottom row: an icon over a short label, at least 48 dp to touch. */
@Composable
fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit, selected: Boolean = false, enabled: Boolean = true) {
    val colour = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colour)
        Text(label, color = colour, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LabelledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(PanelPadding), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp))
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = range,
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
        )
    }
}

/** T-2: every font, each previewed in its own face. Tapping one changes the selected text straight away. */
@Composable
fun FontPanel(text: ElementPayload.Text, vm: EditorViewModel) {
    LazyRow(contentPadding = PanelPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PageFonts) { font ->
            val selected = font.id == text.fontId
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .clickable { vm.changePayload<ElementPayload.Text> { it.copy(fontId = font.id) } }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text.text.take(12).ifBlank { "Hello" }, fontFamily = font.family, fontSize = (26 * font.sizeFactor).sp, maxLines = 1)
                Text(font.label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ColourPanel(text: ElementPayload.Text, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LazyRow(contentPadding = PanelPadding, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(Inks) { ink ->
                Swatch(Color(ink), selected = ink == text.colour) { vm.changePayload<ElementPayload.Text> { it.copy(colour = ink) } }
            }
        }
        // The picker: any hue, at an ink-like depth.
        var hue by remember { mutableIntStateOf(20) }
        Row(Modifier.fillMaxWidth().padding(PanelPadding), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color.hsv(hue.toFloat(), 0.7f, 0.6f)))
            Slider(
                value = hue.toFloat(),
                onValueChange = { value ->
                    hue = value.toInt()
                    val picked = Color.hsv(value, 0.7f, 0.6f).toArgb().toLong() and 0xFFFFFFFF
                    vm.slidePayload<ElementPayload.Text>("colour") { it.copy(colour = picked) }
                },
                onValueChangeFinished = vm::commit,
                valueRange = 0f..359f,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun Swatch(colour: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(colour)
            .clickable(onClick = onClick),
    )
}

@Composable
fun SizePanel(text: ElementPayload.Text, vm: EditorViewModel) {
    LabelledSlider(stringResource(R.string.tool_size), text.size, 28f..280f, { size -> vm.slidePayload<ElementPayload.Text>("size") { it.copy(size = size) } }, vm::commit)
}

@Composable
fun SpacingPanel(text: ElementPayload.Text, vm: EditorViewModel) {
    Column {
        LabelledSlider(stringResource(R.string.spacing_line), text.lineSpacing, 0.8f..2.2f, { v -> vm.slidePayload<ElementPayload.Text>("line") { it.copy(lineSpacing = v) } }, vm::commit)
        LabelledSlider(stringResource(R.string.spacing_letter), text.letterSpacing, -3f..24f, { v -> vm.slidePayload<ElementPayload.Text>("letter") { it.copy(letterSpacing = v) } }, vm::commit)
    }
}

@Composable
private fun <T> ChoiceRow(options: List<Pair<T, Int>>, selected: T, onPick: (T) -> Unit) {
    LazyRow(contentPadding = PanelPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onPick(value) }, label = { Text(stringResource(label)) })
        }
    }
}

@Composable
fun StylePanel(text: ElementPayload.Text, vm: EditorViewModel) {
    ChoiceRow(
        listOf(
            TextBoxStyle.NONE to R.string.style_none,
            TextBoxStyle.HIGHLIGHTER to R.string.style_highlighter,
            TextBoxStyle.TORN_NOTE to R.string.style_torn,
            TextBoxStyle.GRID_CARD to R.string.style_grid,
            TextBoxStyle.LINED_CARD to R.string.style_lined,
        ),
        text.boxStyle,
    ) { style -> vm.changePayload<ElementPayload.Text> { it.copy(boxStyle = style) } }
}

@Composable
fun FramePanel(photo: ElementPayload.Photo, vm: EditorViewModel) {
    ChoiceRow(
        listOf(
            FrameStyle.PLAIN to R.string.frame_plain,
            FrameStyle.POLAROID to R.string.frame_polaroid,
            FrameStyle.FILM_STRIP to R.string.frame_film,
            FrameStyle.TORN to R.string.frame_torn,
            FrameStyle.STAMP to R.string.frame_stamp,
        ),
        photo.frameStyle,
    ) { frame -> vm.changePayload<ElementPayload.Photo> { it.copy(frameStyle = frame) } }
}

@Composable
fun CaptionPanel(photo: ElementPayload.Photo, vm: EditorViewModel) {
    OutlinedTextField(
        value = photo.caption.orEmpty(),
        onValueChange = { caption -> vm.slidePayload<ElementPayload.Photo>("caption") { it.copy(caption = caption.take(40)) } },
        placeholder = { Text(stringResource(R.string.caption_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(PanelPadding),
    )
}

@Composable
fun OpacityPanel(opacity: Float, vm: EditorViewModel) {
    LabelledSlider(stringResource(R.string.tool_opacity), opacity, 0.1f..1f, { v -> vm.slideSelected("opacity") { it.copy(opacity = v) } }, vm::commit)
}

/** B-1 and B-2: papers, solid colours, or a photo with zoom, blur and dim. */
@Composable
fun BackgroundPanel(background: Background, vm: EditorViewModel, onPickPhoto: () -> Unit) {
    var tab by remember { mutableIntStateOf(if (background is Background.Photo) 2 else if (background is Background.Solid) 1 else 0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(PanelPadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.bg_paper, R.string.bg_colour, R.string.bg_photo).forEachIndexed { index, label ->
                FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(stringResource(label)) })
            }
        }
        when (tab) {
            0 -> LazyRow(contentPadding = PanelPadding, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(PaperPresets) { preset ->
                    val selected = (background as? Background.Paper)?.textureId == preset.id
                    Column(
                        Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.RadioButton) { vm.setBackground(Background.Paper(preset.id)) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Canvas(
                            Modifier
                                .size(64.dp, 80.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                        ) { drawPaperSwatch(preset) }
                        Text(preset.label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            1 -> LazyRow(contentPadding = PanelPadding, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(60.dp)) {
                items(SolidColours) { colour ->
                    Swatch(Color(colour), selected = (background as? Background.Solid)?.colour == colour) { vm.setBackground(Background.Solid(colour)) }
                }
            }
            else -> {
                val photo = background as? Background.Photo
                Column {
                    OutlinedButton(onClick = onPickPhoto, modifier = Modifier.padding(PanelPadding)) {
                        Text(stringResource(if (photo == null) R.string.bg_choose_photo else R.string.bg_change_photo))
                    }
                    if (photo != null) {
                        LabelledSlider(stringResource(R.string.bg_zoom), photo.zoom, 1f..4f, { v -> vm.slideBackground("zoom") { it.copy(zoom = v) } }, vm::commit)
                        LabelledSlider(stringResource(R.string.bg_blur), photo.blur, 0f..40f, { v -> vm.slideBackground("blur") { it.copy(blur = v) } }, vm::commit)
                        LabelledSlider(stringResource(R.string.bg_dim), photo.dim, 0f..0.8f, { v -> vm.slideBackground("dim") { it.copy(dim = v) } }, vm::commit)
                        Text(
                            stringResource(R.string.bg_move_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(PanelPadding),
                        )
                    }
                }
            }
        }
    }
}

/** S-1: the tape patterns. Picking one arms the canvas for drawing a strip. */
@Composable
fun TapePanel(activePattern: String?, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.tape_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(PanelPadding),
        )
        LazyRow(contentPadding = PanelPadding, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(TapePatterns) { pattern ->
                val selected = pattern.id == activePattern
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .clickable { onPick(pattern.id) }
                        .semantics { contentDescription = pattern.label }
                        .padding(horizontal = 10.dp, vertical = 14.dp),
                ) {
                    Canvas(Modifier.size(104.dp, 24.dp)) { drawTape(pattern) }
                }
            }
        }
    }
}
