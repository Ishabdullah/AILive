package com.ailive.websearch.integration

import android.content.Context
import android.util.Log
import com.ailive.personality.tools.BaseTool
import com.ailive.personality.tools.ToolResult
import com.ailive.websearch.WebSearchManager
import com.ailive.websearch.WebSearchConfig
import com.ailive.websearch.core.*
import com.ailive.websearch.intelligence.SearchDecisionEngine
import com.ailive.websearch.intelligence.QueryContext
import com.ailive.websearch.intelligence.LocationInfo
import com.ailive.websearch.providers.general.DuckDuckGoInstantProvider
import com.ailive.websearch.providers.general.SerpApiProvider
import com.ailive.websearch.providers.news.NewsApiProvider
import com.ailive.websearch.providers.weather.OpenWeatherProvider
import com.ailive.websearch.providers.weather.WttrProvider
import com.ailive.websearch.providers.wiki.WikipediaProvider

/**
 * Web Search tool for AILive PersonalityEngine with Intelligent Search Detection.
 *
 * Provides web search capabilities with automatic detection of when searches are needed.
 *
 * Capabilities:
 * - Automatic knowledge confidence detection
 * - Context-aware decision making (location, time, history)
 * - Intent-based search routing (weather, news, people, general)
 * - Multi-provider aggregation for comprehensive results
 * - Source attribution and fact verification
 * - Smart summarization for concise responses
 * - Adaptive search strategy with history tracking
 *
 * Parameters:
 * - query (String, required): The search query
 * - auto_detect (Boolean, optional): Auto-detect if search is needed (default: true)
 * - intent (String, optional): Explicit intent (WEATHER, NEWS, PERSON_WHOIS, GENERAL, FACT_CHECK)
 * - max_results (Int, optional): Maximum results to return (default: 10)
 * - verify_facts (Boolean, optional): Enable fact verification (default: false)
 * - location (Map, optional): Location context {latitude, longitude, city, country}
 *
 * Example usage:
 * ```
 * // Auto-detect mode (recommended)
 * execute(mapOf(
 *     "query" to "What's the weather in Boston?",
 *     "auto_detect" to true
 * ))
 *
 * // Explicit search mode
 * execute(mapOf(
 *     "query" to "Latest news about Tesla",
 *     "auto_detect" to false,
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
        Search the web for current information with intelligent detection of when searches are needed.

        INTELLIGENT AUTO-DETECTION (recommended):
        - Automatically detects when queries need web search based on temporal signals
        - Analyzes knowledge confidence (queries about 2025+, "recent", "latest", etc.)
        - Checks search history to avoid redundant queries
        - Uses location and time context for better decisions

        Use when:
        - User asks about current events, news, or recent information
        - User requests weather forecasts or current conditions
        - User asks about people, places, or entities requiring up-to-date info
        - User wants to verify facts or check claims
        - User's question requires real-time or external knowledge beyond training data
        - Query contains temporal keywords: "today", "now", "recent", "latest", "2025"

        Do NOT use when:
        - Information is already in training data and doesn't require real-time updates
        - User asks about personal/private information
        - Query is conversational or opinion-based
        - Recent similar search was performed (checked automatically)

        MODES:
        - auto_detect=true (default): AI decides if search is needed
        - auto_detect=false: Always search (explicit mode)
    """.trimIndent()

    override val requiresPermissions: Boolean = true  // Requires INTERNET permission

    private val searchManager: WebSearchManager by lazy {
        initializeSearchManager()
    }

    private val decisionEngine: SearchDecisionEngine by lazy {
        SearchDecisionEngine(context, searchManager)
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
            val autoDetect = params["auto_detect"] as? Boolean ?: true
            val maxResults = params["max_results"] as? Int ?: 10
            val intentStr = params["intent"] as? String
            val verifyFacts = params["verify_facts"] as? Boolean ?: false
            val locationMap = params["location"] as? Map<String, Any>

            // Build query context
            val queryContext = QueryContext(
                location = locationMap?.let { parseLocationInfo(it) }
            )

            // MODE 1: AUTO-DETECT (Intelligent Search Detection)
            if (autoDetect) {
                Log.d(TAG, "Auto-detect mode: analyzing query '$queryText'")

                val result = decisionEngine.searchIfNeeded(queryText, queryContext)

                // If search was not recommended, return decision explanation
                if (!result.decision.shouldSearch) {
                    Log.d(TAG, "Search not needed: ${result.decision.reason}")

                    return ToolResult.Success(
                        data = mapOf(
                            "search_triggered" to false,
                            "should_search" to false,
                            "reason" to result.decision.reason,
                            "confidence" to result.decision.confidence,
                            "internal_knowledge_sufficient" to true,
                            "decision" to result.decision.toJson()
                        ),
                        context = mapOf(
                            "query" to queryText,
                            "auto_detect" to true,
                            "search_skipped" to true
                        )
                    )
                }

                // Search was executed, return results
                if (result.execution?.success == true && result.execution.response != null) {
                    val response = result.execution.response

                    val resultData: MutableMap<String, Any> = mutableMapOf(
                        "search_triggered" to true,
                        "should_search" to true,
                        "reason" to result.decision.reason,
                        "confidence" to result.decision.confidence
                    )

                    // Add search results
                    resultData.putAll(formatSearchResponse(response, verifyFacts))

                    // Add decision metadata
                    resultData["decision"] = result.decision.toJson()
                    resultData["execution"] = result.execution.toJson()

                    Log.d(TAG, "Auto-detect search completed: ${response.results.size} results")

                    return ToolResult.Success(
                        data = resultData,
                        context = mapOf(
                            "query" to queryText,
                            "auto_detect" to true,
                            "intent" to response.intent.name,
                            "result_count" to response.results.size,
                            "cache_hit" to response.cacheHit,
                            "latency_ms" to response.latencyMs,
                            "urgency" to result.decision.urgency.name
                        )
                    )
                }

                // Search failed
                return ToolResult.Failure(
                    error = result.execution?.error ?: Exception("Search failed"),
                    reason = result.execution?.reason ?: "Unknown error",
                    recoverable = true
                )
            }

            // MODE 2: EXPLICIT SEARCH (Always search, no auto-detection)
            Log.d(TAG, "Explicit search mode: executing search for '$queryText'")

            val searchQuery = SearchQuery(
                text = queryText,
                intent = intentStr?.let { SearchIntent.valueOf(it.uppercase()) },
                maxResults = maxResults,
                location = locationMap?.let { parseLocation(it) }
            )

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
            val resultData: MutableMap<String, Any> = mutableMapOf(
                "search_triggered" to true,
                "should_search" to true,
                "reason" to "Explicit search mode"
            )
            resultData.putAll(formatSearchResponse(response, verifyFacts))

            Log.d(TAG, "Explicit search completed: ${response.results.size} results in ${response.latencyMs}ms")

            return ToolResult.Success(
                data = resultData,
                context = mapOf(
                    "query" to queryText,
                    "auto_detect" to false,
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
     * Parses location from parameter map (for SearchQuery).
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
     * Parses location from parameter map (for QueryContext / SearchDecisionEngine).
     */
    private fun parseLocationInfo(locationMap: Map<String, Any>): LocationInfo? {
        return try {
            val lat = (locationMap["latitude"] as? Number)?.toDouble() ?: return null
            val lon = (locationMap["longitude"] as? Number)?.toDouble() ?: return null
            val city = locationMap["city"] as? String
            val country = locationMap["country"] as? String

            LocationInfo(
                latitude = lat,
                longitude = lon,
                city = city,
                country = country
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse location info", e)
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
