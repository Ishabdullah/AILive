package com.ailive.core

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.ailive.motor.MotorAI
import com.ailive.emotion.EmotionAI
import com.ailive.memory.MemoryAI
import com.ailive.predictive.PredictiveAI
import com.ailive.reward.RewardAI
import com.ailive.meta.MetaAI
import com.ailive.core.messaging.MessageBus
import com.ailive.core.state.StateManager
import com.ailive.audio.TTSManager
import com.ailive.ai.llm.LLMManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * AILiveCore - Central coordinator for all AI agents
 * Manages lifecycle and communication between agents
 */
class AILiveCore(
    private val context: Context,
    private val activity: FragmentActivity
) {
    private val TAG = "AILiveCore"

    lateinit var messageBus: MessageBus  // Public for CommandRouter access
    private lateinit var stateManager: StateManager
    lateinit var ttsManager: TTSManager  // Public for agents and CommandRouter
    lateinit var llmManager: LLMManager  // Public for generating intelligent responses

    private lateinit var motorAI: MotorAI
    private lateinit var emotionAI: EmotionAI
    private lateinit var memoryAI: MemoryAI
    private lateinit var predictiveAI: PredictiveAI
    private lateinit var rewardAI: RewardAI
    private lateinit var metaAI: MetaAI

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
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
            ttsManager = TTSManager(context)
            llmManager = LLMManager(context)

            // Initialize LLM asynchronously (takes time to load)
            scope.launch {
                Log.i(TAG, "Loading language model (SmolLM2)...")
                val success = llmManager.initialize()
                if (success) {
                    Log.i(TAG, "✓ Language model ready")
                } else {
                    Log.e(TAG, "✗ Language model failed to load")
                }
            }

            // Create all agents
            motorAI = MotorAI(context, activity, messageBus, stateManager)
            emotionAI = EmotionAI(messageBus, stateManager)
            memoryAI = MemoryAI(context, messageBus, stateManager)
            predictiveAI = PredictiveAI(messageBus, stateManager)
            rewardAI = RewardAI(messageBus, stateManager)
            metaAI = MetaAI(messageBus, stateManager)

            isInitialized = true
            Log.i(TAG, "✓ AILive initialized successfully (6 agents + TTS + LLM)")

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
            ttsManager.shutdown()
            llmManager.close()

            isRunning = false
            Log.i(TAG, "✓ AILive stopped")

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AILive", e)
        }
    }
    
    /**
     * Get agent status count
     */
    fun getAgentStatus(): Int {
        return if (isRunning) 6 else 0
    }

    /**
     * Get access to specific agents
     * Note: messageBus is now a public property, no getter needed
     */
    fun getStateManager(): StateManager = stateManager
    fun getMotorAI(): MotorAI = motorAI
    fun getEmotionAI(): EmotionAI = emotionAI
    fun getMemoryAI(): MemoryAI = memoryAI
    fun getPredictiveAI(): PredictiveAI = predictiveAI
    fun getRewardAI(): RewardAI = rewardAI
    fun getMetaAI(): MetaAI = metaAI
}
