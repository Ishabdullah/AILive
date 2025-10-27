package com.ailive.memory

import android.content.Context
import android.util.Log
import com.ailive.core.messaging.*
import com.ailive.core.state.StateManager
import com.ailive.core.types.AgentType
import com.ailive.memory.embeddings.TextEmbedder
import com.ailive.memory.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/**
 * Memory AI - AILive's hippocampus for storing and recalling memories.
 */
class MemoryAI(
    private val context: Context,
    private val messageBus: MessageBus,
    private val stateManager: StateManager
) {
    private val TAG = "MemoryAI"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val vectorDB = VectorDB(dimensions = 384, maxEntries = 50000)
    private val memoryStore = MemoryStore(context)
    private val embedder = TextEmbedder(dimensions = 384)
    
    private var isRunning = false
    private var autoSaveJob: Job? = null
    
    /**
     * Start Memory AI.
     */
    fun start() {
        if (isRunning) return
        
        isRunning = true
        Log.i(TAG, "Memory AI starting...")
        
        scope.launch {
            loadMemoriesFromDisk()
        }
        
        subscribeToMessages()
        
        autoSaveJob = scope.launch {
            autoSaveLoop()
        }
        
        scope.launch {
            messageBus.publish(
                AIMessage.System.AgentStarted(source = AgentType.MEMORY_AI)
            )
        }
        
        Log.i(TAG, "Memory AI started")
    }
    
    /**
     * Stop Memory AI.
     */
    fun stop() {
        if (!isRunning) return
        
        Log.i(TAG, "Memory AI stopping...")
        
        runBlocking {
            saveMemoriesToDisk()
        }
        
        autoSaveJob?.cancel()
        scope.cancel()
        isRunning = false
        
        Log.i(TAG, "Memory AI stopped")
    }
    
    /**
     * Store a memory.
     */
    suspend fun store(
        content: String,
        contentType: ContentType = ContentType.TEXT,
        importance: Float = 0.5f,
        tags: Set<String> = emptySet(),
        metadata: Map<String, String> = emptyMap()
    ): String {
        val embedding = embedder.embed(content)
        
        val entry = MemoryEntry(
            content = content,
            contentType = contentType,
            embedding = embedding,
            importance = importance,
            tags = tags,
            metadata = metadata
        )
        
        val success = vectorDB.insert(entry)
        
        if (success) {
            messageBus.publish(
                AIMessage.Cognition.MemoryStored(
                    embeddingId = entry.id,
                    contentType = contentType,
                    metadata = metadata
                )
            )
            
            stateManager.updateCognition { cognition ->
                cognition.copy(
                    recentEmbeddings = (cognition.recentEmbeddings + entry.id).takeLast(100)
                )
            }
            
            Log.d(TAG, "Stored memory: ${entry.id}")
        }
        
        return entry.id
    }
    
    /**
     * Recall memories similar to a query.
     */
    suspend fun recall(
        query: String,
        k: Int = 10,
        minSimilarity: Float = 0.5f,
        contentTypeFilter: ContentType? = null
    ): List<SearchResult> {
        val queryEmbedding = embedder.embed(query)
        
        val filter: ((MemoryEntry) -> Boolean)? = contentTypeFilter?.let { type ->
            { entry -> entry.contentType == type }
        }
        
        val results = vectorDB.search(
            queryEmbedding = queryEmbedding,
            k = k,
            minSimilarity = minSimilarity,
            filter = filter
        )
        
        if (results.isNotEmpty()) {
            messageBus.publish(
                AIMessage.Cognition.MemoryRecalled(
                    query = query,
                    results = results.map { result ->
                        com.ailive.core.messaging.MemoryResult(
                            embeddingId = result.entry.id,
                            similarity = result.similarity,
                            content = result.entry.content,
                            timestamp = result.entry.timestamp
                        )
                    },
                    topKSimilarity = results.firstOrNull()?.similarity ?: 0f
                )
            )
        }
        
        Log.d(TAG, "Recalled ${results.size} memories for query: $query")
        return results
    }
    
    /**
     * Get memory by ID.
     */
    suspend fun get(id: String): MemoryEntry? {
        return vectorDB.get(id)
    }
    
    /**
     * Delete memory by ID.
     */
    suspend fun delete(id: String): Boolean {
        return vectorDB.delete(id)
    }
    
    /**
     * Clear all memories.
     */
    suspend fun clearAll() {
        vectorDB.clear()
        memoryStore.deleteAll()
        Log.w(TAG, "Cleared all memories")
    }
    
    /**
     * Subscribe to messages from other agents.
     */
    private fun subscribeToMessages() {
        scope.launch {
            messageBus.subscribe(AIMessage.Perception.AudioTranscript::class.java)
                .collect { transcript ->
                    store(
                        content = transcript.transcript,
                        contentType = ContentType.AUDIO_TRANSCRIPT,
                        importance = 0.7f,
                        tags = setOf("audio", "perception"),
                        metadata = mapOf("language" to transcript.language)
                    )
                }
        }
        
        scope.launch {
            messageBus.subscribe(AIMessage.Control.GoalSet::class.java)
                .collect { goal ->
                    store(
                        content = goal.goal,
                        contentType = ContentType.GOAL_RECORD,
                        importance = 0.9f,
                        tags = setOf("goal", "planning"),
                        metadata = mapOf("deadline" to (goal.deadline?.toString() ?: "none"))
                    )
                }
        }
    }
    
    /**
     * Load memories from disk on startup.
     */
    private suspend fun loadMemoriesFromDisk() {
        val entries = memoryStore.loadAll()
        entries.forEach { entry ->
            vectorDB.insert(entry)
        }
        Log.i(TAG, "Loaded ${entries.size} memories from disk")
    }
    
    /**
     * Save memories to disk periodically.
     */
    private suspend fun autoSaveLoop() {
        while (isRunning) {
            delay(60000) // Save every minute
            saveMemoriesToDisk()
        }
    }
    
    /**
     * Save all memories to disk.
     */
    private suspend fun saveMemoriesToDisk() {
        val allEntries = vectorDB.filter { true }
        memoryStore.saveAll(allEntries)
        Log.d(TAG, "Auto-saved ${allEntries.size} memories")
    }
    
    /**
     * Get statistics.
     */
    suspend fun getStats(): MemoryStats {
        val dbStats = vectorDB.getStats()
        return MemoryStats(
            totalMemories = dbStats.totalEntries,
            memoryUsageMB = dbStats.memoryUsageMB,
            totalSearches = dbStats.totalSearches,
            avgAccessCount = dbStats.avgAccessCount,
            storagePath = memoryStore.getStoragePath()
        )
    }
}

data class MemoryStats(
    val totalMemories: Int,
    val memoryUsageMB: Float,
    val totalSearches: Long,
    val avgAccessCount: Double,
    val storagePath: String
)
