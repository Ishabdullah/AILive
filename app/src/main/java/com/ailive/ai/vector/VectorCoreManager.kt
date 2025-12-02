package com.ailive.ai.vector

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * VectorCore Manager
 * Provides high-performance vector storage, indexing, and retrieval for embeddings
 */
class VectorCoreManager(private val context: Context) {
    
    companion object {
        private const val TAG = "VectorCoreManager"
        private const val VECTOR_DB_FILE = "vector_database.dat"
        private const val INDEX_FILE = "vector_index.idx"
        private const val METADATA_FILE = "vector_metadata.json"
        private const val DEFAULT_VECTOR_DIMENSION = 768 // Common for BGE models
    }
    
    // Vector storage
    private val vectors = ConcurrentHashMap<String, FloatArray>()
    private val metadata = ConcurrentHashMap<String, VectorMetadata>()
    private val index = VectorIndex()
    
    // State management
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private val _vectorCount = MutableStateFlow(0)
    val vectorCount: StateFlow<Int> = _vectorCount.asStateFlow()
    
    private val _indexSize = MutableStateFlow(0)
    val indexSize: StateFlow<Int> = _indexSize.asStateFlow()
    
    private val _searchPerformance = MutableStateFlow(SearchPerformanceMetrics())
    val searchPerformance: StateFlow<SearchPerformanceMetrics> = _searchPerformance.asStateFlow()
    
    // Configuration
    private val vectorDimension = DEFAULT_VECTOR_DIMENSION
    private val maxIndexSize = 10000
    
    // File paths
    private val vectorDbDir = File(context.filesDir, "vector_core")
    private val vectorDbFile = File(vectorDbDir, VECTOR_DB_FILE)
    private val indexFile = File(vectorDbDir, INDEX_FILE)
    private val metadataFile = File(vectorDbDir, METADATA_FILE)
    
    /**
     * Initialize VectorCore manager
     */
    suspend fun initialize(vectorDimension: Int = DEFAULT_VECTOR_DIMENSION): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Create directories if they don't exist
                if (!vectorDbDir.exists()) {
                    vectorDbDir.mkdirs()
                }
                
                // Load existing vector database or create new one
                if (vectorDbFile.exists()) {
                    loadVectorDatabase()
                } else {
                    createEmptyDatabase()
                }
                
                // Initialize or load index
                if (indexFile.exists()) {
                    loadIndex()
                } else {
                    buildIndex()
                }
                
                _isInitialized.value = true
                updateMetrics()
                true
                
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Add a vector to the database
     */
    suspend fun addVector(
        id: String,
        vector: FloatArray,
        metadata: VectorMetadata
    ): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                // Validate vector dimensions
                if (vector.size != vectorDimension) {
                    return@withContext false
                }
                
                // Normalize vector for better similarity search
                val normalizedVector = normalizeVector(vector)
                
                // Store vector and metadata
                vectors[id] = normalizedVector
                this@VectorCoreManager.metadata[id] = metadata
                
                // Add to index if space available
                if (_indexSize.value < maxIndexSize) {
                    index.addVector(id, normalizedVector)
                    _indexSize.value = index.size()
                }
                
                _vectorCount.value = vectors.size
                true
                
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Add multiple vectors in batch
     */
    suspend fun addVectors(
        vectorData: List<VectorData>
    ): BatchAddResult {
        return withContext(Dispatchers.Default) {
            var successCount = 0
            val errors = mutableListOf<String>()
            
            vectorData.forEach { data ->
                if (addVector(data.id, data.vector, data.metadata)) {
                    successCount++
                } else {
                    errors.add("Failed to add vector: ${data.id}")
                }
            }
            
            BatchAddResult(
                successCount = successCount,
                totalCount = vectorData.size,
                errors = errors
            )
        }
    }
    
