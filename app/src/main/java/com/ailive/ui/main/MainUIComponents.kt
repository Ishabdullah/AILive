package com.ailive.ui.main

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Container class for MainActivity UI components
 * Provides easy access to all UI elements
 */
class MainUIComponents {
    // Core views
    lateinit var cameraPreview: PreviewView
    lateinit var appIconBackground: ImageView
    lateinit var appTitle: TextView
    lateinit var classificationResult: TextView
    lateinit var responseScrollView: ScrollView
    lateinit var confidenceText: TextView
    lateinit var inferenceTime: TextView
    lateinit var statusIndicator: TextView
    lateinit var typingIndicator: TextView
    lateinit var imageViewCaptured: ImageView
    
    // Control buttons
    lateinit var btnToggleMic: Button
    lateinit var btnToggleCamera: Button
    lateinit var editTextCommand: EditText
    lateinit var btnSendCommand: Button
    lateinit var btnCancelGeneration: Button
    lateinit var btnSettings: Button
    lateinit var btnMemory: Button
    lateinit var btnCaptureImage: FloatingActionButton
    lateinit var btnToggleDashboard: FloatingActionButton
    
    // Dashboard
    lateinit var dashboardContainer: FrameLayout
    
    /**
     * Initialize all UI components by finding them in the given context
     */
    fun initialize(context: Context) {
        cameraPreview = context.findViewById(R.id.cameraPreview)
        appIconBackground = context.findViewById(R.id.appIconBackground)
        appTitle = context.findViewById(R.id.appTitle)
        classificationResult = context.findViewById(R.id.classificationResult)
        responseScrollView = context.findViewById(R.id.responseScrollView)
        confidenceText = context.findViewById(R.id.confidenceText)
        inferenceTime = context.findViewById(R.id.inferenceTime)
        statusIndicator = context.findViewById(R.id.statusIndicator)
        typingIndicator = context.findViewById(R.id.typingIndicator)
        imageViewCaptured = context.findViewById(R.id.imageViewCaptured)
        
        btnToggleMic = context.findViewById(R.id.btnToggleMic)
        btnToggleCamera = context.findViewById(R.id.btnToggleCamera)
        editTextCommand = context.findViewById(R.id.editTextCommand)
        btnSendCommand = context.findViewById(R.id.btnSendCommand)
        btnCancelGeneration = context.findViewById(R.id.btnCancelGeneration)
        btnSettings = context.findViewById(R.id.btnSettings)
        btnMemory = context.findViewById(R.id.btnMemory)
        btnCaptureImage = context.findViewById(R.id.btnCaptureImage)
        btnToggleDashboard = context.findViewById(R.id.btnToggleDashboard)
        dashboardContainer = context.findViewById(R.id.dashboardContainer)
    }
    
    /**
     * Update UI based on current state
     */
    fun updateUI(state: MainUiState, settings: AISettings) {
        classificationResult.text = state.classificationResult
        statusIndicator.text = "● ${state.statusIndicator}"
        confidenceText.text = state.confidenceText
        inferenceTime.text = state.inferenceTime
        typingIndicator.visibility = if (state.typingIndicatorVisible) View.VISIBLE else View.GONE
        btnCancelGeneration.visibility = if (state.cancelGenerationVisible) View.VISIBLE else View.GONE
        btnSendCommand.isEnabled = state.sendCommandEnabled
        
        appTitle.text = "${settings.aiName} (Vision + Audio)"
    }
    
    /**
     * Set initial UI state
     */
    fun setInitialState() {
        btnToggleMic.text = "🎤 MIC OFF"
        btnToggleCamera.text = "📷 CAM OFF"
        cameraPreview.visibility = View.GONE
        appIconBackground.visibility = View.VISIBLE
        typingIndicator.visibility = View.GONE
        btnCancelGeneration.visibility = View.GONE
        imageViewCaptured.visibility = View.GONE
    }
}

/**
 * Utility class for permission handling
 */
class PermissionManager {
    
    /**
     * Build list of permissions based on Android version
     */
    fun buildPermissionList(): List<String> {
        val permissionsToRequest = mutableListOf<String>()
        permissionsToRequest.add(android.Manifest.permission.CAMERA)
        permissionsToRequest.add(android.Manifest.permission.RECORD_AUDIO)
        
        // Location permissions for GPS/Location Awareness
        permissionsToRequest.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        permissionsToRequest.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        
        // Storage permissions based on Android version
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - granular media permissions
            permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10-12 - READ_EXTERNAL_STORAGE
            permissionsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            // Android 9 and below
            permissionsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            permissionsToRequest.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        
        return permissionsToRequest
    }
}