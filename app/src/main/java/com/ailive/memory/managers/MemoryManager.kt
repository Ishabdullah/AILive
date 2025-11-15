package com.ailive.memory.managers

import android.content.Context
import com.ailive.memory.database.MemoryDatabase
import com.ailive.memory.database.entities.FactCategory
import com.ailive.memory.database.entities.LongTermFactEntity
import java.util.UUID

/**
 * Manages the storage, retrieval, and analysis of long-term memories (facts).
 * This class is the primary entry point for all memory-related operations.
 */
class MemoryManager(context: Context) {

    private val db = MemoryDatabase.getInstance(context)
    private val factDao = db.longTermFactDao()

    /**
     * Creates and stores a new long-term fact.
     *
     * @param factText The text of the fact (e.g., "User's cat is named Whiskers").
     * @param category The category of the fact.
     * @param extractedFrom A reference to where this fact was learned (e.g., a conversation ID).
     * @return The newly created LongTermFactEntity.
     */
    suspend fun rememberFact(
        factText: String,
        category: FactCategory,
        extractedFrom: String
    ): LongTermFactEntity {
        val timestamp = System.currentTimeMillis()

        // TODO: Call the C++ layer via JNI to get the embedding for the factText.
        val embedding = generateEmbedding(factText)

        val newFact = LongTermFactEntity(
            id = UUID.randomUUID().toString(),
            factText = factText,
            category = category,
            extractedFrom = extractedFrom,
            firstMentioned = timestamp,
            lastVerified = timestamp,
            embedding = embedding
            // Other fields can be set to their default values.
        )

        factDao.insert(newFact)
        return newFact
    }

    /**
     * Retrieves a specific fact by its ID.
     */
    suspend fun recallFact(id: String): LongTermFactEntity? {
        val fact = factDao.getById(id)
        // Update access stats when a fact is recalled.
        fact?.let {
            factDao.update(it.withAccessUpdate())
        }
        return fact
    }

    /**
     * Retrieves all stored facts.
     */
    suspend fun recallAllFacts(): List<LongTermFactEntity> {
        return factDao.getAll()
    }

    /**
     * Performs a semantic search to find the most relevant facts to a given query.
     *
     * @param queryText The text to search for relevant facts against.
     * @param topN The number of top results to return.
     * @return A list of the most relevant facts.
     */
    suspend fun searchRelevantFacts(queryText: String, topN: Int = 5): List<LongTermFactEntity> {
        // 1. Generate an embedding for the query text.
        val queryEmbedding = generateEmbedding(queryText)
        if (queryEmbedding == null) {
            // Fallback to simple text search if embedding fails.
            return factDao.searchByText(queryText).take(topN)
        }

        // 2. Get all facts that have an embedding.
        val allFacts = factDao.getAll().filter { it.embedding != null }

        // 3. Calculate cosine similarity and sort. (This is a simplified example).
        // In a real implementation, this would be a more complex operation,
        // potentially optimized in the C++ layer or with a dedicated vector database.
        val scoredFacts = allFacts.map { fact ->
            val similarity = cosineSimilarity(queryEmbedding, fact.embedding!!)
            fact to similarity
        }.sortedByDescending { it.second }

        return scoredFacts.take(topN).map { it.first }
    }

    /**
     * Deletes a fact from memory.
     */
    suspend fun forgetFact(id: String) {
        factDao.deleteById(id)
    }

    // --- Private Helper Functions ---

    /**
     * TODO: Placeholder for the JNI call to the C++ layer to generate an embedding.
     */
    private suspend fun generateEmbedding(text: String): List<Float>? {
        // This should call a native C++ function that runs the embedding model.
        // For now, returns null.
        println("TODO: Generate embedding for: $text")
        return null
    }

    /**
     * TODO: Placeholder for cosine similarity calculation.
     */
    private fun cosineSimilarity(vec1: List<Float>, vec2: List<Float>): Float {
        // This should also ideally be implemented in C++ for performance.
        if (vec1.size != vec2.size) return 0f
        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }
        if (norm1 == 0.0 || norm2 == 0.0) return 0f
        return (dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2))).toFloat()
    }
}
