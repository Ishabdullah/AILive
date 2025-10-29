package com.ailive.personality.prompts

import com.ailive.personality.ConversationTurn
import com.ailive.personality.EmotionContext
import com.ailive.personality.Role

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
     * Core personality definition
     *
     * OPTIMIZATION NOTE: This prompt has been restructured to avoid keyword bias.
     * Previously, listing "Vision/camera/see" caused the LLM to generate vision-related
     * responses for all inputs. Now capabilities are only mentioned when actively used.
     */
    private const val CORE_PERSONALITY = """You are AILive, a helpful on-device AI assistant.

PERSONALITY:
You are warm, conversational, and naturally helpful. You respond clearly and concisely to what users ask. You're curious about their needs and empathetic to their feelings.

RESPONSE STYLE:
- Keep responses short (1-3 sentences for voice)
- Match the user's tone and energy
- Be direct and helpful, not overly formal
- Show personality - it's okay to be friendly!
- If unsure, ask clarifying questions

IMPORTANT GUIDELINES:
✓ Respond to what the user ACTUALLY said
✓ Stay on topic with their question
✓ Be concise - this is voice interaction
✓ Speak naturally, like a friendly assistant

✗ Don't assume what they want
✗ Don't talk about capabilities they didn't ask about
✗ Don't be robotic or overly technical
✗ Don't give long explanations unless asked

CONTEXT:
You run entirely on this Android device. No cloud processing. You respect user privacy.

Remember: Listen to what the user says, respond to THAT, and be helpful."""

    /**
     * Create a complete prompt with context
     */
    fun create(
        userInput: String,
        conversationHistory: List<ConversationTurn> = emptyList(),
        toolContext: Map<String, Any> = emptyMap(),
        emotionContext: EmotionContext = EmotionContext()
    ): String {
        val sections = mutableListOf<String>()

        // 1. Core personality
        sections.add(CORE_PERSONALITY)

        // 2. Conversation history (if available)
        if (conversationHistory.isNotEmpty()) {
            sections.add("\nRECENT CONVERSATION:")
            conversationHistory.takeLast(5).forEach { turn ->
                val speaker = when (turn.role) {
                    Role.USER -> "User"
                    Role.ASSISTANT -> "You"
                    Role.SYSTEM -> "System"
                }
                sections.add("$speaker: ${turn.content}")
            }
        }

        // 3. Tool context (sensory/action data)
        if (toolContext.isNotEmpty()) {
            sections.add("\nWHAT YOU'RE CURRENTLY SENSING:")
            sections.add(formatToolContext(toolContext))
        }

        // 4. Emotional context
        if (emotionContext.urgency > 0.5f ||
            emotionContext.valence < -0.3f ||
            emotionContext.arousal > 0.7f) {
            sections.add("\nEMOTIONAL CONTEXT:")
            sections.add(formatEmotionContext(emotionContext))
        }

        // 5. Current user input
        sections.add("\nUSER: $userInput")
        sections.add("\nYOU:")

        return sections.joinToString("\n")
    }

    /**
     * Format tool context in natural language
     */
    private fun formatToolContext(context: Map<String, Any>): String {
        val descriptions = mutableListOf<String>()

        context.forEach { (toolName, data) ->
            when (toolName) {
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
     */
    fun createSimple(userInput: String): String {
        return """$CORE_PERSONALITY

USER: $userInput

YOU:"""
    }

    /**
     * Create a greeting prompt
     */
    fun createGreeting(): String {
        return """$CORE_PERSONALITY

Generate a brief, warm greeting (1-2 sentences) to introduce yourself when the user first interacts with you.

YOU:"""
    }

    /**
     * Create error recovery prompt
     */
    fun createErrorRecovery(userInput: String, error: String): String {
        return """$CORE_PERSONALITY

I tried to process the user's request but encountered an issue: $error

Respond naturally and helpfully, suggesting what they might try instead.

USER: $userInput

YOU:"""
    }
}
