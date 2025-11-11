package com.ailive.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * AISettings - Stores AI name and wake word configuration
 */
class AISettings(context: Context) {
    private val TAG = "AISettings"
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("ailive_settings", Context.MODE_PRIVATE)
    
    // AI Name
    var aiName: String
        get() = prefs.getString("ai_name", "AILive") ?: "AILive"
        set(value) {
            prefs.edit().putString("ai_name", value).apply()
            Log.i(TAG, "AI name set to: $value")
        }
    
    // Wake phrase (e.g., "Yo, Chris!")
    var wakePhrase: String
        get() = prefs.getString("wake_phrase", "Hey AILive") ?: "Hey AILive"
        set(value) {
            prefs.edit().putString("wake_phrase", value).apply()
            Log.i(TAG, "Wake phrase set to: $value")
        }
    
    // Voice samples paths
    fun getNameSamplePaths(): List<String> {
        val count = prefs.getInt("name_sample_count", 0)
        return (0 until count).map { "name_sample_$it.wav" }
    }
    
    fun addNameSample(index: Int) {
        prefs.edit().putInt("name_sample_count", index + 1).apply()
    }
    
    fun getWakeSamplePaths(): List<String> {
        val count = prefs.getInt("wake_sample_count", 0)
        return (0 until count).map { "wake_sample_$it.wav" }
    }
    
    fun addWakeSample(index: Int) {
        prefs.edit().putInt("wake_sample_count", index + 1).apply()
    }
    
    // Setup completion
    var isSetupComplete: Boolean
        get() = prefs.getBoolean("setup_complete", false)
        set(value) = prefs.edit().putBoolean("setup_complete", value).apply()

    // Streaming Speech - Real-time token-to-speech
    var streamingSpeechEnabled: Boolean
        get() = prefs.getBoolean("streaming_speech_enabled", true)  // Enabled by default
        set(value) {
            prefs.edit().putBoolean("streaming_speech_enabled", value).apply()
            Log.i(TAG, "Streaming speech ${if (value) "enabled" else "disabled"}")
        }

    // Speech buffer delay (seconds) - how long to wait before speaking incomplete sentences
    var speechBufferDelay: Float
        get() = prefs.getFloat("speech_buffer_delay", 0.5f)
        set(value) {
            val clamped = value.coerceIn(0.1f, 2.0f)
            prefs.edit().putFloat("speech_buffer_delay", clamped).apply()
            Log.i(TAG, "Speech buffer delay set to: ${clamped}s")
        }

    // Location Awareness - Provide location context to AI
    var locationAwarenessEnabled: Boolean
        get() = prefs.getBoolean("location_awareness_enabled", false)  // Disabled by default
        set(value) {
            prefs.edit().putBoolean("location_awareness_enabled", value).apply()
            Log.i(TAG, "Location awareness ${if (value) "enabled" else "disabled"}")
        }

    fun clear() {
        prefs.edit().clear().apply()
        Log.i(TAG, "Settings cleared")
    }
}
