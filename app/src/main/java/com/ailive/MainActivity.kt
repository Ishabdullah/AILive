package com.ailive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
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
import com.ailive.ui.dashboard.DashboardFragment
import com.ailive.ui.ModelSetupDialog
import com.ailive.ai.llm.ModelDownloadManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
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

    // Manual control UI components
    private lateinit var btnToggleMic: android.widget.Button
    private lateinit var btnToggleCamera: android.widget.Button
    private lateinit var editTextCommand: android.widget.EditText
    private lateinit var btnSendCommand: android.widget.Button
    private lateinit var btnToggleDashboard: FloatingActionButton
    private lateinit var dashboardContainer: FrameLayout

    // Dashboard
    private var dashboardFragment: DashboardFragment? = null
    private var isDashboardVisible = false

    // Model Setup Dialog (Phase 7.3)
    private lateinit var modelSetupDialog: ModelSetupDialog
    private lateinit var modelDownloadManager: ModelDownloadManager

    // Phase 7.5: Modern ActivityResultLauncher for file picker (replaces deprecated startActivityForResult)
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private var filePickerOnComplete: (() -> Unit)? = null

    private var callbackCount = 0
    private var isInitialized = false
    private var isListeningForWakeWord = false
    private var isMicEnabled = false
    private var isCameraEnabled = false

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Phase 7.5: CRITICAL - Register ActivityResultLauncher BEFORE any other lifecycle operations
        // This must be done before setContentView() or the activity enters RESUMED state
        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    Log.i(TAG, "File picker selected: $uri")
                    val onComplete = filePickerOnComplete
                    if (onComplete != null) {
                        modelSetupDialog.handleFilePickerResult(uri, onComplete)
                        filePickerOnComplete = null
                    }
                }
            }
        }

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

        // Initialize manual control buttons
        btnToggleMic = findViewById(R.id.btnToggleMic)
        btnToggleCamera = findViewById(R.id.btnToggleCamera)
        editTextCommand = findViewById(R.id.editTextCommand)
        btnSendCommand = findViewById(R.id.btnSendCommand)
        btnToggleDashboard = findViewById(R.id.btnToggleDashboard)
        dashboardContainer = findViewById(R.id.dashboardContainer)

        // Set up button click listeners
        setupManualControls()
        setupDashboard()

        // Use custom AI name in UI
        appTitle.text = "${settings.aiName} (Vision + Audio)"

        statusIndicator.text = "● INITIALIZING..."
        classificationResult.text = "Initializing ${settings.aiName}..."

        // Phase 7.3/7.5: Initialize model download system
        modelDownloadManager = ModelDownloadManager(this)
        modelSetupDialog = ModelSetupDialog(this, modelDownloadManager, filePickerLauncher)

        // Phase 7.7: Storage permission not needed on Android 10+ (API 29+)
        // DownloadManager can write to public Downloads folder without WRITE_EXTERNAL_STORAGE
        // This permission is deprecated on modern Android versions
        Log.i(TAG, "Storage permission not required - using DownloadManager for downloads")

        // Check if model setup is needed and show dialog
        if (modelSetupDialog.isSetupNeeded()) {
            Log.i(TAG, "Model setup needed - showing dialog")
            statusIndicator.text = "● MODEL SETUP REQUIRED"
            classificationResult.text = "Please download or import an AI model"
            filePickerOnComplete = {
                Log.i(TAG, "Model setup complete, continuing initialization")
                continueInitialization()
            }
            modelSetupDialog.showFirstRunDialog {
                Log.i(TAG, "Model setup complete, continuing initialization")
                continueInitialization()
            }
            // Don't proceed with initialization until model is ready
            return
        }

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

    /**
     * Continue initialization after model setup is complete
     * This runs the same code that was in onCreate() after model check
     */
    private fun continueInitialization() {
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

            // PHASE 5: Register VisionAnalysisTool now that camera is ready
            val visionTool = com.ailive.personality.tools.VisionAnalysisTool(
                modelManager = modelManager,
                cameraManager = cameraManager
            )
            aiLiveCore.personalityEngine.registerTool(visionTool)
            Log.i(TAG, "✓ VisionAnalysisTool registered")

            // PHASE 5 Part 3: PatternAnalysisTool and FeedbackTrackingTool registered in AILiveCore

            Log.i(TAG, "✓ Camera started")
            statusIndicator.text = "● ANALYZING..."
            classificationResult.text = "Point at objects"

            // Update camera button state
            isCameraEnabled = true
            btnToggleCamera.text = "📷 CAM"
            btnToggleCamera.backgroundTintList = getColorStateList(android.R.color.holo_green_dark)
            
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

            // Create audio components
            speechProcessor = SpeechProcessor(applicationContext)
            wakeWordDetector = WakeWordDetector(settings.wakePhrase, aiLiveCore.ttsManager)
            commandRouter = CommandRouter(aiLiveCore)

            // Initialize speech processor
            if (!speechProcessor.initialize()) {
                Log.e(TAG, "❌ Speech processor failed to initialize")
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
                    // Show partial transcription in text field
                    editTextCommand.setText(text)
                    // Check for wake word in partial results
                    if (isListeningForWakeWord) {
                        wakeWordDetector.processText(text)
                    }
                }
            }

            speechProcessor.onFinalResult = { text ->
                runOnUiThread {
                    // Show final transcription in text field
                    editTextCommand.setText(text)
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
                        statusIndicator.text = "● LISTENING"
                    } else {
                        statusIndicator.text = "● COMMAND"
                    }
                }
            }

            speechProcessor.onError = { error ->
                runOnUiThread {
                    Log.w(TAG, "Speech error: $error")
                    // Auto-retry on timeout or no match if mic is enabled
                    if (isMicEnabled && isListeningForWakeWord) {
                        restartWakeWordListening()
                    } else if (isMicEnabled && !isListeningForWakeWord) {
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

                    // Return to wake word listening AFTER TTS finishes speaking (only if mic is still enabled)
                    CoroutineScope(Dispatchers.Main).launch {
                        // Wait for TTS to actually finish speaking (not just 3 seconds!)
                        aiLiveCore.ttsManager.state.collect { ttsState ->
                            if (ttsState == com.ailive.audio.TTSManager.TTSState.READY) {
                                // TTS is done, safe to restart listening (but only if mic is enabled)
                                delay(500) // Small buffer to release audio resources
                                if (isMicEnabled) {
                                    isListeningForWakeWord = true
                                    restartWakeWordListening()
                                }
                                return@collect // Exit collection after restarting
                            }
                        }
                    }
                }
            }

            // Start listening for wake word
            isListeningForWakeWord = true
            isMicEnabled = true
            startWakeWordListening()

            // Update mic button state
            runOnUiThread {
                btnToggleMic.text = "🎤 MIC"
                btnToggleMic.backgroundTintList = getColorStateList(android.R.color.holo_green_dark)
            }

            // Monitor TTS status
            CoroutineScope(Dispatchers.Main).launch {
                aiLiveCore.ttsManager.state.collect { ttsState ->
                    when (ttsState) {
                        com.ailive.audio.TTSManager.TTSState.SPEAKING -> {
                            if (!isListeningForWakeWord) {
                                statusIndicator.text = "● SPEAKING"
                            }
                        }
                        else -> {
                            // Don't update if we're already showing another status
                        }
                    }
                }
            }

            Log.i(TAG, "✓ Phase 2.3: Audio pipeline operational")
            Log.i(TAG, "✓ Phase 2.4: TTS ready")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Audio init failed", e)
            statusIndicator.text = "● ERROR"
        }
    }

    /**
     * Start listening for wake word
     */
    private fun startWakeWordListening() {
        editTextCommand.setText("")
        editTextCommand.hint = "Say \"${settings.wakePhrase}\" or type..."
        speechProcessor.startListening(continuous = false)
        Log.i(TAG, "Listening for wake word...")
    }

    /**
     * Restart wake word listening after timeout (only if mic is enabled)
     */
    private fun restartWakeWordListening() {
        CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            if (isListeningForWakeWord && isMicEnabled) {
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

        editTextCommand.setText("")
        editTextCommand.hint = "Listening for command..."
        statusIndicator.text = "● COMMAND"

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
        statusIndicator.text = "● PROCESSING"

        CoroutineScope(Dispatchers.Default).launch {
            commandRouter.processCommand(command)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Set up manual control buttons for testing and debugging
     */
    private fun setupManualControls() {
        // Microphone toggle button
        btnToggleMic.setOnClickListener {
            if (isMicEnabled) {
                // Turn off microphone - FIXED: actually stop listening
                if (::speechProcessor.isInitialized) {
                    speechProcessor.stopListening()
                    isListeningForWakeWord = false
                }
                isMicEnabled = false
                btnToggleMic.text = "🎤 MIC"
                btnToggleMic.backgroundTintList = getColorStateList(android.R.color.holo_red_dark)
                statusIndicator.text = "● MIC OFF"
                editTextCommand.hint = "Type command..."
                Log.i(TAG, "🎤 Microphone manually disabled")
            } else {
                // Turn on microphone
                if (::speechProcessor.isInitialized) {
                    isListeningForWakeWord = true
                    isMicEnabled = true
                    startWakeWordListening()
                } else {
                    isMicEnabled = true
                }
                btnToggleMic.text = "🎤 MIC"
                btnToggleMic.backgroundTintList = getColorStateList(android.R.color.holo_green_dark)
                Log.i(TAG, "🎤 Microphone manually enabled")
            }
        }

        // Camera toggle button
        btnToggleCamera.setOnClickListener {
            if (isCameraEnabled) {
                // Turn off camera
                if (::cameraManager.isInitialized) {
                    cameraManager.stopCamera()
                }
                // Hide the preview to show black screen (FIXED: visibility instead of background)
                cameraPreview.visibility = android.view.View.INVISIBLE
                cameraPreview.setBackgroundColor(android.graphics.Color.BLACK)
                isCameraEnabled = false
                btnToggleCamera.text = "📷 CAM"
                btnToggleCamera.backgroundTintList = getColorStateList(android.R.color.holo_red_dark)
                statusIndicator.text = "● CAM OFF"
                classificationResult.text = "Camera off"
                confidenceText.text = ""
                inferenceTime.text = ""
                Log.i(TAG, "📷 Camera manually disabled")
            } else {
                // Turn on camera
                // Show the preview to display camera feed (FIXED: visibility instead of background)
                cameraPreview.visibility = android.view.View.VISIBLE
                cameraPreview.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                if (::cameraManager.isInitialized) {
                    cameraManager.startCamera(cameraPreview)
                }
                isCameraEnabled = true
                btnToggleCamera.text = "📷 CAM"
                btnToggleCamera.backgroundTintList = getColorStateList(android.R.color.holo_green_dark)
                statusIndicator.text = "● ANALYZING"
                classificationResult.text = "Point at objects"
                Log.i(TAG, "📷 Camera manually enabled")
            }
        }

        // Send command button
        btnSendCommand.setOnClickListener {
            val command = editTextCommand.text.toString().trim()
            if (command.isNotEmpty()) {
                processTextCommand(command)
                editTextCommand.setText("")  // FIXED: Clear text field after sending
            }
        }

        // Handle enter key in text field
        editTextCommand.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                val command = editTextCommand.text.toString().trim()
                if (command.isNotEmpty()) {
                    processTextCommand(command)
                    editTextCommand.setText("")  // FIXED: Clear text field after sending
                }
                true
            } else {
                false
            }
        }
    }

    /**
     * Process text command (bypassing voice recognition)
     */
    private fun processTextCommand(command: String) {
        Log.i(TAG, "📝 Processing text command: '$command'")

        statusIndicator.text = "● PROCESSING"

        // Process through command router
        CoroutineScope(Dispatchers.Default).launch {
            commandRouter.processCommand(command)
        }
    }

    /**
     * Set up dashboard toggle
     */
    private fun setupDashboard() {
        btnToggleDashboard.setOnClickListener {
            toggleDashboard()
        }
    }

    /**
     * Toggle dashboard visibility
     */
    private fun toggleDashboard() {
        isDashboardVisible = !isDashboardVisible

        if (isDashboardVisible) {
            // Show dashboard
            dashboardContainer.visibility = View.VISIBLE

            // Create dashboard fragment if not exists
            if (dashboardFragment == null) {
                dashboardFragment = DashboardFragment().apply {
                    aiLiveCore = this@MainActivity.aiLiveCore
                }

                supportFragmentManager.beginTransaction()
                    .replace(R.id.dashboardContainer, dashboardFragment!!)
                    .commit()
            }

            Log.i(TAG, "📊 Dashboard opened")
        } else {
            // Hide dashboard
            dashboardContainer.visibility = View.GONE
            Log.i(TAG, "📊 Dashboard closed")
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        if (::speechProcessor.isInitialized) speechProcessor.release()
        if (::cameraManager.isInitialized) cameraManager.stopCamera()
        if (::modelManager.isInitialized) modelManager.close()
        if (::aiLiveCore.isInitialized) aiLiveCore.stop()
        if (::modelSetupDialog.isInitialized) modelSetupDialog.cleanup()
    }
}
