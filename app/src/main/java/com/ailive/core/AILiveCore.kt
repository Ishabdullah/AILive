package com.ailive.core

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.ailive.motor.MotorAI
import com.ailive.emotion.EmotionAI
import com.ailive.location.LocationManager
import com.ailive.memory.MemoryAI
import com.ailive.predictive.PredictiveAI
import com.ailive.reward.RewardAI
import com.ailive.meta.MetaAI
import com.ailive.core.messaging.MessageBus
import com.ailive.core.state.StateManager
import com.ailive.audio.TTSManager
import com.ailive.ai.llm.LLMManager
import com.ailive.personality.PersonalityEngine
import com.ailive.personality.tools.SentimentAnalysisTool
import com.ailive.personality.tools.DeviceControlTool
import com.ailive.personality.tools.MemoryRetrievalTool
import com.ailive.stats.StatisticsManager
import com.ailive.memory.managers.UnifiedMemoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AILiveCore - Central coordinator for all AI agents
 * Manages lifecycle and communication between agents
 *
 * REFACTORING NOTE: This now includes PersonalityEngine for unified intelligence.
 * Old agents are kept for backward compatibility during transition.
 */
class AILiveCore(
    private val context: Context,
    private val activity: FragmentActivity
) {
    private val TAG = "AILiveCore"

    lateinit var messageBus: MessageBus  // Public for CommandRouter access
    private lateinit var stateManager: StateManager
    lateinit var ttsManager: TTSManager  // Public for agents and CommandRouter
    lateinit var llmManager: LLMManager   // Public for CommandRouter - Phase 2.6

    // NEW: PersonalityEngine for unified intelligence
    lateinit var personalityEngine: PersonalityEngine  // Public for CommandRouter

    // Context managers
    lateinit var locationManager: LocationManager  // GPS and geocoding
    lateinit var statisticsManager: StatisticsManager  // Usage statistics
    lateinit var memoryManager: UnifiedMemoryManager  // Persistent memory system (v1.3)

    // Legacy agents (kept for backward compatibility during transition)
    private lateinit var motorAI: MotorAI
    private lateinit var emotionAI: EmotionAI
    private lateinit var memoryAI: MemoryAI
    private lateinit var predictiveAI: PredictiveAI
    private lateinit var rewardAI: RewardAI
    private lateinit var metaAI: MetaAI

    private var isInitialized = false
    private var isRunning = false

    // Feature flag for PersonalityEngine (can be toggled during transition)
    var usePersonalityEngine = true  // Set to true to enable unified intelligence
    
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

            // Context managers
            locationManager = LocationManager(context)
            statisticsManager = StatisticsManager(context)
            memoryManager = UnifiedMemoryManager(context)
            Log.i(TAG, "✓ Context managers initialized (location + statistics + memory)")

            // Initialize LLM in background (takes ~5-10 seconds)
            // v1.5: LLM must initialize BEFORE memory system (memory needs LLMManager)
            Log.i(TAG, "⏱️  Starting LLM initialization (5-10 seconds)...")
            CoroutineScope(Dispatchers.IO).launch {
                val success = llmManager.initialize()
                if (success) {
                    Log.i(TAG, "✅ LLM ready for intelligent responses")

                    // v1.5: Now initialize memory system with LLMManager
                    // This enables LLM-based fact extraction using Qwen
                    try {
                        memoryManager.initialize(llmManager)
                        Log.i(TAG, "✅ Memory system initialized with Qwen-powered fact extraction")
                    } catch (e: Exception) {
                        Log.e(TAG, "⚠️ Memory system initialization failed", e)
                    }

                    // Notify user that AI is now fully powered
                    ttsManager.speak(
                        text = "Language model loaded. AI responses are now fully powered!",
                        priority = TTSManager.Priority.LOW
                    )
                } else {
                    val error = llmManager.getInitializationError()
                    Log.w(TAG, "⚠️ LLM not available: $error")
                    Log.w(TAG, "   Using fallback response system")

                    // Initialize memory system without LLM (will use regex fallback)
                    try {
                        memoryManager.initialize(null)
                        Log.i(TAG, "✅ Memory system initialized (regex-based extraction)")
                    } catch (e: Exception) {
                        Log.e(TAG, "⚠️ Memory system initialization failed", e)
                    }
                }
            }

            // Create all agents (kept for backward compatibility)
            motorAI = MotorAI(context, activity, messageBus, stateManager)
            emotionAI = EmotionAI(messageBus, stateManager)
            memoryAI = MemoryAI(context, messageBus, stateManager)
            predictiveAI = PredictiveAI(messageBus, stateManager)
            rewardAI = RewardAI(messageBus, stateManager)
            metaAI = MetaAI(messageBus, stateManager)

            // NEW: Initialize PersonalityEngine for unified intelligence
            personalityEngine = PersonalityEngine(
                context = context,
                messageBus = messageBus,
                stateManager = stateManager,
                llmManager = llmManager,
                ttsManager = ttsManager,
                memoryManager = memoryManager  // v1.3: Pass memory system
            )

            // Register tools with PersonalityEngine
            personalityEngine.registerTool(SentimentAnalysisTool(emotionAI))
            personalityEngine.registerTool(DeviceControlTool(motorAI))
            personalityEngine.registerTool(MemoryRetrievalTool(memoryAI, context))  // PHASE 5: Added context for storage

            // PHASE 5 Part 3: Register additional tools
            personalityEngine.registerTool(com.ailive.personality.tools.PatternAnalysisTool(context, stateManager))
            personalityEngine.registerTool(com.ailive.personality.tools.FeedbackTrackingTool(context))

            // v1.4: Register critical missing tools (GPS, web search, corrections)
            personalityEngine.registerTool(com.ailive.personality.tools.LocationTool(locationManager, context))
            personalityEngine.registerTool(com.ailive.websearch.integration.WebSearchTool(context))
            personalityEngine.registerTool(com.ailive.personality.tools.UserCorrectionTool(context, memoryManager))

            isInitialized = true

            if (usePersonalityEngine) {
                Log.i(TAG, "✓ AILive initialized successfully (PersonalityEngine + 8 tools + legacy agents)")
                Log.i(TAG, "  Tools: Sentiment, Device, Memory, Patterns, Feedback, Location, WebSearch, Corrections")
            } else {
                Log.i(TAG, "✓ AILive initialized successfully (6 agents + TTS + LLM) [Legacy mode]")
            }

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

            // Start MessageBus first
            messageBus.start()

            // Start legacy agents (still needed as tools use them)
            motorAI.start()
            emotionAI.start()
            memoryAI.start()
            predictiveAI.start()
            rewardAI.start()
            metaAI.start()

            // NEW: Start PersonalityEngine for unified intelligence
            if (usePersonalityEngine) {
                personalityEngine.start()
                Log.i(TAG, "🧠 PersonalityEngine activated")
            }

            isRunning = true

            if (usePersonalityEngine) {
                Log.i(TAG, "✓ AILive system fully operational (PersonalityEngine mode)")
            } else {
                Log.i(TAG, "✓ AILive system fully operational (all agents active) [Legacy mode]")
            }

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
            // Stop PersonalityEngine first
            if (usePersonalityEngine && ::personalityEngine.isInitialized) {
                personalityEngine.stop()
            }

            // Stop legacy agents
            motorAI.stop()
            emotionAI.stop()
            memoryAI.stop()
            predictiveAI.stop()
            rewardAI.stop()
            metaAI.stop()

            // Stop MessageBus
            messageBus.stop()

            // Shutdown core systems
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
