package com.ailive.testing

import android.util.Log
import com.ailive.AILiveCore
import kotlinx.coroutines.*

/**
 * Termux-compatible runner for AILive.
 * NOTE: Android-specific features (Camera, Battery, etc) won't work in termux.
 * This is for testing the core logic (messaging, planning, memory).
 */
object TermuxRunner {
    private val TAG = "TermuxRunner"
    
    /**
     * Simulated Android context for termux testing.
     * In production, use real Android Context.
     */
    class MockContext : android.content.Context() {
        override fun getFilesDir(): java.io.File {
            return java.io.File("/data/data/com.termux/files/home/AILive/storage")
        }
        
        // Stub implementations for required Context methods
        override fun getApplicationContext() = this
        override fun getPackageName() = "com.ailive"
        override fun getResources() = null
        override fun getAssets() = null
        override fun getContentResolver() = null
        override fun getMainLooper() = null
        override fun getApplicationInfo() = null
        override fun getTheme() = null
        override fun getClassLoader() = this.javaClass.classLoader
        override fun getPackageManager() = null
        override fun getSystemService(name: String) = null
        override fun getString(resId: Int) = ""
        override fun checkPermission(permission: String, pid: Int, uid: Int) = 0
    }
    
    /**
     * Simulated FragmentActivity for termux testing.
     */
    class MockActivity : androidx.fragment.app.FragmentActivity() {
        override fun shouldShowRequestPermissionRationale(permission: String) = false
    }
    
    /**
     * Run AILive in termux mode.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        println("
╔═══════════════════════════════════════╗")
        println("║      AILive Termux Test Runner       ║")
        println("╚═══════════════════════════════════════╝
")
        
        try {
            // Create mock Android context
            val context = MockContext()
            val activity = MockActivity()
            
            // Initialize AILive
            val ailive = AILiveCore(context, activity)
            ailive.initialize()
            ailive.start()
            
            // Run tests
            runBlocking {
                val tests = TestScenarios(ailive)
                tests.runAllTests()
                
                // Keep running for a bit to observe
                delay(2000)
            }
            
            // Shutdown
            ailive.stop()
            
            println("
✓ AILive terminated successfully")
            
        } catch (e: Exception) {
            println("
✗ Error: ${e.message}")
            e.printStackTrace()
        }
    }
}
