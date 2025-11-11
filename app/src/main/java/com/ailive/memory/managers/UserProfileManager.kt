package com.ailive.memory.managers

import android.content.Context
import android.util.Log
import com.ailive.memory.database.MemoryDatabase
import com.ailive.memory.database.entities.UserProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * User Profile Manager
 *
 * Manages user-specific personal information, preferences, and relationships.
 * Singleton pattern - one profile per user.
 */
class UserProfileManager(context: Context) {
    private val TAG = "UserProfileManager"

    private val database = MemoryDatabase.getInstance(context)
    private val profileDao = database.userProfileDao()

    private val profileId = "default_user"

    // ===== Profile Initialization and Retrieval =====

    /**
     * Get or create user profile
     */
    suspend fun getOrCreateProfile(): UserProfileEntity {
        return profileDao.getProfile(profileId) ?: run {
            val newProfile = UserProfileEntity()
            profileDao.insertProfile(newProfile)
            Log.i(TAG, "Created new user profile")
            newProfile
        }
    }

    /**
     * Get profile as Flow (for UI)
     */
    fun getProfileFlow(): Flow<UserProfileEntity?> {
        return profileDao.getProfileFlow(profileId)
    }

    // ===== Personal Information =====

    suspend fun updateName(name: String) {
        val profile = getOrCreateProfile()
        profileDao.updateProfile(profile.copy(name = name).withUpdate())
        Log.i(TAG, "Updated name to: $name")
    }

    suspend fun updateNickname(nickname: String) {
        val profile = getOrCreateProfile()
        profileDao.updateProfile(profile.copy(nickname = nickname).withUpdate())
        Log.i(TAG, "Updated nickname to: $nickname")
    }

    suspend fun updateBirthday(birthday: Long) {
        val profile = getOrCreateProfile()
        profileDao.updateProfile(profile.copy(birthday = birthday).withUpdate())
        Log.i(TAG, "Updated birthday")
    }

    suspend fun updateLocation(location: String) {
        val profile = getOrCreateProfile()
        profileDao.updateProfile(profile.copy(location = location).withUpdate())
        Log.i(TAG, "Updated location to: $location")
    }

    // ===== Preferences =====

    suspend fun updateFavoriteColor(color: String) {
        val profile = getOrCreateProfile()
        profileDao.updateProfile(profile.copy(favoriteColor = color).withUpdate())
        Log.i(TAG, "Updated favorite color to: $color")
    }

    suspend fun addFavoriteFood(food: String) {
        val profile = getOrCreateProfile()
        val updated = profile.favoriteFoods.toMutableList().apply { add(food) }
        profileDao.updateProfile(profile.copy(favoriteFoods = updated).withUpdate())
        Log.i(TAG, "Added favorite food: $food")
    }

    suspend fun addFavoriteSportsTeam(team: String) {
        val profile = getOrCreateProfile()
        val updated = profile.favoriteTeams.toMutableList().apply { add(team) }
        profileDao.updateProfile(profile.copy(favoriteTeams = updated).withUpdate())
        Log.i(TAG, "Added favorite sports team: $team")
    }

    suspend fun addHobby(hobby: String) {
        val profile = getOrCreateProfile()
        val updated = profile.hobbies.toMutableList().apply { add(hobby) }
        profileDao.updateProfile(profile.copy(hobbies = updated).withUpdate())
        Log.i(TAG, "Added hobby: $hobby")
    }

    suspend fun addInterest(interest: String) {
        val profile = getOrCreateProfile()
        val updated = profile.interests.toMutableList().apply { add(interest) }
        profileDao.updateProfile(profile.copy(interests = updated).withUpdate())
        Log.i(TAG, "Added interest: $interest")
    }

    // ===== Work & Education =====

    suspend fun updateOccupation(occupation: String, company: String? = null) {
        val profile = getOrCreateProfile()
        profileDao.updateProfile(
            profile.copy(
                occupation = occupation,
                company = company ?: profile.company
            ).withUpdate()
        )
        Log.i(TAG, "Updated occupation to: $occupation${company?.let { " at $it" } ?: ""}")
    }

    suspend fun addSkill(skill: String) {
        val profile = getOrCreateProfile()
        val updated = profile.skills.toMutableList().apply { add(skill) }
        profileDao.updateProfile(profile.copy(skills = updated).withUpdate())
        Log.i(TAG, "Added skill: $skill")
    }

    // ===== Relationships =====

    suspend fun addFamilyMember(relation: String, name: String) {
        val profile = getOrCreateProfile()
        val updated = profile.familyMembers.toMutableMap().apply { put(relation, name) }
        profileDao.updateProfile(profile.copy(familyMembers = updated).withUpdate())
        Log.i(TAG, "Added family member: $relation - $name")
    }

