package com.ailive.websearch.core

import java.time.Instant

/**
 * Result from a single search provider.
 *
 * Encapsulates the results, metadata, and health status from one provider's
 * search operation.
 *
 * @property providerName The name of the provider (e.g., "OpenWeather", "Wikipedia")
 * @property success Whether the provider successfully returned results
 * @property results List of search result items
 * @property error Optional error message if the provider failed
 * @property latencyMs Time taken to fetch results in milliseconds
 * @property retrievedAt When the results were retrieved
 * @property metadata Provider-specific metadata (e.g., quota remaining, API version)
 * @since v1.4
 */
data class ProviderResult(
    val providerName: String,
    val success: Boolean,
    val results: List<SearchResultItem> = emptyList(),
    val error: String? = null,
    val latencyMs: Long,
    val retrievedAt: Instant = Instant.now(),
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(providerName.isNotBlank()) { "Provider name cannot be blank" }
        require(latencyMs >= 0) { "Latency must be non-negative, got $latencyMs" }
        if (!success) {
            require(!error.isNullOrBlank()) { "Error message required when success=false" }
        }
    }

    /**
     * Returns the number of results.
     */
    val resultCount: Int
        get() = results.size

    /**
     * Checks if this provider had usable results.
     */
    fun hasResults(): Boolean = success && results.isNotEmpty()

    companion object {
        /**
         * Creates a successful result.
         */
        fun success(
            providerName: String,
            results: List<SearchResultItem>,
            latencyMs: Long,
            metadata: Map<String, String> = emptyMap()
        ): ProviderResult {
            return ProviderResult(
                providerName = providerName,
                success = true,
                results = results,
                error = null,
                latencyMs = latencyMs,
                metadata = metadata
            )
        }

        /**
         * Creates a failed result.
         */
        fun failure(
            providerName: String,
            error: String,
            latencyMs: Long,
            metadata: Map<String, String> = emptyMap()
        ): ProviderResult {
            return ProviderResult(
                providerName = providerName,
                success = false,
                results = emptyList(),
                error = error,
                latencyMs = latencyMs,
                metadata = metadata
            )
        }
    }
}

/**
 * Health status of a search provider.
 *
 * @property providerName The provider name
 * @property healthy Whether the provider is currently operational
 * @property errorMessage Optional error message if unhealthy
 * @property quotaRemaining Optional quota/rate-limit information
 * @property lastChecked When this status was last checked
 */
data class ProviderStatus(
    val providerName: String,
    val healthy: Boolean,
    val errorMessage: String? = null,
    val quotaRemaining: Int? = null,
    val lastChecked: Instant = Instant.now()
)
