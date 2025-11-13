package com.ailive.personality.tools

import android.content.Context
import android.util.Log
import com.ailive.memory.MemoryAI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * MemoryRetrievalTool - Retrieves stored memories and conversation history
 *
 * Converted from MemoryAI agent to a tool for PersonalityEngine.
 * Provides memory storage/retrieval capabilities with actual on-device storage.
 *
 * Phase 5: Enhanced with JSON-based storage for lightweight on-device memory
 *
 * ⚠️ CRITICAL ISSUE: DUPLICATE MEMORY SYSTEM
 * ================================
 * PROBLEM: This tool duplicates functionality of UnifiedMemoryManager
 *          and uses a separate JSON file (memories.json) instead of Room database.
 *
 * EVIDENCE OF DUPLICATION:
 * 1. This file: /data/data/com.ailive/files/memories.json (max 200 entries)
 * 2. MemoryAI: /data/data/com.ailive/files/memory/entries.json (vector-based)
 * 3. UnifiedMemoryManager: Room database (ailive_memory_db)
 * 4. MemoryAI also has VectorDB (in-memory)
 *
 * RESULT: AILive has 3+ separate memory storage systems that don't sync!
 *
 * CURRENT USAGE:
 * - PersonalityEngine uses UnifiedMemoryManager for context (BUT IT'S DROPPED!)
 * - MemoryAI is instantiated but never used in production
 * - This tool is registered but unclear if PersonalityEngine actually calls it
 * - SearchHistoryManager uses its own SharedPreferences for web search history
 *
 * IMPACT:
 * - Memory fragmentation across multiple stores
 * - No single source of truth
 * - Duplication and inconsistency
 * - Wasted storage and confusion
 *
 * RECOMMENDATION:
 * 1. Deprecate MemoryAI's JSON-based storage
 * 2. Deprecate this tool's memories.json
 * 3. Consolidate everything into UnifiedMemoryManager (Room DB)
 * 4. Fix UnifiedPrompt to actually USE memory context
 * 5. Use lightweight LLM for semantic search over Room DB
 *
 * TODO: Consolidate to single memory system ASAP
 * ================================
 */
class MemoryRetrievalTool(
    private val memoryAI: MemoryAI,
    private val context: Context
) : BaseTool() {

    companion object {
        private const val TAG = "MemoryRetrievalTool"
        private const val MEMORY_FILE = "memories.json"
        private const val MAX_MEMORIES = 200  // Keep most recent 200
    }

    override val name: String = "retrieve_memory"

    override val description: String =
        "Retrieves stored memories, conversation history, and user preferences. " +
        "Can search for specific topics or recall recent interactions."

    override val requiresPermissions: Boolean = false

    override suspend fun isAvailable(): Boolean {
        // TODO: Check if memory system is initialized
        return true
    }

    override fun validateParams(params: Map<String, Any>): String? {
        if (!params.containsKey("query")) {
            return "Parameter 'query' is required"
        }

        val query = params["query"] as? String
        if (query.isNullOrBlank()) {
            return "Parameter 'query' cannot be empty"
        }

        return null
    }

    override suspend fun executeInternal(params: Map<String, Any>): ToolResult {
        return withContext(Dispatchers.IO) {
            val query = params["query"] as String
            val limit = (params["limit"] as? Int) ?: 5
            val action = params["action"] as? String ?: "retrieve"

            Log.d(TAG, "Memory action: $action, query: ${query.take(50)}...")

            try {
                when (action) {
                    "store" -> storeMemory(query, params)
                    "retrieve" -> retrieveMemories(query, limit)
                    "clear" -> clearMemories()
                    else -> ToolResult.Failure(
                        error = IllegalArgumentException("Unknown action"),
                        reason = "Action must be 'store', 'retrieve', or 'clear'",
                        recoverable = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process memory: ${e.message}", e)
                ToolResult.Failure(
                    error = e,
                    reason = "Could not access memory storage: ${e.message}",
                    recoverable = true
                )
            }
        }
    }

    /**
     * Retrieve memories matching query
     * Uses simple keyword matching (for lightweight on-device operation)
     */
    private fun retrieveMemories(query: String, limit: Int): ToolResult {
        val allMemories = loadMemories()

        if (allMemories.isEmpty()) {
            return ToolResult.Success(
                data = MemoryResult(
                    query = query,
                    memories = emptyList(),
                    count = 0
                ),
                context = mapOf(
                    "has_memories" to false,
                    "message" to "No memories stored yet. I'll remember our conversations!"
                )
            )
        }

        // Simple keyword-based search
        val queryWords = query.lowercase().split("\\s+".toRegex())
        val matchedMemories = allMemories
            .map { memory ->
                val contentLower = memory.content.lowercase()
                val matchCount = queryWords.count { word -> contentLower.contains(word) }
                val relevance = matchCount.toFloat() / queryWords.size.toFloat()
                memory to relevance
            }
            .filter { it.second > 0 }  // At least one keyword match
            .sortedByDescending { it.second }
            .take(limit)
            .map { (memory, relevance) ->
                memory.copy(relevance = relevance)
            }

        Log.i(TAG, "✓ Retrieved ${matchedMemories.size} memories for '$query'")

        return ToolResult.Success(
            data = MemoryResult(
                query = query,
                memories = matchedMemories,
                count = matchedMemories.size
            ),
            context = mapOf(
                "has_memories" to matchedMemories.isNotEmpty(),
                "memory_count" to matchedMemories.size,
                "total_memories" to allMemories.size
            )
        )
    }

    /**
     * Store new memory
     */
    private fun storeMemory(content: String, params: Map<String, Any>): ToolResult {
        val memories = loadMemories().toMutableList()

        val newMemory = Memory(
            content = content,
            timestamp = System.currentTimeMillis(),
            relevance = 1.0f,
            metadata = params.filterKeys { it != "query" && it != "action" }
        )

        memories.add(newMemory)

        // Keep only recent memories
        while (memories.size > MAX_MEMORIES) {
            memories.removeAt(0)
        }

        saveMemories(memories)

        Log.i(TAG, "✓ Stored memory: ${content.take(50)}...")

        return ToolResult.Success(
            data = mapOf(
                "stored" to true,
                "total_memories" to memories.size
            ),
            context = mapOf(
                "memory_count" to memories.size
            )
        )
    }

    /**
     * Clear all memories
     */
    private fun clearMemories(): ToolResult {
        val file = File(context.filesDir, MEMORY_FILE)
        if (file.exists()) {
            file.delete()
        }

        Log.i(TAG, "✓ Cleared all memories")

        return ToolResult.Success(
            data = mapOf("cleared" to true),
            context = mapOf("memory_count" to 0)
        )
    }

    /**
     * Load memories from storage
     */
    private fun loadMemories(): List<Memory> {
        val file = File(context.filesDir, MEMORY_FILE)
        if (!file.exists()) return emptyList()

        return try {
            val json = file.readText()
            val jsonArray = JSONArray(json)

            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                Memory(
                    content = obj.getString("content"),
                    timestamp = obj.getLong("timestamp"),
                    relevance = obj.optDouble("relevance", 1.0).toFloat(),
                    metadata = parseMetadata(obj.optJSONObject("metadata"))
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load memories", e)
            emptyList()
        }
    }

    /**
     * Save memories to storage
     */
    private fun saveMemories(memories: List<Memory>) {
        val file = File(context.filesDir, MEMORY_FILE)

        try {
            val jsonArray = JSONArray()
            memories.forEach { memory ->
                val obj = JSONObject().apply {
                    put("content", memory.content)
                    put("timestamp", memory.timestamp)
                    put("relevance", memory.relevance.toDouble())
                    put("metadata", JSONObject(memory.metadata))
                }
                jsonArray.put(obj)
            }

            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save memories", e)
        }
    }

    /**
     * Parse metadata from JSON
     */
    private fun parseMetadata(jsonObject: JSONObject?): Map<String, Any> {
        if (jsonObject == null) return emptyMap()

        val map = mutableMapOf<String, Any>()
        jsonObject.keys().forEach { key ->
            map[key] = jsonObject.get(key)
        }
        return map
    }


    /**
     * Result of memory retrieval
     */
    data class MemoryResult(
        val query: String,
        val memories: List<Memory>,
        val count: Int
    )

    /**
     * Individual memory entry
     */
    data class Memory(
        val content: String,
        val timestamp: Long,
        val relevance: Float,  // 0-1 similarity score
        val metadata: Map<String, Any> = emptyMap()
    )
}
