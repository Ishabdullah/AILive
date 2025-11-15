package com.ailive.memory.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ailive.memory.database.entities.FactCategory
import com.ailive.memory.database.entities.LongTermFactEntity

@Dao
interface LongTermFactDao {

    /**
     * Insert a new fact or replace it if it already exists.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fact: LongTermFactEntity)

    /**
     * Update an existing fact.
     */
    @Update
    suspend fun update(fact: LongTermFactEntity)

    /**
     * Get a fact by its unique ID.
     */
    @Query("SELECT * FROM long_term_facts WHERE id = :id")
    suspend fun getById(id: String): LongTermFactEntity?

    /**
     * Get all facts, ordered by when they were last verified.
     */
    @Query("SELECT * FROM long_term_facts ORDER BY lastVerified DESC")
    suspend fun getAll(): List<LongTermFactEntity>

    /**
     * Find facts by their category.
     */
    @Query("SELECT * FROM long_term_facts WHERE category = :category ORDER BY importance DESC")
    suspend fun findByCategory(category: FactCategory): List<LongTermFactEntity>

    /**
     * Search for facts containing specific text.
     */
    @Query("SELECT * FROM long_term_facts WHERE factText LIKE '%' || :searchText || '%'")
    suspend fun searchByText(searchText: String): List<LongTermFactEntity>

    /**
     * Delete a fact by its ID.
     */
    @Query("DELETE FROM long_term_facts WHERE id = :id")
    suspend fun deleteById(id: String)
}