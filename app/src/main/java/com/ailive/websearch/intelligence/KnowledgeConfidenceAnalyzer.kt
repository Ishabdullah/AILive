package com.ailive.websearch.intelligence

import android.util.Log
import com.ailive.websearch.core.SearchIntent
import com.ailive.websearch.core.SearchQuery
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Analyzes queries to determine confidence in internal knowledge.
 *
 * Detects:
 * - Temporal indicators (recent, current, latest, today, 2025, etc.)
 * - Knowledge cutoff relevance (queries about post-training events)
 * - Uncertainty signals (low confidence topics)
 *
 * @property knowledgeCutoffDate The AI's training data cutoff date
 * @since v1.4
 */
class KnowledgeConfidenceAnalyzer(
    private val knowledgeCutoffDate: LocalDate = LocalDate.of(2025, 1, 1)
) {
    private val TAG = "KnowledgeConfidence"

    companion object {
        // Temporal keywords indicating recent/current information needs
        private val TEMPORAL_INDICATORS = setOf(
            "latest", "current", "recent", "now", "today", "tonight", "tomorrow",
            "this week", "this month", "this year", "upcoming", "new",
            "breaking", "just", "recently", "update", "updated", "2025", "2026"
        )

        // Keywords suggesting uncertainty or need for verification
        private val UNCERTAINTY_INDICATORS = setOf(
            "is it true", "verify", "fact check", "confirm", "check if",
            "accurate", "correct", "real", "fake", "hoax"
        )

        // Topics that inherently require real-time data
        private val REALTIME_TOPICS = setOf(
            "weather", "stock", "price", "news", "score", "result",
            "traffic", "schedule", "availability", "open", "closed"
        )

        // Confidence thresholds
        private const val HIGH_CONFIDENCE_THRESHOLD = 0.8f
        private const val LOW_CONFIDENCE_THRESHOLD = 0.4f
    }

    /**
     * Analyzes a query and returns confidence assessment.
     *
     * @param query The user query text
     * @param context Optional context (location, time, user history)
     * @return ConfidenceAssessment with recommendation
     */
    fun analyze(query: String, context: QueryContext? = null): ConfidenceAssessment {
        val lowerQuery = query.lowercase()

        // Check for temporal indicators
        val hasTemporalIndicator = TEMPORAL_INDICATORS.any { lowerQuery.contains(it) }

        // Check for uncertainty indicators
        val hasUncertaintyIndicator = UNCERTAINTY_INDICATORS.any { lowerQuery.contains(it) }

        // Check for real-time topics
        val isRealtimeTopic = REALTIME_TOPICS.any { lowerQuery.contains(it) }

        // Check for specific year references beyond cutoff
        val mentionsRecentYear = detectYearReference(lowerQuery)

        // Check for location-specific queries
        val requiresLocation = context?.location != null && detectLocationDependency(lowerQuery)

        // Check for time-sensitive queries
        val requiresTimeSensitivity = detectTimeSensitivity(lowerQuery, context)

        // Calculate confidence score
        val confidence = calculateConfidence(
            hasTemporalIndicator,
            hasUncertaintyIndicator,
            isRealtimeTopic,
            mentionsRecentYear,
            requiresLocation,
            requiresTimeSensitivity
        )

        // Determine recommendation
        val shouldSearch = confidence < HIGH_CONFIDENCE_THRESHOLD

        // Generate reasoning
        val reasons = mutableListOf<String>()
        if (hasTemporalIndicator) reasons.add("query contains temporal keywords")
        if (mentionsRecentYear) reasons.add("query references year beyond training cutoff")
        if (isRealtimeTopic) reasons.add("topic requires real-time data")
        if (hasUncertaintyIndicator) reasons.add("user requesting verification")
        if (requiresLocation) reasons.add("query requires location-specific information")
        if (requiresTimeSensitivity) reasons.add("query is time-sensitive")

        val reasoning = if (shouldSearch) {
            "Web search recommended: ${reasons.joinToString(", ")}"
        } else {
            "Internal knowledge sufficient (confidence: ${String.format("%.2f", confidence)})"
        }

        Log.d(TAG, "Query analysis: '$query' -> confidence=$confidence, shouldSearch=$shouldSearch")

        return ConfidenceAssessment(
            query = query,
            internalConfidence = confidence,
            shouldSearch = shouldSearch,
            reasoning = reasoning,
            detectedIntent = detectIntent(lowerQuery),
            temporalSignals = hasTemporalIndicator || mentionsRecentYear,
            uncertaintySignals = hasUncertaintyIndicator,
            realtimeRequired = isRealtimeTopic,
            locationDependent = requiresLocation,
            timeSensitive = requiresTimeSensitivity
        )
    }

    /**
     * Detects if query mentions years beyond knowledge cutoff.
     */
    private fun detectYearReference(query: String): Boolean {
        val yearPattern = Regex("\\b(20\\d{2})\\b")
        val matches = yearPattern.findAll(query)

        return matches.any { match ->
            val year = match.value.toIntOrNull() ?: return@any false
            year > knowledgeCutoffDate.year
        }
    }

    /**
     * Detects if query depends on location context.
     */
    private fun detectLocationDependency(query: String): Boolean {
        val locationKeywords = setOf(
            "near me", "nearby", "local", "around here", "in my area",
            "weather", "restaurants", "stores", "events"
        )
        return locationKeywords.any { query.contains(it) }
    }

    /**
     * Detects if query is time-sensitive.
     */
    private fun detectTimeSensitivity(query: String, context: QueryContext?): Boolean {
        val timeKeywords = setOf(
            "today", "tonight", "tomorrow", "this morning", "this evening",
            "now", "current", "at the moment"
        )

        if (timeKeywords.any { query.contains(it) }) {
            return true
        }

        // Check if query involves relative time references
        context?.currentTime?.let { time ->
            val hour = time.hour
            if (query.contains("tonight") && hour < 18) return true
            if (query.contains("this morning") && hour >= 12) return true
        }

        return false
    }

    /**
     * Calculates overall confidence in internal knowledge.
     *
     * @return Confidence score (0.0 = no confidence, 1.0 = full confidence)
     */
    private fun calculateConfidence(
        hasTemporalIndicator: Boolean,
        hasUncertaintyIndicator: Boolean,
        isRealtimeTopic: Boolean,
        mentionsRecentYear: Boolean,
        requiresLocation: Boolean,
        requiresTimeSensitivity: Boolean
    ): Float {
        var confidence = 1.0f

        // Penalize confidence for various factors
        if (hasTemporalIndicator) confidence -= 0.3f
        if (mentionsRecentYear) confidence -= 0.4f
        if (isRealtimeTopic) confidence -= 0.5f
        if (hasUncertaintyIndicator) confidence -= 0.2f
        if (requiresLocation) confidence -= 0.2f
        if (requiresTimeSensitivity) confidence -= 0.3f

        return confidence.coerceIn(0.0f, 1.0f)
    }

    /**
     * Detects likely search intent from query.
     */
    private fun detectIntent(query: String): SearchIntent? {
        return when {
            REALTIME_TOPICS.any { query.contains(it) } -> {
                when {
                    query.contains("weather") -> SearchIntent.WEATHER
                    query.contains("news") -> SearchIntent.NEWS
                    else -> SearchIntent.GENERAL
                }
            }
            UNCERTAINTY_INDICATORS.any { query.contains(it) } -> SearchIntent.FACT_CHECK
            query.contains("who is") || query.contains("who was") -> SearchIntent.PERSON_WHOIS
            else -> null
        }
    }
}

