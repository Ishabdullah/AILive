package com.ailive.memory.managers

import android.content.Context
import android.util.Log
import com.ailive.memory.database.entities.FactCategory
import com.ailive.memory.database.entities.LongTermFactEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Unified Memory Manager
 *
 * Central orchestrator for all memory systems:
 * - Working Memory (Conversations)
 * - Short-term Memory (Last 7 days)
 * - Long-term Memory (Important facts)
 * - User Profile (Personal information)
 *
 * Provides a unified interface for the PersonalityEngine to interact with memory.
 */
class UnifiedMemoryManager(private val context: Context) {
    private val TAG = "UnifiedMemoryManager"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Memory managers
    val conversationMemory = ConversationMemoryManager(context)
    val longTermMemory = LongTermMemoryManager(context)
    val userProfile = UserProfileManager(context)

    // ===== Lifecycle Management =====

    /**
     * Initialize memory system
     */
    suspend fun initialize() {
        Log.i(TAG, "Initializing unified memory system...")

        // Ensure user profile exists
        userProfile.getOrCreateProfile()

        // Run maintenance tasks
        performMaintenance()

        Log.i(TAG, "Memory system initialized")
    }

    /**
     * Perform routine maintenance
     */
    private suspend fun performMaintenance() {
        // Archive old conversations
        conversationMemory.archiveOldConversations()

        // Clean up old facts
        longTermMemory.cleanupOldFacts()

        // Recalculate profile completeness
        userProfile.recalculateCompleteness()
    }

    // ===== Working Memory (Conversations) =====

    /**
     * Record a conversation turn
     */
    suspend fun recordConversationTurn(
        role: String,  // "USER" or "ASSISTANT"
        content: String,
        emotionContext: String? = null,
        locationContext: String? = null,
        responseTime: Long? = null,
        tokenCount: Int? = null
    ) {
        // Add to conversation memory
        conversationMemory.addTurn(
            role = role,
            content = content,
            emotionContext = emotionContext,
            locationContext = locationContext,
            responseTime = responseTime,
            tokenCount = tokenCount
        )

        // Auto-extract facts in background
        if (role == "USER") {
            scope.launch {
                try {
                    val conversationId = conversationMemory.getCurrentConversation()
                    // Get last AI response for context
                    val history = conversationMemory.getConversationHistory(conversationId, limit = 2)
                    val lastAiResponse = history.lastOrNull { it.role == "ASSISTANT" }?.content ?: ""

                    longTermMemory.extractFactsFromConversation(
                        userMessage = content,
                        aiResponse = lastAiResponse,
                        conversationId = conversationId
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error extracting facts", e)
                }
            }
        }
    }

    /**
     * Get recent conversation history for context
     */
    suspend fun getRecentContext(messageCount: Int = 10): String {
        val conversationId = conversationMemory.getCurrentConversation()
        val turns = conversationMemory.getConversationHistory(conversationId, limit = messageCount)

        return turns.joinToString("\n") { turn ->
            "${turn.role}: ${turn.content}"
        }
    }

    // ===== Short-term Memory (Last 7 days) =====

    /**
     * Get conversations from last 7 days
     */
    suspend fun getRecentMemory(): List<String> {
        val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val conversations = conversationMemory.getRecentConversations(limit = 20)

        return conversations.filter { it.lastMessageTime >= cutoffTime }
            .map { "${it.title} - ${it.summary ?: "No summary"}" }
    }

    /**
     * Search recent conversations
     */
    suspend fun searchRecentMemory(query: String): List<String> {
        val messages = conversationMemory.searchMessages(query, limit = 10)
        return messages.map { "${it.role}: ${it.content}" }
    }

    // ===== Long-term Memory (Important Facts) =====

    /**
     * Learn a new fact
     */
    suspend fun learnFact(
        category: FactCategory,
        factText: String,
        importance: Float = 0.5f
    ): LongTermFactEntity {
        val conversationId = conversationMemory.getCurrentConversation()
        return longTermMemory.learnFact(
            category = category,
            factText = factText,
            extractedFrom = conversationId,
            importance = importance
        )
    }

    /**
     * Recall relevant facts for a query
     */
    suspend fun recallFacts(query: String, limit: Int = 5): List<LongTermFactEntity> {
        return longTermMemory.searchFacts(query, limit)
    }

    /**
     * Get facts by category
     */
    suspend fun getFactsByCategory(category: FactCategory): List<LongTermFactEntity> {
        return longTermMemory.getFactsByCategory(category)
    }

    /**
     * Get important facts for context
     */
    suspend fun getImportantFacts(limit: Int = 10): List<LongTermFactEntity> {
        return longTermMemory.getImportantFacts(minImportance = 0.7f, limit = limit)
    }

    // ===== Context Generation for AI =====

    /**
     * Generate complete memory context for AI prompts
     *
     * Includes:
     * - User profile summary
     * - Recent conversation history
     * - Relevant long-term facts
     */
    suspend fun generateContextForPrompt(
        userInput: String,
        includeProfile: Boolean = true,
        includeRecentContext: Boolean = true,
        includeFacts: Boolean = true,
        maxContextLength: Int = 1000
    ): String {
        val contextParts = mutableListOf<String>()

        // User profile
        if (includeProfile) {
            val profileSummary = userProfile.getProfileSummary()
            if (profileSummary.isNotBlank()) {
                contextParts.add("USER PROFILE:\n$profileSummary")
            }
        }

        // Recent conversation context
        if (includeRecentContext) {
            val recentContext = getRecentContext(messageCount = 5)
            if (recentContext.isNotBlank()) {
                contextParts.add("RECENT CONVERSATION:\n$recentContext")
            }
        }

        // Relevant facts
        if (includeFacts) {
            val relevantFacts = recallFacts(userInput, limit = 5)
            if (relevantFacts.isNotEmpty()) {
                val factsText = relevantFacts.joinToString("\n") { "- ${it.factText}" }
                contextParts.add("RELEVANT FACTS:\n$factsText")
            }
        }

        // Join and truncate if needed
        val fullContext = contextParts.joinToString("\n\n")
        return if (fullContext.length > maxContextLength) {
            fullContext.take(maxContextLength) + "..."
        } else {
            fullContext
        }
    }

    // ===== Statistics and Monitoring =====

    /**
     * Get memory system statistics
     */
    suspend fun getStatistics(): MemoryStatistics {
        val conversationCount = conversationMemory.getActiveConversationCount()
        val factStats = longTermMemory.getFactStatistics()
        val profileCompleteness = userProfile.getProfileCompleteness()

        return MemoryStatistics(
            activeConversations = conversationCount,
            totalFacts = factStats.totalFacts,
            averageFactImportance = factStats.averageImportance,
            profileCompleteness = profileCompleteness,
            factsByCategory = factStats.categoryCounts
        )
    }

    /**
     * Perform scheduled maintenance
     */
    fun scheduleMaintenanceIfNeeded() {
        scope.launch {
            try {
                performMaintenance()
                Log.i(TAG, "Scheduled maintenance completed")
            } catch (e: Exception) {
                Log.e(TAG, "Maintenance failed", e)
            }
        }
    }
}

/**
 * Statistics about the memory system
 */
data class MemoryStatistics(
    val activeConversations: Int,
    val totalFacts: Int,
    val averageFactImportance: Float,
    val profileCompleteness: Float,
    val factsByCategory: Map<FactCategory, Int>
)
