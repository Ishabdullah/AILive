package com.ailive.audio

import android.util.Log

/**
 * WakeWordDetector - Intelligent wake phrase detection
 * Phase 2.3: Detects wake word in transcribed text
 * Phase 2.4: Added TTS feedback on detection
 * Phase 8: Added custom AI name support (e.g., "Hey Joe", "Hey Genesis", "Hey Chris")
 *
 * Features:
 * - Supports user-defined AI names
 * - Phonetic matching for similar-sounding names
 * - Automatic alternative generation
 * - ML-ready architecture for future audio-based detection
 *
 * @since Phase 8 - Custom AI names
 */
class WakeWordDetector(
    private var aiName: String = "AILive",  // Changed from wakePhrase to aiName
    private val ttsManager: TTSManager? = null
) {
    private val TAG = "WakeWordDetector"

    // Detection sensitivity
    private val SIMILARITY_THRESHOLD = 0.7f

    // Current wake phrase (e.g., "hey ailive", "hey joe")
    private var wakePhrase: String = generateWakePhrase(aiName)

    // Dynamic alternatives based on AI name
    private var wakeAlternatives: List<String> = generateAlternatives(aiName)

    // Callback when wake word detected
    var onWakeWordDetected: (() -> Unit)? = null

    /**
     * Check if text contains wake word
     * Supports custom AI names (e.g., "Hey Joe", "Hey Genesis")
     */
    fun processText(text: String): Boolean {
        val normalized = text.lowercase().trim()

        // Check exact match
        if (normalized.contains(wakePhrase.lowercase())) {
            Log.i(TAG, "🎯 Wake word detected (exact): '$text' → '$wakePhrase'")
            triggerWakeWordResponse()
            return true
        }

        // Check just the AI name without "hey"
        if (normalized.contains(aiName.lowercase())) {
            Log.i(TAG, "🎯 Wake word detected (name only): '$text' → '$aiName'")
            triggerWakeWordResponse()
            return true
        }

        // Check alternatives
        for (alt in wakeAlternatives) {
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
     * Set custom AI name (e.g., "Joe", "Genesis", "Chris")
     * Automatically generates wake phrase and alternatives
     */
    fun setAIName(name: String) {
        aiName = name
        wakePhrase = generateWakePhrase(name)
        wakeAlternatives = generateAlternatives(name)
        Log.i(TAG, "✅ AI name set to: '$name'")
        Log.i(TAG, "   Wake phrase: '$wakePhrase'")
        Log.i(TAG, "   Alternatives: ${wakeAlternatives.joinToString(", ")}")
    }

    /**
     * Get current AI name
     */
    fun getAIName(): String = aiName

    /**
     * Get current wake phrase (backward compatibility)
     */
    fun getWakePhrase(): String = wakePhrase

    /**
     * Update wake phrase (for custom phrases - backward compatibility)
     * Note: Prefer using setAIName() for better alternative generation
     */
    fun setWakePhrase(phrase: String) {
        wakePhrase = phrase
        // Extract AI name from phrase if possible
        val match = Regex("hey (.+)", RegexOption.IGNORE_CASE).find(phrase)
        if (match != null) {
            aiName = match.groupValues[1]
            wakeAlternatives = generateAlternatives(aiName)
        }
        Log.i(TAG, "Wake phrase updated to: '$phrase'")
    }

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
        return "Listening for: \"$wakePhrase\" (AI name: $aiName, ${wakeAlternatives.size} alternatives)"
    }

    // ===== Helper Functions =====

    /**
     * Generate wake phrase from AI name
     * Examples: "Joe" → "hey joe", "Genesis" → "hey genesis"
     */
    private fun generateWakePhrase(name: String): String {
        return "hey ${name.lowercase().trim()}"
    }

    /**
     * Generate phonetic and common alternatives for an AI name
     * This helps catch speech recognition variations
     *
     * Examples:
     * - "Joe" → ["jo", "joey", "joel"]
     * - "Genesis" → ["jen", "jenny", "genesis"]
     * - "Chris" → ["kris", "christian", "christopher"]
     * - "AILive" → ["ai live", "a live", "alive", "eye live"]
     */
    private fun generateAlternatives(name: String): List<String> {
        val alts = mutableListOf<String>()
        val normalized = name.lowercase().trim()

        // Always add the name alone (without "hey")
        alts.add(normalized)

        // Special handling for "AILive" (default)
        if (normalized.contains("ailive") || normalized.contains("ai") && normalized.contains("live")) {
            alts.addAll(listOf(
                "ai live",
                "a live",
                "alive",
                "eye live",
                "ailive",
                "a.i. live"
            ))
            return alts
        }

        // Generate phonetic variations based on common patterns
        when {
            // Common name shortenings
            normalized.startsWith("joe") -> alts.addAll(listOf("jo", "joey"))
            normalized.startsWith("gen") -> alts.addAll(listOf("jen", "jenny", "gene"))
            normalized.startsWith("chris") -> alts.addAll(listOf("kris", "cris"))
            normalized.startsWith("alex") -> alts.addAll(listOf("al", "lexi"))
            normalized.startsWith("sam") -> alts.addAll(listOf("sammy", "sammie"))
            normalized.startsWith("mike") -> alts.addAll(listOf("mikey", "michael"))
            normalized.startsWith("matt") -> alts.addAll(listOf("matthew", "matty"))

            // Handle multi-syllable names (add with spaces)
            normalized.length > 6 -> {
                // Split long names into chunks (e.g., "Genesis" → "gen esis")
                if (normalized.length > 8) {
                    val mid = normalized.length / 2
                    alts.add("${normalized.substring(0, mid)} ${normalized.substring(mid)}")
                }
            }
        }

        // Common speech recognition errors
        if (normalized.contains("s")) {
            // S can be heard as Z
            alts.add(normalized.replace("s", "z"))
        }
        if (normalized.contains("f")) {
            // F can be heard as PH
            alts.add(normalized.replace("f", "ph"))
        }
        if (normalized.contains("k")) {
            // K can be heard as C
            alts.add(normalized.replace("k", "c"))
        }

        return alts.distinct()
    }
}
