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

        // Load native library
        init {
            try {
                System.loadLibrary("ailive_llm")
                Log.i(TAG, "✅ Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "❌ Failed to load native library", e)
                Log.e(TAG, "   Make sure NDK build is configured properly")
            }
        }
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
     * @param prompt Input text
     * @param maxTokens Maximum tokens to generate (default 80)
     * @return Generated text
     */
    external fun nativeGenerate(prompt: String, maxTokens: Int = 80): String

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
     */
    fun generate(prompt: String, maxTokens: Int = 80): String {
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
