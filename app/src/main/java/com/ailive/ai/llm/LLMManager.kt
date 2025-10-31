package com.ailive.ai.llm

import android.content.Context
import android.util.Log
import ai.onnxruntime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer
import java.nio.FloatBuffer
import kotlin.math.exp

/**
 * LLMManager - Language model inference using ONNX Runtime
 * Phase 2.6 & Phase 7: Provides intelligent text generation for AI agents
 *
 * Currently supports:
 * - ONNX models via ONNX Runtime (.onnx files)
 *
 * TODO: Add GGUF support when stable Android library is available
 * Note: GGUF models (.gguf) can be imported but won't work until llama.cpp is integrated
 *
 * @author AILive Team
 * @since Phase 2.6
 * @updated Phase 7.8 - Accept any model format (ONNX only for now)
 */
class LLMManager(private val context: Context) {

    companion object {
        private const val TAG = "LLMManager"

        // OPTIMIZATION: Reduced from 150 to 80 for faster generation
        // Voice responses should be concise (1-3 sentences = ~50-80 tokens)
        private const val MAX_LENGTH = 80

        // OPTIMIZATION: Higher temperature (0.9) for more varied responses
        // Previously 0.7 was causing some repetition
        private const val TEMPERATURE = 0.9f

        private const val TOP_P = 0.9f
    }

    // ONNX Runtime (for .onnx models)
    private var ortSession: OrtSession? = null
    private var ortEnv: OrtEnvironment? = null

    private var isInitialized = false

    // Model download manager
    private val modelDownloadManager = ModelDownloadManager(context)

    // Simple tokenizer (will use byte-pair encoding approximation)
    private val vocabulary = mutableMapOf<String, Long>()
    private var vocabSize = 32000  // TinyLlama vocab size

    // Current model path and info
    private var currentModelPath: String? = null
    private var currentModelName: String? = null

    /**
     * Initialize the LLM model
     * Called once on app startup in background thread
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🤖 Initializing LLM (ONNX Runtime)...")

            // Check if ANY model is available (prefer .onnx but check all)
            val availableModels = modelDownloadManager.getAvailableModels()
            if (availableModels.isEmpty()) {
                Log.e(TAG, "❌ No model files found")
                Log.i(TAG, "📥 Please download SmolLM2-360M ONNX model")
                return@withContext false
            }

            // Find ONNX model (prefer .onnx over .gguf)
            val onnxModel = availableModels.firstOrNull { it.name.endsWith(".onnx", ignoreCase = true) }

            if (onnxModel == null) {
                // Check if GGUF models exist
                val ggufModels = availableModels.filter { it.name.endsWith(".gguf", ignoreCase = true) }
                if (ggufModels.isNotEmpty()) {
                    Log.e(TAG, "❌ Found GGUF models but GGUF support not yet implemented")
                    Log.i(TAG, "   GGUF models found: ${ggufModels.map { it.name }}")
                    Log.i(TAG, "   Please download SmolLM2-360M ONNX model instead")
                    return@withContext false
                }
                Log.e(TAG, "❌ No ONNX models found")
                return@withContext false
            }

            currentModelPath = onnxModel.absolutePath
            currentModelName = onnxModel.name

            Log.i(TAG, "📂 Loading model: ${onnxModel.name} (${onnxModel.length() / 1024 / 1024}MB)")

            // Create ONNX Runtime environment
            ortEnv = OrtEnvironment.getEnvironment()

            // Create session options with GPU acceleration
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            sessionOptions.setIntraOpNumThreads(4)  // Use 4 CPU threads

            // OPTIMIZATION: Enable NNAPI for GPU/NPU acceleration
            try {
                sessionOptions.addNnapi()
                Log.i(TAG, "✅ NNAPI GPU acceleration enabled")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ NNAPI not available, using CPU: ${e.message}")
            }

            // Create session
            ortSession = ortEnv?.createSession(onnxModel.absolutePath, sessionOptions)

            // Initialize simple vocabulary (simplified for demo)
            initializeVocabulary()

            isInitialized = true
            Log.i(TAG, "✅ LLM initialized successfully!")
            Log.i(TAG, "   Model: $currentModelName")
            Log.i(TAG, "   Size: ${onnxModel.length() / 1024 / 1024}MB")
            Log.i(TAG, "   Engine: ONNX Runtime")
            Log.i(TAG, "   Max length: $MAX_LENGTH tokens")
            Log.i(TAG, "   Temperature: $TEMPERATURE | Top-p: $TOP_P")

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize LLM", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * Generate text response using the language model
     *
     * @param prompt The input text prompt
     * @param agentName Name of the agent for personality context
     * @return Generated text response
     */
    suspend fun generate(prompt: String, agentName: String = "AILive"): String = withContext(Dispatchers.IO) {
        if (!isInitialized || ortSession == null) {
            Log.w(TAG, "⚠️ LLM not initialized, using fallback response")
            return@withContext getFallbackResponse(prompt, agentName)
        }

        try {
            // Create chat prompt with agent personality
            val chatPrompt = createChatPrompt(prompt, agentName)
            Log.d(TAG, "🔍 Generating response for: ${prompt.take(50)}...")

            // Tokenize input
            val inputIds = tokenize(chatPrompt)

            // Run inference
            val outputIds = runInference(inputIds)

            // Decode output
            val response = decode(outputIds)

            Log.d(TAG, "✨ Generated: ${response.take(50)}...")
            return@withContext response.trim()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Generation failed", e)
            e.printStackTrace()
            return@withContext getFallbackResponse(prompt, agentName)
        }
    }

