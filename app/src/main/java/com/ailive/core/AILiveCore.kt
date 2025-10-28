package com.ailive.core

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.ailive.agents.*

/**
 * AILiveCore - Central coordinator for all AI agents
 * Manages lifecycle and communication between agents
 */
class AILiveCore(
    private val context: Context,
    private val activity: FragmentActivity
) {
    private val TAG = "AILiveCore"
    
    private lateinit var messageBus: MessageBus
    private lateinit var stateManager: StateManager
    
    private lateinit var motorAI: MotorAI
    private lateinit var emotionAI: EmotionAI
    private lateinit var memoryAI: MemoryAI
    private lateinit var predictiveAI: PredictiveAI
    private lateinit var rewardAI: RewardAI
    private lateinit var metaAI: MetaAI
    
    private var isInitialized = false
    private var isRunning = false
    
    /**
     * Initialize all AI components
     */
    fun initialize() {
        if (isInitialized) {
            Log.w(TAG, "AILive already initialized")
            return
        }
        
        try {
            Log.i(TAG, "Initializing AILive system...")
            
            // Core systems
            messageBus = MessageBus()
            stateManager = StateManager()
            
            // Create all agents
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
     * Start all AI agents
     */
    fun start() {
        if (!isInitialized) {
            throw IllegalStateException("AILive not initialized")
        }
        
        if (isRunning) {
            Log.w(TAG, "AILive already running")
            return
        }
        
        try {
            Log.i(TAG, "Starting AILive agents...")
            
            motorAI.start()
            emotionAI.start()
            memoryAI.start()
            predictiveAI.start()
            rewardAI.start()
            metaAI.start()
            
            isRunning = true
            Log.i(TAG, "✓ AILive system fully operational (all agents active)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AILive", e)
            throw e
        }
    }
    
    /**
     * Stop all AI agents
     */
    fun stop() {
        if (!isRunning) return
        
        Log.i(TAG, "Stopping AILive...")
        
        try {
            motorAI.stop()
            emotionAI.stop()
            memoryAI.stop()
            predictiveAI.stop()
            rewardAI.stop()
            metaAI.stop()
            
            isRunning = false
            Log.i(TAG, "✓ AILive stopped")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AILive", e)
        }
    }
    
    /**
     * Get access to specific agents
     */
    fun getMessageBus(): MessageBus = messageBus
    fun getStateManager(): StateManager = stateManager
    fun getMotorAI(): MotorAI = motorAI
    fun getEmotionAI(): EmotionAI = emotionAI
    fun getMemoryAI(): MemoryAI = memoryAI
    fun getPredictiveAI(): PredictiveAI = predictiveAI
    fun getRewardAI(): RewardAI = rewardAI
    fun getMetaAI(): MetaAI = metaAI
}
