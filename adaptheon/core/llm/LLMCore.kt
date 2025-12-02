package com.adaptheon.core.llm

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

/**
 * Unified interface for multiple LLM backends
 * Provides abstraction layer for different language models
 */
class LLMCore {
    private val modelRegistry = ConcurrentHashMap<String, LLMModel>()
    private val activeSessions = ConcurrentHashMap<String, LLMSession>()
    private var defaultModel: String? = null
    
    /**
     * Register a new LLM model
     */
    fun registerModel(modelId: String, model: LLMModel) {
        modelRegistry[modelId] = model
        if (defaultModel == null) {
            defaultModel = modelId
        }
        println("Registered LLM model: $modelId")
    }
    
    /**
     * Set default model for inference
     */
    fun setDefaultModel(modelId: String) {
        if (modelRegistry.containsKey(modelId)) {
            defaultModel = modelId
            println("Default LLM model set to: $modelId")
        } else {
            throw IllegalArgumentException("Model $modelId not found")
        }
    }
    
    /**
     * Create a new conversation session
     */
    suspend fun createSession(
        modelId: String? = null,
        systemPrompt: String? = null,
        maxTokens: Int = 2048,
        temperature: Double = 0.7
    ): String {
        val sessionId = generateSessionId()
        val model = getModel(modelId ?: defaultModel!!)
        
        val session = LLMSession(
            id = sessionId,
            model = model,
            systemPrompt = systemPrompt,
            maxTokens = maxTokens,
            temperature = temperature
        )
        
        activeSessions[sessionId] = session
        return sessionId
    }
    
    /**
     * Send message to LLM and get response
     */
    suspend fun sendMessage(
        sessionId: String,
        message: String,
        stream: Boolean = false
    ): LLMResponse {
        val session = activeSessions[sessionId]
            ?: throw IllegalArgumentException("Session $sessionId not found")
        
        session.addMessage(message, role = "user")
        
        val response = if (stream) {
            generateStreamingResponse(session)
        } else {
            generateResponse(session)
        }
        
        session.addMessage(response.content, role = "assistant")
        return response
    }
    
    /**
     * Send message and get streaming response
     */
    fun sendMessageStream(sessionId: String, message: String): Flow<String> {
        return flow {
            val session = activeSessions[sessionId]
                ?: throw IllegalArgumentException("Session $sessionId not found")
            
            session.addMessage(message, role = "user")
            
            session.model.generateStream(
                messages = session.getMessages(),
                systemPrompt = session.systemPrompt,
                temperature = session.temperature
            ).collect { chunk ->
                emit(chunk)
            }
        }
    }
    
    /**
     * Get session history
     */
    suspend fun getSessionHistory(sessionId: String): List<ConversationMessage> {
        val session = activeSessions[sessionId]
            ?: throw IllegalArgumentException("Session $sessionId not found")
        return session.getMessages()
    }
    
    /**
     * Clear session history
     */
    suspend fun clearSession(sessionId: String) {
        val session = activeSessions[sessionId]
            ?: throw IllegalArgumentException("Session $sessionId not found")
        session.clearMessages()
    }
    
    /**
     * Delete session
     */
    suspend fun deleteSession(sessionId: String) {
        activeSessions.remove(sessionId)
    }
    
    /**
     * Get available models
     */
    fun getAvailableModels(): List<ModelInfo> {
        return modelRegistry.values.map { model ->
            ModelInfo(
                id = model.id,
                name = model.name,
                type = model.type,
                maxTokens = model.maxTokens,
                description = model.description
            )
        }
    }
    
    /**
     * Get model capabilities
     */
    fun getModelCapabilities(modelId: String): ModelCapabilities? {
        return modelRegistry[modelId]?.capabilities
    }
    
    /**
     * Check if model supports specific feature
     */
    fun supportsFeature(modelId: String, feature: ModelFeature): Boolean {
        return modelRegistry[modelId]?.capabilities?.supportedFeatures?.contains(feature) == true
    }
    
