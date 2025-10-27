package com.ailive.memory.storage

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Handles persistence of memory entries to disk.
 * Uses JSON for simplicity (future: migrate to SQLite with sqlite-vec).
 */
class MemoryStore(private val context: Context) {
    private val TAG = "MemoryStore"
    
    private val storageDir = File(context.filesDir, "memory")
    private val entriesFile = File(storageDir, "entries.json")
    private val metadataFile = File(storageDir, "metadata.json")
    
    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
            Log.i(TAG, "Created memory storage directory: ${storageDir.absolutePath}")
        }
    }
    
    /**
     * Save all entries to disk.
     */
    suspend fun saveAll(entries: List<MemoryEntry>) = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()
            
            entries.forEach { entry ->
                val jsonEntry = JSONObject().apply {
                    put("id", entry.id)
                    put("content", entry.content)
                    put("contentType", entry.contentType.name)
                    put("embedding", JSONArray(entry.embedding.toList()))
                    put("timestamp", entry.timestamp)
                    put("importance", entry.importance)
                    put("tags", JSONArray(entry.tags.toList()))
                    put("metadata", JSONObject(entry.metadata))
                    put("accessCount", entry.accessCount)
                    put("lastAccessed", entry.lastAccessed)
                }
                jsonArray.put(jsonEntry)
            }
            
            entriesFile.writeText(jsonArray.toString())
            Log.i(TAG, "Saved ${entries.size} entries to disk")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving entries", e)
        }
    }
    
    /**
     * Load all entries from disk.
     */
    suspend fun loadAll(): List<MemoryEntry> = withContext(Dispatchers.IO) {
        if (!entriesFile.exists()) {
            Log.d(TAG, "No saved entries found")
            return@withContext emptyList()
        }
        
        try {
            val jsonText = entriesFile.readText()
            val jsonArray = JSONArray(jsonText)
            val entries = mutableListOf<MemoryEntry>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonEntry = jsonArray.getJSONObject(i)
                
                val embeddingArray = jsonEntry.getJSONArray("embedding")
                val embedding = FloatArray(embeddingArray.length()) { idx ->
                    embeddingArray.getDouble(idx).toFloat()
                }
                
                val tagsArray = jsonEntry.getJSONArray("tags")
                val tags = mutableSetOf<String>()
                for (j in 0 until tagsArray.length()) {
                    tags.add(tagsArray.getString(j))
                }
                
                val metadataJson = jsonEntry.getJSONObject("metadata")
                val metadata = mutableMapOf<String, String>()
                metadataJson.keys().forEach { key ->
                    metadata[key] = metadataJson.getString(key)
                }
                
                val entry = MemoryEntry(
                    id = jsonEntry.getString("id"),
                    content = jsonEntry.getString("content"),
                    contentType = ContentType.valueOf(jsonEntry.getString("contentType")),
                    embedding = embedding,
                    timestamp = jsonEntry.getLong("timestamp"),
                    importance = jsonEntry.getDouble("importance").toFloat(),
                    tags = tags,
                    metadata = metadata,
                    accessCount = jsonEntry.getInt("accessCount"),
                    lastAccessed = jsonEntry.getLong("lastAccessed")
                )
                
                entries.add(entry)
            }
            
            Log.i(TAG, "Loaded ${entries.size} entries from disk")
            return@withContext entries
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading entries", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Delete all stored data.
     */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        entriesFile.delete()
        metadataFile.delete()
        Log.w(TAG, "Deleted all stored memories")
    }
    
    /**
     * Get storage directory path.
     */
    fun getStoragePath(): String = storageDir.absolutePath
}
