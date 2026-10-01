package com.wanderpage.app.ui.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.wanderpage.app.data.db.ElementEntity
import com.wanderpage.app.data.model.ElementPayload
import com.wanderpage.app.data.model.PAGE_WIDTH_UNITS
import com.wanderpage.app.data.model.PageFormat
import com.wanderpage.app.ui.page.ElementContent
import com.wanderpage.app.ui.page.PageSurface
import com.wanderpage.app.ui.page.TextElement
import com.wanderpage.app.ui.page.paperShadow
import com.wanderpage.app.ui.page.textStyle
import com.wanderpage.app.ui.theme.LocalReducedMotion
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** What a one-finger drag on the page does when it doesn't start on an element. */
sealed interface CanvasTool {
    data object Select : CanvasTool

    /** Drag out a strip of washi tape (S-1). */
    data class Tape(val patternId: String) : CanvasTool

    /** Move and pinch the photo background (B-2). */
    data object MoveBackground : CanvasTool
}

private enum class Guide { CENTRE_X, CENTRE_Y, LEFT, RIGHT, TOP, BOTTOM, ANGLE }

private const val SNAP_DISTANCE = 14f
private const val SNAP_DEGREES = 4f
private const val TAPE_WIDTH = 64f
private val SnapAngles = floatArrayOf(-180f, -90f, -15f, 0f, 15f, 90f, 180f)

// Instagram covers these bands of a story with its own controls (PRD §7).
private const val STORY_SAFE_TOP = 250f
private const val STORY_SAFE_BOTTOM = 340f

/**
 * The page being edited, with all of its touch handling (PRD §6.6):
 * tap to select, drag to move, pinch and twist to scale and rotate, with snapping and haptic ticks.
 *
 * Touches are handled in one place, on the page, and hit-tested against the elements by hand. That keeps
 * a two-finger gesture working on the selected element even when the fingers land outside it.
 */
