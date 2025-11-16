package com.ailive.ai.llm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * HybridModelManager - Manages dual GGUF models for optimal performance
 *
 * Architecture (Option 4: Hybrid Keep-One-Loaded):
 * - SmolLM2-360M: Always loaded (~350MB RAM) - Instant responses
 * - Qwen2-VL-2B: Load on-demand (~1.2GB RAM) - Vision & complex tasks
 *
 * Benefits:
 * - Instant chat responses (SmolLM2 is always ready)
 * - Memory efficient (only load Qwen when needed)
 * - Smart routing (auto-select model based on query)
 *
 * @author AILive Team
 * @since Multimodal MVP - testing-123 branch
 */
class HybridModelManager(private val context: Context) {

    companion object {
        private const val TAG = "HybridModelManager"
    }

    // Fast model - always loaded
    private val fastModel = LLMBridge()
    private var isFastModelLoaded = false

    // Heavy model - load on demand
    private val visionModel = LLMBridge()
    private var isVisionModelLoaded = false

    // Model download manager
    private val modelDownloadManager = ModelDownloadManager(context)

    // Model settings
    private var settings: ModelSettings = ModelSettings.load(context)

    /**
     * Initialize the hybrid system
     * Loads SmolLM2 immediately, Qwen on first vision request
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "🚀 Initializing Hybrid Model System...")
        Log.i(TAG, "   Fast Model: SmolLM2-360M (always loaded)")
        Log.i(TAG, "   Vision Model: Qwen2-VL-2B (on-demand)")

        // Check if SmolLM2 is available
        if (!modelDownloadManager.isSmolLM2ModelAvailable()) {
            Log.w(TAG, "⚠️ SmolLM2 not available - hybrid mode unavailable")
            Log.i(TAG, "   Falling back to single-model mode")
            return@withContext false
        }

        // Load SmolLM2 (lightweight, instant)
        val fastModelPath = modelDownloadManager.getModelPath(ModelDownloadManager.SMOLLM2_MODEL_GGUF)
        Log.i(TAG, "📂 Loading fast model: $fastModelPath")

        if (fastModel.loadModel(fastModelPath, 2048)) {
            isFastModelLoaded = true
            Log.i(TAG, "✅ Fast model loaded successfully!")
            Log.i(TAG, "   RAM: ~350MB")
            Log.i(TAG, "   Ready for instant chat")
            true
        } else {
            Log.e(TAG, "❌ Failed to load fast model")
            false
        }
    }

    /**
     * Ensure vision model is loaded for image/complex tasks
     */
    private suspend fun ensureVisionModelLoaded(): Boolean = withContext(Dispatchers.IO) {
        if (isVisionModelLoaded) {
            Log.d(TAG, "✓ Vision model already loaded")
            return@withContext true
        }

        Log.i(TAG, "📥 Loading vision model (Qwen2-VL)...")
        Log.i(TAG, "   This may take 3-5 seconds...")

        // Check if Qwen model is available
        if (!modelDownloadManager.isQwenVLModelAvailable()) {
            Log.e(TAG, "❌ Qwen2-VL model not available")
            return@withContext false
        }

        val visionModelFile = modelDownloadManager.getActiveModelFile()
        if (visionModelFile == null) {
            Log.e(TAG, "❌ No vision model file found")
            return@withContext false
        }

        Log.i(TAG, "📂 Loading vision model: ${visionModelFile.name}")

        if (visionModel.loadModel(visionModelFile.absolutePath, 4096)) {
            isVisionModelLoaded = true
            Log.i(TAG, "✅ Vision model loaded successfully!")
            Log.i(TAG, "   RAM: ~1.2GB")
            Log.i(TAG, "   Total RAM: ~1.5GB (both models)")
            true
        } else {
            Log.e(TAG, "❌ Failed to load vision model")
            false
        }
    }

