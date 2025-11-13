package com.ailive.personality

import java.util.regex.Pattern

/**
 * SentenceDetector - Intelligent sentence boundary detection for streaming TTS
 *
 * Problem: Naive detection using endsWith(". ") fails on abbreviations and edge cases
 * Solution: Use regex pattern matching with abbreviation dictionary
 *
 * Features:
 * - Detects complete sentences based on punctuation + whitespace/newline
 * - Ignores common abbreviations (Dr., Mr., U.S., etc.)
 * - Handles edge cases (ellipsis, quotes, parentheses)
 * - Supports long phrase detection (>80 chars) for better TTS pacing
 *
 * @author AILive Team
 */
object SentenceDetector {

    // Common abbreviations that should NOT trigger sentence boundaries
    private val COMMON_ABBREVIATIONS = setOf(
        "Dr", "Mr", "Mrs", "Ms", "Prof", "Sr", "Jr",
        "vs", "etc", "i.e", "e.g", "Ph.D", "M.D",
        "U.S", "U.K", "Inc", "Ltd", "Co", "Corp",
        "Jan", "Feb", "Mar", "Apr", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        "St", "Ave", "Blvd", "Rd", "No", "Vol", "Fig",
        "a.m", "p.m", "A.M", "P.M"
    )

    // Sentence-ending punctuation followed by whitespace or newline or end-of-string
    // Captures: . ! ? followed by space/newline OR at end of string
    private val SENTENCE_END_PATTERN = Pattern.compile(
        "[.!?]+\\s+|[.!?]+\$|[.!?]+\\n"
    )

    /**
     * Check if the current text buffer ends with a complete sentence
     *
     * @param text The accumulated text buffer
     * @param minLength Minimum length before considering sentence breaks (default: 10)
     * @param longPhraseThreshold Treat as sentence if longer than this (default: 80)
     * @return true if text ends with a complete sentence and should be spoken
     */
    fun isCompleteSentence(
        text: String,
        minLength: Int = 10,
        longPhraseThreshold: Int = 80
    ): Boolean {
        if (text.length < minLength) return false

        // Long phrases should be broken up for better TTS pacing
        // If text is very long and ends with a word boundary, speak it
        if (text.length > longPhraseThreshold && text.endsWith(" ")) {
            return true
        }

        // Check for sentence-ending punctuation
        val matcher = SENTENCE_END_PATTERN.matcher(text)
        if (!matcher.find()) return false

        // Find the last match (we care about the end of the string)
        var lastMatch: String? = null
        var lastStart = -1
        while (matcher.find()) {
            lastMatch = matcher.group()
            lastStart = matcher.start()
        }

        // If no match found at or near end, not a complete sentence
        if (lastMatch == null || lastStart < 0) return false

        // Check if match is at the very end or near end (within 2 chars)
        val isAtEnd = lastStart + lastMatch.length >= text.length - 2
        if (!isAtEnd) return false

        // Extract the word before the punctuation to check for abbreviations
        val beforePunctuation = text.substring(0, lastStart).trim()
        val lastWord = beforePunctuation.split(Regex("\\s+")).lastOrNull() ?: ""

        // If the last word (without period) is a common abbreviation, NOT a sentence
        val wordWithoutPeriod = lastWord.replace(".", "")
        if (COMMON_ABBREVIATIONS.contains(wordWithoutPeriod)) {
            return false
        }

        // Passed all checks - this is a complete sentence
        return true
    }

    /**
     * Extract complete sentences from text buffer
     * Returns pair of (sentences to speak, remaining buffer)
     *
     * @param buffer The accumulated text buffer
     * @return Pair<String, String> - (complete sentences, remaining text)
     */
    fun extractCompleteSentences(buffer: String): Pair<String, String> {
        if (buffer.isEmpty()) return Pair("", "")

        val sentences = StringBuilder()
        val matcher = SENTENCE_END_PATTERN.matcher(buffer)

        var lastEnd = 0
        while (matcher.find()) {
            val matchStart = matcher.start()
            val matchEnd = matcher.end()

            // Extract sentence including punctuation
            val potentialSentence = buffer.substring(lastEnd, matchEnd).trim()

            // Check if this is actually a sentence (not abbreviation)
            if (potentialSentence.isNotEmpty()) {
                val beforePunctuation = buffer.substring(lastEnd, matchStart).trim()
                val lastWord = beforePunctuation.split(Regex("\\s+")).lastOrNull() ?: ""
                val wordWithoutPeriod = lastWord.replace(".", "")

                if (!COMMON_ABBREVIATIONS.contains(wordWithoutPeriod)) {
                    sentences.append(potentialSentence).append(" ")
                    lastEnd = matchEnd
                }
            }
        }

        val remaining = if (lastEnd < buffer.length) {
            buffer.substring(lastEnd)
        } else {
            ""
        }

        return Pair(sentences.toString().trim(), remaining)
    }

    /**
     * Add abbreviation to the detector (for customization)
     */
    fun addAbbreviation(abbr: String) {
        (COMMON_ABBREVIATIONS as MutableSet).add(abbr)
    }
}
