package com.ailive.personality.tools

import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.ailive.location.LocationManager

/**
 * Location Tool - Provides GPS and location services
 *
 * Capabilities:
 * - Get current GPS coordinates (latitude, longitude)
 * - Get current location name (city, state, country)
 * - Get formatted address
 *
 * Use when user asks:
 * - "Where am I?"
 * - "What town/city/state am I in?"
 * - "What's my location?"
 * - "Where are we?"
 * - Any question requiring current location
 *
 * @param locationManager The LocationManager instance
 * @param context Android context for geocoding
 * @since v1.4 - Tool Integration Fix
 */
class LocationTool(
    private val locationManager: LocationManager,
    private val context: Context
) : BaseTool() {

    override val name: String = "get_location"

    override val description: String = """
        Get current GPS location and address information.

        Use this tool when user asks:
        - "Where am I?" / "What's my location?"
        - "What town/city/state am I in?"
        - "What country am I in?"
        - Any question requiring current location

        Returns:
        - City, state, country
        - Latitude, longitude
        - Full formatted address

        Example:
        User: "What town am I in?"
        Action: Call get_location tool
        Response: "You're in Weathersfield, Connecticut"
    """.trimIndent()

    override val requiresPermissions: Boolean = true  // Requires LOCATION permission

    private val TAG = "LocationTool"
    private val geocoder = Geocoder(context)

    /**
     * Check if tool is available (has permissions)
     */
    override suspend fun isAvailable(): Boolean {
        return locationManager.hasLocationPermission()
    }

    /**
     * Execute location query
     *
     * Parameters: None required
     * Returns: Location information as ToolResult
     */
    override suspend fun executeInternal(parameters: Map<String, Any>): ToolResult {
        Log.i(TAG, "🌍 Getting current location...")

        // Check permissions first
        if (!locationManager.hasLocationPermission()) {
            return ToolResult.Blocked(
                reason = "Location permission not granted",
                requiredAction = "Please grant location permissions in Settings"
            )
        }

        // Get current location
        val location = locationManager.getCurrentLocation(forceRefresh = false)

        if (location == null) {
            Log.w(TAG, "No location available yet")
            return ToolResult.Failure(
                error = Exception("Location not available"),
                reason = "Location not available. GPS may still be acquiring position. Please wait a moment and try again.",
                recoverable = true
            )
        }

        // Get location details
        val latitude = location.latitude
        val longitude = location.longitude

        Log.i(TAG, "📍 Got coordinates: $latitude, $longitude")

        // Try to get address information via geocoding
        return try {
            @Suppress("DEPRECATION")
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Use new API for Android 13+
                var result: List<android.location.Address>? = null
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    result = addresses
                }
                result
            } else {
                // Use deprecated API for older Android versions
                geocoder.getFromLocation(latitude, longitude, 1)
            }

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val city = address.locality ?: address.subAdminArea ?: "Unknown"
                val state = address.adminArea ?: "Unknown"
                val country = address.countryName ?: "Unknown"
                val postalCode = address.postalCode ?: ""

                val locationText = buildString {
                    append("Current Location:\n")
                    append("City: $city\n")
                    append("State: $state\n")
                    append("Country: $country\n")
                    if (postalCode.isNotBlank()) {
                        append("Postal Code: $postalCode\n")
                    }
                    append("Coordinates: ${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}")
                }

                Log.i(TAG, "✅ Location: $city, $state, $country")

                ToolResult.Success(
                    data = mapOf(
                        "city" to city,
                        "state" to state,
                        "country" to country,
                        "postal_code" to postalCode,
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "formatted" to locationText
                    ),
                    context = mapOf(
                        "location_available" to true,
                        "address_resolved" to true
                    )
                )
            } else {
                // Couldn't get address, return coordinates only
                val locationText = "Location: ${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}\n(Address lookup unavailable)"

                Log.w(TAG, "⚠️ Got coordinates but no address")

                ToolResult.Success(
                    data = mapOf(
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "formatted" to locationText
                    ),
                    context = mapOf(
                        "location_available" to true,
                        "address_resolved" to false
                    )
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Geocoding failed", e)

            // Fall back to coordinates only
            val locationText = "Location: ${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}"

            ToolResult.Success(
                data = mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "formatted" to locationText
                ),
                context = mapOf(
                    "location_available" to true,
                    "address_resolved" to false,
                    "geocoding_error" to (e.message ?: "Unknown error")
                )
            )
        }
    }
}
