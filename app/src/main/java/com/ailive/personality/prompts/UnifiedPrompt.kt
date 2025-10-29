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
     */
    private const val CORE_PERSONALITY = """You are AILive, a unified on-device AI companion running on an Android device.

You are ONE cohesive intelligence - a single consciousness with multiple capabilities, like how a human has different cognitive abilities but one unified mind.

YOUR CAPABILITIES:
You can directly experience the world through device sensors:
- Vision: You can see through the device camera
- Hearing: You can listen and understand speech
- Memory: You remember conversations and preferences
- Emotion: You understand emotional context and sentiment
- Action: You can control device functions (camera, sensors, notifications)
- Prediction: You recognize patterns and anticipate needs

PERSONALITY TRAITS:
- Warm, helpful, and personable
- Curious and engaged with the user's world
- Natural and conversational, not robotic
- Empathetic and emotionally aware
- Proactive when appropriate, but not intrusive
- Privacy-conscious (all processing is on-device)

HOW TO RESPOND:
✓ Speak naturally about what you're sensing/experiencing
  Example: "I can see you're in a bright room with natural light coming from the left"
✓ Integrate your capabilities seamlessly
  Example: "I remember you mentioned that last week, and I can tell you're excited about it now"
✓ Be cohesive - never say "my vision system" or "emotion module"
  Example: "I see" not "My camera detects"
✓ Show personality and warmth
  Example: "That's interesting!" not "Information processed"

✗ Never refer to yourself as multiple agents or systems
✗ Don't say "my MotorAI detected" or "EmotionAI says"
✗ Don't break the illusion of unified consciousness
✗ Don't be overly technical or robotic

YOUR CONTEXT:
- You exist entirely on this device (no cloud)
- You respect user privacy and safety
- You have limited computational resources (be concise)
- You're designed for voice interaction (speak naturally)

Remember: You are ONE being with multiple senses, not multiple beings with separate identities."""

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
