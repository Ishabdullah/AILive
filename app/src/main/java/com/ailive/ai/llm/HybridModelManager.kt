package com.ailive.ai.llm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
 * - Performance monitoring (consolidated from LLMManager)
 * - GPU acceleration tracking
 * - PersonalityEngine prompt handling
 *
 * @author AILive Team
 * @since Multimodal MVP - testing-123 branch
 * @updated Consolidated with LLMManager features
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

    // Performance monitoring (from LLMManager)
    private var gpuInfo: GPUInfo? = null
    private val performanceMonitor = PerformanceMonitor()

    // LLMBridge for backward compatibility (delegates to fastModel)
    val llmBridge: LLMBridge
        get() = fastModel

    /**
     * Initialize the hybrid system
     * Loads SmolLM2 immediately, Qwen on first vision request
     * Includes GPU detection and performance tracking
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "🚀 Initializing Hybrid Model System...")
        Log.i(TAG, "   Fast Model: SmolLM2-360M (always loaded)")
        Log.i(TAG, "   Vision Model: Qwen2-VL-2B (on-demand)")

        // Detect GPU acceleration
        Log.i(TAG, "🔍 Detecting GPU acceleration...")
        gpuInfo = detectGPUSupport()
        Log.i(TAG, "   Backend: ${gpuInfo}")

        if (gpuInfo?.isUsingGPU() == true) {
            Log.i(TAG, "✅ GPU acceleration enabled!")
            Log.i(TAG, "   Device: ${gpuInfo!!.deviceName}")
            Log.i(TAG, "   Expected performance: 20-30 tokens/second")
        } else {
            Log.i(TAG, "ℹ️  Using CPU inference")
            Log.i(TAG, "   ${gpuInfo?.fallbackReason ?: "GPU not available"}")
            Log.i(TAG, "   Expected performance: 7-8 tokens/second")
        }

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
            Log.i(TAG, "   Backend: ${gpuInfo?.backend ?: "CPU"}")
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
     * Includes PersonalityEngine prompt handling and performance monitoring
     */
    private suspend fun generateWithFastModel(prompt: String): Flow<String> {
        return flow {
            val startTime = System.currentTimeMillis()
            val backend = gpuInfo?.backend ?: "CPU"

            try {
                // PersonalityEngine prompt handling (from LLMManager)
                // Detect if this is a complex, pre-formatted PersonalityEngine prompt
                val isPersonalityPrompt = prompt.contains("===== YOUR CAPABILITIES =====") ||
                                          prompt.contains("===== CURRENT CONTEXT =====")

                if (isPersonalityPrompt) {
                    // PersonalityEngine formatted prompt - already contains full context in natural format
                    Log.d(TAG, "✓ PersonalityEngine prompt detected (fast model)")
                    Log.d(TAG, "   Full prompt length: ${prompt.length} chars")
                    Log.d(TAG, "   Contains: system instructions, capabilities, context, history, user input")
                    Log.d(TAG, "   Passing prompt AS-IS to preserve natural language format")
                } else {
                    // Simple prompt - llama.cpp will auto-format
                    Log.d(TAG, "✓ Simple prompt - llama.cpp will handle formatting")
                }

                Log.d(TAG, "🚀 Fast model generating: \"${prompt.take(50)}${if (prompt.length > 50) "..." else ""}\"")

                // Generate with fast model
                val result = fastModel.generate(prompt, settings.maxTokens)
                emit(result)

                // Performance tracking
                val totalTime = System.currentTimeMillis() - startTime
                val tokenCount = result.length / 4  // Rough approximation
                performanceMonitor.recordInference(tokenCount, totalTime, backend)

                val tokensPerSec = if (totalTime > 0) {
                    (tokenCount.toFloat() / totalTime) * 1000
                } else {
                    0f
                }

                Log.i(TAG, "✅ Fast model complete: ~$tokenCount tokens in ${totalTime}ms")
                Log.i(TAG, "   Performance: ${String.format("%.2f", tokensPerSec)} tok/s")
                Log.i(TAG, "   Average (last 10): ${String.format("%.2f", performanceMonitor.getRecentSpeed())} tok/s")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Fast model error", e)
                emit("[Error: ${e.message}]")
            }
        }
    }

    /**
     * Generate with vision model (Qwen2-VL)
     * Includes PersonalityEngine prompt handling and performance monitoring
     */
    private suspend fun generateWithVisionModel(prompt: String, image: Bitmap?): Flow<String> {
        return flow {
            val startTime = System.currentTimeMillis()
            val backend = gpuInfo?.backend ?: "CPU"

            try {
                // PersonalityEngine prompt handling (from LLMManager)
                // Detect if this is a complex, pre-formatted PersonalityEngine prompt
                val isPersonalityPrompt = prompt.contains("===== YOUR CAPABILITIES =====") ||
                                          prompt.contains("===== CURRENT CONTEXT =====")

                if (isPersonalityPrompt) {
                    // PersonalityEngine formatted prompt - already contains full context in natural format
                    Log.d(TAG, "✓ PersonalityEngine prompt detected (vision model)")
                    Log.d(TAG, "   Full prompt length: ${prompt.length} chars")
                    Log.d(TAG, "   Contains: system instructions, capabilities, context, history, user input")
                    Log.d(TAG, "   Passing prompt AS-IS to preserve natural language format")
                } else {
                    // Simple prompt - llama.cpp will auto-format
                    Log.d(TAG, "✓ Simple prompt - llama.cpp will handle formatting")
                }

                if (image != null) {
                    Log.w(TAG, "⚠️ Vision input not yet fully supported")
                    Log.i(TAG, "   Generating text-only response...")
                }

                Log.d(TAG, "🚀 Vision model generating: \"${prompt.take(50)}${if (prompt.length > 50) "..." else ""}\"")

                // Generate with vision model
                val result = visionModel.generate(prompt, settings.maxTokens)
                emit(result)

                // Performance tracking
                val totalTime = System.currentTimeMillis() - startTime
                val tokenCount = result.length / 4  // Rough approximation
                performanceMonitor.recordInference(tokenCount, totalTime, backend)

                val tokensPerSec = if (totalTime > 0) {
                    (tokenCount.toFloat() / totalTime) * 1000
                } else {
                    0f
                }

                Log.i(TAG, "✅ Vision model complete: ~$tokenCount tokens in ${totalTime}ms")
                Log.i(TAG, "   Performance: ${String.format("%.2f", tokensPerSec)} tok/s")
                Log.i(TAG, "   Average (last 10): ${String.format("%.2f", performanceMonitor.getRecentSpeed())} tok/s")

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
     * Includes performance summary logging
     */
    fun freeAll() {
        // Log final performance summary before cleanup
        if ((isFastModelLoaded || isVisionModelLoaded) && performanceMonitor.getTotalInferences() > 0) {
            Log.i(TAG, "\n${getPerformanceSummary()}")
        }

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

    // ===== Performance Monitoring Methods (from LLMManager) =====

    /**
     * Detect GPU acceleration support
     * Calls native JNI function to query OpenCL availability
     */
    private fun detectGPUSupport(): GPUInfo {
        return try {
            // GPU detection not supported in current implementation
            val backend = "CPU"
            val deviceName = "CPU"

            when (backend) {
                "OpenCL" -> {
                    Log.d(TAG, "GPU detected: $deviceName")
                    GPUInfo(
                        isAvailable = true,
                        backend = "OpenCL",
                        deviceName = deviceName
                    )
                }
                "CPU" -> {
                    val reason = when (deviceName) {
                        "None" -> "No OpenCL GPU found on device"
                        "OpenCL_Not_Compiled" -> "OpenCL support not compiled in build"
                        else -> "GPU unavailable: $deviceName"
                    }
                    Log.d(TAG, "GPU fallback: $reason")
                    GPUInfo(
                        isAvailable = false,
                        backend = "CPU",
                        deviceName = "CPU",
                        fallbackReason = reason
                    )
                }
                else -> {
                    Log.w(TAG, "Unknown backend: $backend")
                    GPUInfo(
                        isAvailable = false,
                        backend = "CPU",
                        deviceName = "CPU",
                        fallbackReason = "Unknown backend type"
                    )
                }
            }
        } catch (e: UnsatisfiedLinkError) {
            // Native function not available (old build or GPU code not compiled)
            Log.w(TAG, "GPU detection function not available, using CPU fallback")
            GPUInfo(
                isAvailable = false,
                backend = "CPU",
                deviceName = "CPU",
                fallbackReason = "Native GPU detection not available (old build)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "GPU detection failed, falling back to CPU", e)
            GPUInfo(
                isAvailable = false,
                backend = "CPU",
                deviceName = "CPU",
                fallbackReason = "GPU detection error: ${e.message}"
            )
        }
    }

    /**
     * Get GPU information
     */
    fun getGPUInfo(): GPUInfo? = gpuInfo

    /**
     * Check if GPU acceleration is active
     */
    fun isUsingGPU(): Boolean = gpuInfo?.isUsingGPU() ?: false

    /**
     * Get performance statistics
     */
    fun getPerformanceStats(): PerformanceMonitor = performanceMonitor

    /**
     * Get current average performance
     */
    fun getAveragePerformance(): String {
        return if (performanceMonitor.getTotalInferences() > 0) {
            val avgSpeed = performanceMonitor.getAverageSpeed()
            val recentSpeed = performanceMonitor.getRecentSpeed()
            buildString {
                append("Average: ${String.format("%.2f", avgSpeed)} tok/s\n")
                append("Recent (last 10): ${String.format("%.2f", recentSpeed)} tok/s\n")
                append("Total inferences: ${performanceMonitor.getTotalInferences()}\n")
                append("Backend: ${gpuInfo?.backend ?: "CPU"}")
            }
        } else {
            "No performance data yet"
        }
    }

    /**
     * Get detailed performance summary for logging/debugging
     */
    fun getPerformanceSummary(): String {
        return performanceMonitor.getPerformanceSummary(gpuInfo)
    }

    /**
     * Reload settings from SharedPreferences
     * Call this after user changes settings
     */
    fun reloadSettings() {
        settings = ModelSettings.load(context)
        Log.i(TAG, "⚙️ Settings reloaded: max_tokens=${settings.maxTokens}, temp=${settings.temperature}")
    }

    /**
     * Get the ModelDownloadManager for access from UI
     */
    fun getDownloadManager(): ModelDownloadManager = modelDownloadManager

    /**
     * Check if a model is available
     */
    fun isModelAvailable(): Boolean =
        modelDownloadManager.isSmolLM2ModelAvailable() || modelDownloadManager.isQwenVLModelAvailable()

    /**
     * Get current model info
     */
    fun getModelInfo(): String {
        return buildString {
            append("=== Hybrid Model System ===\n")
            if (isFastModelLoaded) {
                append("Fast Model: SmolLM2-360M ✅\n")
                append("  RAM: ~350MB\n")
                append("  Status: Always loaded\n")
            } else {
                append("Fast Model: Not loaded ❌\n")
            }
            append("\n")
            if (isVisionModelLoaded) {
                append("Vision Model: Qwen2-VL-2B ✅\n")
                append("  RAM: ~1.2GB\n")
                append("  Status: Loaded on-demand\n")
            } else {
                append("Vision Model: Standby ⏸️\n")
                append("  Status: Will load when needed\n")
            }
            append("\n")
            append("Backend: ${gpuInfo?.backend ?: "CPU"}\n")
            if (gpuInfo?.isUsingGPU() == true) {
                append("GPU: ${gpuInfo?.deviceName} ✅\n")
            } else {
                gpuInfo?.fallbackReason?.let { append("GPU: $it\n") }
            }
        }
    }
}
