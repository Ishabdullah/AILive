package com.ailive.emotion

import android.util.Log
import com.ailive.core.messaging.*
import com.ailive.core.state.StateManager
import com.ailive.core.types.AgentType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlin.math.abs

/**
 * Emotion AI - AILive's amygdala for emotional context and urgency detection.
 * Analyzes text/audio for sentiment, valence, arousal, and urgency.
 */
class EmotionAI(
    private val messageBus: MessageBus,
    private val stateManager: StateManager
) {
    private val TAG = "EmotionAI"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Sentiment analyzer (placeholder - replace with DistilBERT)
    private val sentimentAnalyzer = SimpleSentimentAnalyzer()
    
    // Running emotion state
    private var currentEmotion = EmotionState()
    private var isRunning = false
    
    /**
     * Start Emotion AI.
     */
    fun start() {
        if (isRunning) return
        
        isRunning = true
        Log.i(TAG, "Emotion AI starting...")
        
        subscribeToMessages()
        
        scope.launch {
            messageBus.publish(
                AIMessage.System.AgentStarted(source = AgentType.EMOTION_AI)
            )
        }
        
        Log.i(TAG, "Emotion AI started")
    }
    
    /**
     * Stop Emotion AI.
     */
    fun stop() {
        if (!isRunning) return
        
        Log.i(TAG, "Emotion AI stopping...")
        scope.cancel()
        isRunning = false
        Log.i(TAG, "Emotion AI stopped")
    }
    
    /**
     * Analyze text for emotion.
     */
    suspend fun analyzeText(text: String): EmotionVector {
        val sentiment = sentimentAnalyzer.analyze(text)
        
        // Calculate emotion dimensions
        val valence = sentiment.score // -1 (negative) to 1 (positive)
        val arousal = calculateArousal(text, sentiment)
        val urgency = calculateUrgency(text, sentiment)
        
        val emotion = EmotionVector(
            valence = valence,
            arousal = arousal,
            urgency = urgency
        )
        
        // Update running state with decay
        currentEmotion = currentEmotion.blend(emotion, weight = 0.3f)
        
        // Publish emotion update
        messageBus.publish(
            AIMessage.Perception.EmotionVector(
                valence = currentEmotion.valence,
                arousal = currentEmotion.arousal,
                urgency = currentEmotion.urgency
            )
        )
        
        // Update state
        stateManager.updateAffect { affect ->
            affect.copy(
                currentEmotion = com.ailive.core.state.EmotionVector(
                    valence = currentEmotion.valence,
                    arousal = currentEmotion.arousal,
                    urgency = currentEmotion.urgency
                )
            )
        }
        
        return emotion
    }
    
    /**
     * Calculate arousal from text features.
     */
    private fun calculateArousal(text: String, sentiment: SentimentResult): Float {
        var arousal = 0f
        
        // Exclamation marks increase arousal
        arousal += text.count { it == '!' } * 0.1f
        
        // ALL CAPS increases arousal
        val capsRatio = text.count { it.isUpperCase() }.toFloat() / text.length.coerceAtLeast(1)
        arousal += capsRatio * 0.5f
        
        // Question marks increase arousal slightly
        arousal += text.count { it == '?' } * 0.05f
        
        // Strong sentiment (positive or negative) increases arousal
        arousal += abs(sentiment.score) * 0.3f
        
        return arousal.coerceIn(0f, 1f)
    }
    
    /**
     * Calculate urgency from text features.
     */
    private fun calculateUrgency(text: String, sentiment: SentimentResult): Float {
        var urgency = 0f
        
        val lowerText = text.lowercase()
        
        // Urgent keywords
        val urgentKeywords = listOf(
            "urgent", "emergency", "now", "immediately", "critical", 
            "asap", "hurry", "quick", "help", "warning", "alert"
        )
        urgentKeywords.forEach { keyword ->
            if (lowerText.contains(keyword)) urgency += 0.2f
        }
        
        // Multiple exclamations
        if (text.contains("!!")) urgency += 0.3f
        
        // Negative sentiment + high arousal = urgency
        if (sentiment.score < -0.5f) {
            urgency += 0.2f
        }
        
        return urgency.coerceIn(0f, 1f)
    }
    
    /**
     * Get current emotion state.
     */
    fun getCurrentEmotion(): EmotionState = currentEmotion
    
    /**
     * Subscribe to messages from other agents.
     */
    private fun subscribeToMessages() {
        // Analyze transcripts
        scope.launch {
            messageBus.subscribe(AIMessage.Perception.AudioTranscript::class.java)
                .collect { transcript ->
                    analyzeText(transcript.transcript)
                }
        }
        
        // Monitor safety violations (increase urgency)
        scope.launch {
            messageBus.subscribe(AIMessage.System.SafetyViolation::class.java)
                .collect { violation ->
                    currentEmotion = currentEmotion.copy(urgency = 0.9f)
                    Log.w(TAG, "Safety violation detected - urgency elevated")
                }
        }
    }
}