@Composable
fun EditorCanvas(
    state: EditorState,
    vm: EditorViewModel,
    tool: CanvasTool,
    onToolDone: () -> Unit,
    onTapEmpty: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val reducedMotion = LocalReducedMotion.current
    val latest by rememberUpdatedState(state)
    val latestTool by rememberUpdatedState(tool)
    // Unscaled element sizes in page units, as reported by the renderer.
    val sizes = remember { mutableStateMapOf<Long, Size>() }
    var guides by remember { mutableStateOf(emptySet<Guide>()) }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val aspect = state.format.aspectRatio
        val pageWidth = if (maxWidth / maxHeight > aspect) maxHeight * aspect else maxWidth
        val unit = with(LocalDensity.current) { pageWidth.toPx() } / PAGE_WIDTH_UNITS
        // Thin things like tape get a minimum grab area of 48 dp across.
        val slack = with(LocalDensity.current) { 24.dp.toPx() } / unit

        fun hitTest(point: Offset): ElementEntity? = latest.elements.lastOrNull { element ->
            val size = sizes[element.id] ?: return@lastOrNull false
            val radians = Math.toRadians(-element.rotation.toDouble())
            val dx = point.x - element.x
            val dy = point.y - element.y
            val localX = (dx * cos(radians) - dy * sin(radians)).toFloat()
            val localY = (dx * sin(radians) + dy * cos(radians)).toFloat()
            abs(localX) <= maxOf(size.width * element.scale / 2, slack) && abs(localY) <= maxOf(size.height * element.scale / 2, slack)
        }

        Box(
            Modifier
                .size(pageWidth, pageWidth / aspect)
                .pointerInput(unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val start = down.position / unit
                        val before = latest

                        if (before.editingTextId != null) {
                            // A touch outside the text field ends typing.
                            vm.finishTextEdit()
                            do { val event = awaitPointerEvent() } while (event.changes.any { it.pressed })
                            return@awaitEachGesture
                        }

                        val activeTool = latestTool
                        if (activeTool is CanvasTool.Tape) {
                            var tapeId: Long? = null
                            var end = start
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.first()
                                end = change.position / unit
                                val length = (end - start).getDistance()
                                if (length > 30f) {
                                    val centre = (start + end) / 2f
                                    val angle = Math.toDegrees(atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())).toFloat()
                                    val id = tapeId ?: vm.add(
                                        ElementPayload.Tape(activeTool.patternId, length, TAPE_WIDTH), centre.x, centre.y, angle,
                                    ).also { tapeId = it }
                                    vm.update(id) {
                                        it.copy(x = centre.x, y = centre.y, rotation = angle, payload = ElementPayload.Tape(activeTool.patternId, length, TAPE_WIDTH))
                                    }
                                }
                                change.consume()
                            } while (event.changes.any { it.pressed })
                            // A plain tap puts down a short strip where the finger landed.
                            if (tapeId == null) vm.add(ElementPayload.Tape(activeTool.patternId, 340f, TAPE_WIDTH), start.x, start.y, -6f)
                            vm.commit()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToolDone()
                            return@awaitEachGesture
                        }

                        val hit = hitTest(start)
                        if (hit != null && hit.id != before.selectedId) {
                            vm.select(hit.id)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        // Positions before snapping. Snapping is applied on top, so an element can be dragged out of a snap.
                        val grabbed = hit ?: before.selected
                        var rawX = grabbed?.x ?: 0f
                        var rawY = grabbed?.y ?: 0f
                        var rawScale = grabbed?.scale ?: 1f
                        var rawRotation = grabbed?.rotation ?: 0f
                        var travelled = Offset.Zero
                        var moving = false
                        var began = false

                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            val pan = event.calculatePan()
                            if (!moving) {
                                travelled += pan
                                moving = travelled.getDistance() > viewConfiguration.touchSlop || fingers > 1
                            }
                            if (moving) {
                                // One finger moves only what it landed on; two fingers work on the selection from anywhere.
                                val target = if (fingers > 1) grabbed else hit
                                if (target != null && !target.locked) {
                                    if (!began) {
                                        vm.begin()
                                        began = true
                                    }
                                    rawX += pan.x / unit
                                    rawY += pan.y / unit
                                    rawScale = (rawScale * event.calculateZoom()).coerceIn(0.15f, 8f)
                                    rawRotation += event.calculateRotation()
                                    val size = sizes[target.id] ?: Size.Zero
                                    val snap = snap(rawX, rawY, rawRotation, size * rawScale, before.height)
                                    if ((snap.guides - guides).isNotEmpty()) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    guides = snap.guides
                                    vm.update(target.id) { it.copy(x = snap.x, y = snap.y, rotation = snap.rotation, scale = rawScale) }
                                } else if (target == null && latestTool == CanvasTool.MoveBackground) {
                                    val zoom = event.calculateZoom()
                                    vm.slideBackground("move") {
                                        it.copy(offsetX = it.offsetX + pan.x / unit, offsetY = it.offsetY + pan.y / unit, zoom = (it.zoom * zoom).coerceIn(1f, 4f))
                                    }
                                    began = true
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })

                        guides = emptySet()
                        when {
                            began -> vm.commit()
                            moving -> Unit
                            hit == null -> {
                                vm.select(null)
                                onTapEmpty()
                            }
                            // A second tap on a selected text block starts typing in it (T-1).
                            hit.id == before.selectedId && hit.payload is ElementPayload.Text && !hit.locked -> vm.startTextEdit(hit.id)
                        }
                    }
                },
        ) {
            PageSurface(
                format = state.format,
                background = state.background,
                elements = state.elements,
                modifier = Modifier.fillMaxSize().paperShadow(strength = 1.6f),
                onElementSize = { id, width, height -> sizes[id] = Size(width, height) },
                elementModifier = { element ->
                    // Selected elements lift a little; new ones drop in with a small overshoot (PRD §8).
                    val lift by animateFloatAsState(if (element.id == state.selectedId) 1.03f else 1f, tween(150), label = "lift")
                    val drop = remember(element.id) { Animatable(if (element.id == state.freshId && !reducedMotion) 0f else 1f) }
                    LaunchedEffect(element.id) {
                        if (drop.value < 1f) drop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 320f))
                    }
                    Modifier.graphicsLayer {
                        val scale = lift * lerp(1.3f, 1f, drop.value)
                        scaleX = scale
                        scaleY = scale
                        alpha = (drop.value * 2.5f).coerceIn(0f, 1f)
                    }
                },
                element = { element ->
                    val payload = element.payload
                    if (element.id == state.editingTextId && payload is ElementPayload.Text) {
                        TextElement(payload) { PageTextField(payload, onChange = { vm.setText(element.id, it) }) }
                    } else {
                        ElementContent(payload)
                    }
                },
            )

            // Selection frame and guides, in page units, drawn over the page and not clipped to it.
            CompositionLocalProvider(LocalDensity provides Density(unit, 1f)) {
                Canvas(Modifier.requiredSize(PAGE_WIDTH_UNITS.dp, state.format.heightUnits.dp)) {
                    val accent = Color(0xFFB5533C)
                    val hairline = 3.dp.toPx() / 1.5f
                    if (state.format == PageFormat.STORY) {
                        val dashes = PathEffect.dashPathEffect(floatArrayOf(18.dp.toPx(), 14.dp.toPx()))
                        for (y in floatArrayOf(STORY_SAFE_TOP, state.height - STORY_SAFE_BOTTOM)) {
                            drawLine(Color(0x99000000), Offset(0f, y.dp.toPx()), Offset(size.width, y.dp.toPx()), hairline, pathEffect = dashes)
                        }
                    }
                    if (Guide.CENTRE_X in guides) drawLine(accent, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), hairline)
                    if (Guide.CENTRE_Y in guides) drawLine(accent, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), hairline)
                    if (Guide.LEFT in guides) drawLine(accent, Offset(0f, 0f), Offset(0f, size.height), hairline * 2)
                    if (Guide.RIGHT in guides) drawLine(accent, Offset(size.width, 0f), Offset(size.width, size.height), hairline * 2)
                    if (Guide.TOP in guides) drawLine(accent, Offset(0f, 0f), Offset(size.width, 0f), hairline * 2)
                    if (Guide.BOTTOM in guides) drawLine(accent, Offset(0f, size.height), Offset(size.width, size.height), hairline * 2)

                    val selected = state.selected
                    val box = selected?.let { sizes[it.id] }
                    if (selected != null && box != null && state.editingTextId == null) {
                        val centre = Offset(selected.x.dp.toPx(), selected.y.dp.toPx())
                        val frame = Size((box.width * selected.scale + 20f).dp.toPx(), (box.height * selected.scale + 20f).dp.toPx())
                        val topLeft = centre - Offset(frame.width / 2, frame.height / 2)
                        rotate(selected.rotation, centre) {
                            val colour = if (Guide.ANGLE in guides) accent else Color(0xFF3B6FB5)
                            drawRoundRect(Color.White, topLeft, frame, CornerRadius(6.dp.toPx()), Stroke(hairline * 2.2f))
                            drawRoundRect(
                                colour,
                                topLeft,
                                frame,
                                CornerRadius(6.dp.toPx()),
                                Stroke(hairline, pathEffect = if (selected.locked) PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 10.dp.toPx())) else null),
                            )
                            for (corner in listOf(topLeft, topLeft + Offset(frame.width, 0f), topLeft + Offset(0f, frame.height), topLeft + Offset(frame.width, frame.height))) {
                                drawCircle(Color.White, 9.dp.toPx(), corner)
                                drawCircle(colour, 6.dp.toPx(), corner)
                            }
                        }
                    }
                }
            }
        }
    }
}

