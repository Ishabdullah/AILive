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
    private var isInitialized = false
    
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "=== AILive onCreate ===")
        
        setContentView(R.layout.activity_main)
        
        // Initialize UI
        cameraPreview = findViewById(R.id.cameraPreview)
        classificationResult = findViewById(R.id.classificationResult)
        confidenceText = findViewById(R.id.confidenceText)
        inferenceTime = findViewById(R.id.inferenceTime)
        statusIndicator = findViewById(R.id.statusIndicator)
        
        // Show permission status
        statusIndicator.text = "● CHECKING PERMISSIONS..."
        classificationResult.text = "Initializing..."
        
        // Check permissions FIRST before anything else
        if (allPermissionsGranted()) {
            Log.i(TAG, "✓ Permissions already granted")
            startAILive()
        } else {
            Log.i(TAG, "Requesting camera permission...")
            statusIndicator.text = "● REQUESTING PERMISSION..."
            classificationResult.text = "Please allow camera access"
            
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }
    
    /**
     * Handle permission result
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "✓ Permission granted by user")
                startAILive()
            } else {
                Log.e(TAG, "✗ Permission denied")
                statusIndicator.text = "● PERMISSION DENIED"
                classificationResult.text = "Camera permission required"
                finish()
            }
        }
    }
    
    /**
     * Start AILive ONLY after permission is granted
     */
    private fun startAILive() {
        if (isInitialized) {
            Log.w(TAG, "Already initialized, skipping")
            return
        }
        
        isInitialized = true
        
        try {
            Log.i(TAG, "=== Starting AILive ===")
            statusIndicator.text = "● INITIALIZING..."
            classificationResult.text = "Starting AI agents..."
            
            // Phase 1: Core agents
            aiLiveCore = AILiveCore(applicationContext, this)
            aiLiveCore.initialize()
            aiLiveCore.start()
            
            Log.i(TAG, "✓ Phase 1: Agents started")
            statusIndicator.text = "● LOADING AI MODEL..."
            
            // Phase 2.1: TensorFlow Lite
            modelManager = ModelManager(applicationContext)
            
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    modelManager.initialize()
                    
                    withContext(Dispatchers.Main) {
                        Log.i(TAG, "✓ Phase 2.1: TensorFlow ready")
                        statusIndicator.text = "● STARTING CAMERA..."
                        classificationResult.text = "Initializing camera..."
                        
                        // Phase 2.2: Camera (with delay to ensure lifecycle ready)
                        delay(500)
                        initializeCamera()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.e(TAG, "TensorFlow init failed", e)
                        statusIndicator.text = "● AI MODEL ERROR"
                        classificationResult.text = "Error: ${e.message}"
                    }
                }
            }
            
            // Run tests
            CoroutineScope(Dispatchers.Main).launch {
                delay(1000)
                val tests = TestScenarios(aiLiveCore)
                tests.runAllTests()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Init failed", e)
            statusIndicator.text = "● INIT ERROR"
            classificationResult.text = "Error: ${e.message}"
        }
    }
    
    /**
     * Initialize camera - called AFTER permission granted
     */
    private fun initializeCamera() {
        try {
            Log.i(TAG, "=== Starting Camera ===")
            
            cameraManager = CameraManager(
                context = applicationContext,
                lifecycleOwner = this,
                modelManager = modelManager
            )
            
            cameraManager.onClassificationResult = { label, confidence, time ->
                callbackCount++
                Log.i(TAG, ">>> Classification #$callbackCount: $label")
                updateUI(label, confidence, time)
            }
            
            cameraManager.startCamera(cameraPreview)
            
            Log.i(TAG, "✓ Camera started")
            statusIndicator.text = "● ANALYZING..."
            classificationResult.text = "Point at objects"
            
            // Debug counter
            var seconds = 0
            CoroutineScope(Dispatchers.Main).launch {
                while (true) {
                    delay(1000)
                    seconds++
                    if (callbackCount == 0) {
                        statusIndicator.text = "● WAITING ${seconds}s (0 results)"
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Camera failed", e)
            statusIndicator.text = "● CAMERA ERROR"
            classificationResult.text = "Camera error: ${e.message}"
        }
    }
    
    /**
     * Update UI with classification
     */
    private fun updateUI(label: String, confidence: Float, time: Long) {
        runOnUiThread {
            classificationResult.text = label
            confidenceText.text = "Confidence: ${(confidence * 100).toInt()}%"
            inferenceTime.text = "${time}ms | Frame #$callbackCount"
            statusIndicator.text = "● LIVE ($callbackCount frames)"
            
            val color = when {
                confidence > 0.7f -> getColor(android.R.color.holo_green_light)
                confidence > 0.4f -> getColor(android.R.color.holo_orange_light)
                else -> getColor(android.R.color.holo_red_light)
            }
            classificationResult.setTextColor(color)
        }
    }
    
    /**
     * Check if permissions granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::cameraManager.isInitialized) cameraManager.stopCamera()
        if (::modelManager.isInitialized) modelManager.close()
        if (::aiLiveCore.isInitialized) aiLiveCore.stop()
    }
}
