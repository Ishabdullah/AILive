package com.ailive.ai.memory

import android.content.Context
import android.llama.cpp.LLamaAndroid
import android.util.Log
import com.ailive.ai.llm.ModelDownloadManager
import com.ailive.memory.database.entities.FactCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException

/**
 * MemoryModelManager - Lightweight AI model for memory operations
 *
 * Uses TinyLlama-1.1B for:
 * - Extracting facts from conversations
 * - Summarizing conversations
 * - Filtering relevant memories
 * - Generating embeddings (future)
 *
 * Runs separately from main Qwen model to avoid blocking.
 * Designed to initialize quickly (< 5 seconds) and run efficiently in background.
 *
 * @author AILive Memory System Team
 * @since v1.4
 */
class MemoryModelManager(private val context: Context) {

    companion object {
        private const val TAG = "MemoryModelManager"
        private const val MAX_CONTEXT_LENGTH = 2048  // TinyLlama context window
        private const val MAX_RESPONSE_TOKENS = 512  // Limit response length
    }

    // Lazy initialization of llama.cpp instance for memory model
    // NOTE: This is a SEPARATE instance from the main Qwen model
    private val llamaAndroid = LLamaAndroid.instance()

    private val modelDownloadManager = ModelDownloadManager(context)

    @Volatile
    private var isInitialized = false

    @Volatile
    private var isInitializing = false

    private var initializationError: String? = null

    /**
     * Initialize memory model (TinyLlama)
     * Call on app startup in background thread
     *
     * @return true if initialization successful, false otherwise
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
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
            Log.i(TAG, "🧠 Initializing Memory Model (TinyLlama-1.1B)...")
            Log.i(TAG, "   Purpose: Fact extraction, summarization, context filtering")

            // Check if memory model is available
            if (!modelDownloadManager.isMemoryModelAvailable()) {
                val error = "Memory model not found. Using fallback regex extraction."
                Log.w(TAG, "⚠️  $error")
                Log.i(TAG, "   Required file: ${ModelDownloadManager.MEMORY_MODEL_GGUF}")
                Log.i(TAG, "   Download from: ${ModelDownloadManager.MEMORY_MODEL_URL}")
                Log.i(TAG, "   App will continue with limited memory capabilities")
                initializationError = error
                isInitializing = false
                return@withContext false  // Non-critical - app can run without it
            }

            // Get model path
            val modelPath = modelDownloadManager.getModelPath(ModelDownloadManager.MEMORY_MODEL_GGUF)
            val modelFile = java.io.File(modelPath)

            Log.i(TAG, "📂 Loading memory model: ${modelFile.name}")
            Log.i(TAG, "   Size: ${modelFile.length() / 1024 / 1024}MB")
            Log.i(TAG, "   Format: GGUF (Q4_K_M quantization)")
            Log.i(TAG, "   Context: $MAX_CONTEXT_LENGTH tokens")

            // Load model using llama.cpp
            // NOTE: This creates a SEPARATE model instance from Qwen
            llamaAndroid.load(modelPath)

            isInitialized = true
            isInitializing = false

            Log.i(TAG, "✅ Memory Model initialized successfully!")
            Log.i(TAG, "   Model: TinyLlama-1.1B-Chat-v1.0")
            Log.i(TAG, "   Capabilities: Fact extraction, summarization, context filtering")
            Log.i(TAG, "   Expected performance: 10-15 tokens/sec (CPU), 30-40 tokens/sec (GPU)")
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
     * Uses the memory model to analyze a user-AI conversation exchange
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
        if (!isInitialized) {
            Log.w(TAG, "Memory model not initialized - skipping fact extraction")
            return@withContext emptyList()
        }

        if (userMessage.isBlank()) {
            Log.d(TAG, "Empty user message - skipping fact extraction")
            return@withContext emptyList()
        }

        try {
            val prompt = buildFactExtractionPrompt(userMessage, assistantResponse)
            Log.d(TAG, "Extracting facts from conversation turn...")

            val responseTokens: List<String> = llamaAndroid.send(prompt, formatChat = false)
                .toList()
            val response = responseTokens.joinToString(separator = "")

            val facts = parseFacts(response)
            Log.i(TAG, "✅ Extracted ${facts.size} facts from conversation")
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
     * Generates a 2-3 sentence summary of a conversation
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
        if (!isInitialized) {
            Log.w(TAG, "Memory model not initialized - using simple summarization")
            return@withContext "Conversation with ${turns.size} turns"
        }

        if (turns.isEmpty()) {
            return@withContext "Empty conversation"
        }

        try {
            val prompt = buildSummarizationPrompt(turns)
            Log.d(TAG, "Summarizing conversation $conversationId with ${turns.size} turns...")

            val responseTokens: List<String> = llamaAndroid.send(prompt, formatChat = false)
                .toList()
            val summary = responseTokens.joinToString(separator = "").trim()

            Log.i(TAG, "✅ Generated summary: ${summary.take(100)}...")
            summary
        } catch (e: Exception) {
            Log.e(TAG, "Summarization failed: ${e.message}", e)
            "Conversation summary unavailable"
        }
    }

    /**
     * Enhance memory context by filtering most relevant facts
     *
     * Given a user query and existing memory context, uses the model
     * to select the most relevant facts for the current conversation.
     *
     * @param userQuery Current user query
     * @param existingContext Memory context from UnifiedMemoryManager
     * @return Enhanced, focused context with only relevant facts
     */
    suspend fun enhanceContext(
        userQuery: String,
        existingContext: String
    ): String = withContext(Dispatchers.IO) {
        if (!isInitialized || existingContext.isBlank()) {
            return@withContext existingContext
        }

        try {
            val prompt = buildContextEnhancementPrompt(userQuery, existingContext)
            Log.d(TAG, "Enhancing memory context for query: ${userQuery.take(50)}...")

            val responseTokens: List<String> = llamaAndroid.send(prompt, formatChat = false)
                .toList()
            responseTokens.joinToString(separator = "").trim()
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
