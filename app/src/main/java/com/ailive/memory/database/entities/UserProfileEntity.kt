package com.ailive.memory.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.ailive.memory.database.converters.Converters

/**
 * User Profile - Personal information, preferences, and profile data
 *
 * Stores structured user information that's frequently accessed.
 * Only one profile per user (singleton pattern).
 */
@Entity(tableName = "user_profile")
@TypeConverters(Converters::class)
data class UserProfileEntity(
    @PrimaryKey
    val id: String = "default_user",  // Singleton

    // Personal Information
    val name: String? = null,
    val nickname: String? = null,
    val birthday: Long? = null,  // Timestamp
    val age: Int? = null,
    val gender: String? = null,
    val location: String? = null,

    // Contact & Social
    val email: String? = null,
    val phone: String? = null,
    val socialMedia: Map<String, String> = emptyMap(),  // platform -> handle

    // Preferences
    val favoriteColor: String? = null,
    val favoriteFoods: List<String> = emptyList(),
    val favoriteMusic: List<String> = emptyList(),
    val favoriteMovies: List<String> = emptyList(),
    val favoriteSports: List<String> = emptyList(),
    val favoriteTeams: List<String> = emptyList(),
    val hobbies: List<String> = emptyList(),
    val interests: List<String> = emptyList(),

    // Work & Education
    val occupation: String? = null,
    val company: String? = null,
    val education: String? = null,
    val skills: List<String> = emptyList(),

    // Relationships
    val familyMembers: Map<String, String> = emptyMap(),  // relation -> name
    val friends: List<String> = emptyList(),
    val pets: Map<String, String> = emptyMap(),  // name -> type

    // Goals & Projects
    val currentGoals: List<String> = emptyList(),
    val activeProjects: List<String> = emptyList(),
    val achievements: List<String> = emptyList(),

    // Preferences & Settings
    val communicationStyle: String? = null,  // formal, casual, technical, etc.
    val preferredTopics: List<String> = emptyList(),
    val avoidTopics: List<String> = emptyList(),

    // Metadata
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val profileCompleteness: Float = 0.0f,  // 0.0-1.0

    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * Calculate profile completeness based on filled fields
     */
    fun calculateCompleteness(): Float {
        var filledFields = 0
        var totalFields = 0

        // Count basic fields
        listOf(name, nickname, birthday, occupation, location).forEach {
            totalFields++
            if (it != null) filledFields++
        }

        // Count list fields
        listOf(hobbies, interests, favoriteMovies, favoriteMusic, currentGoals).forEach {
            totalFields++
            if (it.isNotEmpty()) filledFields++
        }

        // Count map fields
        listOf(familyMembers, pets, socialMedia).forEach {
            totalFields++
            if (it.isNotEmpty()) filledFields++
        }

        return if (totalFields > 0) filledFields.toFloat() / totalFields else 0f
    }

    fun withUpdate(): UserProfileEntity {
        return copy(
            lastUpdated = System.currentTimeMillis(),
            profileCompleteness = calculateCompleteness()
        )
    }
}
