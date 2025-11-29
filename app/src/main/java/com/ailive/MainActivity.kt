package com.ailive

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import java.io.File
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ailive.ai.models.ModelManager
import com.ailive.audio.AudioManager
import com.ailive.audio.CommandRouter
import com.ailive.audio.WhisperProcessor
import com.ailive.audio.WakeWordDetector
import com.ailive.camera.CameraManager
import com.ailive.core.AILiveCore
import com.ailive.settings.AISettings
import com.ailive.testing.TestScenarios
import com.ailive.ui.dashboard.DashboardFragment
import com.ailive.ui.ModelSetupDialog
import com.ailive.ai.llm.ModelDownloadManager
import com.ailive.ai.vision.VisionManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    private lateinit var settings: AISettings
    private lateinit var aiLiveCore: AILiveCore
    private lateinit var modelManager: ModelManager
    private lateinit var cameraManager: CameraManager
    private lateinit var visionManager: VisionManager

    // Audio components
    private lateinit var audioManager: AudioManager
    private lateinit var whisperProcessor: WhisperProcessor
    private lateinit var wakeWordDetector: WakeWordDetector
    private lateinit var commandRouter: CommandRouter

    // UI
    private lateinit var cameraPreview: PreviewView
    private lateinit var appIconBackground: android.widget.ImageView
    private lateinit var appTitle: TextView
    private lateinit var classificationResult: TextView
    private lateinit var responseScrollView: android.widget.ScrollView
    private lateinit var confidenceText: TextView
    private lateinit var inferenceTime: TextView
    private lateinit var statusIndicator: TextView
    private lateinit var typingIndicator: TextView
    private lateinit var imageViewCaptured: ImageView // New: for captured image preview

    // Manual controls
    private lateinit var btnToggleMic: android.widget.Button
    private lateinit var btnToggleCamera: android.widget.Button
    private lateinit var editTextCommand: android.widget.EditText
    private lateinit var btnSendCommand: android.widget.Button
    private lateinit var btnCancelGeneration: android.widget.Button
    private lateinit var btnSettings: android.widget.Button
    private lateinit var btnMemory: android.widget.Button
    private lateinit var btnCaptureImage: FloatingActionButton // New: for image capture
    private lateinit var btnToggleDashboard: FloatingActionButton
    private lateinit var dashboardContainer: FrameLayout

    // Dashboard
    private var dashboardFragment: DashboardFragment? = null
    private var isDashboardVisible = false

    // Model setup
    private lateinit var modelSetupDialog: ModelSetupDialog
    private lateinit var modelDownloadManager: ModelDownloadManager

    // File picker
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private var filePickerOnComplete: (() -> Unit)? = null

    // Image capture
    private lateinit var imageCaptureLauncher: ActivityResultLauncher<Intent> // New: for image capture
    private var capturedImageBitmap: Bitmap? = null // New: to store captured image

    // Permissions launcher
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    // Settings launcher (to receive download requests)
    private lateinit var settingsLauncher: ActivityResultLauncher<Intent>

    private var callbackCount = 0
    private var isInitialized = false
    private var isListeningForWakeWord = false
    private var isMicEnabled = false
    private var isCameraEnabled = false

    // LLM generation control
    private var generationJob: Job? = null

    companion object {
        private const val TAG_COMPANION = "MainActivity.Companion"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register file picker BEFORE UI operations (you had this right; keep it)
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

        // Register settings launcher to receive download requests
        settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra("download_model")?.let { modelType ->
                    Log.i(TAG, "📥 Download request from settings: $modelType")
                    // Trigger download via ModelSetupDialog public API
                    when (modelType) {
                        "BGE" -> {
                            Log.i(TAG, "BGE model is built-in - no download needed")
                            Toast.makeText(this, "BGE Embedding Model is built-in and ready!", Toast.LENGTH_SHORT).show()
                        }
                        "Memory" -> modelSetupDialog.triggerMemoryDownload()
                        "Qwen" -> modelSetupDialog.triggerQwenDownload()
                        "All" -> modelSetupDialog.triggerAllModelsDownload()
                    }
                }
            }
        }

        // Register permissions launcher - single modern path
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            // results: map of permission -> Boolean
            val allGranted = results.values.all { it }
            if (allGranted) {
                Log.i(TAG, "✓ All requested permissions granted (launcher)")
                // After permissions granted, check if model setup is needed
                proceedAfterPermissions()
            } else {
                Log.e(TAG, "✗ One or more permissions denied: $results")
                runOnUiThread {
                    statusIndicator?.text = "● PERMISSION DENIED"
                    classificationResult?.text = "Permissions required for full functionality"

                    // Show explanation dialog with option to open settings
                    AlertDialog.Builder(this)
                        .setTitle("Permissions Required")
                        .setMessage("Permissions required for full functionality")
                        .setPositiveButton("Open Settings") { _, _ ->
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                            }
                            startActivity(intent)
                            finish()
                        }
                        .setNegativeButton("Exit") { _, _ ->
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                }
            }
        }

        // Register image capture launcher
        imageCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val imageBitmap = result.data?.extras?.get("data") as? Bitmap
                if (imageBitmap != null) {
                    capturedImageBitmap = imageBitmap
                    imageViewCaptured.setImageBitmap(imageBitmap)
                    imageViewCaptured.visibility = View.VISIBLE
                    Log.i(TAG, "✅ Image captured and displayed.")
                } else {
                    Log.e(TAG, "❌ Failed to get bitmap from camera intent.")
                }
            } else {
                Log.i(TAG, "Image capture cancelled or failed.")
            }
        }

        // Initialize settings
        settings = AISettings(this)
        if (!settings.isSetupComplete) {
            Log.i(TAG, "Setup bypassed - using defaults")
            settings.isSetupComplete = true
        }

        Log.i(TAG, "=== ${settings.aiName} Starting ===")

        setContentView(R.layout.activity_main)

        // Init UI references (ensure activity_main contains these IDs)
        cameraPreview = findViewById(R.id.cameraPreview)
        appIconBackground = findViewById(R.id.appIconBackground)
        appTitle = findViewById(R.id.appTitle)
        classificationResult = findViewById(R.id.classificationResult)
        responseScrollView = findViewById(R.id.responseScrollView)
        confidenceText = findViewById(R.id.confidenceText)
        inferenceTime = findViewById(R.id.inferenceTime)
        statusIndicator = findViewById(R.id.statusIndicator)
        typingIndicator = findViewById(R.id.typingIndicator)
        imageViewCaptured = findViewById(R.id.imageViewCaptured) // New: for captured image preview

        btnToggleMic = findViewById(R.id.btnToggleMic)
        btnToggleCamera = findViewById(R.id.btnToggleCamera)
        editTextCommand = findViewById(R.id.editTextCommand)
        btnSendCommand = findViewById(R.id.btnSendCommand)
        btnCancelGeneration = findViewById(R.id.btnCancelGeneration)
        btnSettings = findViewById(R.id.btnSettings)
        btnMemory = findViewById(R.id.btnMemory)
        btnCaptureImage = findViewById(R.id.btnCaptureImage) // New: for image capture
        btnToggleDashboard = findViewById(R.id.btnToggleDashboard)
        dashboardContainer = findViewById(R.id.dashboardContainer)

        setupManualControls()
        setupDashboard()
        setupSettingsButton()
        setupMemoryButton()

        appTitle.text = "${settings.aiName} (Vision + Audio)"

        statusIndicator.text = "● INITIALIZING..."
        classificationResult.text = "Initializing ${settings.aiName}..."

        // Model downloader and dialog
        modelDownloadManager = ModelDownloadManager(this)
        modelSetupDialog = ModelSetupDialog(this, modelDownloadManager, filePickerLauncher)

        // Request permissions FIRST before showing model setup dialog
        requestInitialPermissions()
    }

    /**
     * Build list of permissions based on Android version
     */
    private fun buildPermissionList(): List<String> {
        val permissionsToRequest = mutableListOf<String>()
        permissionsToRequest.add(Manifest.permission.CAMERA)
        permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)

        // Location permissions for GPS/Location Awareness (v1.2)
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // Storage permissions:
        // We store models in PUBLIC Downloads folder (Environment.DIRECTORY_DOWNLOADS)
        // Android 13+ uses granular READ_MEDIA_* permissions
        // Android 10-12 uses READ_EXTERNAL_STORAGE
        // Android 9- uses WRITE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - need granular media permissions to read model files
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-12 - need READ_EXTERNAL_STORAGE for Downloads access
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            // Android 9 and below - need both READ and WRITE
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        return permissionsToRequest
    }

    /**
     * Request all necessary permissions before proceeding with initialization
     */
    private fun requestInitialPermissions() {
        val permissionsToRequest = buildPermissionList()

        // Check current permission status
        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            Log.i(TAG, "✓ Permissions already granted")
            proceedAfterPermissions()
        } else {
            Log.i(TAG, "Requesting permissions: $missing")
            statusIndicator.text = "● REQUESTING PERMISSIONS..."
            classificationResult.text = "Please allow camera, microphone, location, and storage access"
            // Launch modern permission flow
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    /**
     * Called after permissions are granted. Check if model setup is needed.
     */
    private fun proceedAfterPermissions() {
        // If model setup required, show dialog and defer init
        if (modelSetupDialog.isSetupNeeded()) {
            Log.i(TAG, "Model setup needed - showing dialog")
            statusIndicator.text = "● SETUP REQUIRED"
            classificationResult.text = "Let's set up your AI assistant!"

            filePickerOnComplete = {
                Log.i(TAG, "Model setup complete, continuing initialization")
                continueInitialization()
            }

            // Show AI name customization first, then model setup
            modelSetupDialog.showNameSetupDialog {
                Log.i(TAG, "Setup complete, continuing initialization")
                continueInitialization()
            }
            // stop further initialization until user completes model setup
            return
        }

        // If we get here, either models already present or modelSetupDialog not required.
        continueInitialization()
    }

    private fun continueInitialization() {
        // Verify at least one model is available before continuing
        if (!modelDownloadManager.isModelAvailable(modelName = null)) {
            Log.e(TAG, "❌ No models available after setup dialog!")
            runOnUiThread {
                statusIndicator.text = "● MODEL MISSING"
                classificationResult.text = "No models found. Please download or import a model and restart the app."
            }
            return
        }

        // Log which models are available
        val models = modelDownloadManager.getAvailableModelsInDownloads()
        Log.i(TAG, "✅ Found ${models.size} model(s) available:")
        models.forEach { model ->
            Log.i(TAG, "   - ${model.name} (${model.length() / 1024 / 1024}MB)")
        }

        // Initialize core early (keeps your previous design)
        try {
            Log.i(TAG, "=== Initializing ${settings.aiName} Core ===")
            aiLiveCore = AILiveCore(applicationContext, this)
            aiLiveCore.initialize()
            aiLiveCore.start()
            Log.i(TAG, "✓ Phase 1: Agents operational")
        } catch (e: Exception) {
            Log.e(TAG, "AILive Core init failed", e)
            runOnUiThread {
                statusIndicator.text = "● CORE ERROR"
                classificationResult.text = "Error: ${e.message}"
            }
            return
        }

        // Permissions are now requested earlier in requestInitialPermissions()
        // Proceed directly to starting models
        Log.i(TAG, "✓ Permissions verified, starting models")
        startModels()
    }

    private fun startModels() {
        if (isInitialized) return
        isInitialized = true

        try {
            runOnUiThread {
                statusIndicator.text = "● LOADING AI MODEL..."
                classificationResult.text = "Initializing camera..." // Changed from TensorFlow Lite
            }

            modelManager = ModelManager(applicationContext)

            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    // modelManager.initialize() // Removed: Deprecated

                    withContext(Dispatchers.Main) {
                        Log.i(TAG, "✓ Phase 2.1: Vision (Camera) ready") // Updated log
                        statusIndicator.text = "● STARTING CAMERA..."
                        classificationResult.text = "Initializing camera..."

                        delay(500)
                        initializeCamera()

                        delay(500)
                        initializeAudio()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.e(TAG, "Vision/Audio init failed", e) // Updated log
                        statusIndicator.text = "● AI MODEL ERROR"
                        classificationResult.text = "Error: ${e.message}"
                    }
                }
            }

            // Run tests on main (non-blocking)
            lifecycleScope.launch {
                delay(1000)
                val tests = TestScenarios(aiLiveCore)
                tests.runAllTests()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Init failed", e)
            runOnUiThread {
                statusIndicator.text = "● INIT ERROR"
                classificationResult.text = "Error: ${e.message}"
            }
        }
    }

    private fun initializeCamera() {
        try {
            Log.i(TAG, "=== Starting Camera ===")
            cameraManager = CameraManager(
                context = applicationContext,
                lifecycleOwner = this
            )

            // Removed onClassificationResult as CameraManager is now a pure frame provider
            // Vision processing will be triggered manually or by VisionManager

            cameraManager.startCamera(cameraPreview)

            // Initialize VisionManager
            visionManager = VisionManager(aiLiveCore.hybridModelManager.llmBridge)
            Log.i(TAG, "✓ VisionManager initialized")

            val visionTool = com.ailive.personality.tools.VisionAnalysisTool(
                visionManager = visionManager, // Pass the new VisionManager
                cameraManager = cameraManager
            )
            aiLiveCore.personalityEngine.registerTool(visionTool)
            Log.i(TAG, "✓ VisionAnalysisTool registered")

            runOnUiThread {
                statusIndicator.text = "●"
                classificationResult.text = "Ready for input..."

                // Camera starts OFF by default - user must enable manually
                isCameraEnabled = false
                btnToggleCamera.text = "📷 CAM OFF"
                btnToggleCamera.setBackgroundResource(R.drawable.button_toggle_off)
                cameraPreview.visibility = View.GONE
                appIconBackground.visibility = View.VISIBLE
            }

            // Debug counter coroutine - use lifecycleScope so it's cancelled with Activity
            lifecycleScope.launch {
                var seconds = 0
                while (isActive) {
                    delay(1000)
                    seconds++
                    if (callbackCount == 0) {
                        statusIndicator.text = "● WAITING ${seconds}s (0 results)"
                    }
                }
            }

            Log.i(TAG, "✓ Camera started")
        } catch (e: Exception) {
            Log.e(TAG, "Camera failed", e)
            runOnUiThread {
                statusIndicator.text = "● CAMERA ERROR"
                classificationResult.text = "Camera error: ${e.message}"
            }
        }
    }

    private fun updateUI(label: String, confidence: Float, time: Long) {
        runOnUiThread {
            classificationResult.text = label
            confidenceText.text = "Confidence: ${(confidence * 100).toInt()}%"
            inferenceTime.text = "${time}ms | Frame #$callbackCount"
            statusIndicator.text = "● LIVE ($callbackCount frames)"

            val colorRes = when {
                confidence > 0.7f -> android.R.color.holo_green_light
                confidence > 0.4f -> android.R.color.holo_orange_light
                else -> android.R.color.holo_red_light
            }
            classificationResult.setTextColor(ContextCompat.getColor(this, colorRes))
        }
    }

    private fun initializeAudio() {
        try {
            Log.i(TAG, "=== Initializing Offline Audio Pipeline ===")
            Log.i(TAG, "⏱️  Sequential initialization: Whisper → LLM → TTS")

            // PHASE 1: Initialize WhisperProcessor FIRST
            Log.i(TAG, "📍 PHASE 1: Initializing Whisper ASR...")
            whisperProcessor = WhisperProcessor(applicationContext)
            
            // Initialize Whisper in background (may take 5-10 seconds)
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val whisperSuccess = whisperProcessor.initialize()
                    
                    withContext(Dispatchers.Main) {
                        if (!whisperSuccess) {
                            Log.e(TAG, "❌ Whisper processor failed to initialize")
                            statusIndicator.text = "⚠️ ASR UNAVAILABLE"
                            classificationResult.text = "Whisper model not ready. Voice input disabled."
                            return@withContext
                        }
                        
                        Log.i(TAG, "✅ PHASE 1 COMPLETE: Whisper processor ready")
                        
                        // PHASE 2: Wait for LLM to be ready (initialized in AILiveCore)
                        Log.i(TAG, "📍 PHASE 2: Waiting for LLM initialization...")
                        waitForLLMReady()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Whisper initialization exception", e)
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        statusIndicator.text = "⚠️ ASR ERROR"
                        classificationResult.text = "Whisper initialization failed: ${e.message}"
                    }
                }
            }
            
            // Continue with other audio component setup (non-blocking)
            setupAudioComponents()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Audio init failed", e)
            e.printStackTrace()
            runOnUiThread { 
                statusIndicator.text = "⚠️ ERROR"
                classificationResult.text = "Audio initialization failed: ${e.message}"
            }
        }
    }
    
    /**
     * Wait for LLM to be ready before enabling voice commands
     * This prevents crashes from sending null/empty text to uninitialized LLM
     */
    private fun waitForLLMReady() {
        lifecycleScope.launch(Dispatchers.IO) {
            var attempts = 0
            val maxAttempts = 60 // Wait up to 60 seconds
            
            while (attempts < maxAttempts) {
                if (aiLiveCore.hybridModelManager.isReady) {
                    Log.i(TAG, "✅ PHASE 2 COMPLETE: LLM is ready")
                    
                    withContext(Dispatchers.Main) {
                        // PHASE 3: TTS is already initialized in AILiveCore
                        Log.i(TAG, "✅ PHASE 3 COMPLETE: TTS ready")
                        Log.i(TAG, "🎉 ALL PHASES COMPLETE: Audio pipeline fully operational")
                        
                        // Enable UI controls now that all models are ready
                        enableAudioControls()
                    }
                    return@launch
                }
                
                delay(1000) // Check every second
                attempts++
                
                if (attempts % 5 == 0) {
                    Log.d(TAG, "   Still waiting for LLM... ($attempts/$maxAttempts)")
                }
            }
            
            // Timeout - LLM not ready
            Log.w(TAG, "⚠️ LLM initialization timeout after ${maxAttempts}s")
            withContext(Dispatchers.Main) {
                statusIndicator.text = "⚠️ LLM TIMEOUT"
                classificationResult.text = "LLM not ready. Voice commands may not work."
            }
        }
    }
    
    /**
     * Setup audio components (non-blocking initialization)
     */
    private fun setupAudioComponents() {
        try {
            Log.i(TAG, "🔧 Setting up audio components...")
            
            // 2. Initialize other audio components
            wakeWordDetector = WakeWordDetector(settings.wakePhrase, aiLiveCore.ttsManager)
            commandRouter = CommandRouter(aiLiveCore)


            // 3. Setup Callbacks with SAFETY CHECKS
            wakeWordDetector.onWakeWordDetected = {
                runOnUiThread {
                    Log.i(TAG, "🎯 Wake word detected!")
                    onWakeWordDetected()
                }
            }

            whisperProcessor.onFinalResult = { text ->
                runOnUiThread {
                    try {
                        Log.i(TAG, "📝 ASR transcription received: '$text'")
                        
                        // CRITICAL SAFETY CHECK 1: Guard against null/empty text
                        if (text.isNullOrBlank()) {
                            Log.w(TAG, "⚠️ Empty transcription received, ignoring")
                            if (isListeningForWakeWord && isMicEnabled) {
                                restartWakeWordListening()
                            }
                            return@runOnUiThread
                        }
                        
                        editTextCommand.setText(text)
                        Log.i(TAG, "Final transcription: '$text'")

                        // The new processor is not continuous in the same way.
                        // We decide whether to process as a command or listen for wake word.
                        if (isListeningForWakeWord) {
                            Log.d(TAG, "   Mode: Wake word detection")
                            if (!wakeWordDetector.processText(text)) {
                                Log.d(TAG, "   No wake word detected, restarting listening")
                                restartWakeWordListening()
                            }
                        } else {
                            Log.d(TAG, "   Mode: Command processing")
                            
                            // CRITICAL SAFETY CHECK 2: Verify LLM is ready before processing
                            if (!aiLiveCore.hybridModelManager.isReady) {
                                Log.e(TAG, "❌ LLM not ready, cannot process command")
                                classificationResult.text = "AI is still initializing. Please wait..."
                                statusIndicator.text = "⚠️ NOT READY"
                                
                                // Restart wake word listening
                                if (isMicEnabled) {
                                    isListeningForWakeWord = true
                                    restartWakeWordListening()
                                }
                                return@runOnUiThread
                            }
                            
                            processVoiceCommand(text)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error in ASR callback", e)
                        e.printStackTrace()
                        classificationResult.text = "Error processing voice input: ${e.message}"
                        
                        // Restart wake word listening on error
                        if (isMicEnabled) {
                            isListeningForWakeWord = true
                            restartWakeWordListening()
                        }
                    }
                }
            }

            whisperProcessor.onReadyForSpeech = {
                runOnUiThread {
                    try {
                        Log.d(TAG, "🎤 Ready for speech")
                        statusIndicator.text = if (isListeningForWakeWord) "● LISTENING" else "● COMMAND"
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error in onReadyForSpeech callback", e)
                        e.printStackTrace()
                    }
                }
            }

            whisperProcessor.onError = { error ->
                runOnUiThread {
                    try {
                        Log.w(TAG, "⚠️ Whisper error: $error")
                        statusIndicator.text = "⚠️ ASR ERROR"
                        classificationResult.text = "Speech recognition error: $error"
                        
                        if (isMicEnabled) {
                            isListeningForWakeWord = true
                            restartWakeWordListening()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error in onError callback", e)
                        e.printStackTrace()
                    }
                }
            }

            commandRouter.onResponse = { response ->
                runOnUiThread {
                    try {
                        Log.i(TAG, "📤 Command response received: ${response.take(50)}...")
                        classificationResult.text = response
                        confidenceText.text = "Voice Command Response"
                        
                        // wait for TTS to finish
                        lifecycleScope.launch {
                            aiLiveCore.ttsManager.state.collect { ttsState ->
                                if (ttsState == com.ailive.audio.TTSManager.TTSState.READY) {
                                    delay(500)
                                    if (isMicEnabled) {
                                        isListeningForWakeWord = true
                                        restartWakeWordListening()
                                    }
                                    return@collect
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error in onResponse callback", e)
                        e.printStackTrace()
                    }
                }
            }

            // Mic starts OFF by default - user must enable manually
            isListeningForWakeWord = false
            isMicEnabled = false

            runOnUiThread {
                btnToggleMic.text = "🎤 MIC OFF"
                btnToggleMic.setBackgroundResource(R.drawable.button_toggle_off)
                btnToggleMic.isEnabled = false // Disable until models are ready
            }

            lifecycleScope.launch {
                aiLiveCore.ttsManager.state.collect { ttsState ->
                    when (ttsState) {
                        com.ailive.audio.TTSManager.TTSState.SPEAKING -> {
                            if (!isListeningForWakeWord) {
                                statusIndicator.text = "● SPEAKING"
                            }
                        }
                        else -> { /* no-op */ }
                    }
                }
            }

            Log.i(TAG, "✓ Audio components setup complete")
            Log.i(TAG, "⏱️  Waiting for model initialization to complete...")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Audio component setup failed", e)
            e.printStackTrace()
            runOnUiThread { 
                statusIndicator.text = "⚠️ ERROR"
                classificationResult.text = "Audio setup failed: ${e.message}"
            }
        }
    }
    
    /**
     * Enable audio controls after all models are ready
     * Called after sequential initialization completes
     */
    private fun enableAudioControls() {
        try {
            Log.i(TAG, "🎉 Enabling audio controls - all models ready!")
            
            btnToggleMic.isEnabled = true
            statusIndicator.text = "● READY"
            classificationResult.text = "All systems ready! Enable microphone to start voice interaction."
            
            Log.i(TAG, "✓ Phase 2.3: Offline Audio pipeline operational")
            Log.i(TAG, "✓ Phase 2.4: TTS ready")
            Log.i(TAG, "✓ Sequential initialization complete: Whisper ✓ → LLM ✓ → TTS ✓")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enabling audio controls", e)
            e.printStackTrace()
        }
    }

    private fun startWakeWordListening() {
        editTextCommand.setText("")
        editTextCommand.hint = "Say \"${settings.wakePhrase}\" or type..."
        whisperProcessor.startListening()
        Log.i(TAG, "Listening for wake word...")
    }

    private fun restartWakeWordListening() {
        lifecycleScope.launch {
            delay(500)
            if (isListeningForWakeWord && isMicEnabled) startWakeWordListening()
        }
    }

    private fun onWakeWordDetected() {
        Log.i(TAG, "🎯 Wake word detected!")
        isListeningForWakeWord = false
        editTextCommand.setText("")
        editTextCommand.hint = "Listening for command..."
        statusIndicator.text = "● COMMAND"

        whisperProcessor.stopListening()
        lifecycleScope.launch {
            delay(500)
            whisperProcessor.startListening()
        }
    }

    private fun processVoiceCommand(command: String) {
        try {
            Log.i(TAG, "🎯 Processing voice command: '$command'")
            
            // CRITICAL SAFETY CHECK 1: Validate command is not empty
            if (command.isBlank()) {
                Log.w(TAG, "⚠️ Empty command received, ignoring")
                if (isMicEnabled) {
                    isListeningForWakeWord = true
                    restartWakeWordListening()
                }
                return
            }
            
            // CRITICAL SAFETY CHECK 2: Verify LLM is ready
            if (!aiLiveCore.hybridModelManager.isReady) {
                Log.e(TAG, "❌ LLM not ready, cannot process command")
                statusIndicator.text = "⚠️ NOT READY"
                classificationResult.text = "AI is still initializing. Please wait..."
                
                if (isMicEnabled) {
                    isListeningForWakeWord = true
                    restartWakeWordListening()
                }
                return
            }
            
            statusIndicator.text = "● PROCESSING"
            
            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    Log.d(TAG, "   Routing command to CommandRouter...")
                    commandRouter.processCommand(command)
                    Log.d(TAG, "   ✓ Command processing complete")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing command", e)
                    e.printStackTrace()
                    
                    withContext(Dispatchers.Main) {
                        statusIndicator.text = "⚠️ ERROR"
                        classificationResult.text = "Error processing command: ${e.message}"
                        
                        // Restart wake word listening on error
                        if (isMicEnabled) {
                            isListeningForWakeWord = true
                            restartWakeWordListening()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in processVoiceCommand", e)
            e.printStackTrace()
            statusIndicator.text = "⚠️ ERROR"
            classificationResult.text = "Command processing failed: ${e.message}"
        }
    }

    private fun setupManualControls() {
        btnToggleMic.setOnClickListener {
            if (isMicEnabled) {
                if (::whisperProcessor.isInitialized) {
                    whisperProcessor.stopListening()
                    isListeningForWakeWord = false
                }
                isMicEnabled = false
                btnToggleMic.text = "🎤 MIC OFF"
                btnToggleMic.setBackgroundResource(R.drawable.button_toggle_off)
                statusIndicator.text = "●"
                statusIndicator.textSize = 16f
                editTextCommand.hint = "Type your command..."
                Log.i(TAG, "🎤 Microphone manually disabled")
            } else {
                if (::whisperProcessor.isInitialized) {
                    isListeningForWakeWord = true
                    isMicEnabled = true
                    startWakeWordListening()
                } else {
                    isMicEnabled = true
                }
                btnToggleMic.text = "🎤 MIC ON"
                btnToggleMic.setBackgroundResource(R.drawable.button_toggle_on)
                statusIndicator.text = "●"
                statusIndicator.textSize = 16f
                Log.i(TAG, "🎤 Microphone manually enabled")
            }
        }

        btnToggleCamera.setOnClickListener {
            if (isCameraEnabled) {
                if (::cameraManager.isInitialized) {
                    cameraManager.stopCamera()
                }
                cameraPreview.visibility = View.GONE
                appIconBackground.visibility = View.VISIBLE
                isCameraEnabled = false
                btnToggleCamera.text = "📷 CAM OFF"
                btnToggleCamera.setBackgroundResource(R.drawable.button_toggle_off)
                statusIndicator.text = "●"
                statusIndicator.textSize = 16f
                classificationResult.text = "Camera is OFF. Enable to use vision features."
                confidenceText.text = ""
                inferenceTime.text = ""
                Log.i(TAG, "📷 Camera manually disabled")
            } else {
                cameraPreview.visibility = View.VISIBLE
                appIconBackground.visibility = View.GONE
                if (::cameraManager.isInitialized) {
                    cameraManager.startCamera(cameraPreview)
                }
                isCameraEnabled = true
                btnToggleCamera.text = "📷 CAM ON"
                btnToggleCamera.setBackgroundResource(R.drawable.button_toggle_on)
                statusIndicator.text = "●"
                statusIndicator.textSize = 16f
                classificationResult.text = "Point camera at objects to analyze"
                Log.i(TAG, "📷 Camera manually enabled")
            }
        }

        btnSendCommand.setOnClickListener {
            val command = editTextCommand.text.toString().trim()
            if (command.isNotEmpty() || capturedImageBitmap != null) { // Allow sending with just image
                processTextCommand(command)
                editTextCommand.setText("")
            }
        }

        btnCancelGeneration.setOnClickListener {
            Log.i(TAG, "🛑 User requested cancellation")
            generationJob?.cancel()
            runOnUiThread {
                typingIndicator.visibility = View.GONE
                btnCancelGeneration.visibility = View.GONE
                btnSendCommand.isEnabled = true
                classificationResult.text = "Generation cancelled by user"
                statusIndicator.text = "● CANCELLED"
            }
        }

        editTextCommand.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                val command = editTextCommand.text.toString().trim()
                if (command.isNotEmpty() || capturedImageBitmap != null) { // Allow sending with just image
                    processTextCommand(command)
                    editTextCommand.setText("")
                }
                true
            } else {
                false
            }
        }

        btnCaptureImage.setOnClickListener {
            Log.i(TAG, "📸 Capture Image button clicked.")
            val cameraIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            imageCaptureLauncher.launch(cameraIntent)
        }
    }

    /**
     * Setup settings button to launch Model Settings Activity
     */
    private fun setupSettingsButton() {
        btnSettings.setOnClickListener {
            Log.i(TAG, "⚙️ Opening Model Settings")
            val intent = Intent(this, com.ailive.ui.ModelSettingsActivity::class.java)
            settingsLauncher.launch(intent)  // Use launcher to receive download requests
        }
    }

    /**
     * Setup memory button to launch Memory Activity
     */
    private fun setupMemoryButton() {
        btnMemory.setOnClickListener {
            Log.i(TAG, "🧠 Opening AI Memory")
            val intent = Intent(this, com.ailive.ui.MemoryActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload settings when returning from settings activity
        if (::aiLiveCore.isInitialized) {
            aiLiveCore.hybridModelManager.reloadSettings()
            Log.i(TAG, "⚙️ Settings reloaded on resume")
        }
    }

    private fun processTextCommand(command: String) {
        Log.i(TAG, "📝 Processing command: '$command'")

        // ===== LLM RESPONSE PROCESSING ENTRY POINT =====
        // This is the main function that handles user text commands and generates AI responses.
        // It coordinates the entire flow from user input to AI response delivery.
        
        // SYSTEM VALIDATION: Ensure all components are ready before processing user request
        if (!::aiLiveCore.isInitialized) {
            Log.w(TAG, "⚠️ System not initialized yet - still starting up")
            runOnUiThread {
                statusIndicator.text = "● INITIALIZING..."
                classificationResult.text = "System is still initializing. Please wait..."
                android.widget.Toast.makeText(
                    this,
                    "Please wait for initialization to complete",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        // Show thinking indicator, typing indicator, and cancel button
        runOnUiThread {
            statusIndicator.text = "● THINKING..."
            classificationResult.text = "⏳ Generating response..."
            confidenceText.text = ""
            inferenceTime.text = ""
            typingIndicator.visibility = View.VISIBLE
            btnCancelGeneration.visibility = View.VISIBLE
            btnSendCommand.isEnabled = false
        }

        // Determine if this is a multimodal (image + text) command
        val currentImage = capturedImageBitmap
        val isMultimodal = currentImage != null

        generationJob = lifecycleScope.launch {
            try {
                val responseBuilder = StringBuilder()
                val sentenceBuffer = StringBuilder()
                var tokenCount = 0
                val startTime = System.currentTimeMillis()
                val streamingSpeechEnabled = settings.streamingSpeechEnabled

                if (isMultimodal) {
                    Log.i(TAG, "🖼️ Processing multimodal command with image.")
                    // Call VisionManager for multimodal generation
                    val response = visionManager.generateResponseWithImage(currentImage!!, command)
                    responseBuilder.append(response)
                    sentenceBuffer.append(response)
                    tokenCount = response.length / 5 // Rough token count for display

                    withContext(Dispatchers.Main) {
                        classificationResult.text = responseBuilder.toString()
                        responseScrollView.post {
                            responseScrollView.fullScroll(android.view.View.FOCUS_DOWN)
                        }
                    }
                } else {
                    // ===== LLM STREAMING RESPONSE GENERATION =====
                    // Use PersonalityEngine for proper context (name, time, location)
                    // This generates AI responses token by token for real-time display
                    aiLiveCore.personalityEngine.generateStreamingResponse(command)
                        .collect { token ->
                            tokenCount++
                            responseBuilder.append(token)
                            sentenceBuffer.append(token)

                            // ===== REAL-TIME UI UPDATES FOR STREAMING RESPONSE =====
                            // Each token is immediately displayed to the user as it's generated
                            // This provides instant feedback and natural conversation flow
                            withContext(Dispatchers.Main) {
                                classificationResult.text = responseBuilder.toString()

                                // Auto-scroll to bottom to show new content
                                responseScrollView.post {
                                    responseScrollView.fullScroll(android.view.View.FOCUS_DOWN)
                                }

                                // Update performance stats every 5 tokens
                                if (tokenCount % 5 == 0) {
                                    val elapsed = System.currentTimeMillis() - startTime
                                    val tokensPerSec = if (elapsed > 0) {
                                        (tokenCount.toFloat() / elapsed) * 1000
                                    } else {
                                        0f
                                    }
                                    inferenceTime.text = "${String.format("%.1f", tokensPerSec)} tok/s | $tokenCount tokens"
                                    statusIndicator.text = "● 🔊 LIVE SPEAKING..."
                                }

                                // STREAMING TTS: Speak sentence as soon as it's complete
                                if (streamingSpeechEnabled && ::aiLiveCore.isInitialized) {
                                    val currentText = sentenceBuffer.toString()

                                    // Check if we have a complete sentence or phrase
                                    val shouldSpeak = currentText.endsWith(". ") ||
                                                      currentText.endsWith("! ") ||
                                                      currentText.endsWith("? ") ||
                                                      currentText.endsWith(".\n") ||
                                                      currentText.endsWith("!\n") ||
                                                      currentText.endsWith("?\n") ||
                                                      (currentText.length > 80 && token == " ")  // Long phrase, speak at word boundary

                                    if (shouldSpeak && currentText.length > 10) {
                                        // Speak this sentence incrementally (queues, doesn't interrupt)
                                        val sentenceToSpeak = currentText.trim()
                                        Log.d(TAG, "🔊 Streaming TTS: Speaking sentence of ${sentenceToSpeak.length} chars")
                                        aiLiveCore.ttsManager.speakIncremental(sentenceToSpeak)
                                        sentenceBuffer.clear()
                                    }
                                }
                            }
                        }
                }


                // Generation complete
                val totalTime = System.currentTimeMillis() - startTime
                val tokensPerSec = if (totalTime > 0) {
                    (tokenCount.toFloat() / totalTime) * 1000
                } else {
                    0f
                }

                withContext(Dispatchers.Main) {
                    statusIndicator.text = "●"
                    confidenceText.text = "Completed"
                    inferenceTime.text = "${String.format("%.1f", tokensPerSec)} tok/s | ${totalTime}ms"
                    typingIndicator.visibility = View.GONE
                    btnCancelGeneration.visibility = View.GONE
                    btnSendCommand.isEnabled = true
                    Log.i(TAG, "✅ Streaming complete: $tokenCount tokens in ${totalTime}ms")

                    // Add to conversation history for PersonalityEngine
                    if (::aiLiveCore.isInitialized) {
                        aiLiveCore.personalityEngine.addToHistory(
                            com.ailive.personality.ConversationTurn(
                                role = com.ailive.personality.Role.USER,
                                content = command,
                                timestamp = startTime
                            )
                        )
                        aiLiveCore.personalityEngine.addToHistory(
                            com.ailive.personality.ConversationTurn(
                                role = com.ailive.personality.Role.ASSISTANT,
                                content = responseBuilder.toString(),
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        Log.d(TAG, "📝 Added conversation to history")
                    }

                    // Speak any remaining buffered text
                    if (streamingSpeechEnabled && ::aiLiveCore.isInitialized && sentenceBuffer.isNotEmpty()) {
                        val remaining = sentenceBuffer.toString().trim()
                        if (remaining.isNotEmpty()) {
                            Log.d(TAG, "🔊 Speaking final buffer: ${remaining.length} chars")
                            aiLiveCore.ttsManager.speakIncremental(remaining)
                        }
                    } else if (!streamingSpeechEnabled && ::aiLiveCore.isInitialized) {
                        // Fallback to batched TTS if streaming disabled
                        aiLiveCore.ttsManager.speak(responseBuilder.toString(), com.ailive.audio.TTSManager.Priority.NORMAL)
                    }

                    // Clear captured image after processing
                    capturedImageBitmap?.recycle()
                    capturedImageBitmap = null
                    imageViewCaptured.visibility = View.GONE
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Streaming generation failed", e)
                withContext(Dispatchers.Main) {
                    statusIndicator.text = "● ERROR"
                    classificationResult.text = "Error: ${e.message}"
                    inferenceTime.text = ""
                    typingIndicator.visibility = View.GONE
                    btnCancelGeneration.visibility = View.GONE
                    btnSendCommand.isEnabled = true
                }
            }
        }
    }

    private fun setupDashboard() {
        btnToggleDashboard.setOnClickListener {
            toggleDashboard()
        }
    }

    private fun toggleDashboard() {
        isDashboardVisible = !isDashboardVisible
        if (isDashboardVisible) {
            dashboardContainer.visibility = View.VISIBLE
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
            dashboardContainer.visibility = View.GONE
            Log.i(TAG, "📊 Dashboard closed")
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
            Log.e(TAG, message)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { if (::whisperProcessor.isInitialized) whisperProcessor.release() } catch (e: Exception) {}
        try { if (::cameraManager.isInitialized) cameraManager.stopCamera() } catch (e: Exception) {}
        try { if (::modelManager.isInitialized) modelManager.close() } catch (e: Exception) {} // ModelManager is deprecated, but still has a close() method
        try { if (::aiLiveCore.isInitialized) aiLiveCore.stop() } catch (e: Exception) {}
        try { if (::modelSetupDialog.isInitialized) modelSetupDialog.cleanup() } catch (e: Exception) {}
        capturedImageBitmap?.recycle() // Recycle captured image bitmap
    }
}
