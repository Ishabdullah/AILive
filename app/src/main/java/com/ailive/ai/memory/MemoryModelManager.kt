package com.ailive.ai.memory

import android.content.Context
import android.util.Log
import com.ailive.ai.llm.LLMManager
import com.ailive.memory.database.entities.FactCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException

/**
 * MemoryModelManager - AI-powered memory operations
 *
 * v1.5: Now uses Qwen (via LLMManager) for memory operations instead of TinyLlama
 *
 * Reason: llama.cpp can only load ONE GGUF model at a time (singleton).
 * Since Qwen is already loaded for conversations and is MORE capable than TinyLlama,
 * we use it for memory operations too.
 *
 * Capabilities:
 * - Extracting facts from conversations using Qwen
 * - Summarizing conversations using Qwen
 * - Filtering relevant memories using Qwen
 *
 * Benefits of using Qwen over TinyLlama:
 * - Better accuracy (2B params vs 1.1B params)
 * - No model loading conflicts (llama.cpp singleton)
 * - Faster (no need to swap models)
 * - More robust fact extraction
 *
 * @author AILive Memory System Team
 * @since v1.4
 * @updated v1.5 - Using Qwen instead of TinyLlama
 */
class MemoryModelManager(private val context: Context) {

    companion object {
        private const val TAG = "MemoryModelManager"
        private const val MAX_RESPONSE_TOKENS = 512  // Limit response length
    }

    // Use LLMManager (which has Qwen loaded) instead of separate llama.cpp instance
    // This avoids the llama.cpp singleton conflict
    private var llmManager: LLMManager? = null

    @Volatile
    private var isInitialized = false

    @Volatile
    private var isInitializing = false

    private var initializationError: String? = null