    private suspend fun generateResponse(session: LLMSession): LLMResponse {
        val startTime = System.currentTimeMillis()
        
        try {
            val response = session.model.generate(
                messages = session.getMessages(),
                systemPrompt = session.systemPrompt,
                temperature = session.temperature,
                maxTokens = session.maxTokens
            )
            
            val endTime = System.currentTimeMillis()
            
            return LLMResponse(
                content = response,
                modelId = session.model.id,
                sessionId = session.id,
                tokensUsed = estimateTokens(response),
                responseTimeMs = endTime - startTime,
                finishReason = "stop"
            )
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            
            return LLMResponse(
                content = "Error generating response: ${e.message}",
                modelId = session.model.id,
                sessionId = session.id,
                tokensUsed = 0,
                responseTimeMs = endTime - startTime,
                finishReason = "error",
                error = e.message
            )
        }
    }
    
    private suspend fun generateStreamingResponse(session: LLMSession): LLMResponse {
        val startTime = System.currentTimeMillis()
        var fullResponse = ""
        
        try {
            session.model.generateStream(
                messages = session.getMessages(),
                systemPrompt = session.systemPrompt,
                temperature = session.temperature
            ).collect { chunk ->
                fullResponse += chunk
            }
            
            val endTime = System.currentTimeMillis()
            
            return LLMResponse(
                content = fullResponse,
                modelId = session.model.id,
                sessionId = session.id,
                tokensUsed = estimateTokens(fullResponse),
                responseTimeMs = endTime - startTime,
                finishReason = "stop"
            )
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            
            return LLMResponse(
                content = "Error generating streaming response: ${e.message}",
                modelId = session.model.id,
                sessionId = session.id,
                tokensUsed = estimateTokens(fullResponse),
                responseTimeMs = endTime - startTime,
                finishReason = "error",
                error = e.message
            )
        }
    }
    
    private fun getModel(modelId: String): LLMModel {
        return modelRegistry[modelId]
            ?: throw IllegalArgumentException("Model $modelId not found")
    }
    
    private fun generateSessionId(): String {
        return "session_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    private fun estimateTokens(text: String): Int {
        // Rough estimation: ~4 characters per token
        return (text.length / 4).coerceAtLeast(1)
    }
    
    data class LLMSession(
        val id: String,
        val model: LLMModel,
        val systemPrompt: String?,
        val maxTokens: Int,
        val temperature: Double
    ) {
        private val messages = mutableListOf<ConversationMessage>()
        
        fun addMessage(content: String, role: String) {
            messages.add(ConversationMessage(content, role, System.currentTimeMillis()))
        }
        
        fun getMessages(): List<ConversationMessage> = messages.toList()
        
        fun clearMessages() {
            messages.clear()
            systemPrompt?.let { 
                messages.add(ConversationMessage(it, "system", System.currentTimeMillis()))
            }
        }
    }
    
    @Serializable
    data class ConversationMessage(
        val content: String,
        val role: String,
        val timestamp: Long
    )
    
    @Serializable
    data class LLMResponse(
        val content: String,
        val modelId: String,
        val sessionId: String,
        val tokensUsed: Int,
        val responseTimeMs: Long,
        val finishReason: String,
        val error: String? = null
    )
    
    data class ModelInfo(
        val id: String,
        val name: String,
        val type: LLMType,
        val maxTokens: Int,
        val description: String
    )
    
    data class ModelCapabilities(
        val supportedFeatures: Set<ModelFeature>,
        val maxContextLength: Int,
        val supportedLanguages: Set<String>,
        val streamingSupported: Boolean
    )
    
    enum class LLMType {
        CAUSAL_LM, SEQ2SEQ, INSTRUCTION_FOLLOWING, CHAT
    }
    
    enum class ModelFeature {
        STREAMING, FUNCTION_CALLING, VISION_INPUT, CODE_GENERATION, MULTILINGUAL
    }
}

/**
 * Interface for LLM model implementations
 */
interface LLMModel {
    val id: String
    val name: String
    val type: LLMCore.LLMType
    val maxTokens: Int
    val description: String
    val capabilities: LLMCore.ModelCapabilities
    
    suspend fun generate(
        messages: List<LLMCore.ConversationMessage>,
        systemPrompt: String? = null,
        temperature: Double = 0.7,
        maxTokens: Int = 2048
    ): String
    
    fun generateStream(
        messages: List<LLMCore.ConversationMessage>,
        systemPrompt: String? = null,
        temperature: Double = 0.7,
        maxTokens: Int = 2048
    ): Flow<String>
}