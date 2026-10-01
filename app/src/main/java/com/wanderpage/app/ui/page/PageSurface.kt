package com.wanderpage.app.ui.page

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wanderpage.app.data.db.ElementEntity
import com.wanderpage.app.data.model.Background
import com.wanderpage.app.data.model.PAGE_WIDTH_UNITS
import com.wanderpage.app.data.model.PageFormat
import java.io.File
import kotlin.math.roundToInt

/**
 * Counts page images that are still loading, so the exporter can wait for the full-resolution files
 * before it captures the page.
 */
class ImageLoadTracker {
    var pending by mutableIntStateOf(0)
}

val LocalImageLoadTracker = compositionLocalOf<ImageLoadTracker?> { null }

/**
 * The one renderer for a page: the book, the zoomed page, the editor and the export all draw through it.
 *
 * Inside, the density is replaced so that **1.dp and 1.sp are one page unit** on the 1080-wide reference
 * page. Everything a page draws is therefore written in page units and scales with the page.
 *
 * @param element draws one element's content; the editor swaps in a text field for the block being typed.
 * @param onElementSize reports each element's unscaled size in page units, for hit-testing.
 * @param overlay extra content in page units, drawn above the elements and clipped to the page.
 */
@Composable
fun PageSurface(
    format: PageFormat,
    background: Background,
    elements: List<ElementEntity>,
    modifier: Modifier = Modifier,
    element: @Composable (ElementEntity) -> Unit = { ElementContent(it.payload) },
    elementModifier: @Composable (ElementEntity) -> Modifier = { Modifier },
    onElementSize: ((Long, Float, Float) -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier.aspectRatio(format.aspectRatio)) {
        val unit = constraints.maxWidth.toFloat() / PAGE_WIDTH_UNITS
        CompositionLocalProvider(LocalDensity provides Density(unit, 1f)) {
            Box(Modifier.requiredSize(PAGE_WIDTH_UNITS.dp, format.heightUnits.dp).clipToBounds()) {
                PageBackground(background)
                for (item in elements) {
                    key(item.id) {
                        Box(
                            Modifier
                                .centredAt(item.x, item.y)
                                .then(elementModifier(item))
                                .graphicsLayer {
                                    scaleX = if (item.flipped) -item.scale else item.scale
                                    scaleY = item.scale
                                    rotationZ = item.rotation
                                    alpha = item.opacity
                                }
                                .then(
                                    if (onElementSize == null) {
                                        Modifier
                                    } else {
                                        Modifier.onSizeChanged { onElementSize(item.id, it.width / unit, it.height / unit) }
                                    },
                                ),
                        ) {
                            element(item)
                        }
                    }
                }
                overlay()
            }
        }
    }
}

/** Lets the content take its natural size and puts its centre at ([x], [y]) page units. */
private fun Modifier.centredAt(x: Float, y: Float): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(0, 0) {
        placeable.place((x.dp.toPx() - placeable.width / 2f).roundToInt(), (y.dp.toPx() - placeable.height / 2f).roundToInt())
    }
}

@Composable
fun PageBackground(background: Background) {
    when (background) {
        is Background.Paper -> {
            val preset = paperPreset(background.textureId)
            Canvas(Modifier.fillMaxSize()) { drawPaper(preset, density) }
        }
        is Background.Solid -> Box(Modifier.fillMaxSize().background(Color(background.colour)))
        is Background.Photo -> Box(Modifier.fillMaxSize().background(Color(0xFF2A2623)), contentAlignment = Alignment.Center) {
            PageImage(
                path = background.uri,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = background.zoom
                        scaleY = background.zoom
                        translationX = background.offsetX.dp.toPx()
                        translationY = background.offsetY.dp.toPx()
                    }
                    .then(if (background.blur > 0f) Modifier.blur(background.blur.dp) else Modifier),
            )
            if (background.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = background.dim)))
        }
    }
}

/** A picture from app-private storage. Reports to the [ImageLoadTracker] when one is in scope. */
@Composable
fun PageImage(
    path: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
) {
    val settle = rememberLoadReporter(path)
    AsyncImage(
        model = File(path),
        contentDescription = null,
        contentScale = contentScale,
        colorFilter = colorFilter,
        onSuccess = { settle() },
        onError = { settle() },
        modifier = modifier,
    )
}

/** Marks one image as pending until the returned callback runs (or the image leaves the page). */
@Composable
fun rememberLoadReporter(path: String): () -> Unit {
    val tracker = LocalImageLoadTracker.current ?: return {}
    val settled = remember(path) { booleanArrayOf(false) }
    DisposableEffect(path, tracker) {
        tracker.pending++
        onDispose { if (!settled[0]) tracker.pending-- }
    }
    return {
        if (!settled[0]) {
            settled[0] = true
            tracker.pending--
        }
    }
}
