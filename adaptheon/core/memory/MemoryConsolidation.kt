package com.adaptheon.core.memory

import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Memory consolidation system that transfers memories between tiers
 * Implements forgetting curve and importance-based promotion
 */
class MemoryConsolidation(
    private val shortTermMemory: ShortTermMemory,
    private val longTermMemory: LongTermMemory,
    private val consolidationIntervalMs: Long = 60000 // 1 minute
) {
    private val isRunning = AtomicBoolean(false)
    private var consolidationJob: Job? = null
    
    /**
     * Start automatic memory consolidation
     */
    fun startConsolidation() {
        if (isRunning.compareAndSet(false, true)) {
            consolidationJob = CoroutineScope(Dispatchers.IO).launch {
                while (isRunning.get()) {
                    try {
                        performConsolidation()
                        delay(consolidationIntervalMs)
                    } catch (e: Exception) {
                        println("Memory consolidation error: ${e.message}")
                    }
                }
            }
            println("Memory consolidation started")
        }
    }
    
    /**
     * Stop automatic memory consolidation
     */
    fun stopConsolidation() {
        isRunning.set(false)
        consolidationJob?.cancel()
        consolidationJob = null
        println("Memory consolidation stopped")
    }
    
    /**
     * Manual consolidation trigger
     */
    suspend fun performConsolidation() {
        println("Starting memory consolidation cycle...")
        
        val memoriesToConsolidate = selectMemoriesForConsolidation()
        var consolidatedCount = 0
        
        memoriesToConsolidate.forEach { memory ->
            if (shouldConsolidateToLongTerm(memory)) {
                consolidateMemory(memory)
                consolidatedCount++
            }
        }
        
        println("Consolidation completed: $consolidatedCount memories moved to long-term storage")
    }
    
    /**
     * Select memories eligible for consolidation
     */
    private suspend fun selectMemoriesForConsolidation(): List<ShortTermMemory.MemoryItem> {
        val cutoffTime = LocalDateTime.now().minusMinutes(15) // Memories older than 15 minutes
        return shortTermMemory.getMemoriesInRange(
            startTime = LocalDateTime.MIN,
            endTime = cutoffTime
        )
    }
    
    /**
     * Determine if memory should be consolidated to long-term storage
     */
    private suspend fun shouldConsolidateToLongTerm(memory: ShortTermMemory.MemoryItem): Boolean {
        // Factors affecting consolidation decision:
        
        // 1. Priority threshold
        if (memory.priority >= 3) return true
        
        // 2. Content length (substantive memories)
        if (memory.content.length > 100) return true
        
        // 3. Contains important keywords
        val importantKeywords = setOf("important", "remember", "learn", "critical", "urgent")
        val hasImportantKeywords = importantKeywords.any { keyword ->
            memory.content.contains(keyword, ignoreCase = true)
        }
        if (hasImportantKeywords) return true
        
        // 4. Contains questions (indicating learning moments)
        if (memory.content.contains("?") && memory.content.length > 20) return true
        
        // 5. Has specific tags that indicate importance
        val importantTags = setOf("learning", "preference", "skill", "relationship")
        if (memory.tags.intersect(importantTags).isNotEmpty()) return true
        
        return false
    }
    
    /**
     * Consolidate individual memory to long-term storage
     */
    private suspend fun consolidateMemory(memory: ShortTermMemory.MemoryItem) {
        val knowledgeType = determineKnowledgeType(memory)
        val confidence = calculateConfidence(memory)
        val importance = calculateImportance(memory)
        
        longTermMemory.store(
            content = memory.content,
            type = knowledgeType,
            confidence = confidence,
            tags = memory.tags,
            source = "short_term_consolidation",
            importance = importance
        )
        
        // Remove from short-term memory
        shortTermMemory.remove(memory.id)
    }
    
    /**
     * Determine knowledge type based on content analysis
     */
    private fun determineKnowledgeType(memory: ShortTermMemory.MemoryItem): LongTermMemory.KnowledgeType {
        val content = memory.content.lowercase()
        
        return when {
            content.contains("fact") || content.contains("information") -> 
                LongTermMemory.KnowledgeType.FACT
            content.contains("learn") || content.contains("skill") -> 
                LongTermMemory.KnowledgeType.SKILL
            content.contains("prefer") || content.contains("like") -> 
                LongTermMemory.KnowledgeType.PREFERENCE
            content.contains("experience") || content.contains("happened") -> 
                LongTermMemory.KnowledgeType.EXPERIENCE
            content.contains("person") || content.contains("friend") -> 
                LongTermMemory.KnowledgeType.RELATIONSHIP
            content.contains("event") || content.contains("meeting") -> 
                LongTermMemory.KnowledgeType.EVENT
            else -> LongTermMemory.KnowledgeType.CONCEPT
        }
    }
    
    /**
     * Calculate confidence based on memory attributes
     */
    private fun calculateConfidence(memory: ShortTermMemory.MemoryItem): Double {
        var confidence = 0.5 // Base confidence
        
        // Higher priority = higher confidence
        confidence += memory.priority * 0.1
        
        // Longer content = higher confidence (more context)
        confidence += (memory.content.length / 1000.0) * 0.2
        
        // Tags indicate structured information = higher confidence
        confidence += memory.tags.size * 0.05
        
        return confidence.coerceAtMost(1.0)
    }
    
    /**
     * Calculate importance based on multiple factors
     */
    private fun calculateImportance(memory: ShortTermMemory.MemoryItem): Double {
        var importance = 1.0 // Base importance
        
        // Priority factor
        importance *= (1.0 + memory.priority * 0.2)
        
        // Recency factor (newer might be more important)
        val hoursOld = java.time.Duration.between(
            memory.timestamp, LocalDateTime.now()
        ).toHours()
        val recencyFactor = if (hoursOld < 1) 1.5 else 1.0
        importance *= recencyFactor
        
        // Content length factor
        val lengthFactor = if (memory.content.length > 200) 1.3 else 1.0
        importance *= lengthFactor
        
        return importance.coerceAtMost(3.0) // Cap at 3.0
    }
    
    /**
     * Get consolidation statistics
     */
    suspend fun getConsolidationStats(): ConsolidationStats {
        val shortTermStats = shortTermMemory.getStats()
        val longTermStats = longTermMemory.getStats()
        
        return ConsolidationStats(
            shortTermCount = shortTermStats.totalItems,
            longTermCount = longTermStats.totalItems,
            consolidationActive = isRunning.get(),
            consolidationInterval = consolidationIntervalMs
        )
    }
    
    /**
     * Force consolidation of all memories older than specified minutes
     */
    suspend fun forceConsolidation(olderThanMinutes: Int): Int {
        val cutoffTime = LocalDateTime.now().minusMinutes(olderThanMinutes.toLong())
        val memoriesToConsolidate = shortTermMemory.getMemoriesInRange(
            startTime = LocalDateTime.MIN,
            endTime = cutoffTime
        )
        
        var consolidatedCount = 0
        memoriesToConsolidate.forEach { memory ->
            consolidateMemory(memory)
            consolidatedCount++
        }
        
        return consolidatedCount
    }
    
    data class ConsolidationStats(
        val shortTermCount: Int,
        val longTermCount: Int,
        val consolidationActive: Boolean,
        val consolidationInterval: Long
    )
}