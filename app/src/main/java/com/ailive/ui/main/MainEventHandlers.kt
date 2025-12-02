package com.ailive.ui.main

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ailive.settings.AISettings
import com.ailive.ui.MemoryActivity
import com.ailive.ui.ModelSettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Handles all user interaction events for MainActivity
 */
class MainEventHandlers(
    private val activity: AppCompatActivity,
    private val viewModel: MainViewModel,
    private val uiComponents: MainUIComponents,
    private val lifecycleScope: CoroutineScope
) {
    private val settings = AISettings(activity)
    
    /**
     * Setup all click listeners and event handlers
     */
    fun setupEventHandlers() {
        setupMicrophoneToggle()
        setupCameraToggle()
        setupCommandSending()
        setupSettingsButton()
        setupMemoryButton()
        setupImageCapture()
        setupCommandEditor()
    }
    
    private fun setupMicrophoneToggle() {
        uiComponents.btnToggleMic.setOnClickListener {
            viewModel.toggleMicrophone()
            updateMicrophoneUI()
        }
    }
    
    private fun setupCameraToggle() {
        uiComponents.btnToggleCamera.setOnClickListener {
            viewModel.toggleCamera()
            updateCameraUI()
        }
    }
    
    private fun setupCommandSending() {
        uiComponents.btnSendCommand.setOnClickListener {
            sendCommand()
        }
        
        uiComponents.btnCancelGeneration.setOnClickListener {
            cancelGeneration()
        }
    }
    
    private fun setupSettingsButton() {
        uiComponents.btnSettings.setOnClickListener {
            openSettings()
        }
    }
    
    private fun setupMemoryButton() {
        uiComponents.btnMemory.setOnClickListener {
            openMemory()
        }
    }
    
    private fun setupImageCapture() {
        uiComponents.btnCaptureImage.setOnClickListener {
            captureImage()
        }
    }
    
    private fun setupCommandEditor() {
        uiComponents.editTextCommand.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendCommand()
                true
            } else {
                false
            }
        }
    }
    
    private fun sendCommand() {
        val command = uiComponents.editTextCommand.text.toString().trim()
        if (command.isNotEmpty()) {
            viewModel.processTextCommand(command)
            uiComponents.editTextCommand.setText("")
        }
    }
    
    private fun cancelGeneration() {
        // Cancel any ongoing generation
        Toast.makeText(activity, "Generation cancelled", Toast.LENGTH_SHORT).show()
    }
    
    private fun openSettings() {
        val intent = Intent(activity, ModelSettingsActivity::class.java)
        activity.startActivity(intent)
    }
    
    private fun openMemory() {
        val intent = Intent(activity, MemoryActivity::class.java)
        activity.startActivity(intent)
    }
    
    private fun captureImage() {
        // Implement image capture logic
        Toast.makeText(activity, "Image capture clicked", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateMicrophoneUI() {
        val isMicEnabled = viewModel.isMicEnabled.value
        
        if (isMicEnabled) {
            uiComponents.btnToggleMic.text = "🎤 MIC ON"
            uiComponents.btnToggleMic.setBackgroundResource(R.drawable.button_toggle_on)
            uiComponents.editTextCommand.hint = "Say &quot;${settings.wakePhrase}&quot; or type..."
        } else {
            uiComponents.btnToggleMic.text = "🎤 MIC OFF"
            uiComponents.btnToggleMic.setBackgroundResource(R.drawable.button_toggle_off)
            uiComponents.editTextCommand.hint = "Type your command..."
        }
    }
    
    private fun updateCameraUI() {
        val isCameraEnabled = viewModel.isCameraEnabled.value
        
        if (isCameraEnabled) {
            uiComponents.btnToggleCamera.text = "📷 CAM ON"
            uiComponents.btnToggleCamera.setBackgroundResource(R.drawable.button_toggle_on)
            uiComponents.cameraPreview.visibility = View.VISIBLE
            uiComponents.appIconBackground.visibility = View.GONE
        } else {
            uiComponents.btnToggleCamera.text = "📷 CAM OFF"
            uiComponents.btnToggleCamera.setBackgroundResource(R.drawable.button_toggle_off)
            uiComponents.cameraPreview.visibility = View.GONE
            uiComponents.appIconBackground.visibility = View.VISIBLE
            uiComponents.confidenceText.text = ""
            uiComponents.inferenceTime.text = ""
        }
    }
    
    /**
     * Handle permissions result
     */
    fun handlePermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        // Handle permission results here
        val allGranted = grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
        
        if (allGranted) {
            proceedAfterPermissions()
        } else {
            showPermissionDeniedDialog()
        }
    }
    
    private fun proceedAfterPermissions() {
        // Check if model setup is needed
        if (::viewModel.isInitialized && viewModel::modelSetupDialog.isInitialized) {
            if (viewModel.modelSetupDialog.isSetupNeeded()) {
                showModelSetupDialog()
            } else {
                viewModel.startModels()
            }
        }
    }
    
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(activity)
            .setTitle("Permissions Required")
            .setMessage("Permissions required for full functionality")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", activity.packageName, null)
                }
                activity.startActivity(intent)
                activity.finish()
            }
            .setNegativeButton("Exit") { _, _ ->
                activity.finish()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showModelSetupDialog() {
        // Show model setup dialog
        lifecycleScope.launch {
            delay(500) // Small delay to ensure UI is ready
            viewModel.modelSetupDialog.showNameSetupDialog {
                viewModel.startModels()
            }
        }
    }
    
    /**
     * Handle activity results (file picker, image capture, etc.)
     */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            // File picker result
            1001 -> {
                if (resultCode == AppCompatActivity.RESULT_OK) {
                    data?.data?.let { uri ->
                        viewModel.modelSetupDialog.handleFilePickerResult(uri) {
                            // File handled successfully
                        }
                    }
                }
            }
            
            // Image capture result
            1002 -> {
                if (resultCode == AppCompatActivity.RESULT_OK) {
                    val imageBitmap = data?.extras?.get("data") as? Bitmap
                    if (imageBitmap != null) {
                        uiComponents.imageViewCaptured.setImageBitmap(imageBitmap)
                        uiComponents.imageViewCaptured.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
    
    /**
     * Setup observers for ViewModel state changes
     */
    fun setupObservers() {
        // Observe UI state
        viewModel.uiState.observe(activity) { state ->
            uiComponents.updateUI(state, settings)
        }
        
        // Observe component states
        viewModel.isMicEnabled.observe(activity) { enabled ->
            updateMicrophoneUI()
        }
        
        viewModel.isCameraEnabled.observe(activity) { enabled ->
            updateCameraUI()
        }
        
        viewModel.isInitialized.observe(activity) { initialized ->
            if (initialized) {
                // System is ready
                uiComponents.btnToggleMic.isEnabled = true
                uiComponents.btnToggleCamera.isEnabled = true
                uiComponents.btnSendCommand.isEnabled = true
            }
        }
    }
}