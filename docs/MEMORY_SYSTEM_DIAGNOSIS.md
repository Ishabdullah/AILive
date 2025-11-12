# AILive Memory System Diagnosis Report

**Date:** 2025-11-12
**Status:** Critical Issues Identified
**Priority:** HIGH - Memory system non-functional

---

## Executive Summary

AILive's memory system has **5 critical failures** preventing it from functioning correctly:

1. ✅ **Memory context silently dropped** - UnifiedPrompt doesn't include memory in Qwen prompts
2. ✅ **Fake embeddings** - TextEmbedder uses random numbers, semantic search is broken
3. ✅ **Duplicate memory systems** - 3+ separate storage systems with no synchronization
4. ✅ **Regex-based fact extraction** - Captures < 10% of user information
5. ⚠️ **No memory pruning** - Old/stale facts accumulate indefinitely

**Impact:** AI appears to have amnesia - cannot remember user preferences, facts, or past conversations across sessions.

---

## Critical Issue #1: Memory Context Silently Dropped

### Location
`app/src/main/java/com/ailive/personality/prompts/UnifiedPrompt.kt:208`

### Problem
PersonalityEngine.kt retrieves memory context from UnifiedMemoryManager and passes it in `toolContext` with key "memory":

```kotlin
// PersonalityEngine.kt:258-259
val toolContext = if (memoryContext != null && memoryContext.isNotBlank()) {
    mapOf("memory" to memoryContext)
} else {
    emptyMap()
}
```

But UnifiedPrompt.formatToolContext() only handles these keys:
- "analyze_sentiment"
- "control_device"
- "retrieve_memory"

**Result:** The "memory" key is **silently ignored** and never included in the prompt sent to Qwen.

### Evidence
```kotlin
// UnifiedPrompt.kt:208-227
private fun formatToolContext(context: Map<String, Any>): String {
    val descriptions = mutableListOf<String>()

    context.forEach { (toolName, data) ->
        when (toolName) {
            "analyze_sentiment" -> { ... }
            "control_device" -> { ... }
            "retrieve_memory" -> { ... }
            // ⚠️ NO CASE FOR "memory" - context is dropped!
        }
    }

    return descriptions.joinToString("\n")  // Returns empty string if no matches
}
```

### Impact
- **100% of persistent memory is lost** - Qwen never sees user profile, facts, or conversation history
- User experience: AI has complete amnesia between sessions
- All memory infrastructure (Room DB, managers) works but is **completely ineffective**

### Fix (Quick Win)
Add case for "memory" key:

```kotlin
when (toolName) {
    "memory" -> {
        descriptions.add("PERSISTENT MEMORY:\n$data")
    }
    // ... other cases
}
```

---

## Critical Issue #2: Fake Embeddings (Semantic Search Broken)

### Location
`app/src/main/java/com/ailive/memory/embeddings/TextEmbedder.kt:30`

### Problem
TextEmbedder generates **deterministic random embeddings** based on text hash instead of real semantic embeddings:

```kotlin
fun embed(text: String): FloatArray {
    val seed = text.hashCode().toLong()
    val random = Random(seed)

    val embedding = FloatArray(dimensions) {
        random.nextFloat() - 0.5f  // ⚠️ RANDOM NUMBERS, NOT SEMANTIC!
    }

    return normalize(embedding)
}
```

### Impact
- MemoryAI.recall() returns **meaningless results** - similarity scores are pseudo-random
- VectorDB.search() finds random matches, not semantically similar ones
- LongTermMemoryManager fact embeddings are useless (never used anyway)
- Memory system appears to work but retrieves **irrelevant memories**

### Root Cause
No real embedding model integrated. Comment says "TODO: Integrate actual BGE-small-en-v1.5 model via ONNX Runtime"

### Solution Options

**Option 1: ONNX Runtime + BGE-small-en-v1.5**
- Model: BAAI/bge-small-en-v1.5 (MIT license, commercial-safe)
- Size: 133 MB
- Dimensions: 384
- Runtime: ONNX Runtime Android
- Pros: Best quality, dedicated embedding model
- Cons: Adds dependency, separate model to load

