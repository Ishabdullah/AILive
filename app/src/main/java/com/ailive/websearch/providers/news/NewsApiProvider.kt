package com.ailive.websearch.providers.news

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
import java.time.format.DateTimeFormatter

/**
 * Search provider for NewsAPI.org.
 *
 * Provides news articles from 80,000+ sources worldwide.
 *
 * API Documentation: https://newsapi.org/docs
 *
 * Configuration:
 * - Requires API key
 * - Free tier: 100 requests/day
 * - Developer tier: 500 requests/day
 *
 * @param apiKey NewsAPI.org API key
 * @param httpClient Optional custom HTTP client
 * @since v1.4
 */
class NewsApiProvider(
    private val apiKey: String,
    private val httpClient: OkHttpClient = HttpClientFactory.createClient()
) : BaseSearchProvider() {

    override val name: String = "NewsAPI"
    override val supportedIntents: Set<SearchIntent> = setOf(SearchIntent.NEWS, SearchIntent.GENERAL)

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val newsAdapter = moshi.adapter(NewsApiResponse::class.java)

    companion object {
        private const val BASE_URL = "https://newsapi.org/v2/everything"
        private const val TOP_HEADLINES_URL = "https://newsapi.org/v2/top-headlines"
    }

    override suspend fun search(query: SearchQuery): ProviderResult = withContext(Dispatchers.IO) {
        val (result, latency) = timedSearch {
            executeSearch(query)
        }
        result.copy(latencyMs = latency)
    }

    private suspend fun executeSearch(query: SearchQuery): ProviderResult {
        try {
            // Extract search keywords
            val keywords = extractKeywords(query.text)

            // Build request URL
            val url = BASE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("q", keywords)
                .addQueryParameter("language", query.language)
                .addQueryParameter("sortBy", "relevancy")
                .addQueryParameter("pageSize", query.maxResults.coerceAtMost(100).toString())
                .addQueryParameter("apiKey", apiKey)
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return createErrorResult(
                    error = "NewsAPI error: ${response.code} ${response.message}",
                    latency = 0,
                    metadata = mapOf("status_code" to response.code.toString())
                )
            }

            val newsData = response.body?.string()?.let { newsAdapter.fromJson(it) }
                ?: return createErrorResult(
                    error = "Failed to parse NewsAPI response",
                    latency = 0
                )

            if (newsData.status != "ok") {
                return createErrorResult(
                    error = "NewsAPI returned error: ${newsData.message ?: "Unknown"}",
                    latency = 0
                )
            }

            // Convert articles to search results
            val results = newsData.articles.map { convertToSearchResult(it) }

            return createSuccessResult(
                results = results,
                latency = 0,
                metadata = mapOf(
                    "total_results" to newsData.totalResults.toString(),
                    "keywords" to keywords
                )
            )
        } catch (e: Exception) {
            return createErrorResult(
                error = "NewsAPI request failed: ${e.message}",
                latency = 0
            )
        }
    }

    override suspend fun healthCheck(): ProviderStatus {
        return try {
            val url = TOP_HEADLINES_URL.toHttpUrl().newBuilder()
                .addQueryParameter("country", "us")
                .addQueryParameter("pageSize", "1")
                .addQueryParameter("apiKey", apiKey)
                .build()

            val request = Request.Builder().url(url).get().build()
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
            SearchIntent.NEWS -> 95  // Highest for news
            SearchIntent.GENERAL -> 60
            else -> 0
        }
    }

    private fun extractKeywords(text: String): String {
        return text
            .replace(Regex("^(latest |recent |breaking )?news (about |on |regarding )?", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun convertToSearchResult(article: NewsArticle): SearchResultItem {
        val publishedAt = try {
            Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(article.publishedAt))
        } catch (e: Exception) {
            null
        }

        return SearchResultItem(
            title = article.title,
            snippet = article.description ?: article.content?.take(300) ?: "",
            url = article.url,
            source = article.source.name,
            publishedAt = publishedAt,
            confidence = 0.85f,
            imageUrl = article.urlToImage,
            metadata = mapOf(
                "author" to (article.author ?: "Unknown"),
                "source_id" to (article.source.id ?: "unknown")
            )
        )
    }
}

// Data classes for NewsAPI responses

@JsonClass(generateAdapter = true)
data class NewsApiResponse(
    val status: String,
    val totalResults: Int,
    val articles: List<NewsArticle>,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class NewsArticle(
    val source: NewsSource,
    val author: String?,
    val title: String,
    val description: String?,
    val url: String,
    val urlToImage: String?,
    val publishedAt: String,
    val content: String?
)

@JsonClass(generateAdapter = true)
data class NewsSource(
    val id: String?,
    val name: String
)
