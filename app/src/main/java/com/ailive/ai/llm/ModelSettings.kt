package com.ailive.ai.llm

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject

/**
 * Model Settings for LLM Inference
 *
 * Controls various parameters that affect model generation:
 * - temperature: Randomness in token selection (higher = more random)
 * - topP: Nucleus sampling threshold (cumulative probability)
 * - topK: Number of top tokens to consider
 * - repetitionPenalty: Penalty for repeating tokens
 * - presencePenalty: Penalty for using already-seen tokens
 * - frequencyPenalty: Penalty based on token frequency
 * - ctxSize: Context window size in tokens
 * - maxTokens: Maximum number of tokens to generate
 * - mirostat: Mirostat sampling mode (0=disabled, 1=v1, 2=v2)
 * - mirostatTau: Mirostat target entropy
 * - mirostatEta: Mirostat learning rate
 *
 * RAM Impact:
 * - Base model (Q4_K_M): ~1.0 GB
 * - Context size: Linear scaling (~0.5 GB per 1024 tokens)
 * - Max tokens: Minimal impact (~5-10 MB per 512 tokens)
 * - Sampling parameters: Negligible RAM impact
 *
 * v1.1 optimized defaults:
 * - Context: 4096 tokens (2x increase for longer conversations)
 * - Batch: 1024 (set at C++ level)
 * - Max tokens: 512 (balance between quality and speed)
 */
