package com.ailive.core.state

import com.ailive.core.messaging.AIMessage
import com.ailive.core.types.AgentType
import java.util.concurrent.ConcurrentHashMap

/**
 * Blackboard pattern implementation for shared state.
 * Thread-safe, immutable reads, structured writes.
 */
data class BlackboardState(
    val sensors: SensorState = SensorState(),
    val perception: PerceptionState = PerceptionState(),
    val cognition: CognitionState = CognitionState(),
    val affect: AffectState = AffectState(),
    val meta: MetaState = MetaState(),
    val motor: MotorState = MotorState()
) {
    data class SensorState(
        val camera: CameraSnapshot? = null,
        val microphone: AudioSnapshot? = null,
        val gps: GPSSnapshot? = null,
        val battery: BatterySnapshot? = null,
        val network: NetworkSnapshot? = null,
        val lastUpdate: Long = System.currentTimeMillis()
    )
    
    data class PerceptionState(
        val visualObjects: List<AIMessage.Perception.VisualDetection> = emptyList(),
        val audioTranscripts: List<AIMessage.Perception.AudioTranscript> = emptyList(),
        val emotionVectors: List<AIMessage.Perception.EmotionVector> = emptyList(),
        val lastUpdate: Long = System.currentTimeMillis()
    )
    
    data class CognitionState(
        val recentEmbeddings: List<String> = emptyList(),
        val activePredictions: List<AIMessage.Cognition.PredictionGenerated> = emptyList(),
        val rewardHistory: List<AIMessage.Cognition.RewardUpdate> = emptyList(),
        val lastUpdate: Long = System.currentTimeMillis()
    )
    
    data class AffectState(
        val currentEmotion: EmotionVector = EmotionVector(),
        val moodHistory: List<EmotionVector> = emptyList(),
        val lastUpdate: Long = System.currentTimeMillis()
    )
    
    data class MetaState(
        val currentGoal: String? = null,
        val subGoals: List<String> = emptyList(),
        val activeAgents: Set<AgentType> = emptySet(),
        val resourceAllocations: Map<AgentType, ResourceAllocation> = emptyMap(),
        val lastUpdate: Long = System.currentTimeMillis()
    )
    
    data class MotorState(
        val actionsInFlight: List<AIMessage.Control.ActionRequest> = emptyList(),
        val executionHistory: List<AIMessage.Motor.ActionExecuted> = emptyList(),
        val lastUpdate: Long = System.currentTimeMillis()
    )
}

// Supporting Data Classes
data class CameraSnapshot(val frameId: String, val timestamp: Long, val resolution: Pair<Int, Int>)
data class AudioSnapshot(val bufferSize: Int, val sampleRate: Int, val timestamp: Long)
data class GPSSnapshot(val lat: Double, val lon: Double, val accuracy: Float, val timestamp: Long)
data class BatterySnapshot(val percent: Int, val isCharging: Boolean, val temperature: Float, val timestamp: Long)
data class NetworkSnapshot(val type: String, val isConnected: Boolean, val strength: Int, val timestamp: Long)

data class EmotionVector(
    val valence: Float = 0.0f,
    val arousal: Float = 0.0f,
    val urgency: Float = 0.0f,
    val timestamp: Long = System.currentTimeMillis()
)

data class ResourceAllocation(
    val cpuPercent: Int,
    val memoryMB: Int,
    val gpuAllowed: Boolean
)
