package com.ailive.websearch.providers.wiki

import com.ailive.websearch.core.*
import com.ailive.websearch.network.HttpClientFactory
import com.squareup.moshi.Json
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
 * Search provider for Wikipedia using the MediaWiki Action API.
 *
 * Provides encyclopedic information about people, places, concepts, and events.
 *
 * API Documentation: https://www.mediawiki.org/wiki/API:Main_page
 *
 * Features:
 * - Extract first paragraph summaries
 * - Full page metadata and attribution
 * - Multilingual support (defaults to English)
 * - No API key required
 *
 * @param language Language code (default: "en")
 * @param httpClient Optional custom HTTP client
 * @since v1.4
 */
class WikipediaProvider(
    private val language: String = "en",
    private val httpClient: OkHttpClient = HttpClientFactory.createClient()
) : BaseSearchProvider() {

    override val name: String = "Wikipedia"
    override val supportedIntents: Set<SearchIntent> = setOf(
        SearchIntent.PERSON_WHOIS,
        SearchIntent.GENERAL
    )

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val queryAdapter = moshi.adapter(WikipediaQueryResponse::class.java)

    companion object {
        private const val USER_AGENT = "AILive/1.4 (Android; Web Search; contact@ailive.app)"
    }

    private fun getApiUrl(): String = "https://$language.wikipedia.org/w/api.php"

    override suspend fun search(query: SearchQuery): ProviderResult = withContext(Dispatchers.IO) {
        val (result, latency) = timedSearch {
            executeSearch(query)
        }
        result.copy(latencyMs = latency)
    }

    private suspend fun executeSearch(query: SearchQuery): ProviderResult {
        try {
            // Extract search term (remove "who is", "what is", etc.)
            val searchTerm = cleanSearchTerm(query.text)

            // Build search URL
            val url = getApiUrl().toHttpUrl().newBuilder()
                .addQueryParameter("action", "query")
                .addQueryParameter("format", "json")
                .addQueryParameter("prop", "extracts|pageimages|info")
                .addQueryParameter("exintro", "true")
                .addQueryParameter("explaintext", "true")
                .addQueryParameter("inprop", "url")
                .addQueryParameter("piprop", "thumbnail")
                .addQueryParameter("pithumbsize", "300")
                .addQueryParameter("titles", searchTerm)
                .addQueryParameter("redirects", "1")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return createErrorResult(
                    error = "Wikipedia API error: ${response.code} ${response.message}",
                    latency = 0
                )
            }

            val wikipediaData = response.body?.string()?.let { queryAdapter.fromJson(it) }
                ?: return createErrorResult(
                    error = "Failed to parse Wikipedia response",
                    latency = 0
                )

            // Convert pages to search results
            val results = wikipediaData.query.pages.values
                .filter { !it.missing }
                .map { convertToSearchResult(it) }

            if (results.isEmpty()) {
                return createErrorResult(
                    error = "No Wikipedia pages found for: $searchTerm",
                    latency = 0
                )
            }

            return createSuccessResult(
                results = results,
                latency = 0,
                metadata = mapOf(
                    "language" to language,
                    "search_term" to searchTerm
                )
            )
        } catch (e: Exception) {
            return createErrorResult(
                error = "Wikipedia request failed: ${e.message}",
                latency = 0
            )
        }
    }

    override suspend fun healthCheck(): ProviderStatus {
        return try {
            val url = getApiUrl().toHttpUrl().newBuilder()
                .addQueryParameter("action", "query")
                .addQueryParameter("format", "json")
                .addQueryParameter("meta", "siteinfo")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
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
            SearchIntent.PERSON_WHOIS -> 95  // Highest priority for person queries
            SearchIntent.GENERAL -> 70
            else -> 0
        }
    }

    private fun cleanSearchTerm(text: String): String {
        return text
            .replace(Regex("^(who is|what is|tell me about|about)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun convertToSearchResult(page: WikipediaPage): SearchResultItem {
        val extract = page.extract?.take(500) ?: "No description available"
        val snippet = if (extract.length > 300) {
            extract.take(297) + "..."
        } else {
            extract
        }

        return SearchResultItem(
            title = page.title,
            snippet = snippet,
            url = page.fullurl ?: "https://$language.wikipedia.org/wiki/${page.title.replace(" ", "_")}",
            source = name,
            confidence = 0.90f,
            imageUrl = page.thumbnail?.source,
            metadata = mapOf(
                "page_id" to page.pageid.toString(),
                "language" to language
            )
        )
    }
}

// Data classes for Wikipedia API responses

@JsonClass(generateAdapter = true)
data class WikipediaQueryResponse(
    val query: WikipediaQuery
)

@JsonClass(generateAdapter = true)
data class WikipediaQuery(
    val pages: Map<String, WikipediaPage>
)

@JsonClass(generateAdapter = true)
data class WikipediaPage(
    val pageid: Long = 0,
    val title: String,
    val extract: String? = null,
    val fullurl: String? = null,
    val thumbnail: WikipediaThumbnail? = null,
    val missing: Boolean = false
)

@JsonClass(generateAdapter = true)
data class WikipediaThumbnail(
    val source: String,
    val width: Int,
    val height: Int
)
