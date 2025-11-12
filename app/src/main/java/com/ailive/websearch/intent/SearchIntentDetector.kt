package com.ailive.websearch.intent

import com.ailive.websearch.core.IntentDetectionResult
import com.ailive.websearch.core.SearchIntent
import com.ailive.websearch.core.SearchQuery

/**
 * Detects user intent from search queries using rule-based classification.
 *
 * This implementation uses a combination of:
 * 1. Keyword matching (e.g., "weather", "who is", "latest news")
 * 2. Pattern matching (regex for specific query structures)
 * 3. Entity detection (person names, locations)
 * 4. Contextual hints (location data, time of day)
 *
 * Future versions may include lightweight ML classification.
 *
 * @since v1.4
 */
class SearchIntentDetector {

    /**
     * Detects the intent of a search query.
     *
     * @param query The search query to analyze
     * @return IntentDetectionResult with detected intent, confidence, and reasoning
     */
    fun detectIntent(query: SearchQuery): IntentDetectionResult {
        val text = query.text.lowercase().trim()

        // Check each intent in priority order
        return detectWeather(text, query)
            ?: detectPersonWhois(text)
            ?: detectNews(text)
            ?: detectFactCheck(text)
            ?: detectForum(text)
            ?: detectImage(text)
            ?: detectVideo(text)
            ?: detectGeneral(text)
    }

    /**
     * Detects weather-related queries.
     * Examples: "what's the weather", "temperature in Boston", "forecast for tomorrow"
     */
    private fun detectWeather(text: String, query: SearchQuery): IntentDetectionResult? {
        val weatherKeywords = listOf(
            "weather", "temperature", "forecast", "rain", "snow",
            "sunny", "cloudy", "humid", "wind", "storm", "hot", "cold"
        )

        val weatherPatterns = listOf(
            Regex("what'?s? the weather"),
            Regex("(weather|temperature|forecast) (in|for|at)"),
            Regex("(will it|is it going to) (rain|snow)"),
            Regex("how (hot|cold|warm) is it"),
        )

        // High confidence if query matches weather patterns
        for (pattern in weatherPatterns) {
            if (pattern.containsMatchIn(text)) {
                return IntentDetectionResult(
                    intent = SearchIntent.WEATHER,
                    confidence = 0.95f,
                    reasoning = "Query matches weather pattern: ${pattern.pattern}"
                )
            }
        }

        // Medium confidence if contains weather keywords
        val matchedKeywords = weatherKeywords.filter { text.contains(it) }
        if (matchedKeywords.isNotEmpty()) {
            // Higher confidence if location context is present
            val confidence = if (query.location != null) 0.9f else 0.75f
            return IntentDetectionResult(
                intent = SearchIntent.WEATHER,
                confidence = confidence,
                reasoning = "Query contains weather keywords: ${matchedKeywords.joinToString()}"
            )
        }

        return null
    }

    /**
     * Detects person/entity information queries.
     * Examples: "who is Ada Lovelace", "who was Einstein", "tell me about Marie Curie"
     */
    private fun detectPersonWhois(text: String): IntentDetectionResult? {
        val whoisPatterns = listOf(
            Regex("who (is|was|were) ([a-z]+ ){1,3}"),
            Regex("(tell me about|information about) ([a-z]+ ){1,3}"),
            Regex("(biography|bio) of ([a-z]+ ){1,3}"),
            Regex("who'?s ([a-z]+ ){1,3}")
        )

        for (pattern in whoisPatterns) {
            if (pattern.containsMatchIn(text)) {
                // Extract potential person name (capitalize for reasoning)
                val match = pattern.find(text)
                val name = match?.groups?.lastOrNull()?.value?.trim()?.split(" ")
                    ?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

                return IntentDetectionResult(
                    intent = SearchIntent.PERSON_WHOIS,
                    confidence = 0.90f,
                    reasoning = "Query asking about person${if (name != null) ": $name" else ""}"
                )
            }
        }

        return null
    }

    /**
     * Detects news-related queries.
     * Examples: "latest news about Tesla", "recent news on AI", "breaking news"
     */
    private fun detectNews(text: String): IntentDetectionResult? {
        val newsKeywords = listOf(
            "news", "latest", "recent", "breaking", "headline",
            "article", "report", "announcement", "update"
        )

        val newsPatterns = listOf(
            Regex("(latest|recent|breaking|today'?s?) news"),
            Regex("news (about|on|regarding)"),
            Regex("(what|any) (latest|recent) (news|updates|headlines)"),
            Regex("(top|latest|recent) (headlines|stories|articles)")
        )

        // High confidence for news patterns
        for (pattern in newsPatterns) {
            if (pattern.containsMatchIn(text)) {
                return IntentDetectionResult(
                    intent = SearchIntent.NEWS,
                    confidence = 0.92f,
                    reasoning = "Query matches news pattern: ${pattern.pattern}"
                )
            }
        }

        // Medium confidence for news keywords
        val matchedKeywords = newsKeywords.filter { text.contains(it) }
        if (matchedKeywords.size >= 2) {
            return IntentDetectionResult(
                intent = SearchIntent.NEWS,
                confidence = 0.80f,
                reasoning = "Query contains multiple news keywords: ${matchedKeywords.joinToString()}"
            )
        }

        return null
    }

