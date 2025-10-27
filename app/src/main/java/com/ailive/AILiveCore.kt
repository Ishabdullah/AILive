package com.ailive

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.ailive.core.messaging.MessageBus
import com.ailive.core.state.StateManager
import com.ailive.emotion.EmotionAI
import com.ailive.memory.MemoryAI
import com.ailive.meta.MetaAI
import com.ailive.motor.MotorAI
import com.ailive.predictive.PredictiveAI
import com.ailive.reward.RewardAI
import kotlinx.coroutines.*

/**
 * AILiveCore - Main orchestrator for the entire AILive system.
 * Manages lifecycle of all AI agents and coordinates system startup/shutdown.
 */
class AILiveCore(
    private val context: Context,
    private val activity: FragmentActivity
) {
    private val TAG = "AILiveCore"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Core infrastructure
    val messageBus = MessageBus(scope)
    val stateManager = StateManager()
    
    // AI Agents - Perception Layer
    lateinit var motorAI: MotorAI
    lateinit var emotionAI: EmotionAI
    
    // AI Agents - Cognition Layer
    lateinit var memoryAI: MemoryAI
    lateinit var predictiveAI: PredictiveAI
    lateinit var rewardAI: RewardAI
    
    // AI Agents - Meta Layer
    lateinit var metaAI: MetaAI
    
    // System state
    private var isInitialized = false
    private var isRunning = false
    
    /**
     * Initialize all components (call once).
     */
    fun initialize() {
        if (isInitialized) {
            Log.w(TAG, "AILive already initialized")
            return
        }
        
        Log.i(TAG, "Initializing AILive system...")
        
        try {
            // Initialize agents
            motorAI = MotorAI(context, activity, messageBus, stateManager)
            emotionAI = EmotionAI(messageBus, stateManager)
            memoryAI = MemoryAI(context, messageBus, stateManager)
            predictiveAI = PredictiveAI(messageBus, stateManager)
            rewardAI = RewardAI(messageBus, stateManager)
            metaAI = MetaAI(messageBus, stateManager)
            
            isInitialized = true
            Log.i(TAG, "✓ AILive initialized successfully (6 agents)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AILive", e)
            throw e
        }
    }
    
    /**
     * Start the entire AILive system.
     */
    fun start() {
        if (!isInitialized) {
            throw IllegalStateException("AILive not initialized. Call initialize() first.")
        }
        
        if (isRunning) {
            Log.w(TAG, "AILive already running")
            return
        }
        
        Log.i(TAG, "Starting AILive system...")
        
        // Start in dependency order
        messageBus.start()
        motorAI.start()
        emotionAI.start()
        memoryAI.start()
        predictiveAI.start()
        rewardAI.start()
        metaAI.start()
        
        isRunning = true
        Log.i(TAG, "✓ AILive system fully operational (all agents active)")
        
        // Log system info
        scope.launch {
            delay(1000)
            logSystemStatus()
        }
    }
    
    /**
     * Stop the entire AILive system gracefully.
     */
    fun stop() {
        if (!isRunning) {
            Log.w(TAG, "AILive not running")
            return
        }
        
        Log.i(TAG, "Stopping AILive system...")
        
        // Stop in reverse dependency order
        metaAI.stop()
        rewardAI.stop()
        predictiveAI.stop()
        memoryAI.stop()
        emotionAI.stop()
        motorAI.stop()
        messageBus.stop()
        
        scope.cancel()
        
        isRunning = false
        Log.i(TAG, "✓ AILive system stopped")
    }
    
    /**
     * Restart the system.
     */
    fun restart() {
        Log.i(TAG, "Restarting AILive system...")
        stop()
        Thread.sleep(500)
        start()
    }
    
    /**
     * Get system health status.
     */
    suspend fun getSystemHealth(): SystemHealth {
        val busStats = messageBus.getStats()
        val metaStats = metaAI.getStats()
        val memoryStats = memoryAI.getStats()
        val rewardStats = rewardAI.getStats()
        val currentEmotion = emotionAI.getCurrentEmotion()
        
        return SystemHealth(
            isRunning = isRunning,
            messagesProcessed = busStats.messagesProcessed,
            currentGoal = metaStats.currentGoal,
            totalMemories = memoryStats.totalMemories,
            memoryUsageMB = memoryStats.memoryUsageMB,
            rewardUpdates = rewardStats.totalUpdates,
            emotionValence = currentEmotion.valence,
            emotionUrgency = currentEmotion.urgency,
            systemUptimeMs = System.currentTimeMillis()
        )
    }
    
    /**
     * Log current system status.
     */
    private suspend fun logSystemStatus() {
        val health = getSystemHealth()
        Log.i(TAG, """
            |╔═══════════════════════════════════╗
            |║   AILive System Status            ║
            |╠═══════════════════════════════════╣
            |║ Running: ${health.isRunning}
            |║ Messages: ${health.messagesProcessed}
            |║ Goal: ${health.currentGoal ?: "None"}
            |║ Memories: ${health.totalMemories}
            |║ Memory Usage: ${"%.2f".format(health.memoryUsageMB)} MB
            |║ Reward Updates: ${health.rewardUpdates}
            |║ Emotion: valence=${"%.2f".format(health.emotionValence)} urgency=${"%.2f".format(health.emotionUrgency)}
            |╚═══════════════════════════════════╝
        """.trimMargin())
    }
    
    /**
     * Emergency shutdown (in case of critical error).
     */
    fun emergencyShutdown(reason: String) {
        Log.e(TAG, "EMERGENCY SHUTDOWN: $reason")
        try {
            stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error during emergency shutdown", e)
        }
    }
}

data class SystemHealth(
    val isRunning: Boolean,
    val messagesProcessed: Long,
    val currentGoal: String?,
    val totalMemories: Int,
    val memoryUsageMB: Float,
    val rewardUpdates: Long,
    val emotionValence: Float,
    val emotionUrgency: Float,
    val systemUptimeMs: Long
)
