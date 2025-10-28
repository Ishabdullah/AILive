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

/**
 * AILive - Phase 2.2: Camera + Vision Integration
 * Real-time object recognition through device camera
 */
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
    
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "=== AILive Phase 2.2 Starting ===")
        
        setContentView(R.layout.activity_main)
        
        // Initialize UI elements
        cameraPreview = findViewById(R.id.cameraPreview)
        classificationResult = findViewById(R.id.classificationResult)
        confidenceText = findViewById(R.id.confidenceText)
        inferenceTime = findViewById(R.id.inferenceTime)
        statusIndicator = findViewById(R.id.statusIndicator)
        
        // Request camera permission
        if (allPermissionsGranted()) {
            initializeAILive()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }
    
    /**
     * Initialize AI system
     */
    private fun initializeAILive() {
        try {
            Log.i(TAG, "=== Initializing AILive Core ===")
            
            // Phase 1: Core agents
            aiLiveCore = AILiveCore(applicationContext, this)
            aiLiveCore.initialize()
            aiLiveCore.start()
            
            Log.i(TAG, "✓ Phase 1: All agents operational")
            
            // Phase 2: TensorFlow Lite
            modelManager = ModelManager(applicationContext)
            
            CoroutineScope(Dispatchers.Default).launch {
                modelManager.initialize()
                
                withContext(Dispatchers.Main) {
                    Log.i(TAG, "✓ Phase 2.1: TensorFlow Lite ready")
                    statusIndicator.text = "● TF LITE READY"
                    
                    // Phase 2.2: Camera
                    initializeCamera()
                }
            }
            
            // Run Phase 1 tests
            CoroutineScope(Dispatchers.Main).launch {
                delay(1000)
                val tests = TestScenarios(aiLiveCore)
                tests.runAllTests()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AILive", e)
            statusIndicator.text = "● ERROR"
            statusIndicator.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }
    
    /**
     * Initialize camera and start classification
     */
    private fun initializeCamera() {
        try {
            Log.i(TAG, "=== Initializing Camera ===")
            
            cameraManager = CameraManager(
                context = applicationContext,
                lifecycleOwner = this,
                modelManager = modelManager
            )
            
            // Set classification callback
            cameraManager.onClassificationResult = { label, confidence, time ->
                updateUI(label, confidence, time)
            }
            
            // Start camera
            cameraManager.startCamera(cameraPreview)
            
            Log.i(TAG, "✓ Phase 2.2: Camera operational")
            statusIndicator.text = "● LIVE"
            classificationResult.text = "Point camera at objects"
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize camera", e)
            statusIndicator.text = "● CAMERA ERROR"
        }
    }
    
    /**
     * Update UI with classification results
     */
    private fun updateUI(label: String, confidence: Float, time: Long) {
        runOnUiThread {
            classificationResult.text = label
            confidenceText.text = "Confidence: ${(confidence * 100).toInt()}%"
            inferenceTime.text = "Inference: ${time}ms"
            
            // Color code by confidence
            val color = when {
                confidence > 0.7f -> getColor(android.R.color.holo_green_light)
                confidence > 0.4f -> getColor(android.R.color.holo_orange_light)
                else -> getColor(android.R.color.holo_red_light)
            }
            classificationResult.setTextColor(color)
        }
    }
    
    /**
     * Check camera permission
     */
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
                initializeAILive()
            } else {
                Log.e(TAG, "Camera permission denied")
                finish()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::cameraManager.isInitialized) {
            cameraManager.stopCamera()
        }
        if (::modelManager.isInitialized) {
            modelManager.close()
        }
        if (::aiLiveCore.isInitialized) {
            aiLiveCore.stop()
        }
    }
}
