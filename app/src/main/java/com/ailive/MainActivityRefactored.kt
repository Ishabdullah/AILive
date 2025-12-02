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
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ailive.ai.llm.SapientHRMManager
import com.ailive.ai.orchestrator.MultiModelOrchestrator
import com.ailive.ai.knowledge.KnowledgeGraphManager
import com.ailive.ai.vector.VectorCoreManager
import com.ailive.ai.memory.MemoryConsolidationManager
import com.ailive.ai.vision.VisionManager
import com.ailive.ui.main.MainViewModel
import com.ailive.ui.main.MainUIComponents
import com.ailive.ui.main.MainEventHandlers
import com.ailive.ui.dashboard.DashboardFragment
import com.ailive.ui.ModelSetupDialog
import com.ailive.ai.llm.ModelDownloadManager
import com.ailive.settings.AISettings
import kotlinx.coroutines.launch

/**
 * Refactored MainActivity - Clean, maintainable, and follows MVVM architecture
 * Delegates business logic to ViewModel and UI interactions to dedicated handlers
 */
class MainActivityRefactored : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CAMERA_PERMISSION = 1001
        private const val REQUEST_AUDIO_PERMISSION = 1002
        private const val REQUEST_STORAGE_PERMISSION = 1003
    }
    
    // Core components
    private lateinit var viewModel: MainViewModel
    private lateinit var uiComponents: MainUIComponents
    private lateinit var eventHandlers: MainEventHandlers
    private lateinit var settings: AISettings
    
    // Advanced AI systems
    private lateinit var sapientHRMManager: SapientHRMManager
    private lateinit var multiModelOrchestrator: MultiModelOrchestrator
    private lateinit var knowledgeGraphManager: KnowledgeGraphManager
    private lateinit var vectorCoreManager: VectorCoreManager
    private lateinit var memoryConsolidationManager: MemoryConsolidationManager
    
    // Dashboard
    private var dashboardFragment: DashboardFragment? = null
    private var isDashboardVisible = false
    
    // Permission launchers
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var audioPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var storagePermissionLauncher: ActivityResultLauncher<Array<String>>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeSettings()
        initializeViewModel()
        initializeUI()
        initializePermissionLaunchers()
        initializeAdvancedSystems()
        
        setupObservers()
        setupUI()
        
        // Start initialization
        lifecycleScope.launch {
            viewModel.startModels()
        }
    }
    
    private fun initializeSettings() {
        settings = AISettings(this)
    }
    
    private fun initializeViewModel() {
        viewModel = MainViewModel(application)
    }
    
    private fun initializeUI() {
        uiComponents = MainUIComponents().apply {
            initialize(this@MainActivityRefactored)
        }
        
        eventHandlers = MainEventHandlers(
            activity = this,
            viewModel = viewModel,
            uiComponents = uiComponents,
            lifecycleScope = lifecycleScope
        )
    }
    
    private fun initializePermissionLaunchers() {
        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                viewModel.enableCamera()
            } else {
                showPermissionRationale("Camera", "Camera access is required for vision features.")
            }
        }
        
        audioPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                viewModel.enableMicrophone()
            } else {
                showPermissionRationale("Microphone", "Microphone access is required for voice interaction.")
            }
        }
        
        storagePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val storageGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
            val writeStorageGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true
            
            if (storageGranted && writeStorageGranted) {
                viewModel.enableStorageAccess()
            } else {
                showPermissionRationale("Storage", "Storage access is required for model downloads and file operations.")
            }
        }
    }
    
    private fun initializeAdvancedSystems() {
        lifecycleScope.launch {
            try {
                // Initialize Sapient HRM Manager
                sapientHRMManager = SapientHRMManager(this@MainActivityRefactored)
                val sapientInitialized = sapientHRMManager.initialize()
                Log.d(TAG, "Sapient HRM initialized: $sapientInitialized")
                
                // Initialize Knowledge Graph Manager
                knowledgeGraphManager = KnowledgeGraphManager(this@MainActivityRefactored)
                val knowledgeGraphInitialized = knowledgeGraphManager.initialize()
                Log.d(TAG, "Knowledge Graph initialized: $knowledgeGraphInitialized")
                
                // Initialize VectorCore Manager
                vectorCoreManager = VectorCoreManager(this@MainActivityRefactored)
                val vectorCoreInitialized = vectorCoreManager.initialize()
                Log.d(TAG, "VectorCore initialized: $vectorCoreInitialized")
                
                // Initialize Memory Consolidation Manager
                memoryConsolidationManager = MemoryConsolidationManager(this@MainActivityRefactored)
                val memoryInitialized = memoryConsolidationManager.initialize()
                Log.d(TAG, "Memory Consolidation initialized: $memoryInitialized")
                
                // Initialize Multi-Model Orchestrator
                if (sapientInitialized) {
                    multiModelOrchestrator = MultiModelOrchestrator(this@MainActivityRefactored)
                    // The orchestrator will be initialized when other model managers are ready
                    Log.d(TAG, "Multi-Model Orchestrator created")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing advanced systems", e)
                Toast.makeText(
                    this@MainActivityRefactored,
                    "Error initializing AI systems: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun setupObservers() {
        // Observe ViewModel state
        viewModel.uiState.observe(this) { state ->
            uiComponents.updateUI(state, settings)
        }
        
        viewModel.isInitialized.observe(this) { initialized ->
            if (initialized) {
                Log.d(TAG, "MainViewModel initialized successfully")
            }
        }
        
        viewModel.isMicEnabled.observe(this) { enabled ->
            updateMicrophoneUI(enabled)
        }
        
        viewModel.isCameraEnabled.observe(this) { enabled ->
            updateCameraUI(enabled)
        }
        
        viewModel.isListeningForWakeWord.observe(this) { listening ->
            updateWakeWordUI(listening)
        }
    }
    
    private fun setupUI() {
        // Set initial UI state
        uiComponents.setInitialState()
        
        // Setup event handlers
        eventHandlers.setupEventHandlers()
        
        // Setup dashboard
        setupDashboard()
        
        // Setup permissions
        checkAndRequestPermissions()
    }
    
    private fun setupDashboard() {
        dashboardFragment = DashboardFragment()
        
        uiComponents.btnToggleDashboard.setOnClickListener {
            toggleDashboard()
        }
    }
    
    private fun toggleDashboard() {
        if (isDashboardVisible) {
            supportFragmentManager.beginTransaction()
                .remove(dashboardFragment!!)
                .commit()
            isDashboardVisible = false
        } else {
            supportFragmentManager.beginTransaction()
                .replace(R.id.dashboardContainer, dashboardFragment!!)
                .commit()
            isDashboardVisible = true
        }
    }
    
    private fun checkAndRequestPermissions() {
        // Check and request camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        
        // Check and request audio permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        
        // Check and request storage permissions
        val storagePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        
        val hasStoragePermissions = storagePermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        
        if (!hasStoragePermissions) {
            storagePermissionLauncher.launch(storagePermissions)
        }
    }
    
    private fun showPermissionRationale(permission: String, rationale: String) {
        AlertDialog.Builder(this)
            .setTitle("$permission Permission Required")
            .setMessage(rationale + "\n\nGo to Settings to grant the permission.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun updateMicrophoneUI(enabled: Boolean) {
        uiComponents.btnToggleMic.text = if (enabled) "🎤 MIC ON" else "🎤 MIC OFF"
        uiComponents.btnToggleMic.setBackgroundResource(
            if (enabled) R.drawable.button_toggle_on else R.drawable.button_toggle_off
        )
        
        if (enabled) {
            uiComponents.editTextCommand.hint = "Say &quot;${settings.wakePhrase}&quot; or type..."
        } else {
            uiComponents.editTextCommand.hint = "Type your command..."
        }
    }
    
    private fun updateCameraUI(enabled: Boolean) {
        uiComponents.btnToggleCamera.text = if (enabled) "📷 CAMERA ON" else "📷 CAMERA OFF"
        uiComponents.btnToggleCamera.setBackgroundResource(
            if (enabled) R.drawable.button_toggle_on else R.drawable.button_toggle_off
        )
        
        uiComponents.cameraPreview.visibility = if (enabled) View.VISIBLE else View.GONE
        uiComponents.btnCaptureImage.visibility = if (enabled) View.VISIBLE else View.GONE
    }
    
    private fun updateWakeWordUI(listening: Boolean) {
        val statusText = if (listening) "👂 Listening for &quot;${settings.wakePhrase}&quot;..." else "Ready"
        uiComponents.statusIndicator.text = "● $statusText"
    }
    
    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        viewModel.onPause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        viewModel.onDestroy()
        
        // Cleanup advanced systems
        lifecycleScope.launch {
            try {
                if (::memoryConsolidationManager.isInitialized) {
                    // Save any pending memory consolidations
                    memoryConsolidationManager.saveDatabase()
                }
                
                if (::vectorCoreManager.isInitialized) {
                    // Save vector database
                    vectorCoreManager.saveDatabase()
                }
                
                if (::knowledgeGraphManager.isInitialized) {
                    // Save knowledge graph
                    knowledgeGraphManager.saveKnowledgeGraph()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving data on destroy", e)
            }
        }
    }
    
    // Public methods for external access
    
    /**
     * Get the multi-model orchestrator for external use
     */
    fun getMultiModelOrchestrator(): MultiModelOrchestrator? {
        return if (::multiModelOrchestrator.isInitialized) {
            multiModelOrchestrator
        } else {
            null
        }
    }
    
    /**
     * Get the Sapient HRM manager for external use
     */
    fun getSapientHRMManager(): SapientHRMManager? {
        return if (::sapientHRMManager.isInitialized) {
            sapientHRMManager
        } else {
            null
        }
    }
    
    /**
     * Process a multimodal request
     */
    fun processMultimodalRequest(
        text: String,
        imageBitmap: Bitmap,
        context: Map<String, Any> = emptyMap()
    ) {
        lifecycleScope.launch {
            try {
                val orchestrator = getMultiModelOrchestrator()
                if (orchestrator != null) {
                    val response = orchestrator.processMultimodalRequest(text, imageBitmap, context)
                    displayResponse(response.content)
                } else {
                    displayResponse("Multi-model orchestrator not available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing multimodal request", e)
                displayResponse("Error: ${e.message}")
            }
        }
    }
    
    private fun displayResponse(response: String) {
        // Update UI with response
        uiComponents.classificationResult.text = response
    }
    
    /**
     * Get system statistics for debugging
     */
    fun getSystemStatistics(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()
        
        if (::knowledgeGraphManager.isInitialized) {
            stats["knowledgeGraph"] = knowledgeGraphManager.statistics.value
        }
        
        if (::vectorCoreManager.isInitialized) {
            stats["vectorCore"] = vectorCoreManager.getStatistics()
        }
        
        if (::memoryConsolidationManager.isInitialized) {
            stats["memoryConsolidation"] = memoryConsolidationManager.getMemoryStatistics()
        }
        
        if (::multiModelOrchestrator.isInitialized) {
            stats["multiModelOrchestrator"] = multiModelOrchestrator.getStatistics()
        }
        
        return stats
    }
}