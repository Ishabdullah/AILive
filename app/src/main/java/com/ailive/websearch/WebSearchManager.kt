package com.ailive.websearch

import android.content.Context
import android.util.Log
import com.ailive.websearch.cache.CacheLayer
import com.ailive.websearch.core.*
import com.ailive.websearch.intent.SearchIntentDetector
import com.ailive.websearch.ratelimit.RateLimiterManager
import com.ailive.websearch.summarizer.ResultSummarizer
import com.ailive.websearch.verification.FactVerifier
import kotlinx.coroutines.*
import java.time.Instant

/**
 * Main orchestrator for the Web Search subsystem.
 *
 * Responsibilities:
 * - Intent detection and provider selection
 * - Query fan-out to multiple providers
 * - Result aggregation, ranking, and deduplication
 * - Summarization with source attribution
 * - Fact verification
 * - Caching and rate limiting
 * - Telemetry and diagnostics
 *
 * Architecture:
 * - Uses coroutines for concurrent provider calls
 * - Fail-open: returns best-effort results even if some providers fail
 * - Provider health tracking with circuit breaker pattern
 * - Supports cancelation for responsive Android UI
 *
 * @param context Android context
 * @param config Search configuration
 * @since v1.4
 */
class WebSearchManager(
    private val context: Context,
    private val config: WebSearchConfig = WebSearchConfig()
) {
    private val TAG = "WebSearchManager"

    // Core components
    private val intentDetector = SearchIntentDetector()
    private val summarizer = ResultSummarizer()
    private val factVerifier = FactVerifier(summarizer)
    private val cacheLayer = CacheLayer()
    private val rateLimiter = RateLimiterManager()

    // Provider registry
    private val providers = mutableListOf<SearchProvider>()

    // Statistics
    private var totalQueries = 0
    private var cacheHits = 0

    init {
        // Initialize rate limiters for each provider
        initializeRateLimiters()
    }

    /**
     * Registers a search provider.
     *
     * @param provider The provider to register
     */
    fun registerProvider(provider: SearchProvider) {
        providers.add(provider)
        Log.d(TAG, "Registered provider: ${provider.name}")
    }

    /**
     * Unregisters a search provider.
     *
     * @param providerName The name of the provider to unregister
     */
    fun unregisterProvider(providerName: String) {
        providers.removeAll { it.name == providerName }
        Log.d(TAG, "Unregistered provider: $providerName")
    }

    /**
     * Executes a search query.
     *
     * Process:
     * 1. Detect intent if not specified
     * 2. Check cache
     * 3. Select appropriate providers
     * 4. Fan-out query to providers (parallel)
     * 5. Aggregate and rank results
     * 6. Deduplicate
     * 7. Summarize with attribution
     * 8. Fact-check if requested
     * 9. Cache results
     *
     * @param query The search query
     * @return SearchResponse with results, summary, and metadata
     */
    suspend fun search(query: SearchQuery): SearchResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        totalQueries++

        try {
            // Step 1: Detect intent if not specified
            val enrichedQuery = if (query.intent == null) {
                val detectionResult = intentDetector.detectIntent(query)
                Log.d(TAG, "Detected intent: ${detectionResult.intent} (${detectionResult.confidence})")
                query.withIntent(detectionResult.intent)
            } else {
                query
            }

            // Step 2: Check cache
            cacheLayer.getResponse(enrichedQuery)?.let { cachedResponse ->
                cacheHits++
                Log.d(TAG, "Cache hit for query: ${query.text}")
                return@withContext cachedResponse.copy(
                    cacheHit = true,
                    latencyMs = System.currentTimeMillis() - startTime
                )
            }

            // Step 3: Select providers based on intent
            val selectedProviders = selectProviders(enrichedQuery)
            if (selectedProviders.isEmpty()) {
                return@withContext createErrorResponse(
                    query = enrichedQuery,
                    error = "No providers available for intent: ${enrichedQuery.intent}",
                    latency = System.currentTimeMillis() - startTime
                )
            }

            Log.d(TAG, "Selected ${selectedProviders.size} providers for intent ${enrichedQuery.intent}")

            // Step 4: Fan-out to providers (parallel with timeout)
            val providerResults = withTimeoutOrNull(enrichedQuery.timeout) {
                queryProviders(selectedProviders, enrichedQuery)
            } ?: run {
                Log.w(TAG, "Search timeout after ${enrichedQuery.timeout}ms")
                emptyList()
            }

            // Step 5-6: Aggregate, rank, and deduplicate
            val aggregatedResults = aggregateResults(providerResults)
            val rankedResults = rankResults(aggregatedResults)
            val deduplicatedResults = deduplicateResults(rankedResults)
                .take(enrichedQuery.maxResults)

            // Check if we have any results
            if (deduplicatedResults.isEmpty()) {
                val errors = providerResults.filter { !it.success }.map { it.error ?: "Unknown error" }
                return@withContext createErrorResponse(
                    query = enrichedQuery,
                    error = "No results found from ${providerResults.size} providers",
                    latency = System.currentTimeMillis() - startTime,
                    errors = errors
                )
            }

            // Step 7: Summarize with attribution
            val summaryReport = if (config.enableSummarization) {
                summarizer.generateSummaryReport(deduplicatedResults, providerResults)
            } else {
                null
            }

            // Step 8: Fact-check if requested
            val factVerification = if (enrichedQuery.intent == SearchIntent.FACT_CHECK) {
                factVerifier.verify(enrichedQuery.text, providerResults)
            } else {
                null
            }

            // Create response
            val response = SearchResponse(
                queryId = enrichedQuery.id,
                query = enrichedQuery.text,
                intent = enrichedQuery.intent ?: SearchIntent.GENERAL,
                results = deduplicatedResults,
                summary = summaryReport?.briefSummary,
                extendedSummary = summaryReport?.extendedSummary,
                attributions = summaryReport?.attributions ?: emptyList(),
                factVerification = factVerification,
                providerResults = providerResults,
                totalResults = aggregatedResults.size,
                latencyMs = System.currentTimeMillis() - startTime,
                cacheHit = false,
                errors = providerResults.filter { !it.success }.map { "${it.providerName}: ${it.error}" }
            )

            // Step 9: Cache result
            cacheLayer.putResponse(enrichedQuery, response)

            Log.d(TAG, "Search completed: ${deduplicatedResults.size} results in ${response.latencyMs}ms")
            response

        } catch (e: CancellationException) {
            Log.w(TAG, "Search cancelled for query: ${query.text}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for query: ${query.text}", e)
            createErrorResponse(
                query = query,
                error = "Search failed: ${e.message}",
                latency = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Queries multiple providers in parallel.
     */
    private suspend fun queryProviders(
        providers: List<SearchProvider>,
        query: SearchQuery
    ): List<ProviderResult> = coroutineScope {
        providers.map { provider ->
            async {
                queryProvider(provider, query)
            }
        }.awaitAll()
    }

    /**
     * Queries a single provider with rate limiting.
     */
    private suspend fun queryProvider(
        provider: SearchProvider,
        query: SearchQuery
    ): ProviderResult {
        try {
            // Check rate limit
            if (!rateLimiter.tryAcquire(provider.name)) {
                Log.w(TAG, "Rate limit exceeded for provider: ${provider.name}")
                return ProviderResult.failure(
                    providerName = provider.name,
                    error = "Rate limit exceeded",
                    latencyMs = 0,
                    metadata = mapOf("rate_limited" to "true")
                )
            }

            // Check cache first
            cacheLayer.getProviderResult(provider.name, query)?.let { cached ->
                Log.d(TAG, "Provider cache hit: ${provider.name}")
                return cached
            }

            // Execute search
            val result = provider.search(query)

            // Cache successful results
            if (result.success) {
                cacheLayer.putProviderResult(provider.name, query, result)
            }

            return result

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Provider ${provider.name} failed", e)
            return ProviderResult.failure(
                providerName = provider.name,
                error = e.message ?: "Unknown error",
                latencyMs = 0
            )
        }
    }

    /**
     * Selects providers based on query intent and priority.
     */
    private fun selectProviders(query: SearchQuery): List<SearchProvider> {
        val intent = query.intent ?: SearchIntent.GENERAL

        return providers
            .filter { it.canHandle(query) }
            .sortedByDescending { it.getPriority(intent) }
            .take(config.maxProvidersPerQuery)
    }

    /**
     * Aggregates results from all providers.
     */
    private fun aggregateResults(providerResults: List<ProviderResult>): List<SearchResultItem> {
        return providerResults
            .filter { it.success }
            .flatMap { it.results }
    }

    /**
     * Ranks results by confidence and relevance.
     */
    private fun rankResults(results: List<SearchResultItem>): List<SearchResultItem> {
        return results.sortedByDescending { result ->
            // Combine confidence, source reputation, and recency
            val confidenceScore = result.confidence ?: 0.5f
            val recencyScore = result.publishedAt?.let {
                val ageHours = (Instant.now().epochSecond - it.epochSecond) / 3600.0
                (1.0 / (1.0 + ageHours / 24.0)).toFloat()  // Decay over days
            } ?: 0.5f

            confidenceScore * 0.7f + recencyScore * 0.3f
        }
    }

    /**
     * Removes duplicate results based on URL similarity.
     */
    private fun deduplicateResults(results: List<SearchResultItem>): List<SearchResultItem> {
        val seen = mutableSetOf<String>()
        return results.filter { result ->
            val normalized = normalizeUrl(result.url)
            if (normalized in seen) {
                false
            } else {
                seen.add(normalized)
                true
            }
        }
    }

    /**
     * Normalizes URL for deduplication.
     */
    private fun normalizeUrl(url: String): String {
        return url.lowercase()
            .replace(Regex("^https?://"), "")
            .replace(Regex("^www\\."), "")
            .replace(Regex("[?#].*$"), "")  // Remove query params
            .trimEnd('/')
    }

    /**
     * Creates an error response.
     */
    private fun createErrorResponse(
        query: SearchQuery,
        error: String,
        latency: Long,
        errors: List<String> = listOf(error)
    ): SearchResponse {
        return SearchResponse(
            queryId = query.id,
            query = query.text,
            intent = query.intent ?: SearchIntent.UNKNOWN,
            results = emptyList(),
            totalResults = 0,
            latencyMs = latency,
            errors = errors
        )
    }

    /**
     * Initializes rate limiters for all providers.
     */
    private fun initializeRateLimiters() {
        // Default rate limits (can be overridden by config)
        val defaultCapacity = 60
        val defaultRefillRate = 1.0  // 1 request/second

        // These will be configured per-provider via config
        // For now, use defaults
    }

    /**
     * Gets cache statistics.
     */
    fun getCacheStatistics() = cacheLayer.getStatistics()

    /**
     * Gets search statistics.
     */
    fun getSearchStatistics(): SearchStatistics {
        return SearchStatistics(
            totalQueries = totalQueries,
            cacheHits = cacheHits,
            cacheHitRate = if (totalQueries > 0) cacheHits.toFloat() / totalQueries else 0f,
            registeredProviders = providers.size
        )
    }

    /**
     * Clears all caches.
     */
    suspend fun clearCache() {
        cacheLayer.clearAll()
    }
}

/**
 * Configuration for WebSearchManager.
 *
 * @property maxProvidersPerQuery Maximum providers to query concurrently
 * @property enableSummarization Whether to generate summaries
 * @property enableFactVerification Whether to perform fact verification
 */
data class WebSearchConfig(
    val maxProvidersPerQuery: Int = 5,
    val enableSummarization: Boolean = true,
    val enableFactVerification: Boolean = true
)

/**
 * Search statistics.
 */
data class SearchStatistics(
    val totalQueries: Int,
    val cacheHits: Int,
    val cacheHitRate: Float,
    val registeredProviders: Int
)
