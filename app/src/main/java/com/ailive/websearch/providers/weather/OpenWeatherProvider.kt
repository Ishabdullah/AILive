package com.ailive.websearch.providers.weather

import com.ailive.websearch.core.*
import com.ailive.websearch.network.HttpClientFactory
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant

/**
 * Search provider for OpenWeatherMap API.
 *
 * Provides current weather and forecasts using the OneCall API 3.0.
 *
 * API Documentation: https://openweathermap.org/api/one-call-3
 *
 * Configuration:
 * - Requires API key (set via OPENWEATHER_API_KEY)
 * - Free tier: 1,000 calls/day, 60 calls/minute
 * - Supports coordinates or city name
 *
 * @param apiKey OpenWeatherMap API key
 * @param httpClient Optional custom HTTP client
 * @since v1.4
 */
class OpenWeatherProvider(
    private val apiKey: String,
    private val httpClient: OkHttpClient = HttpClientFactory.createClient()
) : BaseSearchProvider() {

    override val name: String = "OpenWeather"
    override val supportedIntents: Set<SearchIntent> = setOf(SearchIntent.WEATHER)

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val weatherAdapter = moshi.adapter(OpenWeatherResponse::class.java)

    companion object {
        private const val BASE_URL = "https://api.openweathermap.org/data/3.0/onecall"
        private const val GEO_URL = "https://api.openweathermap.org/geo/1.0/direct"
    }

    override suspend fun search(query: SearchQuery): ProviderResult = withContext(Dispatchers.IO) {
        val (result, latency) = timedSearch {
            executeSearch(query)
        }
        result.copy(latencyMs = latency)
    }

    private suspend fun executeSearch(query: SearchQuery): ProviderResult {
        try {
            // Extract location from query
            val location = query.location ?: extractLocationFromText(query.text)
            if (location == null) {
                return createErrorResult(
                    error = "Unable to determine location from query",
                    latency = 0
                )
            }

            // Fetch weather data
            val url = buildWeatherUrl(location.latitude, location.longitude)
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return createErrorResult(
                    error = "OpenWeather API error: ${response.code} ${response.message}",
                    latency = 0,
                    metadata = mapOf(
                        "status_code" to response.code.toString(),
                        "quota_remaining" to (response.header("X-RateLimit-Remaining") ?: "unknown")
                    )
                )
            }

            val weatherData = response.body?.string()?.let { weatherAdapter.fromJson(it) }
                ?: return createErrorResult(
                    error = "Failed to parse OpenWeather response",
                    latency = 0
                )

            // Convert to search results
            val results = listOf(
                convertToSearchResult(weatherData, location)
            )

            return createSuccessResult(
                results = results,
                latency = 0,
                metadata = mapOf(
                    "location" to location.toDisplayString(),
                    "units" to "metric",
                    "api_version" to "3.0"
                )
            )
        } catch (e: Exception) {
            return createErrorResult(
                error = "OpenWeather request failed: ${e.message}",
                latency = 0
            )
        }
    }

    override suspend fun healthCheck(): ProviderStatus {
        return try {
            // Test with a known location (London)
            val testUrl = buildWeatherUrl(51.5074, -0.1278)
            val request = Request.Builder()
                .url(testUrl)
                .get()
                .build()

            val response = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }

            val quotaRemaining = response.header("X-RateLimit-Remaining")?.toIntOrNull()

            ProviderStatus(
                providerName = name,
                healthy = response.isSuccessful,
                errorMessage = if (!response.isSuccessful) {
                    "HTTP ${response.code}: ${response.message}"
                } else null,
                quotaRemaining = quotaRemaining
            )
        } catch (e: Exception) {
            ProviderStatus(
                providerName = name,
                healthy = false,
                errorMessage = e.message
            )
        }
    }

    override fun getPriority(intent: SearchIntent): Int {
        return if (intent == SearchIntent.WEATHER) 90 else 0  // High priority for weather
    }

    private fun buildWeatherUrl(lat: Double, lon: Double): String {
        return "$BASE_URL?lat=$lat&lon=$lon&appid=$apiKey&units=metric&exclude=minutely,alerts"
    }

    /**
     * Extracts location from query text (simplified geocoding).
     * In production, use OpenWeather's Geocoding API.
     */
    private suspend fun extractLocationFromText(text: String): com.ailive.websearch.core.LocationContext? {
        // Extract city name using simple patterns
        val cityPattern = Regex("(in|for|at) ([a-z ]+)", RegexOption.IGNORE_CASE)
        val match = cityPattern.find(text)
        val cityName = match?.groups?.get(2)?.value?.trim() ?: return null

        // Use geocoding API to get coordinates
        return geocodeCity(cityName)
    }

    /**
     * Geocodes a city name to coordinates using OpenWeather Geocoding API.
     */
    private suspend fun geocodeCity(cityName: String): com.ailive.websearch.core.LocationContext? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$GEO_URL?q=$cityName&limit=1&appid=$apiKey"
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val json = response.body?.string() ?: return@withContext null
                    val adapter = moshi.adapter<List<GeocodingResult>>(
                        com.squareup.moshi.Types.newParameterizedType(List::class.java, GeocodingResult::class.java)
                    )
                    val results = adapter.fromJson(json) ?: return@withContext null

                    results.firstOrNull()?.let {
                        com.ailive.websearch.core.LocationContext(
                            latitude = it.lat,
                            longitude = it.lon,
                            city = it.name,
                            country = it.country
                        )
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun convertToSearchResult(
        weather: OpenWeatherResponse,
        location: com.ailive.websearch.core.LocationContext
    ): SearchResultItem {
        val current = weather.current
        val condition = current.weather.firstOrNull()

        val title = "Weather in ${location.toDisplayString()}"
        val snippet = buildString {
            append("${current.temp}°C, ${condition?.description ?: "unknown conditions"}. ")
            append("Feels like ${current.feelsLike}°C. ")
            append("Humidity: ${current.humidity}%. ")
            append("Wind: ${current.windSpeed} m/s.")
        }

        return SearchResultItem(
            title = title,
            snippet = snippet,
            url = "https://openweathermap.org/",
            source = name,
            publishedAt = Instant.now(),
            confidence = 0.95f,
            metadata = mapOf(
                "temperature" to current.temp.toString(),
                "feels_like" to current.feelsLike.toString(),
                "humidity" to current.humidity.toString(),
                "wind_speed" to current.windSpeed.toString(),
                "condition" to (condition?.main ?: "unknown")
            )
        )
    }
}

