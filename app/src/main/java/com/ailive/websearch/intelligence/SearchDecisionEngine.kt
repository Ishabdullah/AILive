package com.ailive.websearch.intelligence

import android.content.Context
import android.util.Log
import com.ailive.websearch.WebSearchManager
import com.ailive.websearch.core.SearchQuery
import com.ailive.websearch.core.SearchResponse
import com.squareup.moshi.JsonClass
import java.time.Duration
import java.time.Instant

/**
 * Intelligent decision engine for determining when to search the web.
 *
 * Integrates:
 * - Knowledge confidence analysis
 * - Search history tracking
 * - Context-aware decision making
 * - Adaptive search strategies
 *
 * This is the main entry point for intelligent web search detection.
 *
 * @param context Android context
 * @param searchManager WebSearchManager instance
 * @since v1.4
 */
class SearchDecisionEngine(
    context: Context,
    private val searchManager: WebSearchManager
) {
    private val TAG = "SearchDecisionEngine"

    private val confidenceAnalyzer = KnowledgeConfidenceAnalyzer()
    private val historyManager = SearchHistoryManager(context)

    /**
     * Analyzes a query and decides whether to search the web.
     *
     * Decision Process:
     * 1. Analyze knowledge confidence
     * 2. Check search history for recent similar searches
     * 3. Consider query context (location, time, etc.)
     * 4. Make final decision with reasoning
     *
     * @param query The user query
     * @param context Optional query context
     * @param forceSearch Force web search regardless of analysis
     * @return SearchDecision with recommendation and reasoning
     */
    suspend fun analyzeQuery(
        query: String,
        context: QueryContext? = null,
        forceSearch: Boolean = false
    ): SearchDecision {
        Log.d(TAG, "Analyzing query: '$query'")

        // Step 1: Confidence analysis
        val assessment = confidenceAnalyzer.analyze(query, context)

        if (forceSearch) {
            return SearchDecision(
                query = query,
                shouldSearch = true,
                searchTriggered = true,
                reason = "Force search requested by user",
                confidence = assessment.internalConfidence,
                urgency = SearchUrgency.HIGH,
                recommendedSources = selectSources(assessment),
                assessment = assessment
            )
        }

        // Step 2: Check search history
        val recentMatch = historyManager.findRecentSimilar(
            query = query,
            timeWindow = getTimeWindowForQuery(assessment)
        )

        // If we have a fresh recent match, skip search
        if (recentMatch != null && recentMatch.isFresh()) {
            Log.d(TAG, "Found recent similar search (${recentMatch.ageMinutes}min ago, similarity=${recentMatch.similarity})")

            return SearchDecision(
                query = query,
                shouldSearch = false,
                searchTriggered = false,
                reason = "Recent similar search found (${recentMatch.ageMinutes}min ago)",
                confidence = 0.8f,  // High confidence due to recent data
                urgency = SearchUrgency.NONE,
                recentMatch = recentMatch,
                assessment = assessment
            )
        }

        // Step 3: Make decision based on assessment
        val urgency = assessment.getUrgency()
        val shouldSearch = when (urgency) {
            SearchUrgency.HIGH -> true
            SearchUrgency.MEDIUM -> assessment.internalConfidence < 0.6f
            SearchUrgency.LOW -> assessment.internalConfidence < 0.8f
            SearchUrgency.NONE -> false
        }

        // Step 4: Select sources if searching
        val sources = if (shouldSearch) {
            selectSources(assessment)
        } else {
            emptyList()
        }

        return SearchDecision(
            query = query,
            shouldSearch = shouldSearch,
            searchTriggered = shouldSearch,
            reason = assessment.reasoning,
            confidence = assessment.internalConfidence,
            urgency = urgency,
            recommendedSources = sources,
            assessment = assessment,
            recentMatch = recentMatch
        )
    }

    /**
     * Executes a web search with the decision engine's recommendations.
     *
     * @param query The search query
     * @param decision The search decision (from analyzeQuery)
     * @return SearchExecutionResult with response and metadata
     */
    suspend fun executeSearch(
        query: String,
        decision: SearchDecision
    ): SearchExecutionResult {
        val startTime = System.currentTimeMillis()

        if (!decision.shouldSearch) {
            return SearchExecutionResult(
                success = false,
                response = null,
                reason = "Search not recommended: ${decision.reason}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        try {
            // Build search query
            val searchQuery = SearchQuery(
                text = query,
                intent = decision.assessment.detectedIntent,
                maxResults = getMaxResultsForUrgency(decision.urgency)
            )

            // Execute search
            val response = searchManager.search(searchQuery)

            // Record in history
            historyManager.recordSearch(query, response)

            val executionTime = System.currentTimeMillis() - startTime

            Log.d(TAG, "Search completed: ${response.results.size} results in ${executionTime}ms")

            return SearchExecutionResult(
                success = response.isSuccessful(),
                response = response,
                reason = response.getStatusMessage(),
                executionTimeMs = executionTime,
                fromCache = response.cacheHit
            )

        } catch (e: Exception) {
            Log.e(TAG, "Search execution failed", e)

            return SearchExecutionResult(
                success = false,
                response = null,
                reason = "Search failed: ${e.message}",
                executionTimeMs = System.currentTimeMillis() - startTime,
                error = e
            )
        }
    }

    /**
     * Convenience method: analyze + execute if recommended.
     *
     * @param query The user query
     * @param context Optional query context
     * @return SearchResult with decision and response
     */
    suspend fun searchIfNeeded(
        query: String,
        context: QueryContext? = null
    ): SearchResult {
        val decision = analyzeQuery(query, context)

        val execution = if (decision.shouldSearch) {
            executeSearch(query, decision)
        } else {
            null
        }

        return SearchResult(
            query = query,
            decision = decision,
            execution = execution,
            timestamp = Instant.now()
        )
    }

    /**
     * Gets search history statistics.
     */
    suspend fun getStatistics(): SearchStatistics {
        return historyManager.getStatistics()
    }

    /**
     * Clears search history.
     */
    suspend fun clearHistory() {
        historyManager.clearAll()
    }

    /**
     * Selects appropriate sources based on assessment.
     */
    private fun selectSources(assessment: ConfidenceAssessment): List<String> {
        val sources = mutableListOf<String>()

        when (assessment.detectedIntent) {
            com.ailive.websearch.core.SearchIntent.WEATHER -> {
                sources.add("OpenWeather")
                sources.add("wttr.in")
            }
            com.ailive.websearch.core.SearchIntent.PERSON_WHOIS -> {
                sources.add("Wikipedia")
                sources.add("DuckDuckGo")
            }
            com.ailive.websearch.core.SearchIntent.NEWS -> {
                sources.add("NewsAPI")
                sources.add("DuckDuckGo")
            }
            com.ailive.websearch.core.SearchIntent.FACT_CHECK -> {
                sources.add("Wikipedia")
                sources.add("NewsAPI")
                sources.add("DuckDuckGo")
            }
            else -> {
                sources.add("DuckDuckGo")
                sources.add("Wikipedia")
            }
        }

        return sources
    }

    /**
     * Determines time window for checking recent searches.
     */
    private fun getTimeWindowForQuery(assessment: ConfidenceAssessment): Duration {
        return when (assessment.getUrgency()) {
            SearchUrgency.HIGH -> Duration.ofMinutes(30)  // Real-time: short window
            SearchUrgency.MEDIUM -> Duration.ofHours(3)   // Recent: moderate window
            SearchUrgency.LOW -> Duration.ofHours(12)     // General: long window
            SearchUrgency.NONE -> Duration.ofDays(1)      // Background: very long
        }
    }

    /**
     * Determines max results based on urgency.
     */
    private fun getMaxResultsForUrgency(urgency: SearchUrgency): Int {
        return when (urgency) {
            SearchUrgency.HIGH -> 5   // Fast response
            SearchUrgency.MEDIUM -> 10  // Balanced
            SearchUrgency.LOW -> 15    // Comprehensive
            SearchUrgency.NONE -> 10
        }
    }
}

/**
 * Decision about whether to search the web.
 *
 * @property query The original query
 * @property shouldSearch Recommendation to search
 * @property searchTriggered Whether search will be triggered
 * @property reason Human-readable explanation
 * @property confidence Confidence in internal knowledge (0.0-1.0)
 * @property urgency Search urgency level
 * @property recommendedSources Recommended sources for this query
 * @property assessment Full confidence assessment
 * @property recentMatch Recent similar search (if found)
 */
@JsonClass(generateAdapter = true)
data class SearchDecision(
    val query: String,
    val shouldSearch: Boolean,
    val searchTriggered: Boolean,
    val reason: String,
    val confidence: Float,
    val urgency: SearchUrgency,
    val recommendedSources: List<String> = emptyList(),
    val assessment: ConfidenceAssessment,
    val recentMatch: RecentSearchMatch? = null
) {
    /**
     * Converts to JSON format for API responses.
     */
    fun toJson(): Map<String, Any> {
        return mapOf(
            "search_triggered" to searchTriggered,
            "should_search" to shouldSearch,
            "reason" to reason,
            "confidence" to confidence,
            "urgency" to urgency.name,
            "recommended_sources" to recommendedSources,
            "assessment" to mapOf(
                "temporal_signals" to assessment.temporalSignals,
                "uncertainty_signals" to assessment.uncertaintySignals,
                "realtime_required" to assessment.realtimeRequired,
                "location_dependent" to assessment.locationDependent,
                "time_sensitive" to assessment.timeSensitive,
                "detected_intent" to assessment.detectedIntent?.name
            ),
            "recent_match" to if (recentMatch != null) {
                mapOf(
                    "found" to true,
                    "age_minutes" to recentMatch.ageMinutes,
                    "similarity" to recentMatch.similarity
                )
            } else {
                mapOf("found" to false)
            }
        )
    }
}

/**
 * Result of search execution.
 *
 * @property success Whether search succeeded
 * @property response Search response (if successful)
 * @property reason Status message or error reason
 * @property executionTimeMs Time taken to execute
 * @property fromCache Whether result was from cache
 * @property error Exception if failed
 */
data class SearchExecutionResult(
    val success: Boolean,
    val response: SearchResponse?,
    val reason: String,
    val executionTimeMs: Long,
    val fromCache: Boolean = false,
    val error: Throwable? = null
) {
    /**
     * Converts to JSON format for API responses.
     */
    fun toJson(): Map<String, Any> {
        val json = mutableMapOf<String, Any>(
            "success" to success,
            "reason" to reason,
            "execution_time_ms" to executionTimeMs,
            "from_cache" to fromCache
        )

        response?.let { resp ->
            json["results"] = resp.results.map { result ->
                mapOf(
                    "title" to result.title,
                    "snippet" to result.snippet,
                    "url" to result.url,
                    "source" to result.source,
                    "confidence" to (result.confidence ?: 0.0f)
                )
            }

            json["summary"] = resp.summary ?: ""

            json["sources"] = resp.attributions.map { attr ->
                mapOf(
                    "name" to attr.source,
                    "url" to attr.url,
                    "confidence" to (attr.confidence ?: 0.0f)
                )
            }

            resp.factVerification?.let { verification ->
                json["fact_verification"] = mapOf(
                    "verdict" to verification.verdict.name,
                    "confidence" to verification.confidenceScore,
                    "summary" to verification.getSummary()
                )
            }
        }

        error?.let {
            json["error"] = it.message ?: "Unknown error"
        }

        return json
    }
}

/**
 * Complete search result including decision and execution.
 *
 * @property query The original query
 * @property decision The search decision
 * @property execution Execution result (if search was performed)
 * @property timestamp When this result was generated
 */
data class SearchResult(
    val query: String,
    val decision: SearchDecision,
    val execution: SearchExecutionResult?,
    val timestamp: Instant
) {
    /**
     * Converts to comprehensive JSON response.
     */
    fun toJson(): Map<String, Any> {
        val json = mutableMapOf<String, Any>(
            "query" to query,
            "timestamp" to timestamp.toString(),
            "decision" to decision.toJson()
        )

        execution?.let {
            json["execution"] = it.toJson()
        }

        return json
    }

    /**
     * Returns a formatted summary for logging/display.
     */
    fun getSummary(): String {
        return buildString {
            appendLine("Query: $query")
            appendLine("Decision: ${if (decision.shouldSearch) "SEARCH" else "SKIP"}")
            appendLine("Reason: ${decision.reason}")
            appendLine("Confidence: ${String.format("%.2f", decision.confidence)}")
            appendLine("Urgency: ${decision.urgency}")

            execution?.let {
                appendLine("Execution: ${if (it.success) "SUCCESS" else "FAILED"}")
                appendLine("Time: ${it.executionTimeMs}ms")
                appendLine("From Cache: ${it.fromCache}")

                it.response?.let { resp ->
                    appendLine("Results: ${resp.results.size}")
                    appendLine("Summary: ${resp.summary?.take(100) ?: "N/A"}")
                }
            }
        }
    }
}
