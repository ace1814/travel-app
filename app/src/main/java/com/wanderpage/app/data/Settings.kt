package com.wanderpage.app.data

import android.content.Context
import androidx.core.content.edit
import com.wanderpage.app.data.model.PageFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MotionPreference { SYSTEM, REDUCED, FULL }

data class MapViewport(val lat: Double, val lng: Double, val zoom: Double)

data class SettingsState(
    val defaultFormat: PageFormat = PageFormat.POST_PORTRAIT,
    val exportPng: Boolean = true,
    val jpegQuality: Int = 92,
    val watermark: Boolean = false,
    val motion: MotionPreference = MotionPreference.SYSTEM,
    /** Where the map was last left (M-5). Null until the map has been opened once. */
    val mapViewport: MapViewport? = null,
)

/** User settings (PRD §6.8), kept in SharedPreferences. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun update(transform: (SettingsState) -> SettingsState) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit {
            putString("defaultFormat", next.defaultFormat.name)
            putBoolean("exportPng", next.exportPng)
            putInt("jpegQuality", next.jpegQuality)
            putBoolean("watermark", next.watermark)
            putString("motion", next.motion.name)
            val viewport = next.mapViewport
            if (viewport == null) {
                remove("mapLat")
            } else {
                putString("mapLat", viewport.lat.toString())
                putString("mapLng", viewport.lng.toString())
                putString("mapZoom", viewport.zoom.toString())
            }
        }
    }

    private fun read(): SettingsState {
        val defaults = SettingsState()
        val lat = prefs.getString("mapLat", null)?.toDoubleOrNull()
        val lng = prefs.getString("mapLng", null)?.toDoubleOrNull()
        val zoom = prefs.getString("mapZoom", null)?.toDoubleOrNull()
        return SettingsState(
            defaultFormat = prefs.getString("defaultFormat", null)?.let { name -> PageFormat.entries.firstOrNull { it.name == name } }
                ?: defaults.defaultFormat,
            exportPng = prefs.getBoolean("exportPng", defaults.exportPng),
            jpegQuality = prefs.getInt("jpegQuality", defaults.jpegQuality),
            watermark = prefs.getBoolean("watermark", defaults.watermark),
            motion = prefs.getString("motion", null)?.let { name -> MotionPreference.entries.firstOrNull { it.name == name } }
                ?: defaults.motion,
            mapViewport = if (lat != null && lng != null && zoom != null) MapViewport(lat, lng, zoom) else null,
        )
    }
}
