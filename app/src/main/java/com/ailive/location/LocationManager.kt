package com.ailive.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resume

/**
 * LocationManager - Handles GPS location and reverse geocoding
 *
 * Features:
 * - Request current location
 * - Reverse geocoding (lat/lon -> city, state, country)
 * - Permission checking
 * - Context-aware location strings
 */
class LocationManager(private val context: Context) {
    private val TAG = "LocationManager"

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val geocoder: Geocoder = Geocoder(context, Locale.US)

    private var cachedLocation: Location? = null
    private var cachedLocationString: String? = null
    private var lastLocationTime: Long = 0
    private val CACHE_DURATION_MS = 5 * 60 * 1000 // 5 minutes

    /**
     * Check if location permissions are granted
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get current location with optional caching
     * @param forceRefresh Force a new location fetch, ignoring cache
     * @return Location or null if unavailable
     */
    suspend fun getCurrentLocation(forceRefresh: Boolean = false): Location? {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted")
            return null
        }

        // Check cache first (unless forceRefresh is true)
        if (!forceRefresh && cachedLocation != null) {
            val timeSinceLastLocation = System.currentTimeMillis() - lastLocationTime
            if (timeSinceLastLocation < CACHE_DURATION_MS) {
                Log.d(TAG, "Using cached location (age: ${timeSinceLastLocation / 1000}s)")
                return cachedLocation
            }
        }

        return try {
            Log.i(TAG, "Fetching current location...")
            val location = suspendCancellableCoroutine { continuation ->
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    object : CancellationToken() {
                        override fun onCanceledRequested(listener: OnTokenCanceledListener) =
                            CancellationTokenSource().token
                        override fun isCancellationRequested() = false
                    }
                ).addOnSuccessListener { location: Location? ->
                    continuation.resume(location)
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get location", e)
                    continuation.resume(null)
                }
            }

            if (location != null) {
                cachedLocation = location
                lastLocationTime = System.currentTimeMillis()
                Log.i(TAG, "✓ Location obtained: ${location.latitude}, ${location.longitude}")
            } else {
                Log.w(TAG, "Location is null")
            }

            location
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting location", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting location", e)
            null
        }
    }

    /**
     * Reverse geocode location to human-readable address
     * @param location Location to geocode
     * @return Address string or null
     */
    @Suppress("DEPRECATION")
    private fun reverseGeocode(location: Location): String? {
        return try {
            val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Use new API for Android 13+
                var result: List<Address>? = null
                geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1
                ) { addresses ->
                    result = addresses
                }
                result
            } else {
                // Use deprecated API for older Android versions
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(location.latitude, location.longitude, 1)
            }

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val parts = mutableListOf<String>()

                // Build location string: "City, State, Country"
                address.locality?.let { parts.add(it) }  // City
                address.adminArea?.let { parts.add(it) } // State
                address.countryName?.let { parts.add(it) } // Country

                val locationStr = parts.joinToString(", ")
                Log.i(TAG, "✓ Geocoded location: $locationStr")
                locationStr
            } else {
                Log.w(TAG, "No address found for location")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding failed", e)
            null
        }
    }

    /**
     * Get location context string for AI prompts
     * Format: "You're currently in New York, NY, United States"
     * @param forceRefresh Force a new location fetch
     * @return Location context string or null if unavailable
     */
    suspend fun getLocationContext(forceRefresh: Boolean = false): String? {
        // Check cache first (unless forceRefresh is true)
        if (!forceRefresh && cachedLocationString != null) {
            val timeSinceLastLocation = System.currentTimeMillis() - lastLocationTime
            if (timeSinceLastLocation < CACHE_DURATION_MS) {
                Log.d(TAG, "Using cached location string")
                return cachedLocationString
            }
        }

        val location = getCurrentLocation(forceRefresh) ?: return null

        val addressStr = reverseGeocode(location) ?: run {
            // Fallback to coordinates if geocoding fails
            "lat: ${String.format("%.4f", location.latitude)}, lon: ${String.format("%.4f", location.longitude)}"
        }

        val locationContext = "You're currently in $addressStr"
        cachedLocationString = locationContext
        return locationContext
    }

    /**
     * Clear location cache (forces fresh location on next request)
     */
    fun clearCache() {
        cachedLocation = null
        cachedLocationString = null
        lastLocationTime = 0
        Log.d(TAG, "Location cache cleared")
    }
}
