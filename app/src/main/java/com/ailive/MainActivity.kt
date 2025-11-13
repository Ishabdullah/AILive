package com.ailive

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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
    private lateinit var appIconBackground: ImageView
    private lateinit var appTitle: TextView
    private lateinit var classificationResult: TextView
    private lateinit var responseScrollView: ScrollView
    private lateinit var confidenceText: TextView
    private lateinit var inferenceTime: TextView
    private lateinit var statusIndicator: TextView
    private lateinit var typingIndicator: TextView

    // Manual controls
    private lateinit var btnToggleMic: Button
    private lateinit var btnToggleCamera: Button
    private lateinit var editTextCommand: EditText
    private lateinit var btnSendCommand: Button
    private lateinit var btnCancelGeneration: Button
    private lateinit var btnSettings: Button
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

        // Reset callback counter on fresh start
        callbackCount = 0

        // Register file picker BEFORE UI operations (you had this right; keep it)
        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    Log.d(TAG, "File picker selected: $uri")
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
                    Log.d(TAG, "📥 Download request from settings: $modelType")
                    // Trigger download via ModelSetupDialog public API
                    when (modelType) {
                        "BGE" -> modelSetupDialog.triggerBGEDownload()
                        "Memory" -> modelSetupDialog.triggerMemoryDownload()
                        "Qwen" -> modelSetupDialog.triggerQwenDownload()
                        "All" -> modelSetupDialog.triggerAllModelsDownload()
                    }
                }
            }
        }

        // Register permissions launcher - handles partial denials and "don't ask again"
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            // results: map of permission -> Boolean
            val granted = results.filterValues { it }.keys
            val denied = results.filterValues { !it }.keys

            if (denied.isEmpty()) {
                Log.d(TAG, "✓ All requested permissions granted")
                // After permissions granted, check if model setup is needed
                proceedAfterPermissions()
            } else {
                Log.w(TAG, "Permissions denied: $denied")
                handlePartialOrDeniedPermissions(denied.toList())
            }
        }

        // Initialize settings
        settings = AISettings(this)
        if (!settings.isSetupComplete) {
            Log.d(TAG, "Setup bypassed - using defaults")
            settings.isSetupComplete = true
        }

        Log.i(TAG, "=== ${settings.aiName} Starting ===")

        setContentView(R.layout.activity_main)

        // Init UI references with null checks to ensure all IDs exist in activity_main.xml
        cameraPreview = findViewById(R.id.cameraPreview)
            ?: throw IllegalStateException("cameraPreview not found in layout")
        appIconBackground = findViewById(R.id.appIconBackground)
            ?: throw IllegalStateException("appIconBackground not found in layout")
        appTitle = findViewById(R.id.appTitle)
            ?: throw IllegalStateException("appTitle not found in layout")
        classificationResult = findViewById(R.id.classificationResult)
            ?: throw IllegalStateException("classificationResult not found in layout")
        responseScrollView = findViewById(R.id.responseScrollView)
            ?: throw IllegalStateException("responseScrollView not found in layout")
        confidenceText = findViewById(R.id.confidenceText)
            ?: throw IllegalStateException("confidenceText not found in layout")
        inferenceTime = findViewById(R.id.inferenceTime)
            ?: throw IllegalStateException("inferenceTime not found in layout")
        statusIndicator = findViewById(R.id.statusIndicator)
            ?: throw IllegalStateException("statusIndicator not found in layout")
        typingIndicator = findViewById(R.id.typingIndicator)
            ?: throw IllegalStateException("typingIndicator not found in layout")

        btnToggleMic = findViewById(R.id.btnToggleMic)
            ?: throw IllegalStateException("btnToggleMic not found in layout")
        btnToggleCamera = findViewById(R.id.btnToggleCamera)
            ?: throw IllegalStateException("btnToggleCamera not found in layout")
        editTextCommand = findViewById(R.id.editTextCommand)
            ?: throw IllegalStateException("editTextCommand not found in layout")
        btnSendCommand = findViewById(R.id.btnSendCommand)
            ?: throw IllegalStateException("btnSendCommand not found in layout")
        btnCancelGeneration = findViewById(R.id.btnCancelGeneration)
            ?: throw IllegalStateException("btnCancelGeneration not found in layout")
        btnSettings = findViewById(R.id.btnSettings)
            ?: throw IllegalStateException("btnSettings not found in layout")
        btnToggleDashboard = findViewById(R.id.btnToggleDashboard)
            ?: throw IllegalStateException("btnToggleDashboard not found in layout")
        dashboardContainer = findViewById(R.id.dashboardContainer)
            ?: throw IllegalStateException("dashboardContainer not found in layout")

        setupManualControls()
        setupDashboard()
        setupSettingsButton()
        setupAccessibility()

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
     * Requests only essential permissions for core functionality
     */
    private fun buildPermissionList(): List<String> {
        val permissionsToRequest = mutableListOf<String>()

        // Core permissions - always required
        permissionsToRequest.add(Manifest.permission.CAMERA)
        permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)

        // Location permissions for contextual awareness (used by LocationManager)
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // Storage permissions for model files in Downloads folder
        // Note: For Android 13+, we use scoped storage (MediaStore) which doesn't require
        // READ_MEDIA_* permissions for Downloads folder access. Only legacy paths need permissions.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Android 10-12 - need READ_EXTERNAL_STORAGE for Downloads access
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 9 and below - need both READ and WRITE
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        // Android 13+ uses scoped storage - no permissions needed for Downloads folder

        return permissionsToRequest
    }

    /**
     * Request all necessary permissions before proceeding with initialization
     * Shows rationale if user previously denied permissions
     */
    private fun requestInitialPermissions() {
        val permissionsToRequest = buildPermissionList()

        // Check current permission status
        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            Log.d(TAG, "✓ All permissions already granted")
            proceedAfterPermissions()
        } else {
            Log.d(TAG, "Missing permissions (${missing.size}): ${missing.joinToString()}")

            // Check if we should show rationale for any of the missing permissions
            val shouldShowRationale = missing.any { permission ->
                ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
            }

            if (shouldShowRationale) {
                // User previously denied - show explanation before re-requesting
                showPermissionRationale(missing)
            } else {
                // First time request or "Don't ask again" - proceed with request
                statusIndicator.text = "● REQUESTING PERMISSIONS..."
                classificationResult.text = "Please allow camera, microphone, location, and storage access"
                permissionLauncher.launch(missing.toTypedArray())
            }
        }
    }

    /**
     * Show rationale dialog explaining why permissions are needed
     * Called when user previously denied permissions
     */
    private fun showPermissionRationale(permissions: List<String>) {
        val permissionDescriptions = buildPermissionDescriptions(permissions)

        AlertDialog.Builder(this)
            .setTitle("Permissions Needed")
            .setMessage("AILive requires the following permissions to function properly:\n\n$permissionDescriptions\n\nWould you like to grant these permissions?")
            .setPositiveButton("Grant Permissions") { _, _ ->
                statusIndicator.text = "● REQUESTING PERMISSIONS..."
                classificationResult.text = "Please allow the requested permissions"
                permissionLauncher.launch(permissions.toTypedArray())
            }
            .setNegativeButton("Continue with Limited Features") { _, _ ->
                // Allow app to continue with limited functionality
                Log.w(TAG, "User chose to continue without granting all permissions")
                proceedAfterPermissions()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Handle permissions that were denied or partially granted
     * Distinguishes between "denied" and "don't ask again" states
     */
    private fun handlePartialOrDeniedPermissions(deniedPermissions: List<String>) {
        // Categorize denied permissions by type
        val criticalPermissions = deniedPermissions.filter {
            it == Manifest.permission.CAMERA || it == Manifest.permission.RECORD_AUDIO
        }

        // Check if any permission has "don't ask again" status
        val permanentlyDenied = deniedPermissions.filter { permission ->
            !ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        }

        runOnUiThread {
            statusIndicator?.text = "● PERMISSION ISSUE"

            val permissionDescriptions = buildPermissionDescriptions(deniedPermissions)

            val message = if (permanentlyDenied.isNotEmpty()) {
                // User selected "Don't ask again" - must open settings
                "Some permissions were permanently denied. To use all features:\n\n$permissionDescriptions\n\nPlease enable them in app settings."
            } else {
                // Temporarily denied - can retry
                "The following permissions are needed:\n\n$permissionDescriptions"
            }

            if (criticalPermissions.isNotEmpty()) {
                // Critical permissions denied - show mandatory dialog
                classificationResult?.text = "Core permissions required for functionality"

                AlertDialog.Builder(this)
                    .setTitle("Critical Permissions Required")
                    .setMessage(message)
                    .setPositiveButton(if (permanentlyDenied.isNotEmpty()) "Open Settings" else "Retry") { _, _ ->
                        if (permanentlyDenied.isNotEmpty()) {
                            // Open app settings
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                            }
                            startActivity(intent)
                        } else {
                            // Retry permission request
                            requestInitialPermissions()
                        }
                    }
                    .setNegativeButton("Exit") { _, _ ->
                        finish()
                    }
                    .setCancelable(false)
                    .show()
            } else {
                // Non-critical permissions denied - allow continuation with limited features
                classificationResult?.text = "Some features may be unavailable"

                AlertDialog.Builder(this)
                    .setTitle("Optional Permissions")
                    .setMessage("$message\n\nYou can continue with limited functionality or grant permissions for full features.")
                    .setPositiveButton(if (permanentlyDenied.isNotEmpty()) "Open Settings" else "Grant") { _, _ ->
                        if (permanentlyDenied.isNotEmpty()) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                            }
                            startActivity(intent)
                        } else {
                            requestInitialPermissions()
                        }
                    }
                    .setNegativeButton("Continue") { _, _ ->
                        proceedAfterPermissions()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    /**
     * Build user-friendly descriptions for requested permissions
     */
    private fun buildPermissionDescriptions(permissions: List<String>): String {
        return permissions.mapNotNull { permission ->
            when (permission) {
                Manifest.permission.CAMERA -> "• Camera - for visual AI analysis"
                Manifest.permission.RECORD_AUDIO -> "• Microphone - for voice commands and wake word detection"
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION -> "• Location - for contextual awareness (time zones, local info)"
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE -> "• Storage - to access AI model files"
                else -> null
            }
        }.distinct().joinToString("\n")
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
                Log.d(TAG, "Model setup complete, continuing initialization")
                continueInitialization()
            }

            // Show AI name customization first, then model setup
            modelSetupDialog.showNameSetupDialog {
                Log.d(TAG, "Setup complete, continuing initialization")
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
        val models = modelDownloadManager.getAvailableModels()
        Log.d(TAG, "✅ Found ${models.size} model(s) available:")
        models.forEach { model ->
            Log.d(TAG, "   - ${model.name} (${model.length() / 1024 / 1024}MB)")
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
                Log.d(TAG, ">>> Classification #$callbackCount: $label")
                updateUI(label, confidence, time)
            }

            cameraManager.startCamera(cameraPreview)

            val visionTool = com.ailive.personality.tools.VisionAnalysisTool(
                modelManager = modelManager,
                cameraManager = cameraManager
            )
            aiLiveCore.personalityEngine.registerTool(visionTool)
            Log.d(TAG, "✓ VisionAnalysisTool registered")

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
            Log.i(TAG, "=== Initializing Audio Pipeline ===")

            speechProcessor = SpeechProcessor(applicationContext)
            wakeWordDetector = WakeWordDetector(settings.wakePhrase, aiLiveCore.ttsManager)
            commandRouter = CommandRouter(aiLiveCore)

            if (!speechProcessor.initialize()) {
                Log.e(TAG, "❌ Speech processor failed to initialize")
                return
            }

            Log.d(TAG, "✓ Speech processor ready")

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
                    Log.d(TAG, "Final transcription: '$text'")

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

            // Mic starts OFF by default - user must enable manually
            isListeningForWakeWord = false
            isMicEnabled = false
            // Don't start wake word listening automatically

            runOnUiThread {
                btnToggleMic.text = "🎤 MIC OFF"
                btnToggleMic.setBackgroundResource(R.drawable.button_toggle_off)
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
                btnToggleMic.text = "🎤 MIC OFF"
                btnToggleMic.setBackgroundResource(R.drawable.button_toggle_off)
                statusIndicator.text = "●"
                statusIndicator.textSize = 16f
                editTextCommand.hint = "Type your command..."
                Log.d(TAG, "🎤 Microphone manually disabled")
            } else {
                if (::speechProcessor.isInitialized) {
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
                Log.d(TAG, "🎤 Microphone manually enabled")
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
                Log.d(TAG, "📷 Camera manually disabled")
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
                Log.d(TAG, "📷 Camera manually enabled")
            }
        }

        btnSendCommand.setOnClickListener {
            val command = editTextCommand.text.toString().trim()
            if (command.isNotEmpty()) {
                processTextCommand(command)
                editTextCommand.setText("")
            }
        }

        btnCancelGeneration.setOnClickListener {
            Log.d(TAG, "🛑 User requested cancellation")
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

    /**
     * Setup settings button to launch Model Settings Activity
     */
    private fun setupSettingsButton() {
        btnSettings.setOnClickListener {
            Log.d(TAG, "⚙️ Opening Model Settings")
            val intent = Intent(this, com.ailive.ui.ModelSettingsActivity::class.java)
            settingsLauncher.launch(intent)  // Use launcher to receive download requests
        }
    }

    /**
     * Setup accessibility content descriptions for all interactive UI elements
     */
    private fun setupAccessibility() {
        btnToggleMic.contentDescription = "Toggle microphone on or off"
        btnToggleCamera.contentDescription = "Toggle camera on or off"
        btnSendCommand.contentDescription = "Send text command to AI"
        btnCancelGeneration.contentDescription = "Cancel AI response generation"
        btnSettings.contentDescription = "Open settings"
        btnToggleDashboard.contentDescription = "Toggle dashboard visibility"
        editTextCommand.contentDescription = "Enter text command for AI assistant"
    }

    override fun onResume() {
        super.onResume()
        // Reset callback counter on resume to prevent overflow
        callbackCount = 0
        // Reload settings when returning from settings activity
        if (::aiLiveCore.isInitialized) {
            aiLiveCore.llmManager.reloadSettings()
            Log.d(TAG, "⚙️ Settings reloaded on resume")
        }
    }

    private fun processTextCommand(command: String) {
        Log.i(TAG, "📝 Processing text command: '$command'")

        // Check if AILive core is initialized
        if (!::aiLiveCore.isInitialized) {
            Log.w(TAG, "⚠️ System not initialized yet - still starting up")
            runOnUiThread {
                statusIndicator.text = "● INITIALIZING..."
                classificationResult.text = "System is still initializing. Please wait..."
                Toast.makeText(
                    this,
                    "Please wait for initialization to complete",
                    Toast.LENGTH_SHORT
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

        // Stream response in real-time with incremental TTS (store Job for cancellation)
        generationJob = lifecycleScope.launch {
            try {
                val responseBuilder = StringBuilder()
                val sentenceBuffer = StringBuilder()
                var tokenCount = 0
                val startTime = System.currentTimeMillis()
                val streamingSpeechEnabled = settings.streamingSpeechEnabled

                // Use PersonalityEngine for proper context (name, time, location)
                aiLiveCore.personalityEngine.generateStreamingResponse(command)
                    .collect { token ->
                        tokenCount++
                        responseBuilder.append(token)
                        sentenceBuffer.append(token)

                        // Update UI with streaming text
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

    override fun onDestroy() {
        super.onDestroy()
        try { if (::speechProcessor.isInitialized) speechProcessor.release() } catch (e: Exception) {}
        try { if (::cameraManager.isInitialized) cameraManager.stopCamera() } catch (e: Exception) {}
        try { if (::modelManager.isInitialized) modelManager.close() } catch (e: Exception) {}
        try { if (::aiLiveCore.isInitialized) aiLiveCore.stop() } catch (e: Exception) {}
        try { if (::modelSetupDialog.isInitialized) modelSetupDialog.cleanup() } catch (e: Exception) {}
    }
}
