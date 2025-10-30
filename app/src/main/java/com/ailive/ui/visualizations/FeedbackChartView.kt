package com.ailive.ui.visualizations

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.ailive.R
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.BarChart
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * FeedbackChartView - Visualizes user feedback and satisfaction
 * Phase 6.2: Shows satisfaction rates, trends, and performance by intent
 *
 * Data Source: FeedbackTrackingTool → user_feedback.json
 */
class FeedbackChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val satisfactionChart: LineChart
    private val intentPerformanceChart: BarChart
    private val summaryText: TextView

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_feedback_chart, this, true)

        satisfactionChart = findViewById(R.id.satisfactionLineChart)
        intentPerformanceChart = findViewById(R.id.intentPerformanceChart)
        summaryText = findViewById(R.id.feedbackSummaryText)

        // Configure charts
        ChartUtils.configureLineChart(
            satisfactionChart,
            "Satisfaction Over Time"
        )

        ChartUtils.configureBarChart(
            intentPerformanceChart,
            "Performance by Intent",
            listOf("Vision", "Emotion", "Memory", "Device", "Predict")
        )

        // Load initial data
        updateCharts()
    }

    /**
     * Update charts with latest feedback data
     */
    fun updateCharts() {
        val feedbackData = loadFeedbackData()

        // Update satisfaction line chart
        updateSatisfactionChart(feedbackData)

        // Update intent performance bar chart
        updateIntentPerformanceChart(feedbackData)

        // Update summary
        updateSummary(feedbackData)
    }

    /**
     * Load feedback data from storage
     */
    private fun loadFeedbackData(): FeedbackData {
        return try {
            val file = File(context.filesDir, "user_feedback.json")
            if (!file.exists()) {
                return FeedbackData()
            }

            // Read as JSONArray (FeedbackTrackingTool saves as plain array)
            val feedbackArray = JSONArray(file.readText())

            var positiveCount = 0
            var negativeCount = 0
            val intentScores = mutableMapOf<String, MutableList<Boolean>>()

            for (i in 0 until feedbackArray.length()) {
                val feedback = feedbackArray.getJSONObject(i)
                val type = feedback.optString("type", "")
                val intent = feedback.optString("intent", "unknown")

                val isPositive = type == "positive" || type == "correction"

                if (type == "positive" || type == "negative") {
                    if (isPositive) positiveCount++ else negativeCount++
                }

                // Track by intent (only if intent is present)
                if (intent != "unknown" && intent.isNotEmpty()) {
                    if (!intentScores.containsKey(intent)) {
                        intentScores[intent] = mutableListOf()
                    }
                    intentScores[intent]?.add(isPositive)
                }
            }

            val total = positiveCount + negativeCount
            val overallSatisfaction = if (total > 0) {
                positiveCount.toFloat() / total
            } else 0f

            // Calculate satisfaction by intent
            val intentSatisfaction = intentScores.mapValues { (_, scores) ->
                val positives = scores.count { it }
                if (scores.isNotEmpty()) positives.toFloat() / scores.size else 0f
            }

            FeedbackData(
                overallSatisfaction = overallSatisfaction,
                intentSatisfaction = intentSatisfaction,
                totalFeedback = total,
                positiveCount = positiveCount,
                negativeCount = negativeCount
            )

        } catch (e: Exception) {
            e.printStackTrace()
            FeedbackData()
        }
    }

    /**
     * Update satisfaction line chart
     */
    private fun updateSatisfactionChart(data: FeedbackData) {
        if (data.totalFeedback == 0) {
            satisfactionChart.clear()
            return
        }

        // For now, show current satisfaction as a simple line
        // In future, can track satisfaction over time
        val values = listOf(
            data.overallSatisfaction * 100
        )

        val lineData = ChartUtils.createLineData(
            listOf("Satisfaction" to values)
        )

        satisfactionChart.data = lineData
        satisfactionChart.invalidate()
    }

    /**
     * Update intent performance bar chart
     */
    private fun updateIntentPerformanceChart(data: FeedbackData) {
        if (data.intentSatisfaction.isEmpty()) {
            intentPerformanceChart.clear()
            return
        }

        // Get top 5 intents by feedback count
        val topIntents = data.intentSatisfaction.entries
            .sortedByDescending { it.value }
            .take(5)

        val labels = topIntents.map { formatIntentName(it.key) }
        val values = topIntents.map { it.value * 100 } // Convert to percentage

        val barData = ChartUtils.createBarData(
            labels = labels,
            datasets = listOf("Satisfaction %" to values)
        )

        intentPerformanceChart.data = barData
        intentPerformanceChart.invalidate()
    }

    /**
     * Update summary text
     */
    private fun updateSummary(data: FeedbackData) {
        if (data.totalFeedback == 0) {
            summaryText.text = "No feedback data available yet."
            return
        }

        val satisfactionPercent = (data.overallSatisfaction * 100).toInt()
        val trend = if (data.positiveCount > data.negativeCount) {
            ChartUtils.getTrendIcon("UP")
        } else {
            ChartUtils.getTrendIcon("DOWN")
        }

        // Find best performing intent
        val bestIntent = data.intentSatisfaction.maxByOrNull { it.value }
        val bestIntentName = bestIntent?.key?.let { formatIntentName(it) } ?: "N/A"
        val bestScore = ((bestIntent?.value ?: 0f) * 100).toInt()

        summaryText.text = """
            Overall Satisfaction: $satisfactionPercent% $trend
            Total Feedback: ${data.totalFeedback}
            Best Performer: $bestIntentName ($bestScore%)
        """.trimIndent()

        // Set text color based on satisfaction
        val textColor = when {
            data.overallSatisfaction >= 0.8f -> ChartUtils.POSITIVE_COLOR
            data.overallSatisfaction >= 0.6f -> ChartUtils.NEUTRAL_COLOR
            else -> ChartUtils.NEGATIVE_COLOR
        }
        summaryText.setTextColor(textColor)
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
     * Data class for feedback information
     */
    private data class FeedbackData(
        val overallSatisfaction: Float = 0f,
        val intentSatisfaction: Map<String, Float> = emptyMap(),
        val totalFeedback: Int = 0,
        val positiveCount: Int = 0,
        val negativeCount: Int = 0
    )
}