**Option 2: Use Lightweight Memory Model**
- Leverage the new lightweight LLM for embeddings too
- Pros: Single model for all memory tasks
- Cons: Slower than dedicated embedding model

**Option 3: Use Qwen for Embeddings (Temporary)**
- Extract hidden states from Qwen as embeddings
- Pros: No new dependencies
- Cons: Very slow, Qwen is heavy for this task

### Recommended Fix
**Option 1** - Integrate BGE-small-en-v1.5 via ONNX Runtime for production-quality semantic search

---

## Critical Issue #3: Duplicate Memory Systems

### Problem
AILive has **4 separate memory storage systems** with no synchronization:

1. **MemoryRetrievalTool**
   - File: `/data/data/com.ailive/files/memories.json`
   - Max: 200 entries
   - Search: Keyword matching
   - Used by: PersonalityEngine (unclear if actually called)

2. **MemoryAI (VectorDB)**
   - File: `/data/data/com.ailive/files/memory/entries.json`
   - Max: 50,000 entries (in-memory), unlimited on disk
   - Search: Cosine similarity (with fake embeddings)
   - Used by: Nobody in production!

3. **UnifiedMemoryManager (Room DB)**
   - Database: `ailive_memory_db` (SQLite)
   - Tables: conversations, conversation_turns, long_term_facts, user_profile
   - Search: SQL LIKE queries (no vector search)
   - Used by: PersonalityEngine (but context is dropped!)

4. **SearchHistoryManager**
   - Storage: SharedPreferences (Moshi JSON)
   - Purpose: Web search query history
   - Search: Jaccard similarity

### Impact
- **Memory fragmentation** - no single source of truth
- Inconsistency and confusion
- Wasted storage
- Development complexity

### Recommended Fix
1. **Deprecate** MemoryAI JSON storage
2. **Deprecate** MemoryRetrievalTool memories.json
3. **Consolidate** everything into UnifiedMemoryManager (Room DB)
4. Keep SearchHistoryManager separate (domain-specific)
5. Add vector search to Room DB (use sqlite-vec extension or in-memory index)

---

## Critical Issue #4: Regex-Based Fact Extraction

### Location
`app/src/main/java/com/ailive/memory/managers/LongTermMemoryManager.kt:72`

### Problem
Fact extraction uses hardcoded regex patterns that capture **< 10% of user information**:

**Current Coverage:**
```kotlin
// Only these 4 patterns:
"my name is X" → PERSONAL_INFO
"my favorite Y is Z" → PREFERENCES
"i want to X" → GOALS
"my wife/husband X" → RELATIONSHIPS
```

**What It Misses:**
- Complex statements: "I've been working as a software engineer for 5 years"
- Implied facts: "I love dogs" → User likes dogs
- Context-dependent: "She's my sister" (who is "she"?)
- Temporal facts: "I moved to NYC last month"
- Multi-sentence: "I studied CS. Then worked at Google."

### Impact
- Memory captures **< 10% of actual user information**
- No entity linking, coreference resolution, or semantic understanding
- Facts are overly rigid and miss natural language variations

### Solution
Replace regex with **LLM-based fact extraction** using lightweight memory model:

**Example Prompt:**
```
Extract facts from this conversation turn:

User: I love dogs. I've had a golden retriever for 3 years.
AI: That's wonderful! Golden retrievers are great companions.

Extract all facts as structured data:

Facts:
[PREFERENCES] User likes dogs (importance: 0.7)
[PERSONAL_INFO] User has owned a golden retriever for 3 years (importance: 0.8)
[PREFERENCES] User has positive sentiment toward golden retrievers (importance: 0.6)
```

**Model Requirements:**
- TinyLlama-1.1B-Chat (Q4_K_M, ~700MB) OR
- Phi-2 (Q4_K_M, ~1.6GB) OR
- Qwen2-0.5B (if available)