data class ModelSettings(
    // Sampling parameters
    var temperature: Float = 0.7f,           // Range: 0.1-2.0, Default: 0.7
    var topP: Float = 0.9f,                  // Range: 0.1-1.0, Default: 0.9
    var topK: Int = 40,                      // Range: 1-100, Default: 40
    var repetitionPenalty: Float = 1.18f,    // Range: 1.0-1.5, Default: 1.18
    var presencePenalty: Float = 0.6f,       // Range: 0.0-2.0, Default: 0.6
    var frequencyPenalty: Float = 0.3f,      // Range: 0.0-2.0, Default: 0.3

    // Generation parameters
    var ctxSize: Int = 4096,                 // Range: 512-8192, Default: 4096 (v1.1)
    var maxTokens: Int = 512,                // Range: 50-2048, Default: 512

    // Mirostat parameters (advanced)
    var mirostat: Int = 0,                   // 0=disabled, 1=v1, 2=v2
    var mirostatTau: Float = 5.0f,           // Target entropy
    var mirostatEta: Float = 0.1f            // Learning rate
) {
    companion object {
        private const val TAG = "ModelSettings"
        private const val PREFS_NAME = "ailive_model_settings"

        // Keys for SharedPreferences
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_TOP_P = "top_p"
        private const val KEY_TOP_K = "top_k"
        private const val KEY_REPETITION_PENALTY = "repetition_penalty"
        private const val KEY_PRESENCE_PENALTY = "presence_penalty"
        private const val KEY_FREQUENCY_PENALTY = "frequency_penalty"
        private const val KEY_CTX_SIZE = "ctx_size"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_MIROSTAT = "mirostat"
        private const val KEY_MIROSTAT_TAU = "mirostat_tau"
        private const val KEY_MIROSTAT_ETA = "mirostat_eta"

        /**
         * Load settings from SharedPreferences
         */
        fun load(context: Context): ModelSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            return ModelSettings(
                temperature = prefs.getFloat(KEY_TEMPERATURE, 0.7f),
                topP = prefs.getFloat(KEY_TOP_P, 0.9f),
                topK = prefs.getInt(KEY_TOP_K, 40),
                repetitionPenalty = prefs.getFloat(KEY_REPETITION_PENALTY, 1.18f),
                presencePenalty = prefs.getFloat(KEY_PRESENCE_PENALTY, 0.6f),
                frequencyPenalty = prefs.getFloat(KEY_FREQUENCY_PENALTY, 0.3f),
                ctxSize = prefs.getInt(KEY_CTX_SIZE, 4096),
                maxTokens = prefs.getInt(KEY_MAX_TOKENS, 512),
                mirostat = prefs.getInt(KEY_MIROSTAT, 0),
                mirostatTau = prefs.getFloat(KEY_MIROSTAT_TAU, 5.0f),
                mirostatEta = prefs.getFloat(KEY_MIROSTAT_ETA, 0.1f)
            ).also {
                Log.i(TAG, "Settings loaded: ctx=${it.ctxSize}, max_tokens=${it.maxTokens}, temp=${it.temperature}")
            }
        }

        /**
         * Reset to v1.1 optimized defaults
         */
        fun getDefaults(): ModelSettings = ModelSettings()
    }

    /**
     * Save settings to SharedPreferences
     */
    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        prefs.edit().apply {
            putFloat(KEY_TEMPERATURE, temperature)
            putFloat(KEY_TOP_P, topP)
            putInt(KEY_TOP_K, topK)
            putFloat(KEY_REPETITION_PENALTY, repetitionPenalty)
            putFloat(KEY_PRESENCE_PENALTY, presencePenalty)
            putFloat(KEY_FREQUENCY_PENALTY, frequencyPenalty)
            putInt(KEY_CTX_SIZE, ctxSize)
            putInt(KEY_MAX_TOKENS, maxTokens)
            putInt(KEY_MIROSTAT, mirostat)
            putFloat(KEY_MIROSTAT_TAU, mirostatTau)
            putFloat(KEY_MIROSTAT_ETA, mirostatEta)
            apply()
        }

        Log.i(TAG, "Settings saved: ctx=$ctxSize, max_tokens=$maxTokens, temp=$temperature")
    }

    /**
     * Estimate RAM usage based on current settings
     *
     * Formula:
     * - Base model (Q4_K_M): 1024 MB
     * - Context scaling: (ctxSize / 2048) * 1024 MB
     * - Max tokens: (maxTokens / 512) * 128 MB
     * - Total = base + context + tokens
     *
     * Examples:
     * - ctx=2048, max=512: ~2.1 GB
     * - ctx=4096, max=512: ~3.1 GB (v1.1 default)
     * - ctx=8192, max=1024: ~5.3 GB
     */
    fun estimateRamUsageMB(): Int {
        val baseModelRam = 1024 // Q4_K_M quantized model
        val ctxRam = (ctxSize.toFloat() / 2048f) * 1024f
        val maxTokenRam = (maxTokens.toFloat() / 512f) * 128f

        return (baseModelRam + ctxRam + maxTokenRam).toInt()
    }

    /**
     * Check if settings are safe for the device
     * Warning threshold: 4 GB (leave 1-2 GB for system)
     */
    fun isSafeForDevice(totalDeviceRamMB: Int): Boolean {
        val estimatedUsage = estimateRamUsageMB()
        val safeThreshold = (totalDeviceRamMB * 0.6).toInt() // Use max 60% of RAM

        return estimatedUsage <= safeThreshold
    }

    /**
     * Get warning message if settings are unsafe
     */
    fun getWarningMessage(totalDeviceRamMB: Int): String? {
        if (!isSafeForDevice(totalDeviceRamMB)) {
            val estimatedUsage = estimateRamUsageMB()
            val usageGB = estimatedUsage / 1024f
            val totalGB = totalDeviceRamMB / 1024f

            return "⚠️ High RAM usage: ${String.format("%.1f", usageGB)} GB / ${String.format("%.1f", totalGB)} GB\n" +
                   "Reduce context size or max tokens to improve stability."
        }
        return null
    }

    /**
     * Convert to JSON for debugging/export
     */
    fun toJson(): String {
        return JSONObject().apply {
            put("temperature", temperature)
            put("top_p", topP)
            put("top_k", topK)
            put("repetition_penalty", repetitionPenalty)
            put("presence_penalty", presencePenalty)
            put("frequency_penalty", frequencyPenalty)
            put("ctx_size", ctxSize)
            put("max_tokens", maxTokens)
            put("mirostat", mirostat)
            put("mirostat_tau", mirostatTau)
            put("mirostat_eta", mirostatEta)
            put("estimated_ram_mb", estimateRamUsageMB())
        }.toString(2)
    }

    /**
     * Validate and clamp values to safe ranges
     */
    fun validate(): ModelSettings {
        return copy(
            temperature = temperature.coerceIn(0.1f, 2.0f),
            topP = topP.coerceIn(0.1f, 1.0f),
            topK = topK.coerceIn(1, 100),
            repetitionPenalty = repetitionPenalty.coerceIn(1.0f, 1.5f),
            presencePenalty = presencePenalty.coerceIn(0.0f, 2.0f),
            frequencyPenalty = frequencyPenalty.coerceIn(0.0f, 2.0f),
            ctxSize = ctxSize.coerceIn(512, 8192),
            maxTokens = maxTokens.coerceIn(50, 2048),
            mirostat = mirostat.coerceIn(0, 2),
            mirostatTau = mirostatTau.coerceIn(1.0f, 10.0f),
            mirostatEta = mirostatEta.coerceIn(0.01f, 1.0f)
        )
    }
}
