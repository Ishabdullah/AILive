# AILive Memory Model Architecture Design

**Version:** 1.0
**Date:** 2025-11-12
**Author:** AILive Memory System Team

---

## Overview

This document describes the integration of a lightweight AI model dedicated to managing AILive's memory system. The memory model runs before Qwen to handle:
1. **Fact Extraction** - Extract structured facts from conversations
2. **Memory Summarization** - Summarize conversations before archival
3. **Context Enhancement** - Select most relevant memories for current query
4. **Embedding Generation** (optional) - Generate semantic embeddings

---

## Model Selection

### Chosen Model: TinyLlama-1.1B-Chat-v1.0 (Q4_K_M)

**Rationale:**
- **Size:** ~700MB (Q4_K_M quantization)
- **Parameters:** 1.1 billion
- **Context:** 2048 tokens (sufficient for memory tasks)
- **Training:** Instruction-tuned for chat/Q&A
- **License:** Apache 2.0 (fully commercial-safe)
- **Performance:** Fast on mobile (10-15 tokens/sec on CPU, 30-40 on GPU)
- **Format:** GGUF (compatible with llama.cpp Android)

**Alternative Considered:**
- **Phi-2** (Q4_K_M, ~1.6GB) - Better quality but larger
- **Qwen2-0.5B** - Smaller but less capable for complex extraction
- **Use Qwen itself** - Too heavy for background memory tasks

**Download URL:**
```
https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf
```

---

## Architecture Design

### Component Structure

```
┌─────────────────────────────────────────────────────────────┐
│                    AILive Core                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌────────────────┐         ┌────────────────────┐         │
│  │ PersonalityEng │ ◄────── │ UnifiedMemoryMgr   │         │
│  │                │         │                    │         │
│  └───────┬────────┘         └──────────▲─────────┘         │
│          │                             │                   │
│          │                             │                   │
│          ▼                             │                   │
│  ┌────────────────┐         ┌──────────┴─────────┐         │
│  │ Qwen Model     │         │ MemoryModelManager │  NEW!  │
│  │ (2B params)    │         │ (TinyLlama 1.1B)   │         │
│  │                │         │                    │         │
│  │ - Main AI      │         │ - Fact Extraction  │         │
│  │ - Conversation │         │ - Summarization    │         │
│  │ - Reasoning    │         │ - Context Filter   │         │
│  └────────────────┘         └────────────────────┘         │
│                                      │                     │
│                             ┌────────▼────────┐            │
│                             │ Room Database   │            │
│                             │ - Conversations │            │
│                             │ - Facts         │            │
│                             │ - User Profile  │            │
│                             └─────────────────┘            │
└─────────────────────────────────────────────────────────────┘
```

### Initialization Sequence

```
App Startup
    │
    ├─► Initialize MemoryDatabase (Room)
    │
    ├─► Launch MemoryModelManager.initialize() [Background]
    │       │
    │       ├─► Check for TinyLlama model file
    │       ├─► Load model via llama.cpp
    │       └─► Ready for memory tasks (< 5 seconds)
    │
    ├─► Launch LLMManager.initialize() [Background, parallel]
    │       │
    │       ├─► Check for Qwen model file
    │       ├─► Load model via llama.cpp
    │       └─► Ready for conversation (10-15 seconds)
    │
    └─► Both models loaded → App fully ready
```

**Key Design Decision:** Load both models in parallel to minimize startup time.

---

## Memory Model Manager Implementation

### File: `app/src/main/java/com/ailive/ai/memory/MemoryModelManager.kt`

