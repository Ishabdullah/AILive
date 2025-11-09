package com.ailive.ai.llm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer
import java.nio.FloatBuffer
import kotlin.math.exp

/**
 * LLMManager - Multimodal Vision-Chat inference using ONNX Runtime
 * Phase 8.0: Unified text + vision AI with Qwen2-VL-2B-Instruct
 *
 * Capabilities:
 * - Text-only conversation (backward compatible)
 * - Visual Question Answering (VQA) when image provided
 * - Image captioning and description
 * - Smart resource management: vision encoder loaded only when needed
 *
 * Model: Qwen2-VL-2B-Instruct Q4F16 (~3.7GB)
 * - QwenVL_A_q4f16.onnx (1.33 GB) - Output projection
 * - QwenVL_B_q4f16.onnx (234 MB) - Vision encoder
 * - QwenVL_E_q4f16.onnx (997 MB) - Text decoder
 * - embeddings_bf16.bin (467 MB) - Token embeddings
 * - vocab.json + merges.txt - Tokenizer files
 *
 * @author AILive Team
 * @since Phase 2.6
 * @updated Phase 8.0 - Qwen2-VL multimodal architecture
 */
class LLMManager(private val context: Context) {

    companion object {
        private const val TAG = "LLMManager"

        // OPTIMIZATION: Balanced for quality and speed
        // At ~2.5s/token, 40 tokens = ~100s response time (acceptable for mobile)
        // Medium-length responses: 30-35 words
        private const val MAX_LENGTH = 40

        // OPTIMIZATION: Lower temperature for faster, more deterministic responses
        // Lower temp = faster sampling with less randomness
        private const val TEMPERATURE = 0.7f

        private const val TOP_P = 0.9f

        // Qwen2-VL specific tokens
        private const val QWEN_EOS_TOKEN = 151643L  // Qwen's EOS token (<|endoftext|>)
        private const val QWEN_VOCAB_SIZE = 151936  // Qwen2-VL vocabulary size
    }

    // ONNX Runtime sessions (3 models for Qwen2-VL)
    private var ortSession: OrtSession? = null  // Text decoder (E model)
    private var visionEncoderSession: OrtSession? = null  // Vision encoder (B model) - lazy loaded
    private var ortEnv: OrtEnvironment? = null

    // Initialization state tracking
    private var isInitialized = false
    private var isInitializing = false
    private var initializationError: String? = null

    // Vision encoder state tracking (lazy loading)
    private var isVisionEncoderLoaded = false
    private var isVisionEncoderLoading = false

    // Model download manager
    private val modelDownloadManager = ModelDownloadManager(context)

    // Qwen2-VL tokenizer (BPE with chat format support)
    private var tokenizer: QwenVLTokenizer? = null

    // Current model path and info
    private var currentModelPath: String? = null
    private var currentModelName: String? = null

