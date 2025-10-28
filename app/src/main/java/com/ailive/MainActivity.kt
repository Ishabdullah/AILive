package com.ailive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ailive.ai.models.ModelManager
import com.ailive.audio.AudioManager
import com.ailive.audio.CommandRouter
import com.ailive.audio.SpeechProcessor
import com.ailive.audio.WakeWordDetector
import com.ailive.camera.CameraManager
import com.ailive.core.AILiveCore
import com.ailive.settings.AISettings
import com.ailive.testing.TestScenarios
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    
    private lateinit var settings: AISettings
    private lateinit var aiLiveCore: AILiveCore
    private lateinit var modelManager: ModelManager
    private lateinit var cameraManager: CameraManager

    // Phase 2.3: Audio components
    private lateinit var audioManager: AudioManager
    private lateinit var speechProcessor: SpeechProcessor
    private lateinit var wakeWordDetector: WakeWordDetector
    private lateinit var commandRouter: CommandRouter

    private lateinit var cameraPreview: PreviewView
    private lateinit var appTitle: TextView
    private lateinit var classificationResult: TextView
    private lateinit var confidenceText: TextView
    private lateinit var inferenceTime: TextView
    private lateinit var statusIndicator: TextView
    private lateinit var audioStatus: TextView
    private lateinit var transcriptionText: TextView

    private var callbackCount = 0
    private var isInitialized = false
    private var isListeningForWakeWord = false

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize settings (setup wizard temporarily disabled)
        settings = AISettings(this)
        // TODO: Re-enable setup wizard after fixing resource issues
        // Bypass setup for now - use default AI name
        if (!settings.isSetupComplete) {
            Log.i(TAG, "Setup bypassed - using defaults")
            settings.isSetupComplete = true  // Mark as complete to prevent redirect
        }

        Log.i(TAG, "=== ${settings.aiName} Starting ===")
        
        setContentView(R.layout.activity_main)
        
        // Initialize UI
        cameraPreview = findViewById(R.id.cameraPreview)
        appTitle = findViewById(R.id.appTitle)
        classificationResult = findViewById(R.id.classificationResult)
        confidenceText = findViewById(R.id.confidenceText)
        inferenceTime = findViewById(R.id.inferenceTime)
        statusIndicator = findViewById(R.id.statusIndicator)
        audioStatus = findViewById(R.id.audioStatus)
        transcriptionText = findViewById(R.id.transcriptionText)

        // Use custom AI name in UI
        appTitle.text = "${settings.aiName} (Vision + Audio)"

        statusIndicator.text = "● INITIALIZING..."
        classificationResult.text = "Initializing ${settings.aiName}..."

        // Initialize AILiveCore EARLY (before permissions) to avoid lifecycle issues
        try {
            Log.i(TAG, "=== Initializing ${settings.aiName} Core ===")
            aiLiveCore = AILiveCore(applicationContext, this)
            aiLiveCore.initialize()
            aiLiveCore.start()
            Log.i(TAG, "✓ Phase 1: Agents operational")
        } catch (e: Exception) {
            Log.e(TAG, "AILive Core init failed", e)
            statusIndicator.text = "● CORE ERROR"
            classificationResult.text = "Error: ${e.message}"
            return
        }

        // Now check permissions
        statusIndicator.text = "● CHECKING PERMISSIONS..."

        if (allPermissionsGranted()) {
            Log.i(TAG, "✓ Permissions granted")
            startModels()
        } else {
            Log.i(TAG, "Requesting permissions...")
            statusIndicator.text = "● REQUESTING PERMISSIONS..."
            classificationResult.text = "Please allow camera and microphone access"

            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.i(TAG, "✓ All permissions granted")
                startModels()
            } else {
                Log.e(TAG, "✗ Permissions denied")
                statusIndicator.text = "● PERMISSION DENIED"
                classificationResult.text = "Camera and microphone permissions required"
                finish()
            }
        }
    }

    private fun startModels() {
        if (isInitialized) return
        isInitialized = true

        try {
            statusIndicator.text = "● LOADING AI MODEL..."
            classificationResult.text = "Loading TensorFlow Lite..."

            // Phase 2.1: TensorFlow Lite
            modelManager = ModelManager(applicationContext)
            
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    modelManager.initialize()
                    
                    withContext(Dispatchers.Main) {
                        Log.i(TAG, "✓ Phase 2.1: TensorFlow ready")
                        statusIndicator.text = "● STARTING CAMERA..."
                        classificationResult.text = "Initializing camera..."
                        
                        delay(500)
                        initializeCamera()

                        // Phase 2.3: Initialize audio after camera
                        delay(500)
                        initializeAudio()
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
     * Phase 2.3: Initialize audio pipeline
     */
    private fun initializeAudio() {
        try {
            Log.i(TAG, "=== Initializing Audio Pipeline ===")
            audioStatus.text = "🎤 Initializing..."

            // Create audio components
            speechProcessor = SpeechProcessor(applicationContext)
            wakeWordDetector = WakeWordDetector(settings.wakePhrase)
            commandRouter = CommandRouter(aiLiveCore)

            // Initialize speech processor
            if (!speechProcessor.initialize()) {
                Log.e(TAG, "❌ Speech processor failed to initialize")
                audioStatus.text = "🎤 Not available"
                return
            }

            Log.i(TAG, "✓ Speech processor ready")

            // Configure wake word detector
            wakeWordDetector.onWakeWordDetected = {
                runOnUiThread {
                    onWakeWordDetected()
                }
            }

            // Configure speech processor callbacks
            speechProcessor.onPartialResult = { text ->
                runOnUiThread {
                    transcriptionText.text = text
                    // Check for wake word in partial results
                    if (isListeningForWakeWord) {
                        wakeWordDetector.processText(text)
                    }
                }
            }

            speechProcessor.onFinalResult = { text ->
                runOnUiThread {
                    transcriptionText.text = text
                    Log.i(TAG, "Final transcription: '$text'")

                    if (isListeningForWakeWord) {
                        // Check for wake word
                        if (wakeWordDetector.processText(text)) {
                            // Wake word detected - handled by detector callback
                        } else {
                            // Not wake word, restart listening
                            restartWakeWordListening()
                        }
                    } else {
                        // Process as command
                        processVoiceCommand(text)
                    }
                }
            }

            speechProcessor.onReadyForSpeech = {
                runOnUiThread {
                    if (isListeningForWakeWord) {
                        audioStatus.text = "🎤 Say \"${settings.wakePhrase}\""
                    } else {
                        audioStatus.text = "🎤 Listening for command..."
                    }
                }
            }

            speechProcessor.onError = { error ->
                runOnUiThread {
                    Log.w(TAG, "Speech error: $error")
                    // Auto-retry on timeout or no match
                    if (isListeningForWakeWord) {
                        restartWakeWordListening()
                    } else {
                        isListeningForWakeWord = true
                        restartWakeWordListening()
                    }
                }
            }

            // Configure command router
            commandRouter.onResponse = { response ->
                runOnUiThread {
                    classificationResult.text = response
                    confidenceText.text = "Voice Command Response"

                    // Return to wake word listening after response
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(3000)
                        isListeningForWakeWord = true
                        restartWakeWordListening()
                    }
                }
            }

            // Start listening for wake word
            isListeningForWakeWord = true
            startWakeWordListening()

            Log.i(TAG, "✓ Phase 2.3: Audio pipeline operational")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Audio init failed", e)
            audioStatus.text = "🎤 Error: ${e.message}"
        }
    }

    /**
     * Start listening for wake word
     */
    private fun startWakeWordListening() {
        audioStatus.text = "🎤 Say \"${settings.wakePhrase}\""
        transcriptionText.text = ""
        speechProcessor.startListening(continuous = false)
        Log.i(TAG, "Listening for wake word...")
    }

    /**
     * Restart wake word listening after timeout
     */
    private fun restartWakeWordListening() {
        CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            if (isListeningForWakeWord) {
                startWakeWordListening()
            }
        }
    }

    /**
     * Handle wake word detection
     */
    private fun onWakeWordDetected() {
        Log.i(TAG, "🎯 Wake word detected!")
        isListeningForWakeWord = false

        audioStatus.text = "🎤 Activated! Listening..."
        transcriptionText.text = ""
        statusIndicator.text = "● VOICE ACTIVE"

        // Beep or vibrate (optional)
        // vibrator.vibrate(100)

        // Start listening for command
        speechProcessor.stopListening()
        CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            speechProcessor.startListening(continuous = false)
        }
    }

    /**
     * Process voice command
     */
    private fun processVoiceCommand(command: String) {
        Log.i(TAG, "Processing command: '$command'")
        audioStatus.text = "🎤 Processing..."

        CoroutineScope(Dispatchers.Default).launch {
            commandRouter.processCommand(command)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::speechProcessor.isInitialized) speechProcessor.release()
        if (::cameraManager.isInitialized) cameraManager.stopCamera()
        if (::modelManager.isInitialized) modelManager.close()
        if (::aiLiveCore.isInitialized) aiLiveCore.stop()
    }
}
