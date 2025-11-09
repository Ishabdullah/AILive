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

        // GPT-2 specific tokens (from config.json)
        private const val GPT2_EOS_TOKEN = 50256L  // GPT-2 uses 50256 for EOS
        private const val GPT2_PAD_TOKEN = 0L      // Pad token
        private const val GPT2_VOCAB_SIZE = 50257  // GPT-2 vocabulary size
    }

    // ONNX Runtime (primary inference engine)
    private var ortSession: OrtSession? = null
    private var ortEnv: OrtEnvironment? = null

    // Initialization state tracking
    private var isInitialized = false
    private var isInitializing = false
    private var initializationError: String? = null

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
        // Prevent duplicate initialization
        if (isInitialized) {
            Log.i(TAG, "LLM already initialized")
            return@withContext true
        }

        if (isInitializing) {
            Log.w(TAG, "LLM initialization already in progress")
            return@withContext false
        }

        isInitializing = true
        initializationError = null

        try {
            Log.i(TAG, "🤖 Initializing LLM (ONNX-only mode)...")
            Log.i(TAG, "⏱️  This may take 5-10 seconds for model loading...")

            // Check if ANY model is available
            val availableModels = modelDownloadManager.getAvailableModels()
            if (availableModels.isEmpty()) {
                val error = "No model files found. Please download or import a model."
                Log.e(TAG, "❌ $error")
                initializationError = error
                isInitializing = false
                return@withContext false
            }

            // ONNX-only: Look for .onnx models
            val onnxModel = availableModels.firstOrNull { it.name.endsWith(".onnx", ignoreCase = true) }

            if (onnxModel == null) {
                val foundModels = availableModels.joinToString { it.name }
                val error = "No ONNX models found. This version only supports .onnx format. Found: $foundModels"
                Log.e(TAG, "❌ No ONNX models found")
                Log.i(TAG, "   This version only supports .onnx format")
                Log.i(TAG, "   Found models: $foundModels")
                Log.i(TAG, "   Please download an ONNX model")
                initializationError = error
                isInitializing = false
                return@withContext false
            }

            val modelFile = onnxModel
            currentModelPath = modelFile.absolutePath
            currentModelName = modelFile.name

            Log.i(TAG, "📂 Loading model: ${modelFile.name} (${modelFile.length() / 1024 / 1024}MB)")
            Log.i(TAG, "   Format: ONNX (ONNX Runtime with NNAPI)")

            // Verify model integrity before attempting to load
            Log.i(TAG, "🔍 Verifying model integrity...")
            if (!ModelIntegrityVerifier.verify(modelFile.name)) {
                val error = "Model integrity check failed. File may be corrupted. Please re-download."
                Log.e(TAG, "❌ $error")
                initializationError = error
                isInitializing = false
                return@withContext false
            }

            // Load with ONNX Runtime
            Log.i(TAG, "🔷 Loading with ONNX Runtime...")
            val success = initializeONNX(modelFile)

            if (!success) {
                val error = "Failed to load ONNX model. The model file may be corrupted."
                Log.e(TAG, "❌ $error")
                initializationError = error
                isInitializing = false
                return@withContext false
            }

            isInitialized = true
            isInitializing = false
            Log.i(TAG, "✅ LLM initialized successfully!")
            Log.i(TAG, "   Model: $currentModelName")
            Log.i(TAG, "   Size: ${modelFile.length() / 1024 / 1024}MB")
            Log.i(TAG, "   Engine: ONNX Runtime")
            Log.i(TAG, "   Max length: $MAX_LENGTH tokens")
            Log.i(TAG, "🎉 AI responses are now powered by the language model!")

            true
        } catch (e: Exception) {
            val error = "LLM initialization failed: ${e.message}"
            Log.e(TAG, "❌ Failed to initialize LLM", e)
            e.printStackTrace()
            initializationError = error
            isInitializing = false
            false
        }
    }

    /**
     * Initialize ONNX model (primary inference engine)
     */
    private fun initializeONNX(modelFile: File): Boolean {
        var sessionOptions: OrtSession.SessionOptions? = null
        return try {
            // Create ONNX Runtime environment
            ortEnv = OrtEnvironment.getEnvironment()

            // Create session options with GPU acceleration
            sessionOptions = OrtSession.SessionOptions()
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
            // Clean up resources on failure
            try {
                ortSession?.close()
                ortSession = null
            } catch (ex: Exception) {
                Log.w(TAG, "Error closing session during cleanup: ${ex.message}")
            }
            false
        } finally {
            // SessionOptions should be closed after session creation
            // Note: Don't close sessionOptions here as it's still needed by the session
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
        // Check initialization status
        when {
            isInitializing -> {
                Log.w(TAG, "⏳ LLM still initializing (loading model)...")
                throw IllegalStateException("LLM is still loading. Please wait a moment.")
            }
            !isInitialized -> {
                val errorMsg = initializationError ?: "LLM not initialized"
                Log.w(TAG, "⚠️ $errorMsg")
                throw IllegalStateException(errorMsg)
            }
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
     *
     * IMPORTANT: GPT-2 format - simpler than ChatML, no special chat tokens
     * GPT-2 is a causal LM without instruction tuning, so we use natural language formatting
     */
    private fun createChatPrompt(userMessage: String, agentName: String): String {
        // Unified personality for all interactions
        val personality = """You are AILive, a unified on-device AI companion.
You are ONE cohesive intelligence with multiple capabilities (vision, emotion, memory, device control).
Speak naturally as a single character, never as separate agents or systems.
Be warm, helpful, concise, and conversational."""

        // GPT-2 format - simple concatenation with clear structure
        // No special tokens needed, just natural text flow
        return """System: $personality

User: $userMessage

Assistant:"""
    }

    /**
     * Tokenize text using HuggingFace BPE tokenizer
     */
    private fun tokenize(text: String): LongArray {
        val tok = tokenizer ?: throw IllegalStateException("Tokenizer not initialized")

        Log.d(TAG, "Tokenizing: ${text.take(100)}...")
        val encoding = tok.encode(text)
        val ids = encoding.ids

        Log.d(TAG, "Token count: ${ids.size}, First 10 tokens: ${ids.take(10).joinToString()}")
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
     * Run ONNX inference with proper autoregressive generation
     *
     * CRITICAL FIX: Implements proper transformer generation loop
     * - Feeds generated tokens back as new inputs
     * - Samples from last position logits only
     * - Stops on EOS token (GPT-2 specific: 50256)
     * - Includes attention_mask for proper GPT-2 inference
     */
    private fun runInference(inputIds: LongArray): LongArray {
        val session = ortSession ?: throw IllegalStateException("Session not initialized")

        val generatedIds = mutableListOf<Long>()
        val currentSequence = inputIds.toMutableList()

        try {
            // Autoregressive generation loop
            for (step in 0 until MAX_LENGTH) {
                var inputTensor: OnnxTensor? = null
                var attentionMaskTensor: OnnxTensor? = null
                var outputs: OrtSession.Result? = null

                try {
                    val seqLen = currentSequence.size.toLong()
                    val shape = longArrayOf(1, seqLen)

                    // Create input_ids tensor
                    inputTensor = OnnxTensor.createTensor(
                        ortEnv,
                        LongBuffer.wrap(currentSequence.toLongArray()),
                        shape
                    )

                    // Create attention_mask tensor (all 1s for GPT-2)
                    val attentionMask = LongArray(currentSequence.size) { 1L }
                    attentionMaskTensor = OnnxTensor.createTensor(
                        ortEnv,
                        LongBuffer.wrap(attentionMask),
                        shape
                    )

                    // Run model with both input_ids and attention_mask
                    val inputs = mapOf(
                        "input_ids" to inputTensor,
                        "attention_mask" to attentionMaskTensor
                    )
                    outputs = session.run(inputs)

                    // Get logits tensor: shape [batch_size, seq_len, vocab_size]
                    // GPT-2 decoder_model.onnx outputs "logits" as first output
                    val outputTensor = outputs[0] as OnnxTensor
                    val logitsBuffer = outputTensor.floatBuffer

                    // Extract logits for LAST position only
                    val vocabSize = outputTensor.info.shape[2].toInt()
                    val lastPosLogits = extractLastPositionLogits(logitsBuffer, currentSequence.size, vocabSize)

                    // Sample next token from last position logits
                    val nextTokenId = sampleNextToken(lastPosLogits)

                    // Add to generated sequence
                    generatedIds.add(nextTokenId)
                    currentSequence.add(nextTokenId)

                    Log.v(TAG, "Step $step: Generated token $nextTokenId")

                    // Check for GPT-2 EOS token (50256)
                    if (nextTokenId == GPT2_EOS_TOKEN) {
                        Log.d(TAG, "GPT-2 EOS token detected, stopping generation")
                        break
                    }

                } finally {
                    // Clean up resources for this step
                    outputs?.close()
                    inputTensor?.close()
                    attentionMaskTensor?.close()
                }
            }

            Log.d(TAG, "Generated ${generatedIds.size} tokens")
            return generatedIds.toLongArray()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in autoregressive generation", e)
            Log.e(TAG, "   Exception: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Extract logits for the last position from ONNX output
     *
     * Output shape: [batch_size, seq_len, vocab_size] = [1, seq_len, vocab_size]
     * We need logits for position [0, seq_len-1, :] (last position in sequence)
     */
    private fun extractLastPositionLogits(logitsBuffer: FloatBuffer, seqLen: Int, vocabSize: Int): FloatArray {
        val lastPosLogits = FloatArray(vocabSize)

        // Calculate offset to last position: (seqLen - 1) * vocabSize
        val offset = (seqLen - 1) * vocabSize

        // Set buffer position and read vocab_size floats
        logitsBuffer.position(offset)
        logitsBuffer.get(lastPosLogits, 0, vocabSize)

        return lastPosLogits
    }

    /**
     * Sample next token using temperature and softmax
     *
     * FIXED: Now takes FloatArray (from extractLastPositionLogits)
     * Properly applies softmax with numerical stability
     *
     * @param logits Raw logits for vocabulary (shape: [vocab_size])
     * @return Sampled token ID
     */
    private fun sampleNextToken(logits: FloatArray): Long {
        val vocabSize = logits.size

        // Apply temperature scaling
        val scaledLogits = FloatArray(vocabSize) { i ->
            logits[i] / TEMPERATURE
        }

        // Apply softmax with numerical stability (subtract max for stability)
        val maxLogit = scaledLogits.maxOrNull() ?: 0f
        val expLogits = FloatArray(vocabSize) { i ->
            exp((scaledLogits[i] - maxLogit).toDouble()).toFloat()
        }

        val sumExp = expLogits.sum()
        val probs = FloatArray(vocabSize) { i ->
            expLogits[i] / sumExp
        }

        // Greedy sampling (argmax) - most reliable for small models
        val sampledToken = probs.indices.maxByOrNull { probs[it] }?.toLong() ?: 0L

        Log.v(TAG, "Sampled token $sampledToken with probability ${probs[sampledToken.toInt()]}")

        return sampledToken
    }


    /**
     * Check if a model is available (checks for ANY model, GGUF or ONNX)
     */
    fun isModelAvailable(): Boolean = modelDownloadManager.isModelAvailable(modelName = null)

    /**
     * Check if LLM is currently initializing
     */
    fun isInitializing(): Boolean = isInitializing

    /**
     * Get initialization error message if failed
     */
    fun getInitializationError(): String? = initializationError

    /**
     * Check if LLM is ready to use
     */
    fun isReady(): Boolean = isInitialized

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
            ortSession = null
            Log.d(TAG, "Session closed")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing session: ${e.message}")
        }

        try {
            ortEnv?.close()
            ortEnv = null
            Log.d(TAG, "Environment closed")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing environment: ${e.message}")
        }

        try {
            tokenizer?.close()
            tokenizer = null
            Log.d(TAG, "Tokenizer closed")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing tokenizer: ${e.message}")
        }

        isInitialized = false
        currentModelPath = null
        currentModelName = null
        Log.i(TAG, "🔒 ONNX Runtime resources released")
    }
}
