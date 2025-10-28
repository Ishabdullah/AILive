package com.ailive

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ailive.ai.models.ModelManager
import com.ailive.camera.CameraManager
import com.ailive.core.AILiveCore
import com.ailive.testing.TestScenarios
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    
    private lateinit var aiLiveCore: AILiveCore
    private lateinit var modelManager: ModelManager
    private lateinit var cameraManager: CameraManager
    
    private lateinit var cameraPreview: PreviewView
    private lateinit var classificationResult: TextView
    private lateinit var confidenceText: TextView
    private lateinit var inferenceTime: TextView
    private lateinit var statusIndicator: TextView
    
    private var callbackCount = 0
    
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "=== AILive Phase 2.2 Starting ===")
        
        setContentView(R.layout.activity_main)
        
        cameraPreview = findViewById(R.id.cameraPreview)
        classificationResult = findViewById(R.id.classificationResult)
        confidenceText = findViewById(R.id.confidenceText)
        inferenceTime = findViewById(R.id.inferenceTime)
        statusIndicator = findViewById(R.id.statusIndicator)
        
        if (allPermissionsGranted()) {
            // Delay init slightly to ensure lifecycle is ready
            cameraPreview.post {
                initializeAILive()
            }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }
    
    private fun initializeAILive() {
        try {
            Log.i(TAG, "=== Initializing AILive Core ===")
            statusIndicator.text = "● INIT CORE..."
            
            aiLiveCore = AILiveCore(applicationContext, this)
            aiLiveCore.initialize()
            aiLiveCore.start()
            
            Log.i(TAG, "✓ Phase 1 complete")
            statusIndicator.text = "● INIT TF LITE..."
            
            modelManager = ModelManager(applicationContext)
            
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    modelManager.initialize()
                    
                    withContext(Dispatchers.Main) {
                        Log.i(TAG, "✓ Phase 2.1 complete")
                        statusIndicator.text = "● INIT CAMERA..."
                        
                        // Small delay before camera
                        delay(500)
                        initializeCamera()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.e(TAG, "TensorFlow init failed", e)
                        statusIndicator.text = "● TF ERROR"
                        classificationResult.text = "Error: ${e.message}"
                    }
                }
            }
            
            CoroutineScope(Dispatchers.Main).launch {
                delay(1000)
                val tests = TestScenarios(aiLiveCore)
                tests.runAllTests()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Init failed", e)
            statusIndicator.text = "● ERROR"
            classificationResult.text = "Error: ${e.message}"
        }
    }
    
    private fun initializeCamera() {
        try {
            Log.i(TAG, "=== Initializing Camera ===")
            
            cameraManager = CameraManager(
                context = applicationContext,
                lifecycleOwner = this,
                modelManager = modelManager
            )
            
            cameraManager.onClassificationResult = { label, confidence, time ->
                callbackCount++
                Log.i(TAG, ">>> CALLBACK #$callbackCount: $label")
                updateUI(label, confidence, time)
            }
            
            cameraManager.startCamera(cameraPreview)
            
            Log.i(TAG, "✓ Camera initialized")
            statusIndicator.text = "● WAITING..."
            classificationResult.text = "Waiting for analyzer..."
            
            // Debug timer
            var seconds = 0
            CoroutineScope(Dispatchers.Main).launch {
                while (true) {
                    delay(1000)
                    seconds++
                    if (callbackCount == 0) {
                        statusIndicator.text = "● ${seconds}s | 0 callbacks"
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Camera init failed", e)
            statusIndicator.text = "● CAM ERROR"
            classificationResult.text = "Camera error: ${e.message}"
        }
    }
    
    private fun updateUI(label: String, confidence: Float, time: Long) {
        runOnUiThread {
            classificationResult.text = label
            confidenceText.text = "Confidence: ${(confidence * 100).toInt()}%"
            inferenceTime.text = "${time}ms | Frame #$callbackCount"
            statusIndicator.text = "● LIVE ($callbackCount)"
            
            val color = when {
                confidence > 0.7f -> getColor(android.R.color.holo_green_light)
                confidence > 0.4f -> getColor(android.R.color.holo_orange_light)
                else -> getColor(android.R.color.holo_red_light)
            }
            classificationResult.setTextColor(color)
        }
    }
    
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                cameraPreview.post {
                    initializeAILive()
                }
            } else {
                finish()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::cameraManager.isInitialized) cameraManager.stopCamera()
        if (::modelManager.isInitialized) modelManager.close()
        if (::aiLiveCore.isInitialized) aiLiveCore.stop()
    }
}
