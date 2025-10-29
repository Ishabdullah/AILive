package com.ailive.personality.tools

import android.content.Context
import android.util.Log
import com.ailive.core.state.StateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * PatternAnalysisTool - Analyzes user behavior patterns for predictions
 *
 * Tracks interaction patterns to predict user needs and provide proactive assistance.
 * Converted from OracleAI's prediction capabilities to a tool for PersonalityEngine.
 *
 * Phase 5: Tool Expansion
 */
class PatternAnalysisTool(
    private val context: Context,
    private val stateManager: StateManager
) : BaseTool() {

    companion object {
        private const val TAG = "PatternAnalysisTool"
        private const val PATTERN_FILE = "user_patterns.json"
        private const val MAX_HISTORY_SIZE = 100
    }

    override val name: String = "analyze_patterns"

    override val description: String =
        "Analyzes user behavior patterns to predict needs and provide proactive suggestions. " +
        "Tracks common query times, frequent intents, and conversation patterns."

    override val requiresPermissions: Boolean = false

    override suspend fun isAvailable(): Boolean {
        return true  // Always available
    }

    override fun validateParams(params: Map<String, Any>): String? {
        // Optional: action parameter ("predict", "track", "summary")
        val action = params["action"] as? String
        if (action != null && action !in listOf("predict", "track", "summary")) {
            return "Invalid action. Must be 'predict', 'track', or 'summary'"
        }
        return null
    }

    override suspend fun executeInternal(params: Map<String, Any>): ToolResult {
        try {
            val action = params["action"] as? String ?: "predict"

            Log.d(TAG, "Analyzing patterns with action: $action")

            return when (action) {
                "predict" -> predictNextNeed(params)
                "track" -> trackInteraction(params)
                "summary" -> getPatternSummary()
                else -> ToolResult.Failure(
                    error = IllegalArgumentException("Unknown action"),
                    reason = "Action must be 'predict', 'track', or 'summary'",
                    recoverable = false
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Pattern analysis failed", e)
            return ToolResult.Failure(
                error = e,
                reason = "Failed to analyze patterns: ${e.message}",
                recoverable = true
            )
        }
    }

    /**
     * Predict what user might need next based on patterns
     */
    private suspend fun predictNextNeed(params: Map<String, Any>): ToolResult {
        return withContext(Dispatchers.IO) {
            val patterns = loadPatterns()
            val currentTime = Calendar.getInstance()
            val currentHour = currentTime.get(Calendar.HOUR_OF_DAY)
            val currentDayOfWeek = currentTime.get(Calendar.DAY_OF_WEEK)

            // Analyze time-based patterns
            val timePatterns = analyzeTimePatterns(patterns, currentHour)

            // Analyze frequency patterns
            val frequentIntents = analyzeFrequencyPatterns(patterns)

            // Analyze sequence patterns
            val sequencePatterns = analyzeSequencePatterns(patterns)

            // Generate prediction
            val prediction = generatePrediction(
                timePatterns = timePatterns,
                frequentIntents = frequentIntents,
                sequencePatterns = sequencePatterns,
                currentHour = currentHour,
                currentDay = currentDayOfWeek
            )

            Log.i(TAG, "✓ Prediction generated: ${prediction.predictedIntent}")

            ToolResult.Success(
                data = PatternPrediction(
                    predictedIntent = prediction.predictedIntent,
                    confidence = prediction.confidence,
                    reason = prediction.reason,
                    suggestions = prediction.suggestions,
                    timeContext = getTimeContext(currentHour)
                ),
                context = mapOf(
                    "pattern_count" to patterns.size,
                    "current_hour" to currentHour
                )
            )
        }
    }

    /**
     * Track a new interaction for pattern learning
     */
    private suspend fun trackInteraction(params: Map<String, Any>): ToolResult {
        return withContext(Dispatchers.IO) {
            val intent = params["intent"] as? String ?: "CONVERSATION"
            val success = params["success"] as? Boolean ?: true

            val interaction = InteractionRecord(
                timestamp = System.currentTimeMillis(),
                intent = intent,
                hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK),
                success = success
            )

            val patterns = loadPatterns().toMutableList()
            patterns.add(interaction)

            // Keep only recent history
            while (patterns.size > MAX_HISTORY_SIZE) {
                patterns.removeAt(0)
            }

            savePatterns(patterns)

            Log.d(TAG, "✓ Tracked interaction: $intent at hour ${interaction.hour}")

            ToolResult.Success(
                data = mapOf(
                    "tracked" to true,
                    "intent" to intent,
                    "total_patterns" to patterns.size
                ),
                context = mapOf(
                    "learning_enabled" to true
                )
            )
        }
    }

    /**
     * Get summary of learned patterns
     */
    private suspend fun getPatternSummary(): ToolResult {
        return withContext(Dispatchers.IO) {
            val patterns = loadPatterns()

            if (patterns.isEmpty()) {
                return@withContext ToolResult.Success(
                    data = mapOf(
                        "message" to "No patterns learned yet. Keep interacting to build your pattern profile!",
                        "pattern_count" to 0
                    )
                )
            }

            // Calculate statistics
            val intentCounts = patterns.groupingBy { it.intent }.eachCount()
            val mostCommonIntent = intentCounts.maxByOrNull { it.value }?.key ?: "UNKNOWN"
            val hourCounts = patterns.groupingBy { it.hour }.eachCount()
            val mostActiveHour = hourCounts.maxByOrNull { it.value }?.key ?: 12

            val summary = """
                I've learned ${patterns.size} patterns from our interactions!

                Your most common request: $mostCommonIntent (${intentCounts[mostCommonIntent]} times)
                Your most active hour: ${formatHour(mostActiveHour)}

                This helps me better predict what you might need.
            """.trimIndent()

            ToolResult.Success(
                data = PatternSummary(
                    totalPatterns = patterns.size,
                    mostCommonIntent = mostCommonIntent,
                    mostActiveHour = mostActiveHour,
                    intentDistribution = intentCounts,
                    summary = summary
                ),
                context = mapOf(
                    "patterns_available" to true
                )
            )
        }
    }

    /**
     * Analyze patterns by time of day
     */
    private fun analyzeTimePatterns(patterns: List<InteractionRecord>, currentHour: Int): Map<String, Int> {
        // Find intents commonly used at this hour (±1 hour window)
        return patterns
            .filter { Math.abs(it.hour - currentHour) <= 1 }
            .groupingBy { it.intent }
            .eachCount()
    }

    /**
     * Analyze patterns by frequency
     */
    private fun analyzeFrequencyPatterns(patterns: List<InteractionRecord>): Map<String, Int> {
        return patterns
            .groupingBy { it.intent }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .toMap()
    }

    /**
     * Analyze sequential patterns
     */
    private fun analyzeSequencePatterns(patterns: List<InteractionRecord>): List<Pair<String, String>> {
        if (patterns.size < 2) return emptyList()

        val sequences = mutableListOf<Pair<String, String>>()
        for (i in 0 until patterns.size - 1) {
            sequences.add(patterns[i].intent to patterns[i + 1].intent)
        }

        return sequences
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
    }

    /**
     * Generate prediction based on analyzed patterns
     */
    private fun generatePrediction(
        timePatterns: Map<String, Int>,
        frequentIntents: Map<String, Int>,
        sequencePatterns: List<Pair<String, String>>,
        currentHour: Int,
        currentDay: Int
    ): Prediction {
        // Priority: time patterns > frequent intents > sequence patterns

        val predictedIntent = when {
            timePatterns.isNotEmpty() -> {
                timePatterns.maxByOrNull { it.value }?.key ?: "CONVERSATION"
            }
            frequentIntents.isNotEmpty() -> {
                frequentIntents.keys.first()
            }
            else -> "CONVERSATION"
        }

        val confidence = when {
            timePatterns.isNotEmpty() -> 0.8f
            frequentIntents.isNotEmpty() -> 0.6f
            else -> 0.3f
        }

        val reason = when {
            timePatterns.isNotEmpty() ->
                "You often use $predictedIntent around ${formatHour(currentHour)}"
            frequentIntents.isNotEmpty() ->
                "$predictedIntent is your most common request"
            else ->
                "Not enough data yet, learning your patterns"
        }

        val suggestions = generateSuggestions(predictedIntent, currentHour)

        return Prediction(predictedIntent, confidence, reason, suggestions)
    }

    /**
     * Generate contextual suggestions
     */
    private fun generateSuggestions(intent: String, hour: Int): List<String> {
        return when (intent) {
            "VISION" -> listOf(
                "Would you like me to look around?",
                "I can analyze what's in front of the camera"
            )
            "EMOTION" -> listOf(
                "How are you feeling?",
                "I can sense the emotional atmosphere"
            )
            "MEMORY" -> listOf(
                "Looking for something we discussed?",
                "I can help you recall past conversations"
            )
            "DEVICE_CONTROL" -> when (hour) {
                in 6..9 -> listOf("Good morning! Need the flashlight?")
                in 18..22 -> listOf("Evening! Want me to turn on the flashlight?")
                else -> listOf("Need device control help?")
            }
            else -> listOf(
                "I'm here to help!",
                "What would you like me to do?"
            )
        }
    }

    /**
     * Get time context description
     */
    private fun getTimeContext(hour: Int): String {
        return when (hour) {
            in 5..11 -> "morning"
            in 12..17 -> "afternoon"
            in 18..21 -> "evening"
            else -> "night"
        }
    }

    /**
     * Format hour for display
     */
    private fun formatHour(hour: Int): String {
        val period = if (hour < 12) "AM" else "PM"
        val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        return "$displayHour:00 $period"
    }

    /**
     * Load patterns from storage
     */
    private fun loadPatterns(): List<InteractionRecord> {
        val file = File(context.filesDir, PATTERN_FILE)
        if (!file.exists()) return emptyList()

        return try {
            val json = file.readText()
            val jsonArray = JSONArray(json)

            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                InteractionRecord(
                    timestamp = obj.getLong("timestamp"),
                    intent = obj.getString("intent"),
                    hour = obj.getInt("hour"),
                    dayOfWeek = obj.getInt("dayOfWeek"),
                    success = obj.optBoolean("success", true)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load patterns", e)
            emptyList()
        }
    }

    /**
     * Save patterns to storage
     */
    private fun savePatterns(patterns: List<InteractionRecord>) {
        val file = File(context.filesDir, PATTERN_FILE)

        try {
            val jsonArray = JSONArray()
            patterns.forEach { record ->
                val obj = JSONObject().apply {
                    put("timestamp", record.timestamp)
                    put("intent", record.intent)
                    put("hour", record.hour)
                    put("dayOfWeek", record.dayOfWeek)
                    put("success", record.success)
                }
                jsonArray.put(obj)
            }

            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save patterns", e)
        }
    }

    /**
     * Interaction record for pattern tracking
     */
    data class InteractionRecord(
        val timestamp: Long,
        val intent: String,
        val hour: Int,
        val dayOfWeek: Int,
        val success: Boolean
    )

    /**
     * Pattern prediction result
     */
    data class PatternPrediction(
        val predictedIntent: String,
        val confidence: Float,
        val reason: String,
        val suggestions: List<String>,
        val timeContext: String
    )

    /**
     * Pattern summary result
     */
    data class PatternSummary(
        val totalPatterns: Int,
        val mostCommonIntent: String,
        val mostActiveHour: Int,
        val intentDistribution: Map<String, Int>,
        val summary: String
    )

    /**
     * Internal prediction result
     */
    private data class Prediction(
        val predictedIntent: String,
        val confidence: Float,
        val reason: String,
        val suggestions: List<String>
    )
}
