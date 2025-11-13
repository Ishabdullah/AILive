package com.ailive.ui.dashboard

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.ailive.R
import com.ailive.core.AILiveCore
import com.ailive.personality.ToolExecutionListener
import com.ailive.personality.tools.AITool
import com.ailive.ui.visualizations.FeedbackChartView
import com.ailive.ui.visualizations.PatternGraphView
import com.ailive.utils.TestDataGenerator
import kotlinx.coroutines.*
import java.io.File

/**
 * Dashboard fragment displaying real-time tool status
 *
 * Phase 6.1: Core Dashboard
 * - Displays all 6 AI tools with status cards
 * - Shows execution statistics
 * - Real-time updates via tool execution listeners
 *
 * Phase 6.2: Data Visualizations
 * - Pattern analysis graphs (time-based, frequency)
 * - Feedback charts (satisfaction, intent performance)
 */
class DashboardFragment : Fragment(), ToolExecutionListener {

    companion object {
        private const val KEY_TOTAL_EXECUTIONS = "total_executions"
        private const val KEY_SUCCESSFUL_EXECUTIONS = "successful_executions"
    }

    private val TAG = "DashboardFragment"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Views
    private lateinit var totalToolsValue: TextView
    private lateinit var activeToolsValue: TextView
    private lateinit var executionsValue: TextView
    private lateinit var successRateValue: TextView
    private lateinit var toolStatusContainer: LinearLayout

    // Phase 6.2: Visualization views
    private lateinit var patternGraphView: PatternGraphView
    private lateinit var feedbackChartView: FeedbackChartView

    // Tool status tracking
    private val toolStatusMap = mutableMapOf<String, ToolStatus>()
    private var totalExecutions = 0
    private var successfulExecutions = 0

    // AILive core reference (will be set by MainActivity)
    var aiLiveCore: AILiveCore? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        totalToolsValue = view.findViewById(R.id.totalToolsValue)
        activeToolsValue = view.findViewById(R.id.activeToolsValue)
        executionsValue = view.findViewById(R.id.executionsValue)
        successRateValue = view.findViewById(R.id.successRateValue)
        toolStatusContainer = view.findViewById(R.id.toolStatusContainer)

        // Initialize visualization views (Phase 6.2)
        patternGraphView = view.findViewById(R.id.patternGraphView)
        feedbackChartView = view.findViewById(R.id.feedbackChartView)

        // Restore state if available
        savedInstanceState?.let {
            totalExecutions = it.getInt(KEY_TOTAL_EXECUTIONS, 0)
            successfulExecutions = it.getInt(KEY_SUCCESSFUL_EXECUTIONS, 0)
            Log.i(TAG, "Restored state: $totalExecutions executions, $successfulExecutions successful")
        }

        // Generate test data if needed (for Phase 6.2 testing)
        generateTestDataIfNeeded()

        // Initialize dashboard
        initializeDashboard()

        // Register as tool execution listener
        aiLiveCore?.personalityEngine?.addToolExecutionListener(this)

        // Start real-time updates
        startUpdates()