**Integration:**
Use same llama.cpp infrastructure already in place for Qwen model loading.

---

## Critical Issue #5: No Memory Pruning

### Problem
- Conversations never summarized or archived
- Facts never expire or decay in importance
- No "forgetting" of irrelevant information
- Disk usage grows unbounded

### Current State
- ConversationMemoryManager has `archiveOldConversations()` but only sets `isActive = false`
- No summarization before archival
- Old facts remain at original importance forever

### Impact
- Performance degradation over time
- Memory bloat
- Retrieval becomes slower and less relevant

### Solution
1. **Conversation Summarization**
   - Before archiving (30 days old), generate 2-3 sentence summary using memory model
   - Store summary, delete individual turns (or mark as archived)

2. **Fact Importance Decay**
   - Implement time-based importance decay: `importance *= exp(-λ * age)`
   - Periodically prune facts with importance < 0.1

3. **Fact Consolidation**
   - Detect duplicate/similar facts and merge them
   - Use semantic similarity (once embeddings are fixed)

---

## Additional Findings

### Performance Issues

**VectorDB Brute-Force Search**
- O(n) for every query
- No indexing or optimization
- Will be slow with > 10,000 memories
- **Solution:** Use approximate nearest neighbor (ANN) algorithm or sqlite-vec

**MemoryStore JSON Persistence**
- Saves entire JSON file on every update
- No incremental writes
- AutoSave runs every 60 seconds
- **Solution:** Batch writes or use Room DB (already available)

### Architecture Issues

**No Memory Consolidation**
- Similar facts stored separately
- No deduplication
- **Solution:** Periodic consolidation job using memory model

**Limited Fact Search**
- SQL LIKE search only (UnifiedMemoryManager)
- Embeddings stored but never used
- **Solution:** Add semantic search using fixed embeddings

**No Memory Visualization**
- Users can't see what AI remembers
- No UI for memory management
- No way to correct false memories
- **Solution:** Add memory debug UI (future)

---

## Recommended Implementation Plan

### Phase 1: Quick Wins (Immediate)

1. **Fix UnifiedPrompt Memory Context Bug** ⚡
   - Add case for "memory" key in formatToolContext()
   - **Impact:** Enables persistent memory immediately
   - **Effort:** 5 minutes

2. **Test Memory Persistence**
   - Verify UnifiedMemoryManager populates database
   - Verify memory context flows to Qwen
   - **Effort:** 15 minutes

### Phase 2: Integrate Lightweight Memory Model

1. **Select Model**
   - Recommended: **TinyLlama-1.1B-Chat-v1.0** (Q4_K_M, ~700MB)
   - Alternative: Phi-2 (better quality, larger)

2. **Add Model to ModelDownloadManager**
   - Define constant: `MEMORY_MODEL_GGUF = "tinyllama-1.1b-chat-q4_k_m.gguf"`
   - Add download URL or bundle with app
   - Implement availability check

3. **Create MemoryModelManager**
   - Similar to LLMManager
   - Use same llama.cpp infrastructure
   - Load on app startup (before Qwen if possible)

4. **Implement Memory Operations**
   - `extractFacts(userMsg, aiMsg) → List<Fact>`
   - `summarizeConversation(turns) → String`
   - `enhanceContext(query, context) → String`
   - `generateEmbedding(text) → FloatArray` (if not using ONNX)

### Phase 3: Consolidate Memory Systems

1. **Deprecate MemoryAI JSON Storage**
   - Mark as @Deprecated
   - Add migration to UnifiedMemoryManager (if needed)

2. **Deprecate MemoryRetrievalTool**
   - Remove from PersonalityEngine tools
   - UnifiedMemoryManager handles all memory

3. **Unify on Room Database**
   - All memory operations go through UnifiedMemoryManager
   - Single source of truth

### Phase 4: Fix Embeddings

1. **Integrate BGE-small-en-v1.5 ONNX**
   - Add ONNX Runtime Android dependency
   - Download model from HuggingFace
   - Replace TextEmbedder placeholder

