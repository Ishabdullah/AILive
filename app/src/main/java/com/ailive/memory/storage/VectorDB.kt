package com.ailive.memory.storage

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.sqrt

/**
 * Lightweight in-memory vector database for AILive.
 * Uses brute-force cosine similarity (fast enough for <100K vectors).
 */
class VectorDB(
    private val dimensions: Int = 384,
    private val maxEntries: Int = 50000
) {
    private val TAG = "VectorDB"
    
    private val entries = mutableMapOf<String, MemoryEntry>()
    private val mutex = Mutex()
    
    private var totalSearches = 0L
    private var totalInserts = 0L
    
    /**
     * Insert a memory entry.
     */
    suspend fun insert(entry: MemoryEntry): Boolean = mutex.withLock {
        if (entry.embedding.size != dimensions) {
            Log.e(TAG, "Invalid embedding dimensions: ${entry.embedding.size}, expected: $dimensions")
            return@withLock false
        }
        
        if (entries.size >= maxEntries && !entries.containsKey(entry.id)) {
            evictLRU()
        }
        
        entries[entry.id] = entry
        totalInserts++
        
        if (totalInserts % 100 == 0L) {
            Log.d(TAG, "Inserted $totalInserts memories, current size: ${entries.size}")
        }
        
        return@withLock true
    }
    
    /**
     * Search for k nearest neighbors using cosine similarity.
     */
    suspend fun search(
        queryEmbedding: FloatArray,
        k: Int = 10,
        minSimilarity: Float = 0.0f,
        filter: ((MemoryEntry) -> Boolean)? = null
    ): List<SearchResult> = mutex.withLock {
        if (queryEmbedding.size != dimensions) {
            Log.e(TAG, "Invalid query dimensions: ${queryEmbedding.size}")
            return@withLock emptyList()
        }
        
        totalSearches++
        
        val normalizedQuery = normalize(queryEmbedding)
        
        val results = entries.values
            .asSequence()
            .filter { filter?.invoke(it) ?: true }
            .map { entry ->
                val similarity = cosineSimilarity(normalizedQuery, normalize(entry.embedding))
                SearchResult(entry, similarity)
            }
            .filter { it.similarity >= minSimilarity }
            .sortedDescending()
            .take(k)
            .toList()
        
        results.forEach { result ->
            entries[result.entry.id] = result.entry.withAccessUpdate()
        }
        
        Log.d(TAG, "Search completed: found ${results.size} results (min similarity: $minSimilarity)")
        return@withLock results
    }
    
    /**
     * Get entry by ID.
     */
    suspend fun get(id: String): MemoryEntry? = mutex.withLock {
        val entry = entries[id]
        if (entry != null) {
            entries[id] = entry.withAccessUpdate()
        }
        return@withLock entry
    }
    
    /**
     * Delete entry by ID.
     */
    suspend fun delete(id: String): Boolean = mutex.withLock {
        val removed = entries.remove(id) != null
        if (removed) {
            Log.d(TAG, "Deleted memory: $id")
        }
        return@withLock removed
    }
    
    /**
     * Get all entries matching a filter.
     */
    suspend fun filter(predicate: (MemoryEntry) -> Boolean): List<MemoryEntry> = mutex.withLock {
        return@withLock entries.values.filter(predicate)
    }
    
    /**
     * Clear all entries.
     */
    suspend fun clear() = mutex.withLock {
        val size = entries.size
        entries.clear()
        Log.w(TAG, "Cleared $size memories")
    }
    
    /**
     * Get current size.
     */
    suspend fun size(): Int = mutex.withLock {
        return@withLock entries.size
    }
    
    /**
     * Evict least recently used entry.
     */
    private fun evictLRU() {
        val lruEntry = entries.values.minByOrNull { it.lastAccessed }
        if (lruEntry != null) {
            entries.remove(lruEntry.id)
            Log.d(TAG, "Evicted LRU memory: ${lruEntry.id}")
        }
    }
    
    /**
     * Cosine similarity between two vectors.
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dotProduct = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
        }
        return dotProduct
    }
    
    /**
     * Normalize vector to unit length.
     */
    private fun normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0f
        for (value in vector) {
            sumSquares += value * value
        }
        val norm = sqrt(sumSquares)
        
        return if (norm > 0f) {
            FloatArray(vector.size) { i -> vector[i] / norm }
        } else {
            vector
        }
    }
    
    /**
     * Get database statistics.
     */
    suspend fun getStats(): VectorDBStats = mutex.withLock {
        val avgAccessCount = if (entries.isNotEmpty()) {
            entries.values.map { it.accessCount }.average()
        } else 0.0
        
        return@withLock VectorDBStats(
            totalEntries = entries.size,
            maxCapacity = maxEntries,
            dimensions = dimensions,
            totalSearches = totalSearches,
            totalInserts = totalInserts,
            avgAccessCount = avgAccessCount,
            memoryUsageMB = estimateMemoryMB()
        )
    }
    
    /**
     * Estimate memory usage in MB.
     */
    private fun estimateMemoryMB(): Float {
        val vectorSizeBytes = dimensions * 4
        val entryOverhead = 200
        val totalBytes = entries.size * (vectorSizeBytes + entryOverhead)
        return totalBytes / (1024f * 1024f)
    }
}

data class VectorDBStats(
    val totalEntries: Int,
    val maxCapacity: Int,
    val dimensions: Int,
    val totalSearches: Long,
    val totalInserts: Long,
    val avgAccessCount: Double,
    val memoryUsageMB: Float
)
