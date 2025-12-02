package com.adaptheon.core.vector

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * Embedding generation, storage, and retrieval system
 * Provides vector operations for semantic search and similarity matching
 */
class VectorCore {
    private val embeddingGenerator = EmbeddingGenerator()
    private val vectorDatabase = VectorDatabase()
    private val similarityCalculator = SimilarityCalculator()
    
    /**
     * Generate embedding from text
     */
    suspend fun generateEmbedding(
        text: String,
        model: EmbeddingModel = EmbeddingModel.DEFAULT
    ): EmbeddingResult {
        return embeddingGenerator.generate(text, model)
    }
    
    /**
     * Store embedding with metadata
     */
    suspend fun storeEmbedding(
        embedding: FloatArray,
        id: String,
        metadata: Map<String, Any> = emptyMap(),
        namespace: String = "default"
    ): Boolean {
        return vectorDatabase.store(embedding, id, metadata, namespace)
    }
    
    /**
     * Generate and store embedding from text
     */
    suspend fun generateAndStore(
        text: String,
        id: String,
        metadata: Map<String, Any> = emptyMap(),
        namespace: String = "default",
        model: EmbeddingModel = EmbeddingModel.DEFAULT
    ): String {
        val embeddingResult = generateEmbedding(text, model)
        storeEmbedding(embeddingResult.embedding, id, metadata, namespace)
        return embeddingResult.id
    }
    
    /**
     * Find similar vectors by query embedding
     */
    suspend fun findSimilar(
        queryEmbedding: FloatArray,
        topK: Int = 10,
        namespace: String = "default",
        similarityThreshold: Double = 0.0
    ): List<SimilarityResult> {
        val candidates = vectorDatabase.getAllInNamespace(namespace)
        val similarities = candidates.map { candidate ->
            val similarity = similarityCalculator.cosineSimilarity(queryEmbedding, candidate.embedding)
            SimilarityResult(
                id = candidate.id,
                similarity = similarity,
                metadata = candidate.metadata
            )
        }
        
        return similarities
            .filter { it.similarity >= similarityThreshold }
            .sortedByDescending { it.similarity }
            .take(topK)
    }
    
    /**
     * Find similar vectors by query text
     */
    suspend fun findSimilarByText(
        queryText: String,
        topK: Int = 10,
        namespace: String = "default",
        similarityThreshold: Double = 0.0,
        model: EmbeddingModel = EmbeddingModel.DEFAULT
    ): List<SimilarityResult> {
        val queryEmbedding = generateEmbedding(queryText, model).embedding
        return findSimilar(queryEmbedding, topK, namespace, similarityThreshold)
    }
    
    /**
     * Search with metadata filtering
     */
    suspend fun searchWithFilters(
        queryEmbedding: FloatArray,
        filters: Map<String, Any>,
        topK: Int = 10,
        namespace: String = "default",
        similarityThreshold: Double = 0.0
    ): List<SimilarityResult> {
        val candidates = vectorDatabase.getAllInNamespace(namespace)
        val filtered = candidates.filter { candidate ->
            filters.all { (key, value) ->
                candidate.metadata[key] == value
            }
        }
        
        val similarities = filtered.map { candidate ->
            val similarity = similarityCalculator.cosineSimilarity(queryEmbedding, candidate.embedding)
            SimilarityResult(
                id = candidate.id,
                similarity = similarity,
                metadata = candidate.metadata
            )
        }
        
        return similarities
            .filter { it.similarity >= similarityThreshold }
            .sortedByDescending { it.similarity }
            .take(topK)
    }
    
    /**
     * Get embedding by ID
     */
    suspend fun getEmbedding(id: String, namespace: String = "default"): StoredEmbedding? {
        return vectorDatabase.get(id, namespace)
    }
    
    /**
     * Update embedding metadata
     */
    suspend fun updateMetadata(
        id: String,
        metadata: Map<String, Any>,
        namespace: String = "default"
    ): Boolean {
        return vectorDatabase.updateMetadata(id, metadata, namespace)
    }
    
