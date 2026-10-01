package com.wanderpage.app.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wanderpage.app.R
import com.wanderpage.app.container
import com.wanderpage.app.data.MapViewport
import com.wanderpage.app.data.db.PageWithElements
import com.wanderpage.app.data.model.CoverStyle
import com.wanderpage.app.ui.home.DiaryCard
import com.wanderpage.app.ui.home.pinColours
import com.wanderpage.app.ui.page.PageSurface
import com.wanderpage.app.ui.page.paperShadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val SOURCE = "diaries"
private const val PIN_LAYER = "diary-pins"
private const val CLUSTER_LAYER = "diary-clusters"
private const val CLUSTER_IMAGE = "cluster"

private class MapHandle(var map: MapLibreMap? = null, var style: Style? = null, var positioned: Boolean = false)

/**
 * Home in map view (PRD §6.2): a paper-styled world map where every city of every diary is a small
 * polaroid of that diary's cover. Nearby pins gather into a stack with a count.
 */
@Composable
fun DiaryMap(cards: List<DiaryCard>, onOpen: (Long, Rect?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = context.container.settings
    val repo = context.container.diaries
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val handle = remember { MapHandle() }
    var ready by remember { mutableStateOf(false) }
    // More than one when the tapped spot holds several diaries, such as two trips to the same city.
    var selectedIds by remember { mutableStateOf(emptyList<Long>()) }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    DisposableEffect(lifecycle) {
        // A new observer is walked up to the current state, so the map gets its start and resume calls here too.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        val styleJson = withContext(Dispatchers.IO) { context.assets.open("map_style.json").bufferedReader().use { it.readText() } }
        mapView.getMapAsync { map ->
            map.uiSettings.apply {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = false
                isCompassEnabled = false
            }
            map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                handle.map = map
                handle.style = style
                ready = true
            }
            map.addOnMapClickListener { point ->
                val screen = map.projection.toScreenLocation(point)
                val hit = map.queryRenderedFeatures(screen, PIN_LAYER, CLUSTER_LAYER).firstOrNull()
                when {
                    hit == null -> selectedIds = emptyList()
                    hit.hasProperty("point_count") -> {
                        val leaves = handle.style?.getSourceAs<GeoJsonSource>(SOURCE)?.getClusterLeaves(hit, 20, 0)?.features().orEmpty()
                        val spots = leaves.mapNotNull { (it.geometry() as? Point)?.let { p -> LatLng(p.latitude(), p.longitude()) } }
                        val together = spots.isNotEmpty() &&
                            spots.maxOf { it.latitude } - spots.minOf { it.latitude } < 0.05 &&
                            spots.maxOf { it.longitude } - spots.minOf { it.longitude } < 0.05
                        if (together) {
                            // Zooming can't pull these apart, so offer all of them.
                            selectedIds = leaves.map { it.getNumberProperty("diaryId").toLong() }.distinct()
                        } else if (spots.size > 1) {
                            map.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(spots).build(), 200))
                        } else {
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, map.cameraPosition.zoom + 2.0))
                        }
                    }
                    else -> selectedIds = listOf(hit.getNumberProperty("diaryId").toLong())
                }
                true
            }
            // M-5: come back to wherever the map was left.
            map.addOnCameraIdleListener {
                if (handle.positioned) {
                    val camera = map.cameraPosition
                    camera.target?.let { target -> settings.update { it.copy(mapViewport = MapViewport(target.latitude, target.longitude, camera.zoom)) } }
                }
            }
        }
    }

    LaunchedEffect(ready, cards) {
        val map = handle.map ?: return@LaunchedEffect
        val style = handle.style ?: return@LaunchedEffect
        val pins = withContext(Dispatchers.Default) { cards.associate { it.id to pinBitmap(context, it) } }
        for ((id, bitmap) in pins) style.addImage("pin-$id", bitmap)
        if (style.getImage(CLUSTER_IMAGE) == null) style.addImage(CLUSTER_IMAGE, clusterBitmap(context))

        val features = cards.flatMap { card ->
            card.places.mapIndexed { index, place ->
                Feature.fromGeometry(Point.fromLngLat(place.lng, place.lat)).apply {
                    addNumberProperty("diaryId", card.id)
                    addStringProperty("icon", "pin-${card.id}")
                    // A fixed tilt per pin, as if each photo was stuck on by hand.
                    addNumberProperty("tilt", ((card.id * 7 + index * 5) % 13 - 6).toFloat())
                }
            }
        }
        val collection = FeatureCollection.fromFeatures(features)
        val source = style.getSourceAs<GeoJsonSource>(SOURCE)
        if (source == null) {
            style.addSource(GeoJsonSource(SOURCE, collection, GeoJsonOptions().withCluster(true).withClusterRadius(46).withClusterMaxZoom(12)))
            style.addLayer(
                SymbolLayer(PIN_LAYER, SOURCE)
                    .withFilter(Expression.not(Expression.has("point_count")))
                    .withProperties(
                        PropertyFactory.iconImage(Expression.get("icon")),
                        PropertyFactory.iconRotate(Expression.get("tilt")),
                        PropertyFactory.iconAnchor("bottom"),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                    ),
            )
            style.addLayer(
                SymbolLayer(CLUSTER_LAYER, SOURCE)
                    .withFilter(Expression.has("point_count"))
                    .withProperties(
                        PropertyFactory.iconImage(CLUSTER_IMAGE),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.textField(Expression.toString(Expression.get("point_count"))),
                        PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
                        PropertyFactory.textSize(15f),
                        PropertyFactory.textColor("#4A3B2E"),
                        PropertyFactory.textAllowOverlap(true),
                        PropertyFactory.textIgnorePlacement(true),
                    ),
            )
        } else {
            source.setGeoJson(collection)
        }

        if (!handle.positioned) {
            val saved = settings.state.value.mapViewport
            val points = cards.flatMap { it.places }.map { LatLng(it.lat, it.lng) }
            when {
                saved != null -> map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(saved.lat, saved.lng), saved.zoom))
                points.isEmpty() -> Unit
                // Pins in one small area would zoom the map right down to street level, so show the region instead.
                points.maxOf { it.latitude } - points.minOf { it.latitude } < 1.0 && points.maxOf { it.longitude } - points.minOf { it.longitude } < 1.0 ->
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 4.5))
                // First visit: fit every pin, clear of the top bar and the button at the bottom.
                else -> map.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), 220, 520, 220, 520))
            }
            handle.positioned = true
        }
        selectedIds = selectedIds.filter { id -> cards.any { it.id == id } }
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Text(
            stringResource(R.string.map_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 12.dp, bottom = 6.dp),
        )
        if (cards.isEmpty()) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.align(Alignment.Center).padding(32.dp)) {
                Text(stringResource(R.string.map_empty), modifier = Modifier.padding(20.dp))
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (card in cards.filter { it.id in selectedIds }.take(3)) {
                key(card.id) {
                    PeekCard(
                        card = card,
                        firstPage = produceState<PageWithElements?>(null, card.id) { value = repo.firstPage(card.id) }.value,
                        onOpen = { bounds -> onOpen(card.id, bounds) },
                    )
                }
            }
        }
    }
}

