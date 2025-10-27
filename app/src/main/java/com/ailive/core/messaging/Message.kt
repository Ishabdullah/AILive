package com.ailive.core.messaging

import com.ailive.core.types.AgentType
import java.util.UUID

sealed class AIMessage {
    abstract val id: String
    abstract val source: AgentType
    abstract val timestamp: Long
    abstract val priority: MessagePriority
    abstract val ttl: Long
    
    companion object {
        @JvmStatic
        fun now(): Long = java.lang.System.currentTimeMillis()
    }

    sealed class Perception : AIMessage() {
        data class VisualDetection(
            override val id: String = UUID.randomUUID().toString(),
            override val source: AgentType = AgentType.VISUAL_AI,
            override val timestamp: Long = AIMessage.now(),
            override val priority: MessagePriority = MessagePriority.HIGH,
            override val ttl: Long = 5000,
            val objects: List<DetectedObject>,
            val imageEmbeddingId: String?,
            val confidence: Float
        ) : Perception()

        data class AudioTranscript(
            override val id: String = UUID.randomUUID().toString(),
            override val source: AgentType = AgentType.LANGUAGE_AI,
            override val timestamp: Long = AIMessage.now(),
            override val priority: MessagePriority = MessagePriority.NORMAL,
            override val ttl: Long = 10000,
            val transcript: String,
            val language: String,
            val confidence: Float
        ) : Perception()

        data class EmotionVector(
            override val id: String = UUID.randomUUID().toString(),
            override val source: AgentType = AgentType.EMOTION_AI,
            override val timestamp: Long = AIMessage.now(),
            override val priority: MessagePriority = MessagePriority.NORMAL,
            override val ttl: Long = 3000,
            val valence: Float,
            val arousal: Float,
            val urgency: Float
        ) : Perception()
    }

    sealed class Cognition : AIMessage() {
        data class MemoryStored(
            override val id: String = UUID.randomUUID().toString(),
            override val source: AgentType = AgentType.MEMORY_AI,
            override val timestamp: Long = AIMessage.now(),
            override val priority: MessagePriority = MessagePriority.LOW,
            override val ttl: Long = 30000,
            val embeddingId: String,
            val contentType: com.ailive.memory.storage.ContentType,
            val metadata: Map<String, String>
        ) : Cognition()

        data class MemoryRecalled(
            override val id: String = UUID.randomUUID().toString(),
            override val source: AgentType = AgentType.MEMORY_AI,
            override val timestamp: Long = AIMessage.now(),
            override val priority: MessagePriority = MessagePriority.NORMAL,
            override val ttl: Long = 15000,
            val query: String,
            val results: List<com.ailive.memory.storage.MemoryEntry>,
            val topKSimilarity: Float
        ) : Cognition()