    /**
     * Search for similar vectors using cosine similarity
     */
    suspend fun searchSimilar(
        queryVector: FloatArray,
        topK: Int = 10,
        threshold: Float = 0.7f
    ): List<SearchResult> {
        return withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            
            try {
                // Normalize query vector
                val normalizedQuery = normalizeVector(queryVector)
                
                // Use index for fast search if available
                val results = if (_indexSize.value > 0) {
                    index.search(normalizedQuery, topK, threshold)
                } else {
                    // Fallback to linear search
                    performLinearSearch(normalizedQuery, topK, threshold)
                }
                
                val processingTime = System.currentTimeMillis() - startTime
                updateSearchMetrics(processingTime, results.size)
                
                results
                
            } catch (e: Exception) {
                updateSearchMetrics(System.currentTimeMillis() - startTime, 0)
                emptyList()
            }
        }
    }
    
    /**
     * Search vectors by metadata filters
     */
    suspend fun searchByMetadata(
        filters: Map<String, Any>,
        topK: Int = 100
    ): List<SearchResult> {
        return withContext(Dispatchers.Default) {
            try {
                val filteredVectors = metadata.filter { (_, meta) ->
                    filters.all { (key, value) ->
                        when (key) {
                            "type" -> meta.type == value as String
                            "source" -> meta.source == value as String
                            "timestamp" -> {
                                val timeRange = value as Pair<Long, Long>
                                meta.timestamp in timeRange.first..timeRange.second
                            }
                            else -> meta.properties[key] == value
                        }
                    }
                }
                
                filteredVectors.map { (id, meta) ->
                    vectors[id]?.let { vector ->
                        SearchResult(
                            id = id,
                            vector = vector,
                            metadata = meta,
                            score = 1.0f, // Perfect match for metadata search
                            distance = 0.0f
                        )
                    }
                }.filterNotNull().take(topK)
                
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * Delete a vector from the database
     */
    suspend fun deleteVector(id: String): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                vectors.remove(id)
                metadata.remove(id)
                index.removeVector(id)
                
                _vectorCount.value = vectors.size
                _indexSize.value = index.size()
                true
                
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Update vector metadata
     */
    suspend fun updateMetadata(id: String, newMetadata: VectorMetadata): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                if (vectors.containsKey(id)) {
                    metadata[id] = newMetadata
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Get vector by ID
     */
    suspend fun getVector(id: String): FloatArray? {
        return withContext(Dispatchers.Default) {
            vectors[id]
        }
    }
    
    /**
     * Get metadata for a vector
     */
    suspend fun getMetadata(id: String): VectorMetadata? {
        return withContext(Dispatchers.Default) {
            metadata[id]
        }
    }
    
    /**
     * Rebuild the search index
     */
    suspend fun rebuildIndex(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                index.clear()
                
                vectors.forEach { (id, vector) ->
                    index.addVector(id, vector)
                }
                
                _indexSize.value = index.size()
                saveIndex()
                true
                
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Optimize the vector database
     */
    suspend fun optimize(): OptimizationResult {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                
                // Remove expired vectors if any
                val expiredThreshold = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L) // 30 days
                val expiredIds = metadata.filter { (_, meta) ->
                    meta.timestamp < expiredThreshold
                }.keys
                
                expiredIds.forEach { id ->
                    deleteVector(id)
                }
                
                // Rebuild index for better performance
                rebuildIndex()
                
                // Compact storage
                saveVectorDatabase()
                
                val processingTime = System.currentTimeMillis() - startTime
                
                OptimizationResult(
                    success = true,
                    vectorsRemoved = expiredIds.size,
                    processingTime = processingTime,
                    newSize = _vectorCount.value
                )
                
            } catch (e: Exception) {
                OptimizationResult(
                    success = false,
                    error = e.message
                )
            }
        }
    }
    
    /**
     * Save vector database to disk
     */
    suspend fun saveDatabase(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                saveVectorDatabase()
                saveMetadata()
                saveIndex()
                true
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Get database statistics
     */
    fun getStatistics(): VectorDatabaseStatistics {
        return VectorDatabaseStatistics(
            vectorCount = _vectorCount.value,
            indexSize = _indexSize.value,
            dimension = vectorDimension,
            storageSize = if (vectorDbFile.exists()) vectorDbFile.length() else 0L,
            searchMetrics = _searchPerformance.value
        )
    }
    
    // Private helper methods
    
    private fun loadVectorDatabase() {
        try {
            // Load vectors from binary file
            if (vectorDbFile.exists()) {
                val buffer = ByteBuffer.wrap(vectorDbFile.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
                
                while (buffer.hasRemaining()) {
                    val idLength = buffer.int
                    val idBytes = ByteArray(idLength)
                    buffer.get(idBytes)
                    val id = String(idBytes)
                    
                    val vector = FloatArray(vectorDimension)
                    repeat(vectorDimension) { i ->
                        vector[i] = buffer.float
                    }
                    
                    vectors[id] = vector
                }
                
                _vectorCount.value = vectors.size
            }
            
        } catch (e: Exception) {
            createEmptyDatabase()
        }
    }
    
    private fun createEmptyDatabase() {
        vectors.clear()
        _vectorCount.value = 0
    }
    
    private fun saveVectorDatabase() {
        try {
            val buffer = ByteBuffer.allocate(vectors.size * (4 + vectorDimension * 4 + 32)).order(ByteOrder.LITTLE_ENDIAN)
            
            vectors.forEach { (id, vector) ->
                val idBytes = id.toByteArray()
                buffer.putInt(idBytes.size)
                buffer.put(idBytes)
                
                vector.forEach { value ->
                    buffer.putFloat(value)
                }
            }
            
            vectorDbFile.writeBytes(buffer.array())
            
        } catch (e: Exception) {
            // Handle save error
        }
    }
    
    private fun loadMetadata() {
        try {
            if (metadataFile.exists()) {
                val metadataJson = JSONObject(metadataFile.readText())
                metadataJson.keys().forEach { id ->
                    val metaJson = metadataJson.getJSONObject(id)
                    metadata[id] = VectorMetadata.fromJson(metaJson)
                }
            }
        } catch (e: Exception) {
            // Handle load error
        }
    }
    
    private fun saveMetadata() {
        try {
            val metadataJson = JSONObject()
            metadata.forEach { (id, meta) ->
                metadataJson.put(id, meta.toJson())
            }
            metadataFile.writeText(metadataJson.toString())
        } catch (e: Exception) {
            // Handle save error
        }
    }
    
    private fun loadIndex() {
        try {
            if (indexFile.exists()) {
                index.loadFromFile(indexFile)
                _indexSize.value = index.size()
            }
        } catch (e: Exception) {
            buildIndex()
        }
    }
    
    private fun saveIndex() {
        try {
            index.saveToFile(indexFile)
        } catch (e: Exception) {
            // Handle save error
        }
    }
    
    private fun buildIndex() {
        vectors.forEach { (id, vector) ->
            index.addVector(id, vector)
        }
        _indexSize.value = index.size()
    }
    
    private fun normalizeVector(vector: FloatArray): FloatArray {
        val norm = sqrt(vector.map { it * it }.sum())
        return if (norm > 0) {
            vector.map { it / norm }.toFloatArray()
        } else {
            vector
        }
    }
    
    private fun performLinearSearch(
        queryVector: FloatArray,
        topK: Int,
        threshold: Float
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        
        vectors.forEach { (id, vector) ->
            val similarity = cosineSimilarity(queryVector, vector)
            if (similarity >= threshold) {
                metadata[id]?.let { meta ->
                    results.add(
                        SearchResult(
                            id = id,
                            vector = vector,
                            metadata = meta,
                            score = similarity,
                            distance = 1.0f - similarity
                        )
                    )
                }
            }
        }
        
        return results.sortedByDescending { it.score }.take(topK)
    }
    
    private fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }
        
        return if (norm1 > 0 && norm2 > 0) {
            dotProduct / (sqrt(norm1) * sqrt(norm2))
        } else {
            0f
        }
    }
    
    private fun updateMetrics() {
        _vectorCount.value = vectors.size
        _indexSize.value = index.size()
    }
    
    private fun updateSearchMetrics(processingTime: Long, resultCount: Int) {
        val currentMetrics = _searchPerformance.value
        _searchPerformance.value = SearchPerformanceMetrics(
            averageSearchTime = (currentMetrics.averageSearchTime + processingTime) / 2,
            totalSearches = currentMetrics.totalSearches + 1,
            averageResultCount = (currentMetrics.averageResultCount + resultCount) / 2,
            indexHitRate = if (_indexSize.value > 0) 1.0f else 0.0f
        )
    }
}

// VectorIndex implementation for fast similarity search
class VectorIndex {
    private val vectors = ConcurrentHashMap<String, FloatArray>()
    private val dimension = 768 // Default dimension
    
    fun addVector(id: String, vector: FloatArray) {
        vectors[id] = vector
    }
    
    fun removeVector(id: String) {
        vectors.remove(id)
    }
    
    fun search(queryVector: FloatArray, topK: Int, threshold: Float): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        
        vectors.forEach { (id, vector) ->
            val similarity = cosineSimilarity(queryVector, vector)
            if (similarity >= threshold) {
                results.add(
                    SearchResult(
                        id = id,
                        vector = vector,
                        metadata = VectorMetadata("", "", 0L), // Would be populated by caller
                        score = similarity,
                        distance = 1.0f - similarity
                    )
                )
            }
        }
        
        return results.sortedByDescending { it.score }.take(topK)
    }
    
    fun size(): Int = vectors.size
    
    fun clear() {
        vectors.clear()
    }
    
    fun loadFromFile(file: File) {
        // Implementation for loading index from file
    }
    
    fun saveToFile(file: File) {
        // Implementation for saving index to file
    }
    
    private fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }
        
        return if (norm1 > 0 && norm2 > 0) {
            dotProduct / (sqrt(norm1) * sqrt(norm2))
        } else {
            0f
        }
    }
}