    /**
     * Smart routing: decide which model to use
     */
    private fun shouldUseFastModel(prompt: String, hasImage: Boolean): Boolean {
        return when {
            // Always use vision model for images
            hasImage -> {
                Log.d(TAG, "🎯 Routing to vision model: has image")
                false
            }
            // Use vision model for long context
            prompt.length > 500 -> {
                Log.d(TAG, "🎯 Routing to vision model: long context (${prompt.length} chars)")
                false
            }
            // Use vision model for complex keywords
            prompt.contains("analyze", ignoreCase = true) ||
            prompt.contains("detail", ignoreCase = true) ||
            prompt.contains("explain", ignoreCase = true) ||
            prompt.contains("describe", ignoreCase = true) -> {
                Log.d(TAG, "🎯 Routing to vision model: complex task detected")
                false
            }
            // Everything else: use fast model
            else -> {
                Log.d(TAG, "🎯 Routing to fast model: simple chat")
                true
            }
        }
    }

    /**
     * Generate response with smart model routing
     */
    suspend fun generateStreaming(
        prompt: String,
        image: Bitmap? = null,
        agentName: String = "AILive"
    ): Flow<String> {
        // Reload settings
        settings = ModelSettings.load(context)

        val hasImage = image != null
        val useFastModel = shouldUseFastModel(prompt, hasImage)

        return if (useFastModel && isFastModelLoaded) {
            // Fast path: SmolLM2 instant response
            Log.i(TAG, "⚡ Using fast model (SmolLM2)")
            generateWithFastModel(prompt)
        } else {
            // Complex path: Qwen2-VL for vision/reasoning
            Log.i(TAG, "🎨 Using vision model (Qwen2-VL)")
            ensureVisionModelLoaded()
            generateWithVisionModel(prompt, image)
        }
    }

    /**
     * Generate with fast model (SmolLM2)
     */
    private suspend fun generateWithFastModel(prompt: String): Flow<String> {
        return kotlinx.coroutines.flow.flow {
            try {
                val result = fastModel.generate(prompt, settings.maxTokens)
                emit(result)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Fast model error", e)
                emit("[Error: ${e.message}]")
            }
        }
    }

    /**
     * Generate with vision model (Qwen2-VL)
     */
    private suspend fun generateWithVisionModel(prompt: String, image: Bitmap?): Flow<String> {
        return kotlinx.coroutines.flow.flow {
            try {
                if (image != null) {
                    Log.w(TAG, "⚠️ Vision input not yet fully supported")
                    Log.i(TAG, "   Generating text-only response...")
                }

                val result = visionModel.generate(prompt, settings.maxTokens)
                emit(result)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Vision model error", e)
                emit("[Error: ${e.message}]")
            }
        }
    }

    /**
     * Get status of both models
     */
    fun getStatus(): String {
        return buildString {
            appendLine("Hybrid Model System Status:")
            appendLine("  Fast Model (SmolLM2): ${if (isFastModelLoaded) "✅ Loaded (~350MB)" else "❌ Not loaded"}")
            appendLine("  Vision Model (Qwen): ${if (isVisionModelLoaded) "✅ Loaded (~1.2GB)" else "⏸️ Standby"}")
            if (isFastModelLoaded && isVisionModelLoaded) {
                appendLine("  Total RAM: ~1.5GB")
            } else if (isFastModelLoaded) {
                appendLine("  Total RAM: ~350MB")
            }
        }
    }

    /**
     * Free vision model if memory pressure detected
     */
    fun freeVisionModelIfNeeded() {
        if (isVisionModelLoaded) {
            Log.i(TAG, "🗑️ Freeing vision model to save memory...")
            visionModel.free()
            isVisionModelLoaded = false
            Log.i(TAG, "✅ Vision model freed (~1.2GB released)")
        }
    }

    /**
     * Free all models
     */
    fun freeAll() {
        Log.i(TAG, "🗑️ Freeing all models...")

        if (isFastModelLoaded) {
            fastModel.free()
            isFastModelLoaded = false
            Log.i(TAG, "   Fast model freed")
        }

        if (isVisionModelLoaded) {
            visionModel.free()
            isVisionModelLoaded = false
            Log.i(TAG, "   Vision model freed")
        }

        Log.i(TAG, "✅ All models freed")
    }

    /**
     * Check if system is ready
     */
    fun isReady(): Boolean {
        return isFastModelLoaded
    }

    /**
     * Check if vision capability is ready
     */
    fun isVisionReady(): Boolean {
        return isVisionModelLoaded
    }
}
