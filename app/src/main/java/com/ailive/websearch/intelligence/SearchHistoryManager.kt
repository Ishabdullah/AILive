package com.ailive.websearch.intelligence

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.ailive.websearch.core.SearchResponse
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/**
 * Manages search history to avoid redundant queries.
 *
 * Features:
 * - Tracks recent searches with timestamps
 * - Detects similar queries within time window
 * - Provides search frequency analytics
 * - Persists history across app restarts
 *
 * @param context Android context for SharedPreferences
 * @param maxHistorySize Maximum number of history entries to keep
 * @since v1.4
 */
class SearchHistoryManager(
    context: Context,
    private val maxHistorySize: Int = 100
) {
    private val TAG = "SearchHistory"

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "websearch_history",
        Context.MODE_PRIVATE
    )

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val historyAdapter = moshi.adapter<List<SearchHistoryEntry>>(
        Types.newParameterizedType(List::class.java, SearchHistoryEntry::class.java)
    )

    private val mutex = Mutex()
    private val history = mutableListOf<SearchHistoryEntry>()

    init {
        loadHistory()
    }

    /**
     * Records a search in history.
     *
     * @param query The search query
     * @param response The search response
     */
    suspend fun recordSearch(query: String, response: SearchResponse) {
        mutex.withLock {
            val entry = SearchHistoryEntry(
                query = query,
                normalizedQuery = normalizeQuery(query),
                timestamp = Instant.now(),
                resultCount = response.results.size,
                sources = response.providerResults.map { it.providerName },
                cacheHit = response.cacheHit,
                latencyMs = response.latencyMs
            )

            history.add(0, entry)  // Add to beginning

            // Trim to max size
            if (history.size > maxHistorySize) {
                history.removeAt(history.size - 1)
            }

            saveHistory()
            Log.d(TAG, "Recorded search: $query (${response.results.size} results)")
        }
    }

    /**
     * Checks if a similar query was recently executed.
     *
     * @param query The query to check
     * @param timeWindow Maximum age of previous search (default: 3 hours)
     * @return RecentSearchMatch if found, null otherwise
     */
    suspend fun findRecentSimilar(
        query: String,
        timeWindow: Duration = Duration.ofHours(3)
    ): RecentSearchMatch? {
        return mutex.withLock {
            val normalizedQuery = normalizeQuery(query)
            val cutoffTime = Instant.now().minus(timeWindow)

            // Find exact match first
            history.firstOrNull { entry ->
                entry.normalizedQuery == normalizedQuery &&
                entry.timestamp.isAfter(cutoffTime)
            }?.let { entry ->
                return@withLock RecentSearchMatch(
                    entry = entry,
                    similarity = 1.0f,
                    ageMinutes = Duration.between(entry.timestamp, Instant.now()).toMinutes()
                )
            }

            // Find similar matches
            history.firstOrNull { entry ->
                entry.timestamp.isAfter(cutoffTime) &&
                calculateSimilarity(normalizedQuery, entry.normalizedQuery) > 0.7f
            }?.let { entry ->
                val similarity = calculateSimilarity(normalizedQuery, entry.normalizedQuery)
                return@withLock RecentSearchMatch(
                    entry = entry,
                    similarity = similarity,
                    ageMinutes = Duration.between(entry.timestamp, Instant.now()).toMinutes()
                )
            }

            null
        }
    }

    /**
     * Gets search frequency for a topic.
     *
     * @param topic The topic to analyze
     * @param timeWindow Time window to analyze (default: 7 days)
     * @return Number of searches for this topic
     */
    suspend fun getTopicFrequency(
        topic: String,
        timeWindow: Duration = Duration.ofDays(7)
    ): Int {
        return mutex.withLock {
            val cutoffTime = Instant.now().minus(timeWindow)
            val normalizedTopic = normalizeQuery(topic)

            history.count { entry ->
                entry.timestamp.isAfter(cutoffTime) &&
                entry.normalizedQuery.contains(normalizedTopic)
            }
        }
    }

    /**
     * Gets all history entries within time window.
     *
     * @param timeWindow Time window (default: 24 hours)
     * @return List of history entries
     */
    suspend fun getRecentHistory(
        timeWindow: Duration = Duration.ofHours(24)
    ): List<SearchHistoryEntry> {
        return mutex.withLock {
            val cutoffTime = Instant.now().minus(timeWindow)
            history.filter { it.timestamp.isAfter(cutoffTime) }
        }
    }

    /**
     * Gets search statistics.
     *
     * @return SearchStatistics with analytics
     */
    suspend fun getStatistics(): SearchStatistics {
        return mutex.withLock {
            val now = Instant.now()
            val last24h = history.count {
                Duration.between(it.timestamp, now).toHours() < 24
            }
            val last7d = history.count {
                Duration.between(it.timestamp, now).toDays() < 7
            }

            val avgResultCount = if (history.isNotEmpty()) {
                history.map { it.resultCount }.average().toInt()
            } else 0

            val cacheHitRate = if (history.isNotEmpty()) {
                history.count { it.cacheHit }.toFloat() / history.size
            } else 0f

            val topSources = history
                .flatMap { it.sources }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(5)
                .associate { it.key to it.value }

            SearchStatistics(
                totalSearches = history.size,
                searchesLast24h = last24h,
                searchesLast7d = last7d,
                averageResultCount = avgResultCount,
                cacheHitRate = cacheHitRate,
                topSources = topSources
            )
        }
    }

    /**
     * Clears old entries beyond retention period.
     *
     * @param retentionDays Days to retain history (default: 30)
     */
    suspend fun cleanup(retentionDays: Int = 30) {
        mutex.withLock {
            val cutoffTime = Instant.now().minus(Duration.ofDays(retentionDays.toLong()))
            val beforeSize = history.size

            history.removeAll { it.timestamp.isBefore(cutoffTime) }

            if (history.size < beforeSize) {
                saveHistory()
                Log.d(TAG, "Cleaned up ${beforeSize - history.size} old entries")
            }
        }
    }

    /**
     * Clears all history.
     */
    suspend fun clearAll() {
        mutex.withLock {
            history.clear()
            saveHistory()
            Log.d(TAG, "Cleared all search history")
        }
    }

    /**
     * Normalizes query for comparison.
     */
    private fun normalizeQuery(query: String): String {
        return query.lowercase()
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[^a-z0-9 ]"), "")
    }

    /**
     * Calculates similarity between two normalized queries.
     * Uses Jaccard similarity of word sets.
     */
    private fun calculateSimilarity(query1: String, query2: String): Float {
        val words1 = query1.split(" ").toSet()
        val words2 = query2.split(" ").toSet()

        if (words1.isEmpty() && words2.isEmpty()) return 1.0f
        if (words1.isEmpty() || words2.isEmpty()) return 0.0f

        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size

        return intersection.toFloat() / union.toFloat()
    }

    /**
     * Loads history from SharedPreferences.
     */
    private fun loadHistory() {
        try {
            val json = prefs.getString(HISTORY_KEY, null) ?: return
            val loaded = historyAdapter.fromJson(json) ?: return

            history.clear()
            history.addAll(loaded)

            Log.d(TAG, "Loaded ${history.size} search history entries")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load search history", e)
        }
    }

    /**
     * Saves history to SharedPreferences.
     */
    private fun saveHistory() {
        try {
            val json = historyAdapter.toJson(history)
            prefs.edit()
                .putString(HISTORY_KEY, json)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save search history", e)
        }
    }

    companion object {
        private const val HISTORY_KEY = "search_history"
    }
}

