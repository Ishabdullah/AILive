package com.ailive

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.ailive.testing.TestScenarios
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private lateinit var ailiveCore: AILiveCore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "=== Step 1: onCreate started ===")
        
        setContentView(R.layout.activity_main)
        Log.i(TAG, "=== Step 2: setContentView complete ===")

        Log.i(TAG, "=== Step 3: Creating AILiveCore instance ===")
        try {
            ailiveCore = AILiveCore(applicationContext, this)
            Log.i(TAG, "=== Step 4: AILiveCore instance created ===")
            
            ailiveCore.initialize()
            Log.i(TAG, "=== Step 5: AILiveCore initialized ===")
            
            ailiveCore.start()
            Log.i(TAG, "=== Step 6: AILiveCore started ===")

            // Run tests
            CoroutineScope(Dispatchers.Main).launch {
                Log.i(TAG, "=== Step 7: Starting test scenarios ===")
                delay(1000)
                val tests = TestScenarios(ailiveCore)
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
        if (::ailiveCore.isInitialized) {
            ailiveCore.stop()
        }
    }
}
