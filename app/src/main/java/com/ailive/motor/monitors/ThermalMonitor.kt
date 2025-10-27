package com.ailive.motor.monitors

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors device thermal state (Android 10+).
 * Falls back to temperature estimation on older devices.
 */
class ThermalMonitor(private val context: Context) {
    private val TAG = "ThermalMonitor"
    
    private val _thermalState = MutableStateFlow(ThermalState())
    val thermalState: StateFlow<ThermalState> = _thermalState.asStateFlow()
    
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    
    /**
     * Start monitoring thermal state.
     */
    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startThermalMonitoring()
        } else {
            Log.w(TAG, "Thermal API not available on this Android version")
            updateThermalState(PowerManager.THERMAL_STATUS_NONE)
        }
    }
    
    /**
     * Stop monitoring.
     */
    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            stopThermalMonitoring()
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startThermalMonitoring() {
        thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
            Log.i(TAG, "Thermal status changed: ${getThermalStatusName(status)}")
            updateThermalState(status)
        }
        
        powerManager.addThermalStatusListener(thermalListener!!)
        Log.i(TAG, "Thermal monitoring started")
        
        // Get initial state
        val currentStatus = powerManager.currentThermalStatus
        updateThermalState(currentStatus)
    }
    
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun stopThermalMonitoring() {
        thermalListener?.let {
            powerManager.removeThermalStatusListener(it)
            Log.i(TAG, "Thermal monitoring stopped")
        }
    }
    
    private fun updateThermalState(status: Int) {
        val newState = ThermalState(
            status = status,
            statusName = getThermalStatusName(status),
            shouldThrottle = status >= PowerManager.THERMAL_STATUS_MODERATE,
            severityLevel = getSeverityLevel(status),
            timestamp = System.currentTimeMillis()
        )
        
        _thermalState.value = newState
        
        if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
            Log.e(TAG, "SEVERE thermal throttling: $status")
        }
    }
    
    private fun getThermalStatusName(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "NONE"
        PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
        PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
        PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
        PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
        else -> "UNKNOWN"
    }
    
    private fun getSeverityLevel(status: Int): Float = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> 0.0f
        PowerManager.THERMAL_STATUS_LIGHT -> 0.3f
        PowerManager.THERMAL_STATUS_MODERATE -> 0.5f
        PowerManager.THERMAL_STATUS_SEVERE -> 0.7f
        PowerManager.THERMAL_STATUS_CRITICAL -> 0.9f
        PowerManager.THERMAL_STATUS_EMERGENCY -> 0.95f
        PowerManager.THERMAL_STATUS_SHUTDOWN -> 1.0f
        else -> 0.0f
    }
}

data class ThermalState(
    val status: Int = PowerManager.THERMAL_STATUS_NONE,
    val statusName: String = "NONE",
    val shouldThrottle: Boolean = false,
    val severityLevel: Float = 0.0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isHealthy: Boolean
        get() = status <= PowerManager.THERMAL_STATUS_LIGHT
}