/**
 * Emotion vector representation.
 */
data class EmotionVector(
    val valence: Float,  // -1 (negative) to 1 (positive)
    val arousal: Float,  // 0 (calm) to 1 (excited)
    val urgency: Float   // 0 (low) to 1 (critical)
)

/**
 * Running emotion state with temporal smoothing.
 */
data class EmotionState(
    val valence: Float = 0f,
    val arousal: Float = 0f,
    val urgency: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Blend with new emotion (exponential moving average).
     */
    fun blend(new: EmotionVector, weight: Float = 0.3f): EmotionState {
        return EmotionState(
            valence = valence * (1 - weight) + new.valence * weight,
            arousal = arousal * (1 - weight) + new.arousal * weight,
            urgency = urgency * (1 - weight) + new.urgency * weight,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Decay emotion over time (emotions fade).
     */
    fun decay(decayRate: Float = 0.1f): EmotionState {
        return EmotionState(
            valence = valence * (1 - decayRate),
            arousal = (arousal * (1 - decayRate)).coerceAtLeast(0.1f),
            urgency = urgency * (1 - decayRate * 2), // Urgency decays faster
            timestamp = System.currentTimeMillis()
        )
    }
}

/**
 * Simple rule-based sentiment analyzer (placeholder).
 * TODO: Replace with DistilBERT sentiment model.
 */
class SimpleSentimentAnalyzer {
    
    private val positiveWords = setOf(
        "good", "great", "excellent", "amazing", "wonderful", "fantastic",
        "love", "like", "happy", "joy", "smile", "best", "perfect",
        "awesome", "brilliant", "nice", "beautiful", "excited"
    )
    
    private val negativeWords = setOf(
        "bad", "terrible", "awful", "horrible", "hate", "dislike",
        "sad", "angry", "worst", "poor", "ugly", "wrong", "fail",
        "error", "problem", "issue", "broken", "stuck"
    )
    
    fun analyze(text: String): SentimentResult {
        val lowerText = text.lowercase()
        val words = lowerText.split(Regex("\\W+"))
        
        var positiveCount = 0
        var negativeCount = 0
        
        words.forEach { word ->
            if (word in positiveWords) positiveCount++
            if (word in negativeWords) negativeCount++
        }
        
        val totalSentimentWords = positiveCount + negativeCount
        val score = if (totalSentimentWords > 0) {
            (positiveCount - negativeCount).toFloat() / totalSentimentWords
        } else {
            0f // Neutral
        }
        
        return SentimentResult(
            score = score.coerceIn(-1f, 1f),
            confidence = (totalSentimentWords.toFloat() / words.size.coerceAtLeast(1)).coerceIn(0f, 1f)
        )
    }
}

data class SentimentResult(
    val score: Float,      // -1 (negative) to 1 (positive)
    val confidence: Float  // 0 to 1
)