// Data classes for OpenWeather API responses

@JsonClass(generateAdapter = true)
data class OpenWeatherResponse(
    val lat: Double,
    val lon: Double,
    val timezone: String,
    val current: CurrentWeather,
    val hourly: List<HourlyWeather>? = null,
    val daily: List<DailyWeather>? = null
)

@JsonClass(generateAdapter = true)
data class CurrentWeather(
    val dt: Long,
    val temp: Double,
    @Json(name = "feels_like") val feelsLike: Double,
    val pressure: Int,
    val humidity: Int,
    @Json(name = "wind_speed") val windSpeed: Double,
    val weather: List<WeatherCondition>
)

@JsonClass(generateAdapter = true)
data class HourlyWeather(
    val dt: Long,
    val temp: Double,
    val weather: List<WeatherCondition>
)

@JsonClass(generateAdapter = true)
data class DailyWeather(
    val dt: Long,
    val temp: DailyTemp,
    val weather: List<WeatherCondition>
)

@JsonClass(generateAdapter = true)
data class DailyTemp(
    val day: Double,
    val min: Double,
    val max: Double
)

@JsonClass(generateAdapter = true)
data class WeatherCondition(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
)

@JsonClass(generateAdapter = true)
data class GeocodingResult(
    val name: String,
    val lat: Double,
    val lon: Double,
    val country: String,
    val state: String? = null
)
