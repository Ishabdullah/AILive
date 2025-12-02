package com.adaptheon.core.memory

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Persistent long-term memory storage with semantic search capabilities
 * Provides durable storage for important memories and knowledge
 */
class LongTermMemory {
    private val persistentStorage = ConcurrentHashMap<Long, PersistentMemoryItem>()
    private val semanticIndex = SemanticIndex()
    private val idCounter = AtomicLong(0)
    
    data class PersistentMemoryItem(
        val id: Long,
        val content: String,
        val summary: String,
        val timestamp: LocalDateTime,
        val lastAccessed: LocalDateTime,
        val accessCount: Long,
        val importance: Double,
        val categories: Set<String>,
        val tags: Set<String>,
        val embedding: FloatArray? = null
    )
    
    data class MemoryQuery(
        val query: String,
        val categories: Set<String> = emptySet(),
        val tags: Set<String> = emptySet(),
        val timeRange: TimeRange? = null,
        val minImportance: Double = 0.0,
        val limit: Int = 50
    )
    
    data class TimeRange(
        val startTime: LocalDateTime,
        val endTime: LocalDateTime
    )
    
    /**
     * Store a memory item in long-term storage
     */
    suspend fun store(
        content: String,
        summary: String = "",
        importance: Double = 1.0,
        categories: Set<String> = emptySet(),
        tags: Set<String> = emptySet(),
        generateEmbedding: Boolean = true
    ): Long {
        val memoryItem = PersistentMemoryItem(
            id = idCounter.incrementAndGet(),
            content = content,
            summary = if (summary.isEmpty()) content.take(200) + "..." else summary,
            timestamp = LocalDateTime.now(),
            lastAccessed = LocalDateTime.now(),
            accessCount = 0L,
            importance = importance,
            categories = categories,
            tags = tags,
            embedding = if (generateEmbedding) generateEmbedding(content) else null
        )
        
        persistentStorage[memoryItem.id] = memoryItem
        
        // Update semantic index
        memoryItem.embedding?.let { embedding ->
            semanticIndex.addToIndex(memoryItem.id, embedding)
        }
        
        return memoryItem.id
    }
    
    /**
     * Retrieve a specific memory item by ID
     */
    suspend fun retrieve(id: Long): PersistentMemoryItem? {
        val item = persistentStorage[id]
        item?.let {
            // Update access statistics
            val updatedItem = it.copy(
                lastAccessed = LocalDateTime.now(),
                accessCount = it.accessCount + 1
            )
            persistentStorage[id] = updatedItem
        }
        return item
    }
    
    /**
     * Search memories using various criteria
     */
    suspend fun search(query: MemoryQuery): Flow<PersistentMemoryItem> = flow {
        val candidates = persistentStorage.values.filter { item ->
            // Filter by importance
            if (item.importance < query.minImportance) return@filter false
            
            // Filter by categories
            if (query.categories.isNotEmpty() && item.categories.intersect(query.categories).isEmpty()) {
                return@filter false
            }
            
            // Filter by tags
            if (query.tags.isNotEmpty() && item.tags.intersect(query.tags).isEmpty()) {
                return@filter false
            }
            
            // Filter by time range
            query.timeRange?.let { range ->
                if (item.timestamp.isBefore(range.startTime) || item.timestamp.isAfter(range.endTime)) {
                    return@filter false
                }
            }
            
            true
        }
        
        // Rank by relevance to query
        val ranked = candidates.sortedWith(compareByDescending<PersistentMemoryItem> { item ->
            // Text relevance score
            val textScore = calculateTextRelevance(query.query, item.content, item.summary)
            
            // Semantic similarity score (if embeddings available)
            val semanticScore = if (item.embedding != null) {
                semanticIndex.findSimilar(query.query).maxOfOrNull { similarity ->
                    if (similarity.memoryId == item.id) similarity.score else 0.0
                } ?: 0.0
            } else 0.0
            
            // Access frequency score
            val accessScore = (item.accessCount.toDouble() / 100.0).coerceAtMost(1.0)
            
            // Importance score
            val importanceScore = item.importance
            
            // Combined score
            (textScore * 0.3) + (semanticScore * 0.4) + (accessScore * 0.1) + (importanceScore * 0.2)
        })
        
        // Emit limited results
        ranked.take(query.limit).forEach { emit(it) }
    }
    
    /**
     * Get memories by category
     */
    suspend fun getByCategory(category: String): List<PersistentMemoryItem> {
        return persistentStorage.values.filter { category in it.categories }
            .sortedByDescending { it.importance }
    }
    
    /**
     * Get memories by tag
     */
    suspend fun getByTag(tag: String): List<PersistentMemoryItem> {
        return persistentStorage.values.filter { tag in it.tags }
            .sortedByDescending { it.importance }
    }
    
    /**
     * Get recently accessed memories
     */
    suspend fun getRecentlyAccessed(limit: Int = 20): List<PersistentMemoryItem> {
        return persistentStorage.values.sortedByDescending { it.lastAccessed }
            .take(limit)
    }
    