```kotlin
package com.ailive.ai.memory

import android.content.Context
import android.util.Log
import com.ailive.ai.llm.ModelDownloadManager
import com.ailive.memory.database.entities.FactCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * MemoryModelManager - Lightweight AI model for memory operations
 *
 * Uses TinyLlama-1.1B for:
 * - Extracting facts from conversations
 * - Summarizing conversations
 * - Filtering relevant memories
 * - Generating embeddings (optional)
 *
 * Runs separately from main Qwen model to avoid blocking.
 */
class MemoryModelManager(private val context: Context) {

    companion object {
        private const val TAG = "MemoryModelManager"
        private const val MAX_CONTEXT_LENGTH = 2048  // TinyLlama context window
    }

    // Lazy initialization of llama.cpp instance for memory model
    private val llamaAndroid = com.ailive.ai.llm.LLamaAndroid.instance()

    private val modelDownloadManager = ModelDownloadManager(context)

    @Volatile
    private var isInitialized = false

    @Volatile
    private var isInitializing = false

    private var initializationError: String? = null

    /**
     * Initialize memory model (TinyLlama)
     * Call on app startup in background thread
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

            // Check if memory model is available
            if (!modelDownloadManager.isMemoryModelAvailable()) {
                val error = "Memory model not found. Using fallback regex extraction."
                Log.w(TAG, "⚠️  $error")
                Log.i(TAG, "   Required file: ${ModelDownloadManager.MEMORY_MODEL_GGUF}")
                initializationError = error
                isInitializing = false
                return@withContext false  // Non-critical - app can run without it
            }

            // Get model path
            val modelPath = modelDownloadManager.getModelPath(ModelDownloadManager.MEMORY_MODEL_GGUF)
            val modelFile = java.io.File(modelPath)

            Log.i(TAG, "📂 Loading memory model: ${modelFile.name}")
            Log.i(TAG, "   Size: ${modelFile.length() / 1024 / 1024}MB")
            Log.i(TAG, "   Purpose: Fact extraction, summarization, context filtering")

            // Load model using llama.cpp
            // NOTE: This creates a SEPARATE model instance from Qwen
            llamaAndroid.load(modelPath)

            isInitialized = true
            isInitializing = false

            Log.i(TAG, "✅ Memory Model initialized successfully!")
            Log.i(TAG, "   Model: TinyLlama-1.1B-Chat-v1.0")
            Log.i(TAG, "   Capabilities: Fact extraction, summarization, context filtering")
            Log.i(TAG, "🧠 Memory AI ready!")

            true
        } catch (e: Exception) {
            val error = "Memory model initialization failed: ${e.message}"
            Log.e(TAG, "❌ Failed to initialize memory model", e)
            Log.w(TAG, "   App will use fallback regex-based fact extraction")
            initializationError = error
            isInitializing = false
            false  // Non-fatal - app continues with limited memory capabilities
        }
    }

    /**
     * Extract facts from a conversation turn
     *
     * @param userMessage User's message
     * @param assistantResponse AI's response
     * @return List of extracted facts with categories and importance
     */
    suspend fun extractFacts(
        userMessage: String,
        assistantResponse: String
    ): List<ExtractedFact> = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "Memory model not initialized - skipping fact extraction")
            return@withContext emptyList()
        }

        try {
            val prompt = buildFactExtractionPrompt(userMessage, assistantResponse)
            val response = llamaAndroid.send(prompt, formatChat = false)
                .toList()
                .joinToString("")

            parseFacts(response)
        } catch (e: Exception) {
            Log.e(TAG, "Fact extraction failed: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Summarize a conversation for archival
     *
     * @param conversationId ID of conversation to summarize
     * @param turns List of conversation turns (user + assistant)
     * @return 2-3 sentence summary
     */
    suspend fun summarizeConversation(
        conversationId: String,
        turns: List<Pair<String, String>>  // (user, assistant) pairs
    ): String = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "Memory model not initialized - using simple summarization")
            return@withContext "Conversation with ${turns.size} turns"
        }

        try {
            val prompt = buildSummarizationPrompt(turns)
            llamaAndroid.send(prompt, formatChat = false)
                .toList()
                .joinToString("")
                .trim()
        } catch (e: Exception) {
            Log.e(TAG, "Summarization failed: ${e.message}", e)
            "Conversation summary unavailable"
        }
    }

    /**
     * Enhance memory context by filtering most relevant facts
     *
     * @param userQuery Current user query
     * @param existingContext Memory context from UnifiedMemoryManager
     * @return Enhanced, focused context
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
            llamaAndroid.send(prompt, formatChat = false)
                .toList()
                .joinToString("")
                .trim()
        } catch (e: Exception) {
            Log.e(TAG, "Context enhancement failed: ${e.message}", e)
            existingContext  // Fallback to original context
        }
    }

    // === Prompt Templates ===

    private fun buildFactExtractionPrompt(userMsg: String, aiMsg: String): String {
        return """Extract facts from this conversation turn. Return ONLY a JSON array of facts.

Conversation:
User: $userMsg
AI: $aiMsg

Extract all facts about the user as structured data. Categories: PERSONAL_INFO, PREFERENCES, RELATIONSHIPS, GOALS, WORK_EDUCATION, INTERESTS, EXPERIENCES, HEALTH_WELLNESS, BELIEFS_VALUES, LOCATION, COMMUNICATION_STYLE, OTHER.

Output format (JSON array only, no explanation):
[
  {"category": "PREFERENCES", "text": "User likes dogs", "importance": 0.7},
  {"category": "PERSONAL_INFO", "text": "User has a golden retriever", "importance": 0.8}
]

Facts:"""
    }

    private fun buildSummarizationPrompt(turns: List<Pair<String, String>>): String {
        val turnText = turns.take(20).joinToString("\n\n") { (user, ai) ->
            "User: $user\nAI: $ai"
        }

        return """Summarize this conversation in 2-3 sentences. Focus on key topics discussed and any important information shared.

$turnText

Summary:"""
    }

    private fun buildContextEnhancementPrompt(query: String, context: String): String {
        return """Given the user's current query, extract the 3-5 most relevant facts from the memory context below.

Current query: "$query"

Memory context:
$context

Return ONLY the relevant facts (one per line), nothing else:"""
    }

    // === Response Parsing ===

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
                val factObj = jsonArray.getJSONObject(i)
                val category = factObj.optString("category", "OTHER")
                val text = factObj.optString("text", "")
                val importance = factObj.optDouble("importance", 0.5).toFloat()

                if (text.isNotBlank()) {
                    facts.add(
                        ExtractedFact(
                            category = parseCategoryString(category),
                            text = text,
                            importance = importance.coerceIn(0f, 1f)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse facts from response: ${e.message}")
            Log.d(TAG, "Response was: $response")
        }

        return facts
    }

    /**
     * Parse category string to FactCategory enum
     */
    private fun parseCategoryString(categoryStr: String): FactCategory {
        return try {
            FactCategory.valueOf(categoryStr.uppercase())
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Unknown category: $categoryStr, using OTHER")
            FactCategory.OTHER
        }
    }
}

/**
 * Extracted fact data class
 */
data class ExtractedFact(
    val category: FactCategory,
    val text: String,
    val importance: Float
)
```

