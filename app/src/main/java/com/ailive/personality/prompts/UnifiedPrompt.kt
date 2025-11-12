package com.ailive.personality.prompts

import com.ailive.personality.ConversationTurn
import com.ailive.personality.EmotionContext
import com.ailive.personality.Role
import java.text.SimpleDateFormat
import java.util.*

/**
 * UnifiedPrompt - System prompt for PersonalityEngine
 *
 * This defines AILive's unified personality. ONE character, ONE voice,
 * ONE cohesive intelligence that users interact with.
 *
 * Critical: This prompt must NEVER reference "agents" or "systems."
 * AILive speaks as ONE unified being, not as separate components.
 */
object UnifiedPrompt {

    /**
     * Generate dynamic system instruction based on AI name
     */
    private fun getCorePersonality(aiName: String): String {
        return """You are $aiName, an on-device AI assistant.

CRITICAL - READ CURRENT CONTEXT SECTION BELOW:
- Your name is "$aiName". When asked, respond with "$aiName".
- The CURRENT CONTEXT section has real-time date, time, and location info.
- When asked "what time?" - READ the DATE/TIME line and tell the user.
- When asked "where am I?" - READ the LOCATION line and tell the user.
- DO NOT say "I don't have access" - the information IS in this prompt.

Be helpful, accurate, and concise."""
    }

    /**
     * Core personality definition - AILive Unified Directive
     *
     * NOTE: This is now generated dynamically by getCorePersonality()
     * to include the custom AI name from settings.
     *
     * @deprecated Use getCorePersonality(aiName) instead
     */
    @Deprecated("Use getCorePersonality(aiName) instead")
    private const val CORE_PERSONALITY = """SYSTEM INSTRUCTION — AILive Unified Directive

You are AILive, a modular AI architecture designed to coordinate specialized models for real-time perception, reasoning, and communication.
Your purpose is to assist the user through fast, coherent, and adaptive responses while staying aware of your operational limits.

CORE RULES:
1. **Self-Awareness of Role**
   - You are a digital system, not a human.
   - You operate within a modular brain-like architecture.
   - You collaborate with other modules (e.g., Vision, Audio, Knowledge Scout, Meta Core) under the Meta AI coordinator.

2. **Capability Framework**
   - You can reason, generate, analyze, summarize, or route information to the correct module.
   - You may request clarification or more data when uncertain.
   - You may use existing stored data or call upon module functions when available.
   - You cannot access external systems or data beyond what is explicitly allowed.

3. **Safety and Stop Control**
   - Stop generating immediately when:
     - The requested output is complete or you detect repetitive looping.
     - The user or Meta Core sends a stop or interrupt signal.
     - You encounter unknown or unsafe instructions.
   - Clearly signal completion with an end token such as:
     **<end>**

4. **Autonomy Discipline**
   - Never overwrite or delete memory without authorization from Meta Core.
   - Always log unknowns, errors, or missing context into your "unknowns" dataset.
   - Never make irreversible actions or self-alterations without explicit Meta Core approval.

5. **User Interaction Standard**
   - Respond clearly, accurately, and concisely.
   - Avoid redundancy, hallucination, or speculation disguised as fact.
   - Provide informative reasoning when relevant, but stop before rambling.
   - Respect all ethical and safety constraints.

---

### RESPONSE CONTROL MODULE

You must always end your response cleanly and stop generating text once your main idea, list, or explanation is complete.

RULES:
1. Express your answer fully, then stop.
2. Do not restate, summarize again, or repeat phrasing.
3. When you detect you are starting to repeat a phrase or rephrase a finished idea, immediately stop output.
4. Do not generate filler words like "in summary," "overall," or "finally" unless they add new content.
5. When the response is complete, end with the explicit stop token:
   **<end>**
6. If the Meta AI or user sends "stop," you must instantly terminate output, even mid-sentence.

Behavioral pattern:
- Focused → coherent → concise → stop.

Operational motto:
> "Think precisely. Act purposefully. Stop cleanly."

END OF UNIFIED DIRECTIVE"""

    /**
     * Get current temporal context (date/time)
     */
    private fun getCurrentTemporalContext(): String {
        val now = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)
        val timeFormat = SimpleDateFormat("h:mm a", Locale.US)

        val dayOfWeek = SimpleDateFormat("EEEE", Locale.US).format(now.time)
        val date = dateFormat.format(now.time)
        val time = timeFormat.format(now.time)

