package com.ailive.websearch.providers.general

import com.ailive.websearch.core.*
import com.ailive.websearch.network.HttpClientFactory
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Search provider for SerpApi (Google Search Results API).
 *
 * SerpApi provides structured SERP data from Google, Bing, and other search engines.
 *
 * API Documentation: https://serpapi.com/search-api
 *
 * Configuration:
 * - Requires API key
 * - Paid service with free tier (100 searches/month)
 * - Supports multiple search engines
 *
 * @param apiKey SerpApi API key
 * @param engine Search engine to use (default: "google")
 * @param httpClient Optional custom HTTP client
 * @since v1.4
 */
class SerpApiProvider(
    private val apiKey: String,
    private val engine: String = "google",
    private val httpClient: OkHttpClient = HttpClientFactory.createClient()
) : BaseSearchProvider() {

    override val name: String = "SerpApi"
    override val supportedIntents: Set<SearchIntent> = setOf(
        SearchIntent.GENERAL,
        SearchIntent.IMAGE,
        SearchIntent.VIDEO
    )

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val serpAdapter = moshi.adapter(SerpApiResponse::class.java)

    companion object {
        private const val BASE_URL = "https://serpapi.com/search"
    }

    override suspend fun search(query: SearchQuery): ProviderResult = withContext(Dispatchers.IO) {
        val (result, latency) = timedSearch {
            executeSearch(query)
        }
        result.copy(latencyMs = latency)
    }

    private suspend fun executeSearch(query: SearchQuery): ProviderResult {
        try {
            val url = BASE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("q", query.text)
                .addQueryParameter("engine", engine)
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("num", query.maxResults.coerceAtMost(20).toString())
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return createErrorResult(
                    error = "SerpApi error: ${response.code} ${response.message}",
                    latency = 0,
                    metadata = mapOf(
                        "status_code" to response.code.toString(),
                        "quota_remaining" to (response.header("X-RateLimit-Remaining") ?: "unknown")
                    )
                )
            }

            val serpData = response.body?.string()?.let { serpAdapter.fromJson(it) }
                ?: return createErrorResult(
                    error = "Failed to parse SerpApi response",
                    latency = 0
                )

            // Convert organic results to search results
            val results = serpData.organic_results?.map { convertToSearchResult(it) } ?: emptyList()

            if (results.isEmpty()) {
                return createErrorResult(
                    error = "No search results found",
                    latency = 0
                )
            }

            return createSuccessResult(
                results = results,
                latency = 0,
                metadata = mapOf(
                    "engine" to engine,
                    "search_id" to (serpData.search_metadata?.id ?: "unknown")
                )
            )
        } catch (e: Exception) {
            return createErrorResult(
                error = "SerpApi request failed: ${e.message}",
                latency = 0
            )
        }
    }

    override suspend fun healthCheck(): ProviderStatus {
        return try {
            val url = BASE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("q", "test")
                .addQueryParameter("engine", engine)
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("num", "1")
                .build()

            val request = Request.Builder().url(url).get().build()
            val response = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }

            val quotaRemaining = response.header("X-RateLimit-Remaining")?.toIntOrNull()

            ProviderStatus(
                providerName = name,
                healthy = response.isSuccessful,
                errorMessage = if (!response.isSuccessful) "HTTP ${response.code}" else null,
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
        return when (intent) {
            SearchIntent.GENERAL -> 85  // High priority for general search
            SearchIntent.IMAGE, SearchIntent.VIDEO -> 75
            else -> 0
        }
    }

    private fun convertToSearchResult(result: OrganicResult): SearchResultItem {
        return SearchResultItem(
            title = result.title,
            snippet = result.snippet ?: "",
            url = result.link,
            source = result.displayed_link ?: extractDomain(result.link),
            confidence = 0.80f,
            metadata = mapOf(
                "position" to (result.position?.toString() ?: "unknown")
            )
        )
    }

    private fun extractDomain(url: String): String {
        return try {
            url.toHttpUrl().host
        } catch (e: Exception) {
            "unknown"
        }
    }
}

// Data classes for SerpApi responses

@JsonClass(generateAdapter = true)
data class SerpApiResponse(
    val search_metadata: SearchMetadata?,
    val organic_results: List<OrganicResult>?,
    val error: String?
)

@JsonClass(generateAdapter = true)
data class SearchMetadata(
    val id: String,
    val status: String,
    val created_at: String
)

@JsonClass(generateAdapter = true)
data class OrganicResult(
    val position: Int?,
    val title: String,
    val link: String,
    val displayed_link: String?,
    val snippet: String?,
    val thumbnail: String?
)