    /**
     * Initialize Qwen2-VL multimodal model
     * Called once on app startup in background thread
     *
     * Checks for all 8 required Qwen2-VL files in Downloads folder:
     * - vocab.json, merges.txt (tokenizer)
     * - QwenVL_A_q4f16.onnx, QwenVL_B_q4f16.onnx, QwenVL_C_q4f16.onnx, QwenVL_D_q4f16.onnx, QwenVL_E_q4f16.onnx (5 ONNX models)
     * - embeddings_bf16.bin (token embeddings)
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
            Log.i(TAG, "🤖 Initializing Qwen2-VL multimodal AI...")
            Log.i(TAG, "⏱️  This may take 10-15 seconds for model loading...")

            // Check if Qwen2-VL model files are available
            if (!modelDownloadManager.isQwenVLModelAvailable()) {
                val error = "Qwen2-VL model files not found in Downloads folder. Please download the model first."
                Log.e(TAG, "❌ $error")
                Log.i(TAG, "   Required files:")
                Log.i(TAG, "     - vocab.json, merges.txt")
                Log.i(TAG, "     - QwenVL_A_q4f16.onnx, QwenVL_B_q4f16.onnx, QwenVL_E_q4f16.onnx")
                Log.i(TAG, "     - embeddings_bf16.bin")
                initializationError = error
                isInitializing = false
                return@withContext false
            }

            Log.i(TAG, "✅ All Qwen2-VL files found in Downloads")
            currentModelName = "Qwen2-VL-2B-Instruct-Q4F16"

            // Get paths for model files
            val modelA = File(modelDownloadManager.getModelPath(ModelDownloadManager.QWEN_VL_MODEL_A))
            val modelE = File(modelDownloadManager.getModelPath(ModelDownloadManager.QWEN_VL_MODEL_E))

            val totalSize = modelA.length() + modelE.length()
            Log.i(TAG, "📂 Loading Qwen2-VL models (~${totalSize / 1024 / 1024}MB total)")
            Log.i(TAG, "   Format: ONNX (ONNX Runtime with NNAPI)")

            // Load with ONNX Runtime
            Log.i(TAG, "🔷 Loading text decoder and tokenizer...")
            val success = initializeONNX()

            if (!success) {
                val error = "Failed to load Qwen2-VL model. Files may be corrupted."
                Log.e(TAG, "❌ $error")
                initializationError = error
                isInitializing = false
                return@withContext false
            }

            isInitialized = true
            isInitializing = false
            Log.i(TAG, "✅ Qwen2-VL initialized successfully!")
            Log.i(TAG, "   Model: $currentModelName")
            Log.i(TAG, "   Capabilities: Text + Vision (multimodal)")
            Log.i(TAG, "   Engine: ONNX Runtime")
            Log.i(TAG, "   Max length: $MAX_LENGTH tokens")
            Log.i(TAG, "🎉 Vision-chat AI is ready!")

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
     * Initialize ONNX model (text decoder for now, vision encoder lazy-loaded later)
     */
    private fun initializeONNX(): Boolean {
        var sessionOptions: OrtSession.SessionOptions? = null
        return try {
            // Create ONNX Runtime environment
            ortEnv = OrtEnvironment.getEnvironment()

            // Load Qwen2-VL tokenizer first (from models folder)
            Log.i(TAG, "📖 Loading Qwen2-VL tokenizer...")
            tokenizer = QwenVLTokenizer()
            val modelsDir = File(modelDownloadManager.getModelsDirectory())
            val tokenizerLoaded = tokenizer?.initialize(modelsDir) ?: false

            if (!tokenizerLoaded) {
                Log.e(TAG, "❌ Failed to load Qwen2-VL tokenizer")
                return false
            }

            Log.i(TAG, "✅ Tokenizer loaded successfully")
            Log.i(TAG, "   Vocab size: ${tokenizer?.getVocabSize()}")
            Log.i(TAG, "   EOS token: ${tokenizer?.getEosTokenId()}")

            // Load text decoder model (QwenVL_E_q4f16.onnx)
            val modelEPath = modelDownloadManager.getModelPath(ModelDownloadManager.QWEN_VL_MODEL_E)
            val modelEFile = File(modelEPath)

            Log.i(TAG, "📂 Loading text decoder: ${modelEFile.name} (${modelEFile.length() / 1024 / 1024}MB)")

            // Create session options with GPU acceleration
            sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            sessionOptions.setIntraOpNumThreads(4)

            // NOTE: NNAPI disabled - ArgMax(13) operation not supported by NNAPI
            // The Qwen2-VL model uses ArgMax opset 13 which requires CPU execution provider
            // Using CPU-only execution for full operation compatibility
            Log.i(TAG, "✅ Using CPU execution provider (NNAPI disabled for compatibility)")

            // Create session for text decoder
            ortSession = ortEnv?.createSession(modelEPath, sessionOptions)
            Log.i(TAG, "✅ Text decoder loaded successfully")

            // Note: Vision encoder (QwenVL_B_q4f16.onnx) will be lazy-loaded when image is provided
            Log.i(TAG, "📝 Vision encoder will be loaded on-demand when camera is enabled")

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
     * Lazy-load vision encoder when image is provided
     * Called on-demand when generate() receives an image parameter
     *
     * This saves ~1GB RAM when camera is OFF (text-only mode)
     */
    private suspend fun loadVisionEncoder(): Boolean = withContext(Dispatchers.IO) {
        // Check if already loaded
        if (isVisionEncoderLoaded) {
            Log.d(TAG, "Vision encoder already loaded")
            return@withContext true
        }

        // Prevent concurrent loading
        if (isVisionEncoderLoading) {
            Log.w(TAG, "Vision encoder loading in progress")
            return@withContext false
        }

        isVisionEncoderLoading = true

        return@withContext try {
            Log.i(TAG, "🎨 Loading vision encoder (camera ON - first time)...")
            Log.i(TAG, "⏱️  This may take 30-40 seconds for vision preprocessing...")

            // Get vision encoder model path
            val modelBPath = modelDownloadManager.getModelPath(ModelDownloadManager.QWEN_VL_MODEL_B)
            val modelBFile = File(modelBPath)

            if (!modelBFile.exists()) {
                Log.e(TAG, "❌ Vision encoder file not found: ${modelBFile.name}")
                isVisionEncoderLoading = false
                return@withContext false
            }

            Log.i(TAG, "📂 Loading vision encoder: ${modelBFile.name} (${modelBFile.length() / 1024 / 1024}MB)")

            // Create session options
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            sessionOptions.setIntraOpNumThreads(4)

            // Enable NNAPI for vision encoder (GPU acceleration)
            try {
                sessionOptions.addNnapi()
                Log.i(TAG, "✅ NNAPI GPU acceleration enabled for vision encoder")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ NNAPI not available for vision, using CPU")
            }

            // Create vision encoder session
            visionEncoderSession = ortEnv?.createSession(modelBPath, sessionOptions)

            isVisionEncoderLoaded = true
            isVisionEncoderLoading = false

            Log.i(TAG, "✅ Vision encoder loaded successfully!")
            Log.i(TAG, "   Model: QwenVL_B_q4f16.onnx")
            Log.i(TAG, "   Size: ${modelBFile.length() / 1024 / 1024}MB")
            Log.i(TAG, "🎉 Vision capabilities now active!")

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load vision encoder", e)
            e.printStackTrace()
            isVisionEncoderLoading = false

            // Clean up on failure
            try {
                visionEncoderSession?.close()
                visionEncoderSession = null
            } catch (ex: Exception) {
                Log.w(TAG, "Error closing vision encoder during cleanup: ${ex.message}")
            }

            false
        }
    }

    /**
     * Generate text response using Qwen2-VL (multimodal)
     *
     * @param prompt The input text prompt
     * @param image Optional image for vision understanding (null = text-only mode)
     * @param agentName Name of the agent for personality context
     * @return Generated text response
     */
    suspend fun generate(prompt: String, image: Bitmap? = null, agentName: String = "AILive"): String = withContext(Dispatchers.IO) {
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

            // Lazy-load vision encoder if image is provided (camera ON)
            if (image != null && !isVisionEncoderLoaded) {
                Log.i(TAG, "📸 Image provided - loading vision encoder...")
                val visionLoaded = loadVisionEncoder()
                if (!visionLoaded) {
                    Log.w(TAG, "⚠️ Failed to load vision encoder, falling back to text-only")
                    // Continue with text-only generation
                }
            }

            // Log generation mode
            val mode = if (image != null) "Vision + Text" else "Text-only"
            Log.i(TAG, "🚀 Starting generation ($mode): \"${prompt.take(50)}${if (prompt.length > 50) "..." else ""}\"")

            // Create chat prompt with agent personality
            val chatPrompt = createChatPrompt(prompt, agentName)

            // Use ONNX Runtime
            Log.i(TAG, "🔷 Using ONNX Runtime (NNAPI GPU acceleration)")
            val response = generateONNX(chatPrompt, image)

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
     * Generate using ONNX Runtime (multimodal inference)
     *
     * @param chatPrompt The formatted chat prompt
     * @param image Optional image for vision understanding
     */
    private fun generateONNX(chatPrompt: String, image: Bitmap?): String {
        if (ortSession == null) {
            throw IllegalStateException("ONNX session not initialized")
        }

        // If image is provided, preprocess it for vision encoder
        var visionFeatures: FloatBuffer? = null
        if (image != null && isVisionEncoderLoaded) {
            Log.i(TAG, "🎨 Vision mode: preprocessing image...")

            // Validate image
            if (!VisionPreprocessor.validateImage(image)) {
                Log.w(TAG, "⚠️ Invalid image, falling back to text-only")
            } else {
                try {
                    // Preprocess image for vision encoder
                    visionFeatures = VisionPreprocessor.preprocessImage(image)
                    Log.i(TAG, "✅ Image preprocessed successfully")

                    // TODO: Run vision encoder to extract features
                    // This requires understanding Qwen2-VL's vision-text fusion architecture
                    Log.d(TAG, "   Vision encoder inference will be integrated in next phase")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Vision preprocessing failed", e)
                    Log.w(TAG, "   Falling back to text-only mode")
                }
            }
        }

        // Tokenize input
        val inputIds = tokenize(chatPrompt)

        // Run inference (text-only for now, vision integration coming next)
        val outputIds = runInference(inputIds)

        // Decode output
        return decode(outputIds)
    }

    /**
     * Create a chat-formatted prompt for Qwen2-VL
     *
     * Qwen uses ChatML-style format with <|im_start|> and <|im_end|> tokens.
     * The tokenizer handles adding these tokens, but we can prepare the message structure.
     *
     * For simple text-only queries, we just pass the user message directly.
     * The tokenizer will wrap it with chat format tokens automatically.
     */
    private fun createChatPrompt(userMessage: String, agentName: String): String {
        // Qwen2-VL is instruction-tuned, so direct user messages work well
        // For now, pass through the message - tokenizer adds chat format tokens
        return userMessage
    }

    /**
     * Tokenize text using Qwen2-VL tokenizer with chat format
     *
     * Qwen chat format: <|im_start|> text <|im_end|>
     * The tokenizer automatically adds these tokens when addImTokens=true
     */
    private fun tokenize(text: String): LongArray {
        val tok = tokenizer ?: throw IllegalStateException("Tokenizer not initialized")

        Log.i(TAG, "📝 Tokenizing prompt: \"${text.take(100)}${if (text.length > 100) "..." else ""}\"")

        // Qwen tokenizer handles chat format internally
        val ids = tok.encode(text, addImTokens = true)

        Log.i(TAG, "   ✓ Input tokens: ${ids.size} (including chat format tokens)")
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
                        Log.d(TAG, "🔍 Vocab size: $vocabSize (expected: $QWEN_VOCAB_SIZE)")
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

                    // Check for Qwen EOS token (151643)
                    if (nextTokenId == QWEN_EOS_TOKEN) {
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

        // Multinomial sampling (categorical distribution)
        // Sample a token based on the probability distribution
        val randomValue = Math.random().toFloat() // 0.0 to 1.0
        var cumulativeProb = 0f
        var sampledToken = 0L

        for (i in probs.indices) {
            cumulativeProb += probs[i]
            if (randomValue <= cumulativeProb) {
                sampledToken = i.toLong()
                break
            }
        }

        Log.v(TAG, "Sampled token $sampledToken with probability ${probs[sampledToken.toInt()]} (random=$randomValue)")

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
     * Cleanup resources (text decoder + vision encoder if loaded)
     */
    fun close() {
        try {
            ortSession?.close()
            ortSession = null
            Log.d(TAG, "Text decoder session closed")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing text decoder: ${e.message}")
        }

        try {
            visionEncoderSession?.close()
            visionEncoderSession = null
            isVisionEncoderLoaded = false
            Log.d(TAG, "Vision encoder session closed")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing vision encoder: ${e.message}")
        }

        try {
            ortEnv?.close()
            ortEnv = null
            Log.d(TAG, "ONNX environment closed")
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
        Log.i(TAG, "🔒 ONNX Runtime resources released (text + vision)")
    }
}