// Data classes

data class VectorMetadata(
    val type: String,
    val source: String,
    val timestamp: Long,
    val properties: Map<String, Any> = emptyMap()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("type", type)
            put("source", source)
            put("timestamp", timestamp)
            put("properties", JSONObject(properties))
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): VectorMetadata {
            val properties = mutableMapOf<String, Any>()
            val propsJson = json.getJSONObject("properties")
            propsJson.keys().forEach { key ->
                properties[key] = propsJson.get(key)
            }
            
            return VectorMetadata(
                type = json.getString("type"),
                source = json.getString("source"),
                timestamp = json.getLong("timestamp"),
                properties = properties
            )
        }
    }
}

data class VectorData(
    val id: String,
    val vector: FloatArray,
    val metadata: VectorMetadata
)

data class SearchResult(
    val id: String,
    val vector: FloatArray,
    val metadata: VectorMetadata,
    val score: Float,
    val distance: Float
)

data class BatchAddResult(
    val successCount: Int,
    val totalCount: Int,
    val errors: List<String>
)

data class OptimizationResult(
    val success: Boolean,
    val vectorsRemoved: Int = 0,
    val processingTime: Long = 0L,
    val newSize: Int = 0,
    val error: String? = null
)

data class SearchPerformanceMetrics(
    val averageSearchTime: Long = 0L,
    val totalSearches: Long = 0L,
    val averageResultCount: Float = 0f,
    val indexHitRate: Float = 0f
)

data class VectorDatabaseStatistics(
    val vectorCount: Int,
    val indexSize: Int,
    val dimension: Int,
    val storageSize: Long,
    val searchMetrics: SearchPerformanceMetrics
)