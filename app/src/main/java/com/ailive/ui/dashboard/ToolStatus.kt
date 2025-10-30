package com.ailive.ui.dashboard

/**
 * Tool status tracking for dashboard display
 */
data class ToolStatus(
    val toolName: String,
    val displayName: String,
    val icon: String,
    val state: ToolState,
    val lastExecutionTime: Long = 0L,
    val executionCount: Int = 0,
    val lastResult: String? = null,
    val isActive: Boolean = true
)

/**
 * Tool execution state
 */
enum class ToolState(val displayName: String, val colorHex: String) {
    READY("Ready", "#4CAF50"),           // Green
    EXECUTING("Executing", "#2196F3"),   // Blue
    SUCCESS("Success", "#66BB6A"),       // Light Green
    ERROR("Error", "#F44336"),           // Red
    BLOCKED("Blocked", "#FF9800"),       // Orange
    UNAVAILABLE("Unavailable", "#9E9E9E"), // Gray
    INACTIVE("Inactive", "#757575")      // Dark Gray
}

/**
 * Dashboard statistics
 */
data class DashboardStats(
    val totalTools: Int = 0,
    val activeTools: Int = 0,
    val totalExecutions: Int = 0,
    val successRate: Float = 0f
)