    suspend fun addFriend(name: String) {
        val profile = getOrCreateProfile()
        val updated = profile.friends.toMutableList().apply { add(name) }
        profileDao.updateProfile(profile.copy(friends = updated).withUpdate())
        Log.i(TAG, "Added friend: $name")
    }

    suspend fun addPet(name: String, type: String) {
        val profile = getOrCreateProfile()
        val updated = profile.pets.toMutableMap().apply { put(name, type) }
        profileDao.updateProfile(profile.copy(pets = updated).withUpdate())
        Log.i(TAG, "Added pet: $name ($type)")
    }

    // ===== Goals & Projects =====

    suspend fun addGoal(goal: String) {
        val profile = getOrCreateProfile()
        val updated = profile.currentGoals.toMutableList().apply { add(goal) }
        profileDao.updateProfile(profile.copy(currentGoals = updated).withUpdate())
        Log.i(TAG, "Added goal: $goal")
    }

    suspend fun removeGoal(goal: String) {
        val profile = getOrCreateProfile()
        val updated = profile.currentGoals.toMutableList().apply { remove(goal) }
        profileDao.updateProfile(profile.copy(currentGoals = updated).withUpdate())
        Log.i(TAG, "Removed goal: $goal")
    }

    suspend fun addProject(project: String) {
        val profile = getOrCreateProfile()
        val updated = profile.activeProjects.toMutableList().apply { add(project) }
        profileDao.updateProfile(profile.copy(activeProjects = updated).withUpdate())
        Log.i(TAG, "Added project: $project")
    }

    suspend fun addAchievement(achievement: String) {
        val profile = getOrCreateProfile()
        val updated = profile.achievements.toMutableList().apply { add(achievement) }
        profileDao.updateProfile(profile.copy(achievements = updated).withUpdate())
        Log.i(TAG, "Added achievement: $achievement")
    }

    // ===== Preferences & Settings =====

    suspend fun setCommunicationStyle(style: String) {
        val profile = getOrCreateProfile()
        profileDao.updateProfile(profile.copy(communicationStyle = style).withUpdate())
        Log.i(TAG, "Set communication style to: $style")
    }

    suspend fun addPreferredTopic(topic: String) {
        val profile = getOrCreateProfile()
        val updated = profile.preferredTopics.toMutableList().apply { add(topic) }
        profileDao.updateProfile(profile.copy(preferredTopics = updated).withUpdate())
        Log.i(TAG, "Added preferred topic: $topic")
    }

    suspend fun addAvoidTopic(topic: String) {
        val profile = getOrCreateProfile()
        val updated = profile.avoidTopics.toMutableList().apply { add(topic) }
        profileDao.updateProfile(profile.copy(avoidTopics = updated).withUpdate())
        Log.i(TAG, "Added topic to avoid: $topic")
    }

    // ===== Profile Completeness =====

    suspend fun getProfileCompleteness(): Float {
        return profileDao.getProfileCompleteness(profileId) ?: 0f
    }

    suspend fun recalculateCompleteness() {
        val profile = getOrCreateProfile()
        val completeness = profile.calculateCompleteness()
        profileDao.updateCompleteness(completeness, profileId)
        Log.i(TAG, "Profile completeness: ${(completeness * 100).toInt()}%")
    }

    // ===== Profile Summary =====

    /**
     * Get a text summary of the user profile for AI context
     */
    suspend fun getProfileSummary(): String {
        val profile = getOrCreateProfile()

        return buildString {
            profile.name?.let { append("Name: $it\n") }
            profile.occupation?.let { append("Occupation: $it\n") }
            profile.location?.let { append("Location: $it\n") }

            if (profile.hobbies.isNotEmpty()) {
                append("Hobbies: ${profile.hobbies.joinToString(", ")}\n")
            }
            if (profile.interests.isNotEmpty()) {
                append("Interests: ${profile.interests.joinToString(", ")}\n")
            }
            if (profile.currentGoals.isNotEmpty()) {
                append("Goals: ${profile.currentGoals.joinToString(", ")}\n")
            }
            if (profile.familyMembers.isNotEmpty()) {
                append("Family: ${profile.familyMembers.entries.joinToString(", ") { "${it.key}: ${it.value}" }}\n")
            }
            if (profile.pets.isNotEmpty()) {
                append("Pets: ${profile.pets.entries.joinToString(", ") { "${it.key} (${it.value})" }}\n")
            }
        }.trim()
    }

    // ===== Profile Reset =====

    suspend fun clearProfile() {
        profileDao.deleteProfile()
        Log.w(TAG, "User profile cleared")
    }
}
