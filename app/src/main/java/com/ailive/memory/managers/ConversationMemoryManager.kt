package com.ailive.memory.managers

import android.content.Context
import android.util.Log
import com.ailive.memory.database.MemoryDatabase
import com.ailive.memory.database.entities.ConversationEntity
import com.ailive.memory.database.entities.ConversationTurnEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Conversation Memory Manager - Working Memory
 *
 * Manages active conversations and recent message history.
 * Automatically archives old conversations and cleans up inactive ones.
 */
class ConversationMemoryManager(context: Context) {
    private val TAG = "ConversationMemoryManager"

    private val database = MemoryDatabase.getInstance(context)
    private val conversationDao = database.conversationDao()

    // ===== Active Conversation Management =====

    private var currentConversationId: String? = null

    /**
     * Start a new conversation
     */
    suspend fun startNewConversation(title: String? = null): String {
        val conversationId = UUID.randomUUID().toString()
        val conversation = ConversationEntity(
            id = conversationId,
            title = title ?: "New Conversation",
            startTime = System.currentTimeMillis(),
            lastMessageTime = System.currentTimeMillis(),
            messageCount = 0,
            isActive = true
        )

        conversationDao.insertConversation(conversation)
        currentConversationId = conversationId

        Log.i(TAG, "Started new conversation: $conversationId")
        return conversationId
    }

    /**
     * Get or create current conversation
     */
    suspend fun getCurrentConversation(): String {
        // If we have an active conversation, return it
        currentConversationId?.let { id ->
            conversationDao.getConversation(id)?.let {
                if (it.isActive) return id
            }
        }

        // Otherwise, start a new one
        return startNewConversation()
    }

    /**
     * Add a message turn to current conversation
     */
    suspend fun addTurn(
        role: String,  // "USER" or "ASSISTANT"
        content: String,
        emotionContext: String? = null,
        locationContext: String? = null,
        responseTime: Long? = null,
        tokenCount: Int? = null
    ): ConversationTurnEntity {
        val conversationId = getCurrentConversation()

        val turn = ConversationTurnEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = role,
            content = content,
            timestamp = System.currentTimeMillis(),
            emotionContext = emotionContext,
            locationContext = locationContext,
            responseTime = responseTime,
            tokenCount = tokenCount
        )

        conversationDao.insertTurn(turn)

        // Update conversation metadata
        val conversation = conversationDao.getConversation(conversationId)
        conversation?.let { conv ->
            val updatedConv = conv.copy(
                lastMessageTime = turn.timestamp,
                messageCount = conv.messageCount + 1,
                title = if (conv.messageCount == 0 && role == "USER") {
                    content.take(50) + if (content.length > 50) "..." else ""
                } else conv.title
            )
            conversationDao.updateConversation(updatedConv)
        }

        Log.d(TAG, "Added turn to conversation $conversationId: $role - ${content.take(50)}...")
        return turn
    }

    /**
     * Get conversation history
     */
    suspend fun getConversationHistory(conversationId: String, limit: Int? = null): List<ConversationTurnEntity> {
        return if (limit != null) {
            conversationDao.getRecentTurns(conversationId, limit)
        } else {
            conversationDao.getTurnsForConversation(conversationId)
        }
    }

    /**
     * Get recent conversations
     */
    suspend fun getRecentConversations(limit: Int = 10): List<ConversationEntity> {
        return conversationDao.getRecentConversations(limit)
    }

    /**
     * Get active conversations as Flow (for UI)
     */
    fun getActiveConversationsFlow(): Flow<List<ConversationEntity>> {
        return conversationDao.getActiveConversationsFlow()
    }

    /**
     * Bookmark a conversation
     */
    suspend fun bookmarkConversation(conversationId: String, bookmarked: Boolean = true) {
        conversationDao.getConversation(conversationId)?.let { conversation ->
            conversationDao.updateConversation(conversation.copy(isBookmarked = bookmarked))
            Log.i(TAG, "${if (bookmarked) "Bookmarked" else "Unbookmarked"} conversation: $conversationId")
        }
    }

    /**
     * Archive old conversations
     * Conversations older than 30 days are marked inactive
     */
    suspend fun archiveOldConversations() {
        val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(
            ConversationEntity.ACTIVE_DURATION_DAYS.toLong()
        )
        conversationDao.archiveOldConversations(cutoffTime)
        Log.i(TAG, "Archived conversations older than ${ConversationEntity.ACTIVE_DURATION_DAYS} days")
    }

    /**
     * Delete old archived conversations
     * Removes non-bookmarked conversations older than 90 days
     */
    suspend fun deleteOldConversations() {
        val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90)
        conversationDao.deleteOldConversations(cutoffTime)
        Log.i(TAG, "Deleted non-bookmarked conversations older than 90 days")
    }

    /**
     * Search conversations
     */
    suspend fun searchConversations(query: String, limit: Int = 20): List<ConversationEntity> {
        return conversationDao.searchConversations(query, limit)
    }

    /**
     * Search message content
     */
    suspend fun searchMessages(query: String, limit: Int = 20): List<ConversationTurnEntity> {
        return conversationDao.searchTurns(query, limit)
    }

    /**
     * Get conversation count
     */
    suspend fun getActiveConversationCount(): Int {
        return conversationDao.getActiveConversationCount()
    }

    /**
     * Resume a previous conversation
     */
    suspend fun resumeConversation(conversationId: String): Boolean {
        conversationDao.getConversation(conversationId)?.let { conversation ->
            if (!conversation.isActive) {
                // Reactivate the conversation
                conversationDao.updateConversation(conversation.copy(isActive = true))
            }
            currentConversationId = conversationId
            Log.i(TAG, "Resumed conversation: $conversationId")
            return true
        }
        return false
    }

    /**
     * Delete a conversation
     */
    suspend fun deleteConversation(conversationId: String) {
        conversationDao.getConversation(conversationId)?.let { conversation ->
            conversationDao.deleteConversation(conversation)
            if (currentConversationId == conversationId) {
                currentConversationId = null
            }
            Log.i(TAG, "Deleted conversation: $conversationId")
        }
    }
}
