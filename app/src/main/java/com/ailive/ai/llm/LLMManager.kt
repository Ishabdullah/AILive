package com.ailive.ai.llm

import android.content.Context
import android.graphics.Bitmap
import android.llama.cpp.LLamaAndroid
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * LLMManager - On-device LLM inference using official llama.cpp Android
 * Phase 9.0: Qwen2-VL-2B-Instruct GGUF with native llama.cpp
 *
 * Capabilities:
 * - Text-only conversation (current implementation)
 * - Visual Question Answering (coming soon with mmproj)
 *
 * Model: Qwen2-VL-2B-Instruct Q4_K_M GGUF (~986MB)
 * - Single GGUF file with built-in tokenizer
 * - Q4_K_M quantization: 4-bit with medium quality
 *
 * Benefits:
 * - Native ARM64 libraries (no UnsatisfiedLinkError)
 * - Official llama.cpp implementation
 * - Streaming responses via Kotlin Flow
 * - Single file vs 8 files (986MB vs 3.7GB)
 * - Better mobile optimizations
 *
 * @author AILive Team
 * @since Phase 2.6
 * @updated Phase 9.0 - Using official llama.cpp Android bindings
 */
class LLMManager(private val context: Context) {

    companion object {
        private const val TAG = "LLMManager"
    }

    // Official llama.cpp Android instance (singleton)
    private val llamaAndroid = LLamaAndroid.instance()

    // Initialization state tracking
    private var isInitialized = false
    private var isInitializing = false
    private var initializationError: String? = null

    // Model download manager
    private val modelDownloadManager = ModelDownloadManager(context)

    // Current model info
    private var currentModelName: String? = null

    /**
     * Initialize Qwen2-VL GGUF model using official llama.cpp Android
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
            Log.i(TAG, "🤖 Initializing Qwen2-VL with llama.cpp Android...")
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
            val modelFile = java.io.File(modelPath)

            Log.i(TAG, "📂 Loading model: ${modelFile.name}")
            Log.i(TAG, "   Size: ${modelFile.length() / 1024 / 1024}MB")
            Log.i(TAG, "   Format: GGUF (Q4_K_M quantization)")
            Log.i(TAG, "   Engine: llama.cpp (official Android)")

            // Load model using official llama.cpp Android
            Log.i(TAG, "📥 Loading llama.cpp model...")
            llamaAndroid.load(modelPath)

            isInitialized = true
            isInitializing = false

            Log.i(TAG, "✅ Qwen2-VL initialized successfully!")
            Log.i(TAG, "   Model: $currentModelName")
            Log.i(TAG, "   Capabilities: Text-only (vision coming with mmproj)")
            Log.i(TAG, "   Engine: llama.cpp (official Android bindings)")
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

            // Generate response using llama.cpp (streaming)
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
     * Generate text using official llama.cpp Android (streaming)
     *
     * @param prompt The formatted chat prompt
     * @return Generated text
     */
    private suspend fun generateWithLlama(prompt: String): String {
        Log.i(TAG, "🔷 Using llama.cpp for inference (streaming)")

        val response = StringBuilder()
        var tokenCount = 0

        // Use the official llama.cpp Android Flow API
        llamaAndroid.send(prompt, formatChat = false)
            .catch { e ->
                Log.e(TAG, "❌ Generation error", e)
                throw e
            }
            .collect { token ->
                response.append(token)
                tokenCount++

                // Log progress every 10 tokens
                if (tokenCount % 10 == 0) {
                    Log.d(TAG, "   Token $tokenCount generated")
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
            "Model: $currentModelName\nEngine: llama.cpp (official Android)"
        } else {
            "No model loaded"
        }
    }

    /**
     * Cleanup resources
     */
    fun close() {
        runBlocking {
            try {
                llamaAndroid.unload()
                Log.d(TAG, "llama.cpp model unloaded")
            } catch (e: Exception) {
                Log.w(TAG, "Error unloading model: ${e.message}")
            }
        }

        isInitialized = false
        currentModelName = null
        Log.i(TAG, "🔒 llama.cpp resources released")
    }
}
