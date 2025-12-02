package com.ailive.ai.memory

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue

/**
 * Memory Consolidation Manager
 * Implements advanced memory consolidation, organization, and retrieval for AI systems
 */
class MemoryConsolidationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "MemoryConsolidationManager"
        private const val MEMORY_BANKS_FILE = "memory_banks.json"
        private const val CONsolidation_LOG_FILE = "consolidation_log.json"
        private const val CONSOLIDATION_INTERVAL = 24 * 60 * 60 * 1000L // 24 hours
        private const val MAX_MEMORY_BANK_SIZE = 10000
        private const val CONSOLIDATION_THRESHOLD = 100 // Minimum memories to trigger consolidation
    }
    
    // Memory storage systems
    private val shortTermMemory = PriorityBlockingQueue<Memory>(11, compareByDescending { it.importance })
    private val longTermMemory = ConcurrentHashMap<String, Memory>()
    private val memoryBanks = ConcurrentHashMap<String, MemoryBank>()
    private val workingMemory = WorkingMemory()
    
    // Consolidation state
    private val _isConsolidating = MutableStateFlow(false)
    val isConsolidating: StateFlow<Boolean> = _isConsolidating.asStateFlow()
    
    private val _lastConsolidationTime = MutableStateFlow(0L)
    val lastConsolidationTime: StateFlow<Long> = _lastConsolidationTime.asStateFlow()
    
    private val _consolidationProgress = MutableStateFlow(0f)
    val consolidationProgress: StateFlow<Float> = _consolidationProgress.asStateFlow()
    
    // Memory statistics
    private val _memoryStats = MutableStateFlow(MemoryStatistics())
    val memoryStats: StateFlow<MemoryStatistics> = _memoryStats.asStateFlow()
    
    // Configuration
    private val consolidationStrategies = mapOf(
        ConsolidationStrategy.TEMPORAL to TemporalConsolidation(),
        ConsolidationStrategy.SEMANTIC to SemanticConsolidation(),
        ConsolidationStrategy.IMPORTANCE to ImportanceBasedConsolidation(),
        ConsolidationStrategy.FREQUENCY to FrequencyBasedConsolidation()
    )
    
    // File paths
    private val memoryDir = File(context.filesDir, "memory_consolidation")
    private val memoryBanksFile = File(memoryDir, MEMORY_BANKS_FILE)
    private val consolidationLogFile = File(memoryDir, CONSOLIDation_LOG_FILE)
    
    /**
     * Initialize the memory consolidation manager
     */
    suspend fun initialize(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Create directories if they don't exist
                if (!memoryDir.exists()) {
                    memoryDir.mkdirs()
                }
                
                // Load existing memory banks
                if (memoryBanksFile.exists()) {
                    loadMemoryBanks()
                } else {
                    initializeMemoryBanks()
                }
                
                // Load consolidation log
                if (consolidationLogFile.exists()) {
                    loadConsolidationLog()
                }
                
                // Initialize working memory
                workingMemory.initialize()
                
                // Start consolidation scheduler
                scheduleConsolidation()
                
                updateMemoryStatistics()
                true
                
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Add a new memory to the system
     */
    suspend fun addMemory(
        content: String,
        type: MemoryType,
        importance: Float = 0.5f,
        context: Map<String, Any> = emptyMap(),
        tags: Set<String> = emptySet()
    ): String {
        val memory = Memory(
            id = generateMemoryId(),
            content = content,
            type = type,
            importance = importance,
            timestamp = System.currentTimeMillis(),
            context = context,
            tags = tags,
            accessCount = 0,
            lastAccessed = System.currentTimeMillis()
        )
        
        // Add to short-term memory
        shortTermMemory.offer(memory)
        
        // Add to working memory for immediate access
        workingMemory.addMemory(memory)
        
        // Update statistics
        updateMemoryStatistics()
        
        // Check if consolidation is needed
        checkConsolidationNeeded()
        
        return memory.id
    }
    
    /**
     * Retrieve memories based on query
     */
    suspend fun retrieveMemories(
        query: String,
        memoryType: MemoryType? = null,
        limit: Int = 20,
        minImportance: Float = 0.1f
    ): List<Memory> {
        return withContext(Dispatchers.Default) {
            val results = mutableListOf<Memory>()
            
            // Search working memory first (fastest)
            results.addAll(workingMemory.search(query, memoryType, minImportance))
            
            // Search short-term memory
            shortTermMemory.filter { memory ->
                (memoryType == null || memory.type == memoryType) &&
                memory.importance >= minImportance &&
                memory.content.contains(query, ignoreCase = true)
            }.take(limit - results.size).forEach { memory ->
                memory.accessCount++
                memory.lastAccessed = System.currentTimeMillis()
                results.add(memory)
            }
            
            // Search long-term memory
            if (results.size < limit) {
                longTermMemory.values.filter { memory ->
                    (memoryType == null || memory.type == memoryType) &&
                    memory.importance >= minImportance &&
                    memory.content.contains(query, ignoreCase = true)
                }.sortedByDescending { it.relevanceScore }
                 .take(limit - results.size)
                 .forEach { memory ->
                     memory.accessCount++
                     memory.lastAccessed = System.currentTimeMillis()
                     results.add(memory)
                 }
            }
            
            results.take(limit)
        }
    }
    
    /**
     * Perform memory consolidation
     */
    suspend fun performConsolidation(
        strategy: ConsolidationStrategy = ConsolidationStrategy.TEMPORAL
    ): ConsolidationResult {
        return withContext(Dispatchers.Default) {
            if (_isConsolidating.value) {
                return@withContext ConsolidationResult(
                    success = false,
                    error = "Consolidation already in progress"
                )
            }
            
            _isConsolidating.value = true
            _consolidationProgress.value = 0f
            
            try {
                val startTime = System.currentTimeMillis()
                val consolidator = consolidationStrategies[strategy] ?: return@withContext ConsolidationResult(
                    success = false,
                    error = "Unknown consolidation strategy"
                )
                
                // Extract memories from short-term memory
                val memoriesToConsolidate = mutableListOf<Memory>()
                while (shortTermMemory.isNotEmpty() && memoriesToConsolidate.size < CONSOLIDATION_THRESHOLD) {
                    shortTermMemory.poll()?.let { memory ->
                        memoriesToConsolidate.add(memory)
                    }
                }
                
                if (memoriesToConsolidate.isEmpty()) {
                    _isConsolidating.value = false
                    return@withContext ConsolidationResult(
                        success = true,
                        memoriesProcessed = 0,
                        memoriesConsolidated = 0
                    )
                }
                
                // Perform consolidation
                _consolidationProgress.value = 0.2f
                val consolidationResult = consolidator.consolidate(memoriesToConsolidate, memoryBanks)
                
                // Move consolidated memories to long-term storage
                _consolidationProgress.value = 0.6f
                consolidationResult.consolidatedMemories.forEach { memory ->
                    longTermMemory[memory.id] = memory
                }
                
                // Update memory banks
                _consolidationProgress.value = 0.8f
                saveMemoryBanks()
                
                // Log consolidation
                _consolidationProgress.value = 0.9f
                logConsolidation(strategy, consolidationResult)
                
                val processingTime = System.currentTimeMillis() - startTime
                _lastConsolidationTime.value = System.currentTimeMillis()
                _consolidationProgress.value = 1.0f
                
                updateMemoryStatistics()
                
                ConsolidationResult(
                    success = true,
                    memoriesProcessed = memoriesToConsolidate.size,
                    memoriesConsolidated = consolidationResult.consolidatedMemories.size,
                    processingTime = processingTime,
                    strategy = strategy
                )
                
            } catch (e: Exception) {
                ConsolidationResult(
                    success = false,
                    error = e.message
                )
            } finally {
                _isConsolidating.value = false
                _consolidationProgress.value = 0f
            }
        }
    }
    
    /**
     * Create a new memory bank
     */
    suspend fun createMemoryBank(
        name: String,
        type: MemoryBankType,
        description: String = ""
    ): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                val memoryBank = MemoryBank(
                    id = generateBankId(),
                    name = name,
                    type = type,
                    description = description,
                    createdAt = System.currentTimeMillis(),
                    memoryIds = mutableSetOf(),
                    maxSize = MAX_MEMORY_BANK_SIZE
                )
                
                memoryBanks[memoryBank.id] = memoryBank
                saveMemoryBanks()
                true
                
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Get memories from a specific memory bank
     */
    suspend fun getMemoriesFromBank(bankId: String): List<Memory> {
        return withContext(Dispatchers.Default) {
            val bank = memoryBanks[bankId] ?: return@withContext emptyList()
            
            bank.memoryIds.mapNotNull { memoryId ->
                longTermMemory[memoryId]
            }.sortedByDescending { it.timestamp }
        }
    }
    
    /**
     * Update memory importance based on usage patterns
     */
    suspend fun updateMemoryImportance() {
        withContext(Dispatchers.Default) {
            val currentTime = System.currentTimeMillis()
            
            // Update short-term memory importance
            shortTermMemory.forEach { memory ->
                val timeSinceLastAccess = currentTime - memory.lastAccessed
                val frequencyFactor = memory.accessCount.toFloat() / (currentTime - memory.timestamp + 1)
                val recencyFactor = 1.0f / (timeSinceLastAccess + 1)
                
                memory.importance = (memory.importance * 0.7f) + (frequencyFactor * 0.2f + recencyFactor * 0.1f)
            }
            
            // Update long-term memory importance
            longTermMemory.values.forEach { memory ->
                val timeSinceLastAccess = currentTime - memory.lastAccessed
                val frequencyFactor = memory.accessCount.toFloat() / (currentTime - memory.timestamp + 1)
                val recencyFactor = 1.0f / (timeSinceLastAccess + 1)
                
                memory.importance = (memory.importance * 0.8f) + (frequencyFactor * 0.15f + recencyFactor * 0.05f)
            }
            
            // Reorder short-term memory based on new importance
            val reorderedMemories = shortTermMemory.sortedByDescending { it.importance }
            shortTermMemory.clear()
            shortTermMemory.addAll(reorderedMemories)
        }
    }
    
    /**
     * Get memory statistics
     */
    fun getMemoryStatistics(): MemoryStatistics {
        return _memoryStats.value
    }
    
    /**
     * Export memory data for backup or analysis
     */
    suspend fun exportMemoryData(): MemoryExportData {
        return withContext(Dispatchers.IO) {
            MemoryExportData(
                shortTermMemories = shortTermMemory.toList(),
                longTermMemories = longTermMemory.values.toList(),
                memoryBanks = memoryBanks.values.toList(),
                workingMemories = workingMemory.getMemories(),
                exportTimestamp = System.currentTimeMillis()
            )
        }
    }
    
    // Private helper methods
    
    private fun loadMemoryBanks() {
        try {
            val banksJson = JSONArray(memoryBanksFile.readText())
            for (i in 0 until banksJson.length()) {
                val bankJson = banksJson.getJSONObject(i)
                val memoryBank = MemoryBank.fromJson(bankJson)
                memoryBanks[memoryBank.id] = memoryBank
            }
        } catch (e: Exception) {
            initializeMemoryBanks()
        }
    }
    
    private fun initializeMemoryBanks() {
        // Create default memory banks
        createMemoryBank("episodic", MemoryBankType.EPISODIC, "Personal experiences and events")
        createMemoryBank("semantic", MemoryBankType.SEMANTIC, "Facts and general knowledge")
        createMemoryBank("procedural", MemoryBankType.PROCEDURAL, "Skills and procedures")
        createMemoryBank("working", MemoryBankType.WORKING, "Temporary working memory")
    }
    
    private fun saveMemoryBanks() {
        try {
            val banksJson = JSONArray()
            memoryBanks.values.forEach { bank ->
                banksJson.put(bank.toJson())
            }
            memoryBanksFile.writeText(banksJson.toString())
        } catch (e: Exception) {
            // Handle save error
        }
    }
    
    private fun loadConsolidationLog() {
        try {
            val logJson = JSONArray(consolidationLogFile.readText())
            // Load consolidation history for analytics
        } catch (e: Exception) {
            // Handle load error
        }
    }
    
    private fun logConsolidation(strategy: ConsolidationStrategy, result: ConsolidationResult) {
        try {
            val logEntry = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("strategy", strategy.name)
                put("memoriesProcessed", result.memoriesProcessed)
                put("memoriesConsolidated", result.memoriesConsolidated)
                put("processingTime", result.processingTime)
                put("success", result.success)
            }
            
            // Append to log file
            val currentLog = if (consolidationLogFile.exists()) {
                JSONArray(consolidationLogFile.readText())
            } else {
                JSONArray()
            }
            
            currentLog.put(logEntry)
            consolidationLogFile.writeText(currentLog.toString())
            
        } catch (e: Exception) {
            // Handle logging error
        }
    }
    
    private fun checkConsolidationNeeded() {
        val totalMemories = shortTermMemory.size + longTermMemory.size
        val timeSinceLastConsolidation = System.currentTimeMillis() - _lastConsolidationTime.value
        
        if (totalMemories >= CONSOLIDATION_THRESHOLD || 
            timeSinceLastConsolidation >= CONSOLIDATION_INTERVAL) {
            // Trigger consolidation in background
            kotlinx.coroutines.GlobalScope.launch {
                performConsolidation()
            }
        }
    }
    
    private fun scheduleConsolidation() {
        // Schedule periodic consolidation
        kotlinx.coroutines.GlobalScope.launch {
            while (true) {
                kotlinx.coroutines.delay(CONSOLIDATION_INTERVAL)
                performConsolidation()
            }
        }
    }
    
    private fun updateMemoryStatistics() {
        _memoryStats.value = MemoryStatistics(
            shortTermMemoryCount = shortTermMemory.size,
            longTermMemoryCount = longTermMemory.size,
            workingMemoryCount = workingMemory.size(),
            memoryBankCount = memoryBanks.size,
            lastConsolidationTime = _lastConsolidationTime.value,
            averageMemoryImportance = calculateAverageImportance()
        )
    }
    
    private fun calculateAverageImportance(): Float {
        val allMemories = shortTermMemory.toList() + longTermMemory.values.toList()
        return if (allMemories.isNotEmpty()) {
            allMemories.map { it.importance }.average().toFloat()
        } else {
            0f
        }
    }
    
    private fun generateMemoryId(): String {
        return "memory_${System.currentTimeMillis()}_${(0..999).random()}"
    }
    
    private fun generateBankId(): String {
        return "bank_${System.currentTimeMillis()}_${(0..999).random()}"
    }
}