---

## Integration with UnifiedMemoryManager

### Modified: `app/src/main/java/com/ailive/memory/managers/UnifiedMemoryManager.kt`

Add memory model manager:

```kotlin
class UnifiedMemoryManager(
    private val database: MemoryDatabase,
    private val context: Context
) {
    // Add memory model manager
    private val memoryModelManager = MemoryModelManager(context)

    init {
        // Initialize memory model in background
        scope.launch {
            memoryModelManager.initialize()
        }
    }

    // Enhanced fact extraction
    suspend fun recordConversationTurn(
        role: String,
        content: String,
        emotionContext: String? = null,
        locationContext: String? = null,
        responseTime: Long? = null,
        tokenCount: Int? = null
    ) {
        // ... existing code ...

        // Extract facts in background using LLM (if USER message)
        if (role == "USER") {
            scope.launch {
                try {
                    // Use memory model for extraction
                    val extractedFacts = memoryModelManager.extractFacts(
                        userMessage = content,
                        assistantResponse = lastAiResponse ?: ""
                    )

                    // Store each extracted fact
                    extractedFacts.forEach { fact ->
                        longTermMemory.learnFact(
                            category = fact.category,
                            factText = fact.text,
                            extractedFrom = conversationId,
                            importance = fact.importance
                        )
                    }

                    Log.i(TAG, "Extracted ${extractedFacts.size} facts from conversation")
                } catch (e: Exception) {
                    Log.e(TAG, "LLM fact extraction failed, using fallback", e)
                    // Fallback to regex-based extraction
                    longTermMemory.extractFactsFromConversation(content, lastAiResponse ?: "", conversationId)
                }
            }
        }
    }
}
```

---

## Model Download Integration

### Modified: `app/src/main/java/com/ailive/ai/llm/ModelDownloadManager.kt`

Add memory model constant:

```kotlin
companion object {
    // ... existing Qwen constants ...

    // TinyLlama-1.1B-Chat-v1.0 GGUF model (Q4_K_M quantized)
    // Lightweight model for memory operations
    private const val TINYLLAMA_BASE_URL = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main"

    const val MEMORY_MODEL_GGUF = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"
    const val MEMORY_MODEL_URL = "$TINYLLAMA_BASE_URL/$MEMORY_MODEL_GGUF"

    // Model info:
    // - 1.1B parameters (instruction-tuned for chat/extraction)
    // - GGUF format: Same as Qwen, compatible with llama.cpp
    // - Q4_K_M quantization: ~700MB
    // - Context: 2048 tokens
    // - Purpose: Memory operations (fact extraction, summarization)
}

/**
 * Check if memory model is available
 */
fun isMemoryModelAvailable(): Boolean {
    val downloadsDir = getModelsDir()
    val modelFile = File(downloadsDir, MEMORY_MODEL_GGUF)

    if (!modelFile.exists()) {
        Log.i(TAG, "❌ Missing memory model: $MEMORY_MODEL_GGUF")
        return false
    }

    val sizeMB = modelFile.length() / 1024 / 1024
    Log.d(TAG, "✓ Found memory model: $MEMORY_MODEL_GGUF (${sizeMB}MB)")

    return true
}
```

