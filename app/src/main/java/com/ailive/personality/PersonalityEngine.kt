package com.ailive.personality

import android.content.Context
import android.util.Log
import com.ailive.ai.llm.HybridModelManager
import com.ailive.audio.TTSManager
import com.ailive.core.messaging.*
import com.ailive.core.state.StateManager
import com.ailive.core.types.AgentType
import com.ailive.location.LocationManager
import com.ailive.personality.prompts.UnifiedPrompt
import com.ailive.personality.tools.*
import com.ailive.settings.AISettings
import com.ailive.stats.StatisticsManager
import com.ailive.memory.managers.UnifiedMemoryManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import java.util.UUID

/**
 * PersonalityEngine - AILive's unified intelligence core
 *
 * This is the heart of the refactoring that transforms AILive from
 * six separate agents into ONE cohesive, unified intelligence.
 *
 * Design Philosophy:
 * - ONE personality that users interact with
 * - ONE voice across all interactions
 * - Multiple tools/capabilities used seamlessly
 * - Coherent, contextual responses
 * - Users experience unified consciousness, not separate agents
 *
 * Architecture:
 * - Uses LLMManager for unified text generation
 * - Uses TTSManager for consistent voice output
 * - Uses ToolRegistry for capability access
 * - Maintains conversation context
 * - Publishes to MessageBus for system integration
 *
 * @author AILive Team
 * @since Refactoring Phase 1
 */
