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
        setContentView(R.layout.activity_main)

        Log.i(TAG, "AILive Starting...")

        // Initialize AILive
        ailiveCore = AILiveCore(applicationContext, this)
        ailiveCore.initialize()
        ailiveCore.start()

        // Run tests
        CoroutineScope(Dispatchers.Main).launch {
            delay(1000)
            val tests = TestScenarios(ailiveCore)
            tests.runAllTests()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        ailiveCore.stop()
    }
}
