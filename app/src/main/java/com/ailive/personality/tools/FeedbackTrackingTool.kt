package com.ailive.personality.tools

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * FeedbackTrackingTool - Tracks user feedback to learn preferences
 *
 * Learns from user reactions (positive/negative feedback, corrections, preferences)
 * to improve future responses and recommendations.
 *
 * Phase 5: Tool Expansion
 */
class FeedbackTrackingTool(
    private val context: Context
) : BaseTool() {

    companion object {
        private const val TAG = "FeedbackTrackingTool"
        private const val FEEDBACK_FILE = "user_feedback.json"
        private const val MAX_FEEDBACK_ENTRIES = 500
    }

    override val name: String = "track_feedback"

    override val description: String =
        "Tracks user feedback and preferences to learn and improve. " +
        "Records positive/negative reactions, corrections, and user preferences."

    override val requiresPermissions: Boolean = false

    override suspend fun isAvailable(): Boolean {
        return true  // Always available
    }

    override fun validateParams(params: Map<String, Any>): String? {
        val action = params["action"] as? String
        if (action == null) {
            return "Parameter 'action' is required"
        }

        if (action !in listOf("record", "analyze", "preferences", "stats")) {
            return "Invalid action. Must be 'record', 'analyze', 'preferences', or 'stats'"
        }

        if (action == "record" && !params.containsKey("feedback_type")) {
            return "Parameter 'feedback_type' is required for 'record' action"
        }

        return null
    }

    override suspend fun executeInternal(params: Map<String, Any>): ToolResult {
        return withContext(Dispatchers.IO) {
            val action = params["action"] as String

            Log.d(TAG, "Feedback action: $action")

            try {
                when (action) {
                    "record" -> recordFeedback(params)
                    "analyze" -> analyzeFeedback(params)
                    "preferences" -> getUserPreferences()
                    "stats" -> getFeedbackStats()
                    else -> ToolResult.Failure(
                        error = IllegalArgumentException("Unknown action"),
                        reason = "Invalid action: $action",
                        recoverable = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Feedback tracking failed", e)
                ToolResult.Failure(
                    error = e,
                    reason = "Failed to process feedback: ${e.message}",
                    recoverable = true
                )
            }
        }
    }

    /**
     * Record user feedback
     */
    private fun recordFeedback(params: Map<String, Any>): ToolResult {
        val feedbackType = params["feedback_type"] as String  // "positive", "negative", "correction", "preference"
        val context = params["context"] as? String ?: ""
        val intent = params["intent"] as? String
        val responseQuality = params["quality"] as? Float

        val feedback = FeedbackEntry(
            timestamp = System.currentTimeMillis(),
            type = feedbackType,
            context = context,
            intent = intent,
            quality = responseQuality,
            metadata = params.filterKeys {
                it !in listOf("action", "feedback_type", "context", "intent", "quality")
            }
        )

        val feedbackList = loadFeedback().toMutableList()
        feedbackList.add(feedback)

        // Keep most recent entries
        while (feedbackList.size > MAX_FEEDBACK_ENTRIES) {
            feedbackList.removeAt(0)
        }

        saveFeedback(feedbackList)

        Log.i(TAG, "✓ Recorded $feedbackType feedback")

        return ToolResult.Success(
            data = mapOf(
                "recorded" to true,
                "type" to feedbackType,
                "total_feedback" to feedbackList.size
            ),
            context = mapOf(
                "learning_enabled" to true,
                "feedback_count" to feedbackList.size
            )
        )
    }

    /**
     * Analyze feedback patterns
     */
    private fun analyzeFeedback(params: Map<String, Any>): ToolResult {
        val feedbackList = loadFeedback()

        if (feedbackList.isEmpty()) {
            return ToolResult.Success(
                data = mapOf(
                    "message" to "No feedback data yet. Your reactions help me learn and improve!",
                    "feedback_count" to 0
                )
            )
        }

        // Analyze by type
        val typeCounts = feedbackList.groupingBy { it.type }.eachCount()
        val positiveCount = typeCounts["positive"] ?: 0
        val negativeCount = typeCounts["negative"] ?: 0
        val totalRated = positiveCount + negativeCount
        val satisfactionRate = if (totalRated > 0) {
            (positiveCount.toFloat() / totalRated.toFloat() * 100).toInt()
        } else 0

        // Analyze by intent
        val intentFeedback = feedbackList
            .filter { it.intent != null }
            .groupBy { it.intent }
            .mapValues { (_, entries) ->
                val positive = entries.count { it.type == "positive" }
                val negative = entries.count { it.type == "negative" }
                mapOf(
                    "positive" to positive,
                    "negative" to negative,
                    "score" to if (positive + negative > 0)
                        (positive.toFloat() / (positive + negative)) * 100
                    else 0f
                )
            }

        // Find best and worst performing intents
        val bestIntent = intentFeedback.maxByOrNull {
            (it.value["score"] as? Float) ?: 0f
        }?.key
        val worstIntent = intentFeedback.minByOrNull {
            (it.value["score"] as? Float) ?: Float.MAX_VALUE
        }?.key

        val analysis = FeedbackAnalysis(
            totalFeedback = feedbackList.size,
            satisfactionRate = satisfactionRate,
            positiveFeedback = positiveCount,
            negativeFeedback = negativeCount,
            bestPerformingIntent = bestIntent,
            worstPerformingIntent = worstIntent,
            intentPerformance = intentFeedback,
            recentTrend = calculateTrend(feedbackList)
        )

        Log.i(TAG, "✓ Analyzed ${feedbackList.size} feedback entries")

        return ToolResult.Success(
            data = analysis,
            context = mapOf(
                "satisfaction_rate" to satisfactionRate,
                "has_feedback" to true
            )
        )
    }

    /**
     * Get user preferences learned from feedback
     */
    private fun getUserPreferences(): ToolResult {
        val feedbackList = loadFeedback()
        val preferenceEntries = feedbackList.filter { it.type == "preference" }

        if (preferenceEntries.isEmpty()) {
            return ToolResult.Success(
                data = mapOf(
                    "message" to "No preferences recorded yet.",
                    "preferences" to emptyMap<String, Any>()
                )
            )
        }

        // Extract preferences from metadata
        val preferences = mutableMapOf<String, Any>()
        preferenceEntries.forEach { entry ->
            entry.metadata.forEach { (key, value) ->
                preferences[key] = value
            }
        }

        Log.i(TAG, "✓ Retrieved ${preferences.size} user preferences")

        return ToolResult.Success(
            data = UserPreferences(
                preferences = preferences,
                lastUpdated = preferenceEntries.maxOfOrNull { it.timestamp } ?: 0,
                preferenceCount = preferences.size
            ),
            context = mapOf(
                "has_preferences" to preferences.isNotEmpty()
            )
        )
    }

    /**
     * Get feedback statistics
     */
    private fun getFeedbackStats(): ToolResult {
        val feedbackList = loadFeedback()

        val stats = FeedbackStats(
            totalEntries = feedbackList.size,
            byType = feedbackList.groupingBy { it.type }.eachCount(),
            byIntent = feedbackList.mapNotNull { it.intent }.groupingBy { it }.eachCount(),
            averageQuality = feedbackList.mapNotNull { it.quality }.average().toFloat(),
            oldestEntry = feedbackList.minOfOrNull { it.timestamp } ?: 0,
            newestEntry = feedbackList.maxOfOrNull { it.timestamp } ?: 0
        )

        Log.i(TAG, "✓ Generated feedback statistics")

        return ToolResult.Success(
            data = stats,
            context = mapOf(
                "total_feedback" to stats.totalEntries
            )
        )
    }

    /**
     * Calculate recent trend (improving/declining/stable)
     */
    private fun calculateTrend(feedbackList: List<FeedbackEntry>): String {
        if (feedbackList.size < 10) return "insufficient_data"

        // Compare recent 25% vs previous 25%
        val recentSize = feedbackList.size / 4
        val recent = feedbackList.takeLast(recentSize)
        val previous = feedbackList.dropLast(recentSize).takeLast(recentSize)

        val recentPositive = recent.count { it.type == "positive" }.toFloat() / recentSize
        val previousPositive = previous.count { it.type == "positive" }.toFloat() / recentSize

        return when {
            recentPositive > previousPositive + 0.1f -> "improving"
            recentPositive < previousPositive - 0.1f -> "declining"
            else -> "stable"
        }
    }

    /**
     * Load feedback from storage
     */
    private fun loadFeedback(): List<FeedbackEntry> {
        val file = File(context.filesDir, FEEDBACK_FILE)
        if (!file.exists()) return emptyList()

        return try {
            val json = file.readText()
            val jsonArray = JSONArray(json)

            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                FeedbackEntry(
                    timestamp = obj.getLong("timestamp"),
                    type = obj.getString("type"),
                    context = obj.optString("context", ""),
                    intent = obj.optString("intent", null),
                    quality = obj.optDouble("quality", -1.0).toFloat().takeIf { it >= 0 },
                    metadata = parseMetadata(obj.optJSONObject("metadata"))
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load feedback", e)
            emptyList()
        }
    }

    /**
     * Save feedback to storage
     */
    private fun saveFeedback(feedbackList: List<FeedbackEntry>) {
        val file = File(context.filesDir, FEEDBACK_FILE)

        try {
            val jsonArray = JSONArray()
            feedbackList.forEach { entry ->
                val obj = JSONObject().apply {
                    put("timestamp", entry.timestamp)
                    put("type", entry.type)
                    put("context", entry.context)
                    if (entry.intent != null) put("intent", entry.intent)
                    if (entry.quality != null) put("quality", entry.quality.toDouble())
                    put("metadata", JSONObject(entry.metadata))
                }
                jsonArray.put(obj)
            }

            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save feedback", e)
        }
    }

    /**
     * Parse metadata from JSON
     */
    private fun parseMetadata(jsonObject: JSONObject?): Map<String, Any> {
        if (jsonObject == null) return emptyMap()

        val map = mutableMapOf<String, Any>()
        jsonObject.keys().forEach { key ->
            map[key] = jsonObject.get(key)
        }
        return map
    }

    /**
     * Feedback entry data class
     */
    data class FeedbackEntry(
        val timestamp: Long,
        val type: String,  // positive, negative, correction, preference
        val context: String,
        val intent: String? = null,
        val quality: Float? = null,
        val metadata: Map<String, Any> = emptyMap()
    )

    /**
     * Feedback analysis result
     */
    data class FeedbackAnalysis(
        val totalFeedback: Int,
        val satisfactionRate: Int,
        val positiveFeedback: Int,
        val negativeFeedback: Int,
        val bestPerformingIntent: String?,
        val worstPerformingIntent: String?,
        val intentPerformance: Map<String?, Map<String, Any>>,
        val recentTrend: String
    )

    /**
     * User preferences result
     */
    data class UserPreferences(
        val preferences: Map<String, Any>,
        val lastUpdated: Long,
        val preferenceCount: Int
    )

    /**
     * Feedback statistics result
     */
    data class FeedbackStats(
        val totalEntries: Int,
        val byType: Map<String, Int>,
        val byIntent: Map<String, Int>,
        val averageQuality: Float,
        val oldestEntry: Long,
        val newestEntry: Long
    )
}