    /**
     * Update memory item
     */
    suspend fun update(
        id: Long,
        content: String? = null,
        summary: String? = null,
        importance: Double? = null,
        categories: Set<String>? = null,
        tags: Set<String>? = null
    ): Boolean {
        val existing = persistentStorage[id] ?: return false
        
        val updated = existing.copy(
            content = content ?: existing.content,
            summary = summary ?: existing.summary,
            importance = importance ?: existing.importance,
            categories = categories ?: existing.categories,
            tags = tags ?: existing.tags,
            embedding = if (content != null) generateEmbedding(content) else existing.embedding
        )
        
        persistentStorage[id] = updated
        
        // Update semantic index if content changed
        if (content != null) {
            updated.embedding?.let { embedding ->
                semanticIndex.addToIndex(id, embedding)
            }
        }
        
        return true
    }
    
    /**
     * Delete a memory item
     */
    suspend fun delete(id: Long): Boolean {
        val removed = persistentStorage.remove(id) != null
        if (removed) {
            semanticIndex.removeFromIndex(id)
        }
        return removed
    }
    
    /**
     * Get memory statistics
     */
    suspend fun getStats(): LongTermMemoryStats {
        val memories = persistentStorage.values.toList()
        return LongTermMemoryStats(
            totalMemories = memories.size,
            averageImportance = if (memories.isNotEmpty()) {
                memories.map { it.importance }.average()
            } else 0.0,
            totalAccessCount = memories.sumOf { it.accessCount },
            categoryDistribution = memories.flatMap { it.categories }.groupingBy { it }
                .eachCount().mapValues { it.value.toDouble() },
            tagDistribution = memories.flatMap { it.tags }.groupingBy { it }
                .eachCount().mapValues { it.value.toDouble() }
        )
    }
    
    /**
     * Perform memory consolidation - merge similar memories
     */
    suspend fun consolidateMemories(): Int {
        val memories = persistentStorage.values.toList()
        var mergedCount = 0
        
        // Group by similarity and merge
        val similarityThreshold = 0.8
        val processed = mutableSetOf<Long>()
        
        memories.forEach { memory ->
            if (memory.id in processed) return@forEach
            
            val similar = memories.filter { other ->
                other.id != memory.id && 
                other.id !in processed &&
                calculateSimilarity(memory.content, other.content) > similarityThreshold
            }
            
            if (similar.isNotEmpty()) {
                // Merge memories
                val mergedContent = listOf(memory, *similar.toTypedArray())
                    .joinToString("\n---\n") { it.content }
                
                val mergedCategories = (memory.categories + similar.flatMap { it.categories }).toSet()
                val mergedTags = (memory.tags + similar.flatMap { it.tags }).toSet()
                val maxImportance = maxOf(memory.importance, similar.maxOf { it.importance })
                
                // Create consolidated memory
                store(
                    content = mergedContent,
                    summary = "Consolidated from ${similar.size + 1} similar memories",
                    importance = maxImportance,
                    categories = mergedCategories,
                    tags = mergedTags
                )
                
                // Remove original memories
                (listOf(memory.id) + similar.map { it.id }).forEach { id ->
                    delete(id)
                    processed.add(id)
                }
                
                mergedCount += similar.size + 1
            }
            
            processed.add(memory.id)
        }
        
        return mergedCount
    }
    
    private fun generateEmbedding(text: String): FloatArray {
        // Placeholder for actual embedding generation
        // In real implementation, this would call embedding service
        return FloatArray(128) { kotlin.random.Random.nextFloat() }
    }
    
    private fun calculateTextRelevance(query: String, content: String, summary: String): Double {
        val queryWords = query.lowercase().split("\\s+".toRegex())
        val contentWords = (content + " " + summary).lowercase().split("\\s+".toRegex())
        
        val matches = queryWords.count { word ->
            contentWords.any { it.contains(word) }
        }
        
        return matches.toDouble() / queryWords.size.toDouble()
    }
    
    private fun calculateSimilarity(text1: String, text2: String): Double {
        // Simple text similarity calculation
        val words1 = text1.lowercase().split("\\s+".toRegex()).toSet()
        val words2 = text2.lowercase().split("\\s+".toRegex()).toSet()
        
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        
        return if (union > 0) intersection.toDouble() / union.toDouble() else 0.0
    }
    
    data class LongTermMemoryStats(
        val totalMemories: Int,
        val averageImportance: Double,
        val totalAccessCount: Long,
        val categoryDistribution: Map<String, Double>,
        val tagDistribution: Map<String, Double>
    )
    
    private class SemanticIndex {
        private val index = ConcurrentHashMap<Long, FloatArray>()
        
        fun addToIndex(id: Long, embedding: FloatArray) {
            index[id] = embedding
        }
        
        fun removeFromIndex(id: Long) {
            index.remove(id)
        }
        
        fun findSimilar(query: String): List<SimilarityResult> {
            val queryEmbedding = generateEmbedding(query)
            
            return index.map { (id, embedding) ->
                SimilarityResult(id, calculateCosineSimilarity(queryEmbedding, embedding))
            }.sortedByDescending { it.score }
        }
        
        private fun calculateCosineSimilarity(vec1: FloatArray, vec2: FloatArray): Double {
            if (vec1.size != vec2.size) return 0.0
            
            var dotProduct = 0.0
            var norm1 = 0.0
            var norm2 = 0.0
            
            for (i in vec1.indices) {
                dotProduct += vec1[i] * vec2[i]
                norm1 += vec1[i] * vec1[i]
                norm2 += vec2[i] * vec2[i]
            }
            
            return if (norm1 > 0 && norm2 > 0) {
                dotProduct / (kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2))
            } else 0.0
        }
        
        data class SimilarityResult(val memoryId: Long, val score: Double)
    }
}