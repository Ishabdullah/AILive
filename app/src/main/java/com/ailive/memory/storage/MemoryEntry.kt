package com.ailive.memory.storage

import java.util.UUID

/**
 * Represents a single memory entry in AILive's long-term memory.
 */
data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val contentType: ContentType,
    val embedding: FloatArray,
    val timestamp: Long = System.currentTimeMillis(),
    val importance: Float = 0.5f,
    val tags: Set<String> = emptySet(),
    val metadata: Map<String, String> = emptyMap(),
    val accessCount: Int = 0,
    val lastAccessed: Long = timestamp
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MemoryEntry

        if (id != other.id) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
    
    /**
     * Create a copy with updated access stats.
     */
    fun withAccessUpdate(): MemoryEntry {
        return copy(
            accessCount = accessCount + 1,
            lastAccessed = System.currentTimeMillis()
        )
    }
}

enum class ContentType {
    TEXT,
    IMAGE_DESCRIPTION,
    AUDIO_TRANSCRIPT,
    ACTION_LOG,
    GOAL_RECORD,
    CONVERSATION,
    UNKNOWN
}

/**
 * Result of a similarity search.
 */
data class SearchResult(
    val entry: MemoryEntry,
    val similarity: Float,
    val distance: Float = 1.0f - similarity
) : Comparable<SearchResult> {
    override fun compareTo(other: SearchResult): Int {
        return other.similarity.compareTo(this.similarity)
    }
}
