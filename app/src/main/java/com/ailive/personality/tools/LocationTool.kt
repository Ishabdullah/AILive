package com.ailive.personality.tools

import android.util.Log
import com.ailive.location.LocationManager

/**
 * Location Tool - Provides GPS and location services
 *
 * Capabilities:
 * - Get current GPS coordinates (latitude, longitude)
 * - Get current location name (city, state, country)
 * - Get formatted address
 * - Distance calculations
 * - Geocoding (address → coordinates)
 * - Reverse geocoding (coordinates → address)
 *
 * Use when user asks:
 * - "Where am I?"
 * - "What town/city/state am I in?"
 * - "What's my location?"
 * - "Where are we?"
 * - Any question requiring current location
 *
 * @param locationManager The LocationManager instance
 * @since v1.4 - Tool Integration Fix
 */
class LocationTool(
    private val locationManager: LocationManager
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

    /**
     * Execute location query
     *
     * Parameters: None required
     * Returns: Location information as ToolResult
     */
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        return try {
            Log.i(TAG, "🌍 Getting current location...")

            // Get last known location
            val location = locationManager.getLastKnownLocation()

            if (location == null) {
                Log.w(TAG, "No location available yet")
                return ToolResult.error(
                    "Location not available. GPS may still be acquiring position. Please wait a moment and try again."
                )
            }

            // Get location details
            val latitude = location.latitude
            val longitude = location.longitude

            Log.i(TAG, "📍 Got coordinates: $latitude, $longitude")

            // Get address from coordinates (reverse geocoding)
            val address = locationManager.getAddressFromCoordinates(latitude, longitude)

            if (address != null) {
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
                    append("Coordinates: $latitude, $longitude")
                }

                Log.i(TAG, "✅ Location: $city, $state, $country")

                return ToolResult.success(
                    data = mapOf(
                        "city" to city,
                        "state" to state,
                        "country" to country,
                        "postal_code" to postalCode,
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "formatted" to locationText
                    ),
                    message = locationText
                )
            } else {
                // Couldn't get address, return coordinates only
                val locationText = "Location: $latitude, $longitude\n(Address lookup unavailable)"

                Log.w(TAG, "⚠️ Got coordinates but no address")

                return ToolResult.success(
                    data = mapOf(
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "formatted" to locationText
                    ),
                    message = locationText
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Location query failed", e)
            return ToolResult.error("Failed to get location: ${e.message}")
        }
    }
}
