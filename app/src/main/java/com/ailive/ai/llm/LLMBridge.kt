package com.ailive.ai.llm

import android.util.Log

/**
 * LLMBridge - JNI interface to native llama.cpp library
 *
 * Based on SmolChat architecture - provides Kotlin ↔ C++ bridge for GGUF models
 *
 * @author AILive Team
 * @since Phase 7.9 - GGUF Support via JNI
 */
class LLMBridge {

    companion object {
        private const val TAG = "LLMBridge"

        @Volatile
        private var isLibraryLoaded = false
        private var libraryLoadError: String? = null

        // Load native library
        init {
            try {
                System.loadLibrary("ailive_llm")
                isLibraryLoaded = true
                Log.i(TAG, "✅ Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                isLibraryLoaded = false
                libraryLoadError = e.message ?: "Unknown error"
                Log.e(TAG, "❌ CRITICAL: Failed to load native library", e)
                Log.e(TAG, "   Error: ${e.message}")
                Log.e(TAG, "   This APK was likely built without NDK/CMake enabled")
                Log.e(TAG, "   The app will crash if you try to use the LLM")
            }
        }

        /**
         * Check if native library is loaded
         * @return true if libailive_llm.so is loaded and available
         */
        fun isLibraryAvailable(): Boolean = isLibraryLoaded

        /**
         * Get the library load error message if it failed
         * @return error message or null if loaded successfully
         */
        fun getLibraryError(): String? = libraryLoadError
    }

    /**
     * Load GGUF model from file path
     *
     * @param modelPath Absolute path to .gguf file
     * @param contextSize Context window size (default 2048)
     * @return true if loaded successfully
     */
    external fun nativeLoadModel(modelPath: String, contextSize: Int = 2048): Boolean

    /**
     * Generate text completion
     * 
     * ===== NATIVE LLM RESPONSE GENERATION =====
     * This is the core JNI function that calls llama.cpp to generate AI responses.
     * It bridges Kotlin code to the native C++ implementation for actual text generation.
     * 
     * RESPONSE PROCESSING:
     * - Takes user prompt and converts to AI response via native code
     * - Uses llama.cpp library for efficient on-device inference
     * - Returns raw generated text that will be displayed to user
     * 
     * ERROR HANDLING:
     * - Native code handles generation failures gracefully
     * - Empty string returned if generation fails
     * - Exceptions handled by Kotlin wrapper layer
     * 
     * USER IMPACT:
     * - This function directly determines what response user sees
     * - Performance affects response time experienced by user
     * - Quality of response depends on model and parameters
     *
     * @param prompt Input text from user (formatted for chat template)
     * @param maxTokens Maximum tokens to generate (default 80) - controls response length
     * @return Generated text response for display to user
     */

    /**
     * Generate text completion with image input (multimodal)
     *
     * @param prompt Input text
     * @param imageBytes Raw image data (e.g., JPEG, PNG)
     * @param maxTokens Maximum tokens to generate
     * @return Generated text
     */
    external fun nativeGenerateWithImage(prompt: String, imageBytes: ByteArray, maxTokens: Int = 80): String

    /**
     * Generate an embedding for a given prompt.
     *
     * @param prompt Input text
     * @return A float array representing the embedding, or null on failure.
     */
    external fun nativeGenerateEmbedding(prompt: String): FloatArray?

    /**
     * Free model resources
     */
    external fun nativeFreeModel()

    /**
     * Check if model is loaded
     */
    external fun nativeIsLoaded(): Boolean

    /**
     * Kotlin-friendly wrapper for model loading
     */
    fun loadModel(modelPath: String, contextSize: Int = 2048): Boolean {
        // CRITICAL: Check if native library is loaded first
        if (!isLibraryLoaded) {
            val error = "Cannot load model: Native library not loaded (${libraryLoadError})"
            Log.e(TAG, "❌ $error")
            return false
        }

        Log.i(TAG, "📂 Loading model: $modelPath")
        Log.i(TAG, "   Context size: $contextSize")

        val result = nativeLoadModel(modelPath, contextSize)

        if (result) {
            Log.i(TAG, "✅ Model loaded successfully!")
        } else {
            Log.e(TAG, "❌ Failed to load model")
        }

        return result
    }

    /**
     * Kotlin-friendly wrapper for text generation
     * 
     * ===== LLM RESPONSE GENERATION WRAPPER =====
     * This function provides a safe Kotlin interface to the native generation function.
     * It handles validation, error checking, and ensures reliable response delivery.
     * 
     * RESPONSE SAFETY CHECKS:
     * - Validates native library is loaded before generation
     * - Checks model state to prevent crashes
     * - Provides clear error messages for debugging
     * 
     * USER EXPERIENCE PROTECTION:
     * - Prevents app crashes with proper validation
     * - Returns empty string if model not ready (graceful degradation)
     * - Logs generation progress for monitoring
     * 
     * RESPONSE DELIVERY:
     * - Calls native function for actual AI response generation
     * - Returns final response to LLMManager for user display
     * - Handles any native-level errors transparently
     */
    fun generate(prompt: String, maxTokens: Int = 80): String {
        // CRITICAL: Check if native library is loaded first
        if (!isLibraryLoaded) {
            val error = "Cannot generate: Native library not loaded (${libraryLoadError})"
            Log.e(TAG, "❌ $error")
            throw UnsatisfiedLinkError(error)
        }

        if (!nativeIsLoaded()) {
            Log.w(TAG, "⚠️ Model not loaded")
            return ""
        }

        Log.d(TAG, "🔍 Generating response...")
        val result = nativeGenerate(prompt, maxTokens)
        Log.d(TAG, "✨ Generated: ${result.take(50)}...")

        return result
    }

    /**
     * Kotlin-friendly wrapper for embedding generation
     */
    fun generateEmbedding(prompt: String): List<Float>? {
        if (!nativeIsLoaded()) {
            Log.w(TAG, "⚠️ Model not loaded, cannot generate embedding")
            return null
        }

        Log.d(TAG, "🧠 Generating embedding...")
        val result = nativeGenerateEmbedding(prompt)
        return result?.toList()
    }

    /**
     * Free resources
     */
    fun free() {
        if (nativeIsLoaded()) {
            Log.i(TAG, "🔒 Freeing model resources...")
            nativeFreeModel()
        }
    }

    /**
     * Check if ready for inference
     */
    fun isReady(): Boolean = nativeIsLoaded()
}
