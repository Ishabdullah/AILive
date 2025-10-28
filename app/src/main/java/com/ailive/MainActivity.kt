package com.ailive

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.ailive.core.AILiveCore
import com.ailive.testing.TestScenarios
import kotlinx.coroutines.*

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
        
        Log.i(TAG, "=== Step 3: Creating AILiveCore instance ===")
        
        try {
            aiLiveCore = AILiveCore(applicationContext, this)
            Log.i(TAG, "=== Step 4: AILiveCore instance created ===")
            
            aiLiveCore.initialize()
            Log.i(TAG, "=== Step 5: AILiveCore initialized ===")
            
            aiLiveCore.start()
            Log.i(TAG, "=== Step 6: AILiveCore started ===")
            
            // Run Phase 1 tests
            CoroutineScope(Dispatchers.Main).launch {
                Log.i(TAG, "=== Step 7: Starting test scenarios ===")
                delay(1000)
                val tests = TestScenarios(aiLiveCore)
                tests.runAllTests()
                Log.i(TAG, "=== Step 8: Test scenarios complete ===")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "=== CRASH: ${e.message} ===", e)
            e.printStackTrace()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "=== onDestroy called ===")
        if (::aiLiveCore.isInitialized) {
            aiLiveCore.stop()
        }
    }
}