private class Snapped(val x: Float, val y: Float, val rotation: Float, val guides: Set<Guide>)

/**
 * Soft snapping: the page centre and edges for position, and 0°, ±15°, ±90° and 180° for rotation.
 * [size] is the element's scaled size; its rotation is ignored for edge snapping.
 */
private fun snap(x: Float, y: Float, rotation: Float, size: Size, pageHeight: Float): Snapped {
    val guides = mutableSetOf<Guide>()
    val pageWidth = PAGE_WIDTH_UNITS.toFloat()
    var snappedX = x
    var snappedY = y
    when {
        abs(x - pageWidth / 2) < SNAP_DISTANCE -> { snappedX = pageWidth / 2; guides += Guide.CENTRE_X }
        abs(x - size.width / 2) < SNAP_DISTANCE -> { snappedX = size.width / 2; guides += Guide.LEFT }
        abs(x + size.width / 2 - pageWidth) < SNAP_DISTANCE -> { snappedX = pageWidth - size.width / 2; guides += Guide.RIGHT }
    }
    when {
        abs(y - pageHeight / 2) < SNAP_DISTANCE -> { snappedY = pageHeight / 2; guides += Guide.CENTRE_Y }
        abs(y - size.height / 2) < SNAP_DISTANCE -> { snappedY = size.height / 2; guides += Guide.TOP }
        abs(y + size.height / 2 - pageHeight) < SNAP_DISTANCE -> { snappedY = pageHeight - size.height / 2; guides += Guide.BOTTOM }
    }
    // Keep the angle in -180..180 so the snap targets cover every way of reaching them.
    var angle = rotation % 360f
    if (angle > 180f) angle -= 360f
    if (angle < -180f) angle += 360f
    val target = SnapAngles.firstOrNull { abs(angle - it) < SNAP_DEGREES }
    if (target != null) guides += Guide.ANGLE
    return Snapped(snappedX, snappedY, target ?: angle, guides)
}

/** The text field shown in place of a text block while it is being typed into. Same style, so nothing shifts. */
@Composable
private fun PageTextField(payload: ElementPayload.Text, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    var value by remember { mutableStateOf(TextFieldValue(payload.text, TextRange(payload.text.length))) }
    BasicTextField(
        value = value,
        onValueChange = {
            value = it
            onChange(it.text)
        },
        textStyle = payload.textStyle(),
        cursorBrush = SolidColor(Color(payload.colour)),
        modifier = Modifier.focusRequester(focus).widthIn(min = 120.dp),
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}
