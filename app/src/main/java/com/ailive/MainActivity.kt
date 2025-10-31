mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
package com.ailive
import android.Manifest
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import android.content.Intent
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import android.os.Bundle
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import android.util.Log
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import android.view.View
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import android.widget.FrameLayout
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import android.widget.TextView
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import androidx.activity.result.ActivityResultLauncher
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import androidx.camera.view.PreviewView
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import com.ailive.ai.models.ModelManager
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import com.ailive.audio.AudioManager
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import com.ailive.audio.CommandRouter
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import com.ailive.audio.SpeechProcessor
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import com.ailive.audio.WakeWordDetector
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import com.ailive.camera.CameraManager
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import com.ailive.core.AILiveCore
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import com.ailive.settings.AISettings
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import com.ailive.testing.TestScenarios
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import com.ailive.ui.dashboard.DashboardFragment
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import com.ailive.ui.ModelSetupDialog
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import com.ailive.ai.llm.ModelDownloadManager
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
import kotlinx.coroutines.*
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
class MainActivity : AppCompatActivity() {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private val TAG = "MainActivity"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var settings: AISettings
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var aiLiveCore: AILiveCore
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var modelManager: ModelManager
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var cameraManager: CameraManager
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    // Phase 2.3: Audio components
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var audioManager: AudioManager
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var speechProcessor: SpeechProcessor
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var wakeWordDetector: WakeWordDetector
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var commandRouter: CommandRouter
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var cameraPreview: PreviewView
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var appTitle: TextView
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var classificationResult: TextView
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var confidenceText: TextView
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var inferenceTime: TextView
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var statusIndicator: TextView
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    // Manual control UI components
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var btnToggleMic: android.widget.Button
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var btnToggleCamera: android.widget.Button
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var editTextCommand: android.widget.EditText
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var btnSendCommand: android.widget.Button
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var btnToggleDashboard: FloatingActionButton
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var dashboardContainer: FrameLayout
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    // Dashboard
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private var dashboardFragment: DashboardFragment? = null
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private var isDashboardVisible = false
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    // Model Setup Dialog (Phase 7.3)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var modelSetupDialog: ModelSetupDialog
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var modelDownloadManager: ModelDownloadManager
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    // Phase 7.5: Modern ActivityResultLauncher for file picker (replaces deprecated startActivityForResult)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private var filePickerOnComplete: (() -> Unit)? = null
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private var callbackCount = 0
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private var isInitialized = false
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private var isListeningForWakeWord = false
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private var isMicEnabled = false
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private var isCameraEnabled = false
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    companion object {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        private const val REQUEST_CODE_PERMISSIONS = 10
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        private val REQUIRED_PERMISSIONS = arrayOf(
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Manifest.permission.CAMERA,
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Manifest.permission.RECORD_AUDIO
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        )
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    override fun onCreate(savedInstanceState: Bundle?) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        super.onCreate(savedInstanceState)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 101);
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
}
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // Phase 7.5: CRITICAL - Register ActivityResultLauncher BEFORE any other lifecycle operations
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // This must be done before setContentView() or the activity enters RESUMED state
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            if (result.resultCode == RESULT_OK) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                result.data?.data?.let { uri ->
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    Log.i(TAG, "File picker selected: $uri")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    val onComplete = filePickerOnComplete
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    if (onComplete != null) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        modelSetupDialog.handleFilePickerResult(uri, onComplete)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        filePickerOnComplete = null
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // Initialize settings (setup wizard temporarily disabled)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        settings = AISettings(this)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // TODO: Re-enable setup wizard after fixing resource issues
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // Bypass setup for now - use default AI name
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        if (!settings.isSetupComplete) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.i(TAG, "Setup bypassed - using defaults")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            settings.isSetupComplete = true  // Mark as complete to prevent redirect
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        Log.i(TAG, "=== ${settings.aiName} Starting ===")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        setContentView(R.layout.activity_main)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // Initialize UI
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        cameraPreview = findViewById(R.id.cameraPreview)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        appTitle = findViewById(R.id.appTitle)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        classificationResult = findViewById(R.id.classificationResult)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        confidenceText = findViewById(R.id.confidenceText)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        inferenceTime = findViewById(R.id.inferenceTime)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        statusIndicator = findViewById(R.id.statusIndicator)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // Initialize manual control buttons
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        btnToggleMic = findViewById(R.id.btnToggleMic)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        btnToggleCamera = findViewById(R.id.btnToggleCamera)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        editTextCommand = findViewById(R.id.editTextCommand)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        btnSendCommand = findViewById(R.id.btnSendCommand)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        btnToggleDashboard = findViewById(R.id.btnToggleDashboard)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        dashboardContainer = findViewById(R.id.dashboardContainer)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // Set up button click listeners
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        setupManualControls()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        setupDashboard()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // Use custom AI name in UI
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        appTitle.text = "${settings.aiName} (Vision + Audio)"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        statusIndicator.text = "● INITIALIZING..."
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        classificationResult.text = "Initializing ${settings.aiName}..."
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // Phase 7.3/7.5: Initialize model download system
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        modelDownloadManager = ModelDownloadManager(this)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        modelSetupDialog = ModelSetupDialog(this, modelDownloadManager, filePickerLauncher)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // Check if model setup is needed and show dialog
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        if (modelSetupDialog.isSetupNeeded()) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.i(TAG, "Model setup needed - showing dialog")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            statusIndicator.text = "● MODEL SETUP REQUIRED"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            classificationResult.text = "Please download or import an AI model"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            filePickerOnComplete = {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                Log.i(TAG, "Model setup complete, continuing initialization")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                continueInitialization()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            modelSetupDialog.showFirstRunDialog {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                Log.i(TAG, "Model setup complete, continuing initialization")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                continueInitialization()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            // Don't proceed with initialization until model is ready
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            return
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // Initialize AILiveCore EARLY (before permissions) to avoid lifecycle issues
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        try {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.i(TAG, "=== Initializing ${settings.aiName} Core ===")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            aiLiveCore = AILiveCore(applicationContext, this)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            aiLiveCore.initialize()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            aiLiveCore.start()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.i(TAG, "✓ Phase 1: Agents operational")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        } catch (e: Exception) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.e(TAG, "AILive Core init failed", e)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            statusIndicator.text = "● CORE ERROR"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            classificationResult.text = "Error: ${e.message}"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            return
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // Now check permissions
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        statusIndicator.text = "● CHECKING PERMISSIONS..."
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        if (allPermissionsGranted()) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.i(TAG, "✓ Permissions granted")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            startModels()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        } else {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.i(TAG, "Requesting permissions...")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            statusIndicator.text = "● REQUESTING PERMISSIONS..."
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            classificationResult.text = "Please allow camera and microphone access"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            ActivityCompat.requestPermissions(
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                this,
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                REQUIRED_PERMISSIONS,
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                REQUEST_CODE_PERMISSIONS
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            )
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    /**
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     * Continue initialization after model setup is complete
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     * This runs the same code that was in onCreate() after model check
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     */
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private fun continueInitialization() {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // Initialize AILiveCore EARLY (before permissions) to avoid lifecycle issues
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        try {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.i(TAG, "=== Initializing ${settings.aiName} Core ===")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            aiLiveCore = AILiveCore(applicationContext, this)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            aiLiveCore.initialize()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            aiLiveCore.start()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.i(TAG, "✓ Phase 1: Agents operational")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        } catch (e: Exception) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.e(TAG, "AILive Core init failed", e)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            statusIndicator.text = "● CORE ERROR"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            classificationResult.text = "Error: ${e.message}"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            return
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // Now check permissions
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        statusIndicator.text = "● CHECKING PERMISSIONS..."
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        if (allPermissionsGranted()) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.i(TAG, "✓ Permissions granted")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            startModels()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        } else {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.i(TAG, "Requesting permissions...")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            statusIndicator.text = "● REQUESTING PERMISSIONS..."
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            classificationResult.text = "Please allow camera and microphone access"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            ActivityCompat.requestPermissions(
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                this,
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                REQUIRED_PERMISSIONS,
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                REQUEST_CODE_PERMISSIONS
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            )
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    override fun onRequestPermissionsResult(
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        requestCode: Int,
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        permissions: Array<String>,
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        grantResults: IntArray
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    ) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                Log.i(TAG, "✓ All permissions granted")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                startModels()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            } else {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                Log.e(TAG, "✗ Permissions denied")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                statusIndicator.text = "● PERMISSION DENIED"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                classificationResult.text = "Camera and microphone permissions required"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                finish()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private fun startModels() {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        if (isInitialized) return
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        isInitialized = true
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        try {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            statusIndicator.text = "● LOADING AI MODEL..."
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            classificationResult.text = "Loading TensorFlow Lite..."
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            // Phase 2.1: TensorFlow Lite
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            modelManager = ModelManager(applicationContext)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            CoroutineScope(Dispatchers.Default).launch {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                try {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    modelManager.initialize()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    withContext(Dispatchers.Main) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        Log.i(TAG, "✓ Phase 2.1: TensorFlow ready")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        statusIndicator.text = "● STARTING CAMERA..."
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        classificationResult.text = "Initializing camera..."
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        delay(500)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        initializeCamera()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        // Phase 2.3: Initialize audio after camera
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        delay(500)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        initializeAudio()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                } catch (e: Exception) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    withContext(Dispatchers.Main) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        Log.e(TAG, "TensorFlow init failed", e)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        statusIndicator.text = "● AI MODEL ERROR"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        classificationResult.text = "Error: ${e.message}"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            // Run tests
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            CoroutineScope(Dispatchers.Main).launch {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                delay(1000)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                val tests = TestScenarios(aiLiveCore)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                tests.runAllTests()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        } catch (e: Exception) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.e(TAG, "Init failed", e)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            statusIndicator.text = "● INIT ERROR"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            classificationResult.text = "Error: ${e.message}"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private fun initializeCamera() {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        try {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.i(TAG, "=== Starting Camera ===")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            cameraManager = CameraManager(
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                context = applicationContext,
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                lifecycleOwner = this,
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                modelManager = modelManager
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            )
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            cameraManager.onClassificationResult = { label, confidence, time ->
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                callbackCount++
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                Log.i(TAG, ">>> Classification #$callbackCount: $label")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                updateUI(label, confidence, time)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            cameraManager.startCamera(cameraPreview)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            // PHASE 5: Register VisionAnalysisTool now that camera is ready
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            val visionTool = com.ailive.personality.tools.VisionAnalysisTool(
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                modelManager = modelManager,
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                cameraManager = cameraManager
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            )
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            aiLiveCore.personalityEngine.registerTool(visionTool)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.i(TAG, "✓ VisionAnalysisTool registered")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            // PHASE 5 Part 3: PatternAnalysisTool and FeedbackTrackingTool registered in AILiveCore
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.i(TAG, "✓ Camera started")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            statusIndicator.text = "● ANALYZING..."
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            classificationResult.text = "Point at objects"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            // Update camera button state
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            isCameraEnabled = true
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            btnToggleCamera.text = "📷 CAM"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            btnToggleCamera.backgroundTintList = getColorStateList(android.R.color.holo_green_dark)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            // Debug counter
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            var seconds = 0
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            CoroutineScope(Dispatchers.Main).launch {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                while (true) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    delay(1000)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    seconds++
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    if (callbackCount == 0) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        statusIndicator.text = "● WAITING ${seconds}s (0 results)"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        } catch (e: Exception) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.e(TAG, "Camera failed", e)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            statusIndicator.text = "● CAMERA ERROR"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            classificationResult.text = "Camera error: ${e.message}"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private fun updateUI(label: String, confidence: Float, time: Long) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        runOnUiThread {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            classificationResult.text = label
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            confidenceText.text = "Confidence: ${(confidence * 100).toInt()}%"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            inferenceTime.text = "${time}ms | Frame #$callbackCount"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            statusIndicator.text = "● LIVE ($callbackCount frames)"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            val color = when {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                confidence > 0.7f -> getColor(android.R.color.holo_green_light)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                confidence > 0.4f -> getColor(android.R.color.holo_orange_light)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                else -> getColor(android.R.color.holo_red_light)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            classificationResult.setTextColor(color)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    /**
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     * Phase 2.3: Initialize audio pipeline
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     */
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private fun initializeAudio() {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        try {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.i(TAG, "=== Initializing Audio Pipeline ===")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            // Create audio components
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            speechProcessor = SpeechProcessor(applicationContext)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            wakeWordDetector = WakeWordDetector(settings.wakePhrase, aiLiveCore.ttsManager)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            commandRouter = CommandRouter(aiLiveCore)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            // Initialize speech processor
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            if (!speechProcessor.initialize()) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                Log.e(TAG, "❌ Speech processor failed to initialize")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                return
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.i(TAG, "✓ Speech processor ready")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            // Configure wake word detector
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            wakeWordDetector.onWakeWordDetected = {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                runOnUiThread {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    onWakeWordDetected()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            // Configure speech processor callbacks
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            speechProcessor.onPartialResult = { text ->
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                runOnUiThread {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    // Show partial transcription in text field
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    editTextCommand.setText(text)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    // Check for wake word in partial results
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    if (isListeningForWakeWord) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        wakeWordDetector.processText(text)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            speechProcessor.onFinalResult = { text ->
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                runOnUiThread {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    // Show final transcription in text field
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    editTextCommand.setText(text)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    Log.i(TAG, "Final transcription: '$text'")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    if (isListeningForWakeWord) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        // Check for wake word
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        if (wakeWordDetector.processText(text)) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                            // Wake word detected - handled by detector callback
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        } else {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                            // Not wake word, restart listening
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                            restartWakeWordListening()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    } else {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        // Process as command
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        processVoiceCommand(text)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            speechProcessor.onReadyForSpeech = {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                runOnUiThread {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    if (isListeningForWakeWord) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        statusIndicator.text = "● LISTENING"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    } else {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        statusIndicator.text = "● COMMAND"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            speechProcessor.onError = { error ->
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                runOnUiThread {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    Log.w(TAG, "Speech error: $error")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    // Auto-retry on timeout or no match if mic is enabled
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    if (isMicEnabled && isListeningForWakeWord) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        restartWakeWordListening()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    } else if (isMicEnabled && !isListeningForWakeWord) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        isListeningForWakeWord = true
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        restartWakeWordListening()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            // Configure command router
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            commandRouter.onResponse = { response ->
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                runOnUiThread {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    classificationResult.text = response
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    confidenceText.text = "Voice Command Response"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    // Return to wake word listening AFTER TTS finishes speaking (only if mic is still enabled)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    CoroutineScope(Dispatchers.Main).launch {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        // Wait for TTS to actually finish speaking (not just 3 seconds!)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        aiLiveCore.ttsManager.state.collect { ttsState ->
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                            if (ttsState == com.ailive.audio.TTSManager.TTSState.READY) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                                // TTS is done, safe to restart listening (but only if mic is enabled)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                                delay(500) // Small buffer to release audio resources
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                                if (isMicEnabled) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                                    isListeningForWakeWord = true
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                                    restartWakeWordListening()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                                }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                                return@collect // Exit collection after restarting
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            // Start listening for wake word
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            isListeningForWakeWord = true
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            isMicEnabled = true
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            startWakeWordListening()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            // Update mic button state
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            runOnUiThread {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                btnToggleMic.text = "🎤 MIC"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                btnToggleMic.backgroundTintList = getColorStateList(android.R.color.holo_green_dark)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            // Monitor TTS status
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            CoroutineScope(Dispatchers.Main).launch {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                aiLiveCore.ttsManager.state.collect { ttsState ->
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    when (ttsState) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        com.ailive.audio.TTSManager.TTSState.SPEAKING -> {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                            if (!isListeningForWakeWord) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                                statusIndicator.text = "● SPEAKING"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        else -> {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                            // Don't update if we're already showing another status
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.i(TAG, "✓ Phase 2.3: Audio pipeline operational")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.i(TAG, "✓ Phase 2.4: TTS ready")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        } catch (e: Exception) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.e(TAG, "❌ Audio init failed", e)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            statusIndicator.text = "● ERROR"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    /**
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     * Start listening for wake word
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     */
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private fun startWakeWordListening() {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        editTextCommand.setText("")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        editTextCommand.hint = "Say \"${settings.wakePhrase}\" or type..."
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        speechProcessor.startListening(continuous = false)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        Log.i(TAG, "Listening for wake word...")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    /**
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     * Restart wake word listening after timeout (only if mic is enabled)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     */
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private fun restartWakeWordListening() {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        CoroutineScope(Dispatchers.Main).launch {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            delay(500)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            if (isListeningForWakeWord && isMicEnabled) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                startWakeWordListening()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    /**
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     * Handle wake word detection
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     */
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private fun onWakeWordDetected() {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        Log.i(TAG, "🎯 Wake word detected!")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        isListeningForWakeWord = false
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        editTextCommand.setText("")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        editTextCommand.hint = "Listening for command..."
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        statusIndicator.text = "● COMMAND"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // Start listening for command
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        speechProcessor.stopListening()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        CoroutineScope(Dispatchers.Main).launch {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            delay(500)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            speechProcessor.startListening(continuous = false)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    /**
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     * Process voice command
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     */
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private fun processVoiceCommand(command: String) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        Log.i(TAG, "Processing command: '$command'")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        statusIndicator.text = "● PROCESSING"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        CoroutineScope(Dispatchers.Default).launch {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            commandRouter.processCommand(command)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    /**
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     * Set up manual control buttons for testing and debugging
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     */
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private fun setupManualControls() {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // Microphone toggle button
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        btnToggleMic.setOnClickListener {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            if (isMicEnabled) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                // Turn off microphone - FIXED: actually stop listening
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                if (::speechProcessor.isInitialized) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    speechProcessor.stopListening()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    isListeningForWakeWord = false
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                isMicEnabled = false
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                btnToggleMic.text = "🎤 MIC"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                btnToggleMic.backgroundTintList = getColorStateList(android.R.color.holo_red_dark)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                statusIndicator.text = "● MIC OFF"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                editTextCommand.hint = "Type command..."
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                Log.i(TAG, "🎤 Microphone manually disabled")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            } else {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                // Turn on microphone
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                if (::speechProcessor.isInitialized) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    isListeningForWakeWord = true
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    isMicEnabled = true
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    startWakeWordListening()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                } else {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    isMicEnabled = true
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                btnToggleMic.text = "🎤 MIC"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                btnToggleMic.backgroundTintList = getColorStateList(android.R.color.holo_green_dark)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                Log.i(TAG, "🎤 Microphone manually enabled")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // Camera toggle button
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        btnToggleCamera.setOnClickListener {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            if (isCameraEnabled) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                // Turn off camera
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                if (::cameraManager.isInitialized) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    cameraManager.stopCamera()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                // Hide the preview to show black screen (FIXED: visibility instead of background)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                cameraPreview.visibility = android.view.View.INVISIBLE
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                cameraPreview.setBackgroundColor(android.graphics.Color.BLACK)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                isCameraEnabled = false
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                btnToggleCamera.text = "📷 CAM"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                btnToggleCamera.backgroundTintList = getColorStateList(android.R.color.holo_red_dark)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                statusIndicator.text = "● CAM OFF"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                classificationResult.text = "Camera off"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                confidenceText.text = ""
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                inferenceTime.text = ""
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                Log.i(TAG, "📷 Camera manually disabled")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            } else {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                // Turn on camera
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                // Show the preview to display camera feed (FIXED: visibility instead of background)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                cameraPreview.visibility = android.view.View.VISIBLE
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                cameraPreview.setBackgroundColor(android.graphics.Color.TRANSPARENT)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                if (::cameraManager.isInitialized) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    cameraManager.startCamera(cameraPreview)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                isCameraEnabled = true
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                btnToggleCamera.text = "📷 CAM"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                btnToggleCamera.backgroundTintList = getColorStateList(android.R.color.holo_green_dark)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                statusIndicator.text = "● ANALYZING"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                classificationResult.text = "Point at objects"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                Log.i(TAG, "📷 Camera manually enabled")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // Send command button
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        btnSendCommand.setOnClickListener {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            val command = editTextCommand.text.toString().trim()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            if (command.isNotEmpty()) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                processTextCommand(command)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                editTextCommand.setText("")  // FIXED: Clear text field after sending
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // Handle enter key in text field
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        editTextCommand.setOnEditorActionListener { _, actionId, _ ->
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                val command = editTextCommand.text.toString().trim()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                if (command.isNotEmpty()) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    processTextCommand(command)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    editTextCommand.setText("")  // FIXED: Clear text field after sending
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                true
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            } else {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                false
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    /**
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     * Process text command (bypassing voice recognition)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     */
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private fun processTextCommand(command: String) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        Log.i(TAG, "📝 Processing text command: '$command'")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        statusIndicator.text = "● PROCESSING"
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        // Process through command router
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        CoroutineScope(Dispatchers.Default).launch {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            commandRouter.processCommand(command)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    /**
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     * Set up dashboard toggle
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     */
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private fun setupDashboard() {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        btnToggleDashboard.setOnClickListener {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            toggleDashboard()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    /**
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     * Toggle dashboard visibility
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
     */
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    private fun toggleDashboard() {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        isDashboardVisible = !isDashboardVisible
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        if (isDashboardVisible) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            // Show dashboard
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            dashboardContainer.visibility = View.VISIBLE
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            // Create dashboard fragment if not exists
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            if (dashboardFragment == null) {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                dashboardFragment = DashboardFragment().apply {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    aiLiveCore = this@MainActivity.aiLiveCore
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                supportFragmentManager.beginTransaction()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    .replace(R.id.dashboardContainer, dashboardFragment!!)
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
                    .commit()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.i(TAG, "📊 Dashboard opened")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        } else {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            // Hide dashboard
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            dashboardContainer.visibility = View.GONE
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
            Log.i(TAG, "📊 Dashboard closed")
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat

mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    override fun onDestroy() {
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        super.onDestroy()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        if (::speechProcessor.isInitialized) speechProcessor.release()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        if (::cameraManager.isInitialized) cameraManager.stopCamera()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        if (::modelManager.isInitialized) modelManager.close()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        if (::aiLiveCore.isInitialized) aiLiveCore.stop()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
        if (::modelSetupDialog.isInitialized) modelSetupDialog.cleanup()
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
    }
mport android.content.pm.PackageManager
mport androidx.core.app.ActivityCompat
mport androidx.core.content.ContextCompat
}
