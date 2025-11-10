package com.ailive.ai.models

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

/**
 * DEPRECATED - Legacy TensorFlow Lite model manager
 *
 * This class is deprecated as of v1.1 Week 4.
 * TensorFlow Lite dependencies have been removed (~15MB APK reduction).
 *
 * AILive now uses llama.cpp exclusively for all inference:
 * - LLM inference: LLMManager (llama.cpp with GGUF models)
 * - Vision: Future llama.cpp vision models (llava, etc.)
 *
 * This stub remains for API compatibility but returns no-op results.
 */
@Deprecated("Use llama.cpp-based inference instead. TensorFlow removed in v1.1.")
class ModelManager(private val context: Context) {
    private val TAG = "ModelManager"

    companion object {
        private const val MODEL_PATH = "models/mobilenet_v2.tflite"
        private const val LABELS_PATH = "models/labels.txt"
        private const val INPUT_SIZE = 224
        private const val PIXEL_SIZE = 3
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 127.5f
    }

    private val labels: List<String> by lazy {
        try {
            context.assets.open(LABELS_PATH).bufferedReader().readLines()
        } catch (e: Exception) {
            Log.w(TAG, "Labels file not found (expected - TensorFlow removed)")
            listOf("Unknown")
        }
    }

    /**
     * No-op initialization (TensorFlow removed in v1.1)
     */
    fun initialize() {
        Log.w(TAG, "⚠️  ModelManager is DEPRECATED")
        Log.w(TAG, "   TensorFlow Lite removed in v1.1 Week 4 (~15MB saved)")
        Log.w(TAG, "   Use LLMManager (llama.cpp) for all inference")
        Log.i(TAG, "✓ Stub initialized (no-op)")
    }

    /**
     * Returns null (TensorFlow removed in v1.1)
     */
    fun classifyImage(bitmap: Bitmap): ClassificationResult? {
        Log.d(TAG, "classifyImage() called on deprecated stub - returning null")
        return null
    }

    /**
     * Get deprecation info
     */
    fun getModelInfo(): String {
        return """
            [DEPRECATED] ModelManager stub (v1.1)
            TensorFlow Lite: REMOVED (~15MB saved)
            Use: LLMManager with llama.cpp instead
            Status: No-op stub for API compatibility
        """.trimIndent()
    }

    /**
     * No-op cleanup
     */
    fun close() {
        Log.d(TAG, "ModelManager stub closed (no-op)")
    }
}

/**
 * Classification result data class
 * Maintained for API compatibility
 */
data class ClassificationResult(
    val topLabel: String,
    val confidence: Float,
    val allResults: List<Pair<String, Float>>,
    val inferenceTimeMs: Long
)
