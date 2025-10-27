package com.ailive.core.state

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe manager for AILive's shared blackboard state.
 * Uses Kotlin StateFlow for reactive updates.
 */
class StateManager {
    private val TAG = "StateManager"
    
    private val _state = MutableStateFlow(BlackboardState())
    val state: StateFlow<BlackboardState> = _state.asStateFlow()
    
    private val mutex = Mutex()
    
    /**
     * Read current state (thread-safe, non-blocking).
     */
    fun read(): BlackboardState = _state.value
    
    /**
     * Update sensor state.
     */
    suspend fun updateSensors(update: (BlackboardState.SensorState) -> BlackboardState.SensorState) {
        mutex.withLock {
            val current = _state.value
            _state.value = current.copy(
                sensors = update(current.sensors).copy(lastUpdate = System.currentTimeMillis())
            )
            Log.d(TAG, "Sensor state updated")
        }
    }
    
    /**
     * Update perception state.
     */
    suspend fun updatePerception(update: (BlackboardState.PerceptionState) -> BlackboardState.PerceptionState) {
        mutex.withLock {
            val current = _state.value
            _state.value = current.copy(
                perception = update(current.perception).copy(lastUpdate = System.currentTimeMillis())
            )
        }
    }
    
    /**
     * Update cognition state.
     */
    suspend fun updateCognition(update: (BlackboardState.CognitionState) -> BlackboardState.CognitionState) {
        mutex.withLock {
            val current = _state.value
            _state.value = current.copy(
                cognition = update(current.cognition).copy(lastUpdate = System.currentTimeMillis())
            )
        }
    }
    
    /**
     * Update affect state.
     */
    suspend fun updateAffect(update: (BlackboardState.AffectState) -> BlackboardState.AffectState) {
        mutex.withLock {
            val current = _state.value
            _state.value = current.copy(
                affect = update(current.affect).copy(lastUpdate = System.currentTimeMillis())
            )
        }
    }
    
    /**
     * Update meta state.
     */
    suspend fun updateMeta(update: (BlackboardState.MetaState) -> BlackboardState.MetaState) {
        mutex.withLock {
            val current = _state.value
            _state.value = current.copy(
                meta = update(current.meta).copy(lastUpdate = System.currentTimeMillis())
            )
        }
    }
    
    /**
     * Update motor state.
     */
    suspend fun updateMotor(update: (BlackboardState.MotorState) -> BlackboardState.MotorState) {
        mutex.withLock {
            val current = _state.value
            _state.value = current.copy(
                motor = update(current.motor).copy(lastUpdate = System.currentTimeMillis())
            )
        }
    }
    
    /**
     * Reset entire state (for testing or system restart).
     */
    suspend fun reset() {
        mutex.withLock {
            _state.value = BlackboardState()
            Log.i(TAG, "State reset to default")
        }
    }
    
    /**
     * Get state snapshot as JSON (for debugging/logging).
     */
    fun getSnapshot(): String {
        val state = _state.value
        return """
            {
              "sensors_updated": ${state.sensors.lastUpdate},
              "perception_updated": ${state.perception.lastUpdate},
              "cognition_updated": ${state.cognition.lastUpdate},
              "affect_updated": ${state.affect.lastUpdate},
              "meta_updated": ${state.meta.lastUpdate},
              "motor_updated": ${state.motor.lastUpdate},
              "current_goal": "${state.meta.currentGoal}",
              "active_agents": ${state.meta.activeAgents.size},
              "emotion_valence": ${state.affect.currentEmotion.valence}
            }
        """.trimIndent()
    }
}
