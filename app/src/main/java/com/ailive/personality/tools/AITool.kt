package com.ailive.personality.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AITool - Base interface for PersonalityEngine capabilities
 *
 * Tools are the building blocks of AILive's unified intelligence.
 * Each tool provides a specific capability (vision, memory, device control, etc.)
 * that the PersonalityEngine can invoke seamlessly.
 *
 * Design Philosophy:
 * - Tools are NOT personalities - they're capabilities
 * - Tools execute specific functions and return results
 * - The PersonalityEngine integrates tool results into coherent responses
 * - Users never see tools directly - only the unified personality
 */
interface AITool {
    /**
     * Unique identifier for this tool
     */
    val name: String

    /**
     * Human-readable description of what this tool does
     * Used for tool selection and LLM context
     */
    val description: String

    /**
     * Whether this tool requires Android permissions
     */
    val requiresPermissions: Boolean
        get() = false

    /**
     * Execute the tool with given parameters
     *
     * @param params Tool-specific parameters
     * @return Result of tool execution
     */
    suspend fun execute(params: Map<String, Any>): ToolResult

    /**
     * Validate parameters before execution (optional override)
     *
     * @return null if valid, error message if invalid
     */
    fun validateParams(params: Map<String, Any>): String? = null

    /**
     * Check if tool is available/ready to execute
     *
     * @return true if tool can be executed, false otherwise
     */
    suspend fun isAvailable(): Boolean = true
}

/**
 * Result of tool execution
 */
sealed class ToolResult {
    /**
     * Tool executed successfully
     *
     * @param data Result data (tool-specific)
     * @param context Additional context for response generation
     */
    data class Success(
        val data: Any,
        val context: Map<String, Any> = emptyMap()
    ) : ToolResult()

    /**
     * Tool execution failed
     *
     * @param error Error that occurred
     * @param reason Human-readable error reason
     * @param recoverable Whether the error can be retried
     */
    data class Failure(
        val error: Throwable,
        val reason: String,
        val recoverable: Boolean = true
    ) : ToolResult()

    /**
     * Tool execution was blocked (safety, permissions, etc.)
     *
     * @param reason Why execution was blocked
     * @param requiredAction What user needs to do (if applicable)
     */
    data class Blocked(
        val reason: String,
        val requiredAction: String? = null
    ) : ToolResult()

    /**
     * Tool is not currently available
     *
     * @param reason Why tool is unavailable
     */
    data class Unavailable(
        val reason: String
    ) : ToolResult()
}

/**
 * Abstract base class for tools with common functionality
 */
abstract class BaseTool : AITool {

    /**
     * Execute tool with parameter validation
     */
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        // Validate parameters
        validateParams(params)?.let { error ->
            return ToolResult.Failure(
                error = IllegalArgumentException(error),
                reason = error,
                recoverable = false
            )
        }

        // Check availability
        if (!isAvailable()) {
            return ToolResult.Unavailable(
                reason = "$name is not currently available"
            )
        }

        // Execute on IO dispatcher
        return withContext(Dispatchers.IO) {
            try {
                executeInternal(params)
            } catch (e: SecurityException) {
                ToolResult.Blocked(
                    reason = "Permission required",
                    requiredAction = "Grant required permissions"
                )
            } catch (e: Exception) {
                ToolResult.Failure(
                    error = e,
                    reason = e.message ?: "Unknown error",
                    recoverable = true
                )
            }
        }
    }

    /**
     * Internal execution logic (implemented by subclasses)
     */
    protected abstract suspend fun executeInternal(params: Map<String, Any>): ToolResult
}

/**
 * Tool registry for managing available tools
 */
class ToolRegistry {
    private val tools = mutableMapOf<String, AITool>()

    /**
     * Register a tool
     */
    fun register(tool: AITool) {
        tools[tool.name] = tool
    }

    /**
     * Unregister a tool
     */
    fun unregister(toolName: String) {
        tools.remove(toolName)
    }

    /**
     * Get tool by name
     */
    fun getTool(name: String): AITool? = tools[name]

    /**
     * Get all available tools
     */
    fun getAllTools(): List<AITool> = tools.values.toList()

    /**
     * Get tool descriptions for LLM context
     */
    fun getToolDescriptions(): String {
        return tools.values.joinToString("\n") { tool ->
            "- ${tool.name}: ${tool.description}"
        }
    }
}
