package com.ailive.audio

import android.util.Log

/**
 * WakeWordDetector - Simple pattern matching for wake phrase
 * Phase 2.3: Detects wake word in transcribed text
 * Phase 2.4: Added TTS feedback on detection
 *
 * Future: Can be upgraded to ML-based detection (TensorFlow Lite audio models)
 */
class WakeWordDetector(
    private var wakePhrase: String = "hey ailive",
    private val ttsManager: TTSManager? = null
) {
    private val TAG = "WakeWordDetector"

    // Detection sensitivity
    private val SIMILARITY_THRESHOLD = 0.7f

    // Alternative phrases to recognize
    private val WAKE_ALTERNATIVES = listOf(
        "hey ai live",
        "hey a live",
        "hey alive",
        "hey eye live",
        "a live",
        "ailive"
    )

    // Callback when wake word detected
    var onWakeWordDetected: (() -> Unit)? = null

    /**
     * Check if text contains wake word
     */
    fun processText(text: String): Boolean {
        val normalized = text.lowercase().trim()

        // Check exact match
        if (normalized.contains(wakePhrase.lowercase())) {
            Log.i(TAG, "🎯 Wake word detected (exact): '$text'")
            triggerWakeWordResponse()
            return true
        }

        // Check alternatives
        for (alt in WAKE_ALTERNATIVES) {
            if (normalized.contains(alt)) {
                Log.i(TAG, "🎯 Wake word detected (alt: '$alt'): '$text'")
                triggerWakeWordResponse()
                return true
            }
        }

        // Check fuzzy match (allow some typos)
        if (fuzzyMatch(normalized, wakePhrase.lowercase())) {
            Log.i(TAG, "🎯 Wake word detected (fuzzy): '$text'")
            triggerWakeWordResponse()
            return true
        }

        return false
    }

    /**
     * Simple fuzzy matching using Levenshtein-like similarity
     */
    private fun fuzzyMatch(text: String, target: String): Boolean {
        // Check if text contains most of the target characters
        val targetWords = target.split(" ")
        var matchCount = 0

        for (word in targetWords) {
            if (text.contains(word)) {
                matchCount++
            }
        }

        val similarity = matchCount.toFloat() / targetWords.size
        return similarity >= SIMILARITY_THRESHOLD
    }

    /**
     * Update wake phrase (for custom names)
     */
    fun setWakePhrase(phrase: String) {
        wakePhrase = phrase
        Log.i(TAG, "Wake phrase updated to: '$phrase'")
    }

    /**
     * Get current wake phrase
     */
    fun getWakePhrase(): String = wakePhrase

    /**
     * Process audio data (for future ML-based detection)
     * Currently not implemented - using text-based detection
     */
    fun processAudioData(audioData: FloatArray): Boolean {
        // TODO: Implement ML-based wake word detection
        // For now, we rely on SpeechRecognizer + text matching
        return false
    }

    /**
     * Trigger wake word response (callback + TTS feedback)
     */
    private fun triggerWakeWordResponse() {
        // Play TTS feedback
        ttsManager?.playFeedback(TTSManager.FeedbackType.WAKE_WORD_DETECTED)

        // Trigger callback
        onWakeWordDetected?.invoke()
    }

    /**
     * Get detection status
     */
    fun getStatus(): String {
        return "Listening for: \"$wakePhrase\" (and ${WAKE_ALTERNATIVES.size} alternatives)"
    }
}
