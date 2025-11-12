package com.ailive.memory.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.ailive.memory.database.converters.Converters

/**
 * Individual message in a conversation
 *
 * Stores each turn (user message + AI response) in a conversation.
 */
@Entity(
    tableName = "conversation_turns",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId"), Index("timestamp")]
)
@TypeConverters(Converters::class)
data class ConversationTurnEntity(
    @PrimaryKey
    val id: String,

    // Parent conversation
    val conversationId: String,

    // Message content
    val role: String,  // "USER" or "ASSISTANT"
    val content: String,
    val timestamp: Long,

    // Context at this point
    val emotionContext: String? = null,  // Detected emotion
    val locationContext: String? = null,  // Location if available

    // Processing metadata
    val responseTime: Long? = null,  // How long AI took to respond (ms)
    val tokenCount: Int? = null,  // Token count for this turn

    // Semantic search
    val embedding: List<Float>? = null,  // For semantic search

    // Metadata
    val metadata: Map<String, String> = emptyMap()
)