/**
 * Entry in search history.
 *
 * @property query Original query text
 * @property normalizedQuery Normalized query for comparison
 * @property timestamp When the search was executed
 * @property resultCount Number of results returned
 * @property sources List of providers that returned results
 * @property cacheHit Whether this was a cache hit
 * @property latencyMs Search latency in milliseconds
 */
@JsonClass(generateAdapter = true)
data class SearchHistoryEntry(
    val query: String,
    val normalizedQuery: String,
    val timestamp: Instant,
    val resultCount: Int,
    val sources: List<String>,
    val cacheHit: Boolean,
    val latencyMs: Long
)

/**
 * Match for a recent similar search.
 *
 * @property entry The matching history entry
 * @property similarity Similarity score (0.0-1.0)
 * @property ageMinutes Age of the entry in minutes
 */
data class RecentSearchMatch(
    val entry: SearchHistoryEntry,
    val similarity: Float,
    val ageMinutes: Long
) {
    /**
     * Checks if this match is fresh enough to reuse.
     *
     * @param maxAgeMinutes Maximum acceptable age
     * @return true if within acceptable age
     */
    fun isFresh(maxAgeMinutes: Long = 180): Boolean {
        return ageMinutes <= maxAgeMinutes
    }

    /**
     * Checks if this is an exact match.
     */
    fun isExact(): Boolean = similarity >= 1.0f
}

/**
 * Search statistics and analytics.
 *
 * @property totalSearches Total number of searches in history
 * @property searchesLast24h Searches in last 24 hours
 * @property searchesLast7d Searches in last 7 days
 * @property averageResultCount Average number of results per search
 * @property cacheHitRate Cache hit rate (0.0-1.0)
 * @property topSources Top 5 most used sources
 */
data class SearchStatistics(
    val totalSearches: Int,
    val searchesLast24h: Int,
    val searchesLast7d: Int,
    val averageResultCount: Int,
    val cacheHitRate: Float,
    val topSources: Map<String, Int>
) {
    fun getFormattedSummary(): String {
        return """
            Search Statistics:
            - Total Searches: $totalSearches
            - Last 24h: $searchesLast24h
            - Last 7d: $searchesLast7d
            - Avg Results: $averageResultCount
            - Cache Hit Rate: ${String.format("%.1f%%", cacheHitRate * 100)}
            - Top Sources: ${topSources.entries.take(3).joinToString { "${it.key} (${it.value})" }}
        """.trimIndent()
    }
}