    /**
     * Detects fact-checking queries.
     * Examples: "is it true that", "verify claim", "fact check"
     */
    private fun detectFactCheck(text: String): IntentDetectionResult? {
        val factCheckPatterns = listOf(
            Regex("(is it true|is this true) (that)?"),
            Regex("(fact check|verify|check) (if|that|whether)?"),
            Regex("(true or false|real or fake)"),
            Regex("(did .+ really|is it real that)")
        )

        for (pattern in factCheckPatterns) {
            if (pattern.containsMatchIn(text)) {
                return IntentDetectionResult(
                    intent = SearchIntent.FACT_CHECK,
                    confidence = 0.88f,
                    reasoning = "Query requesting fact verification"
                )
            }
        }

        return null
    }

    /**
     * Detects forum/discussion queries.
     * Examples: "reddit discussion on", "forum about", "people talking about"
     */
    private fun detectForum(text: String): IntentDetectionResult? {
        val forumKeywords = listOf("reddit", "forum", "discussion", "thread", "community")
        val forumPatterns = listOf(
            Regex("reddit (post|thread|discussion|comments?)"),
            Regex("(forum|discussion) (on|about|regarding)"),
            Regex("what (people|users) (are )?(saying|think|talking)")
        )

        for (pattern in forumPatterns) {
            if (pattern.containsMatchIn(text)) {
                return IntentDetectionResult(
                    intent = SearchIntent.FORUM,
                    confidence = 0.85f,
                    reasoning = "Query seeking community discussions"
                )
            }
        }

        if (forumKeywords.any { text.contains(it) }) {
            return IntentDetectionResult(
                intent = SearchIntent.FORUM,
                confidence = 0.70f,
                reasoning = "Query mentions forum/community platforms"
            )
        }

        return null
    }

    /**
     * Detects image search queries.
     * Examples: "images of", "pictures of", "show me photos"
     */
    private fun detectImage(text: String): IntentDetectionResult? {
        val imageKeywords = listOf("image", "picture", "photo", "screenshot", "thumbnail")
        val imagePatterns = listOf(
            Regex("(images?|pictures?|photos?) (of|for|showing)"),
            Regex("show me (images?|pictures?|photos?)"),
            Regex("(find|search) (for )?(images?|pictures?|photos?)")
        )

        for (pattern in imagePatterns) {
            if (pattern.containsMatchIn(text)) {
                return IntentDetectionResult(
                    intent = SearchIntent.IMAGE,
                    confidence = 0.87f,
                    reasoning = "Query requesting image results"
                )
            }
        }

        return null
    }

    /**
     * Detects video search queries.
     * Examples: "video of", "watch", "youtube"
     */
    private fun detectVideo(text: String): IntentDetectionResult? {
        val videoKeywords = listOf("video", "youtube", "watch", "clip", "tutorial")
        val videoPatterns = listOf(
            Regex("(video|videos|clip|clips) (of|for|about|on)"),
            Regex("(watch|show me) (video|videos)"),
            Regex("youtube (video|tutorial|clip)")
        )

        for (pattern in videoPatterns) {
            if (pattern.containsMatchIn(text)) {
                return IntentDetectionResult(
                    intent = SearchIntent.VIDEO,
                    confidence = 0.86f,
                    reasoning = "Query requesting video results"
                )
            }
        }

        return null
    }

    /**
     * Default: general web search.
     * Examples: "search for X", "how to do Y", "what is Z"
     */
    private fun detectGeneral(text: String): IntentDetectionResult {
        val generalPatterns = listOf(
            Regex("^(search|find|look up|google)"),
            Regex("how (to|do|does|can)"),
            Regex("what (is|are|does|do)"),
            Regex("why (is|are|does|do)"),
            Regex("when (is|are|was|were)")
        )

        for (pattern in generalPatterns) {
            if (pattern.containsMatchIn(text)) {
                return IntentDetectionResult(
                    intent = SearchIntent.GENERAL,
                    confidence = 0.75f,
                    reasoning = "General information query"
                )
            }
        }

        // Default fallback
        return IntentDetectionResult(
            intent = SearchIntent.GENERAL,
            confidence = 0.50f,
            reasoning = "No specific intent detected, defaulting to general search"
        )
    }
}
