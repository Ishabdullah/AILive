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

        data class PredictionGenerated(
            override val id: String = UUID.randomUUID().toString(),
            override val source: AgentType = AgentType.PREDICTIVE_AI,
            override val timestamp: Long = AIMessage.now(),
            override val priority: MessagePriority = MessagePriority.NORMAL,
            override val ttl: Long = 8000,
            val action: ActionType,
            val expectedReward: Float,
            val cost: Float
        ) : Cognition()

        data class RewardUpdate(
            override val id: String = UUID.randomUUID().toString(),
            override val source: AgentType = AgentType.REWARD_AI,
            override val timestamp: Long = AIMessage.now(),
            override val priority: MessagePriority = MessagePriority.LOW,
            override val ttl: Long = 20000,
            val actionId: String,
            val rewardValue: Float,
            val learningRate: Float
        ) : Cognition()
    }

    sealed class Control : AIMessage() {
        data class GoalSet(
            override val id: String = UUID.randomUUID().toString(),
            override val source: AgentType = AgentType.META_AI,
            override val timestamp: Long = AIMessage.now(),
            override val priority: MessagePriority = MessagePriority.CRITICAL,
            override val ttl: Long = 60000,
            val goal: String,
            val subGoals: List<String>,
            val deadline: Long?
        ) : Control()

        data class ResourceAllocation(
            override val id: String = UUID.randomUUID().toString(),
            override val source: AgentType = AgentType.META_AI,
            override val timestamp: Long = AIMessage.now(),
            override val priority: MessagePriority = MessagePriority.CRITICAL,
            override val ttl: Long = 5000,
            val targetAgent: AgentType,
            val cpuPercent: Int,
            val memoryMB: Int,
            val gpuAllowed: Boolean
        ) : Control()

        data class ActionRequest(
            override val id: String = UUID.randomUUID().toString(),
            override val source: AgentType,
            override val timestamp: Long = AIMessage.now(),
            override val priority: MessagePriority = MessagePriority.NORMAL,
            override val ttl: Long = 10000,
            val actionType: ActionType,
            val params: Map<String, Any>,
            val expectedFeedback: Map<String, Any?>
        ) : Control()

        data class ActionApproved(
            override val id: String = UUID.randomUUID().toString(),
            override val source: AgentType = AgentType.META_AI,
            override val timestamp: Long = AIMessage.now(),
            override val priority: MessagePriority = MessagePriority.HIGH,
            override val ttl: Long = 3000,
            val requestId: String,
            val approvedParams: Map<String, Any>
        ) : Control()

        data class ActionRejected(
            override val id: String = UUID.randomUUID().toString(),
            override val source: AgentType = AgentType.META_AI,
            override val timestamp: Long = AIMessage.now(),
            override val priority: MessagePriority = MessagePriority.HIGH,
            override val ttl: Long = 3000,
            val requestId: String,
            val reason: String
        ) : Control()
    }

    sealed class Motor : AIMessage() {
        data class ActionExecuted(
            override val id: String = UUID.randomUUID().toString(),
            override val source: AgentType = AgentType.MOTOR_AI,
            override val timestamp: Long = AIMessage.now(),
            override val priority: MessagePriority = MessagePriority.NORMAL,
            override val ttl: Long = 15000,
            val actionId: String,
            val success: Boolean,
            val feedback: Map<String, Any>
        ) : Motor()

        data class SensorUpdate(
            override val id: String = UUID.randomUUID().toString(),
            override val source: AgentType = AgentType.MOTOR_AI,
            override val timestamp: Long = AIMessage.now(),
            override val priority: MessagePriority = MessagePriority.LOW,
            override val ttl: Long = 2000,
            val sensorType: SensorType,
            val value: Any
        ) : Motor()
    }

    sealed class System : AIMessage() {
        data class AgentStarted(
            override val id: String = UUID.randomUUID().toString(),
            override val source: AgentType,
            override val timestamp: Long = AIMessage.now(),
            override val priority: MessagePriority = MessagePriority.NORMAL,
            override val ttl: Long = 5000
        ) : System()

        data class AgentStopped(
            override val id: String = UUID.randomUUID().toString(),
            override val source: AgentType,
            override val timestamp: Long = AIMessage.now(),
            override val priority: MessagePriority = MessagePriority.NORMAL,
            override val ttl: Long = 5000,
            val reason: String
        ) : System()

        data class ErrorOccurred(
            override val id: String = UUID.randomUUID().toString(),
            override val source: AgentType,
            override val timestamp: Long = AIMessage.now(),
            override val priority: MessagePriority = MessagePriority.CRITICAL,
            override val ttl: Long = 30000,
            val error: Throwable,
            val context: String
        ) : System()

        data class SafetyViolation(
            override val id: String = UUID.randomUUID().toString(),
            override val source: AgentType = AgentType.MOTOR_AI,
            override val timestamp: Long = AIMessage.now(),
            override val priority: MessagePriority = MessagePriority.CRITICAL,
            override val ttl: Long = 60000,
            val safetyRule: String,
            val attemptedAction: String
        ) : System()
    }
}

data class DetectedObject(val label: String, val boundingBox: BoundingBox, val confidence: Float)
data class BoundingBox(val x: Int, val y: Int, val width: Int, val height: Int)

enum class SensorType { ACCELEROMETER, GYROSCOPE, GPS, LIGHT, PROXIMITY, BATTERY }

enum class ActionType { 
    CAMERA_CAPTURE, 
    SEND_NOTIFICATION, 
    STORE_MEMORY, 
    UPDATE_UI,
    STORE_DATA,
    QUERY_WEB,
    PLAY_AUDIO
}
