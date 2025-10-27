package com.ailive.testing

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.ailive.AILiveCore
import kotlinx.coroutines.*
import java.io.File

object TermuxRunner {
    private val TAG = "TermuxRunner"
    
    class MockContext : Context() {
        override fun getFilesDir(): File {
            return File("/data/data/com.termux/files/home/AILive/storage")
        }
        
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
        override fun setTheme(resId: Int) {}
        override fun getString(resId: Int) = ""
        override fun checkPermission(permission: String, pid: Int, uid: Int) = 0
    }
    
    class MockActivity : FragmentActivity() {
        override fun shouldShowRequestPermissionRationale(permission: String) = false
    }
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("AILive Termux Test Runner")
        
        try {
            val context = MockContext()
            val activity = MockActivity()
            
            val ailive = AILiveCore(context, activity)
            ailive.initialize()
            ailive.start()
            
            runBlocking {
                val tests = TestScenarios(ailive)
                tests.runAllTests()
                delay(2000)
            }
            
            ailive.stop()
            
            println("AILive terminated successfully")
            
        } catch (e: Exception) {
            println("Error: ${e.message}")
            e.printStackTrace()
        }
    }
}