/**
 * Assessment of knowledge confidence for a query.
 *
 * @property query The original query
 * @property internalConfidence Confidence in internal knowledge (0.0-1.0)
 * @property shouldSearch Recommendation to search web
 * @property reasoning Human-readable explanation
 * @property detectedIntent Detected search intent (if any)
 * @property temporalSignals Query contains temporal indicators
 * @property uncertaintySignals Query expresses uncertainty
 * @property realtimeRequired Topic requires real-time data
 * @property locationDependent Query depends on user location
 * @property timeSensitive Query is time-sensitive
 */
data class ConfidenceAssessment(
    val query: String,
    val internalConfidence: Float,
    val shouldSearch: Boolean,
    val reasoning: String,
    val detectedIntent: SearchIntent? = null,
    val temporalSignals: Boolean = false,
    val uncertaintySignals: Boolean = false,
    val realtimeRequired: Boolean = false,
    val locationDependent: Boolean = false,
    val timeSensitive: Boolean = false
) {
    /**
     * Returns urgency level for web search.
     * HIGH = Must search immediately
     * MEDIUM = Should search if cache miss
     * LOW = Optional, can use internal knowledge
     */
    fun getUrgency(): SearchUrgency {
        return when {
            realtimeRequired -> SearchUrgency.HIGH
            temporalSignals && timeSensitive -> SearchUrgency.HIGH
            internalConfidence < 0.4f -> SearchUrgency.MEDIUM
            uncertaintySignals -> SearchUrgency.MEDIUM
            shouldSearch -> SearchUrgency.LOW
            else -> SearchUrgency.NONE
        }
    }
}

/**
 * Context for query analysis.
 *
 * @property location User's current location
 * @property currentTime Current time
 * @property recentQueries Recent user queries
 * @property userPreferences User's search preferences
 */
data class QueryContext(
    val location: LocationInfo? = null,
    val currentTime: LocalDateTime = LocalDateTime.now(),
    val recentQueries: List<String> = emptyList(),
    val userPreferences: Map<String, Any> = emptyMap()
)

/**
 * Location information for context-aware analysis.
 */
data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val city: String? = null,
    val country: String? = null
)

/**
 * Search urgency levels.
 */
enum class SearchUrgency {
    NONE,    // No search needed
    LOW,     // Optional search
    MEDIUM,  // Recommended search
    HIGH     // Required search
}
