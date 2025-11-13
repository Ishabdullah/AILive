package com.ailive.websearch.core

/**
 * Enumeration of search intent types for routing queries to appropriate providers.
 *
 * Used by [SearchIntentDetector] to classify user queries and determine which
 * search providers should handle the request.
 *
 * @since v1.4
 */
enum class SearchIntent {
    /** Weather-related queries (e.g., "What's the weather in Boston?") */
    WEATHER,

    /** Person/entity information queries (e.g., "Who is Ada Lovelace?") */
    PERSON_WHOIS,

    /** News and current events (e.g., "Latest news about Tesla") */
    NEWS,

    /** General web search (e.g., "How to connect to ADB wirelessly") */
    GENERAL,

    /** Forum discussions and community threads */
    FORUM,

    /** Image search queries */
    IMAGE,

    /** Video search queries */
    VIDEO,

    /** Fact verification requests */
    FACT_CHECK,

    /** Unable to determine intent */
    UNKNOWN
}

/**
 * Result of intent detection with confidence score.
 *
 * @property intent The detected search intent
 * @property confidence Confidence score (0.0 to 1.0)
 * @property reasoning Human-readable explanation of why this intent was selected
 */
data class IntentDetectionResult(
    val intent: SearchIntent,
    val confidence: Float,
    val reasoning: String
) {
    init {
        require(confidence in 0.0f..1.0f) {
            "Confidence must be between 0.0 and 1.0, got $confidence"
        }
    }
}
