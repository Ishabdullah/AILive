package com.ailive.ai.llm

import android.util.Log

/**
 * GPU Information (Moved from LLMManager)
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
 * Inference Performance Statistics (Moved from LLMManager)
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
 * Performance Monitor (Moved from LLMManager)
 * Tracks and aggregates performance metrics over time
 */
class PerformanceMonitor {
    private val stats = mutableListOf<InferenceStats>()
    private val maxStatsSize = 100

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

    /**
     * Get detailed performance summary for logging/debugging
     */
    fun getPerformanceSummary(gpuInfo: GPUInfo?): String {
        return buildString {
            append("=== LLM Performance Summary ===\n")
            append("Backend: ${gpuInfo?.backend ?: "CPU"}\n")
            if (gpuInfo?.isUsingGPU() == true) {
                append("GPU: ${gpuInfo?.deviceName}\n")
            } else {
                append("CPU Only: ${gpuInfo?.fallbackReason ?: "Unknown"}\n")
            }
            append("\n")
            append("Total Inferences: ${getTotalInferences()}\n")
            if (getTotalInferences() > 0) {
                append("Average Speed: ${String.format("%.2f", getAverageSpeed())} tok/s\n")
                append("Recent Speed (10): ${String.format("%.2f", getRecentSpeed(10))} tok/s\n")

                val stats = getStats()
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
}