class PersonalityEngine(
    private val context: Context,
    private val messageBus: MessageBus,
    private val stateManager: StateManager,
    private val hybridModelManager: HybridModelManager,
    private val ttsManager: TTSManager,
    private val memoryManager: UnifiedMemoryManager? = null  // v1.3: Persistent memory
) {
    private val TAG = "PersonalityEngine"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Tool registry
    private val toolRegistry = ToolRegistry()

    // Context managers (lazy initialization)
    private val locationManager by lazy { LocationManager(context) }
    private val statisticsManager by lazy { StatisticsManager(context) }
    private val aiSettings by lazy { AISettings(context) }

    // Conversation context
    private val conversationHistory = mutableListOf<ConversationTurn>()
    private val maxHistorySize = 20  // Keep last 20 turns

    // State
    private var isRunning = false
    private var currentEmotion: EmotionContext = EmotionContext()

    // Tool execution listeners
    private val toolExecutionListeners = mutableListOf<ToolExecutionListener>()

    /**
     * Start the PersonalityEngine
     */
    fun start() {
        if (isRunning) return

        isRunning = true
        Log.i(TAG, "🧠 PersonalityEngine starting...")

        // Subscribe to messages
        subscribeToMessages()

        // Publish startup
        scope.launch {
            messageBus.publish(
                AIMessage.System.AgentStarted(source = AgentType.META_AI)
            )
        }

        Log.i(TAG, "✓ PersonalityEngine started - Unified intelligence active")
    }

    /**
     * Stop the PersonalityEngine
     */
    fun stop() {
        if (!isRunning) return

        Log.i(TAG, "PersonalityEngine stopping...")

        scope.cancel()
        isRunning = false

        Log.i(TAG, "PersonalityEngine stopped")
    }

    /**
     * Register a tool with the personality engine
     */
    fun registerTool(tool: AITool) {
        toolRegistry.register(tool)
        Log.i(TAG, "Registered tool: ${tool.name}")
    }

    /**
     * Register a tool execution listener
     */
    fun addToolExecutionListener(listener: ToolExecutionListener) {
        toolExecutionListeners.add(listener)
    }

    /**
     * Remove a tool execution listener
     */
    fun removeToolExecutionListener(listener: ToolExecutionListener) {
        toolExecutionListeners.remove(listener)
    }

    /**
     * Get all registered tools (for dashboard)
     */
    fun getAllTools(): List<AITool> {
        return toolRegistry.getAllTools()
    }

    /**
     * Process user input and generate unified response
     *
     * This is the main entry point for user interactions.
     * It analyzes intent, selects tools, executes them, and generates
     * a coherent response in the unified personality's voice.
     */
    suspend fun processInput(input: String, inputType: InputType = InputType.TEXT): Response {
        Log.d(TAG, "Processing input: ${input.take(50)}...")

        // Add to conversation history
        val userTurn = ConversationTurn(
            role = Role.USER,
            content = input,
            timestamp = System.currentTimeMillis()
        )
        addToHistory(userTurn)

        return try {
            // 1. Analyze intent and select tools
            val intent = analyzeIntent(input)
            val selectedTools = selectTools(intent)

            Log.d(TAG, "Intent: ${intent.primary}, Tools: ${selectedTools.map { it.name }}")

            // 2. Execute tools (in parallel when possible)
            val toolResults = executeTools(selectedTools, intent.parameters)

            // 3. Generate unified response
            val response = generateResponse(input, intent, toolResults)

            // 4. Speak with unified voice
            speakResponse(response)

            // 5. Add assistant response to history
            val assistantTurn = ConversationTurn(
                role = Role.ASSISTANT,
                content = response.text,
                timestamp = System.currentTimeMillis()
            )
            addToHistory(assistantTurn)

            // 6. Update state
            updateState(intent, toolResults)

            response

        } catch (e: Exception) {
            Log.e(TAG, "Error processing input", e)

            val errorResponse = Response(
                text = "I'm having trouble processing that right now. Could you try again?",
                confidence = 0f,
                usedTools = emptyList()
            )

            speakResponse(errorResponse)
            errorResponse
        }
    }

    /**
     * Generate streaming response with full context awareness
     *
     * This method creates a proper UnifiedPrompt with AI name, temporal context,
     * location context, conversation history, and persistent memory before streaming to the LLM.
     *
     * Use this instead of calling hybridModelManager.generateStreaming() directly!
     */
    suspend fun generateStreamingResponse(input: String): Flow<String> {
        Log.d(TAG, "Generating streaming response with full context for: ${input.take(50)}...")

        try {
            // Get location context if enabled
            val locationContext = if (aiSettings.locationAwarenessEnabled) {
                withContext(Dispatchers.IO) {
                    try {
                        locationManager.getLocationContext(forceRefresh = false)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get location context: ${e.message}")
                        null
                    }
                }
            } else {
                Log.d(TAG, "Location awareness disabled")
                null
            }

            // Get memory context if available
            val memoryContext = memoryManager?.let {
                withContext(Dispatchers.IO) {
                    try {
                        it.generateContextForPrompt(
                            userInput = input,
                            includeProfile = true,
                            includeRecentContext = true,
                            includeFacts = true,
                            maxContextLength = 800  // Keep under 1000 chars to avoid bloating prompt
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to generate memory context: ${e.message}")
                        null
                    }
                }
            }

            if (memoryContext != null) {
                Log.d(TAG, "✓ Generated memory context: ${memoryContext.length} chars")
            }

            // Create optimized prompt with ALL context:
            // - AI custom name
            // - Current date and time (temporal awareness)
            // - GPS location (if enabled)
            // - Conversation history
            // - Persistent memory (profile + facts + recent context)
            Log.d(TAG, "Building prompt with AI name='${aiSettings.aiName}'")

            // Include memory context in tool context if available
            val toolContext = if (memoryContext != null && memoryContext.isNotBlank()) {
                mapOf("memory" to memoryContext)
            } else {
                emptyMap()
            }

            // IMPROVEMENT 1: Sliding Window Context Trimmer
            // Dynamically reduce conversation history if prompt exceeds safe limits
            // Safe limits for mobile LLMs (typical 2k-4k token context):
            // - 6000 chars ≈ 1500 tokens (conservative estimate for mobile models)
            // - Leave room for response tokens and system prompt
            val maxSafePromptLength = 6000
            var currentHistory = conversationHistory.takeLast(10).toList()
            var prompt = UnifiedPrompt.create(
                userInput = input,
                aiName = aiSettings.aiName,
                conversationHistory = currentHistory,
                toolContext = toolContext,
                emotionContext = currentEmotion,
                locationContext = locationContext
            )

            // Keep trimming history until prompt fits, or we run out of history
            var trimAttempts = 0
            while (prompt.length > maxSafePromptLength && currentHistory.isNotEmpty() && trimAttempts < 10) {
                Log.w(TAG, "⚠️ Prompt too long (${prompt.length} chars) - trimming conversation history (${currentHistory.size} turns)")

                // Remove oldest message
                currentHistory = currentHistory.drop(1)
                trimAttempts++

                // Rebuild prompt with reduced history
                prompt = UnifiedPrompt.create(
                    userInput = input,
                    aiName = aiSettings.aiName,
                    conversationHistory = currentHistory,
                    toolContext = toolContext,
                    emotionContext = currentEmotion,
                    locationContext = locationContext
                )

                Log.d(TAG, "Trimmed to ${currentHistory.size} turns, new length: ${prompt.length} chars")
            }

            // Final safety check - warn if still too long
            if (prompt.length > maxSafePromptLength) {
                Log.w(TAG, "⚠️ CRITICAL: Prompt still too long after trimming (${prompt.length} chars)")
                Log.w(TAG, "⚠️ This may cause context overflow or crash. Consider reducing memory context length.")
            } else if (trimAttempts > 0) {
                Log.i(TAG, "✅ Prompt trimmed successfully: ${currentHistory.size} turns, ${prompt.length} chars")
            }

            // Log prompt details for debugging
            val promptLength = prompt.length
            Log.i(TAG, "✅ Created full prompt: length=$promptLength chars")
            Log.d(TAG, "Prompt preview (first 500 chars):")
            Log.d(TAG, prompt.take(500))

            // Stream with the FULL PROMPT (not just raw input!)
            Log.d(TAG, "Calling hybridModelManager.generateStreaming()...")
            return hybridModelManager.generateStreaming(prompt, agentName = aiSettings.aiName)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in generateStreamingResponse", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            e.printStackTrace()

            // Provide specific error messages based on exception type
            val errorMessage = when (e) {
                is UnsatisfiedLinkError -> {
                    "The AI model requires native libraries that are missing from this build. " +
                    "Please download a properly built APK with NDK support enabled, or rebuild with CMake configuration."
                }
                is IllegalStateException -> {
                    if (e.message?.contains("not initialized") == true) {
                        "The AI model is not initialized. ${e.message}"
                    } else {
                        "I encountered a state error: ${e.message}. Please try restarting the app."
                    }
                }
                else -> {
                    "I encountered an error: ${e.message}. Please try again or restart the app."
                }
            }

            // Return error message as a flow
            return kotlinx.coroutines.flow.flowOf(errorMessage)
        }
    }

    /**
     * Analyze user intent from input
     *
     * IMPROVEMENT 2: Two-tier intent detection
     * 1. FAST PATH: Keyword-based matching (instant, no LLM needed)
     * 2. SLOW PATH: LLM-based semantic classification (for ambiguous cases)
     */
    private suspend fun analyzeIntent(input: String): Intent {
        val inputLower = input.lowercase()

        // Track confidence in fast path detection
        var fastPathConfidence = 1.0f
        var fastPathIntent: IntentType? = null
        var fastPathParams: Map<String, Any> = emptyMap()

        // FAST PATH: Keyword-based intent detection
        when {
            // Vision-related - high confidence keywords
            "camera" in inputLower || "picture" in inputLower ||
            "photo" in inputLower || "snap" in inputLower -> {
                fastPathIntent = IntentType.VISION
                fastPathParams = extractVisionParams(input)
                fastPathConfidence = 1.0f
            }

            // Vision-related - medium confidence keywords (might be ambiguous)
            "see" in inputLower || "look" in inputLower || "show" in inputLower -> {
                fastPathIntent = IntentType.VISION
                fastPathParams = extractVisionParams(input)
                fastPathConfidence = 0.6f  // Lower confidence - might need semantic check
            }

            // Emotion-related
            "feel" in inputLower || "emotion" in inputLower ||
            "mood" in inputLower || "sentiment" in inputLower -> {
                fastPathIntent = IntentType.EMOTION
                fastPathParams = mapOf("text" to input)
                fastPathConfidence = 0.9f
            }

            // Memory-related
            "remember" in inputLower || "recall" in inputLower ||
            "memory" in inputLower || "stored" in inputLower -> {
                fastPathIntent = IntentType.MEMORY
                fastPathParams = extractMemoryParams(input)
                fastPathConfidence = 0.9f
            }

            // Device control - high confidence
            "flashlight" in inputLower || "notification" in inputLower -> {
                fastPathIntent = IntentType.DEVICE_CONTROL
                fastPathParams = extractDeviceParams(input)
                fastPathConfidence = 1.0f
            }

            // Device control - medium confidence
            "turn on" in inputLower || "turn off" in inputLower ||
            "enable" in inputLower || "disable" in inputLower -> {
                fastPathIntent = IntentType.DEVICE_CONTROL
                fastPathParams = extractDeviceParams(input)
                fastPathConfidence = 0.7f
            }

            // Prediction-related (PHASE 5 Part 3)
            "predict" in inputLower || "what will" in inputLower ||
            "pattern" in inputLower || "suggest" in inputLower ||
            "recommend" in inputLower || "next" in inputLower -> {
                fastPathIntent = IntentType.PREDICTION
                fastPathParams = mapOf("action" to "predict", "text" to input)
                fastPathConfidence = 0.8f
            }

            // No clear keywords - use semantic analysis
            else -> {
                fastPathIntent = null
                fastPathConfidence = 0.0f
            }
        }

        // SLOW PATH: Use LLM for semantic classification if:
        // 1. No keywords matched (fastPathIntent == null), OR
        // 2. Low confidence match (< 0.8)
        //
        // NOTE: This is commented out for now to avoid LLM overhead on every request.
        // Uncomment when you need better semantic understanding or when LLM is fast enough.
        /*
        if (fastPathIntent == null || fastPathConfidence < 0.8f) {
            Log.d(TAG, "Fast path confidence low ($fastPathConfidence) - attempting semantic classification")

            try {
                val semanticIntent = analyzeIntentWithLLM(input)
                if (semanticIntent != null) {
                    Log.i(TAG, "✨ Semantic classification: ${semanticIntent.primary}")
                    return semanticIntent
                }
            } catch (e: Exception) {
                Log.w(TAG, "Semantic classification failed, using fast path: ${e.message}")
            }
        }
        */

        // Return fast path result (or conversation as fallback)
        return if (fastPathIntent != null) {
            Intent(
                primary = fastPathIntent,
                parameters = fastPathParams,
                confidence = fastPathConfidence
            )
        } else {
            // Default to conversation
            Intent(
                primary = IntentType.CONVERSATION,
                parameters = mapOf("text" to input),
                confidence = 0.5f
            )
        }
    }

    /**
     * IMPROVEMENT 2: Semantic intent classification using LLM
     *
     * Use this as a fallback when keyword matching fails or has low confidence.
     * The LLM analyzes the semantic meaning to determine intent.
     *
     * Example:
     * - "I want to capture this moment" → VISION (no "camera" keyword!)
     * - "How am I feeling right now?" → EMOTION (indirect phrasing)
     * - "What did we talk about yesterday?" → MEMORY (no "remember" keyword)
     */
    private suspend fun analyzeIntentWithLLM(input: String): Intent? {
        val classificationPrompt = """
Classify the user's intent into ONE category:

Categories:
- VISION: User wants to see/capture something (camera, photos, visual analysis)
- EMOTION: User asking about feelings, mood, or sentiment
- MEMORY: User wants to recall or remember something
- DEVICE_CONTROL: User wants to control device (flashlight, notifications, etc.)
- PREDICTION: User wants predictions or recommendations
- CONVERSATION: General chat or questions

User input: "$input"

Respond with ONLY the category name (e.g., "VISION" or "MEMORY").
""".trimIndent()

        return try {
            val response = withContext(Dispatchers.IO) {
                hybridModelManager.generate(classificationPrompt, agentName = "IntentClassifier")
            }

            val intentType = when (response.trim().uppercase()) {
                "VISION" -> IntentType.VISION
                "EMOTION" -> IntentType.EMOTION
                "MEMORY" -> IntentType.MEMORY
                "DEVICE_CONTROL" -> IntentType.DEVICE_CONTROL
                "PREDICTION" -> IntentType.PREDICTION
                "CONVERSATION" -> IntentType.CONVERSATION
                else -> null
            }

            if (intentType != null) {
                val params = when (intentType) {
                    IntentType.VISION -> extractVisionParams(input)
                    IntentType.EMOTION -> mapOf("text" to input)
                    IntentType.MEMORY -> extractMemoryParams(input)
                    IntentType.DEVICE_CONTROL -> extractDeviceParams(input)
                    IntentType.PREDICTION -> mapOf("action" to "predict", "text" to input)
                    IntentType.CONVERSATION -> mapOf("text" to input)
                }

                Intent(
                    primary = intentType,
                    parameters = params,
                    confidence = 0.85f  // High confidence from LLM classification
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM intent classification failed", e)
            null
        }
    }

    /**
     * Select appropriate tools for intent
     */
    private fun selectTools(intent: Intent): List<AITool> {
        return when (intent.primary) {
            IntentType.VISION -> {
                // PHASE 5: Use VisionAnalysisTool for camera vision
                listOfNotNull(
                    toolRegistry.getTool("analyze_vision"),     // Phase 5: Real vision processing
                    toolRegistry.getTool("control_device")      // Device control
                )
            }
            IntentType.EMOTION -> {
                listOfNotNull(
                    toolRegistry.getTool("analyze_sentiment")
                )
            }
            IntentType.MEMORY -> {
                listOfNotNull(
                    toolRegistry.getTool("retrieve_memory")
                )
            }
            IntentType.DEVICE_CONTROL -> {
                listOfNotNull(
                    toolRegistry.getTool("control_device")
                )
            }
            IntentType.PREDICTION -> {
                // PHASE 5 Part 3: Use PatternAnalysisTool for predictions
                listOfNotNull(
                    toolRegistry.getTool("analyze_patterns"),    // Pattern-based predictions
                    toolRegistry.getTool("track_feedback")       // Learn from feedback
                )
            }
            IntentType.CONVERSATION -> {
                // May use sentiment for emotional context
                listOfNotNull(
                    toolRegistry.getTool("analyze_sentiment")
                )
            }
        }
    }

    /**
     * Execute tools in parallel when possible
     */
    private suspend fun executeTools(
        tools: List<AITool>,
        parameters: Map<String, Any>
    ): List<ToolExecutionResult> {
        return withContext(Dispatchers.IO) {
            tools.map { tool ->
                async {
                    val startTime = System.currentTimeMillis()
                    val result = tool.execute(parameters)
                    val duration = System.currentTimeMillis() - startTime

                    val executionResult = ToolExecutionResult(
                        tool = tool,
                        result = result,
                        durationMs = duration
                    )

                    // Notify listeners
                    val success = result is ToolResult.Success
                    withContext(Dispatchers.Main) {
                        toolExecutionListeners.forEach { listener ->
                            listener.onToolExecuted(tool.name, success, duration)
                        }
                    }

                    executionResult
                }
            }.awaitAll()
        }
    }

    /**
     * Generate unified response incorporating tool results
     */
    private suspend fun generateResponse(
        input: String,
        intent: Intent,
        toolResults: List<ToolExecutionResult>
    ): Response {
        // Build context from tool results
        val toolContext = buildToolContext(toolResults)

        // Get location context if enabled
        val locationContext = if (aiSettings.locationAwarenessEnabled) {
            withContext(Dispatchers.IO) {
                try {
                    locationManager.getLocationContext(forceRefresh = false)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get location context: ${e.message}")
                    null
                }
            }
        } else null

        // PHASE 4 OPTIMIZATION: LLM re-enabled with improvements
        // - Fixed prompt bias (removed vision keyword issue)
        // - NNAPI GPU acceleration enabled
        // - Reduced max tokens (80 vs 150)
        // - Increased temperature (0.9 vs 0.7)
        // - Fallback system as safety net
        Log.d(TAG, "Attempting LLM generation with optimized prompt...")

        // Create optimized prompt (vision keywords removed)
        val prompt = UnifiedPrompt.create(
            userInput = input,
            aiName = aiSettings.aiName,
            conversationHistory = conversationHistory.takeLast(10),
            toolContext = toolContext,
            emotionContext = currentEmotion,
            locationContext = locationContext
        )

        // Generate response with LLM (with fallback)
        val responseText = try {
            val startTime = System.currentTimeMillis()
            val llmResponse = hybridModelManager.generate(prompt, agentName = aiSettings.aiName)
            val duration = System.currentTimeMillis() - startTime

            // Track statistics
            statisticsManager.recordResponseTime(duration)

            Log.i(TAG, "✨ LLM generated response in ${duration}ms")

            // If LLM response is too short or seems failed, use fallback
            if (llmResponse.length < 10 || llmResponse.isBlank()) {
                Log.w(TAG, "LLM response too short, using fallback")
                generateFallbackResponse(input, intent, toolResults)
            } else {
                llmResponse
            }
        } catch (e: IllegalStateException) {
            // Handle initialization state errors with user-friendly messages
            val message = e.message ?: ""
            when {
                "still loading" in message.lowercase() -> {
                    Log.w(TAG, "⏳ LLM still initializing...")
                    "I'm still loading my language model. This takes about 5-10 seconds. Please try again in a moment!"
                }
                "not initialized" in message.lowercase() -> {
                    Log.w(TAG, "⚠️ LLM not available: $message")
                    val error = hybridModelManager.getInitializationError()
                    if (error != null) {
                        "I'm having trouble with my language model: $error"
                    } else {
                        generateFallbackResponse(input, intent, toolResults)
                    }
                }
                else -> {
                    Log.w(TAG, "LLM error: ${e.message}, using fallback")
                    generateFallbackResponse(input, intent, toolResults)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ LLM GENERATION FAILED - DETAILED ERROR", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Message: ${e.message}")
            Log.e(TAG, "Stack trace:")
            e.printStackTrace()
            Log.w(TAG, "Falling back to rule-based response")

            // DEBUGGING: Show error to user via Toast
            try {
                android.widget.Toast.makeText(
                    context,
                    "LLM ERROR: ${e.javaClass.simpleName}: ${e.message?.take(100)}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } catch (toastError: Exception) {
                Log.e(TAG, "Could not show toast", toastError)
            }

            generateFallbackResponse(input, intent, toolResults)
        }

        return Response(
            text = responseText,
            confidence = calculateConfidence(toolResults),
            usedTools = toolResults.map { it.tool.name },
            toolResults = toolResults
        )
    }

    /**
     * Speak response with unified voice
     */
    private fun speakResponse(response: Response) {
        // Use default unified voice (no agent-specific pitch/rate)
        ttsManager.pitch = 1.0f
        ttsManager.speechRate = 1.0f

        ttsManager.speak(
            text = response.text,
            priority = TTSManager.Priority.NORMAL
        )
    }

    /**
     * Subscribe to messages from other components
     */
    private fun subscribeToMessages() {
        // User input from speech recognition
        scope.launch {
            messageBus.subscribe(AIMessage.Perception.AudioTranscript::class.java)
                .collect { transcript ->
                    processInput(transcript.transcript, InputType.VOICE)
                }
        }

        // Emotion updates for context
        scope.launch {
            messageBus.subscribe(AIMessage.Perception.EmotionVector::class.java)
                .collect { emotion ->
                    currentEmotion = EmotionContext(
                        valence = emotion.valence,
                        arousal = emotion.arousal,
                        urgency = emotion.urgency
                    )
                }
        }
    }

    /**
     * Add turn to conversation history
     */
    fun addToHistory(turn: ConversationTurn) {
        conversationHistory.add(turn)

        // Keep only recent history
        if (conversationHistory.size > maxHistorySize) {
            conversationHistory.removeAt(0)
        }

        // Record in persistent memory system (v1.3)
        memoryManager?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    it.recordConversationTurn(
                        role = if (turn.role == Role.USER) "USER" else "ASSISTANT",
                        content = turn.content
                    )
                    Log.d(TAG, "✓ Recorded ${turn.role} message in persistent memory")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to record ${turn.role} message in memory", e)
                }
            }
        }
    }

    /**
     * Update system state after processing
     */
    private suspend fun updateState(intent: Intent, toolResults: List<ToolExecutionResult>) {
        stateManager.updateMeta { meta ->
            meta.copy(
                currentGoal = "Responding to ${intent.primary}",
                activeAgents = setOf(AgentType.META_AI)  // PersonalityEngine as meta
            )
        }
    }

    // Helper methods for parameter extraction
    private fun extractVisionParams(input: String): Map<String, Any> {
        return mapOf(
            "action" to "capture_image",
            "params" to mapOf("camera_id" to "0")
        )
    }

    private fun extractMemoryParams(input: String): Map<String, Any> {
        return mapOf(
            "query" to input,
            "limit" to 5
        )
    }

    private fun extractDeviceParams(input: String): Map<String, Any> {
        val action = when {
            "flashlight" in input.lowercase() && "on" in input.lowercase() -> "enable_flashlight"
            "flashlight" in input.lowercase() && "off" in input.lowercase() -> "disable_flashlight"
            else -> "get_sensor_data"
        }

        return mapOf(
            "action" to action,
            "params" to emptyMap<String, Any>()
        )
    }

    /**
     * IMPROVEMENT 3: Build human-readable tool context for LLM
     *
     * Transforms raw tool results into human-readable text that the LLM can easily understand.
     * This saves the LLM from having to parse complex data structures.
     *
     * Examples:
     * - Raw: {"lat": 40.7, "long": -74.0, "city": "New York"}
     * - Formatted: "The user is currently in New York at coordinates 40.7, -74.0"
     *
     * - Raw: {"action": "enable_flashlight", "success": true}
     * - Formatted: "Flashlight has been turned on successfully"
     */
    private fun buildToolContext(toolResults: List<ToolExecutionResult>): Map<String, Any> {
        return toolResults.associate { execution ->
            execution.tool.name to when (val result = execution.result) {
                is ToolResult.Success -> {
                    // Try to extract human-readable format
                    formatToolResult(execution.tool.name, result.data)
                }
                is ToolResult.Failure -> "Error: ${result.reason}"
                is ToolResult.Blocked -> "Blocked: ${result.reason}"
                is ToolResult.Unavailable -> "Unavailable: ${result.reason}"
            }
        }
    }

    /**
     * Format tool result data into human-readable text
     *
     * Priority:
     * 1. If data has "formatted" field → use it (many tools already provide this!)
     * 2. If data is a known structure → format it specifically
     * 3. Otherwise → return as-is (fallback)
     */
    private fun formatToolResult(toolName: String, data: Any): String {
        return when {
            // Check if data is a Map with "formatted" field
            data is Map<*, *> && data.containsKey("formatted") -> {
                data["formatted"]?.toString() ?: data.toString()
            }

            // Check if data is a DeviceControlTool.DeviceActionResult
            data is DeviceControlTool.DeviceActionResult -> {
                buildString {
                    append(data.message)
                    if (data.data != null) {
                        append("\n")
                        append(formatDataMap(data.data))
                    }
                }
            }

            // Format location data specifically
            toolName == "get_location" && data is Map<*, *> -> {
                formatLocationData(data)
            }

            // Format sensor data specifically
            toolName == "control_device" && data is Map<*, *> -> {
                formatDeviceData(data)
            }

            // Format memory retrieval results
            toolName == "retrieve_memory" && data is Map<*, *> -> {
                formatMemoryData(data)
            }

            // Default: try to format if it's a map, otherwise return as-is
            data is Map<*, *> -> {
                formatDataMap(data)
            }

            // Fallback: convert to string
            else -> data.toString()
        }
    }

    /**
     * Format location data into human-readable text
     */
    private fun formatLocationData(data: Map<*, *>): String {
        val city = data["city"]?.toString()
        val state = data["state"]?.toString()
        val country = data["country"]?.toString()
        val lat = data["latitude"]
        val long = data["longitude"]

        return buildString {
            if (city != null && state != null) {
                append("The user is currently in $city, $state")
                if (country != null && country != "Unknown") {
                    append(", $country")
                }
                append(".")
            } else if (lat != null && long != null) {
                append("The user's location is at coordinates $lat, $long.")
            } else {
                append("Location information: ${data.entries.joinToString { "${it.key}: ${it.value}" }}")
            }
        }
    }

    /**
     * Format device control data into human-readable text
     */
    private fun formatDeviceData(data: Map<*, *>): String {
        val action = data["action"]?.toString()
        val success = data["success"] as? Boolean

        return buildString {
            when (action) {
                "enable_flashlight" -> append("The flashlight has been turned on")
                "disable_flashlight" -> append("The flashlight has been turned off")
                "capture_image" -> append("An image has been captured from the camera")
                "get_sensor_data" -> {
                    append("Sensor data: ")
                    val sensorData = data["data"]
                    if (sensorData is Map<*, *>) {
                        append(formatDataMap(sensorData))
                    } else {
                        append(sensorData.toString())
                    }
                }
                else -> append(data.entries.joinToString { "${it.key}: ${it.value}" })
            }

            if (success == false) {
                append(" (failed)")
            }
        }
    }

    /**
     * Format memory data into human-readable text
     */
    private fun formatMemoryData(data: Map<*, *>): String {
        val memories = data["memories"]
        return if (memories is List<*> && memories.isNotEmpty()) {
            buildString {
                append("Retrieved ${memories.size} relevant memories:\n")
                memories.take(5).forEachIndexed { index, memory ->
                    append("${index + 1}. $memory\n")
                }
            }
        } else {
            "No relevant memories found for this query."
        }
    }

    /**
     * Generic map formatter - converts map to readable key-value pairs
     */
    private fun formatDataMap(data: Any): String {
        return when (data) {
            is Map<*, *> -> {
                data.entries.joinToString(", ") { entry ->
                    val key = entry.key.toString().replace("_", " ").replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase() else it.toString()
                    }
                    val value = when (val v = entry.value) {
                        is Double -> String.format("%.2f", v)
                        is Float -> String.format("%.2f", v)
                        is Map<*, *> -> formatDataMap(v)
                        else -> v.toString()
                    }
                    "$key: $value"
                }
            }
            is List<*> -> {
                data.joinToString(", ") { it.toString() }
            }
            else -> data.toString()
        }
    }

    private fun calculateConfidence(toolResults: List<ToolExecutionResult>): Float {
        if (toolResults.isEmpty()) return 0.7f

        val successCount = toolResults.count { it.result is ToolResult.Success }
        return successCount.toFloat() / toolResults.size
    }

    /**
     * Generate fallback response when LLM is unavailable
     * Analyzes user input and tool results to provide contextual response
     */
    private fun generateFallbackResponse(
        input: String,
        intent: Intent,
        toolResults: List<ToolExecutionResult>
    ): String {
        val inputLower = input.lowercase()

        // Check if we have tool results to incorporate
        val hasToolResults = toolResults.any { it.result is ToolResult.Success }

        return when (intent.primary) {
            IntentType.VISION -> {
                if (hasToolResults) {
                    "I can see your surroundings through my camera. What would you like to know about what I'm seeing?"
                } else {
                    "I'm ready to look around. What would you like me to see?"
                }
            }
            IntentType.EMOTION -> {
                "I can sense the emotional context. How can I help you understand the mood?"
            }
            IntentType.MEMORY -> {
                "I'm checking my memory for relevant information about your request."
            }
            IntentType.DEVICE_CONTROL -> {
                if ("flashlight" in inputLower) {
                    if ("on" in inputLower) "Turning on the flashlight" else "Turning off the flashlight"
                } else if ("camera" in inputLower) {
                    "Ready to control the camera"
                } else {
                    "What would you like me to control?"
                }
            }
            IntentType.PREDICTION -> {
                "Let me analyze the patterns to help predict what might happen."
            }
            IntentType.CONVERSATION -> {
                // Provide varied conversational responses
                when {
                    "hello" in inputLower || "hi" in inputLower ->
                        "Hello! I'm AILive, your on-device AI companion. How can I help you today?"
                    "how are you" in inputLower || "what's up" in inputLower ->
                        "I'm functioning well and ready to assist! All my systems are operational."
                    "help" in inputLower ->
                        "I can help you with vision, understanding emotions, remembering things, controlling your device, and much more. What do you need?"
                    "thank" in inputLower ->
                        "You're welcome! Happy to help."
                    else ->
                        "I'm here to help. I can see through your camera, understand emotions, remember conversations, and control device functions. What would you like me to do?"
                }
            }
        }
    }
}

// Data classes

data class ConversationTurn(
    val role: Role,
    val content: String,
    val timestamp: Long
)

enum class Role {
    USER,
    ASSISTANT,
    SYSTEM
}

data class Intent(
    val primary: IntentType,
    val parameters: Map<String, Any> = emptyMap(),
    val confidence: Float = 1.0f
)

enum class IntentType {
    VISION,
    EMOTION,
    MEMORY,
    DEVICE_CONTROL,
    PREDICTION,
    CONVERSATION
}

enum class InputType {
    TEXT,
    VOICE
}

data class EmotionContext(
    val valence: Float = 0f,
    val arousal: Float = 0f,
    val urgency: Float = 0f
)

data class Response(
    val text: String,
    val confidence: Float,
    val usedTools: List<String>,
    val toolResults: List<ToolExecutionResult> = emptyList()
)

data class ToolExecutionResult(
    val tool: AITool,
    val result: ToolResult,
    val durationMs: Long
)
