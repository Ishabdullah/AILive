package com.ailive.ai.llm

import android.content.Context
import android.util.Log
import ai.onnxruntime.*
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
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
 * TEMPORARY: ONNX-only implementation (Phase 7 quick fix)
 * - ONNX models via ONNX Runtime with NNAPI GPU acceleration 🔷
 * - GGUF support temporarily disabled (native library not built yet)
 *
 * Based on SmolChat architecture (541 GitHub stars)
 * TODO: Add llama.cpp JNI support for GGUF models in future phase
 *
 * @author AILive Team
 * @since Phase 2.6
 * @updated Phase 7.10 - Simplified to ONNX-only for quick deployment
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

    // ONNX Runtime (primary inference engine)
    private var ortSession: OrtSession? = null
    private var ortEnv: OrtEnvironment? = null

    private var isInitialized = false

    // GGUF support temporarily disabled (native library not built)
    // private val llamaBridge = LLMBridge()
    // private var isGGUF = false

    // Model download manager
    private val modelDownloadManager = ModelDownloadManager(context)

    // HuggingFace tokenizer (proper BPE tokenization)
    private var tokenizer: HuggingFaceTokenizer? = null

    // Current model path and info
    private var currentModelPath: String? = null
    private var currentModelName: String? = null

    /**
     * Initialize the LLM model (ONNX-only)
     * Called once on app startup in background thread
     *
     * TEMPORARY: Only supports ONNX models (.onnx files)
     * GGUF support will be added when native library is built
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🤖 Initializing LLM (ONNX-only mode)...")

            // Check if ANY model is available
            val availableModels = modelDownloadManager.getAvailableModels()
            if (availableModels.isEmpty()) {
                Log.e(TAG, "❌ No model files found")
                Log.i(TAG, "📥 Please download or import a model")
                return@withContext false
            }

            // ONNX-only: Look for .onnx models
            val onnxModel = availableModels.firstOrNull { it.name.endsWith(".onnx", ignoreCase = true) }

            if (onnxModel == null) {
                Log.e(TAG, "❌ No ONNX models found")
                Log.i(TAG, "   This version only supports .onnx format")
                Log.i(TAG, "   Found models: ${availableModels.joinToString { it.name }}")
                Log.i(TAG, "   Please download an ONNX model")
                return@withContext false
            }

            val modelFile = onnxModel
            currentModelPath = modelFile.absolutePath
            currentModelName = modelFile.name

            Log.i(TAG, "📂 Loading model: ${modelFile.name} (${modelFile.length() / 1024 / 1024}MB)")
            Log.i(TAG, "   Format: ONNX (ONNX Runtime with NNAPI)")

            // Load with ONNX Runtime
            Log.i(TAG, "🔷 Loading with ONNX Runtime...")
            val success = initializeONNX(modelFile)

            if (!success) {
                Log.e(TAG, "❌ Failed to load model")
                return@withContext false
            }

            isInitialized = true
            Log.i(TAG, "✅ LLM initialized successfully!")
            Log.i(TAG, "   Model: $currentModelName")
            Log.i(TAG, "   Size: ${modelFile.length() / 1024 / 1024}MB")
            Log.i(TAG, "   Engine: ONNX Runtime")
            Log.i(TAG, "   Max length: $MAX_LENGTH tokens")

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize LLM", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * Initialize ONNX model (primary inference engine)
     */
    private fun initializeONNX(modelFile: File): Boolean {
        return try {
            // Create ONNX Runtime environment
            ortEnv = OrtEnvironment.getEnvironment()

            // Create session options with GPU acceleration
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            sessionOptions.setIntraOpNumThreads(4)

            // OPTIMIZATION: Enable NNAPI for GPU/NPU acceleration
            try {
                sessionOptions.addNnapi()
                Log.i(TAG, "✅ NNAPI GPU acceleration enabled")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ NNAPI not available, using CPU")
            }

            // Create session
            ortSession = ortEnv?.createSession(modelFile.absolutePath, sessionOptions)

            // Load HuggingFace tokenizer from assets
            Log.i(TAG, "📖 Loading tokenizer...")
            val tokenizerFile = File(context.filesDir, "tokenizer.json")

            // Copy tokenizer from assets to filesDir if not exists
            if (!tokenizerFile.exists()) {
                context.assets.open("tokenizer.json").use { input ->
                    tokenizerFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "   Copied tokenizer.json to ${tokenizerFile.absolutePath}")
            }

            // Initialize DJL tokenizer
            tokenizer = HuggingFaceTokenizer.newInstance(tokenizerFile.toPath())
            Log.i(TAG, "✅ Tokenizer loaded successfully")

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ ONNX initialization failed", e)
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
        if (!isInitialized) {
            Log.w(TAG, "⚠️ LLM not initialized, using fallback response")
            return@withContext getFallbackResponse(prompt, agentName)
        }

        try {
            // Create chat prompt with agent personality
            val chatPrompt = createChatPrompt(prompt, agentName)
            Log.d(TAG, "🔍 Generating response for: ${prompt.take(50)}...")

            // Use ONNX Runtime
            Log.d(TAG, "🔷 Generating with ONNX Runtime...")
            val response = generateONNX(chatPrompt)

            Log.d(TAG, "✨ Generated: ${response.take(50)}...")
            return@withContext response.trim().ifEmpty {
                "I'm processing your request. Please try again."
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Generation failed", e)
            e.printStackTrace()
            return@withContext getFallbackResponse(prompt, agentName)
        }
    }

    /**
     * Generate using ONNX Runtime (primary inference engine)
     */
    private fun generateONNX(chatPrompt: String): String {
        if (ortSession == null) {
            throw IllegalStateException("ONNX session not initialized")
        }

        // Tokenize input
        val inputIds = tokenize(chatPrompt)

        // Run inference
        val outputIds = runInference(inputIds)

        // Decode output
        return decode(outputIds)
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
     * Tokenize text using HuggingFace BPE tokenizer
     */
    private fun tokenize(text: String): LongArray {
        val tok = tokenizer ?: throw IllegalStateException("Tokenizer not initialized")

        Log.d(TAG, "Tokenizing: ${text.take(100)}...")
        val encoding = tok.encode(text)
        val ids = encoding.ids

        Log.d(TAG, "Token count: ${ids.size}, First 10 tokens: ${ids.take(10).contentToString()}")
        return ids
    }

    /**
     * Decode token IDs back to text using HuggingFace tokenizer
     */
    private fun decode(ids: LongArray): String {
        val tok = tokenizer ?: throw IllegalStateException("Tokenizer not initialized")

        Log.d(TAG, "Decoding ${ids.size} tokens...")
        val text = tok.decode(ids)

        Log.d(TAG, "Decoded: ${text.take(100)}...")
        return text
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
            Log.i(TAG, "🔒 ONNX Runtime resources released")
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LLM", e)
        }
    }
}
