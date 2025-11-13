package com.ailive.websearch.core

/**
 * Interface for all search providers.
 *
 * Each provider implements this interface to provide search results from
 * a specific data source (e.g., OpenWeather, Wikipedia, NewsAPI).
 *
 * Implementations should:
 * - Be thread-safe and support concurrent requests
 * - Use coroutines for async operations
 * - Handle errors gracefully and return ProviderResult with error details
 * - Respect rate limits and implement backoff
 * - Not leak sensitive data (API keys, PII) in logs or responses
 *
 * @since v1.4
 */
interface SearchProvider {
    /**
     * The unique name of this provider (e.g., "OpenWeather", "Wikipedia").
     */
    val name: String

    /**
     * The supported search intents for this provider.
     */
    val supportedIntents: Set<SearchIntent>

    /**
     * Execute a search query.
     *
     * This method should:
     * 1. Validate the query is appropriate for this provider
     * 2. Make the API call (with timeout and retry logic)
     * 3. Parse the response into SearchResultItem objects
     * 4. Return a ProviderResult with results or error
     *
     * @param query The search query to execute
     * @return ProviderResult with search results or error information
     */
    suspend fun search(query: SearchQuery): ProviderResult

    /**
     * Check the health status of this provider.
     *
     * This is used for:
     * - Circuit breaker pattern (skip unhealthy providers)
     * - UI status indicators
     * - Telemetry and monitoring
     *
     * @return ProviderStatus indicating health, quota, and errors
     */
    suspend fun healthCheck(): ProviderStatus

    /**
     * Get the priority of this provider for a given intent.
     *
     * Higher values = higher priority. This is used for provider selection
     * when multiple providers support the same intent.
     *
     * @param intent The search intent
     * @return Priority value (0-100), or 0 if intent not supported
     */
    fun getPriority(intent: SearchIntent): Int {
        return if (intent in supportedIntents) 50 else 0
    }

    /**
     * Checks if this provider can handle the given query.
     *
     * @param query The search query
     * @return true if this provider can handle the query
     */
    fun canHandle(query: SearchQuery): Boolean {
        val intent = query.intent ?: return false
        return intent in supportedIntents
    }
}

/**
 * Base implementation of SearchProvider with common utilities.
 *
 * Providers can extend this class to inherit common functionality
 * like timing, error handling, and logging.
 */
abstract class BaseSearchProvider : SearchProvider {
    /**
     * Executes a search with timing and error handling.
     */
    protected suspend fun <T> timedSearch(
        block: suspend () -> T
    ): Pair<T, Long> {
        val startTime = System.currentTimeMillis()
        val result = block()
        val latency = System.currentTimeMillis() - startTime
        return result to latency
    }

    /**
     * Creates a standardized error result.
     */
    protected fun createErrorResult(
        error: String,
        latency: Long,
        metadata: Map<String, String> = emptyMap()
    ): ProviderResult {
        return ProviderResult.failure(
            providerName = name,
            error = error,
            latencyMs = latency,
            metadata = metadata
        )
    }

    /**
     * Creates a standardized success result.
     */
    protected fun createSuccessResult(
        results: List<SearchResultItem>,
        latency: Long,
        metadata: Map<String, String> = emptyMap()
    ): ProviderResult {
        return ProviderResult.success(
            providerName = name,
            results = results,
            latencyMs = latency,
            metadata = metadata
        )
    }
}
