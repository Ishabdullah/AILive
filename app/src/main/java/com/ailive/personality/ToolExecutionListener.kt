package com.ailive.personality

/**
 * Listener interface for tool execution events
 * Phase 6.1: Enables real-time dashboard updates
 */
interface ToolExecutionListener {
    /**
     * Called when a tool finishes execution
     * @param toolName The name of the tool that was executed
     * @param success Whether the execution was successful
     * @param executionTime The execution time in milliseconds
     */
    fun onToolExecuted(toolName: String, success: Boolean, executionTime: Long)
}