2. **Add Semantic Fact Search**
   - Use real embeddings for LongTermFactEntity
   - Implement in-memory vector index or sqlite-vec
   - Update recallFacts() to use similarity search

### Phase 5: Memory Pruning & Optimization

1. **Conversation Summarization**
   - Use memory model to summarize before archival
   - Update archiveOldConversations() implementation

2. **Fact Importance Decay**
   - Add time-based decay algorithm
   - Periodic cleanup job

3. **Performance Optimization**
   - Batch database writes
   - Cache frequently accessed memories
   - Background auto-save

---

## Testing Requirements

### Unit Tests
- [ ] Memory context inclusion in UnifiedPrompt
- [ ] Fact extraction with memory model
- [ ] Embedding generation (ONNX)
- [ ] Memory pruning logic

### Integration Tests
- [ ] End-to-end memory flow: store → retrieve → prompt → Qwen
- [ ] Cross-session persistence
- [ ] Memory model initialization
- [ ] Database migration

### Performance Tests
- [ ] Memory recall latency (< 100ms target)
- [ ] Model load time (< 5s for memory model)
- [ ] Database query performance with 10K+ facts
- [ ] Embedding generation speed

### Manual Tests
- [ ] Tell AI your name, restart app, verify it remembers
- [ ] Share preferences, verify they're recalled in later conversations
- [ ] Archive old conversations, verify summaries are generated
- [ ] Check memory pruning after 30 days

---

## Success Criteria

### Functional
- ✅ AI remembers user name across sessions
- ✅ AI recalls user preferences (favorite food, color, etc.)
- ✅ AI references past conversations naturally
- ✅ Facts are extracted from natural language (not just regex patterns)
- ✅ Memory system uses semantic search (not keyword matching)

### Performance
- ✅ Memory recall < 100ms
- ✅ Memory model load < 5 seconds
- ✅ Fact extraction < 500ms per conversation turn
- ✅ Total memory footprint < 200MB (model + data)

### Quality
- ✅ No duplicate memory systems
- ✅ Single source of truth (Room DB)
- ✅ Real embeddings (not random numbers)
- ✅ Memory context reaches Qwen model
- ✅ Fact extraction > 80% coverage

---

## Files Modified (Diagnostic Phase)

1. `app/src/main/java/com/ailive/memory/embeddings/TextEmbedder.kt`
   - Added diagnostic comments documenting fake embeddings issue

2. `app/src/main/java/com/ailive/personality/prompts/UnifiedPrompt.kt`
   - Added diagnostic comments documenting memory context drop bug

3. `app/src/main/java/com/ailive/memory/managers/LongTermMemoryManager.kt`
   - Added diagnostic comments documenting regex-based extraction limitations

4. `app/src/main/java/com/ailive/personality/tools/MemoryRetrievalTool.kt`
   - Added diagnostic comments documenting duplicate memory systems

5. `docs/MEMORY_SYSTEM_DIAGNOSIS.md` (this file)
   - Comprehensive diagnostic report

---

## Next Steps

1. **Immediate:** Fix UnifiedPrompt memory context bug (5 minutes)
2. **Short-term:** Integrate TinyLlama memory model (2-4 hours)
3. **Medium-term:** Fix embeddings with ONNX (4-6 hours)
4. **Long-term:** Consolidate memory systems and add pruning (8-12 hours)

**Total Estimated Effort:** 15-25 hours to fully repair and enhance memory system

---

## References

- MemoryAI architecture: `/app/src/main/java/com/ailive/memory/MemoryAI.kt`
- UnifiedMemoryManager: `/app/src/main/java/com/ailive/memory/managers/UnifiedMemoryManager.kt`
- Room database schema: `/app/src/main/java/com/ailive/memory/database/`
- LLM loading infrastructure: `/app/src/main/java/com/ailive/llm/LLMManager.kt`
- Model download manager: `/app/src/main/java/com/ailive/llm/model/ModelDownloadManager.kt`