// Working memory implementation
class WorkingMemory {
    private val memories = ConcurrentHashMap<String, Memory>()
    private val maxWorkingMemorySize = 50
    
    fun initialize() {
        // Initialize working memory
    }
    
    fun addMemory(memory: Memory) {
        if (memories.size >= maxWorkingMemorySize) {
            // Remove least important memory
            val leastImportant = memories.values.minByOrNull { it.importance }
            leastImportant?.let { memories.remove(it.id) }
        }
        
        memories[memory.id] = memory
    }
    
    fun search(
        query: String,
        memoryType: MemoryType? = null,
        minImportance: Float = 0.1f
    ): List<Memory> {
        return memories.values.filter { memory ->
            (memoryType == null || memory.type == memoryType) &&
            memory.importance >= minImportance &&
            memory.content.contains(query, ignoreCase = true)
        }.sortedByDescending { it.importance }
    }
    
    fun size(): Int = memories.size
    
    fun getMemories(): List<Memory> = memories.values.toList()
}

// Data classes and enums

enum class MemoryType {
    EPISODIC,
    SEMANTIC,
    PROCEDURAL,
    WORKING,
    SENSORY,
    LONG_TERM,
    SHORT_TERM
}

enum class MemoryBankType {
    EPISODIC,
    SEMANTIC,
    PROCEDURAL,
    WORKING,
    SENSORY,
    CUSTOM
}

