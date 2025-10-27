package com.ailive.motor.monitors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors device battery state for resource management decisions.
 */
class BatteryMonitor(private val context: Context) {
    private val TAG = "BatteryMonitor"
    
    private val _batteryState = MutableStateFlow(BatteryState())
    val batteryState: StateFlow<BatteryState> = _batteryState.asStateFlow()
    
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateBatteryState(intent)
        }
    }
    
    private var isRegistered = false
    
    /**
     * Start monitoring battery state.
     */
    fun start() {
        if (!isRegistered) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(batteryReceiver, filter)
            isRegistered = true
            Log.i(TAG, "Battery monitoring started")
            
            // Get initial state
            val intent = context.registerReceiver(null, filter)
            intent?.let { updateBatteryState(it) }
        }
    }
    
    /**
     * Stop monitoring.
     */
    fun stop() {
        if (isRegistered) {
            context.unregisterReceiver(batteryReceiver)
            isRegistered = false
            Log.i(TAG, "Battery monitoring stopped")
        }
    }
    
    /**
     * Get current battery percentage.
     */
    fun getCurrentBatteryPercent(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    
    private fun updateBatteryState(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (scale > 0) (level * 100) / scale else -1
        
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
        
        val plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val chargingSource = when (plugType) {
            BatteryManager.BATTERY_PLUGGED_AC -> ChargingSource.AC
            BatteryManager.BATTERY_PLUGGED_USB -> ChargingSource.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> ChargingSource.WIRELESS
            else -> ChargingSource.NOT_CHARGING
        }
        
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0f
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        
        val newState = BatteryState(
            percent = percent,
            isCharging = isCharging,
            chargingSource = chargingSource,
            temperature = temperature,
            voltage = voltage,
            timestamp = System.currentTimeMillis()
        )
        
        _batteryState.value = newState
        
        if (percent < 20) {
            Log.w(TAG, "Battery low: $percent%")
        }
        if (temperature > 40f) {
            Log.w(TAG, "Battery temperature high: ${temperature}°C")
        }
    }
}

data class BatteryState(
    val percent: Int = 100,
    val isCharging: Boolean = false,
    val chargingSource: ChargingSource = ChargingSource.NOT_CHARGING,
    val temperature: Float = 25f,
    val voltage: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isHealthy: Boolean
        get() = percent > 15 && temperature < 45f
    
    val shouldThrottle: Boolean
        get() = percent < 20 && !isCharging
}

enum class ChargingSource {
    NOT_CHARGING,
    AC,
    USB,
    WIRELESS
}
