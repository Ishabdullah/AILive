package com.ailive.memory.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ailive.memory.database.entities.FactCategory
import com.ailive.memory.database.entities.LongTermFactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LongTermFactDao {

    /**
     * Insert a new fact or replace it if it already exists.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fact: LongTermFactEntity)

    /**
     * Insert a new fact and return its ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(fact: LongTermFactEntity): Long

    /**
     * Update an existing fact.
     */
    @Update
    suspend fun update(fact: LongTermFactEntity)

    /**
     * Update an existing fact.
     */
    @Update
    suspend fun updateFact(fact: LongTermFactEntity)

    /**
     * Delete a fact
     */
    @Delete
    suspend fun deleteFact(fact: LongTermFactEntity)

    /**
     * Get a fact by its unique ID.
     */
    @Query("SELECT * FROM long_term_facts WHERE id = :id")
    suspend fun getById(id: String): LongTermFactEntity?

    /**
     * Get a fact by its unique ID.
     */
    @Query("SELECT * FROM long_term_facts WHERE id = :factId")
    suspend fun getFact(factId: String): LongTermFactEntity?

    /**
     * Get all facts, ordered by when they were last verified.
     */
    @Query("SELECT * FROM long_term_facts ORDER BY lastVerified DESC")
    suspend fun getAll(): List<LongTermFactEntity>

    /**
     * Get all facts
     */
    @Query("SELECT * FROM long_term_facts")
    suspend fun getAllFacts(): List<LongTermFactEntity>

    /**
     * Find facts by their category.
     */
    @Query("SELECT * FROM long_term_facts WHERE category = :category ORDER BY importance DESC")
    suspend fun findByCategory(category: FactCategory): List<LongTermFactEntity>

    /**
     * Get facts by category
     */
    @Query("SELECT * FROM long_term_facts WHERE category = :category")
    suspend fun getFactsByCategory(category: FactCategory): List<LongTermFactEntity>

    /**
     * Get facts by category as Flow
     */
    @Query("SELECT * FROM long_term_facts WHERE category = :category")
    fun getFactsByCategoryFlow(category: FactCategory): Flow<List<LongTermFactEntity>>

    /**
     * Get important facts
     */
    @Query("SELECT * FROM long_term_facts WHERE importance >= :minImportance ORDER BY importance DESC LIMIT :limit")
    suspend fun getImportantFacts(minImportance: Float = 0.7f, limit: Int = 50): List<LongTermFactEntity>

    /**
     * Search for facts containing specific text.
     */
    @Query("SELECT * FROM long_term_facts WHERE factText LIKE '%' || :searchText || '%'")
    suspend fun searchByText(searchText: String): List<LongTermFactEntity>

    /**
     * Search facts
     */
    @Query("SELECT * FROM long_term_facts WHERE factText LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchFacts(query: String, limit: Int = 20): List<LongTermFactEntity>

    /**
     * Get unverified facts
     */
    @Query("SELECT * FROM long_term_facts WHERE lastVerified < :cutoffTime ORDER BY lastVerified ASC LIMIT :limit")
    suspend fun getUnverifiedFacts(cutoffTime: Long, limit: Int = 10): List<LongTermFactEntity>

    /**
     * Delete low importance old facts
     */
    @Query("DELETE FROM long_term_facts WHERE importance < :minImportance AND firstMentioned < :cutoffTime")
    suspend fun deleteLowImportanceOldFacts(minImportance: Float, cutoffTime: Long): Int

    /**
     * Get fact count
     */
    @Query("SELECT COUNT(*) FROM long_term_facts")
    suspend fun getFactCount(): Int

    /**
     * Get average importance
     */
    @Query("SELECT AVG(importance) FROM long_term_facts")
    suspend fun getAverageImportance(): Float?

    /**
     * Get fact count by category
     */
    @Query("SELECT COUNT(*) FROM long_term_facts WHERE category = :category")
    suspend fun getFactCountByCategory(category: FactCategory): Int

    /**
     * Delete a fact by its ID.
     */
    @Query("DELETE FROM long_term_facts WHERE id = :id")
    suspend fun deleteById(id: String)
}