    /**
     * Delete embedding
     */
    suspend fun deleteEmbedding(id: String, namespace: String = "default"): Boolean {
        return vectorDatabase.delete(id, namespace)
    }
    
    /**
     * Batch insert embeddings
     */
    suspend fun batchInsert(
        embeddings: List<BatchEmbeddingItem>,
        namespace: String = "default"
    ): BatchResult {
        return vectorDatabase.batchStore(embeddings, namespace)
    }
    
    /**
     * Get namespace statistics
     */
    suspend fun getNamespaceStats(namespace: String = "default"): NamespaceStats {
        return vectorDatabase.getStats(namespace)
    }
    
    /**
     * List all namespaces
     */
    suspend fun listNamespaces(): List<String> {
        return vectorDatabase.listNamespaces()
    }
    
    /**
     * Calculate similarity between two embeddings
     */
    suspend fun calculateSimilarity(
        embedding1: FloatArray,
        embedding2: FloatArray
    ): Double {
        return similarityCalculator.cosineSimilarity(embedding1, embedding2)
    }
    
    /**
     * Get available embedding models
     */
    suspend fun getAvailableModels(): List<EmbeddingModel> {
        return embeddingGenerator.getAvailableModels()
    }
    
    data class EmbeddingResult(
        val embedding: FloatArray,
        val id: String,
        val model: EmbeddingModel,
        val dimension: Int,
        val processingTimeMs: Long
    )
    
    data class SimilarityResult(
        val id: String,
        val similarity: Double,
        val metadata: Map<String, Any>
    )
    
    data class StoredEmbedding(
        val embedding: FloatArray,
        val id: String,
        val metadata: Map<String, Any>,
        val createdAt: Long
    )
    
    data class BatchEmbeddingItem(
        val embedding: FloatArray,
        val id: String,
        val metadata: Map<String, Any> = emptyMap()
    )
    
    data class BatchResult(
        val successCount: Int,
        val failureCount: Int,
        val errors: List<String>
    )
    
    data class NamespaceStats(
        val totalEmbeddings: Int,
        val dimension: Int,
        val memoryUsageBytes: Long,
        val oldestTimestamp: Long,
        val newestTimestamp: Long
    )
    
    enum class EmbeddingModel {
        DEFAULT,
        SMALL,      // 384 dimensions
        MEDIUM,     // 768 dimensions
        LARGE,      // 1536 dimensions
        MULTILINGUAL
    }
}

/**
 * Embedding generation service
 */
private class EmbeddingGenerator {
    suspend fun generate(text: String, model: VectorCore.EmbeddingModel): VectorCore.EmbeddingResult {
        val startTime = System.currentTimeMillis()
        
        // In real implementation, this would call actual embedding models
        val embedding = when (model) {
            VectorCore.EmbeddingModel.SMALL -> generateRandomEmbedding(384)
            VectorCore.EmbeddingModel.MEDIUM -> generateRandomEmbedding(768)
            VectorCore.EmbeddingModel.LARGE -> generateRandomEmbedding(1536)
            VectorCore.EmbeddingModel.DEFAULT -> generateRandomEmbedding(768)
            VectorCore.EmbeddingModel.MULTILINGUAL -> generateRandomEmbedding(768)
        }
        
        val processingTime = System.currentTimeMillis() - startTime
        
        return VectorCore.EmbeddingResult(
            embedding = embedding,
            id = "emb_${System.currentTimeMillis()}_${(1000..9999).random()}",
            model = model,
            dimension = embedding.size,
            processingTimeMs = processingTime
        )
    }
    
    suspend fun getAvailableModels(): List<VectorCore.EmbeddingModel> {
        return VectorCore.EmbeddingModel.values().toList()
    }
    
    private fun generateRandomEmbedding(dimension: Int): FloatArray {
        return FloatArray(dimension) { kotlin.random.Random.nextFloat() * 2 - 1 }
    }
}

/**
 * Vector database for storage and retrieval
 */
private class VectorDatabase {
    private val storage = ConcurrentHashMap<String, ConcurrentHashMap<String, VectorCore.StoredEmbedding>>()
    
