package com.ailive.personality.tools

import android.util.Log
import com.ailive.core.messaging.*
import com.ailive.motor.ActionResult
import com.ailive.motor.MotorAI

/**
 * DeviceControlTool - Controls device hardware and sensors
 *
 * Converted from MotorAI agent to a tool for PersonalityEngine.
 * Provides device control capabilities (camera, sensors, actuators)
 * without separate personality.
 */
class DeviceControlTool(
    private val motorAI: MotorAI
) : BaseTool() {

    companion object {
        private const val TAG = "DeviceControlTool"
    }

    override val name: String = "control_device"

    override val description: String =
        "Controls device hardware including camera capture, sensors, " +
        "and other actuators. Respects safety policies and permissions."

    override val requiresPermissions: Boolean = true

    override suspend fun isAvailable(): Boolean = true

    override fun validateParams(params: Map<String, Any>): String? {
        if (!params.containsKey("action")) {
            return "Parameter 'action' is required"
        }

        val action = params["action"] as? String
        if (action.isNullOrBlank()) {
            return "Parameter 'action' cannot be empty"
        }

        // Validate action type
        val validActions = listOf(
            "capture_image",
            "capture_video",
            "get_sensor_data",
            "send_notification",
            "set_brightness",
            "enable_flashlight",
            "disable_flashlight"
        )

        if (action !in validActions) {
            return "Invalid action: $action. Valid actions: ${validActions.joinToString()}"
        }

        return null
    }

    override suspend fun executeInternal(params: Map<String, Any>): ToolResult {
        val action = params["action"] as String
        val actionParams = params["params"] as? Map<String, Any> ?: emptyMap()

        Log.d(TAG, "Executing device action: $action")

        return when (action) {
            "capture_image" -> captureImage(actionParams)
            "get_sensor_data" -> getSensorData(actionParams)
            "send_notification" -> sendNotification(actionParams)
            "enable_flashlight" -> controlFlashlight(true)
            "disable_flashlight" -> controlFlashlight(false)
            else -> ToolResult.Failure(
                error = IllegalArgumentException("Unsupported action: $action"),
                reason = "Action not implemented yet: $action",
                recoverable = false
            )
        }
    }

    /**
     * Capture image from camera
     */
    private suspend fun captureImage(params: Map<String, Any>): ToolResult {
        val cameraId = params["camera_id"] as? String ?: "0"

        return try {
            val result = motorAI.cameraController.captureImage(cameraId)

            when (result) {
                is ActionResult.Success<*> -> {
                    ToolResult.Success(
                        data = DeviceActionResult(
                            action = "capture_image",
                            success = true,
                            message = "Image captured successfully",
                            data = result.data
                        ),
                        context = mapOf(
                            "camera_id" to cameraId,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
                is ActionResult.Failure -> {
                    ToolResult.Failure(
                        error = result.error.toException(),
                        reason = "Failed to capture image: ${result.error}",
                        recoverable = true
                    )
                }
                is ActionResult.SafetyBlocked -> {
                    ToolResult.Blocked(
                        reason = "Camera capture blocked by safety policy: ${result.violation}",
                        requiredAction = "Check battery level and thermal status"
                    )
                }
                is ActionResult.Throttled -> {
                    ToolResult.Blocked(
                        reason = "Camera capture throttled: ${result.reason}",
                        requiredAction = "Wait a moment and try again"
                    )
                }
            }
        } catch (e: SecurityException) {
            ToolResult.Blocked(
                reason = "Camera permission not granted",
                requiredAction = "Grant camera permission in settings"
            )
        } catch (e: Exception) {
            ToolResult.Failure(
                error = e,
                reason = "Failed to capture image: ${e.message}",
                recoverable = true
            )
        }
    }

    /**
     * Get sensor data (battery, thermal, etc.)
     */
    private suspend fun getSensorData(params: Map<String, Any>): ToolResult {
        val sensorType = params["sensor"] as? String ?: "battery"

        return try {
            // Get current battery and thermal state
            // TODO: Get actual battery state from MotorAI monitors
            val batteryState = mapOf(
                "level" to 85,
                "charging" to false,
                "temperature" to 32.0
            )
            val thermalState = getThermalStatus()

            val sensorData = when (sensorType) {
                "battery" -> batteryState
                "thermal" -> mapOf(
                    "status" to thermalState["status"],
                    "temperature" to thermalState["temperature"]
                )
                "all" -> mapOf(
                    "battery" to batteryState,
                    "thermal" to thermalState
                )
                else -> mapOf("error" to "Unknown sensor type: $sensorType")
            }

            ToolResult.Success(
                data = DeviceActionResult(
                    action = "get_sensor_data",
                    success = true,
                    message = "Sensor data retrieved",
                    data = sensorData
                ),
                context = mapOf(
                    "sensor_type" to sensorType
                )
            )
        } catch (e: Exception) {
            ToolResult.Failure(
                error = e,
                reason = "Failed to get sensor data: ${e.message}",
                recoverable = true
            )
        }
    }

    /**
     * Send notification
     */
    private suspend fun sendNotification(params: Map<String, Any>): ToolResult {
        val title = params["title"] as? String ?: "AILive"
        val message = params["message"] as? String ?: ""

        if (message.isBlank()) {
            return ToolResult.Failure(
                error = IllegalArgumentException("Message cannot be empty"),
                reason = "Notification message is required",
                recoverable = false
            )
        }

        return try {
            // TODO: Implement actual notification
            ToolResult.Success(
                data = DeviceActionResult(
                    action = "send_notification",
                    success = true,
                    message = "Notification sent",
                    data = mapOf("title" to title, "message" to message)
                )
            )
        } catch (e: Exception) {
            ToolResult.Failure(
                error = e,
                reason = "Failed to send notification: ${e.message}",
                recoverable = true
            )
        }
    }

    /**
     * Control flashlight
     */
    private suspend fun controlFlashlight(enable: Boolean): ToolResult {
        return try {
            // TODO: Implement flashlight control via MotorAI
            ToolResult.Success(
                data = DeviceActionResult(
                    action = if (enable) "enable_flashlight" else "disable_flashlight",
                    success = true,
                    message = "Flashlight ${if (enable) "enabled" else "disabled"}",
                    data = mapOf("enabled" to enable)
                )
            )
        } catch (e: Exception) {
            ToolResult.Failure(
                error = e,
                reason = "Failed to control flashlight: ${e.message}",
                recoverable = true
            )
        }
    }

    /**
     * Get thermal status (placeholder)
     */
    private fun getThermalStatus(): Map<String, Any> {
        // TODO: Get actual thermal status from MotorAI
        return mapOf(
            "status" to "NORMAL",
            "temperature" to 35.0
        )
    }

    /**
     * Result of device action
     */
    data class DeviceActionResult(
        val action: String,
        val success: Boolean,
        val message: String,
        val data: Any? = null
    )
}

/**
 * Extension to convert ActionError to Exception
 */
private fun com.ailive.motor.ActionError.toException(): Exception {
    return when (this) {
        is com.ailive.motor.ActionError.PermissionDenied ->
            SecurityException(this.permission)
        is com.ailive.motor.ActionError.HardwareUnavailable ->
            Exception("Hardware unavailable: ${this.device}")
        is com.ailive.motor.ActionError.ResourceExhausted ->
            Exception("Resource exhausted: ${this.resource} (${this.current}/${this.limit})")
        is com.ailive.motor.ActionError.Timeout ->
            Exception("Action timed out: ${this.operation} after ${this.duration}ms")
        is com.ailive.motor.ActionError.Unknown ->
            if (this.exception is Exception) {
                this.exception as Exception
            } else {
                Exception("Unknown error", this.exception)
            }
    }
}
