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

        // OPTIMIZATION: Balanced for quality and speed
        // At ~2.5s/token, 20 tokens = ~50s response time (acceptable for mobile)
        // Short sentence responses: 15-18 words
        private const val MAX_LENGTH = 20

        // OPTIMIZATION: Lower temperature for faster, more deterministic responses
        // Lower temp = faster sampling with less randomness
        private const val TEMPERATURE = 0.7f

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

    // Android-compatible GPT-2 tokenizer (no native dependencies)
    private var tokenizer: SimpleGPT2Tokenizer? = null

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

            // Load simple Android-compatible tokenizer
            Log.i(TAG, "📖 Loading Android-compatible tokenizer...")
            tokenizer = SimpleGPT2Tokenizer(context)
            val tokenizerLoaded = tokenizer?.initialize() ?: false

            if (!tokenizerLoaded) {
                Log.e(TAG, "❌ Failed to load tokenizer")
                return false
            }

            Log.i(TAG, "✅ Tokenizer loaded successfully")
            Log.i(TAG, "   Vocab size: ${tokenizer?.getVocabSize()}")
            Log.i(TAG, "   EOS token: ${tokenizer?.getEosTokenId()}")

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
            val startTime = System.currentTimeMillis()

            // Create chat prompt with agent personality
            val chatPrompt = createChatPrompt(prompt, agentName)
            Log.i(TAG, "🚀 Starting generation for: \"${prompt.take(50)}${if (prompt.length > 50) "..." else ""}\"")

            // Use ONNX Runtime
            Log.i(TAG, "🔷 Using ONNX Runtime (NNAPI GPU acceleration)")
            val response = generateONNX(chatPrompt)

            val totalTime = (System.currentTimeMillis() - startTime) / 1000.0
            Log.i(TAG, "✅ Generation complete in ${totalTime}s")
            Log.i(TAG, "   Response: \"${response.take(100)}${if (response.length > 100) "..." else ""}\"")

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
        // OPTIMIZED: Minimal prompt for fastest response (reduces input from ~800 to ~20 tokens)
        // GPT-2 format - simple and concise
        return """Q: $userMessage
A:"""
    }

    /**
     * Tokenize text using Android-compatible GPT-2 tokenizer
     */
    private fun tokenize(text: String): LongArray {
        val tok = tokenizer ?: throw IllegalStateException("Tokenizer not initialized")

        Log.i(TAG, "📝 Tokenizing prompt: \"${text.take(100)}${if (text.length > 100) "..." else ""}\"")
        val ids = tok.encode(text)

        Log.i(TAG, "   ✓ Input tokens: ${ids.size} (optimized from ~800 tokens)")
        Log.d(TAG, "   First 10 token IDs: ${ids.take(10).joinToString()}")
        return ids
    }

    /**
     * Decode token IDs back to text using Android-compatible tokenizer
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

        Log.i(TAG, "🎯 Starting autoregressive generation")
        Log.i(TAG, "   Input: ${inputIds.size} tokens | Max output: $MAX_LENGTH tokens")

        val inferenceStartTime = System.currentTimeMillis()
        val generatedIds = mutableListOf<Long>()
        val currentSequence = inputIds.toMutableList()

        try {
            // Autoregressive generation loop
            for (step in 0 until MAX_LENGTH) {
                val stepStartTime = System.currentTimeMillis()
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

                    if (step == 0) {
                        Log.d(TAG, "🔍 First step - running model with inputs: ${inputs.keys}")
                    }

                    outputs = session.run(inputs)

                    if (step == 0) {
                        Log.d(TAG, "🔍 Model outputs: ${outputs.size()} tensors")
                        outputs.forEach { output ->
                            val tensor = output.value as? OnnxTensor
                            if (tensor != null) {
                                Log.d(TAG, "   Output '${output.key}': shape ${tensor.info.shape.contentToString()}")
                            }
                        }
                    }

                    // Get logits tensor: shape [batch_size, seq_len, vocab_size]
                    // GPT-2 decoder_model.onnx outputs "logits" as first output
                    val outputTensor = outputs[0] as OnnxTensor
                    val logitsBuffer = outputTensor.floatBuffer

                    // Extract logits for LAST position only
                    val vocabSize = outputTensor.info.shape[2].toInt()

                    if (step == 0) {
                        Log.d(TAG, "🔍 Vocab size: $vocabSize (expected: $GPT2_VOCAB_SIZE)")
                    }

                    val lastPosLogits = extractLastPositionLogits(logitsBuffer, currentSequence.size, vocabSize)

                    // Sample next token from last position logits
                    val nextTokenId = sampleNextToken(lastPosLogits)

                    // Add to generated sequence
                    generatedIds.add(nextTokenId)
                    currentSequence.add(nextTokenId)

                    val stepTime = (System.currentTimeMillis() - stepStartTime) / 1000.0

                    // Log progress every 5 tokens
                    if (step % 5 == 0 || step < 3) {
                        val progress = ((step + 1) * 100) / MAX_LENGTH
                        Log.i(TAG, "   Token ${step + 1}/$MAX_LENGTH ($progress%) - ${stepTime}s - ID: $nextTokenId")
                    }

                    // Check for GPT-2 EOS token (50256)
                    if (nextTokenId == GPT2_EOS_TOKEN) {
                        Log.i(TAG, "✓ EOS token detected at step $step - generation complete")
                        break
                    }

                } finally {
                    // Clean up resources for this step
                    outputs?.close()
                    inputTensor?.close()
                    attentionMaskTensor?.close()
                }
            }

            val totalInferenceTime = (System.currentTimeMillis() - inferenceStartTime) / 1000.0
            val tokensPerSecond = if (totalInferenceTime > 0) generatedIds.size / totalInferenceTime else 0.0

            Log.i(TAG, "✅ Generation complete:")
            Log.i(TAG, "   Tokens generated: ${generatedIds.size}")
            Log.i(TAG, "   Total time: ${totalInferenceTime}s")
            Log.i(TAG, "   Speed: %.2f tokens/sec".format(tokensPerSecond))
            Log.d(TAG, "   Token IDs: ${generatedIds.take(20).joinToString()}")

            return generatedIds.toLongArray()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in autoregressive generation", e)
            Log.e(TAG, "   Exception: ${e.javaClass.simpleName}: ${e.message}")
            Log.e(TAG, "   Current sequence length: ${currentSequence.size}")
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
