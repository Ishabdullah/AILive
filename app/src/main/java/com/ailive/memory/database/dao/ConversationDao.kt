package com.ailive.memory.database.dao

import androidx.room.*
import com.ailive.memory.database.entities.ConversationEntity
import com.ailive.memory.database.entities.ConversationTurnEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for conversations and conversation turns
 *
 * Provides access to working memory (current conversations).
 */
@Dao
interface ConversationDao {

    // ===== Conversation Operations =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversation(conversationId: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE isActive = 1 ORDER BY lastMessageTime DESC")
    fun getActiveConversationsFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE isActive = 1 ORDER BY lastMessageTime DESC")
    suspend fun getActiveConversations(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE isBookmarked = 1 ORDER BY lastMessageTime DESC")
    fun getBookmarkedConversationsFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY lastMessageTime DESC LIMIT :limit")
    suspend fun getRecentConversations(limit: Int = 10): List<ConversationEntity>

    @Query("UPDATE conversations SET isActive = 0 WHERE lastMessageTime < :cutoffTime")
    suspend fun archiveOldConversations(cutoffTime: Long)

    @Query("DELETE FROM conversations WHERE isActive = 0 AND isBookmarked = 0 AND lastMessageTime < :cutoffTime")
    suspend fun deleteOldConversations(cutoffTime: Long)

    @Query("SELECT COUNT(*) FROM conversations WHERE isActive = 1")
    suspend fun getActiveConversationCount(): Int

    // ===== Conversation Turn Operations =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTurn(turn: ConversationTurnEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTurns(turns: List<ConversationTurnEntity>)

    @Query("SELECT * FROM conversation_turns WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getTurnsForConversation(conversationId: String): List<ConversationTurnEntity>

    @Query("SELECT * FROM conversation_turns WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getTurnsForConversationFlow(conversationId: String): Flow<List<ConversationTurnEntity>>

    @Query("SELECT * FROM conversation_turns WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentTurns(conversationId: String, limit: Int = 10): List<ConversationTurnEntity>

    @Query("DELETE FROM conversation_turns WHERE conversationId = :conversationId")
    suspend fun deleteTurnsForConversation(conversationId: String)

    @Query("SELECT * FROM conversation_turns WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    suspend fun getTurnsInTimeRange(startTime: Long, endTime: Long): List<ConversationTurnEntity>

    // ===== Search Operations =====

    @Query("""
        SELECT * FROM conversations
        WHERE title LIKE '%' || :query || '%'
        ORDER BY lastMessageTime DESC
        LIMIT :limit
    """)
    suspend fun searchConversations(query: String, limit: Int = 20): List<ConversationEntity>

    @Query("""
        SELECT DISTINCT ct.* FROM conversation_turns ct
        WHERE ct.content LIKE '%' || :query || '%'
        ORDER BY ct.timestamp DESC
        LIMIT :limit
    """)
    suspend fun searchTurns(query: String, limit: Int = 20): List<ConversationTurnEntity>
}