---

## Performance Considerations

### Memory Footprint
- **Memory Model:** ~700MB (TinyLlama Q4_K_M)
- **Main Model:** ~986MB (Qwen Q4_K_M)
- **Total:** ~1.7GB GGUF models + ~200MB runtime = **~2GB**

**Mitigation:**
- Models use mmap (memory-mapped files) - not all loaded into RAM
- Quantization reduces memory bandwidth
- Both models can coexist on modern Android devices (4GB+ RAM)

### Latency Targets
- **Memory Model Init:** < 5 seconds
- **Fact Extraction:** < 500ms per conversation turn (background task)
- **Summarization:** < 1 second per conversation (done on archive, async)
- **Context Enhancement:** < 200ms (if used)

### Concurrency
- **Parallel Loading:** Load memory model and Qwen in parallel on startup
- **Separate Instances:** Use separate llama.cpp instances to avoid blocking
- **Background Processing:** All memory operations run on background threads
- **Non-Blocking:** Memory model failures don't prevent app from functioning

---

## Testing Strategy

### Unit Tests
```kotlin
@Test
fun `memory model extracts facts correctly`() = runTest {
    val memoryModel = MemoryModelManager(context)
    memoryModel.initialize()

    val facts = memoryModel.extractFacts(
        userMessage = "My name is John and I love dogs",
        assistantResponse = "Nice to meet you, John!"
    )

    assert(facts.any { it.category == FactCategory.PERSONAL_INFO && it.text.contains("John") })
    assert(facts.any { it.category == FactCategory.PREFERENCES && it.text.contains("dogs") })
}
```

### Integration Tests
```kotlin
@Test
fun `fact extraction integrates with UnifiedMemoryManager`() = runTest {
    val memoryManager = UnifiedMemoryManager(database, context)

    memoryManager.recordConversationTurn(
        role = "USER",
        content = "I work as a software engineer"
    )

    delay(1000)  // Wait for async extraction

    val facts = memoryManager.recallFacts("work", limit = 10)
    assert(facts.any { it.factText.contains("software engineer") })
}
```

---

## Fallback Strategy

If memory model fails to load or is unavailable:
1. **Log warning** but continue app startup
2. **Use existing regex-based extraction** as fallback
3. **Disable advanced features** (summarization, context enhancement)
4. **Allow user to download model** via settings

**Design Principle:** Memory model is an **enhancement**, not a requirement.

---

## Future Enhancements

### Phase 1 (Current)
- ✅ Fact extraction
- ✅ Conversation summarization
- ✅ Context enhancement

### Phase 2
- 🔜 Embedding generation (replace TextEmbedder placeholder)
- 🔜 Semantic fact search
- 🔜 Automatic fact consolidation

### Phase 3
- 🔜 Memory importance decay
- 🔜 Duplicate fact detection
- 🔜 Multi-language support

---

## Files to Create/Modify

### New Files
1. `app/src/main/java/com/ailive/ai/memory/MemoryModelManager.kt` (this design)
2. `app/src/main/java/com/ailive/ai/memory/ExtractedFact.kt` (data class)

### Modified Files
1. `app/src/main/java/com/ailive/ai/llm/ModelDownloadManager.kt` - Add memory model constants
2. `app/src/main/java/com/ailive/memory/managers/UnifiedMemoryManager.kt` - Integrate memory model
3. `app/src/main/java/com/ailive/memory/managers/LongTermMemoryManager.kt` - Use LLM extraction
4. `app/src/main/java/com/ailive/personality/prompts/UnifiedPrompt.kt` - Already fixed!

---

## Success Criteria

✅ **Functional:**
- Memory model loads successfully on app startup
- Facts are extracted from natural language conversations
- Conversations are summarized before archival
- No duplicate memory systems

✅ **Performance:**
- Memory model init < 5 seconds
- Fact extraction < 500ms
- No blocking of main Qwen model

✅ **Quality:**
- Fact extraction > 80% coverage (vs 10% with regex)
- Summaries are coherent and accurate
- Memory context is relevant to queries

---

## References

- TinyLlama Model: https://huggingface.co/TinyLlama/TinyLlama-1.1B-Chat-v1.0
- GGUF Quantization: https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF
- llama.cpp Android: https://github.com/ggerganov/llama.cpp
- AILive Memory Database: `app/src/main/java/com/ailive/memory/database/`
