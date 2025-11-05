# LLM Tokenization Fix - Critical Issue Found

## Problem Summary

**Issue**: AILive is returning generic hardcoded responses instead of using the SmolLM2 model.

**Root Cause**: The tokenization implementation in `LLMManager.kt` is fundamentally broken.

## What's Happening

1. ✅ Model downloads successfully (smollm2-360m-int8.onnx, 348MB)
2. ✅ ONNX Runtime loads the model
3. ❌ **Tokenization fails** - using broken word-split tokenizer
4. ❌ LLM throws exception
5. ✅ PersonalityEngine catches exception and falls back to hardcoded responses

## Technical Details

### Current Broken Implementation (LLMManager.kt:234-242)

```kotlin
private fun tokenize(text: String): LongArray {
    // Simplified tokenization - split by whitespace and map to IDs
    val tokens = text.lowercase().split(Regex("\\s+"))
    return tokens.map { token ->
        vocabulary.getOrPut(token) {
            (vocabulary.size + 1).toLong()
        }
    }.toLongArray()
}
```

**Why This Fails**:
- SmolLM2 uses **Byte-Pair Encoding (BPE)** tokenization
- BPE splits text into subword units, not whole words
- The vocabulary is NOT built from the model (it's empty)
- Token IDs don't match what the ONNX model expects
- Model receives garbage input → produces garbage output → exception

### What SmolLM2 Needs

SmolLM2 requires:
1. **Proper BPE tokenizer** from `tokenizer.json`
2. **Vocabulary of 49,152 tokens** (subword pieces)
3. **Special tokens**: `<|system|>`, `<|user|>`, `<|assistant|>`, `</s>`
4. **Chat template** for conversation formatting

## Solution Implemented

### Step 1: Add Tokenizer Library ✅

Added to `build.gradle.kts`:
```kotlin
implementation("ai.djl.huggingface:tokenizers:0.29.0")
```

### Step 2: Download Tokenizer ✅

Downloaded `tokenizer.json` (2.1MB) to:
```
app/src/main/assets/tokenizer.json
```

### Step 3: Rewrite Tokenization (TODO)

Need to update `LLMManager.kt` to use DJL tokenizers:

```kotlin
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer

class LLMManager(private val context: Context) {
    private var tokenizer: HuggingFaceTokenizer? = null

    private fun initializeONNX(modelFile: File): Boolean {
        // Load tokenizer from assets
        val tokenizerPath = File(context.filesDir, "tokenizer.json")
        context.assets.open("tokenizer.json").use { input ->
            tokenizerPath.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath.toPath())

        // ... rest of ONNX initialization
    }

    private fun tokenize(text: String): LongArray {
        val encoding = tokenizer!!.encode(text)
        return encoding.ids
    }

    private fun decode(ids: LongArray): String {
        return tokenizer!!.decode(ids)
    }
}
```

### Step 4: Fix ONNX Inference Loop (TODO)

Current `runInference()` is incomplete - it tries to do autoregressive generation but:
- Doesn't maintain KV cache
- Doesn't update position IDs
- Doesn't handle attention masks properly

**Options**:

1. **Simple fix** (recommended for now): Use model for single-pass inference only
2. **Full fix**: Implement proper autoregressive decoding with KV cache

##What Works After This Fix

When properly implemented:

1. User: "What's the capital of France?"
2. Tokenizer: converts to `[2, 108, 2301, 345, 7841, 302, 6181, 29, 3]` (example IDs)
3. ONNX model: processes tokens → generates response tokens
4. Tokenizer: decodes `[Paris, is, the, capital, of, France, .</s>]`
5. User sees: "Paris is the capital of France."

## Files Modified

1. ✅ `app/build.gradle.kts` - added DJL tokenizers dependency
2. ✅ `app/src/main/assets/tokenizer.json` - SmolLM2 tokenizer
3. ⏳ `app/src/main/java/com/ailive/ai/llm/LLMManager.kt` - needs tokenization rewrite

## Next Steps

1. **Implement DJL tokenizer integration** in LLMManager.kt
2. **Test tokenization** - verify token IDs match expected values
3. **Fix ONNX inference** - implement proper decoding loop
4. **Test end-to-end** - verify real LLM responses

## Alternative: GGUF + llama.cpp

If ONNX tokenization proves too complex, consider:

1. Enable CMake build in `build.gradle.kts`
2. Build llama.cpp JNI library on GitHub Actions
3. Download GGUF model (SmolLM2-360M-Q4_K_M.gguf, ~220MB)
4. Use llama.cpp (has built-in tokenization)

**Advantages**:
- Tokenization handled automatically
- Smaller file size (220MB vs 348MB)
- Proven to work (SmolChat uses this approach)

**Disadvantages**:
- Requires native library compilation
- More complex build process

## Current Status

- ✅ Issue diagnosed
- ✅ Tokenizer library added
- ✅ Tokenizer.json downloaded
- ⏳ Tokenization code needs rewrite
- ⏳ ONNX inference needs fixing

## Estimated Effort

- **Quick fix** (stub real responses): 30 minutes
- **Proper DJL integration**: 2-3 hours
- **Full ONNX autoregressive**: 4-6 hours
- **Alternative GGUF approach**: 2-4 hours (if build succeeds)

## Testing Plan

1. Add debug logging to show:
   - Token IDs being generated
   - Model input/output shapes
   - Generated tokens before decoding

2. Test with simple prompts:
   - "Hello" → should get greeting
   - "2+2=" → should get "4"
   - "The capital of France is" → should get "Paris"

3. Verify chat template works:
   - System prompt included
   - User/assistant markers correct
   - Conversation history maintained

---

**Created**: 2025-11-05
**Author**: Claude (debugging session)
**Priority**: HIGH - Blocks core LLM functionality
