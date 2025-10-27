package com.ailive.motor.safety

import android.Manifest
import com.ailive.core.messaging.ActionType

/**
 * Immutable safety policies for AILive.
 * These rules CANNOT be overridden by any agent, including Meta AI.
 */
object SafetyPolicy {
    
    /**
     * Actions that require explicit user consent every time.
     */
    val CONSENT_REQUIRED_ACTIONS = setOf(
        ActionType.CAMERA_CAPTURE,
        ActionType.SEND_NOTIFICATION,
        ActionType.QUERY_WEB
    )
    
    /**
     * Actions that are completely forbidden.
     */
    val FORBIDDEN_ACTIONS = setOf(
        "DELETE_USER_FILES",
        "MODIFY_SYSTEM_SETTINGS",
        "INSTALL_APK",
        "ROOT_ACCESS",
        "CALL_PHONE_WITHOUT_CONSENT"
    )
    
    /**
     * Maximum resource limits (soft caps - trigger throttling).
     */
    object ResourceLimits {
        const val MAX_CPU_PERCENT = 80
        const val MAX_MEMORY_MB = 4000
        const val MIN_BATTERY_PERCENT = 15
        const val MAX_THERMAL_STATUS = 3 // LIGHT throttling
        const val MAX_CONCURRENT_ACTIONS = 5
    }
    
    /**
     * Required permissions for each action type.
     */
    val PERMISSION_REQUIREMENTS = mapOf(
        ActionType.CAMERA_CAPTURE to listOf(Manifest.permission.CAMERA),
        ActionType.PLAY_AUDIO to listOf(Manifest.permission.RECORD_AUDIO),
        ActionType.STORE_DATA to listOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    )
    
    /**
     * Validate if an action is allowed under current conditions.
     */
    fun validateAction(
        actionType: String,
        hasPermissions: Boolean,
        batteryPercent: Int,
        thermalStatus: Int,
        cpuLoad: Float
    ): ValidationResult {
        // Check forbidden actions
        if (actionType in FORBIDDEN_ACTIONS) {
            return ValidationResult.Denied("Action is forbidden by safety policy")
        }
        
        // Check battery level
        if (batteryPercent < ResourceLimits.MIN_BATTERY_PERCENT) {
            return ValidationResult.Throttled("Battery too low: $batteryPercent%")
        }
        
        // Check thermal status
        if (thermalStatus > ResourceLimits.MAX_THERMAL_STATUS) {
            return ValidationResult.Throttled("Device thermal status too high: $thermalStatus")
        }
        
        // Check CPU load
        if (cpuLoad > ResourceLimits.MAX_CPU_PERCENT) {
            return ValidationResult.Throttled("CPU load too high: ${cpuLoad.toInt()}%")
        }
        
        // Check permissions
        if (!hasPermissions) {
            return ValidationResult.PermissionRequired
        }
        
        return ValidationResult.Allowed
    }
    
    sealed class ValidationResult {
        object Allowed : ValidationResult()
        object PermissionRequired : ValidationResult()
        data class Denied(val reason: String) : ValidationResult()
        data class Throttled(val reason: String) : ValidationResult()
    }
}
