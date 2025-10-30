package com.ailive.ui.dashboard

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.ailive.R

/**
 * Tool status card view component
 */
class ToolStatusCard(context: Context) : CardView(context) {

    private val toolIcon: TextView
    private val toolName: TextView
    private val toolId: TextView
    private val statusBadge: TextView
    private val executionStats: TextView

    init {
        // Inflate layout
        LayoutInflater.from(context).inflate(R.layout.tool_status_card, this, true)

        // Get views
        toolIcon = findViewById(R.id.toolIcon)
        toolName = findViewById(R.id.toolName)
        toolId = findViewById(R.id.toolId)
        statusBadge = findViewById(R.id.statusBadge)
        executionStats = findViewById(R.id.executionStats)
    }

    /**
     * Update card with tool status
     */
    fun updateStatus(status: ToolStatus) {
        toolIcon.text = status.icon
        toolName.text = status.displayName
        toolId.text = status.toolName

        // Update status badge
        statusBadge.text = "● ${status.state.displayName}"
        statusBadge.setTextColor(Color.parseColor(status.state.colorHex))

        // Update execution stats
        val lastExecText = if (status.lastExecutionTime > 0) {
            val timeAgo = formatTimeAgo(System.currentTimeMillis() - status.lastExecutionTime)
            "$timeAgo ago"
        } else {
            "never"
        }

        executionStats.text = "${status.executionCount} executions • Last: $lastExecText"

        // Dim card if inactive
        alpha = if (status.isActive) 1.0f else 0.5f
    }

    /**
     * Format time ago in human-readable format
     */
    private fun formatTimeAgo(ms: Long): String {
        val seconds = ms / 1000
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m"
            seconds < 86400 -> "${seconds / 3600}h"
            else -> "${seconds / 86400}d"
        }
    }
}
