package com.ailive

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ultra-minimal test - just log to EVERY log level
        Log.wtf("AILIVE_TEST", "========================================")
        Log.e("AILIVE_TEST", "ERROR: MainActivity onCreate STARTED")
        Log.w("AILIVE_TEST", "WARN: MainActivity onCreate STARTED")
        Log.i("AILIVE_TEST", "INFO: MainActivity onCreate STARTED")
        Log.d("AILIVE_TEST", "DEBUG: MainActivity onCreate STARTED")
        Log.v("AILIVE_TEST", "VERBOSE: MainActivity onCreate STARTED")
        System.out.println("AILIVE_TEST: System.out.println test")
        System.err.println("AILIVE_TEST: System.err.println test")
        Log.wtf("AILIVE_TEST", "========================================")
        
        setContentView(R.layout.activity_main)
        
        Log.e("AILIVE_TEST", "UI SET - APP RUNNING")
    }
}