enum class ConsolidationStrategy {
    TEMPORAL,
    SEMANTIC,
    IMPORTANCE,
    FREQUENCY,
    HYBRID
}

data class Memory(
    val id: String,
    var content: String,
    val type: MemoryType,
    var importance: Float,
    val timestamp: Long,
    val context: Map<String, Any>,
    val tags: Set<String>,
    var accessCount: Int,
    var lastAccessed: Long,
    var relevanceScore: Float = 0.5f,
    val embeddings: FloatArray? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("content", content)
            put("type", type.name)
            put("importance", importance)
            put("timestamp", timestamp)
            put("context", JSONObject(context))
            put("tags", JSONArray(tags.toList()))
            put("accessCount", accessCount)
            put("lastAccessed", lastAccessed)
            put("relevanceScore", relevanceScore)
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): Memory {
            val context = mutableMapOf<String, Any>()
            val contextJson = json.getJSONObject("context")
            contextJson.keys().forEach { key ->
                context[key] = contextJson.get(key)
            }
            
            val tags = mutableSetOf<String>()
            val tagsJson = json.getJSONArray("tags")
            for (i in 0 until tagsJson.length()) {
                tags.add(tagsJson.getString(i))
            }
            
            return Memory(
                id = json.getString("id"),
                content = json.getString("content"),
                type = MemoryType.valueOf(json.getString("type")),
                importance = json.getDouble("importance").toFloat(),
                timestamp = json.getLong("timestamp"),
                context = context,
                tags = tags,
                accessCount = json.getInt("accessCount"),
                lastAccessed = json.getLong("lastAccessed"),
                relevanceScore = json.getDouble("relevanceScore").toFloat()
            )
        }
    }
}

