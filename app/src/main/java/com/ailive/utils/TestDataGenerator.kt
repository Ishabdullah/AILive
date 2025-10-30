package com.ailive.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * TestDataGenerator - Creates sample data for testing visualizations
 * Phase 6.2: Testing utility
 */
object TestDataGenerator {

    /**
     * Generate sample pattern data
     */
    fun generatePatternData(context: Context) {
        val patterns = JSONArray()
        val calendar = Calendar.getInstance()

        // Generate 50 sample interactions
        for (i in 0..49) {
            val daysAgo = (0..7).random()
            calendar.timeInMillis = System.currentTimeMillis() - (daysAgo * 24 * 60 * 60 * 1000L)

            val hour = (6..22).random()
            val intent = listOf(
                "VISION", "EMOTION", "MEMORY", "DEVICE_CONTROL",
                "CONVERSATION", "PREDICTION"
            ).random()

            val pattern = JSONObject().apply {
                put("timestamp", calendar.timeInMillis)
                put("intent", intent)
                put("hour", hour)
                put("dayOfWeek", calendar.get(Calendar.DAY_OF_WEEK))
                put("success", true)
            }
            patterns.put(pattern)
        }

        // Save to file
        val file = File(context.filesDir, "user_patterns.json")
        file.writeText(patterns.toString())
    }

    /**
     * Generate sample feedback data
     */
    fun generateFeedbackData(context: Context) {
        val feedback = JSONArray()
        val calendar = Calendar.getInstance()

        // Generate 40 sample feedback entries
        for (i in 0..39) {
            val daysAgo = (0..7).random()
            calendar.timeInMillis = System.currentTimeMillis() - (daysAgo * 24 * 60 * 60 * 1000L)

            val type = if ((0..100).random() > 30) "positive" else "negative"
            val intent = listOf(
                "VISION", "EMOTION", "MEMORY", "DEVICE_CONTROL",
                "CONVERSATION", "PREDICTION"
            ).randomOrNull()

            val entry = JSONObject().apply {
                put("timestamp", calendar.timeInMillis)
                put("type", type)
                put("context", "Test feedback")
                if (intent != null) put("intent", intent)
                put("quality", (0.5f + (0..50).random() / 100f).toDouble())
                put("metadata", JSONObject())
            }
            feedback.put(entry)
        }

        // Save to file
        val file = File(context.filesDir, "user_feedback.json")
        file.writeText(feedback.toString())
    }

    /**
     * Generate all test data
     */
    fun generateAll(context: Context) {
        generatePatternData(context)
        generateFeedbackData(context)
    }

    /**
     * Clear all test data
     */
    fun clearAll(context: Context) {
        File(context.filesDir, "user_patterns.json").delete()
        File(context.filesDir, "user_feedback.json").delete()
    }
}
