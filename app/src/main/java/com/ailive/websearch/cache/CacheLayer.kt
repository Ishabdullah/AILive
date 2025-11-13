package com.ailive.websearch.cache

import com.ailive.websearch.core.ProviderResult
import com.ailive.websearch.core.SearchQuery
import com.ailive.websearch.core.SearchResponse
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Multi-layered cache for search results.
 *
 * Provides:
 * - In-memory caching with Caffeine (fast, local)
 * - Configurable TTL per cache type
 * - Cache statistics for monitoring
 * - Thread-safe operations
 *
 * Future enhancements:
 * - Redis integration for distributed caching
 * - Persistent cache to disk for offline access
 *
 * @since v1.4
 */
class CacheLayer(
    /**
     * Maximum number of provider results to cache.
     */
    providerCacheSize: Long = 1000,

    /**
     * TTL for provider results in minutes.
     */
    providerTtlMinutes: Long = 60,

    /**
     * Maximum number of search responses to cache.
     */
    responseCacheSize: Long = 500,

    /**
     * TTL for search responses in minutes.
     */
    responseTtlMinutes: Long = 30
) {
    // Cache for provider-specific results (keyed by "provider:query")
    private val providerCache: Cache<String, ProviderResult> = Caffeine.newBuilder()
        .maximumSize(providerCacheSize)
        .expireAfterWrite(providerTtlMinutes, TimeUnit.MINUTES)
        .recordStats()
        .build()

    // Cache for final search responses (keyed by query ID or normalized query text)
    private val responseCache: Cache<String, SearchResponse> = Caffeine.newBuilder()
        .maximumSize(responseCacheSize)
        .expireAfterWrite(responseTtlMinutes, TimeUnit.MINUTES)
        .recordStats()
        .build()

    private val mutex = Mutex()

    /**
     * Gets a cached provider result.
     *
     * @param providerName The provider name
     * @param query The search query
     * @return Cached result, or null if not found or expired
     */
    suspend fun getProviderResult(providerName: String, query: SearchQuery): ProviderResult? {
        if (query.bypassCache) return null

        val cacheKey = createProviderKey(providerName, query)
        return providerCache.getIfPresent(cacheKey)
    }

    /**
     * Stores a provider result in the cache.
     *
     * @param providerName The provider name
     * @param query The search query
     * @param result The provider result to cache
     */
    suspend fun putProviderResult(providerName: String, query: SearchQuery, result: ProviderResult) {
        val cacheKey = createProviderKey(providerName, query)
        providerCache.put(cacheKey, result)
    }

    /**
     * Gets a cached search response.
     *
     * @param query The search query
     * @return Cached response, or null if not found or expired
     */
    suspend fun getResponse(query: SearchQuery): SearchResponse? {
        if (query.bypassCache) return null

        val cacheKey = createResponseKey(query)
        return responseCache.getIfPresent(cacheKey)
    }

    /**
     * Stores a search response in the cache.
     *
     * @param query The search query
     * @param response The search response to cache
     */
    suspend fun putResponse(query: SearchQuery, response: SearchResponse) {
        val cacheKey = createResponseKey(query)
        responseCache.put(cacheKey, response.copy(cacheHit = false))  // Mark original as non-cache-hit
    }

    /**
     * Invalidates all provider caches for a specific provider.
     *
     * @param providerName The provider to invalidate
     */
    suspend fun invalidateProvider(providerName: String) {
        mutex.withLock {
            // Remove all entries with keys starting with "provider:"
            val keysToRemove = providerCache.asMap().keys.filter {
                it.startsWith("$providerName:")
            }
            providerCache.invalidateAll(keysToRemove)
        }
    }

    /**
     * Invalidates a specific query across all caches.
     *
     * @param query The query to invalidate
     */
    suspend fun invalidateQuery(query: SearchQuery) {
        val responseKey = createResponseKey(query)
        responseCache.invalidate(responseKey)

        // Also invalidate provider results for this query
        mutex.withLock {
            val normalizedQuery = normalizeQueryText(query.text)
            val keysToRemove = providerCache.asMap().keys.filter {
                it.contains(normalizedQuery)
            }
            providerCache.invalidateAll(keysToRemove)
        }
    }

    /**
     * Clears all caches.
     */
    suspend fun clearAll() {
        mutex.withLock {
            providerCache.invalidateAll()
            responseCache.invalidateAll()
        }
    }

    /**
     * Gets cache statistics.
     *
     * @return CacheStatistics with hit rates, sizes, etc.
     */
    fun getStatistics(): CacheStatistics {
        val providerStats = providerCache.stats()
        val responseStats = responseCache.stats()

        return CacheStatistics(
            providerCacheSize = providerCache.estimatedSize(),
            providerHitRate = providerStats.hitRate(),
            providerMissRate = providerStats.missRate(),
            responseCacheSize = responseCache.estimatedSize(),
            responseHitRate = responseStats.hitRate(),
            responseMissRate = responseStats.missRate()
        )
    }

    /**
     * Creates a cache key for provider results.
     * Format: "providerName:normalizedQuery:language"
     */
    private fun createProviderKey(providerName: String, query: SearchQuery): String {
        val normalizedQuery = normalizeQueryText(query.text)
        return "$providerName:$normalizedQuery:${query.language}"
    }

    /**
     * Creates a cache key for search responses.
     * Format: "normalizedQuery:language:intent"
     */
    private fun createResponseKey(query: SearchQuery): String {
        val normalizedQuery = normalizeQueryText(query.text)
        return "$normalizedQuery:${query.language}:${query.intent?.name ?: "AUTO"}"
    }

    /**
     * Normalizes query text for consistent cache keys.
     * - Lowercase
     * - Trim whitespace
     * - Collapse multiple spaces
     * - Remove special characters
     */
    private fun normalizeQueryText(text: String): String {
        return text.lowercase()
            .trim()
            .replace("\\s+".toRegex(), " ")
            .replace("[^a-z0-9 ]".toRegex(), "")
    }
}

/**
 * Cache statistics for monitoring and telemetry.
 *
 * @property providerCacheSize Current number of cached provider results
 * @property providerHitRate Provider cache hit rate (0.0 to 1.0)
 * @property providerMissRate Provider cache miss rate (0.0 to 1.0)
 * @property responseCacheSize Current number of cached responses
 * @property responseHitRate Response cache hit rate (0.0 to 1.0)
 * @property responseMissRate Response cache miss rate (0.0 to 1.0)
 */
data class CacheStatistics(
    val providerCacheSize: Long,
    val providerHitRate: Double,
    val providerMissRate: Double,
    val responseCacheSize: Long,
    val responseHitRate: Double,
    val responseMissRate: Double
) {
    /**
     * Returns overall cache efficiency (average of provider and response hit rates).
     */
    fun getOverallEfficiency(): Double {
        return (providerHitRate + responseHitRate) / 2.0
    }

    /**
     * Returns a formatted summary string.
     */
    fun getSummary(): String {
        return """
            Cache Statistics:
            - Provider Cache: $providerCacheSize items, ${String.format("%.1f%%", providerHitRate * 100)} hit rate
            - Response Cache: $responseCacheSize items, ${String.format("%.1f%%", responseHitRate * 100)} hit rate
            - Overall Efficiency: ${String.format("%.1f%%", getOverallEfficiency() * 100)}
        """.trimIndent()
    }
}
