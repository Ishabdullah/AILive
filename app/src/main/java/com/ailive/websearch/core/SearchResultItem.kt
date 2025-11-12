package com.ailive.websearch.core

import com.squareup.moshi.JsonClass
import java.time.Instant

/**
 * Canonical representation of a single search result from any provider.
 *
 * All provider-specific result formats are normalized to this structure
 * for consistent processing, ranking, and display.
 *
 * @property title The title or headline of the result
 * @property snippet A brief excerpt or summary (typically 150-300 chars)
 * @property url The source URL
 * @property source The provider or domain that supplied this result
 * @property publishedAt When the content was published (null if unknown)
 * @property language Language code (ISO 639-1), null if unknown
 * @property confidence Provider's confidence score (0.0 to 1.0), null if not provided
 * @property imageUrl Optional thumbnail or featured image URL
 * @property metadata Additional provider-specific metadata
 * @since v1.4
 */
@JsonClass(generateAdapter = true)
data class SearchResultItem(
    val title: String,
    val snippet: String,
    val url: String,
    val source: String,
    val publishedAt: Instant? = null,
    val language: String? = null,
    val confidence: Float? = null,
    val imageUrl: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(title.isNotBlank()) { "Title cannot be blank" }
        require(url.isNotBlank()) { "URL cannot be blank" }
        require(source.isNotBlank()) { "Source cannot be blank" }
        confidence?.let {
            require(it in 0.0f..1.0f) { "Confidence must be between 0.0 and 1.0, got $it" }
        }
    }

    /**
     * Returns a concise quote from the snippet (max 25 words).
     */
    fun getQuote(maxWords: Int = 25): String {
        val words = snippet.split("\\s+".toRegex())
        return if (words.size <= maxWords) {
            snippet
        } else {
            words.take(maxWords).joinToString(" ") + "..."
        }
    }
}

/**
 * Attribution metadata for a search result.
 *
 * Provides full provenance information for fact-checking and citation.
 *
 * @property source The provider name (e.g., "Wikipedia", "NewsAPI")
 * @property url The source URL
 * @property retrievedAt When this result was retrieved
 * @property snippet The relevant excerpt
 * @property confidence Optional confidence score
 */
@JsonClass(generateAdapter = true)
data class Attribution(
    val source: String,
    val url: String,
    val retrievedAt: Instant,
    val snippet: String,
    val confidence: Float? = null
) {
    /**
     * Returns a formatted citation string.
     * Format: "Source: snippet (URL, Retrieved: timestamp)"
     */
    fun toCitation(): String {
        return "$source: ${snippet.take(100)}... ($url, Retrieved: $retrievedAt)"
    }
}
