package com.ailive.websearch.integration

import android.content.Context
import android.util.Log
import com.ailive.personality.tools.BaseTool
import com.ailive.personality.tools.ToolResult
import com.ailive.websearch.WebSearchManager
import com.ailive.websearch.WebSearchConfig
import com.ailive.websearch.core.*
import com.ailive.websearch.providers.general.DuckDuckGoInstantProvider
import com.ailive.websearch.providers.general.SerpApiProvider
import com.ailive.websearch.providers.news.NewsApiProvider
import com.ailive.websearch.providers.weather.OpenWeatherProvider
import com.ailive.websearch.providers.weather.WttrProvider
import com.ailive.websearch.providers.wiki.WikipediaProvider

/**
 * Web Search tool for AILive PersonalityEngine.
 *
 * Provides web search capabilities integrated with the unified personality system.
 *
 * Capabilities:
 * - Intent-based search routing (weather, news, people, general)
 * - Multi-provider aggregation for comprehensive results
 * - Source attribution and fact verification
 * - Smart summarization for concise responses
 *
 * Parameters:
 * - query (String, required): The search query
 * - intent (String, optional): Explicit intent (WEATHER, NEWS, PERSON_WHOIS, GENERAL, FACT_CHECK)
 * - max_results (Int, optional): Maximum results to return (default: 10)
 * - verify_facts (Boolean, optional): Enable fact verification (default: false)
 *
 * Example usage:
 * ```
 * execute(mapOf(
 *     "query" to "What's the weather in Boston?",
 *     "max_results" to 5
 * ))
 * ```
 *
 * @param context Android context
 * @param apiKeys Map of provider API keys (optional, uses free providers if not provided)
 * @since v1.4
 */