    suspend fun store(
        embedding: FloatArray,
        id: String,
        metadata: Map<String, Any>,
        namespace: String
    ): Boolean {
        val namespaceStorage = storage.getOrPut(namespace) { ConcurrentHashMap() }
        val storedEmbedding = VectorCore.StoredEmbedding(
            embedding = embedding,
            id = id,
            metadata = metadata,
            createdAt = System.currentTimeMillis()
        )
        namespaceStorage[id] = storedEmbedding
        return true
    }
    
    suspend fun batchStore(
        embeddings: List<VectorCore.BatchEmbeddingItem>,
        namespace: String
    ): VectorCore.BatchResult {
        val namespaceStorage = storage.getOrPut(namespace) { ConcurrentHashMap() }
        var successCount = 0
        var failureCount = 0
        val errors = mutableListOf<String>()
        
        embeddings.forEach { item ->
            try {
                val storedEmbedding = VectorCore.StoredEmbedding(
                    embedding = item.embedding,
                    id = item.id,
                    metadata = item.metadata,
                    createdAt = System.currentTimeMillis()
                )
                namespaceStorage[item.id] = storedEmbedding
                successCount++
            } catch (e: Exception) {
                failureCount++
                errors.add("Failed to store ${item.id}: ${e.message}")
            }
        }
        
        return VectorCore.BatchResult(successCount, failureCount, errors)
    }
    
    suspend fun get(id: String, namespace: String): VectorCore.StoredEmbedding? {
        return storage[namespace]?.get(id)
    }
    
    suspend fun getAllInNamespace(namespace: String): List<VectorCore.StoredEmbedding> {
        return storage[namespace]?.values?.toList() ?: emptyList()
    }
    
    suspend fun updateMetadata(id: String, metadata: Map<String, Any>, namespace: String): Boolean {
        val namespaceStorage = storage[namespace] ?: return false
        val existing = namespaceStorage[id] ?: return false
        
        val updated = existing.copy(metadata = metadata)
        namespaceStorage[id] = updated
        return true
    }
    
    suspend fun delete(id: String, namespace: String): Boolean {
        return storage[namespace]?.remove(id) != null
    }
    
    suspend fun getStats(namespace: String): VectorCore.NamespaceStats {
        val embeddings = getAllInNamespace(namespace)
        val dimension = embeddings.firstOrNull()?.embedding?.size ?: 0
        
        return VectorCore.NamespaceStats(
            totalEmbeddings = embeddings.size,
            dimension = dimension,
            memoryUsageBytes = embeddings.sumOf { it.embedding.size * 4 }, // 4 bytes per float
            oldestTimestamp = embeddings.minOfOrNull { it.createdAt } ?: 0L,
            newestTimestamp = embeddings.maxOfOrNull { it.createdAt } ?: 0L
        )
    }
    
    suspend fun listNamespaces(): List<String> {
        return storage.keys.toList()
    }
}

/**
 * Similarity calculation utilities
 */
private class SimilarityCalculator {
    fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Double {
        if (vec1.size != vec2.size) {
            throw IllegalArgumentException("Vector dimensions must match")
        }
        
        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0
        
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }
        
        return if (norm1 > 0 && norm2 > 0) {
            dotProduct / (sqrt(norm1) * sqrt(norm2))
        } else {
            0.0
        }
    }
    
    fun euclideanDistance(vec1: FloatArray, vec2: FloatArray): Double {
        if (vec1.size != vec2.size) {
            throw IllegalArgumentException("Vector dimensions must match")
        }
        
        var sumSquaredDifferences = 0.0
        for (i in vec1.indices) {
            val diff = vec1[i] - vec2[i]
            sumSquaredDifferences += diff * diff
        }
        
        return sqrt(sumSquaredDifferences)
    }
    
    fun manhattanDistance(vec1: FloatArray, vec2: FloatArray): Double {
        if (vec1.size != vec2.size) {
            throw IllegalArgumentException("Vector dimensions must match")
        }
        
        var sumAbsoluteDifferences = 0.0
        for (i in vec1.indices) {
            sumAbsoluteDifferences += kotlin.math.abs(vec1[i] - vec2[i])
        }
        
        return sumAbsoluteDifferences
    }
}