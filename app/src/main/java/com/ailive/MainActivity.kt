package com.ailive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
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

    // Audio components
    private lateinit var audioManager: AudioManager
    private lateinit var speechProcessor: SpeechProcessor
    private lateinit var wakeWordDetector: WakeWordDetector
    private lateinit var commandRouter: CommandRouter

    // UI
    private lateinit var cameraPreview: PreviewView
    private lateinit var appTitle: TextView
    private lateinit var classificationResult: TextView
    private lateinit var confidenceText: TextView
    private lateinit var inferenceTime: TextView
    private lateinit var statusIndicator: TextView

    // Manual controls
    private lateinit var btnToggleMic: android.widget.Button
    private lateinit var btnToggleCamera: android.widget.Button
    private lateinit var editTextCommand: android.widget.EditText
    private lateinit var btnSendCommand: android.widget.Button
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

    // Permissions launcher
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    private var callbackCount = 0
    private var isInitialized = false
    private var isListeningForWakeWord = false
    private var isMicEnabled = false
    private var isCameraEnabled = false

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

        // Register permissions launcher - single modern path
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            // results: map of permission -> Boolean
            val allGranted = results.values.all { it }
            if (allGranted) {
                Log.i(TAG, "✓ All requested permissions granted (launcher)")
                startModels()
            } else {
                Log.e(TAG, "✗ One or more permissions denied: $results")
                statusIndicator?.text = "● PERMISSION DENIED"
                classificationResult?.text = "Camera and microphone permissions required"
                // Friendly UX: show explanation, then finish
                finish()
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
        appTitle = findViewById(R.id.appTitle)
        classificationResult = findViewById(R.id.classificationResult)
        confidenceText = findViewById(R.id.confidenceText)
        inferenceTime = findViewById(R.id.inferenceTime)
        statusIndicator = findViewById(R.id.statusIndicator)

        btnToggleMic = findViewById(R.id.btnToggleMic)
        btnToggleCamera = findViewById(R.id.btnToggleCamera)
        editTextCommand = findViewById(R.id.editTextCommand)
        btnSendCommand = findViewById(R.id.btnSendCommand)
        btnToggleDashboard = findViewById(R.id.btnToggleDashboard)
        dashboardContainer = findViewById(R.id.dashboardContainer)

        setupManualControls()
        setupDashboard()

        appTitle.text = "${settings.aiName} (Vision + Audio)"

        statusIndicator.text = "● INITIALIZING..."
        classificationResult.text = "Initializing ${settings.aiName}..."

        // Model downloader and dialog
        modelDownloadManager = ModelDownloadManager(this)
        modelSetupDialog = ModelSetupDialog(this, modelDownloadManager, filePickerLauncher)

        // If model setup required, show dialog and defer init
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
            // stop further onCreate initialization until user completes model setup
            return
        }

        // If we get here, either models already present or modelSetupDialog not required.
        continueInitialization()
    }

    private fun continueInitialization() {
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

        // Build permission list dynamically (device-version-aware)
        val permissionsToRequest = mutableListOf<String>()
        permissionsToRequest.add(Manifest.permission.CAMERA)
        permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)

        // If your ModelDownloadManager or import flow uses legacy storage APIs on SDK < Q, request read/write
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Android 10/11/12: READ_EXTERNAL_STORAGE often still helpful for imports from file system
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        // On Android 13+ apps should use the SAF or media permissions; many import flows don't need storage permission.

        // Check current permission status
        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        runOnUiThread { statusIndicator.text = "● CHECKING PERMISSIONS..." }

        if (missing.isEmpty()) {
            Log.i(TAG, "✓ Permissions already granted")
            startModels()
        } else {
            Log.i(TAG, "Requesting permissions: $missing")
            runOnUiThread {
                statusIndicator.text = "● REQUESTING PERMISSIONS..."
                classificationResult.text = "Please allow camera and microphone access"
            }
            // Launch modern permission flow
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startModels() {
        if (isInitialized) return
        isInitialized = true

        try {
            runOnUiThread {
                statusIndicator.text = "● LOADING AI MODEL..."
                classificationResult.text = "Loading TensorFlow Lite..."
            }

            modelManager = ModelManager(applicationContext)

            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    modelManager.initialize()

                    withContext(Dispatchers.Main) {
                        Log.i(TAG, "✓ Phase 2.1: TensorFlow ready")
                        statusIndicator.text = "● STARTING CAMERA..."
                        classificationResult.text = "Initializing camera..."

                        delay(500)
                        initializeCamera()

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
                lifecycleOwner = this,
                modelManager = modelManager
            )

            cameraManager.onClassificationResult = { label, confidence, time ->
                callbackCount++
                Log.i(TAG, ">>> Classification #$callbackCount: $label")
                updateUI(label, confidence, time)
            }

            cameraManager.startCamera(cameraPreview)

            val visionTool = com.ailive.personality.tools.VisionAnalysisTool(
                modelManager = modelManager,
                cameraManager = cameraManager
            )
            aiLiveCore.personalityEngine.registerTool(visionTool)
            Log.i(TAG, "✓ VisionAnalysisTool registered")

            runOnUiThread {
                statusIndicator.text = "● ANALYZING..."
                classificationResult.text = "Point at objects"

                isCameraEnabled = true
                btnToggleCamera.text = "📷 CAM"
                btnToggleCamera.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
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
            Log.i(TAG, "=== Initializing Audio Pipeline ===")

            speechProcessor = SpeechProcessor(applicationContext)
            wakeWordDetector = WakeWordDetector(settings.wakePhrase, aiLiveCore.ttsManager)
            commandRouter = CommandRouter(aiLiveCore)

            if (!speechProcessor.initialize()) {
                Log.e(TAG, "❌ Speech processor failed to initialize")
                return
            }

            Log.i(TAG, "✓ Speech processor ready")

            wakeWordDetector.onWakeWordDetected = {
                runOnUiThread {
                    onWakeWordDetected()
                }
            }

            speechProcessor.onPartialResult = { text ->
                runOnUiThread {
                    editTextCommand.setText(text)
                    if (isListeningForWakeWord) {
                        wakeWordDetector.processText(text)
                    }
                }
            }

            speechProcessor.onFinalResult = { text ->
                runOnUiThread {
                    editTextCommand.setText(text)
                    Log.i(TAG, "Final transcription: '$text'")

                    if (isListeningForWakeWord) {
                        if (!wakeWordDetector.processText(text)) {
                            restartWakeWordListening()
                        }
                    } else {
                        processVoiceCommand(text)
                    }
                }
            }

            speechProcessor.onReadyForSpeech = {
                runOnUiThread {
                    statusIndicator.text = if (isListeningForWakeWord) "● LISTENING" else "● COMMAND"
                }
            }

            speechProcessor.onError = { error ->
                runOnUiThread {
                    Log.w(TAG, "Speech error: $error")
                    if (isMicEnabled) {
                        isListeningForWakeWord = true
                        restartWakeWordListening()
                    }
                }
            }

            commandRouter.onResponse = { response ->
                runOnUiThread {
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
                }
            }

            // Start listening
            isListeningForWakeWord = true
            isMicEnabled = true
            startWakeWordListening()

            runOnUiThread {
                btnToggleMic.text = "🎤 MIC"
                btnToggleMic.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
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

            Log.i(TAG, "✓ Phase 2.3: Audio pipeline operational")
            Log.i(TAG, "✓ Phase 2.4: TTS ready")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Audio init failed", e)
            runOnUiThread { statusIndicator.text = "● ERROR" }
        }
    }

    private fun startWakeWordListening() {
        editTextCommand.setText("")
        editTextCommand.hint = "Say \"${settings.wakePhrase}\" or type..."
        speechProcessor.startListening(continuous = false)
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

        speechProcessor.stopListening()
        lifecycleScope.launch {
            delay(500)
            speechProcessor.startListening(continuous = false)
        }
    }

    private fun processVoiceCommand(command: String) {
        Log.i(TAG, "Processing command: '$command'")
        statusIndicator.text = "● PROCESSING"
        lifecycleScope.launch(Dispatchers.Default) {
            commandRouter.processCommand(command)
        }
    }

    private fun setupManualControls() {
        btnToggleMic.setOnClickListener {
            if (isMicEnabled) {
                if (::speechProcessor.isInitialized) {
                    speechProcessor.stopListening()
                    isListeningForWakeWord = false
                }
                isMicEnabled = false
                btnToggleMic.text = "🎤 MIC"
                btnToggleMic.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
                statusIndicator.text = "● MIC OFF"
                editTextCommand.hint = "Type command..."
                Log.i(TAG, "🎤 Microphone manually disabled")
            } else {
                if (::speechProcessor.isInitialized) {
                    isListeningForWakeWord = true
                    isMicEnabled = true
                    startWakeWordListening()
                } else {
                    isMicEnabled = true
                }
                btnToggleMic.text = "🎤 MIC"
                btnToggleMic.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
                Log.i(TAG, "🎤 Microphone manually enabled")
            }
        }

        btnToggleCamera.setOnClickListener {
            if (isCameraEnabled) {
                if (::cameraManager.isInitialized) {
                    cameraManager.stopCamera()
                }
                cameraPreview.visibility = View.INVISIBLE
                cameraPreview.setBackgroundColor(android.graphics.Color.BLACK)
                isCameraEnabled = false
                btnToggleCamera.text = "📷 CAM"
                btnToggleCamera.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
                statusIndicator.text = "● CAM OFF"
                classificationResult.text = "Camera off"
                confidenceText.text = ""
                inferenceTime.text = ""
                Log.i(TAG, "📷 Camera manually disabled")
            } else {
                cameraPreview.visibility = View.VISIBLE
                cameraPreview.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                if (::cameraManager.isInitialized) {
                    cameraManager.startCamera(cameraPreview)
                }
                isCameraEnabled = true
                btnToggleCamera.text = "📷 CAM"
                btnToggleCamera.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
                statusIndicator.text = "● ANALYZING"
                classificationResult.text = "Point at objects"
                Log.i(TAG, "📷 Camera manually enabled")
            }
        }

        btnSendCommand.setOnClickListener {
            val command = editTextCommand.text.toString().trim()
            if (command.isNotEmpty()) {
                processTextCommand(command)
                editTextCommand.setText("")
            }
        }

        editTextCommand.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                val command = editTextCommand.text.toString().trim()
                if (command.isNotEmpty()) {
                    processTextCommand(command)
                    editTextCommand.setText("")
                }
                true
            } else {
                false
            }
        }
    }

    private fun processTextCommand(command: String) {
        Log.i(TAG, "📝 Processing text command: '$command'")
        statusIndicator.text = "● PROCESSING"
        lifecycleScope.launch(Dispatchers.Default) {
            commandRouter.processCommand(command)
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

    override fun onDestroy() {
        super.onDestroy()
        try { if (::speechProcessor.isInitialized) speechProcessor.release() } catch (e: Exception) {}
        try { if (::cameraManager.isInitialized) cameraManager.stopCamera() } catch (e: Exception) {}
        try { if (::modelManager.isInitialized) modelManager.close() } catch (e: Exception) {}
        try { if (::aiLiveCore.isInitialized) aiLiveCore.stop() } catch (e: Exception) {}
        try { if (::modelSetupDialog.isInitialized) modelSetupDialog.cleanup() } catch (e: Exception) {}
    }
}
