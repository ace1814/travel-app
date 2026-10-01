package com.wanderpage.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.location.LocationManagerCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class PlaceResult(
    val name: String,
    val detail: String,
    val lat: Double,
    val lng: Double,
    val countryCode: String?,
)

/** Location search on the platform Geocoder, so the MVP needs no API key. */
class PlaceSearch(private val context: Context) {

    suspend fun search(query: String): List<PlaceResult> = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        runCatching { Geocoder(context).getFromLocationName(query, 5) }
            .getOrNull()
            .orEmpty()
            .mapNotNull { it.toPlace() }
            .distinctBy { it.name to it.detail }
    }

    /** The device's coarse position as a place. The caller must hold the coarse location permission. */
    suspend fun current(): PlaceResult? {
        val location = lastOrFreshLocation() ?: return null
        return withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            runCatching { Geocoder(context).getFromLocation(location.latitude, location.longitude, 1) }
                .getOrNull()
                ?.firstOrNull()
                ?.toPlace()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastOrFreshLocation(): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        val last = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null) return last
        val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER } ?: return null
        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                runCatching {
                    LocationManagerCompat.getCurrentLocation(manager, provider, signal, context.mainExecutor) {
                        if (continuation.isActive) continuation.resume(it)
                    }
                }.onFailure { if (continuation.isActive) continuation.resume(null) }
            }
        }
    }

    private fun Address.toPlace(): PlaceResult? {
        if (!hasLatitude() || !hasLongitude()) return null
        val name = locality ?: subAdminArea ?: adminArea ?: featureName ?: countryName ?: return null
        val detail = listOfNotNull(adminArea, countryName).filter { it != name }.distinct().joinToString(", ")
        return PlaceResult(name, detail, latitude, longitude, countryCode)
    }
}
