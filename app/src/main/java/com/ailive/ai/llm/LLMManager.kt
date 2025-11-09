package com.ailive.ai.llm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import de.kherud.llama.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LLMManager - Multimodal Vision-Chat inference using llama.cpp
 * Phase 9.0: Unified text + vision AI with Qwen2-VL-2B-Instruct GGUF
 *
 * Capabilities:
 * - Text-only conversation (current implementation)
 * - Visual Question Answering (VQA) when image provided (coming soon with mmproj)
 * - Image captioning and description (coming soon)
 *
 * Model: Qwen2-VL-2B-Instruct Q4_K_M GGUF (~986MB)
 * - Single GGUF file with built-in tokenizer
 * - Q4_K_M quantization: 4-bit with medium quality
 * - Much simpler than ONNX (no separate vocab files, embeddings, etc.)
 *
 * Benefits over ONNX:
 * - Single file vs 8 files (986MB vs 3.7GB)
 * - Built-in tokenizer (no separate vocab.json/merges.txt)
 * - Better mobile support (no ArgMax incompatibility)
 * - Simpler API (no manual autoregressive loop)
 * - Faster inference with llama.cpp optimizations
 *
 * @author AILive Team
 * @since Phase 2.6
 * @updated Phase 9.0 - Switched from ONNX Runtime to llama.cpp + GGUF
 */
class LLMManager(private val context: Context) {

    companion object {
        private const val TAG = "LLMManager"

        // Generation parameters (balanced for mobile)
        private const val MAX_LENGTH = 40  // Max tokens to generate
        private const val TEMPERATURE = 0.7f  // Sampling temperature
        private const val TOP_P = 0.9f  // Top-p (nucleus) sampling
        private const val TOP_K = 40  // Top-k sampling
    }

    // llama.cpp model instance
    private var llamaModel: LlamaModel? = null

    // Initialization state tracking
    private var isInitialized = false
    private var isInitializing = false
    private var initializationError: String? = null

    // Model download manager
    private val modelDownloadManager = ModelDownloadManager(context)

    // Current model info
    private var currentModelName: String? = null

