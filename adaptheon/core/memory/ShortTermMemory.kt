package com.adaptheon.core.memory

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe short-term memory buffer with automatic cleanup
 * Provides fast access to recent memories with configurable capacity
 */
class ShortTermMemory(
    private val maxCapacity: Int = 1000,
    private val cleanupThreshold: Double = 0.8,
    private val retentionMinutes: Long = 30
) {
    private val memoryBuffer = ConcurrentLinkedQueue<MemoryItem>()
    private val mutex = Mutex()
    private val idCounter = AtomicLong(0)
    
    data class MemoryItem(
        val id: Long,
        val content: String,
        val timestamp: LocalDateTime,
        val priority: Int = 1,
        val tags: Set<String> = emptySet()
    )
    
    /**
     * Add a new memory item to short-term storage
     */
    suspend fun add(content: String, priority: Int = 1, tags: Set<String> = emptySet()): Long {
        val memoryItem = MemoryItem(
            id = idCounter.incrementAndGet(),
            content = content,
            timestamp = LocalDateTime.now(),
            priority = priority,
            tags = tags
        )
        
        memoryBuffer.offer(memoryItem)
        
        // Trigger cleanup if threshold exceeded
        if (memoryBuffer.size > maxCapacity * cleanupThreshold) {
            performCleanup()
        }
        
        return memoryItem.id
    }
    
    /**
     * Retrieve a specific memory item by ID
     */
    suspend fun get(id: Long): MemoryItem? {
        return memoryBuffer.find { it.id == id }
    }
    
    /**
     * Get all memories, sorted by timestamp (newest first)
     */
    suspend fun getAll(): List<MemoryItem> {
        return memoryBuffer.sortedByDescending { it.timestamp }
    }
    
    /**
     * Search memories by content or tags
     */
    suspend fun search(query: String): List<MemoryItem> {
        val lowerQuery = query.lowercase()
        return memoryBuffer.filter { item ->
            item.content.contains(lowerQuery, ignoreCase = true) ||
            item.tags.any { tag -> tag.contains(lowerQuery, ignoreCase = true) }
        }.sortedByDescending { it.priority }
    }
    
    /**
     * Get memories within specified time range
     */
    suspend fun getMemoriesInRange(startTime: LocalDateTime, endTime: LocalDateTime): List<MemoryItem> {
        return memoryBuffer.filter { item ->
            item.timestamp.isAfter(startTime) && item.timestamp.isBefore(endTime)
        }.sortedByDescending { it.timestamp }
    }
    
    /**
     * Remove a specific memory item
     */
    suspend fun remove(id: Long): Boolean {
        return memoryBuffer.removeIf { it.id == id }
    }
    
    /**
     * Clear all short-term memories
     */
    suspend fun clear() {
        memoryBuffer.clear()
    }
    
    /**
     * Get current memory count
     */
    suspend fun getCount(): Int = memoryBuffer.size
    
    /**
     * Automatic cleanup of old memories based on retention policy
     */
    private suspend fun performCleanup() {
        mutex.withLock {
            val cutoffTime = LocalDateTime.now().minusMinutes(retentionMinutes)
            val beforeCount = memoryBuffer.size
            
            // Remove expired memories
            memoryBuffer.removeIf { it.timestamp.isBefore(cutoffTime) }
            
            // If still over capacity, remove lowest priority items
            if (memoryBuffer.size > maxCapacity) {
                val sortedByPriority = memoryBuffer.sortedBy { it.priority }
                val toRemove = memoryBuffer.size - maxCapacity
                repeat(toRemove) { index ->
                    if (index < sortedByPriority.size) {
                        memoryBuffer.remove(sortedByPriority[index])
                    }
                }
            }
            
            val afterCount = memoryBuffer.size
            println("ShortTermMemory cleanup: removed ${beforeCount - afterCount} items")
        }
    }
    
    /**
     * Get memory statistics
     */
    suspend fun getStats(): MemoryStats {
        return MemoryStats(
            totalItems = memoryBuffer.size,
            maxCapacity = maxCapacity,
            oldestMemory = memoryBuffer.minByOrNull { it.timestamp }?.timestamp,
            newestMemory = memoryBuffer.maxByOrNull { it.timestamp }?.timestamp,
            averagePriority = if (memoryBuffer.isNotEmpty()) {
                memoryBuffer.map { it.priority }.average()
            } else 0.0
        )
    }
    
    data class MemoryStats(
        val totalItems: Int,
        val maxCapacity: Int,
        val oldestMemory: LocalDateTime?,
        val newestMemory: LocalDateTime?,
        val averagePriority: Double
    )
}