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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import com.ailive.personality.SentenceDetector
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import java.util.concurrent.CancellationException

/**
 * Audio state machine for wake word and command processing
 */
enum class AudioState {
    IDLE,                    // Not listening
    LISTENING_WAKE_WORD,     // Listening for wake word
    LISTENING_COMMAND,       // Listening for command after wake word
    PROCESSING_COMMAND,      // Processing voice command
    STOPPING                 // Transitioning to stopped state
}

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
    private var isModelsReady = false
    private var isCoreReady = false

    // Audio state machine
    private var currentAudioState = AudioState.IDLE
    private val isListeningForWakeWord: Boolean
        get() = currentAudioState == AudioState.LISTENING_WAKE_WORD

    private var isMicEnabled = false
    private var isCameraEnabled = false

    // LLM generation control
    private var generationJob: Job? = null

    // Track pending conversation turns for cancel handling
    private var pendingUserTurn: com.ailive.personality.ConversationTurn? = null
    private var pendingAssistantTurn: com.ailive.personality.ConversationTurn? = null

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

            // Setup callback for file picker if user chooses to import models
            filePickerOnComplete = {
                Log.d(TAG, "File picker complete, continuing initialization")
                continueInitialization()
            }

            // Show AI name customization first, then model setup
            // This callback is invoked when setup completes (download/import/skip)
            modelSetupDialog.showNameSetupDialog {
                Log.d(TAG, "Model setup dialog complete, continuing initialization")
                // If file picker wasn't used, clear the callback and proceed
                filePickerOnComplete = null
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

        // Initialize AILive Core (but don't start it yet - wait for models)
        try {
            Log.i(TAG, "=== Initializing ${settings.aiName} Core ===")
            aiLiveCore = AILiveCore(applicationContext, this)
            aiLiveCore.initialize()
            isCoreReady = true
            Log.i(TAG, "✓ Phase 1: Core initialized (awaiting models)")
        } catch (e: Exception) {
            Log.e(TAG, "AILive Core init failed", e)
            runOnUiThread {
                statusIndicator.text = "● CORE ERROR"
                classificationResult.text = "Error: ${e.message}"
            }
            return
        }

        // Now initialize models, camera, and audio BEFORE starting core
        Log.i(TAG, "✓ Permissions verified, initializing models")
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
                    // Phase 2.1: Initialize TensorFlow model manager
                    modelManager.initialize()

                    withContext(Dispatchers.Main) {
                        Log.i(TAG, "✓ Phase 2.1: TensorFlow ready")
                        statusIndicator.text = "● STARTING CAMERA..."
                        classificationResult.text = "Initializing camera..."

                        // Initialize camera and audio sequentially
                        // No artificial delays needed - proper error handling ensures stability
                        initializeCamera()
                        initializeAudio()

                        // Mark models as ready
                        isModelsReady = true
                        Log.i(TAG, "✓ Phase 2.4: All models initialized")

                        // Now that models are ready, start the core
                        startAILiveCore()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.e(TAG, "TensorFlow init failed", e)
                        statusIndicator.text = "● AI MODEL ERROR"
                        classificationResult.text = "Error: ${e.message}"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Init failed", e)
            runOnUiThread {
                statusIndicator.text = "● INIT ERROR"
                classificationResult.text = "Error: ${e.message}"
            }
        }
    }

    /**
     * Start AILive Core after all models and tools are ready
     * This ensures tools are registered before the core starts processing
     */
    private fun startAILiveCore() {
        if (!isCoreReady || !isModelsReady) {
            Log.w(TAG, "⚠️ Cannot start core - prerequisites not met (core: $isCoreReady, models: $isModelsReady)")
            return
        }

        try {
            Log.i(TAG, "=== Starting ${settings.aiName} Core ===")
            aiLiveCore.start()
            Log.i(TAG, "✓ Phase 3: Core started - All systems operational")

            // Run integration tests AFTER everything is initialized
            runIntegrationTests()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AILive Core", e)
            runOnUiThread {
                statusIndicator.text = "● START ERROR"
                classificationResult.text = "Error starting core: ${e.message}"
            }
        }
    }

    /**
     * Run integration tests after all systems are initialized
     * Only runs if core and models are ready
     */
    private fun runIntegrationTests() {
        if (!isCoreReady || !isModelsReady) {
            Log.w(TAG, "⚠️ Skipping tests - system not fully initialized")
            return
        }

        lifecycleScope.launch {
            try {
                // Brief delay to ensure all async initialization completes
                delay(1000)  // Reduced from 2000ms - proper state checks make long delays unnecessary

                // Double-check system state before running tests
                if (!::aiLiveCore.isInitialized || !::modelManager.isInitialized) {
                    Log.w(TAG, "⚠️ Skipping tests - core components not initialized")
                    return@launch
                }

                Log.i(TAG, "🧪 Running integration tests...")
                val tests = TestScenarios(aiLiveCore)
                tests.runAllTests()
                Log.i(TAG, "✅ Integration tests complete")
            } catch (e: Exception) {
                Log.e(TAG, "Integration tests failed", e)
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

            // Camera will be started lazily when user toggles it ON
            // This prevents binding camera hardware when not in use (battery drain)
            // See btnToggleCamera.setOnClickListener for camera start logic

            // Register VisionAnalysisTool with PersonalityEngine before core starts
            val visionTool = com.ailive.personality.tools.VisionAnalysisTool(
                modelManager = modelManager,
                cameraManager = cameraManager
            )
            aiLiveCore.personalityEngine.registerTool(visionTool)
            Log.i(TAG, "✓ Phase 2.2: Camera & VisionAnalysisTool registered")

            // Already on Main thread from withContext(Dispatchers.Main) - no need for runOnUiThread
            statusIndicator.text = "●"
            classificationResult.text = "Ready for input..."

            // Camera starts OFF by default - user must enable manually
            isCameraEnabled = false
            btnToggleCamera.text = "📷 CAM OFF"
            btnToggleCamera.setBackgroundResource(R.drawable.button_toggle_off)
            cameraPreview.visibility = View.GONE
            appIconBackground.visibility = View.VISIBLE

            // Debug counter coroutine - respects lifecycle (pauses when app backgrounded)
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    var seconds = 0
                    while (isActive) {
                        delay(1000)
                        seconds++
                        if (callbackCount == 0) {
                            statusIndicator.text = "● WAITING ${seconds}s (0 results)"
                        }
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

            // Use custom theme colors instead of android.R.color for better theming
            val colorRes = when {
                confidence > 0.7f -> R.color.colorConfidenceHigh
                confidence > 0.4f -> R.color.colorConfidenceMedium
                else -> R.color.colorConfidenceLow
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

                    when (currentAudioState) {
                        AudioState.LISTENING_WAKE_WORD -> {
                            if (!wakeWordDetector.processText(text)) {
                                restartWakeWordListening()
                            }
                        }
                        AudioState.LISTENING_COMMAND -> {
                            processVoiceCommand(text)
                        }
                        else -> {
                            Log.d(TAG, "Ignoring speech result in state: $currentAudioState")
                        }
                    }
                }
            }

            speechProcessor.onReadyForSpeech = {
                runOnUiThread {
                    statusIndicator.text = when (currentAudioState) {
                        AudioState.LISTENING_WAKE_WORD -> "● LISTENING"
                        AudioState.LISTENING_COMMAND -> "● COMMAND"
                        else -> "●"
                    }
                }
            }

            speechProcessor.onError = { error ->
                runOnUiThread {
                    Log.w(TAG, "Speech error: $error (state: $currentAudioState)")

                    // Only restart if mic is enabled and we were actively listening
                    if (isMicEnabled && currentAudioState != AudioState.IDLE) {
                        // Don't restart if we're processing a command
                        if (currentAudioState != AudioState.PROCESSING_COMMAND) {
                            restartWakeWordListening()
                        } else {
                            Log.d(TAG, "Processing command, ignoring error")
                        }
                    } else {
                        currentAudioState = AudioState.IDLE
                    }
                }
            }

            // Note: commandRouter.onResponse is no longer used since we switched to streaming
            // Voice commands now use the same streaming path as text commands

            // Mic starts OFF by default - user must enable manually
            currentAudioState = AudioState.IDLE
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
                            if (currentAudioState != AudioState.LISTENING_WAKE_WORD) {
                                statusIndicator.text = "● SPEAKING"
                            }
                        }
                        else -> { /* no-op */ }
                    }
                }
            }

            Log.i(TAG, "✓ Phase 2.3: Audio pipeline operational and TTS ready")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Audio init failed", e)
            runOnUiThread { statusIndicator.text = "● ERROR" }
        }
    }

    private fun startWakeWordListening() {
        if (currentAudioState == AudioState.LISTENING_WAKE_WORD) {
            Log.d(TAG, "Already listening for wake word, ignoring duplicate call")
            return
        }

        currentAudioState = AudioState.LISTENING_WAKE_WORD
        editTextCommand.setText("")
        editTextCommand.hint = "Say \"${settings.wakePhrase}\" or type..."
        speechProcessor.startListening(continuous = false)
        Log.i(TAG, "🎤 Started listening for wake word")
    }

    private fun restartWakeWordListening() {
        // Only restart if mic is enabled and we're not already listening
        if (!isMicEnabled) {
            Log.d(TAG, "Mic disabled, not restarting wake word listening")
            currentAudioState = AudioState.IDLE
            return
        }

        if (currentAudioState == AudioState.LISTENING_WAKE_WORD) {
            Log.d(TAG, "Already listening for wake word, skipping restart")
            return
        }

        // Don't restart if actively processing a command
        if (currentAudioState == AudioState.PROCESSING_COMMAND) {
            Log.d(TAG, "Processing command, not restarting wake word listening")
            return
        }

        Log.d(TAG, "Restarting wake word listening")
        startWakeWordListening()
    }

    private fun onWakeWordDetected() {
        Log.i(TAG, "🎯 Wake word detected!")

        // Transition state without stopping/restarting to prevent buffer overlap
        currentAudioState = AudioState.LISTENING_COMMAND

        editTextCommand.setText("")
        editTextCommand.hint = "Listening for command..."
        statusIndicator.text = "● COMMAND"

        // Continue listening without interruption - the speech processor is already active
        // The onFinalResult callback will handle the command when user finishes speaking
        Log.d(TAG, "🎤 Now listening for command (no audio restart needed)")
    }

    /**
     * Remove pending conversation turns from PersonalityEngine history
     * Used when cancellation occurs mid-stream to prevent history corruption
     */
    private fun removeConversationTurns(
        userTurn: com.ailive.personality.ConversationTurn?,
        assistantTurn: com.ailive.personality.ConversationTurn?
    ) {
        if (!::aiLiveCore.isInitialized) return

        // Access conversation history via reflection (PersonalityEngine doesn't expose removeFromHistory)
        // This is a workaround - ideally PersonalityEngine should have a removeFromHistory method
        try {
            val personalityEngine = aiLiveCore.personalityEngine
            val historyField = personalityEngine.javaClass.getDeclaredField("conversationHistory")
            historyField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val history = historyField.get(personalityEngine) as? MutableList<com.ailive.personality.ConversationTurn>

            if (history != null) {
                var removed = 0
                if (userTurn != null && history.remove(userTurn)) {
                    removed++
                    Log.d(TAG, "✓ Removed pending USER turn from history")
                }
                if (assistantTurn != null && history.remove(assistantTurn)) {
                    removed++
                    Log.d(TAG, "✓ Removed pending ASSISTANT turn from history")
                }
                if (removed > 0) {
                    Log.i(TAG, "Cleaned up $removed pending turn(s) from conversation history")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not remove pending turns from history: ${e.message}")
        }
    }

    private fun processVoiceCommand(command: String) {
        Log.i(TAG, "🗣️ Processing voice command: '$command'")

        // Set state to processing
        currentAudioState = AudioState.PROCESSING_COMMAND

        // Check if AILive core is initialized
        if (!::aiLiveCore.isInitialized) {
            Log.w(TAG, "⚠️ System not initialized yet")
            runOnUiThread {
                statusIndicator.text = "● ERROR"
                classificationResult.text = "System is still initializing. Please wait..."
                // Return to listening after error
                restartWakeWordListening()
            }
            return
        }

        // Show processing indicator
        runOnUiThread {
            statusIndicator.text = "● PROCESSING..."
            classificationResult.text = "⏳ Generating response..."
            confidenceText.text = ""
            inferenceTime.text = ""
            typingIndicator.visibility = View.VISIBLE
            btnCancelGeneration.visibility = View.VISIBLE
            btnSendCommand.isEnabled = false
        }

        // Use streaming response like text commands (unified processing)
        generationJob = lifecycleScope.launch {
            // Track pending turns for cancel handling
            val userTurn = com.ailive.personality.ConversationTurn(
                role = com.ailive.personality.Role.USER,
                content = command,
                timestamp = System.currentTimeMillis()
            )
            pendingUserTurn = userTurn

            try {
                val responseBuilder = StringBuilder()
                val sentenceBuffer = StringBuilder()
                var tokenCount = 0
                val startTime = System.currentTimeMillis()
                val streamingSpeechEnabled = settings.streamingSpeechEnabled
                var lastTTSTime = 0L  // For debouncing

                // Add user turn to history immediately
                if (::aiLiveCore.isInitialized) {
                    aiLiveCore.personalityEngine.addToHistory(userTurn)
                    Log.d(TAG, "Added USER turn to history (pending completion)")
                }

                // Use PersonalityEngine for proper context (unified with text commands)
                try {
                    aiLiveCore.personalityEngine.generateStreamingResponse(command)
                        .collect { token ->
                            tokenCount++
                            responseBuilder.append(token)
                            sentenceBuffer.append(token)

                            // Update UI with streaming text
                            withContext(Dispatchers.Main) {
                                classificationResult.text = responseBuilder.toString()

                                // Auto-scroll to bottom
                                responseScrollView.post {
                                    responseScrollView.fullScroll(android.view.View.FOCUS_DOWN)
                                }

                                // Update performance stats
                                if (tokenCount % 5 == 0) {
                                    val elapsed = System.currentTimeMillis() - startTime
                                    val tokensPerSec = if (elapsed > 0) {
                                        (tokenCount.toFloat() / elapsed) * 1000
                                    } else {
                                        0f
                                    }
                                    inferenceTime.text = "${String.format("%.1f", tokensPerSec)} tok/s | $tokenCount tokens"
                                    statusIndicator.text = "● 🔊 SPEAKING..."
                                }

                                // IMPROVED: Streaming TTS with intelligent sentence detection + debouncing
                                if (streamingSpeechEnabled && ::aiLiveCore.isInitialized) {
                                    val currentText = sentenceBuffer.toString()

                                    // Use SentenceDetector for smarter boundary detection (handles abbreviations)
                                    val isCompleteSentence = SentenceDetector.isCompleteSentence(
                                        currentText,
                                        minLength = 10,
                                        longPhraseThreshold = 80
                                    )

                                    // Debounce: Wait at least 300ms between TTS calls for smoother pacing
                                    val now = System.currentTimeMillis()
                                    val timeSinceLastTTS = now - lastTTSTime

                                    if (isCompleteSentence && timeSinceLastTTS > 300) {
                                        val sentenceToSpeak = currentText.trim()
                                        Log.d(TAG, "🔊 Voice: Streaming TTS (${sentenceToSpeak.length} chars)")
                                        aiLiveCore.ttsManager.speakIncremental(sentenceToSpeak)
                                        sentenceBuffer.clear()
                                        lastTTSTime = now
                                    }
                                }
                            }
                        }
                } catch (e: CancellationException) {
                    // User cancelled - this is expected, rethrow to be handled in outer catch
                    Log.d(TAG, "Voice command streaming cancelled by user")
                    throw e
                } catch (e: Exception) {
                    // Mid-stream error (network, model, etc.)
                    Log.e(TAG, "❌ Mid-stream error during voice command generation", e)
                    throw e
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
                    Log.i(TAG, "✅ Voice command complete: $tokenCount tokens in ${totalTime}ms")

                    // Add assistant turn to conversation history
                    if (::aiLiveCore.isInitialized) {
                        val assistantTurn = com.ailive.personality.ConversationTurn(
                            role = com.ailive.personality.Role.ASSISTANT,
                            content = responseBuilder.toString(),
                            timestamp = System.currentTimeMillis()
                        )
                        pendingAssistantTurn = assistantTurn
                        aiLiveCore.personalityEngine.addToHistory(assistantTurn)
                        Log.d(TAG, "✓ Added ASSISTANT turn to history")

                        // Clear pending turns (successful completion)
                        pendingUserTurn = null
                        pendingAssistantTurn = null
                    }

                    // Speak remaining buffered text
                    if (streamingSpeechEnabled && ::aiLiveCore.isInitialized && sentenceBuffer.isNotEmpty()) {
                        val remaining = sentenceBuffer.toString().trim()
                        if (remaining.isNotEmpty()) {
                            Log.d(TAG, "🔊 Voice: Speaking final buffer (${remaining.length} chars)")
                            aiLiveCore.ttsManager.speakIncremental(remaining)
                        }
                    } else if (!streamingSpeechEnabled && ::aiLiveCore.isInitialized) {
                        aiLiveCore.ttsManager.speak(responseBuilder.toString(), com.ailive.audio.TTSManager.Priority.NORMAL)
                    }

                    // Return to wake word listening after completion
                    restartWakeWordListening()
                }

            } catch (e: CancellationException) {
                // User cancelled - pending turns will be removed by cancel button handler
                Log.d(TAG, "Voice command cancelled - pending turns will be cleaned up")
                withContext(Dispatchers.Main) {
                    // Cancel button handler already does UI cleanup and history removal
                    // Just ensure we return to wake word listening
                    restartWakeWordListening()
                }
            } catch (e: Exception) {
                // Mid-stream or generation error - rollback history
                Log.e(TAG, "❌ Voice command streaming failed: ${e.message}", e)

                // Remove pending turns from history on error
                removeConversationTurns(pendingUserTurn, pendingAssistantTurn)
                pendingUserTurn = null
                pendingAssistantTurn = null

                withContext(Dispatchers.Main) {
                    statusIndicator.text = "● ERROR"
                    classificationResult.text = "Error: ${e.message}"
                    inferenceTime.text = ""
                    typingIndicator.visibility = View.GONE
                    btnCancelGeneration.visibility = View.GONE
                    btnSendCommand.isEnabled = true

                    // Return to wake word listening after error
                    restartWakeWordListening()
                }
            } finally {
                generationJob = null
            }
        }
    }

    private fun setupManualControls() {
        btnToggleMic.setOnClickListener {
            if (isMicEnabled) {
                // Stop listening and transition to IDLE
                if (::speechProcessor.isInitialized) {
                    speechProcessor.stopListening()
                }
                currentAudioState = AudioState.IDLE
                isMicEnabled = false
                btnToggleMic.text = "🎤 MIC OFF"
                btnToggleMic.setBackgroundResource(R.drawable.button_toggle_off)
                statusIndicator.text = "●"
                statusIndicator.textSize = 16f
                editTextCommand.hint = "Type your command..."
                Log.d(TAG, "🎤 Microphone manually disabled")
            } else {
                // Enable mic and start listening for wake word
                isMicEnabled = true
                if (::speechProcessor.isInitialized) {
                    startWakeWordListening()
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
                // Check camera permission before enabling
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Camera permission not granted, cannot enable camera")
                    classificationResult.text = "Camera permission required. Please grant in settings."
                    return@setOnClickListener
                }

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
            generationJob = null  // Prevent memory leak

            // Remove pending turns from conversation history to prevent corruption
            if (pendingUserTurn != null || pendingAssistantTurn != null) {
                Log.d(TAG, "Removing pending conversation turns from history")
                removeConversationTurns(pendingUserTurn, pendingAssistantTurn)
                pendingUserTurn = null
                pendingAssistantTurn = null
            }

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
            try {
                // Check that llmManager is initialized before calling methods on it
                val llmManager = aiLiveCore.llmManager
                llmManager.reloadSettings()
                Log.d(TAG, "⚙️ Settings reloaded on resume")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to reload settings on resume: ${e.message}")
            }
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
            // Track pending turns for cancel handling
            val userTurn = com.ailive.personality.ConversationTurn(
                role = com.ailive.personality.Role.USER,
                content = command,
                timestamp = System.currentTimeMillis()
            )
            pendingUserTurn = userTurn

            try {
                val responseBuilder = StringBuilder()
                val sentenceBuffer = StringBuilder()
                var tokenCount = 0
                val startTime = System.currentTimeMillis()
                val streamingSpeechEnabled = settings.streamingSpeechEnabled
                var lastTTSTime = 0L  // For debouncing

                // Add user turn to history immediately
                if (::aiLiveCore.isInitialized) {
                    aiLiveCore.personalityEngine.addToHistory(userTurn)
                    Log.d(TAG, "Added USER turn to history (pending completion)")
                }

                // Use PersonalityEngine for proper context (name, time, location)
                try {
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

                                // IMPROVED: Streaming TTS with intelligent sentence detection + debouncing
                                if (streamingSpeechEnabled && ::aiLiveCore.isInitialized) {
                                    val currentText = sentenceBuffer.toString()

                                    // Use SentenceDetector for smarter boundary detection (handles abbreviations)
                                    val isCompleteSentence = SentenceDetector.isCompleteSentence(
                                        currentText,
                                        minLength = 10,
                                        longPhraseThreshold = 80
                                    )

                                    // Debounce: Wait at least 300ms between TTS calls for smoother pacing
                                    val now = System.currentTimeMillis()
                                    val timeSinceLastTTS = now - lastTTSTime

                                    if (isCompleteSentence && timeSinceLastTTS > 300) {
                                        val sentenceToSpeak = currentText.trim()
                                        Log.d(TAG, "🔊 Text: Streaming TTS (${sentenceToSpeak.length} chars)")
                                        aiLiveCore.ttsManager.speakIncremental(sentenceToSpeak)
                                        sentenceBuffer.clear()
                                        lastTTSTime = now
                                    }
                                }
                            }
                        }
                } catch (e: CancellationException) {
                    // User cancelled - this is expected, rethrow to be handled in outer catch
                    Log.d(TAG, "Text command streaming cancelled by user")
                    throw e
                } catch (e: Exception) {
                    // Mid-stream error (network, model, etc.)
                    Log.e(TAG, "❌ Mid-stream error during text command generation", e)
                    throw e
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

                    // Add assistant turn to conversation history
                    if (::aiLiveCore.isInitialized) {
                        val assistantTurn = com.ailive.personality.ConversationTurn(
                            role = com.ailive.personality.Role.ASSISTANT,
                            content = responseBuilder.toString(),
                            timestamp = System.currentTimeMillis()
                        )
                        pendingAssistantTurn = assistantTurn
                        aiLiveCore.personalityEngine.addToHistory(assistantTurn)
                        Log.d(TAG, "✓ Added ASSISTANT turn to history")

                        // Clear pending turns (successful completion)
                        pendingUserTurn = null
                        pendingAssistantTurn = null
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

            } catch (e: CancellationException) {
                // User cancelled - pending turns will be removed by cancel button handler
                Log.d(TAG, "Text command cancelled - pending turns will be cleaned up")
                withContext(Dispatchers.Main) {
                    // Cancel button handler already does UI cleanup and history removal
                    // Nothing additional needed here
                }
            } catch (e: Exception) {
                // Mid-stream or generation error - rollback history
                Log.e(TAG, "❌ Text command streaming failed: ${e.message}", e)

                // Remove pending turns from history on error
                removeConversationTurns(pendingUserTurn, pendingAssistantTurn)
                pendingUserTurn = null
                pendingAssistantTurn = null

                withContext(Dispatchers.Main) {
                    statusIndicator.text = "● ERROR"
                    classificationResult.text = "Error: ${e.message}"
                    inferenceTime.text = ""
                    typingIndicator.visibility = View.GONE
                    btnCancelGeneration.visibility = View.GONE
                    btnSendCommand.isEnabled = true
                }
            } finally {
                // Always null the job reference to prevent memory leak
                generationJob = null
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
