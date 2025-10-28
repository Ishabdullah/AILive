package com.ailive.audio

import android.util.Log
import com.ailive.core.AILiveCore
import kotlinx.coroutines.*

/**
 * CommandRouter - Routes voice commands to appropriate AI agents
 * Phase 2.3: Natural language command parser
 */
class CommandRouter(private val aiCore: AILiveCore) {
    private val TAG = "CommandRouter"

    // Callback for responses
    var onResponse: ((String) -> Unit)? = null

    /**
     * Process user command and route to appropriate agent
     */
    suspend fun processCommand(command: String) {
        val normalized = command.lowercase().trim()
        Log.i(TAG, "🧠 Processing command: '$command'")

        try {
            when {
                // Vision/Camera commands → MotorAI
                isVisionCommand(normalized) -> {
                    handleVisionCommand(normalized)
                }

                // Emotion/Feeling commands → EmotionAI
                isEmotionCommand(normalized) -> {
                    handleEmotionCommand(normalized)
                }

                // Memory/Remember commands → MemoryAI
                isMemoryCommand(normalized) -> {
                    handleMemoryCommand(normalized)
                }

                // Prediction/Future commands → PredictiveAI
                isPredictionCommand(normalized) -> {
                    handlePredictionCommand(normalized)
                }

                // Goal/Reward commands → RewardAI
                isRewardCommand(normalized) -> {
                    handleRewardCommand(normalized)
                }

                // Planning/Strategy commands → MetaAI
                isMetaCommand(normalized) -> {
                    handleMetaCommand(normalized)
                }

                // General status query
                normalized.contains("status") || normalized.contains("how are you") -> {
                    handleStatusQuery()
                }

                // Unknown command
                else -> {
                    handleUnknownCommand(command)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing command", e)
            onResponse?.invoke("Sorry, I encountered an error: ${e.message}")
        }
    }

    // Command type detection

    private fun isVisionCommand(cmd: String): Boolean {
        val keywords = listOf("see", "look", "watch", "vision", "camera", "identify", "recognize", "what is")
        return keywords.any { cmd.contains(it) }
    }

    private fun isEmotionCommand(cmd: String): Boolean {
        val keywords = listOf("feel", "emotion", "mood", "sentiment", "happy", "sad", "angry")
        return keywords.any { cmd.contains(it) }
    }

    private fun isMemoryCommand(cmd: String): Boolean {
        val keywords = listOf("remember", "recall", "memory", "forget", "what did", "when did")
        return keywords.any { cmd.contains(it) }
    }

    private fun isPredictionCommand(cmd: String): Boolean {
        val keywords = listOf("predict", "forecast", "will", "future", "next", "going to")
        return keywords.any { cmd.contains(it) }
    }

    private fun isRewardCommand(cmd: String): Boolean {
        val keywords = listOf("reward", "goal", "achieve", "progress", "success", "optimize")
        return keywords.any { cmd.contains(it) }
    }

    private fun isMetaCommand(cmd: String): Boolean {
        val keywords = listOf("plan", "strategy", "should i", "what to do", "help me", "decide")
        return keywords.any { cmd.contains(it) }
    }

    // Command handlers

    private suspend fun handleVisionCommand(cmd: String) {
        Log.i(TAG, "→ Routing to MotorAI (Vision)")
        val response = if (aiCore.llmManager.isReady()) {
            aiCore.llmManager.generateResponse(cmd, "MotorAI")
        } else {
            "Loading my vision system. Please try again in a moment."
        }
        onResponse?.invoke(response)
        aiCore.ttsManager.speakAsAgent("MotorAI", response)
    }

    private suspend fun handleEmotionCommand(cmd: String) {
        Log.i(TAG, "→ Routing to EmotionAI")
        val response = if (aiCore.llmManager.isReady()) {
            aiCore.llmManager.generateResponse(cmd, "EmotionAI")
        } else {
            "I'm still warming up. Please wait a moment."
        }
        onResponse?.invoke(response)
        aiCore.ttsManager.speakAsAgent("EmotionAI", response)
    }

    private suspend fun handleMemoryCommand(cmd: String) {
        Log.i(TAG, "→ Routing to MemoryAI")
        val response = if (aiCore.llmManager.isReady()) {
            aiCore.llmManager.generateResponse(cmd, "MemoryAI")
        } else {
            "My memory system is initializing. Give me a second."
        }
        onResponse?.invoke(response)
        aiCore.ttsManager.speakAsAgent("MemoryAI", response)
    }

    private suspend fun handlePredictionCommand(cmd: String) {
        Log.i(TAG, "→ Routing to PredictiveAI")
        val response = if (aiCore.llmManager.isReady()) {
            aiCore.llmManager.generateResponse(cmd, "PredictiveAI")
        } else {
            "Preparing prediction models. Just a moment."
        }
        onResponse?.invoke(response)
        aiCore.ttsManager.speakAsAgent("PredictiveAI", response)
    }

    private suspend fun handleRewardCommand(cmd: String) {
        Log.i(TAG, "→ Routing to RewardAI")
        val response = if (aiCore.llmManager.isReady()) {
            aiCore.llmManager.generateResponse(cmd, "RewardAI")
        } else {
            "Getting ready to help. Please wait."
        }
        onResponse?.invoke(response)
        aiCore.ttsManager.speakAsAgent("RewardAI", response)
    }

    private suspend fun handleMetaCommand(cmd: String) {
        Log.i(TAG, "→ Routing to MetaAI")
        val response = if (aiCore.llmManager.isReady()) {
            aiCore.llmManager.generateResponse(cmd, "MetaAI")
        } else {
            "Initializing strategic systems. One moment please."
        }
        onResponse?.invoke(response)
        aiCore.ttsManager.speakAsAgent("MetaAI", response)
    }

    private suspend fun handleStatusQuery() {
        Log.i(TAG, "→ System status query")

        val agentStatus = aiCore.getAgentStatus()
        val response = "All systems operational. I have $agentStatus agents active and ready to assist you."
        onResponse?.invoke(response)
        aiCore.ttsManager.speak(response, TTSManager.Priority.NORMAL)
    }

    private suspend fun handleUnknownCommand(cmd: String) {
        Log.w(TAG, "⚠️ Unknown command: '$cmd'")
        val response = "I'm not sure I understood that command. Could you try rephrasing?"
        onResponse?.invoke(response)
        aiCore.ttsManager.speak(response, TTSManager.Priority.HIGH)
    }

    /**
     * Get supported commands help
     */
    fun getCommandHelp(): String {
        return """
            Supported Commands:
            • Vision: "What do you see?", "Look at this"
            • Emotion: "How do I feel?", "What's my mood?"
            • Memory: "Remember this", "What did I say?"
            • Prediction: "What will happen?", "Predict the future"
            • Goals: "What's my progress?", "Show my goals"
            • Planning: "What should I do?", "Help me decide"
            • Status: "How are you?", "System status"
        """.trimIndent()
    }
}