    /**
     * Create a chat-formatted prompt with UNIFIED personality
     *
     * REFACTORING NOTE: This now uses ONE consistent personality (AILive)
     * instead of six separate agent personalities. The agentName parameter
     * is kept for backward compatibility but should always be "AILive".
     */
    private fun createChatPrompt(userMessage: String, agentName: String): String {
        // Unified personality for all interactions
        val personality = """You are AILive, a unified on-device AI companion.
You are ONE cohesive intelligence with multiple capabilities (vision, emotion, memory, device control).
Speak naturally as a single character, never as separate agents or systems.
Be warm, helpful, concise, and conversational."""

        // TinyLlama chat format
        return "<|system|>\n$personality</s>\n<|user|>\n$userMessage</s>\n<|assistant|>\n"
    }

    /**
     * Simple tokenization (approximation for demo)
     * In production, use proper BPE tokenizer
     */
    private fun tokenize(text: String): LongArray {
        // Simplified tokenization - split by whitespace and map to IDs
        val tokens = text.lowercase().split(Regex("\\s+"))
        return tokens.map { token ->
            vocabulary.getOrPut(token) {
                (vocabulary.size + 1).toLong()
            }
        }.toLongArray()
    }

    /**
     * Decode token IDs back to text
     */
    private fun decode(ids: LongArray): String {
        // Reverse vocabulary lookup
        val reverseVocab = vocabulary.entries.associate { it.value to it.key }
        return ids.map { reverseVocab[it] }.filterNotNull().joinToString(" ")
    }

    /**
     * Run ONNX inference
     */
    private fun runInference(inputIds: LongArray): LongArray {
        val session = ortSession ?: throw IllegalStateException("Session not initialized")

        // Prepare input tensor
        val shape = longArrayOf(1, inputIds.size.toLong())
        val inputTensor = OnnxTensor.createTensor(
            ortEnv,
            LongBuffer.wrap(inputIds),
            shape
        )

        // Run inference
        val inputs = mapOf("input_ids" to inputTensor)
        val outputs = session.run(inputs)

        // Get output logits
        val outputTensor = outputs[0] as OnnxTensor
        val logits = outputTensor.floatBuffer

        // Simple greedy decoding (take argmax)
        val outputIds = mutableListOf<Long>()
        for (i in 0 until MAX_LENGTH) {
            val nextTokenId = sampleNextToken(logits)
            outputIds.add(nextTokenId)

            // Stop on end token (assumed to be 2)
            if (nextTokenId == 2L) break
        }

        outputs.close()
        inputTensor.close()

        return outputIds.toLongArray()
    }

    /**
     * Sample next token using temperature and top-p
     */
    private fun sampleNextToken(logits: FloatBuffer): Long {
        // Apply temperature
        val probs = FloatArray(vocabSize)
        for (i in 0 until vocabSize) {
            probs[i] = exp((logits[i] / TEMPERATURE).toDouble()).toFloat()
        }

        // Normalize to probabilities
        val sum = probs.sum()
        for (i in probs.indices) {
            probs[i] /= sum
        }

        // Greedy sampling (take argmax for now)
        return probs.indices.maxByOrNull { probs[it] }?.toLong() ?: 0L
    }

    /**
     * Initialize simple vocabulary
     */
    private fun initializeVocabulary() {
        // Basic English tokens (simplified for demo)
        val commonWords = listOf(
            "the", "a", "an", "i", "you", "he", "she", "it", "we", "they",
            "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did",
            "will", "would", "can", "could", "should", "may", "might",
            "hello", "hi", "how", "what", "when", "where", "why", "who",
            "yes", "no", "ok", "okay", "thanks", "thank", "please",
            "see", "look", "watch", "feel", "think", "know", "remember",
            "system", "status", "help", "camera", "vision", "emotion", "memory"
        )

        commonWords.forEachIndexed { index, word ->
            vocabulary[word] = (index + 3).toLong()  // Start from 3 (0=pad, 1=unk, 2=eos)
        }

        Log.d(TAG, "📖 Vocabulary initialized: ${vocabulary.size} tokens")
    }

    /**
     * Check if a model is available (checks for ANY model, GGUF or ONNX)
     */
    fun isModelAvailable(): Boolean = modelDownloadManager.isModelAvailable(modelName = null)

    /**
     * Get the ModelDownloadManager for access from UI
     */
    fun getDownloadManager(): ModelDownloadManager = modelDownloadManager

    /**
     * Get current model info
     */
    fun getModelInfo(): String {
        return if (isInitialized) {
            "Model: $currentModelName\nEngine: ONNX Runtime"
        } else {
            "No model loaded"
        }
    }

    /**
     * Fallback responses when LLM is not available
     *
     * PHASE 4 FIX: This method should NOT do keyword matching as it causes
     * repetitive responses. Instead, throw exception to let PersonalityEngine's
     * intent-based fallback system handle it properly.
     */
    private fun getFallbackResponse(prompt: String, agentName: String): String {
        // Don't try to be smart here - let PersonalityEngine handle fallbacks
        // PersonalityEngine has better intent-based fallback responses
        throw IllegalStateException("LLM not initialized - use PersonalityEngine fallback")
    }

    /**
     * Cleanup resources
     */
    fun close() {
        try {
            ortSession?.close()
            ortEnv?.close()
            isInitialized = false
            Log.i(TAG, "🔒 ONNX Runtime resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LLM", e)
        }
    }
}
