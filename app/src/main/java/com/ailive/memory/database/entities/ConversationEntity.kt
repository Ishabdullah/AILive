package com.ailive.memory.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.ailive.memory.database.converters.Converters

/**
 * Working Memory - Current and recent conversations
 *
 * Represents a conversation session with the AI.
 * Automatically managed with time-based cleanup.
 */
@Entity(tableName = "conversations")
@TypeConverters(Converters::class)
data class ConversationEntity(
    @PrimaryKey
    val id: String,

    // Conversation metadata
    val title: String,  // Auto-generated from first message
    val startTime: Long,
    val lastMessageTime: Long,
    val messageCount: Int = 0,

    // Summary for quick reference
    val summary: String? = null,  // AI-generated summary

    // Context tracking
    val topics: List<String> = emptyList(),  // Extracted topics
    val participants: List<String> = emptyList(),  // Mentioned people/entities

    // Status
    val isActive: Boolean = true,  // Currently active conversation
    val isBookmarked: Boolean = false,  // User marked as important

    // Metadata
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        // Conversations older than 30 days are auto-archived
        const val ACTIVE_DURATION_DAYS = 30
    }
}
