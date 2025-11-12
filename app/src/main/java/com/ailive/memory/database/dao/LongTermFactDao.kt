package com.ailive.memory.database.dao

import androidx.room.*
import com.ailive.memory.database.entities.FactCategory
import com.ailive.memory.database.entities.LongTermFactEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for long-term facts
 *
 * Provides access to long-term memory (important facts and knowledge).
 */
@Dao
interface LongTermFactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(fact: LongTermFactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFacts(facts: List<LongTermFactEntity>)

    @Update
    suspend fun updateFact(fact: LongTermFactEntity)

    @Delete
    suspend fun deleteFact(fact: LongTermFactEntity)

    @Query("SELECT * FROM long_term_facts WHERE id = :factId")
    suspend fun getFact(factId: String): LongTermFactEntity?

    @Query("SELECT * FROM long_term_facts ORDER BY importance DESC, lastVerified DESC")
    fun getAllFactsFlow(): Flow<List<LongTermFactEntity>>

    @Query("SELECT * FROM long_term_facts ORDER BY importance DESC, lastVerified DESC")
    suspend fun getAllFacts(): List<LongTermFactEntity>

    // ===== Category-based queries =====

    @Query("SELECT * FROM long_term_facts WHERE category = :category ORDER BY importance DESC")
    suspend fun getFactsByCategory(category: FactCategory): List<LongTermFactEntity>

    @Query("SELECT * FROM long_term_facts WHERE category = :category ORDER BY importance DESC")
    fun getFactsByCategoryFlow(category: FactCategory): Flow<List<LongTermFactEntity>>

    // ===== Importance-based queries =====

    @Query("SELECT * FROM long_term_facts WHERE importance >= :minImportance ORDER BY importance DESC LIMIT :limit")
    suspend fun getImportantFacts(minImportance: Float = 0.7f, limit: Int = 50): List<LongTermFactEntity>

    @Query("SELECT * FROM long_term_facts ORDER BY importance DESC LIMIT :limit")
    suspend fun getTopFacts(limit: Int = 20): List<LongTermFactEntity>

    // ===== Search operations =====

    @Query("""
        SELECT * FROM long_term_facts
        WHERE factText LIKE '%' || :query || '%'
        OR :query IN (SELECT value FROM json_each(tags))
        ORDER BY importance DESC
        LIMIT :limit
    """)
    suspend fun searchFacts(query: String, limit: Int = 20): List<LongTermFactEntity>

    // ===== Time-based queries =====

    @Query("SELECT * FROM long_term_facts WHERE firstMentioned >= :startTime AND firstMentioned <= :endTime ORDER BY firstMentioned DESC")
    suspend fun getFactsInTimeRange(startTime: Long, endTime: Long): List<LongTermFactEntity>

    @Query("SELECT * FROM long_term_facts WHERE lastVerified < :cutoffTime ORDER BY lastVerified ASC LIMIT :limit")
    suspend fun getUnverifiedFacts(cutoffTime: Long, limit: Int = 10): List<LongTermFactEntity>

    // ===== Statistics =====

    @Query("SELECT COUNT(*) FROM long_term_facts")
    suspend fun getFactCount(): Int

    @Query("SELECT COUNT(*) FROM long_term_facts WHERE category = :category")
    suspend fun getFactCountByCategory(category: FactCategory): Int

    @Query("SELECT AVG(importance) FROM long_term_facts")
    suspend fun getAverageImportance(): Float?

    // ===== Cleanup =====

    @Query("DELETE FROM long_term_facts WHERE importance < :minImportance AND lastAccessed < :cutoffTime")
    suspend fun deleteLowImportanceOldFacts(minImportance: Float = 0.3f, cutoffTime: Long): Int

    // ===== Related facts =====

    @Query("""
        SELECT * FROM long_term_facts
        WHERE id IN (:relatedIds)
        ORDER BY importance DESC
    """)
    suspend fun getRelatedFacts(relatedIds: List<String>): List<LongTermFactEntity>
}
