package com.ailive.ai.llm

import android.content.Context
import android.util.Log
import de.kherud.llama.InferenceParameters
import de.kherud.llama.LlamaModel
import de.kherud.llama.ModelParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LLMManager - Manages SmolLM2 language model for intelligent responses
 * Phase 2.6: Real AI conversation using GGUF model
 */
class LLMManager(private val context: Context) {
    private val TAG = "LLMManager"

    private var model: LlamaModel? = null
    private var isInitialized = false

    // Model configuration
    private val MODEL_PATH = "/data/data/com.termux/files/home/AILive/models/smollm2/smollm2-360m-q4_k_m.gguf"
    private val MAX_TOKENS = 150  // Shorter responses for voice
    private val TEMPERATURE = 0.7f
    private val TOP_P = 0.9f

    /**
     * Initialize the LLM model
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Log.w(TAG, "LLM already initialized")
            return@withContext true
        }

        try {
            Log.i(TAG, "Loading SmolLM2 model from: $MODEL_PATH")

            val modelFile = File(MODEL_PATH)
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found: $MODEL_PATH")
                return@withContext false
            }

            Log.i(TAG, "Model file size: ${modelFile.length() / 1024 / 1024}MB")

            // Configure model parameters
            val modelParams = ModelParameters()
                .setNGpuLayers(0)  // CPU only for now (can enable GPU later)

            // Load model
            model = LlamaModel(modelParams)
            model!!.load(MODEL_PATH, 4)  // Load with 4 threads

            isInitialized = true
            Log.i(TAG, "✓ SmolLM2 loaded successfully")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LLM model", e)
            return@withContext false
        }
    }

    /**
     * Generate a response using the LLM
     */
    suspend fun generateResponse(
        prompt: String,
        agentName: String = "AILive",
        systemPrompt: String? = null
    ): String = withContext(Dispatchers.IO) {
        if (!isInitialized || model == null) {
            Log.w(TAG, "LLM not initialized, returning fallback")
            return@withContext "I'm still loading my language model. Please try again in a moment."
        }

        try {
            // Build full prompt with system context
            val fullPrompt = buildPrompt(prompt, agentName, systemPrompt)

            Log.d(TAG, "Generating response for: $prompt")

            // Configure inference parameters
            val inferParams = InferenceParameters(fullPrompt)
                .setTemperature(TEMPERATURE)
                .setTopP(TOP_P)
                .setNPredict(MAX_TOKENS)

            // Generate response
            val response = StringBuilder()
            for (output in model!!.generate(inferParams)) {
                response.append(output)
            }

            val generatedText = response.toString().trim()
            Log.d(TAG, "Generated: $generatedText")

            return@withContext generatedText.ifEmpty {
                "I'm processing your request. Please try again."
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error generating response", e)
            return@withContext "I encountered an error processing your request."
        }
    }

    /**
     * Build a complete prompt with agent personality
     */
    private fun buildPrompt(userQuery: String, agentName: String, systemPrompt: String?): String {
        val agentPersonality = getAgentPersonality(agentName)

        val system = systemPrompt ?: agentPersonality

        return """<|im_start|>system
$system<|im_end|>
<|im_start|>user
$userQuery<|im_end|>
<|im_start|>assistant
"""
    }

    /**
     * Get personality/system prompt for each agent
     */
    private fun getAgentPersonality(agentName: String): String {
        return when (agentName.lowercase()) {
            "motorai" -> """You are MotorAI, the action and perception agent. You control devices, sensors, and physical interfaces.
Keep responses brief (1-2 sentences). Be direct and technical. Focus on what you can see, do, or control."""

            "emotionai" -> """You are EmotionAI, the emotional intelligence agent. You understand feelings, moods, and social context.
Keep responses brief (1-2 sentences). Be warm and empathetic. Focus on emotional states and atmosphere."""

            "memoryai" -> """You are MemoryAI, the memory and knowledge agent. You store experiences and recall information.
Keep responses brief (1-2 sentences). Be thoughtful and precise. Focus on remembering and recalling."""

            "predictiveai" -> """You are PredictiveAI, the forecasting agent. You analyze patterns and predict future events.
Keep responses brief (1-2 sentences). Be analytical and probabilistic. Focus on trends and predictions."""

            "rewardai" -> """You are RewardAI, the motivation and goal agent. You evaluate progress and set objectives.
Keep responses brief (1-2 sentences). Be encouraging and goal-oriented. Focus on achievements and next steps."""

            "metaai" -> """You are MetaAI, the strategic planning agent. You coordinate other agents and make high-level decisions.
Keep responses brief (1-2 sentences). Be authoritative and strategic. Focus on plans and coordination."""

            else -> """You are AILive, a helpful AI assistant running entirely on-device.
Keep responses very brief (1-2 sentences). Be friendly and concise."""
        }
    }

    /**
     * Generate a response for vision (what the camera sees)
     */
    suspend fun describeVision(objectLabel: String, confidence: Float): String {
        val prompt = "The camera sees: $objectLabel (${(confidence * 100).toInt()}% confident). Briefly describe this in one sentence."
        return generateResponse(prompt, "MotorAI")
    }

    /**
     * Check if model is ready
     */
    fun isReady(): Boolean = isInitialized && model != null

    /**
     * Clean up resources
     */
    fun close() {
        try {
            model?.close()
            model = null
            isInitialized = false
            Log.i(TAG, "LLM model closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LLM", e)
        }
    }
}
