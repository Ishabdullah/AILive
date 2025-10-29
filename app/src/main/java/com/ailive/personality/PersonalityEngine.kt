package com.ailive.personality

import android.content.Context
import android.util.Log
import com.ailive.ai.llm.LLMManager
import com.ailive.audio.TTSManager
import com.ailive.core.messaging.*
import com.ailive.core.state.StateManager
import com.ailive.core.types.AgentType
import com.ailive.personality.prompts.UnifiedPrompt
import com.ailive.personality.tools.*
import kotlinx.coroutines.*
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
    private val llmManager: LLMManager,
    private val ttsManager: TTSManager
) {
    private val TAG = "PersonalityEngine"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Tool registry
    private val toolRegistry = ToolRegistry()

    // Conversation context
    private val conversationHistory = mutableListOf<ConversationTurn>()
    private val maxHistorySize = 20  // Keep last 20 turns

    // State
    private var isRunning = false
    private var currentEmotion: EmotionContext = EmotionContext()

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
     * Analyze user intent from input
     */
    private fun analyzeIntent(input: String): Intent {
        val inputLower = input.lowercase()

        // Simple rule-based intent detection (replace with LLM-based later)
        return when {
            // Vision-related
            "see" in inputLower || "look" in inputLower ||
            "show" in inputLower || "camera" in inputLower ||
            "picture" in inputLower || "photo" in inputLower -> {
                Intent(
                    primary = IntentType.VISION,
                    parameters = extractVisionParams(input)
                )
            }

            // Emotion-related
            "feel" in inputLower || "emotion" in inputLower ||
            "mood" in inputLower || "sentiment" in inputLower -> {
                Intent(
                    primary = IntentType.EMOTION,
                    parameters = mapOf("text" to input)
                )
            }

            // Memory-related
            "remember" in inputLower || "recall" in inputLower ||
            "memory" in inputLower || "stored" in inputLower -> {
                Intent(
                    primary = IntentType.MEMORY,
                    parameters = extractMemoryParams(input)
                )
            }

            // Device control
            "turn on" in inputLower || "turn off" in inputLower ||
            "enable" in inputLower || "disable" in inputLower ||
            "flashlight" in inputLower || "notification" in inputLower -> {
                Intent(
                    primary = IntentType.DEVICE_CONTROL,
                    parameters = extractDeviceParams(input)
                )
            }

            // General conversation
            else -> {
                Intent(
                    primary = IntentType.CONVERSATION,
                    parameters = mapOf("text" to input)
                )
            }
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
                // TODO: Add PatternAnalysisTool when implemented
                listOfNotNull(
                    toolRegistry.getTool("analyze_sentiment")  // For now, use sentiment
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

                    ToolExecutionResult(
                        tool = tool,
                        result = result,
                        durationMs = duration
                    )
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
            conversationHistory = conversationHistory.takeLast(10),
            toolContext = toolContext,
            emotionContext = currentEmotion
        )

        // Generate response with LLM (with fallback)
        val responseText = try {
            val startTime = System.currentTimeMillis()
            val llmResponse = llmManager.generate(prompt, agentName = "AILive")
            val duration = System.currentTimeMillis() - startTime

            Log.i(TAG, "✨ LLM generated response in ${duration}ms")

            // If LLM response is too short or seems failed, use fallback
            if (llmResponse.length < 10 || llmResponse.isBlank()) {
                Log.w(TAG, "LLM response too short, using fallback")
                generateFallbackResponse(input, intent, toolResults)
            } else {
                llmResponse
            }
        } catch (e: Exception) {
            Log.w(TAG, "LLM generation failed, using fallback: ${e.message}")
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
    private fun addToHistory(turn: ConversationTurn) {
        conversationHistory.add(turn)

        // Keep only recent history
        if (conversationHistory.size > maxHistorySize) {
            conversationHistory.removeAt(0)
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

    private fun buildToolContext(toolResults: List<ToolExecutionResult>): Map<String, Any> {
        return toolResults.associate { execution ->
            execution.tool.name to when (val result = execution.result) {
                is ToolResult.Success -> result.data
                is ToolResult.Failure -> "Error: ${result.reason}"
                is ToolResult.Blocked -> "Blocked: ${result.reason}"
                is ToolResult.Unavailable -> "Unavailable: ${result.reason}"
            }
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
