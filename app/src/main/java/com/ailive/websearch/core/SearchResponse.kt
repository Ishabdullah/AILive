package com.ailive.websearch.core

import com.squareup.moshi.JsonClass
import java.time.Instant

/**
 * Comprehensive search response from the Web Search subsystem.
 *
 * This is the primary output format returned by [WebSearchManager] after
 * orchestrating multiple providers, ranking, deduplication, and summarization.
 *
 * @property queryId Unique identifier matching the original query
 * @property query The original query text
 * @property intent The detected search intent
 * @property results Ranked and deduplicated search results
 * @property summary Brief TL;DR summary (1-3 sentences)
 * @property extendedSummary Optional extended summary with more details
 * @property attributions List of top sources used for the summary
 * @property factVerification Optional fact-checking results (if applicable)
 * @property providerResults Raw results from each provider (for debugging/telemetry)
 * @property totalResults Total number of results before deduplication
 * @property latencyMs Total time taken to complete the search
 * @property timestamp When this search was completed
 * @property cacheHit Whether this result was served from cache
 * @property errors Any errors encountered during the search
 * @since v1.4
 */
@JsonClass(generateAdapter = true)
data class SearchResponse(
    val queryId: String,
    val query: String,
    val intent: SearchIntent,
    val results: List<SearchResultItem>,
    val summary: String? = null,
    val extendedSummary: String? = null,
    val attributions: List<Attribution> = emptyList(),
    val factVerification: FactVerificationResult? = null,
    val providerResults: List<ProviderResult> = emptyList(),
    val totalResults: Int,
    val latencyMs: Long,
    val timestamp: Instant = Instant.now(),
    val cacheHit: Boolean = false,
    val errors: List<String> = emptyList()
) {
    init {
        require(queryId.isNotBlank()) { "Query ID cannot be blank" }
        require(query.isNotBlank()) { "Query cannot be blank" }
        require(totalResults >= 0) { "Total results must be non-negative" }
        require(latencyMs >= 0) { "Latency must be non-negative" }
    }

    /**
     * Returns whether the search was successful (has any results).
     */
    fun isSuccessful(): Boolean = results.isNotEmpty()

    /**
     * Returns whether the search was partially successful (some providers failed).
     */
    fun isPartialSuccess(): Boolean = isSuccessful() && errors.isNotEmpty()

    /**
     * Returns a user-friendly status message.
     */
    fun getStatusMessage(): String {
        return when {
            !isSuccessful() -> "No results found"
            errors.isEmpty() -> "Found ${results.size} results"
            else -> "Found ${results.size} results (${errors.size} providers unavailable)"
        }
    }

    /**
     * Returns the top N attributions.
     */
    fun getTopAttributions(n: Int = 5): List<Attribution> {
        return attributions.take(n)
    }
}

/**
 * Result of fact verification for a claim.
 *
 * @property claim The claim being verified
 * @property verdict The verification verdict
 * @property evidence Supporting and contradicting evidence
 * @property confidenceScore Confidence in the verdict (0.0 to 1.0)
 * @property provenance Detailed source attributions
 * @since v1.4
 */
@JsonClass(generateAdapter = true)
data class FactVerificationResult(
    val claim: String,
    val verdict: Verdict,
    val evidence: Evidence,
    val confidenceScore: Float,
    val provenance: List<Attribution>
) {
    init {
        require(claim.isNotBlank()) { "Claim cannot be blank" }
        require(confidenceScore in 0.0f..1.0f) {
            "Confidence score must be between 0.0 and 1.0, got $confidenceScore"
        }
    }

    /**
     * Verdict for fact verification.
     */
    enum class Verdict {
        /** The claim is supported by multiple sources */
        SUPPORTS,

        /** The claim is contradicted by multiple sources */
        CONTRADICTS,

        /** Insufficient or conflicting evidence */
        INCONCLUSIVE,

        /** The claim is unverified (e.g., about living persons) */
        UNVERIFIED
    }

    /**
     * Evidence supporting or contradicting a claim.
     *
     * @property supporting List of sources that support the claim
     * @property contradicting List of sources that contradict the claim
     * @property neutral List of sources that are neutral or inconclusive
     */
    @JsonClass(generateAdapter = true)
    data class Evidence(
        val supporting: List<SearchResultItem> = emptyList(),
        val contradicting: List<SearchResultItem> = emptyList(),
        val neutral: List<SearchResultItem> = emptyList()
    ) {
        /**
         * Returns total number of evidence items.
         */
        val totalCount: Int
            get() = supporting.size + contradicting.size + neutral.size

        /**
         * Returns whether there's conflicting evidence.
         */
        fun hasConflict(): Boolean {
            return supporting.isNotEmpty() && contradicting.isNotEmpty()
        }
    }

    /**
     * Returns a human-readable summary of the verification.
     */
    fun getSummary(): String {
        return when (verdict) {
            Verdict.SUPPORTS -> "Claim is supported by ${evidence.supporting.size} sources (confidence: ${String.format("%.1f%%", confidenceScore * 100)})"
            Verdict.CONTRADICTS -> "Claim is contradicted by ${evidence.contradicting.size} sources (confidence: ${String.format("%.1f%%", confidenceScore * 100)})"
            Verdict.INCONCLUSIVE -> "Insufficient evidence: ${evidence.supporting.size} supporting, ${evidence.contradicting.size} contradicting"
            Verdict.UNVERIFIED -> "Claim could not be verified from available sources"
        }
    }
}