data class MemoryBank(
    val id: String,
    val name: String,
    val type: MemoryBankType,
    val description: String,
    val createdAt: Long,
    val memoryIds: MutableSet<String>,
    val maxSize: Int
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("type", type.name)
            put("description", description)
            put("createdAt", createdAt)
            put("memoryIds", JSONArray(memoryIds.toList()))
            put("maxSize", maxSize)
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): MemoryBank {
            val memoryIds = mutableSetOf<String>()
            val idsJson = json.getJSONArray("memoryIds")
            for (i in 0 until idsJson.length()) {
                memoryIds.add(idsJson.getString(i))
            }
            
            return MemoryBank(
                id = json.getString("id"),
                name = json.getString("name"),
                type = MemoryBankType.valueOf(json.getString("type")),
                description = json.getString("description"),
                createdAt = json.getLong("createdAt"),
                memoryIds = memoryIds,
                maxSize = json.getInt("maxSize")
            )
        }
    }
}

data class ConsolidationResult(
    val success: Boolean,
    val memoriesProcessed: Int = 0,
    val memoriesConsolidated: Int = 0,
    val processingTime: Long = 0L,
    val strategy: ConsolidationStrategy? = null,
    val consolidatedMemories: List<Memory> = emptyList(),
    val error: String? = null
)

