package com.ailive.audio

import android.util.Log
import com.ailive.core.AILiveCore
import com.ailive.personality.InputType
import kotlinx.coroutines.*

/**
 * CommandRouter - Routes voice commands to appropriate AI agents
 * Phase 2.3: Natural language command parser
 *
 * REFACTORING NOTE: Now supports PersonalityEngine for unified responses.
 * When usePersonalityEngine=true, routes through PersonalityEngine instead of individual agents.
 */
class CommandRouter(private val aiCore: AILiveCore) {
    private val TAG = "CommandRouter"

    // Callback for responses
    var onResponse: ((String) -> Unit)? = null

    /**
     * Process user command and route to appropriate agent or PersonalityEngine
     */
    suspend fun processCommand(command: String) {
        val normalized = command.lowercase().trim()
        Log.i(TAG, "🧠 Processing command: '$command'")

        // NEW: Route through PersonalityEngine if enabled
        if (aiCore.usePersonalityEngine) {
            try {
                handleWithPersonalityEngine(command)
                return
            } catch (e: UninitializedPropertyAccessException) {
                Log.e(TAG, "PersonalityEngine not initialized, falling back to legacy mode", e)
                // Fall through to legacy mode
            }
        }

        // Legacy routing (old agent-based system)
        processCommandLegacy(normalized, command)
    }

    /**
     * NEW: Handle command with PersonalityEngine (unified intelligence)
     */
    private suspend fun handleWithPersonalityEngine(command: String) {
        try {
            Log.i(TAG, "🧠 Routing to PersonalityEngine (unified mode)")
            Log.d(TAG, "Input command: '$command'")

            // Process through PersonalityEngine
            val response = aiCore.personalityEngine.processInput(
                input = command,
                inputType = InputType.VOICE
            )

            // CRITICAL: Send response to user
            val responseText = response.text
            Log.i(TAG, "✅ PersonalityEngine generated response: '$responseText'")
            Log.d(TAG, "Response length: ${responseText.length} chars")
            Log.d(TAG, "Tools used: ${response.usedTools.joinToString()}")
            Log.d(TAG, "Confidence: ${response.confidence}")

            // Invoke callback to send to user
            onResponse?.invoke(responseText)
            Log.d(TAG, "✓ Response sent to user via callback")

        } catch (e: Exception) {
            Log.e(TAG, "❌ PersonalityEngine error - DETAILED", e)
            Log.e(TAG, "Exception: ${e.javaClass.simpleName}")
            Log.e(TAG, "Message: ${e.message}")
            e.printStackTrace()

            val errorResponse = "I'm having trouble processing that. Could you try again?"
            onResponse?.invoke(errorResponse)
            Log.w(TAG, "Sent error response to user")
        }
    }

    /**
     * Legacy command processing (separate agents)
     */
    private suspend fun processCommandLegacy(normalized: String, command: String) {
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
        var response = ""
        aiCore.hybridModelManager.generateStreaming(cmd, agentName = "MotorAI").collect { chunk ->
            response += chunk
        }
        onResponse?.invoke(response)
        aiCore.ttsManager.speakAsAgent("MotorAI", response)
    }

    private suspend fun handleEmotionCommand(cmd: String) {
        Log.i(TAG, "→ Routing to EmotionAI")
        var response = ""
        aiCore.hybridModelManager.generateStreaming(cmd, agentName = "EmotionAI").collect { chunk ->
            response += chunk
        }
        onResponse?.invoke(response)
        aiCore.ttsManager.speakAsAgent("EmotionAI", response)
    }

    private suspend fun handleMemoryCommand(cmd: String) {
        Log.i(TAG, "→ Routing to MemoryAI")
        var response = ""
        aiCore.hybridModelManager.generateStreaming(cmd, agentName = "MemoryAI").collect { chunk ->
            response += chunk
        }
        onResponse?.invoke(response)
        aiCore.ttsManager.speakAsAgent("MemoryAI", response)
    }

    private suspend fun handlePredictionCommand(cmd: String) {
        Log.i(TAG, "→ Routing to PredictiveAI")
        var response = ""
        aiCore.hybridModelManager.generateStreaming(cmd, agentName = "PredictiveAI").collect { chunk ->
            response += chunk
        }
        onResponse?.invoke(response)
        aiCore.ttsManager.speakAsAgent("PredictiveAI", response)
    }

    private suspend fun handleRewardCommand(cmd: String) {
        Log.i(TAG, "→ Routing to RewardAI")
        var response = ""
        aiCore.hybridModelManager.generateStreaming(cmd, agentName = "RewardAI").collect { chunk ->
            response += chunk
        }
        onResponse?.invoke(response)
        aiCore.ttsManager.speakAsAgent("RewardAI", response)
    }

    private suspend fun handleMetaCommand(cmd: String) {
        Log.i(TAG, "→ Routing to MetaAI")
        var response = ""
        aiCore.hybridModelManager.generateStreaming(cmd, agentName = "MetaAI").collect { chunk ->
            response += chunk
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
        var response = ""
        aiCore.hybridModelManager.generateStreaming(cmd, agentName = "AILive").collect { chunk ->
            response += chunk
        }
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
