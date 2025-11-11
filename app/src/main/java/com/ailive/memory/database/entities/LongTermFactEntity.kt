package com.ailive.memory.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.ailive.memory.database.converters.Converters

/**
 * Long-Term Memory - Important facts and knowledge
 *
 * Stores facts learned about the user, preferences, and important information.
 * These persist indefinitely until explicitly deleted.
 */
@Entity(
    tableName = "long_term_facts",
    indices = [Index("category"), Index("importance"), Index("lastVerified")]
)
@TypeConverters(Converters::class)
data class LongTermFactEntity(
    @PrimaryKey
    val id: String,

    // Fact content
    val category: FactCategory,
    val factText: String,  // The actual fact (e.g., "User's favorite color is blue")
    val extractedFrom: String,  // Source conversation ID or context

    // Importance and relevance
    val importance: Float = 0.5f,  // 0.0-1.0, auto-calculated
    val confidence: Float = 1.0f,  // How confident we are (0.0-1.0)

    // Temporal tracking
    val firstMentioned: Long,
    val lastVerified: Long,  // Last time this was confirmed
    val verificationCount: Int = 1,

    // Usage tracking
    val accessCount: Int = 0,
    val lastAccessed: Long = firstMentioned,

    // Relationships
    val relatedFactIds: List<String> = emptyList(),  // Related facts
    val tags: List<String> = emptyList(),  // User-defined tags

    // Semantic search
    val embedding: List<Float>? = null,

    // Metadata
    val metadata: Map<String, String> = emptyMap()
) {
    fun withAccessUpdate(): LongTermFactEntity {
        return copy(
            accessCount = accessCount + 1,
            lastAccessed = System.currentTimeMillis()
        )
    }

    fun withVerification(): LongTermFactEntity {
        return copy(
            lastVerified = System.currentTimeMillis(),
            verificationCount = verificationCount + 1
        )
    }
}

/**
 * Categories for organizing facts
 */
enum class FactCategory {
    PERSONAL_INFO,      // Name, birthday, age, etc.
    PREFERENCES,        // Likes, dislikes, favorites
    RELATIONSHIPS,      // Family, friends, colleagues
    GOALS,              // User's goals and aspirations
    HABITS,             // Daily routines, patterns
    EVENTS,             // Important events, milestones
    SKILLS,             // User's abilities and knowledge
    INTERESTS,          // Hobbies, topics of interest
    LOCATION,           // Places important to user
    WORK,               // Job, career, projects
    HEALTH,             // Health-related information
    OTHER               // Miscellaneous facts
}
