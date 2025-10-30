package com.ailive.ui.visualizations

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.ailive.R
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import org.json.JSONObject
import java.io.File

/**
 * PatternGraphView - Visualizes user behavior patterns
 * Phase 6.2: Shows time-based patterns, frequency analysis, and trends
 *
 * Data Source: PatternAnalysisTool → user_patterns.json
 */
class PatternGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val timeChart: BarChart
    private val frequencyChart: PieChart
    private val summaryText: TextView

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_pattern_graph, this, true)

        timeChart = findViewById(R.id.timePatternChart)
        frequencyChart = findViewById(R.id.frequencyPatternChart)
        summaryText = findViewById(R.id.patternSummaryText)

        // Configure charts
        ChartUtils.configureBarChart(
            timeChart,
            "Activity by Time of Day",
            listOf("Morning", "Afternoon", "Evening", "Night")
        )

        ChartUtils.configurePieChart(
            frequencyChart,
            "Most Common Requests"
        )

        // Load initial data
        updateCharts()
    }

    /**
     * Update charts with latest pattern data
     */
    fun updateCharts() {
        val patternData = loadPatternData()

        // Update time-based bar chart
        updateTimeChart(patternData)

        // Update frequency pie chart
        updateFrequencyChart(patternData)

        // Update summary text
        updateSummary(patternData)
    }

    /**
     * Load pattern data from storage
     */
    private fun loadPatternData(): PatternData {
        return try {
            val file = File(context.filesDir, "user_patterns.json")
            if (!file.exists()) {
                return PatternData() // Empty default
            }

            // Read as JSONArray (PatternAnalysisTool saves as plain array)
            val patternsArray = JSONArray(file.readText())

            val timePatterns = mutableMapOf<String, Int>()
            val requestCounts = mutableMapOf<String, Int>()

            for (i in 0 until patternsArray.length()) {
                val pattern = patternsArray.getJSONObject(i)
                val hour = pattern.optInt("hour", 12)
                val intent = pattern.optString("intent", "unknown")

                // Map hour to time of day
                val timeOfDay = when (hour) {
                    in 5..11 -> "morning"
                    in 12..17 -> "afternoon"
                    in 18..21 -> "evening"
                    else -> "night"
                }

                // Count by time of day
                timePatterns[timeOfDay] = timePatterns.getOrDefault(timeOfDay, 0) + 1

                // Count by intent
                requestCounts[intent] = requestCounts.getOrDefault(intent, 0) + 1
            }

            PatternData(
                timePatterns = timePatterns,
                requestCounts = requestCounts,
                totalPatterns = patternsArray.length()
            )

        } catch (e: Exception) {
            e.printStackTrace()
            PatternData() // Return empty on error
        }
    }

    /**
     * Update time-based bar chart
     */
    private fun updateTimeChart(data: PatternData) {
        val morningCount = data.timePatterns.getOrDefault("morning", 0).toFloat()
        val afternoonCount = data.timePatterns.getOrDefault("afternoon", 0).toFloat()
        val eveningCount = data.timePatterns.getOrDefault("evening", 0).toFloat()
        val nightCount = data.timePatterns.getOrDefault("night", 0).toFloat()

        val values = listOf(morningCount, afternoonCount, eveningCount, nightCount)

        if (values.all { it == 0f }) {
            // No data yet
            summaryText.text = "No pattern data available yet. Use AILive to build patterns!"
            timeChart.clear()
            return
        }

        val barData = ChartUtils.createBarData(
            labels = listOf("Morning", "Afternoon", "Evening", "Night"),
            datasets = listOf("Activity" to values)
        )

        timeChart.data = barData
        timeChart.invalidate()
    }

    /**
     * Update frequency pie chart
     */
    private fun updateFrequencyChart(data: PatternData) {
        if (data.requestCounts.isEmpty()) {
            frequencyChart.clear()
            return
        }

        // Get top 5 most frequent requests
        val topRequests = data.requestCounts.entries
            .sortedByDescending { it.value }
            .take(5)

        val labels = topRequests.map { formatIntentName(it.key) }
        val values = topRequests.map { it.value.toFloat() }

        val pieData = ChartUtils.createPieData(labels, values)
        frequencyChart.data = pieData
        frequencyChart.invalidate()
    }

    /**
     * Update summary text
     */
    private fun updateSummary(data: PatternData) {
        if (data.totalPatterns == 0) {
            summaryText.text = "No patterns detected yet."
            return
        }

        // Find most active time
        val mostActiveTime = data.timePatterns.maxByOrNull { it.value }?.key ?: "unknown"

        // Find most common request
        val mostCommonRequest = data.requestCounts.maxByOrNull { it.value }?.key ?: "unknown"

        summaryText.text = """
            📊 ${data.totalPatterns} patterns detected
            ⏰ Most active: ${formatTimeOfDay(mostActiveTime)}
            🎯 Most common: ${formatIntentName(mostCommonRequest)}
        """.trimIndent()
    }

    /**
     * Format time of day for display
     */
    private fun formatTimeOfDay(time: String): String {
        return when (time.lowercase()) {
            "morning" -> "Morning (6AM-12PM)"
            "afternoon" -> "Afternoon (12PM-6PM)"
            "evening" -> "Evening (6PM-12AM)"
            "night" -> "Night (12AM-6AM)"
            else -> time
        }
    }

    /**
     * Format intent name for display
     */
    private fun formatIntentName(intent: String): String {
        return intent.replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.capitalize() }
    }

    /**
     * Data class for pattern information
     */
    private data class PatternData(
        val timePatterns: Map<String, Int> = emptyMap(),
        val requestCounts: Map<String, Int> = emptyMap(),
        val totalPatterns: Int = 0
    )
}
