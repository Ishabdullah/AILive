package com.ailive.personality.tools

import android.util.Log
import com.ailive.emotion.EmotionAI
import com.ailive.emotion.EmotionVector

/**
 * SentimentAnalysisTool - Analyzes emotional context and sentiment
 *
 * Converted from EmotionAI agent to a tool for PersonalityEngine.
 * Provides sentiment analysis capabilities without separate personality.
 */
class SentimentAnalysisTool(
    private val emotionAI: EmotionAI
) : BaseTool() {

    companion object {
        private const val TAG = "SentimentAnalysisTool"
    }

    override val name: String = "analyze_sentiment"

    override val description: String =
        "Analyzes emotional context, sentiment, and urgency from text. " +
        "Returns valence (positive/negative), arousal (calm/excited), " +
        "and urgency (low/critical)."

    override val requiresPermissions: Boolean = false

    override suspend fun isAvailable(): Boolean = true

    override fun validateParams(params: Map<String, Any>): String? {
        if (!params.containsKey("text")) {
            return "Parameter 'text' is required"
        }

        val text = params["text"] as? String
        if (text.isNullOrBlank()) {
            return "Parameter 'text' cannot be empty"
        }

        return null
    }

    override suspend fun executeInternal(params: Map<String, Any>): ToolResult {
        val text = params["text"] as String

        Log.d(TAG, "Analyzing sentiment for: ${text.take(50)}...")

        return try {
            // Use existing EmotionAI functionality
            val emotion = emotionAI.analyzeText(text)

            // Convert to human-readable context
            val sentiment = interpretSentiment(emotion)

            ToolResult.Success(
                data = SentimentResult(
                    valence = emotion.valence,
                    arousal = emotion.arousal,
                    urgency = emotion.urgency,
                    interpretation = sentiment
                ),
                context = mapOf(
                    "sentiment_category" to sentiment.category,
                    "needs_urgent_response" to (emotion.urgency > 0.7f),
                    "emotional_intensity" to emotion.arousal
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze sentiment", e)
            ToolResult.Failure(
                error = e,
                reason = "Could not analyze emotional context: ${e.message}",
                recoverable = true
            )
        }
    }

    /**
     * Interpret emotion vector into human-readable sentiment
     */
    private fun interpretSentiment(emotion: EmotionVector): SentimentInterpretation {
        val category = when {
            emotion.urgency > 0.7f && emotion.valence < 0f -> "urgent_negative"
            emotion.urgency > 0.7f && emotion.valence > 0f -> "urgent_positive"
            emotion.valence > 0.6f && emotion.arousal > 0.5f -> "excited_positive"
            emotion.valence > 0.6f && emotion.arousal < 0.5f -> "calm_positive"
            emotion.valence < -0.6f && emotion.arousal > 0.5f -> "agitated_negative"
            emotion.valence < -0.6f && emotion.arousal < 0.5f -> "sad_negative"
            emotion.arousal > 0.7f -> "high_energy"
            emotion.arousal < 0.3f -> "low_energy"
            else -> "neutral"
        }

        val description = when (category) {
            "urgent_negative" -> "urgent distress or concern"
            "urgent_positive" -> "urgent excitement or enthusiasm"
            "excited_positive" -> "happy and energetic"
            "calm_positive" -> "content and relaxed"
            "agitated_negative" -> "frustrated or angry"
            "sad_negative" -> "sad or disappointed"
            "high_energy" -> "energetic"
            "low_energy" -> "calm or tired"
            "neutral" -> "neutral"
            else -> "neutral"
        }

        return SentimentInterpretation(
            category = category,
            description = description,
            shouldEmpathize = emotion.valence < -0.3f,
            shouldEnergize = emotion.arousal < 0.3f && emotion.valence > 0f,
            shouldCalm = emotion.arousal > 0.7f && emotion.valence < 0f
        )
    }

    /**
     * Result of sentiment analysis
     */
    data class SentimentResult(
        val valence: Float,      // -1 (negative) to 1 (positive)
        val arousal: Float,      // 0 (calm) to 1 (excited)
        val urgency: Float,      // 0 (low) to 1 (critical)
        val interpretation: SentimentInterpretation
    )

    /**
     * Human-readable sentiment interpretation
     */
    data class SentimentInterpretation(
        val category: String,
        val description: String,
        val shouldEmpathize: Boolean,   // Respond with empathy
        val shouldEnergize: Boolean,    // Respond with energy/motivation
        val shouldCalm: Boolean         // Respond with calming tone
    )
}