class WebSearchTool(
    private val context: Context,
    private val apiKeys: Map<String, String> = emptyMap()
) : BaseTool() {

    override val name: String = "web_search"
    override val description: String = """
        Search the web for current information, weather, news, facts, and general knowledge.

        Use when:
        - User asks about current events, news, or recent information
        - User requests weather forecasts or current conditions
        - User asks about people, places, or entities requiring up-to-date info
        - User wants to verify facts or check claims
        - User's question requires real-time or external knowledge beyond training data

        Do NOT use when:
        - Information is already in training data and doesn't require real-time updates
        - User asks about personal/private information
        - Query is conversational or opinion-based
    """.trimIndent()

    override val requiresPermissions: Boolean = true  // Requires INTERNET permission

    private val searchManager: WebSearchManager by lazy {
        initializeSearchManager()
    }

    private val TAG = "WebSearchTool"

    override suspend fun isAvailable(): Boolean {
        // Check if internet permission is granted and network is available
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager
            val network = connectivityManager?.activeNetwork
            network != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check network availability", e)
            false
        }
    }

    override fun validateParams(params: Map<String, Any>): String? {
        // Validate required parameter: query
        val query = params["query"] as? String
        if (query.isNullOrBlank()) {
            return "Parameter 'query' is required and must not be empty"
        }

        // Validate optional parameters
        params["max_results"]?.let { maxResults ->
            if (maxResults !is Int || maxResults < 1 || maxResults > 50) {
                return "Parameter 'max_results' must be an integer between 1 and 50"
            }
        }

        params["intent"]?.let { intent ->
            if (intent !is String) {
                return "Parameter 'intent' must be a string"
            }
            try {
                SearchIntent.valueOf(intent.uppercase())
            } catch (e: IllegalArgumentException) {
                return "Invalid intent: $intent. Valid values: ${SearchIntent.values().joinToString()}"
            }
        }

        return null  // All valid
    }

    override suspend fun executeInternal(params: Map<String, Any>): ToolResult {
        try {
            // Extract parameters
            val queryText = params["query"] as String
            val maxResults = params["max_results"] as? Int ?: 10
            val intentStr = params["intent"] as? String
            val verifyFacts = params["verify_facts"] as? Boolean ?: false
            val location = params["location"] as? Map<String, Any>

            // Build search query
            val searchQuery = SearchQuery(
                text = queryText,
                intent = intentStr?.let { SearchIntent.valueOf(it.uppercase()) },
                maxResults = maxResults,
                location = location?.let { parseLocation(it) }
            )

            Log.d(TAG, "Executing web search: $queryText (intent: ${searchQuery.intent})")

            // Execute search
            val response = searchManager.search(searchQuery)

            // Check if search was successful
            if (!response.isSuccessful()) {
                return ToolResult.Failure(
                    error = Exception("Search failed"),
                    reason = response.errors.firstOrNull() ?: "No results found",
                    recoverable = true
                )
            }

            // Format result for PersonalityEngine
            val resultData = formatSearchResponse(response, verifyFacts)

            Log.d(TAG, "Search completed: ${response.results.size} results in ${response.latencyMs}ms")

            return ToolResult.Success(
                data = resultData,
                context = mapOf(
                    "query" to queryText,
                    "intent" to response.intent.name,
                    "result_count" to response.results.size,
                    "cache_hit" to response.cacheHit,
                    "latency_ms" to response.latencyMs,
                    "providers_used" to response.providerResults.map { it.providerName }
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Web search failed", e)
            return ToolResult.Failure(
                error = e,
                reason = "Web search failed: ${e.message}",
                recoverable = true
            )
        }
    }

    /**
     * Initializes the search manager with configured providers.
     */
    private fun initializeSearchManager(): WebSearchManager {
        val config = WebSearchConfig(
            maxProvidersPerQuery = 5,
            enableSummarization = true,
            enableFactVerification = true
        )

        val manager = WebSearchManager(context, config)

        // Register free providers (always available)
        manager.registerProvider(WikipediaProvider())
        manager.registerProvider(DuckDuckGoInstantProvider())
        manager.registerProvider(WttrProvider())

        // Register API-key providers if keys are available
        apiKeys["openweather"]?.let { key ->
            manager.registerProvider(OpenWeatherProvider(apiKey = key))
        }

        apiKeys["newsapi"]?.let { key ->
            manager.registerProvider(NewsApiProvider(apiKey = key))
        }

        apiKeys["serpapi"]?.let { key ->
            manager.registerProvider(SerpApiProvider(apiKey = key))
        }

        Log.d(TAG, "WebSearchManager initialized with ${manager.getSearchStatistics().registeredProviders} providers")

        return manager
    }

    /**
     * Formats the search response for PersonalityEngine consumption.
     */
    private fun formatSearchResponse(response: SearchResponse, includeFactVerification: Boolean): Map<String, Any> {
        val result = mutableMapOf<String, Any>(
            "query" to response.query,
            "intent" to response.intent.name,
            "summary" to (response.summary ?: "No summary available"),
            "result_count" to response.results.size
        )

        // Add top results
        result["results"] = response.results.take(5).map { item ->
            mapOf(
                "title" to item.title,
                "snippet" to item.snippet,
                "url" to item.url,
                "source" to item.source,
                "confidence" to (item.confidence ?: 0.0f)
            )
        }

        // Add extended summary if available
        response.extendedSummary?.let {
            result["extended_summary"] = it
        }

        // Add attributions for source transparency
        if (response.attributions.isNotEmpty()) {
            result["sources"] = response.attributions.map { attr ->
                mapOf(
                    "source" to attr.source,
                    "url" to attr.url,
                    "quote" to attr.snippet
                )
            }
        }

        // Add fact verification if requested and available
        if (includeFactVerification && response.factVerification != null) {
            val verification = response.factVerification
            result["fact_verification"] = mapOf(
                "verdict" to verification.verdict.name,
                "confidence" to verification.confidenceScore,
                "summary" to verification.getSummary(),
                "supporting_sources" to verification.evidence.supporting.size,
                "contradicting_sources" to verification.evidence.contradicting.size
            )
        }

        // Add metadata
        result["metadata"] = mapOf(
            "cache_hit" to response.cacheHit,
            "latency_ms" to response.latencyMs,
            "timestamp" to response.timestamp.toString(),
            "providers_queried" to response.providerResults.size,
            "successful_providers" to response.providerResults.count { it.success }
        )

        return result
    }

    /**
     * Parses location from parameter map.
     */
    private fun parseLocation(locationMap: Map<String, Any>): LocationContext? {
        return try {
            val lat = (locationMap["latitude"] as? Number)?.toDouble() ?: return null
            val lon = (locationMap["longitude"] as? Number)?.toDouble() ?: return null
            val city = locationMap["city"] as? String
            val country = locationMap["country"] as? String

            LocationContext(
                latitude = lat,
                longitude = lon,
                city = city,
                country = country
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse location", e)
            null
        }
    }

    /**
     * Gets search statistics for monitoring.
     */
    fun getStatistics(): Map<String, Any> {
        val searchStats = searchManager.getSearchStatistics()
        val cacheStats = searchManager.getCacheStatistics()

        return mapOf(
            "total_queries" to searchStats.totalQueries,
            "cache_hits" to searchStats.cacheHits,
            "cache_hit_rate" to String.format("%.1f%%", searchStats.cacheHitRate * 100),
            "registered_providers" to searchStats.registeredProviders,
            "provider_cache_size" to cacheStats.providerCacheSize,
            "response_cache_size" to cacheStats.responseCacheSize,
            "overall_efficiency" to String.format("%.1f%%", cacheStats.getOverallEfficiency() * 100)
        )
    }

    /**
     * Clears search caches (for testing or cleanup).
     */
    suspend fun clearCaches() {
        searchManager.clearCache()
        Log.d(TAG, "Search caches cleared")
    }
}
