package com.ailive.websearch.core

import java.util.UUID

/**
 * Represents a search query with metadata for routing, caching, and telemetry.
 *
 * @property id Unique identifier for tracking this query
 * @property text The raw query text from the user
 * @property intent The detected intent (if known), or null for auto-detection
 * @property location Optional location context for location-aware queries
 * @property language Preferred language code (ISO 639-1), defaults to "en"
 * @property maxResults Maximum number of results to return (default: 10)
 * @property timeout Maximum time in milliseconds to wait for results (default: 30000)
 * @property bypassCache If true, skip cache and force fresh API calls
 * @property metadata Additional key-value metadata for provider-specific customization
 * @since v1.4
 */
data class SearchQuery(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val intent: SearchIntent? = null,
    val location: LocationContext? = null,
    val language: String = "en",
    val maxResults: Int = 10,
    val timeout: Long = 30_000L,
    val bypassCache: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(text.isNotBlank()) { "Query text cannot be blank" }
        require(maxResults > 0) { "maxResults must be positive, got $maxResults" }
        require(timeout > 0) { "timeout must be positive, got $timeout" }
    }

    /**
     * Creates a copy of this query with updated intent.
     */
    fun withIntent(newIntent: SearchIntent): SearchQuery {
        return copy(intent = newIntent)
    }
}

/**
 * Location context for location-aware search queries.
 *
 * @property latitude Latitude in decimal degrees
 * @property longitude Longitude in decimal degrees
 * @property city Optional city name
 * @property country Optional country code (ISO 3166-1 alpha-2)
 */
data class LocationContext(
    val latitude: Double,
    val longitude: Double,
    val city: String? = null,
    val country: String? = null
) {
    init {
        require(latitude in -90.0..90.0) { "Invalid latitude: $latitude" }
        require(longitude in -180.0..180.0) { "Invalid longitude: $longitude" }
    }

    /**
     * Returns a human-readable location string.
     */
    fun toDisplayString(): String {
        return when {
            city != null && country != null -> "$city, $country"
            city != null -> city
            else -> "${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}"
        }
    }
}
