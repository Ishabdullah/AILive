package com.ailive.memory.database.dao

import androidx.room.*
import com.ailive.memory.database.entities.UserProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for user profile
 *
 * Manages the singleton user profile entity.
 */
@Dao
interface UserProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileEntity)

    @Update
    suspend fun updateProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profile WHERE id = :profileId LIMIT 1")
    suspend fun getProfile(profileId: String = "default_user"): UserProfileEntity?

    @Query("SELECT * FROM user_profile WHERE id = :profileId LIMIT 1")
    fun getProfileFlow(profileId: String = "default_user"): Flow<UserProfileEntity?>

    @Query("DELETE FROM user_profile")
    suspend fun deleteProfile()

    // ===== Convenience methods for common updates =====

    @Query("UPDATE user_profile SET name = :name, lastUpdated = :timestamp WHERE id = :profileId")
    suspend fun updateName(name: String, profileId: String = "default_user", timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE user_profile SET occupation = :occupation, company = :company, lastUpdated = :timestamp WHERE id = :profileId")
    suspend fun updateWorkInfo(occupation: String?, company: String?, profileId: String = "default_user", timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE user_profile SET location = :location, lastUpdated = :timestamp WHERE id = :profileId")
    suspend fun updateLocation(location: String, profileId: String = "default_user", timestamp: Long = System.currentTimeMillis())

    // ===== Profile completeness =====

    @Query("SELECT profileCompleteness FROM user_profile WHERE id = :profileId")
    suspend fun getProfileCompleteness(profileId: String = "default_user"): Float?

    @Query("UPDATE user_profile SET profileCompleteness = :completeness, lastUpdated = :timestamp WHERE id = :profileId")
    suspend fun updateCompleteness(completeness: Float, profileId: String = "default_user", timestamp: Long = System.currentTimeMillis())
}