        Log.i(TAG, "Dashboard initialized")
    }

    /**
     * Generate test data if visualization data files don't exist
     * Phase 6.2: For testing visualizations
     */
    private fun generateTestDataIfNeeded() {
        val context = requireContext()
        val patternFile = File(context.filesDir, "user_patterns.json")
        val feedbackFile = File(context.filesDir, "user_feedback.json")

        if (!patternFile.exists() || !feedbackFile.exists()) {
            Log.i(TAG, "Generating test data for visualizations...")
            TestDataGenerator.generateAll(context)
        }
    }

    /**
     * Initialize dashboard with tool status cards
     */
    private fun initializeDashboard() {
        // Get all tools from PersonalityEngine
        val personalityEngine = aiLiveCore?.personalityEngine
        if (personalityEngine == null) {
            Log.w(TAG, "PersonalityEngine not available yet")
            return
        }

        // Get tools via public method
        val tools = personalityEngine.getAllTools()

        Log.i(TAG, "Found ${tools.size} registered tools")

        // Create status cards for each tool
        tools.forEach { tool ->
            val icon = getToolIcon(tool.name)
            val displayName = getToolDisplayName(tool.name)

            val status = ToolStatus(
                toolName = tool.name,
                displayName = displayName,
                icon = icon,
                state = ToolState.READY,
                isActive = true
            )

            toolStatusMap[tool.name] = status

            // Create and add card
            val card = ToolStatusCard(requireContext())
            card.updateStatus(status)
            toolStatusContainer.addView(card)
        }

        // Update statistics
        updateStatistics()
    }

    /**
     * Get tool icon emoji
     */
    private fun getToolIcon(toolName: String): String {
        return when (toolName) {
            "analyze_sentiment" -> "🎭"
            "control_device" -> "🎮"
            "retrieve_memory" -> "💾"
            "analyze_vision" -> "👁️"
            "analyze_patterns" -> "📊"
            "track_feedback" -> "⭐"
            else -> "🔧"
        }
    }

    /**
     * Get tool display name
     */
    private fun getToolDisplayName(toolName: String): String {
        return when (toolName) {
            "analyze_sentiment" -> "Sentiment Analysis"
            "control_device" -> "Device Control"
            "retrieve_memory" -> "Memory Retrieval"
            "analyze_vision" -> "Vision Analysis"
            "analyze_patterns" -> "Pattern Analysis"
            "track_feedback" -> "Feedback Tracking"
            else -> toolName.replace("_", " ").split(" ")
                .joinToString(" ") { it.capitalize() }
        }
    }

    /**
     * Update dashboard statistics
     */
    private fun updateStatistics() {
        val totalTools = toolStatusMap.size
        val activeTools = toolStatusMap.values.count { it.isActive }

        totalToolsValue.text = totalTools.toString()
        activeToolsValue.text = activeTools.toString()
        executionsValue.text = totalExecutions.toString()

        val successRate = if (totalExecutions > 0) {
            (successfulExecutions.toFloat() / totalExecutions * 100).toInt()
        } else {
            0
        }
        successRateValue.text = if (totalExecutions > 0) "$successRate%" else "--"
    }

    /**
     * Start real-time updates
     */
    private fun startUpdates() {
        // Update every 2 seconds
        scope.launch {
            while (isActive) {
                delay(2000)
                updateDashboard()
            }
        }
    }

    /**
     * Update dashboard with current tool status
     */
    private fun updateDashboard() {
        // Refresh tool cards
        toolStatusMap.values.forEachIndexed { index, status ->
            if (index < toolStatusContainer.childCount) {
                val card = toolStatusContainer.getChildAt(index) as? ToolStatusCard
                card?.updateStatus(status)
            }
        }

        // Refresh statistics
        updateStatistics()

        // Update visualizations (Phase 6.2)
        patternGraphView.updateCharts()
        feedbackChartView.updateCharts()
    }

    /**
     * Tool execution listener callback
     */
    override fun onToolExecuted(toolName: String, success: Boolean, executionTime: Long) {
        toolStatusMap[toolName]?.let { currentStatus ->
            toolStatusMap[toolName] = currentStatus.copy(
                state = if (success) ToolState.SUCCESS else ToolState.ERROR,
                lastExecutionTime = System.currentTimeMillis(),
                executionCount = currentStatus.executionCount + 1
            )

            totalExecutions++
            if (success) successfulExecutions++

            Log.i(TAG, "Tool executed: $toolName, success=$success, time=${executionTime}ms")

            // Reset to READY after 2 seconds
            scope.launch {
                delay(2000)
                toolStatusMap[toolName]?.let { status ->
                    toolStatusMap[toolName] = status.copy(state = ToolState.READY)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save dashboard state for configuration changes and fragment recreation
        outState.putInt(KEY_TOTAL_EXECUTIONS, totalExecutions)
        outState.putInt(KEY_SUCCESSFUL_EXECUTIONS, successfulExecutions)
        Log.d(TAG, "Saved state: $totalExecutions executions, $successfulExecutions successful")
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Unregister listener
        aiLiveCore?.personalityEngine?.removeToolExecutionListener(this)

        scope.cancel()
    }
}
