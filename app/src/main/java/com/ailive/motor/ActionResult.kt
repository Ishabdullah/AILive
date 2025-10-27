package com.ailive.motor

import com.ailive.core.types.AgentType

/**
 * Sealed class for action execution results.
 * Provides type-safe success/failure handling with no exceptions.
 */
sealed class ActionResult<out T> {
    data class Success<T>(
        val data: T,
        val executionTime: Long = System.currentTimeMillis(),
        val metadata: Map<String, Any> = emptyMap()
    ) : ActionResult<T>()
    
    data class Failure(
        val error: ActionError,
        val timestamp: Long = System.currentTimeMillis(),
        val recoverable: Boolean = true,
        val retryAfter: Long? = null
    ) : ActionResult<Nothing>()
    
    data class Throttled(
        val reason: ThrottleReason,
        val retryAfter: Long,
        val currentLoad: Float
    ) : ActionResult<Nothing>()
    
    data class SafetyBlocked(
        val violation: String,
        val rule: String,
        val requiredPermission: String? = null
    ) : ActionResult<Nothing>()
}

sealed class ActionError {
    data class PermissionDenied(val permission: String) : ActionError()
    data class HardwareUnavailable(val device: String) : ActionError()
    data class ResourceExhausted(val resource: String, val current: Float, val limit: Float) : ActionError()
    data class Timeout(val operation: String, val duration: Long) : ActionError()
    data class Unknown(val exception: Throwable) : ActionError()
}

enum class ThrottleReason {
    THERMAL_LIMIT,
    BATTERY_LOW,
    CPU_OVERLOAD,
    MEMORY_PRESSURE,
    NETWORK_UNAVAILABLE
}
