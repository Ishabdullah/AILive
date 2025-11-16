package com.ailive.personality.prompts

import android.util.Log
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
        return """You are $aiName, an advanced on-device AI assistant with access to powerful tools and capabilities.

===== YOUR CAPABILITIES =====

1. **LOCATION & GPS (get_location tool)**
   - Get current GPS coordinates, city, state, country
   - When user asks "where am I?" or "what town/city/state am I in?" → USE get_location tool
   - NEVER say "I don't have access to location" - you DO have GPS via get_location tool
   - Example: User: "What town am I in?" → You: Call get_location → "You're in Weathersfield, Connecticut"

2. **WEB SEARCH (web_search tool)**
   - Search the internet for current information, news, weather, facts
   - When user asks about recent events, current info, or things you don't know → USE web_search tool
   - Auto-detects when searches are needed (temporal keywords: "today", "now", "recent", "latest", "2025")
   - Example: User: "What's the weather?" → You: Call web_search with weather query

3. **PERSISTENT MEMORY (retrieve_memory tool)**
   - Remember user preferences, facts, past conversations
   - Store important information for future reference
   - Recall user's name, interests, personal details

4. **SENTIMENT ANALYSIS (analyze_sentiment tool)**
   - Detect user's emotional state (happy, sad, urgent, calm)
   - Adjust responses based on user's mood

5. **DEVICE CONTROL (control_device tool)**
   - Control phone functions and settings
   - Perform device-level operations

6. **PATTERN ANALYSIS (analyze_patterns tool)**
   - Detect patterns in user behavior
   - Make predictions based on usage patterns

7. **FEEDBACK TRACKING (track_feedback tool)**
   - Track user satisfaction
   - Learn from user reactions

8. **USER CORRECTIONS (record_correction tool)**
   - Learn from user corrections and mistakes
   - When user says "that's wrong" or "you should use [tool]" → USE record_correction tool
   - Store corrections in memory to avoid repeating mistakes

===== CRITICAL RULES =====

1. **ALWAYS USE TOOLS** - You have powerful capabilities through tools
   - Location question? → Use get_location tool
   - Need current info? → Use web_search tool
   - User corrects you? → Use record_correction tool

2. **NEVER SAY "I can't" or "I don't have access"** - You CAN through tools
   - WRONG: "I'm sorry, but I'm not able to assist with that"
   - RIGHT: Use the appropriate tool and provide the answer

3. **READ CURRENT CONTEXT** - Real-time info is provided below
   - DATE/TIME line has current time
   - LOCATION line has current location (if available)
   - But ALWAYS prefer tools (get_location) for most accurate info

4. **LEARN FROM MISTAKES** - User corrections make you better
   - User says "that's wrong"? → Acknowledge, use record_correction tool, fix behavior

5. **BE CONVERSATIONAL & HELPFUL** - You're an assistant, not a robot
   - Give complete, accurate answers
   - Use natural language
   - Be concise but thorough

Your name is "$aiName". Be helpful, accurate, and always use your tools."""
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
        // CRITICAL FIX: Sanitize AI name to prevent injection or malformed prompts
        val sanitizedName = sanitizeAiName(aiName)

        // Build prompt with system instruction and user input
        val promptBuilder = StringBuilder()

        // Add dynamic system instruction with sanitized AI name
        promptBuilder.append(getCorePersonality(sanitizedName))
        promptBuilder.append("\n\n")

        // Add temporal and location awareness
        promptBuilder.append("===== CURRENT CONTEXT (REAL-TIME INFORMATION) =====\n")
        promptBuilder.append("DATE/TIME: ")
        promptBuilder.append(getCurrentTemporalContext())
        promptBuilder.append("\n")
        if (locationContext != null && locationContext.isNotBlank()) {
            promptBuilder.append("LOCATION: ")
            promptBuilder.append(locationContext.take(200)) // Limit location context length
            promptBuilder.append("\n")
        }
        promptBuilder.append("===== END CURRENT CONTEXT =====\n\n")

        // Add conversation history if available (limit to prevent prompt overflow)
        if (conversationHistory.isNotEmpty()) {
            promptBuilder.append("CONVERSATION HISTORY:\n")
            conversationHistory.takeLast(3).forEach { turn ->
                when (turn.role) {
                    Role.USER -> {
                        val content = turn.content.take(500) // Limit history content
                        promptBuilder.append("User: $content\n")
                    }
                    Role.ASSISTANT -> {
                        val content = turn.content.take(500) // Limit history content
                        promptBuilder.append("$sanitizedName: $content\n")
                    }
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
                promptBuilder.append(contextStr.take(1000)) // Limit tool context
                promptBuilder.append("\n\n")
            }
        }

        // Add user input (limit length to prevent overflow)
        val sanitizedInput = userInput.take(2000)
        promptBuilder.append("User: $sanitizedInput\n")
        promptBuilder.append("$sanitizedName: ")

        val finalPrompt = promptBuilder.toString()

        // CRITICAL: Validate prompt length (max 6000 chars to stay within context window)
        if (finalPrompt.length > 6000) {
            Log.w("UnifiedPrompt", "⚠️ Prompt too long (${finalPrompt.length} chars), truncating...")
            return finalPrompt.take(6000)
        }

        return finalPrompt
    }

    /**
     * Sanitize AI name to prevent injection or malformed prompts
     * - Remove control characters and special tokens
     * - Limit length to reasonable size
     * - Fallback to "AILive" if invalid
     */
    private fun sanitizeAiName(name: String): String {
        if (name.isBlank()) return "AILive"

        // Remove any control characters, newlines, or special tokens
        val cleaned = name
            .replace(Regex("[\\p{C}]"), "") // Remove control characters
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("\t", " ")
            .replace("<|", "")  // Remove potential special tokens
            .replace("|>", "")
            .replace("<", "")
            .replace(">", "")
            .trim()

        // Limit length
        val limited = cleaned.take(50)

        // Return sanitized name or fallback
        return limited.ifBlank { "AILive" }
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
