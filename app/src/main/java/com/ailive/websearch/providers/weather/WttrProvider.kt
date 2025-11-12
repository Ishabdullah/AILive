package com.ailive.websearch.providers.weather

import com.ailive.websearch.core.*
import com.ailive.websearch.network.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant

/**
 * Lightweight weather provider using wttr.in service.
 *
 * wttr.in is a free, curl-friendly weather service.
 * Uses as a fallback when OpenWeather is unavailable or quota exceeded.
 *
 * Service URL: https://wttr.in
 * No API key required.
 *
 * @param httpClient Optional custom HTTP client
 * @since v1.4
 */
class WttrProvider(
    private val httpClient: OkHttpClient = HttpClientFactory.createClient()
) : BaseSearchProvider() {

    override val name: String = "wttr.in"
    override val supportedIntents: Set<SearchIntent> = setOf(SearchIntent.WEATHER)

    companion object {
        private const val BASE_URL = "https://wttr.in"
    }

    override suspend fun search(query: SearchQuery): ProviderResult = withContext(Dispatchers.IO) {
        val (result, latency) = timedSearch {
            executeSearch(query)
        }
        result.copy(latencyMs = latency)
    }

    private suspend fun executeSearch(query: SearchQuery): ProviderResult {
        try {
            // Extract location from query text
            val location = extractLocation(query.text)

            // Fetch weather in JSON format
            val url = "$BASE_URL/$location?format=j1"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return createErrorResult(
                    error = "wttr.in error: ${response.code} ${response.message}",
                    latency = 0
                )
            }

            val responseBody = response.body?.string() ?: ""

            // Parse simple text response
            val result = parseWttrResponse(responseBody, location)

            return createSuccessResult(
                results = listOf(result),
                latency = 0,
                metadata = mapOf("location" to location)
            )
        } catch (e: Exception) {
            return createErrorResult(
                error = "wttr.in request failed: ${e.message}",
                latency = 0
            )
        }
    }

    override suspend fun healthCheck(): ProviderStatus {
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/London?format=%t")
                .get()
                .build()

            val response = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }

            ProviderStatus(
                providerName = name,
                healthy = response.isSuccessful,
                errorMessage = if (!response.isSuccessful) "HTTP ${response.code}" else null
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
        return if (intent == SearchIntent.WEATHER) 60 else 0  // Lower priority (fallback)
    }

    private fun extractLocation(text: String): String {
        val pattern = Regex("(in|for|at) ([a-z ]+)", RegexOption.IGNORE_CASE)
        val match = pattern.find(text)
        return match?.groups?.get(2)?.value?.trim() ?: "auto"  // "auto" uses IP geolocation
    }

    private fun parseWttrResponse(response: String, location: String): SearchResultItem {
        // Simple parsing - in production, parse the JSON properly
        val title = "Weather in $location"
        val snippet = "Current conditions from wttr.in (lightweight weather service)"

        return SearchResultItem(
            title = title,
            snippet = snippet,
            url = "$BASE_URL/$location",
            source = name,
            publishedAt = Instant.now(),
            confidence = 0.75f
        )
    }
}
