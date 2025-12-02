package com.ailive.ai.llm

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Sapient HRM 27M Local Model Manager
 * Integrates the Sapient HRM 27M parameter local model for advanced AI capabilities
 */
class SapientHRMManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SapientHRMManager"
        private const val MODEL_NAME = "sapient-hrm-27m.gguf"
        private const val MODEL_SIZE_MB = 108 // Approximate size for 27M parameters
    }
    
    // Model state
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _loadProgress = MutableStateFlow(0f)
    val loadProgress: StateFlow<Float> = _loadProgress.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Model capabilities
    private val _supportedFeatures = MutableStateFlow(
        setOf(
            SapientFeature.TEXT_GENERATION,
            SapientFeature.CONVERSATION,
            SapientFeature.REASONING,
            SapientFeature.CODE_GENERATION,
            SapientFeature.MULTILINGUAL
        )
    )
    val supportedFeatures: StateFlow<Set<SapientFeature>> = _supportedFeatures.asStateFlow()
    
    // Core components
    private var nativeModel: Long = 0 // Native handle
    private val modelDownloadManager = ModelDownloadManager(context)
    
    /**
     * Initialize the Sapient HRM model
     */
    suspend fun initialize(): Boolean {
        if (_isLoaded.value) return true
        
        try {
            _isLoading.value = true
            _error.value = null
            
            // Check if model is available
            val modelPath = getModelPath()
            if (modelPath.isEmpty() || !File(modelPath).exists()) {
                _error.value = "Sapient HRM model not found. Please download the model first."
                return false
            }
            
            // Load native model
            _loadProgress.value = 0.1f
            val success = loadNativeModel(modelPath)
            
            if (success) {
                _isLoaded.value = true
                _loadProgress.value = 1.0f
                return true
            } else {
                _error.value = "Failed to load Sapient HRM model"
                return false
            }
            
        } catch (e: Exception) {
            _error.value = "Initialization error: ${e.message}"
            return false
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Generate text using Sapient HRM model
     */
    suspend fun generateText(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        topP: Float = 0.9f
    ): String {
        if (!_isLoaded.value) {
            throw IllegalStateException("Sapient HRM model not loaded")
        }
        
        return try {
            generateTextNative(
                nativeModel,
                prompt,
                maxTokens,
                temperature,
                topP
            )
        } catch (e: Exception) {
            _error.value = "Generation error: ${e.message}"
            "Error generating text: ${e.message}"
        }
    }
    
    /**
     * Generate response for conversation
     */
    suspend fun generateConversationResponse(
        message: String,
        conversationHistory: List<ConversationTurn> = emptyList(),
        systemPrompt: String? = null
    ): String {
        val fullPrompt = buildConversationPrompt(message, conversationHistory, systemPrompt)
        return generateText(fullPrompt, temperature = 0.8f)
    }
    
    /**
     * Generate code for programming tasks
     */
    suspend fun generateCode(
        task: String,
        language: String = "python",
        context: String = ""
    ): String {
        val codePrompt = buildCodePrompt(task, language, context)
        return generateText(codePrompt, temperature = 0.3f, maxTokens = 1024)
    }
    
    /**
     * Perform reasoning task
     */
    suspend fun performReasoning(
        question: String,
        context: String = ""
    ): String {
        val reasoningPrompt = buildReasoningPrompt(question, context)
        return generateText(reasoningPrompt, temperature = 0.5f)
    }
    
    /**
     * Check if model is available for download/load
     */
    fun isModelAvailable(): Boolean {
        val modelPath = getModelPath()
        return modelPath.isNotEmpty() && File(modelPath).exists()
    }
    
    /**
     * Get model information
     */
    fun getModelInfo(): SapientModelInfo {
        return SapientModelInfo(
            name = "Sapient HRM 27M",
            parameters = 27000000,
            size = MODEL_SIZE_MB,
            version = "1.0.0",
            capabilities = _supportedFeatures.value.toList(),
            isLoaded = _isLoaded.value,
            modelPath = getModelPath()
        )
    }
    
    /**
     * Unload the model and free resources
     */
    fun unload() {
        if (nativeModel != 0L) {
            unloadNativeModel(nativeModel)
            nativeModel = 0
        }
        _isLoaded.value = false
        _loadProgress.value = 0f
    }
    
    // Private helper methods
    
    private fun getModelPath(): String {
        return modelDownloadManager.getModelPath(MODEL_NAME)
    }
    
    private fun buildConversationPrompt(
        message: String,
        history: List<ConversationTurn>,
        systemPrompt: String?
    ): String {
        val prompt = StringBuilder()
        
        if (systemPrompt != null) {
            prompt.append("System: $systemPrompt\n\n")
        }
        
        history.forEach { turn ->
            prompt.append("${turn.role}: ${turn.content}\n")
        }
        
        prompt.append("User: $message\nAssistant: ")
        return prompt.toString()
    }
    
    private fun buildCodePrompt(task: String, language: String, context: String): String {
        return buildString {
            append("Generate $language code for the following task:\n")
            if (context.isNotEmpty()) {
                append("Context: $context\n")
            }
            append("Task: $task\n\n")
            append("Code:\n")
        }
    }
    
    private fun buildReasoningPrompt(question: String, context: String): String {
        return buildString {
            append("Please reason step by step to answer the following question.\n")
            if (context.isNotEmpty()) {
                append("Context: $context\n")
            }
            append("Question: $question\n\n")
            append("Reasoning:\n")
        }
    }
    
    // Native methods (to be implemented in JNI)
    
    private external fun loadNativeModel(modelPath: String): Boolean
    private external fun unloadNativeModel(modelHandle: Long)
    private external fun generateTextNative(
        modelHandle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float
    ): String
    
    // Load native library
    companion object {
        init {
            System.loadLibrary("sapienthrm_jni")
        }
    }
}

// Data classes

enum class SapientFeature {
    TEXT_GENERATION,
    CONVERSATION,
    REASONING,
    CODE_GENERATION,
    MULTILINGUAL,
    SUMMARIZATION,
    TRANSLATION
}

data class ConversationTurn(
    val role: String, // "User", "Assistant", "System"
    val content: String
)

data class SapientModelInfo(
    val name: String,
    val parameters: Int,
    val size: Int, // in MB
    val version: String,
    val capabilities: List<SapientFeature>,
    val isLoaded: Boolean,
    val modelPath: String
)