data class MemoryStatistics(
    val shortTermMemoryCount: Int = 0,
    val longTermMemoryCount: Int = 0,
    val workingMemoryCount: Int = 0,
    val memoryBankCount: Int = 0,
    val lastConsolidationTime: Long = 0L,
    val averageMemoryImportance: Float = 0f
)

data class MemoryExportData(
    val shortTermMemories: List<Memory>,
    val longTermMemories: List<Memory>,
    val memoryBanks: List<MemoryBank>,
    val workingMemories: List<Memory>,
    val exportTimestamp: Long
)

// Consolidation strategy interfaces

interface ConsolidationStrategy {
    suspend fun consolidate(
        memories: List<Memory>,
        memoryBanks: ConcurrentHashMap<String, MemoryBank>
    ): ConsolidationResult
}

class TemporalConsolidation : ConsolidationStrategy {
    override suspend fun consolidate(
        memories: List<Memory>,
        memoryBanks: ConcurrentHashMap<String, MemoryBank>
    ): ConsolidationResult {
        // Implement temporal consolidation logic
        val consolidated = memories.groupBy { 
            // Group by time windows (hourly, daily, etc.)
            (it.timestamp / (24 * 60 * 60 * 1000L)) // Group by day
        }.flatMap { (_, dayMemories) ->
            // Consolidate memories within each time window
            dayMemories.sortedByDescending { it.importance }.take(10)
        }
        
        return ConsolidationResult(
            success = true,
            memoriesProcessed = memories.size,
            memoriesConsolidated = consolidated.size,
            consolidatedMemories = consolidated
        )
    }
}

class SemanticConsolidation : ConsolidationStrategy {
    override suspend fun consolidate(
        memories: List<Memory>,
        memoryBanks: ConcurrentHashMap<String, MemoryBank>
    ): ConsolidationResult {
        // Implement semantic consolidation logic
        // Group memories by semantic similarity and create summaries
        val consolidated = memories.sortedByDescending { it.importance }.take(memories.size / 2)
        
        return ConsolidationResult(
            success = true,
            memoriesProcessed = memories.size,
            memoriesConsolidated = consolidated.size,
            consolidatedMemories = consolidated
        )
    }
}

class ImportanceBasedConsolidation : ConsolidationStrategy {
    override suspend fun consolidate(
        memories: List<Memory>,
        memoryBanks: ConcurrentHashMap<String, MemoryBank>
    ): ConsolidationResult {
        // Keep only high-importance memories
        val threshold = 0.5f
        val consolidated = memories.filter { it.importance >= threshold }
        
        return ConsolidationResult(
            success = true,
            memoriesProcessed = memories.size,
            memoriesConsolidated = consolidated.size,
            consolidatedMemories = consolidated
        )
    }
}

class FrequencyBasedConsolidation : ConsolidationStrategy {
    override suspend fun consolidate(
        memories: List<Memory>,
        memoryBanks: ConcurrentHashMap<String, MemoryBank>
    ): ConsolidationResult {
        // Consolidate based on access frequency
        val consolidated = memories.sortedByDescending { 
            it.accessCount * it.importance 
        }.take(memories.size / 2)
        
        return ConsolidationResult(
            success = true,
            memoriesProcessed = memories.size,
            memoriesConsolidated = consolidated.size,
            consolidatedMemories = consolidated
        )
    }
}