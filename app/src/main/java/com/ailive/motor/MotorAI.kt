package com.ailive.motor

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.ailive.core.messaging.*
import com.ailive.core.state.StateManager
import com.ailive.core.types.AgentType
import com.ailive.motor.actuators.CameraController
import com.ailive.motor.monitors.BatteryMonitor
import com.ailive.motor.monitors.ThermalMonitor
import com.ailive.motor.permissions.PermissionManager
import com.ailive.motor.safety.SafetyPolicy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/**
 * Motor AI - AILive's interface to device hardware and sensors.
 * Handles execution, safety policies, and resource monitoring.
 */
class MotorAI(
    private val context: Context,
    activity: FragmentActivity,
    private val messageBus: MessageBus,
    private val stateManager: StateManager
) {
    private val TAG = "MotorAI"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Components
    val permissionManager = PermissionManager(activity)
    private val batteryMonitor = BatteryMonitor(context)
    private val thermalMonitor = ThermalMonitor(context)
    val cameraController = CameraController(context, permissionManager)
    
    // State
    private val actionsInFlight = mutableMapOf<String, Job>()
    private var isRunning = false
    
    /**
     * Start Motor AI and all monitors.
     */
    fun start() {
        if (isRunning) return
        
        isRunning = true
        Log.i(TAG, "Motor AI starting...")
        
        // Start monitors
        batteryMonitor.start()
        thermalMonitor.start()
        
        // Subscribe to action requests
        scope.launch {
            messageBus.subscribe(AIMessage.Control.ActionRequest::class.java)
                .collect { request ->
                    handleActionRequest(request)
                }
        }
        
        // Monitor system health
        scope.launch {
            monitorSystemHealth()
        }
        
        // Publish startup
        scope.launch {
            messageBus.publish(
                AIMessage.System.AgentStarted(source = AgentType.MOTOR_AI)
            )
        }
        
        Log.i(TAG, "Motor AI started")
    }
    
    /**
     * Stop Motor AI gracefully.
     */
    fun stop() {
        if (!isRunning) return
        
        Log.i(TAG, "Motor AI stopping...")
        
        batteryMonitor.stop()
        thermalMonitor.stop()
        
        // Cancel all in-flight actions
        actionsInFlight.values.forEach { it.cancel() }
        actionsInFlight.clear()
        
        scope.cancel()
        isRunning = false
        
        Log.i(TAG, "Motor AI stopped")
    }
    
    /**
     * Handle incoming action requests from other agents.
     */
    private suspend fun handleActionRequest(request: AIMessage.Control.ActionRequest) {
        Log.d(TAG, "Action request: ${request.actionType} from ${request.source}")
        
        // Safety validation
        val validation = validateActionSafety(request)
        when (validation) {
            is SafetyPolicy.ValidationResult.Denied -> {
                publishSafetyViolation(request, validation.reason)
                rejectAction(request, validation.reason)
                return
            }
            is SafetyPolicy.ValidationResult.Throttled -> {
                publishThrottled(request, validation.reason)
                return
            }
            SafetyPolicy.ValidationResult.PermissionRequired -> {
                publishPermissionRequired(request)
                return
            }
            SafetyPolicy.ValidationResult.Allowed -> {
                // Proceed
            }
        }
        
        // Execute action
        val job = scope.launch {
            val result = executeAction(request)
            publishActionResult(request, result)
        }
        
        actionsInFlight[request.id] = job
        job.invokeOnCompletion {
            actionsInFlight.remove(request.id)
        }
    }
    
    /**
     * Validate action against safety policies.
     */
    private fun validateActionSafety(request: AIMessage.Control.ActionRequest): SafetyPolicy.ValidationResult {
        val battery = batteryMonitor.batteryState.value
        val thermal = thermalMonitor.thermalState.value
        
        // Check permissions
        val requiredPerms = SafetyPolicy.PERMISSION_REQUIREMENTS[request.actionType] ?: emptyList()
        val hasPermissions = permissionManager.arePermissionsGranted(context, requiredPerms)
        
        return SafetyPolicy.validateAction(
            actionType = request.actionType.name,
            hasPermissions = hasPermissions,
            batteryPercent = battery.percent,
            thermalStatus = thermal.status,
            cpuLoad = 50f // TODO: Implement CPU monitoring
        )
    }
    
    /**
     * Execute the actual action.
     */
    private suspend fun executeAction(request: AIMessage.Control.ActionRequest): ActionResult<Any> {
        return when (request.actionType) {
            ActionType.CAMERA_CAPTURE -> {
                val cameraId = request.params["camera_id"] as? String ?: "0"
                cameraController.captureImage(cameraId)
            }
            ActionType.SEND_NOTIFICATION -> {
                // TODO: Implement notification
                ActionResult.Success("Notification sent")
            }
            ActionType.STORE_DATA -> {
                // TODO: Implement data storage
                ActionResult.Success("Data stored")
            }
            else -> {
                ActionResult.Failure(
                    error = ActionError.Unknown(Exception("Unknown action type"))
                )
            }
        }
    }
    
    /**
     * Monitor system health and publish updates.
     */
    private suspend fun monitorSystemHealth() {
        while (isRunning) {
            val battery = batteryMonitor.batteryState.value
            val thermal = thermalMonitor.thermalState.value
            
            // Update state
            stateManager.updateMotor { motor ->
                motor.copy(
                    actionsInFlight = motor.actionsInFlight.take(10) // Keep last 10
                )
            }
            
            // Publish sensor updates
            messageBus.publish(
                AIMessage.Motor.SensorUpdate(
                    sensorType = SensorType.BATTERY,
                    value = battery.percent
                )
            )
            
            delay(5000) // Check every 5 seconds
        }
    }
    
    private suspend fun publishActionResult(
        request: AIMessage.Control.ActionRequest,
        result: ActionResult<Any>
    ) {
        when (result) {
            is ActionResult.Success<*> -> {
                messageBus.publish(
                    AIMessage.Motor.ActionExecuted(
                        actionId = request.id,
                        success = true,
                        feedback = result.metadata + ("data" to result.data.toString())
                    )
                )
            }
            is ActionResult.Failure -> {
                messageBus.publish(
                    AIMessage.Motor.ActionExecuted(
                        actionId = request.id,
                        success = false,
                        feedback = mapOf("error" to result.error.toString())
                    )
                )
            }
            is ActionResult.SafetyBlocked -> {
                publishSafetyViolation(request, result.violation)
            }
            is ActionResult.Throttled -> {
                publishThrottled(request, result.reason.name)
            }
        }
    }
    
    private suspend fun publishSafetyViolation(request: AIMessage.Control.ActionRequest, reason: String) {
        messageBus.publish(
            AIMessage.System.SafetyViolation(
                violationType = "ACTION_BLOCKED",
                attemptedAction = request.actionType.name,
                violationType = reason
            )
        )
    }
    
    private suspend fun publishPermissionRequired(request: AIMessage.Control.ActionRequest) {
        messageBus.publish(
            AIMessage.System.ErrorOccurred(
                source = AgentType.MOTOR_AI,
                error = SecurityException("Permission required"),
                context = "Action: ${request.actionType}"
            )
        )
    }
    
    private suspend fun publishThrottled(request: AIMessage.Control.ActionRequest, reason: String) {
        Log.w(TAG, "Action throttled: ${request.actionType} - $reason")
    }
    
    private suspend fun rejectAction(request: AIMessage.Control.ActionRequest, reason: String) {
        messageBus.publish(
            AIMessage.Control.ActionRejected(
                requestId = request.id,
                reason = reason
            )
        )
    }
}
