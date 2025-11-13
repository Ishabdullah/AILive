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
import java.time.Instant

/**
 * Search provider for DuckDuckGo Instant Answer API.
 *
 * Provides quick facts, definitions, and disambiguation results.
 * Free service, no API key required.
 *
 * API Documentation: https://duckduckgo.com/api
 *
 * Features:
 * - Instant answers for factual queries
 * - Disambiguation for ambiguous terms
 * - Related topics and external links
 * - Zero-click information
 *
 * @param httpClient Optional custom HTTP client
 * @since v1.4
 */
class DuckDuckGoInstantProvider(
    private val httpClient: OkHttpClient = HttpClientFactory.createClient()
) : BaseSearchProvider() {

    override val name: String = "DuckDuckGo"
    override val supportedIntents: Set<SearchIntent> = setOf(
        SearchIntent.GENERAL,
        SearchIntent.PERSON_WHOIS
    )

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val ddgAdapter = moshi.adapter(DuckDuckGoResponse::class.java)

    companion object {
        private const val API_URL = "https://api.duckduckgo.com/"
    }

    override suspend fun search(query: SearchQuery): ProviderResult = withContext(Dispatchers.IO) {
        val (result, latency) = timedSearch {
            executeSearch(query)
        }
        result.copy(latencyMs = latency)
    }

    private suspend fun executeSearch(query: SearchQuery): ProviderResult {
        try {
            val url = API_URL.toHttpUrl().newBuilder()
                .addQueryParameter("q", query.text)
                .addQueryParameter("format", "json")
                .addQueryParameter("no_html", "1")
                .addQueryParameter("skip_disambig", "0")
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return createErrorResult(
                    error = "DuckDuckGo API error: ${response.code} ${response.message}",
                    latency = 0
                )
            }

            val ddgData = response.body?.string()?.let { ddgAdapter.fromJson(it) }
                ?: return createErrorResult(
                    error = "Failed to parse DuckDuckGo response",
                    latency = 0
                )

            // Convert to search results
            val results = mutableListOf<SearchResultItem>()

            // Add instant answer if available
            if (!ddgData.Abstract.isNullOrBlank()) {
                results.add(
                    SearchResultItem(
                        title = ddgData.Heading ?: query.text,
                        snippet = ddgData.Abstract,
                        url = ddgData.AbstractURL ?: "",
                        source = ddgData.AbstractSource ?: name,
                        confidence = 0.85f,
                        imageUrl = ddgData.Image
                    )
                )
            }

            // Add related topics
            ddgData.RelatedTopics?.take(5)?.forEach { topic ->
                if (topic.Text != null && topic.FirstURL != null) {
                    results.add(
                        SearchResultItem(
                            title = topic.Text.take(100),
                            snippet = topic.Text,
                            url = topic.FirstURL,
                            source = name,
                            confidence = 0.70f
                        )
                    )
                }
            }

            if (results.isEmpty()) {
                return createErrorResult(
                    error = "No instant answers found for: ${query.text}",
                    latency = 0
                )
            }

            return createSuccessResult(
                results = results,
                latency = 0,
                metadata = mapOf(
                    "answer_type" to (ddgData.Type ?: "unknown"),
                    "has_infobox" to (ddgData.Infobox != null).toString()
                )
            )
        } catch (e: Exception) {
            return createErrorResult(
                error = "DuckDuckGo request failed: ${e.message}",
                latency = 0
            )
        }
    }

    override suspend fun healthCheck(): ProviderStatus {
        return try {
            val request = Request.Builder()
                .url("$API_URL?q=test&format=json")
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
        return when (intent) {
            SearchIntent.GENERAL -> 75
            SearchIntent.PERSON_WHOIS -> 80
            else -> 0
        }
    }
}

// Data classes for DuckDuckGo API responses

@JsonClass(generateAdapter = true)
data class DuckDuckGoResponse(
    val Abstract: String?,
    val AbstractText: String?,
    val AbstractSource: String?,
    val AbstractURL: String?,
    val Image: String?,
    val Heading: String?,
    val Answer: String?,
    val AnswerType: String?,
    val Definition: String?,
    val DefinitionSource: String?,
    val DefinitionURL: String?,
    val RelatedTopics: List<RelatedTopic>?,
    val Type: String?,
    val Infobox: Map<String, Any>?
)

@JsonClass(generateAdapter = true)
data class RelatedTopic(
    val Text: String?,
    val FirstURL: String?,
    val Icon: Icon?
)

@JsonClass(generateAdapter = true)
data class Icon(
    val URL: String?,
    val Height: Int?,
    val Width: Int?
)