    /**
     * Initialize memory model
     * v1.5: Now uses Qwen via LLMManager instead of loading TinyLlama
     *
     * @param llmManager The initialized LLMManager instance (with Qwen loaded)
     * @return true if initialization successful, false otherwise
     */
    suspend fun initialize(llmManager: LLMManager? = null): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Log.i(TAG, "Memory model already initialized")
            return@withContext true
        }

        if (isInitializing) {
            Log.w(TAG, "Memory model initialization already in progress")
            return@withContext false
        }

        isInitializing = true
        initializationError = null

        try {
            Log.i(TAG, "🧠 Initializing Memory AI (using Qwen)...")
            Log.i(TAG, "   Purpose: Fact extraction, summarization, context filtering")
            Log.i(TAG, "   Strategy: Using Qwen (main model) instead of separate TinyLlama")
            Log.i(TAG, "   Reason: llama.cpp singleton - only one model at a time")

            // Store reference to LLMManager
            this@MemoryModelManager.llmManager = llmManager

            // Check if Qwen is ready
            if (llmManager == null || !llmManager.isReady()) {
                val error = "LLMManager not ready. Using fallback regex extraction."
                Log.w(TAG, "⚠️  $error")
                Log.i(TAG, "   App will continue with limited memory capabilities")
                initializationError = error
                isInitializing = false
                return@withContext false  // Non-critical - app can run without it
            }

            isInitialized = true
            isInitializing = false

            Log.i(TAG, "✅ Memory AI initialized successfully!")
            Log.i(TAG, "   Model: Qwen2-VL-2B-Instruct (shared with conversation)")
            Log.i(TAG, "   Capabilities: LLM-based fact extraction, summarization, context filtering")
            Log.i(TAG, "   Benefits: Better accuracy, no model conflicts, faster")
            Log.i(TAG, "🧠 Memory AI ready!")

            true
        } catch (e: Exception) {
            val error = "Memory model initialization failed: ${e.message}"
            Log.e(TAG, "❌ Failed to initialize memory model", e)
            Log.w(TAG, "   App will use fallback regex-based fact extraction")
            e.printStackTrace()
            initializationError = error
            isInitializing = false
            false  // Non-fatal - app continues with limited memory capabilities
        }
    }

    /**
     * Extract facts from a conversation turn
     *
     * v1.5: Uses Qwen (via LLMManager) to analyze a user-AI conversation exchange
     * and extract structured facts about the user.
     *
     * @param userMessage User's message
     * @param assistantResponse AI's response
     * @return List of extracted facts with categories and importance scores
     */
    suspend fun extractFacts(
        userMessage: String,
        assistantResponse: String
    ): List<ExtractedFact> = withContext(Dispatchers.IO) {
        if (!isInitialized || llmManager == null) {
            Log.w(TAG, "Memory model not initialized - skipping fact extraction")
            return@withContext emptyList()
        }

        if (userMessage.isBlank()) {
            Log.d(TAG, "Empty user message - skipping fact extraction")
            return@withContext emptyList()
        }

        try {
            val prompt = buildFactExtractionPrompt(userMessage, assistantResponse)
            Log.d(TAG, "Extracting facts from conversation using Qwen...")

            // Use Qwen via LLMManager instead of TinyLlama
            val response = llmManager!!.generate(prompt, agentName = "FactExtractor")

            val facts = parseFacts(response)
            Log.i(TAG, "✅ Extracted ${facts.size} facts from conversation (using Qwen)")
            facts.forEach { fact ->
                Log.d(TAG, "   $fact")
            }

            facts
        } catch (e: Exception) {
            Log.e(TAG, "Fact extraction failed: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Summarize a conversation for archival
     *
     * v1.5: Uses Qwen to generate a 2-3 sentence summary of a conversation
     * to store before archiving old conversations.
     *
     * @param conversationId ID of conversation to summarize
     * @param turns List of conversation turns (user, assistant) pairs
     * @return Concise summary (2-3 sentences)
     */
    suspend fun summarizeConversation(
        conversationId: String,
        turns: List<Pair<String, String>>  // (user, assistant) pairs
    ): String = withContext(Dispatchers.IO) {
        if (!isInitialized || llmManager == null) {
            Log.w(TAG, "Memory model not initialized - using simple summarization")
            return@withContext "Conversation with ${turns.size} turns"
        }

        if (turns.isEmpty()) {
            return@withContext "Empty conversation"
        }

        try {
            val prompt = buildSummarizationPrompt(turns)
            Log.d(TAG, "Summarizing conversation $conversationId with ${turns.size} turns using Qwen...")

            // Use Qwen via LLMManager
            val summary = llmManager!!.generate(prompt, agentName = "Summarizer").trim()

            Log.i(TAG, "✅ Generated summary (using Qwen): ${summary.take(100)}...")
            summary
        } catch (e: Exception) {
            Log.e(TAG, "Summarization failed: ${e.message}", e)
            "Conversation summary unavailable"
        }
    }

    /**
     * Enhance memory context by filtering most relevant facts
     *
     * v1.5: Uses Qwen to select the most relevant facts for the current conversation.
     *
     * @param userQuery Current user query
     * @param existingContext Memory context from UnifiedMemoryManager
     * @return Enhanced, focused context with only relevant facts
     */
    suspend fun enhanceContext(
        userQuery: String,
        existingContext: String
    ): String = withContext(Dispatchers.IO) {
        if (!isInitialized || llmManager == null || existingContext.isBlank()) {
            return@withContext existingContext
        }

        try {
            val prompt = buildContextEnhancementPrompt(userQuery, existingContext)
            Log.d(TAG, "Enhancing memory context for query using Qwen: ${userQuery.take(50)}...")

            // Use Qwen via LLMManager
            val enhanced = llmManager!!.generate(prompt, agentName = "ContextEnhancer").trim()
            enhanced
        } catch (e: Exception) {
            Log.e(TAG, "Context enhancement failed: ${e.message}", e)
            existingContext  // Fallback to original context
        }
    }

    /**
     * Check if memory model is ready for use
     */
    fun isReady(): Boolean = isInitialized

    /**
     * Get initialization error message (if any)
     */
    fun getError(): String? = initializationError

    // ===== PRIVATE HELPER METHODS =====

    /**
     * Build prompt for fact extraction
     */
    private fun buildFactExtractionPrompt(userMsg: String, aiMsg: String): String {
        return """Extract facts from this conversation turn. Return ONLY a JSON array of facts.

Conversation:
User: $userMsg
AI: $aiMsg

Extract all facts about the user as structured data. Categories: PERSONAL_INFO, PREFERENCES, RELATIONSHIPS, GOALS, WORK_EDUCATION, INTERESTS, EXPERIENCES, HEALTH_WELLNESS, BELIEFS_VALUES, LOCATION, COMMUNICATION_STYLE, OTHER.

Guidelines:
- Only extract facts explicitly stated or clearly implied
- Importance: 0.0 (trivial) to 1.0 (critical personal info)
- Be specific and precise
- Don't extract facts about the AI or general knowledge

Output format (JSON array only, no explanation):
[
  {"category": "PREFERENCES", "text": "User likes dogs", "importance": 0.7},
  {"category": "PERSONAL_INFO", "text": "User has a golden retriever named Max", "importance": 0.8}
]

Facts:"""
    }

    /**
     * Build prompt for conversation summarization
     */
    private fun buildSummarizationPrompt(turns: List<Pair<String, String>>): String {
        // Take up to 20 turns to avoid exceeding context window
        val turnText = turns.take(20).joinToString("\n\n") { (user, ai) ->
            "User: $user\nAI: $ai"
        }

        return """Summarize this conversation in 2-3 sentences. Focus on key topics discussed and any important information shared.

$turnText

Summary:"""
    }

    /**
     * Build prompt for context enhancement
     */
    private fun buildContextEnhancementPrompt(query: String, context: String): String {
        return """Given the user's current query, extract the 3-5 most relevant facts from the memory context below.

Current query: "$query"

Memory context:
$context

Return ONLY the relevant facts (one per line), nothing else:"""
    }

    /**
     * Parse JSON fact extraction response
     */
    private fun parseFacts(response: String): List<ExtractedFact> {
        val facts = mutableListOf<ExtractedFact>()

        try {
            // Find JSON array in response (model might add extra text)
            val jsonStart = response.indexOf('[')
            val jsonEnd = response.lastIndexOf(']')

            if (jsonStart == -1 || jsonEnd == -1) {
                Log.w(TAG, "No JSON array found in response")
                return emptyList()
            }

            val jsonText = response.substring(jsonStart, jsonEnd + 1)
            val jsonArray = JSONArray(jsonText)

            for (i in 0 until jsonArray.length()) {
                try {
                    val factObj = jsonArray.getJSONObject(i)
                    val category = factObj.optString("category", "OTHER")
                    val text = factObj.optString("text", "")
                    val importance = factObj.optDouble("importance", 0.5).toFloat()

                    if (text.isNotBlank()) {
                        facts.add(
                            ExtractedFact(
                                category = parseCategoryString(category),
                                text = text.trim(),
                                importance = importance.coerceIn(0f, 1f)
                            )
                        )
                    }
                } catch (e: JSONException) {
                    Log.w(TAG, "Failed to parse fact at index $i: ${e.message}")
                    // Continue with other facts
                }
            }

            Log.d(TAG, "Successfully parsed ${facts.size} facts from JSON response")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse facts from response: ${e.message}")
            Log.d(TAG, "Response was: ${response.take(500)}")
        }

        return facts
    }

    /**
     * Parse category string to FactCategory enum
     */
    private fun parseCategoryString(categoryStr: String): FactCategory {
        return try {
            FactCategory.valueOf(categoryStr.uppercase().replace(" ", "_"))
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Unknown category: $categoryStr, using OTHER")
            FactCategory.OTHER
        }
    }
}