/** M-4: what a pin opens into before the book itself. Tapping it opens the diary from the thumbnail's position. */
@Composable
private fun PeekCard(card: DiaryCard, firstPage: PageWithElements?, onOpen: (Rect?) -> Unit, modifier: Modifier = Modifier) {
    var thumbnail by remember { mutableStateOf<Rect?>(null) }
    val label = stringResource(R.string.map_open)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth().clickable(onClickLabel = label) { onOpen(thumbnail) },
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.height(104.dp).onGloballyPositioned { thumbnail = it.boundsInRoot() }) {
                if (firstPage != null) {
                    PageSurface(firstPage.page.format, firstPage.page.background, firstPage.elements, Modifier.height(104.dp).paperShadow())
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(card.title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(card.placeLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                card.dateLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(pluralStringResource(R.plurals.page_count, card.pageCount, card.pageCount), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** M-3: a mini polaroid of the diary's cover, with the title on the white strip. */
private fun pinBitmap(context: Context, card: DiaryCard): Bitmap {
    val d = context.resources.displayMetrics.density
    val width = (62 * d).toInt()
    val height = (78 * d).toInt()
    val bitmap = Bitmap.createBitmap(width + (6 * d).toInt(), height + (6 * d).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.color = 0x33000000
    canvas.drawRoundRect(RectF(3 * d, 4 * d, width + 3 * d, height + 4 * d), 3 * d, 3 * d, paint)
    paint.color = 0xFFFDFBF6.toInt()
    canvas.drawRoundRect(RectF(1 * d, 1 * d, width + 1 * d, height + 1 * d), 3 * d, 3 * d, paint)

    val picture = RectF(6 * d, 6 * d, width - 4 * d, 6 * d + (width - 10 * d))
    val photo = card.coverImageUri?.takeIf { card.coverStyle == CoverStyle.PHOTO }?.let { path ->
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val sample = (minOf(bounds.outWidth, bounds.outHeight) / (picture.width().toInt() * 2)).coerceAtLeast(1)
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    if (photo != null) {
        val side = minOf(photo.width, photo.height)
        val crop = AndroidRect((photo.width - side) / 2, (photo.height - side) / 2, (photo.width + side) / 2, (photo.height + side) / 2)
        canvas.drawBitmap(photo, crop, picture, paint)
    } else {
        val (base, shade, _) = card.coverStyle.pinColours()
        paint.shader = LinearGradient(picture.left, picture.top, picture.right, picture.bottom, base.toArgb(), shade.toArgb(), Shader.TileMode.CLAMP)
        canvas.drawRect(picture, paint)
        paint.shader = null
        // The spine, so the square still reads as a book cover.
        paint.color = 0x40000000
        canvas.drawRect(picture.left, picture.top, picture.left + 5 * d, picture.bottom, paint)
    }

    val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3A3028.toInt()
        textSize = 9.5f * d
        typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        textAlign = Paint.Align.CENTER
    }
    val title = TextUtils.ellipsize(card.title, text, width - 10 * d, TextUtils.TruncateAt.END).toString()
    canvas.drawText(title, (width + 2 * d) / 2, picture.bottom + 12.5f * d, text)
    return bitmap
}

/** A little stack of polaroids. The count is drawn on top by the map as text. */
private fun clusterBitmap(context: Context): Bitmap {
    val d = context.resources.displayMetrics.density
    val size = (64 * d).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val card = RectF(-20 * d, -22 * d, 20 * d, 22 * d)
    for ((index, tilt) in floatArrayOf(-14f, 9f, -2f).withIndex()) {
        canvas.save()
        canvas.translate(size / 2f, size / 2f)
        canvas.rotate(tilt)
        paint.color = 0x30000000
        canvas.drawRoundRect(RectF(card.left + 1.5f * d, card.top + 2.5f * d, card.right + 1.5f * d, card.bottom + 2.5f * d), 3 * d, 3 * d, paint)
        paint.color = if (index == 2) 0xFFFDFBF6.toInt() else 0xFFF1E9D8.toInt()
        canvas.drawRoundRect(card, 3 * d, 3 * d, paint)
        canvas.restore()
    }
    return bitmap
}