    /**
     * Initialize Qwen2-VL GGUF model using llama.cpp
     * Called once on app startup in background thread
     *
     * Checks for GGUF model file in app-private storage:
     * - Qwen2-VL-2B-Instruct-Q4_K_M.gguf (986MB)
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
            Log.i(TAG, "🤖 Initializing Qwen2-VL with llama.cpp...")
            Log.i(TAG, "⏱️  This may take 10-15 seconds for model loading...")

            // Check if Qwen2-VL GGUF model is available
            if (!modelDownloadManager.isQwenVLModelAvailable()) {
                val error = "Qwen2-VL GGUF model not found. Please download the model first."
                Log.e(TAG, "❌ $error")
                Log.i(TAG, "   Required file: ${ModelDownloadManager.QWEN_VL_MODEL_GGUF}")
                initializationError = error
                isInitializing = false
                return@withContext false
            }

            Log.i(TAG, "✅ GGUF model file found in app-private storage")
            currentModelName = "Qwen2-VL-2B-Instruct-Q4_K_M"

            // Get model file path
            val modelPath = modelDownloadManager.getModelPath(ModelDownloadManager.QWEN_VL_MODEL_GGUF)
            val modelFile = File(modelPath)

            Log.i(TAG, "📂 Loading model: ${modelFile.name}")
            Log.i(TAG, "   Size: ${modelFile.length() / 1024 / 1024}MB")
            Log.i(TAG, "   Format: GGUF (Q4_K_M quantization)")
            Log.i(TAG, "   Engine: llama.cpp")

            // Create model parameters
            val modelParams = ModelParameters()
                .setModel(modelPath)
                .setGpuLayers(0)  // CPU-only for now (will add GPU acceleration later)

            Log.i(TAG, "🔧 Model parameters:")
            Log.i(TAG, "   GPU layers: 0 (CPU-only)")
            Log.i(TAG, "   Context size: default (2048)")

            // Load model
            Log.i(TAG, "📥 Loading llama.cpp model...")
            llamaModel = LlamaModel(modelParams)

            isInitialized = true
            isInitializing = false

            Log.i(TAG, "✅ Qwen2-VL initialized successfully!")
            Log.i(TAG, "   Model: $currentModelName")
            Log.i(TAG, "   Capabilities: Text-only (vision coming with mmproj)")
            Log.i(TAG, "   Engine: llama.cpp")
            Log.i(TAG, "   Max length: $MAX_LENGTH tokens")
            Log.i(TAG, "🎉 AI is ready!")

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
     * Generate text response using Qwen2-VL (text-only for now)
     *
     * @param prompt The input text prompt
     * @param image Optional image for vision understanding (not yet supported - requires mmproj)
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

        // Warn if image provided (vision not yet supported)
        if (image != null) {
            Log.w(TAG, "⚠️ Vision input not yet supported (requires mmproj file)")
            Log.i(TAG, "   Continuing with text-only generation...")
        }

        try {
            val startTime = System.currentTimeMillis()

            Log.i(TAG, "🚀 Starting generation (Text-only): \"${prompt.take(50)}${if (prompt.length > 50) "..." else ""}\"")

            // Create chat prompt with agent personality
            val chatPrompt = createChatPrompt(prompt, agentName)

            // Generate response using llama.cpp
            val response = generateWithLlama(chatPrompt)

            val totalTime = (System.currentTimeMillis() - startTime) / 1000.0
            Log.i(TAG, "✅ Generation complete in ${totalTime}s")
            Log.i(TAG, "   Response: \"${response.take(100)}${if (response.length > 100) "..." else ""}\"")

            return@withContext response.trim().ifEmpty {
                "I'm processing your request. Please try again."
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Generation failed", e)
            e.printStackTrace()
            throw e  // Let caller handle fallback
        }
    }

    /**
     * Generate text using llama.cpp
     *
     * @param prompt The formatted chat prompt
     * @return Generated text
     */
    private fun generateWithLlama(prompt: String): String {
        val model = llamaModel ?: throw IllegalStateException("Model not initialized")

        Log.i(TAG, "🔷 Using llama.cpp for inference")

        // Create inference parameters
        val inferParams = InferenceParameters(prompt)
            .setTemperature(TEMPERATURE)
            .setTopP(TOP_P)
            .setTopK(TOP_K)
            .setNPredict(MAX_LENGTH)  // Max tokens to generate
            .setPenalizeNl(false)  // Don't penalize newlines
            .setStopStrings("<|im_end|>", "<|endoftext|>")  // Qwen stop tokens

        Log.i(TAG, "🔧 Inference parameters:")
        Log.i(TAG, "   Temperature: $TEMPERATURE")
        Log.i(TAG, "   Top-P: $TOP_P")
        Log.i(TAG, "   Top-K: $TOP_K")
        Log.i(TAG, "   Max tokens: $MAX_LENGTH")

        // Generate (llama.cpp handles tokenization, sampling, and decoding internally)
        val response = StringBuilder()
        var tokenCount = 0

        for (output in model.generate(inferParams)) {
            response.append(output)
            tokenCount++

            // Log progress every 10 tokens
            if (tokenCount % 10 == 0) {
                val progress = (tokenCount * 100) / MAX_LENGTH
                Log.i(TAG, "   Token $tokenCount/$MAX_LENGTH ($progress%)")
            }
        }

        Log.i(TAG, "✓ Generated $tokenCount tokens")

        return response.toString()
    }

    /**
     * Create a chat-formatted prompt for Qwen2-VL
     *
     * Qwen uses ChatML-style format with <|im_start|> and <|im_end|> tokens.
     * Format: <|im_start|>system\n{system}<|im_end|>\n<|im_start|>user\n{user}<|im_end|>\n<|im_start|>assistant\n
     */
    private fun createChatPrompt(userMessage: String, agentName: String): String {
        // Qwen2-VL chat template
        return buildString {
            append("<|im_start|>system\n")
            append("You are $agentName, a helpful AI assistant.")
            append("<|im_end|>\n")
            append("<|im_start|>user\n")
            append(userMessage)
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
    }

    /**
     * Check if a model is available (GGUF model)
     */
    fun isModelAvailable(): Boolean = modelDownloadManager.isQwenVLModelAvailable()

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
            "Model: $currentModelName\nEngine: llama.cpp"
        } else {
            "No model loaded"
        }
    }

    /**
     * Cleanup resources
     */
    fun close() {
        try {
            llamaModel?.close()
            llamaModel = null
            Log.d(TAG, "llama.cpp model closed")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing model: ${e.message}")
        }

        isInitialized = false
        currentModelName = null
        Log.i(TAG, "🔒 llama.cpp resources released")
    }
}
