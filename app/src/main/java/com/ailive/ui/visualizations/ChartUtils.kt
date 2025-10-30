package com.ailive.ui.visualizations

import android.graphics.Color
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

/**
 * ChartUtils - Shared utilities for data visualization
 * Phase 6.2: Provides consistent chart styling and configuration
 */
object ChartUtils {

    // Material Design 3 Color Palette
    val CHART_COLORS = listOf(
        Color.parseColor("#FF6B6B"), // Red
        Color.parseColor("#4ECDC4"), // Teal
        Color.parseColor("#45B7D1"), // Blue
        Color.parseColor("#FFA07A"), // Orange
        Color.parseColor("#98D8C8"), // Mint
        Color.parseColor("#F7DC6F"), // Yellow
        Color.parseColor("#BB8FCE"), // Purple
        Color.parseColor("#85C1E2")  // Sky Blue
    )

    val POSITIVE_COLOR = Color.parseColor("#66BB6A")  // Green
    val NEGATIVE_COLOR = Color.parseColor("#EF5350")  // Red
    val NEUTRAL_COLOR = Color.parseColor("#FFCA28")   // Amber
    val TEXT_COLOR = Color.parseColor("#FFFFFF")      // White
    val GRID_COLOR = Color.parseColor("#444444")      // Dark Gray

    /**
     * Configure bar chart with Material Design 3 styling
     */
    fun configureBarChart(
        chart: BarChart,
        description: String = "",
        xLabels: List<String> = emptyList()
    ) {
        chart.apply {
            // Description
            this.description.text = description
            this.description.textColor = TEXT_COLOR
            this.description.textSize = 12f

            // Legend
            legend.textColor = TEXT_COLOR
            legend.textSize = 12f

            // X-Axis
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = TEXT_COLOR
                textSize = 10f
                setDrawGridLines(false)
                granularity = 1f
                if (xLabels.isNotEmpty()) {
                    valueFormatter = IndexAxisValueFormatter(xLabels)
                }
            }

            // Y-Axis (Left)
            axisLeft.apply {
                textColor = TEXT_COLOR
                textSize = 10f
                gridColor = GRID_COLOR
                setDrawAxisLine(false)
            }

            // Y-Axis (Right)
            axisRight.isEnabled = false

            // Interaction
            setTouchEnabled(true)
            setDragEnabled(true)
            setScaleEnabled(false)
            setPinchZoom(false)

            // Animation
            animateY(1000)
        }
    }

    /**
     * Configure line chart with Material Design 3 styling
     */
    fun configureLineChart(
        chart: LineChart,
        description: String = ""
    ) {
        chart.apply {
            // Description
            this.description.text = description
            this.description.textColor = TEXT_COLOR
            this.description.textSize = 12f

            // Legend
            legend.textColor = TEXT_COLOR
            legend.textSize = 12f

            // X-Axis
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = TEXT_COLOR
                textSize = 10f
                gridColor = GRID_COLOR
            }

            // Y-Axis (Left)
            axisLeft.apply {
                textColor = TEXT_COLOR
                textSize = 10f
                gridColor = GRID_COLOR
                setDrawAxisLine(false)
            }

            // Y-Axis (Right)
            axisRight.isEnabled = false

            // Interaction
            setTouchEnabled(true)
            setDragEnabled(true)
            setScaleEnabled(false)
            setPinchZoom(false)

            // Animation
            animateX(1000)
        }
    }

    /**
     * Configure pie chart with Material Design 3 styling
     */
    fun configurePieChart(
        chart: PieChart,
        description: String = ""
    ) {
        chart.apply {
            // Description
            this.description.text = description
            this.description.textColor = TEXT_COLOR
            this.description.textSize = 12f

            // Legend
            legend.textColor = TEXT_COLOR
            legend.textSize = 12f

            // Hole
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            holeRadius = 40f
            transparentCircleRadius = 45f

            // Labels
            setDrawEntryLabels(true)
            setEntryLabelColor(TEXT_COLOR)
            setEntryLabelTextSize(11f)

            // Interaction
            setTouchEnabled(true)
            isRotationEnabled = true

            // Animation
            animateY(1000)
        }
    }

    /**
     * Create bar data with multiple datasets
     */
    fun createBarData(
        labels: List<String>,
        datasets: List<Pair<String, List<Float>>>
    ): BarData {
        val dataSets = datasets.mapIndexed { index, (label, values) ->
            val entries = values.mapIndexed { i, value ->
                BarEntry(i.toFloat(), value)
            }
            BarDataSet(entries, label).apply {
                color = CHART_COLORS[index % CHART_COLORS.size]
                valueTextColor = TEXT_COLOR
                valueTextSize = 10f
            }
        }

        return BarData(dataSets).apply {
            barWidth = 0.8f / datasets.size
        }
    }

    /**
     * Create line data with multiple datasets
     */
    fun createLineData(
        datasets: List<Pair<String, List<Float>>>
    ): LineData {
        val dataSets = datasets.mapIndexed { index, (label, values) ->
            val entries = values.mapIndexed { i, value ->
                Entry(i.toFloat(), value)
            }
            LineDataSet(entries, label).apply {
                color = CHART_COLORS[index % CHART_COLORS.size]
                setCircleColor(CHART_COLORS[index % CHART_COLORS.size])
                lineWidth = 2f
                circleRadius = 4f
                setDrawValues(true)
                valueTextColor = TEXT_COLOR
                valueTextSize = 10f
                mode = LineDataSet.Mode.CUBIC_BEZIER  // Smooth curves
            }
        }

        return LineData(dataSets)
    }

    /**
     * Create pie data
     */
    fun createPieData(
        labels: List<String>,
        values: List<Float>
    ): PieData {
        val entries = values.mapIndexed { index, value ->
            PieEntry(value, labels.getOrNull(index) ?: "")
        }

        val dataSet = PieDataSet(entries, "").apply {
            colors = CHART_COLORS
            valueTextColor = TEXT_COLOR
            valueTextSize = 12f
            sliceSpace = 2f
        }

        return PieData(dataSet)
    }

    /**
     * Format percentage for display
     */
    fun formatPercentage(value: Float): String {
        return "${(value * 100).toInt()}%"
    }

    /**
     * Get trend color based on direction
     */
    fun getTrendColor(trend: String): Int {
        return when (trend.uppercase()) {
            "IMPROVING", "UP" -> POSITIVE_COLOR
            "DECLINING", "DOWN" -> NEGATIVE_COLOR
            else -> NEUTRAL_COLOR
        }
    }

    /**
     * Get trend icon based on direction
     */
    fun getTrendIcon(trend: String): String {
        return when (trend.uppercase()) {
            "IMPROVING", "UP" -> "↗️"
            "DECLINING", "DOWN" -> "↘️"
            else -> "→"
        }
    }
}