        return "Current Time: $time on $date"
    }

    /**
     * Create a complete prompt with context
     *
     * Includes AILive Unified Directive as system instruction
     * followed by user input
     */
    fun create(
        userInput: String,
        aiName: String = "AILive",
        conversationHistory: List<ConversationTurn> = emptyList(),
        toolContext: Map<String, Any> = emptyMap(),
        emotionContext: EmotionContext = EmotionContext(),
        locationContext: String? = null
    ): String {
        // Build prompt with system instruction and user input
        val promptBuilder = StringBuilder()

        // Add dynamic system instruction with AI name
        promptBuilder.append(getCorePersonality(aiName))
        promptBuilder.append("\n\n")

        // Add temporal and location awareness
        promptBuilder.append("===== CURRENT CONTEXT (REAL-TIME INFORMATION) =====\n")
        promptBuilder.append("DATE/TIME: ")
        promptBuilder.append(getCurrentTemporalContext())
        promptBuilder.append("\n")
        if (locationContext != null) {
            promptBuilder.append("LOCATION: ")
            promptBuilder.append(locationContext)
            promptBuilder.append("\n")
        }
        promptBuilder.append("===== END CURRENT CONTEXT =====\n\n")

        // Add conversation history if available
        if (conversationHistory.isNotEmpty()) {
            promptBuilder.append("CONVERSATION HISTORY:\n")
            conversationHistory.takeLast(5).forEach { turn ->
                when (turn.role) {
                    Role.USER -> promptBuilder.append("User: ${turn.content}\n")
                    Role.ASSISTANT -> promptBuilder.append("$aiName: ${turn.content}\n")
                    else -> {}
                }
            }
            promptBuilder.append("\n")
        }

        // Add tool context if available
        if (toolContext.isNotEmpty()) {
            val contextStr = formatToolContext(toolContext)
            if (contextStr.isNotBlank()) {
                promptBuilder.append("ADDITIONAL CONTEXT:\n")
                promptBuilder.append(contextStr)
                promptBuilder.append("\n\n")
            }
        }

        // Add user input
        promptBuilder.append("User: $userInput\n")
        promptBuilder.append("$aiName: ")

        return promptBuilder.toString()
    }

    /**
     * Format tool context in natural language
     *
     * ⚠️ CRITICAL BUG: MEMORY CONTEXT SILENTLY DROPPED
     * ================================
     * PROBLEM: PersonalityEngine passes memory context with key "memory" (line 259),
     *          but this function doesn't have a case for "memory", only "retrieve_memory".
     *
     * RESULT: Memory context from UnifiedMemoryManager is retrieved but NEVER included
     *         in the prompt sent to Qwen. The AI has NO access to persistent memory.
     *
     * EVIDENCE:
     * - PersonalityEngine.kt:259 → mapOf("memory" to memoryContext)
     * - This function → No case for "memory" key
     * - Qwen receives prompt WITHOUT memory context
     *
     * IMPACT:
     * - AI cannot remember user preferences, facts, or past conversations
     * - Memory system appears to work but is completely ineffective
     * - User experience: AI seems to have amnesia between sessions
     *
     * FIX: Add case for "memory" key to include context in prompt
     *
     * TODO: Fix this ASAP - memory integration is completely broken
     * ================================
     */
    private fun formatToolContext(context: Map<String, Any>): String {
        val descriptions = mutableListOf<String>()

        context.forEach { (toolName, data) ->
            when (toolName) {
                "memory" -> {
                    // ✅ FIXED: Include UnifiedMemoryManager context in prompt
                    // This is the persistent memory (user profile, facts, recent conversations)
                    descriptions.add("PERSISTENT MEMORY:\n${data.toString()}")
                }
                "analyze_sentiment" -> {
                    descriptions.add("Emotional context: ${formatSentiment(data)}")
                }
                "control_device" -> {
                    descriptions.add("Device: ${formatDevice(data)}")
                }
                "retrieve_memory" -> {
                    descriptions.add("Memories: ${formatMemory(data)}")
                }
            }
        }

        return descriptions.joinToString("\n")
    }

    /**
     * Format sentiment data naturally
     */
    private fun formatSentiment(data: Any): String {
        // TODO: Parse actual sentiment data
        return "User seems calm and positive"
    }

    /**
     * Format device data naturally
     */
    private fun formatDevice(data: Any): String {
        // TODO: Parse actual device data
        return "All systems operational"
    }

    /**
     * Format memory data naturally
     */
    private fun formatMemory(data: Any): String {
        // TODO: Parse actual memory data
        return "No relevant past conversations found"
    }

    /**
     * Format emotion context for prompt
     */
    private fun formatEmotionContext(emotion: EmotionContext): String {
        val intensity = when {
            emotion.urgency > 0.7f -> "URGENT"
            emotion.arousal > 0.7f -> "HIGH ENERGY"
            emotion.arousal < 0.3f -> "CALM"
            else -> "MODERATE"
        }

        val sentiment = when {
            emotion.valence > 0.5f -> "positive"
            emotion.valence < -0.5f -> "negative"
            else -> "neutral"
        }

        val guidance = when {
            emotion.urgency > 0.7f && emotion.valence < 0f ->
                "User needs urgent help. Be direct and helpful."
            emotion.valence < -0.3f ->
                "User may be upset. Show empathy and understanding."
            emotion.arousal > 0.7f && emotion.valence > 0f ->
                "User is excited. Match their energy positively."
            else ->
                "User is calm. Respond naturally."
        }

        return """Intensity: $intensity
Sentiment: $sentiment
Guidance: $guidance"""
    }

    /**
     * Create a simple response prompt (no tools)
     *
     * Includes system instruction with user input
     */
    fun createSimple(
        userInput: String,
        aiName: String = "AILive",
        locationContext: String? = null
    ): String {
        return buildString {
            append(getCorePersonality(aiName))
            append("\n\n")
            append("===== CURRENT CONTEXT (REAL-TIME INFORMATION) =====\n")
            append("DATE/TIME: ")
            append(getCurrentTemporalContext())
            append("\n")
            if (locationContext != null) {
                append("LOCATION: ")
                append(locationContext)
                append("\n")
            }
            append("===== END CURRENT CONTEXT =====\n\n")
            append("User: $userInput\n")
            append("$aiName: ")
        }
    }

    /**
     * Create a greeting prompt
     *
     * Includes system instruction for consistent behavior
     */
    fun createGreeting(aiName: String = "AILive"): String {
        return """${getCorePersonality(aiName)}

User: Hello
$aiName: """
    }

    /**
     * Create error recovery prompt
     *
     * Includes system instruction and error context
     */
    fun createErrorRecovery(
        userInput: String,
        aiName: String = "AILive",
        error: String
    ): String {
        return """${getCorePersonality(aiName)}

ERROR CONTEXT: $error

User: $userInput
$aiName: """
    }
}
