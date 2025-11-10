package com.ailive.ai.llm

import android.content.Context
import android.graphics.Bitmap
import android.llama.cpp.LLamaAndroid
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * GPU Information
 * Tracks GPU acceleration status and device information
 */
data class GPUInfo(
    val isAvailable: Boolean,
    val backend: String,  // "OpenCL", "CPU", etc.
    val deviceName: String,
    val fallbackReason: String? = null
) {
    fun isUsingGPU(): Boolean = backend == "OpenCL" && isAvailable

    override fun toString(): String {
        return if (isUsingGPU()) {
            "GPU: $deviceName (OpenCL)"
        } else {
            "CPU: ${fallbackReason ?: deviceName}"
        }
    }
}

/**
 * Inference Performance Statistics
 * Tracks performance metrics for each generation
 */
data class InferenceStats(
    val tokensPerSecond: Float,
    val totalTokens: Int,
    val durationMs: Long,
    val backend: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Performance Monitor
 * Tracks and aggregates performance metrics over time
 *
 * Memory Management (v1.1 Week 4):
 * - Keeps only the last 100 inference stats to prevent unbounded growth
 * - Synchronized methods ensure thread-safe access
 * - Old stats are automatically pruned when limit is reached
 */
class PerformanceMonitor {
    private val stats = mutableListOf<InferenceStats>()

    // v1.1 Week 4: Memory optimization - limit history to prevent memory growth
    private val maxStatsSize = 100  // Keep last 100 inferences (~12KB max)

    /**
     * Records inference statistics and prunes old data
     * Thread-safe operation with automatic memory management
     */
    @Synchronized
    fun recordInference(tokens: Int, durationMs: Long, backend: String) {
        val tokensPerSecond = if (durationMs > 0) {
            (tokens.toFloat() / durationMs) * 1000
        } else {
            0f
        }

        stats.add(InferenceStats(tokensPerSecond, tokens, durationMs, backend))

        // v1.1 Week 4: Automatic memory management - remove oldest stat
        if (stats.size > maxStatsSize) {
            stats.removeAt(0)
        }
    }

    @Synchronized
    fun getAverageSpeed(): Float {
        return if (stats.isNotEmpty()) {
            stats.map { it.tokensPerSecond }.average().toFloat()
        } else {
            0f
        }
    }

    @Synchronized
    fun getStats(): List<InferenceStats> = stats.toList()

    @Synchronized
    fun getTotalInferences(): Int = stats.size

    @Synchronized
    fun getRecentSpeed(count: Int = 10): Float {
        val recent = stats.takeLast(count)
        return if (recent.isNotEmpty()) {
            recent.map { it.tokensPerSecond }.average().toFloat()
        } else {
            0f
        }
    }
}

/**
 * LLMManager - On-device LLM inference using official llama.cpp Android
 * Phase 9.0: Qwen2-VL-2B-Instruct GGUF with native llama.cpp
 * v1.1: GPU acceleration with OpenCL + performance tracking + optimizations
 *
 * Capabilities:
 * - Text-only conversation (current implementation)
 * - Visual Question Answering (coming soon with mmproj)
 * - GPU acceleration (OpenCL for Adreno 750)
 * - Automatic CPU fallback if GPU unavailable
 * - Performance monitoring and statistics
 * - Streaming token generation via Kotlin Flow
 *
 * Model: Qwen2-VL-2B-Instruct Q4_K_M GGUF (~986MB)
 * - Single GGUF file with built-in tokenizer
 * - Q4_K_M quantization: 4-bit with medium quality
 *
 * Performance Optimizations (v1.1 Week 4):
 * - Context size: 4096 tokens (up from 2048) - longer conversations
 * - Batch size: 1024 (up from 512) - better throughput
 * - Memory management: Automatic pruning of old stats
 * - Thread-safe operations with synchronized methods
 *
 * Benefits:
 * - Native ARM64 libraries (no UnsatisfiedLinkError)
 * - Official llama.cpp implementation
 * - Streaming responses via Kotlin Flow
 * - Single file vs 8 files (986MB vs 3.7GB)
 * - Better mobile optimizations
 * - GPU acceleration: 3-5x speedup (7→20-30 tok/s)
 *
 * @author AILive Team
 * @since Phase 2.6
 * @updated Phase 9.0 - Using official llama.cpp Android bindings
 * @updated v1.1 Week 3 - Streaming display & UI improvements
 * @updated v1.1 Week 4 - Performance optimizations & cleanup
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

    // GPU acceleration tracking (v1.1)
    private var gpuInfo: GPUInfo? = null
    private val performanceMonitor = PerformanceMonitor()

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

            // Detect GPU acceleration (v1.1)
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

            // Load model using official llama.cpp Android
            Log.i(TAG, "📥 Loading llama.cpp model...")
            llamaAndroid.load(modelPath)

            isInitialized = true
            isInitializing = false

            Log.i(TAG, "✅ Qwen2-VL initialized successfully!")
            Log.i(TAG, "   Model: $currentModelName")
            Log.i(TAG, "   Backend: ${gpuInfo?.backend ?: "CPU"}")
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
     * Generate text with streaming tokens (for UI display)
     * Returns a Flow that emits tokens as they're generated
     *
     * @param prompt The user's prompt
     * @param image Optional image (not yet supported)
     * @param agentName Agent personality name
     * @return Flow<String> that emits each generated token
     */
    fun generateStreaming(prompt: String, image: Bitmap? = null, agentName: String = "AILive"): Flow<String> = flow {
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

        // Warn if image provided
        if (image != null) {
            Log.w(TAG, "⚠️ Vision input not yet supported (requires mmproj file)")
            Log.i(TAG, "   Continuing with text-only generation...")
        }

        val startTime = System.currentTimeMillis()
        Log.i(TAG, "🚀 Starting streaming generation: \"${prompt.take(50)}${if (prompt.length > 50) "..." else ""}\"")

        // Create chat prompt
        val chatPrompt = createChatPrompt(prompt, agentName)

        // Stream tokens directly from llama.cpp
        var tokenCount = 0
        val backend = gpuInfo?.backend ?: "CPU"

        llamaAndroid.send(chatPrompt, formatChat = false)
            .catch { e ->
                Log.e(TAG, "❌ Streaming generation error", e)
                throw e
            }
            .collect { token ->
                tokenCount++
                emit(token)  // Emit each token to the UI

                // Log progress every 10 tokens
                if (tokenCount % 10 == 0) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val tokensPerSec = if (elapsed > 0) {
                        (tokenCount.toFloat() / elapsed) * 1000
                    } else {
                        0f
                    }
                    Log.d(TAG, "   Token $tokenCount (${String.format("%.1f", tokensPerSec)} tok/s)")
                }
            }

        // Record final performance
        val totalTime = System.currentTimeMillis() - startTime
        performanceMonitor.recordInference(tokenCount, totalTime, backend)

        val tokensPerSec = if (totalTime > 0) {
            (tokenCount.toFloat() / totalTime) * 1000
        } else {
            0f
        }

        Log.i(TAG, "✓ Streamed $tokenCount tokens in ${totalTime}ms")
        Log.i(TAG, "   Performance: ${String.format("%.2f", tokensPerSec)} tokens/second")
        Log.i(TAG, "   Average speed (last 10): ${String.format("%.2f", performanceMonitor.getRecentSpeed())} tok/s")
    }

    /**
     * Detect GPU acceleration support
     * Calls native JNI function to query OpenCL availability
     */
    private fun detectGPUSupport(): GPUInfo {
        return try {
            val gpuString = llamaAndroid.detectGPU()
            val parts = gpuString.split(":")
            val backend = parts.getOrNull(0) ?: "CPU"
            val deviceName = parts.getOrNull(1) ?: "Unknown"

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
     * Generate text using official llama.cpp Android (streaming)
     * Includes performance tracking
     *
     * @param prompt The formatted chat prompt
     * @return Generated text
     */
    private suspend fun generateWithLlama(prompt: String): String {
        val backend = gpuInfo?.backend ?: "CPU"
        Log.i(TAG, "🔷 Using llama.cpp for inference (backend: $backend)")

        val response = StringBuilder()
        var tokenCount = 0
        val startTime = System.currentTimeMillis()

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
                    val elapsed = System.currentTimeMillis() - startTime
                    val tokensPerSec = if (elapsed > 0) {
                        (tokenCount.toFloat() / elapsed) * 1000
                    } else {
                        0f
                    }
                    Log.d(TAG, "   Token $tokenCount generated (${String.format("%.1f", tokensPerSec)} tok/s)")
                }
            }

        val totalTime = System.currentTimeMillis() - startTime

        // Record performance metrics
        performanceMonitor.recordInference(tokenCount, totalTime, backend)

        val tokensPerSec = if (totalTime > 0) {
            (tokenCount.toFloat() / totalTime) * 1000
        } else {
            0f
        }

        Log.i(TAG, "✓ Generated $tokenCount tokens in ${totalTime}ms")
        Log.i(TAG, "   Performance: ${String.format("%.2f", tokensPerSec)} tokens/second")
        Log.i(TAG, "   Backend: $backend")
        Log.i(TAG, "   Average speed (last 10): ${String.format("%.2f", performanceMonitor.getRecentSpeed())} tok/s")

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
            buildString {
                append("Model: $currentModelName\n")
                append("Engine: llama.cpp (official Android)\n")
                append("Backend: ${gpuInfo?.backend ?: "CPU"}\n")
                if (gpuInfo?.isUsingGPU() == true) {
                    append("GPU: ${gpuInfo?.deviceName}\n")
                    append("Status: ✅ GPU Acceleration Enabled")
                } else {
                    append("Status: CPU Only\n")
                    gpuInfo?.fallbackReason?.let { append("Reason: $it") }
                }
            }
        } else {
            "No model loaded"
        }
    }

    /**
     * Get GPU information (v1.1)
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
        return buildString {
            append("=== LLM Performance Summary ===\n")
            append("Backend: ${gpuInfo?.backend ?: "CPU"}\n")
            if (gpuInfo?.isUsingGPU() == true) {
                append("GPU: ${gpuInfo?.deviceName}\n")
            } else {
                append("CPU Only: ${gpuInfo?.fallbackReason ?: "Unknown"}\n")
            }
            append("\n")
            append("Total Inferences: ${performanceMonitor.getTotalInferences()}\n")
            if (performanceMonitor.getTotalInferences() > 0) {
                append("Average Speed: ${String.format("%.2f", performanceMonitor.getAverageSpeed())} tok/s\n")
                append("Recent Speed (10): ${String.format("%.2f", performanceMonitor.getRecentSpeed(10))} tok/s\n")

                val stats = performanceMonitor.getStats()
                if (stats.isNotEmpty()) {
                    val fastest = stats.maxByOrNull { it.tokensPerSecond }
                    val slowest = stats.minByOrNull { it.tokensPerSecond }
                    append("Fastest: ${String.format("%.2f", fastest?.tokensPerSecond ?: 0f)} tok/s\n")
                    append("Slowest: ${String.format("%.2f", slowest?.tokensPerSecond ?: 0f)} tok/s\n")
                }
            }
            append("==============================")
        }
    }

    /**
     * Cleanup resources
     */
    fun close() {
        // Log final performance summary before cleanup
        if (isInitialized && performanceMonitor.getTotalInferences() > 0) {
            Log.i(TAG, "\n${getPerformanceSummary()}")
        }

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
        gpuInfo = null
        Log.i(TAG, "🔒 llama.cpp resources released")
    }
}
