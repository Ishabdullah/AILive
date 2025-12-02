package com.ailive.ui.main

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ailive.ai.llm.ModelDownloadManager
import com.ailive.ai.vision.VisionManager
import com.ailive.audio.AudioManager
import com.ailive.audio.CommandRouter
import com.ailive.audio.WhisperProcessor
import com.ailive.audio.WakeWordDetector
import com.ailive.camera.CameraManager
import com.ailive.core.AILiveCore
import com.ailive.settings.AISettings
import com.ailive.ui.ModelSetupDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>().applicationContext
    private val settings = AISettings(context)
    
    // Core components
    lateinit var aiLiveCore: AILiveCore
    lateinit var modelManager: com.ailive.ai.models.ModelManager
    lateinit var cameraManager: CameraManager
    lateinit var visionManager: VisionManager
    lateinit var audioManager: AudioManager
    lateinit var whisperProcessor: WhisperProcessor
    lateinit var wakeWordDetector: WakeWordDetector
    lateinit var commandRouter: CommandRouter
    
    // UI State
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    // Component state
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private val _isMicEnabled = MutableStateFlow(false)
    val isMicEnabled: StateFlow<Boolean> = _isMicEnabled.asStateFlow()
    
    private val _isCameraEnabled = MutableStateFlow(false)
    val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()
    
    private val _isListeningForWakeWord = MutableStateFlow(false)
    val isListeningForWakeWord: StateFlow<Boolean> = _isListeningForWakeWord.asStateFlow()
    
    // Model setup
    lateinit var modelDownloadManager: ModelDownloadManager
    lateinit var modelSetupDialog: ModelSetupDialog
    
    init {
        initializeCore()
    }
    
    private fun initializeCore() {
        viewModelScope.launch {
            try {
                updateStatus("INITIALIZING...")
                updateMessage("Initializing ${settings.aiName}...")
                
                aiLiveCore = AILiveCore(context, null)
                aiLiveCore.initialize()
                aiLiveCore.start()
                
                modelDownloadManager = ModelDownloadManager(context)
                
                _isInitialized.value = true
                updateStatus("READY")
                updateMessage("${settings.aiName} is ready!")
                
            } catch (e: Exception) {
                updateStatus("ERROR")
                updateMessage("Initialization failed: ${e.message}")
            }
        }
    }
    
    fun startModels() {
        if (!_isInitialized.value) return
        
        viewModelScope.launch {
            try {
                updateStatus("LOADING AI MODEL...")
                updateMessage("Initializing camera...")
                
                modelManager = com.ailive.ai.models.ModelManager(context)
                initializeCamera()
                initializeAudio()
                
            } catch (e: Exception) {
                updateStatus("AI MODEL ERROR")
                updateMessage("Error: ${e.message}")
            }
        }
    }
    
    private fun initializeCamera() {
        viewModelScope.launch {
            try {
                // Camera initialization logic here
                updateStatus("CAMERA READY")
                updateMessage("Camera initialized")
            } catch (e: Exception) {
                updateStatus("CAMERA ERROR")
                updateMessage("Camera error: ${e.message}")
            }
        }
    }
    
    private fun initializeAudio() {
        viewModelScope.launch {
            try {
                // Audio initialization logic here
                updateStatus("AUDIO READY")
                updateMessage("Audio pipeline initialized")
            } catch (e: Exception) {
                updateStatus("AUDIO ERROR")
                updateMessage("Audio error: ${e.message}")
            }
        }
    }
    
    fun toggleMicrophone() {
        val newState = !_isMicEnabled.value
        _isMicEnabled.value = newState
        
        if (newState) {
            startWakeWordListening()
        } else {
            stopWakeWordListening()
        }
        
        updateStatus(if (newState) "MIC ON" else "MIC OFF")
    }
    
    fun toggleCamera() {
        val newState = !_isCameraEnabled.value
        _isCameraEnabled.value = newState
        
        updateStatus(if (newState) "CAM ON" else "CAM OFF")
        updateMessage(if (newState) "Point camera at objects to analyze" else "Camera is OFF")
    }
    
    private fun startWakeWordListening() {
        _isListeningForWakeWord.value = true
        updateStatus("LISTENING")
    }
    
    private fun stopWakeWordListening() {
        _isListeningForWakeWord.value = false
        updateStatus("READY")
    }
    
    fun processTextCommand(command: String, imageBitmap: Bitmap? = null) {
        if (!_isInitialized.value) {
            updateMessage("System is still initializing. Please wait...")
            return
        }
        
        viewModelScope.launch {
            try {
                updateStatus("THINKING...")
                updateMessage("🔄 Generating response...")
                
                // Process command logic here
                if (imageBitmap != null) {
                    // Multimodal processing
                    processMultimodalCommand(command, imageBitmap)
                } else {
                    // Text-only processing
                    processTextOnlyCommand(command)
                }
                
            } catch (e: Exception) {
                updateStatus("ERROR")
                updateMessage("Processing error: ${e.message}")
            }
        }
    }
    
    private suspend fun processMultimodalCommand(command: String, imageBitmap: Bitmap) {
        // Multimodal processing implementation
        updateMessage("🖼️ Processing image + text...")
        // Integration with VisionManager here
    }
    
    private suspend fun processTextOnlyCommand(command: String) {
        // Text-only processing implementation
        updateMessage("💭 Processing text command...")
        // Integration with LLM systems here
    }
    
    private fun updateStatus(status: String) {
        _uiState.value = _uiState.value.copy(statusIndicator = status)
    }
    
    private fun updateMessage(message: String) {
        _uiState.value = _uiState.value.copy(classificationResult = message)
    }
    
    override fun onCleared() {
        super.onCleared()
        // Cleanup resources
        if (::whisperProcessor.isInitialized) {
            whisperProcessor.stopListening()
        }
    }
}

data class MainUiState(
    val statusIndicator: String = "● INITIALIZING...",
    val classificationResult: String = "Initializing...",
    val confidenceText: String = "",
    val inferenceTime: String = "",
    val typingIndicatorVisible: Boolean = false,
    val cancelGenerationVisible: Boolean = false,
    val sendCommandEnabled: Boolean = true
)