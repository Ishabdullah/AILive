package com.ailive

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.ailive.core.AILiveCore

/**
 * AILive - Multi-Agent AI System
 * Phase 1.1: Complete agent system ✓
 * Phase 2.1: TensorFlow Lite integration ✓
 */
class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private lateinit var aiLiveCore: AILiveCore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "=== Step 1: onCreate started ===")
        
        setContentView(R.layout.activity_main)
        Log.i(TAG, "=== Step 2: setContentView complete ===")
        
        try {
            Log.i(TAG, "=== Step 3: Creating AILiveCore instance ===")
            aiLiveCore = AILiveCore(applicationContext, this)
            Log.i(TAG, "=== Step 4: AILiveCore instance created ===")
            
            Log.i(TAG, "=== Step 5: Initializing AILiveCore ===")
            aiLiveCore.initialize()
            Log.i(TAG, "=== Step 6: AILiveCore initialized ===")
            
            Log.i(TAG, "=== Step 7: Starting AILiveCore ===")
            aiLiveCore.start()
            Log.i(TAG, "=== Step 8: AILiveCore started successfully ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "ERROR: Failed to initialize AILive", e)
            e.printStackTrace()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::aiLiveCore.isInitialized) {
            aiLiveCore.stop()
        }
    }
}
