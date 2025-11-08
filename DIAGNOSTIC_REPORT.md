# AILive LLM Diagnostic Report

**Date:** 2025-11-08
**Issue:** Model downloads successfully but generates generic/fallback responses
**Scope:** Complete investigation of LLM pipeline from download → inference → response

---

## 🔍 Executive Summary

After comprehensive analysis, **5 CRITICAL ISSUES** have been identified that prevent the LLM from working:

1. **CRITICAL**: Chat template format mismatch (TinyLlama vs SmolLM2)
2. **CRITICAL**: Tokenizer incompatibility with model
3. **CRITICAL**: Missing/wrong chat template special tokens
4. **HIGH**: Inference output not being properly decoded
5. **MEDIUM**: Fallback responses masking actual errors

---

## 📋 Complete Pipeline Analysis

### 1. Model Download System ✅ **WORKING**

**Files:** `ModelDownloadManager.kt`, `ModelSetupDialog.kt`

**Download URLs:**
```kotlin
const val ONNX_360M_URL =
  "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct/resolve/main/onnx/model_int8.onnx"
const val ONNX_135M_URL =
  "https://huggingface.co/HuggingFaceTB/SmolLM2-135M-Instruct/resolve/main/onnx/model_int8.onnx"
```

**Storage Location:**
```
Download: /storage/emulated/0/Download/smollm2-360m-int8.onnx
Final:    /data/data/com.ailive/files/models/smollm2-360m-int8.onnx
```

**Status:** ✅ Working correctly
- Downloads successfully
- Moves to app storage
- File validation (1MB minimum) works
- Error handling comprehensive

**Evidence:** Recent fixes added proper validation and error messages.

---

### 2. Model Storage & Discovery ✅ **WORKING**

**File:** `ModelDownloadManager.kt:499-507`

```kotlin
fun getAvailableModels(): List<File> {
    val modelsDir = File(context.filesDir, MODELS_DIR)  // "models"
    if (!modelsDir.exists()) return emptyList()

    return modelsDir.listFiles()?.filter {
        it.isFile && it.name.endsWith(".onnx", ignoreCase = true)
    } ?: emptyList()
}
```

**Storage Path:** `/data/data/com.ailive/files/models/`

**Status:** ✅ Working correctly
- Correctly finds ONNX models
- Filters by extension
- Returns valid File objects

---

### 3. LLM Initialization ⚠️ **PARTIAL ISSUE**

**File:** `LLMManager.kt:75-153`

**Process:**
1. Check for available models ✅
2. Find .onnx file ✅
3. Load with ONNX Runtime ✅
4. Load tokenizer ⚠️ **ISSUE HERE**

**Tokenizer Loading (Line 152-166):**
```kotlin
Log.i(TAG, "📖 Loading tokenizer...")
val tokenizerFile = File(context.filesDir, "tokenizer.json")

// Copy tokenizer from assets to filesDir if not exists
if (!tokenizerFile.exists()) {
    context.assets.open("tokenizer.json").use { input ->
        tokenizerFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
}

// Initialize DJL tokenizer
tokenizer = HuggingFaceTokenizer.newInstance(tokenizerFile.toPath())
```

**Current Tokenizer:** `app/src/main/assets/tokenizer.json` (2.1MB)
**Type:** BPE (Byte Pair Encoding)

**❌ PROBLEM #1: Unknown tokenizer source**
- Where did `tokenizer.json` come from?
- Is it for TinyLlama or SmolLM2?
- No verification that tokenizer matches the model

---

### 4. Chat Format & Prompting ❌ **CRITICAL ISSUE**

**File:** `LLMManager.kt:285-294`

**Current Implementation:**
```kotlin
private fun createChatPrompt(userMessage: String, agentName: String): String {
    val personality = """You are AILive, a unified on-device AI companion.
You are ONE cohesive intelligence with multiple capabilities...
Be warm, helpful, concise, and conversational."""

    // TinyLlama chat format
    return "<|system|>\n$personality</s>\n<|user|>\n$userMessage</s>\n<|assistant|>\n"
}
```

**❌ PROBLEM #2: WRONG CHAT TEMPLATE**

The code uses **TinyLlama chat format:**
```
<|system|>
{system prompt}</s>
<|user|>
{user message}</s>
<|assistant|>
```

But is downloading **SmolLM2 models**, which use **ChatML format:**
```
<|im_start|>system
{system prompt}<|im_end|>
<|im_start|>user
{user message}<|im_end|>
<|im_start|>assistant
```

**Impact:** The model receives malformed input with wrong special tokens
- Model expects `<|im_start|>` and `<|im_end|>`
- Instead gets `<|system|>`, `<|user|>`, `<|assistant|>`, `</s>`
- These tokens don't exist in SmolLM2's vocabulary!
- Model treats them as unknown/garbage input
- Generates nonsense or empty output

**This is why you're getting generic responses!**

---

### 5. Tokenization Process ⚠️ **LIKELY BROKEN**

**File:** `LLMManager.kt:299-308`

```kotlin
private fun tokenize(text: String): LongArray {
    val tok = tokenizer ?: throw IllegalStateException("Tokenizer not initialized")

    Log.d(TAG, "Tokenizing: ${text.take(100)}...")
    val encoding = tok.encode(text)
    val ids = encoding.ids

    Log.d(TAG, "Token count: ${ids.size}, First 10 tokens: ${ids.take(10).joinToString()}")
    return ids
}
```

**❌ PROBLEM #3: No special token handling**

The tokenizer encodes the text, but:
- Doesn't add BOS (beginning of sequence) token if needed
- Doesn't validate special tokens exist in vocabulary
- Doesn't check if `<|im_start|>` / `<|im_end|>` are recognized
- If special tokens are unknown, they get tokenized as regular text

**Example of what's happening:**
```
Input:  "<|system|>\nYou are AILive...</s>\n<|user|>\nHello</s>\n<|assistant|>\n"
Output: [UNKNOWN, UNKNOWN, "You", "are", "AILive", ..., UNKNOWN, ...]
```

The model sees garbage tokens and can't generate proper responses.

---

### 6. ONNX Inference ⚠️ **POSSIBLY BROKEN**

**File:** `LLMManager.kt:326-362`

**Process:**
1. Create input tensor from token IDs ✅
2. Run ONNX session ✅
3. Get logits output ⚠️
4. Sample next token ❌

**❌ PROBLEM #4: Flawed inference logic**

```kotlin
private fun runInference(inputIds: LongArray): LongArray {
    // ...
    val outputTensor = outputs[0] as OnnxTensor
    val logits = outputTensor.floatBuffer

    // Simple greedy decoding (take argmax)
    val outputIds = mutableListOf<Long>()
    for (i in 0 until MAX_LENGTH) {
        val nextTokenId = sampleNextToken(logits)
        outputIds.add(nextTokenId)

        // Stop on end token (assumed to be 2)
        if (nextTokenId == 2L) break
    }
    //...
}
```

**Issues:**
1. **Autoregressive generation is WRONG**
   - Runs model once with input
   - Then samples 80 tokens from THE SAME logits
   - Doesn't feed previous outputs back as new inputs
   - This is not how transformers work!

2. **Expected:**
   ```
   input = [prompt tokens]
   for i in range(max_length):
       logits = model(input)
       next_token = sample(logits[-1])  # Last position only!
       input = concat(input, [next_token])  # Add to sequence
   ```

3. **Current (WRONG):**
   ```
   input = [prompt tokens]
   logits = model(input)  # Run once
   for i in range(max_length):
       next_token = sample(logits)  # Sample from same logits 80 times!
   ```

**This means:**
- Model only processes the input prompt once
- Then the same logits are sampled 80 times
- No new context is fed back
- Output will be repetitive or nonsensical

---

### 7. Token Sampling ❌ **BROKEN**

**File:** `LLMManager.kt:358-383`

```kotlin
private fun sampleNextToken(logits: FloatBuffer): Long {
    val vocabSize = logits.remaining()

    // Apply temperature
    val probs = FloatArray(vocabSize)
    for (i in 0 until vocabSize) {
        probs[i] = exp((logits[i] / TEMPERATURE).toDouble()).toFloat()
    }

    // Normalize to probabilities
    val sum = probs.sum()
    for (i in probs.indices) {
        probs[i] /= sum
    }

    // Greedy sampling (take argmax for now)
    return probs.indices.maxByOrNull { probs[it] }?.toLong() ?: 0L
}
```

**❌ PROBLEM #5: Logits shape is wrong**

The model outputs logits with shape `[batch_size, sequence_length, vocab_size]`
- For input: `[1, 50]` (batch=1, seq_len=50)
- Output: `[1, 50, 49152]` (batch=1, seq_len=50, vocab=49152)

**Current code:**
- Calls `logits.remaining()` to get vocab size
- But `logits` is a FloatBuffer containing ALL values
- So `remaining()` = `1 * 50 * 49152 = 2,457,600` not `49152`

**Should be:**
- Take logits for LAST position only: `logits[0, -1, :]`
- That gives vocab_size = 49152
- Sample from those probabilities

---

### 8. Decoding ⚠️ **LIKELY BROKEN**

**File:** `LLMManager.kt:313-321`

```kotlin
private fun decode(ids: LongArray): String {
    val tok = tokenizer ?: throw IllegalStateException("Tokenizer not initialized")

    Log.d(TAG, "Decoding ${ids.size} tokens...")
    val text = tok.decode(ids)

    Log.d(TAG, "Decoded: ${text.take(100)}...")
    return text
}
```

**Status:** Depends on tokenizer compatibility
- If tokenizer matches model → Should work
- If tokenizer is from different model → Garbage output

**❌ If special tokens mismatch:**
- Generated tokens might be `<|im_start|>` IDs
- But tokenizer doesn't recognize them
- Decoded output is gibberish

---

### 9. Response Generation (PersonalityEngine) ⚠️ **MASKS ERRORS**

**File:** `PersonalityEngine.kt:360-399`

```kotlin
val responseText = try {
    val llmResponse = llmManager.generate(prompt, agentName = "AILive")

    if (llmResponse.length < 10 || llmResponse.isBlank()) {
        generateFallbackResponse(input, intent, toolResults)
    } else {
        llmResponse
    }
} catch (e: IllegalStateException) {
    // Handle initialization errors...
    "I'm still loading..."
} catch (e: Exception) {
    generateFallbackResponse(input, intent, toolResults)
}
```

**❌ PROBLEM: Exceptions are caught and hidden**

If LLM generates:
- Empty string → Fallback
- Gibberish < 10 chars → Fallback
- Throws exception → Fallback

**User never knows there's a problem!**

---

## 🎯 Root Causes Summary

### Primary Issues (Fix These First):

1. **Chat Template Mismatch**
   - Using TinyLlama format for SmolLM2 model
   - Special tokens don't exist in vocabulary
   - Model can't understand input

2. **Wrong Tokenizer**
   - Unknown source of `tokenizer.json`
   - May not match SmolLM2 vocabulary
   - Special tokens undefined

3. **Broken Inference Loop**
   - Doesn't implement autoregressive generation
   - Samples from same logits 80 times
   - No feedback of generated tokens

4. **Incorrect Logits Sampling**
   - Wrong shape interpretation
   - Takes vocab size from total buffer size
   - Doesn't select last position logits

### Secondary Issues:

5. **Silent Failures**
   - Fallback responses hide real errors
   - No visibility into what's breaking
   - Hard to diagnose issues

---

## 🔧 Required Fixes

### Fix #1: Update Chat Template to SmolLM2 Format

**File:** `LLMManager.kt:285-294`

**Change from:**
```kotlin
return "<|system|>\n$personality</s>\n<|user|>\n$userMessage</s>\n<|assistant|>\n"
```

**Change to:**
```kotlin
return """<|im_start|>system
$personality<|im_end|>
<|im_start|>user
$userMessage<|im_end|>
<|im_start|>assistant
"""
```

---

### Fix #2: Download Correct SmolLM2 Tokenizer

**File:** `app/src/main/assets/tokenizer.json`

**Action:**
1. Download SmolLM2 tokenizer from HuggingFace:
   ```
   https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct/resolve/main/tokenizer.json
   ```

2. Replace current `tokenizer.json` in assets

3. Verify it has special tokens:
   - `<|im_start|>`
   - `<|im_end|>`
   - `<|endoftext|>`

---

### Fix #3: Implement Proper Autoregressive Generation

**File:** `LLMManager.kt:326-362`

**Replace runInference() with:**
```kotlin
private fun runInference(inputIds: LongArray): LongArray {
    val session = ortSession ?: throw IllegalStateException("Session not initialized")
    val outputIds = mutableListOf<Long>()
    var currentInput = inputIds.toMutableList()

    for (i in 0 until MAX_LENGTH) {
        // Create input tensor for current sequence
        val shape = longArrayOf(1, currentInput.size.toLong())
        val inputTensor = OnnxTensor.createTensor(
            ortEnv,
            LongBuffer.wrap(currentInput.toLongArray()),
            shape
        )

        // Run model
        val inputs = mapOf("input_ids" to inputTensor)
        val outputs = session.run(inputs)

        // Get logits for LAST position
        val outputTensor = outputs[0] as OnnxTensor
        val logits = extractLastPositionLogits(outputTensor, currentInput.size)

        // Sample next token
        val nextTokenId = sampleNextToken(logits)
        outputIds.add(nextTokenId)

        // Add to input for next iteration
        currentInput.add(nextTokenId)

        // Cleanup
        outputs.close()
        inputTensor.close()

        // Check for end tokens
        if (nextTokenId == 2L || nextTokenId == 0L) break
    }

    return outputIds.toLongArray()
}

private fun extractLastPositionLogits(tensor: OnnxTensor, seqLen: Int): FloatArray {
    val shape = tensor.info.shape
    val vocabSize = shape[2].toInt()

    val allLogits = tensor.floatBuffer
    val lastPosLogits = FloatArray(vocabSize)

    // Skip to last position: (seqLen - 1) * vocabSize
    val offset = (seqLen - 1) * vocabSize
    allLogits.position(offset)
    allLogits.get(lastPosLogits)

    return lastPosLogits
}
```

---

### Fix #4: Fix Token Sampling

**File:** `LLMManager.kt:358-383`

**Replace sampleNextToken() with:**
```kotlin
private fun sampleNextToken(logits: FloatArray): Long {
    val vocabSize = logits.size

    // Apply temperature
    val scaledLogits = logits.map { it / TEMPERATURE }

    // Apply softmax
    val maxLogit = scaledLogits.maxOrNull() ?: 0f
    val expLogits = scaledLogits.map { exp((it - maxLogit).toDouble()).toFloat() }
    val sumExp = expLogits.sum()
    val probs = expLogits.map { it / sumExp }

    // Greedy sampling (argmax)
    return probs.indices.maxByOrNull { probs[it] }?.toLong() ?: 0L
}
```

---

### Fix #5: Add Better Error Logging

**File:** `PersonalityEngine.kt:360-399`

**Add logging before fallback:**
```kotlin
} catch (e: Exception) {
    Log.e(TAG, "❌ LLM GENERATION FAILED", e)
    Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
    Log.e(TAG, "Message: ${e.message}")
    e.printStackTrace()

    generateFallbackResponse(input, intent, toolResults)
}
```

---

## 📊 Impact Assessment

### Before Fixes:
- Model downloads: ✅
- Model loads: ✅ (but with wrong tokenizer)
- Prompt formatting: ❌ (wrong special tokens)
- Inference: ❌ (broken generation loop)
- Output: ❌ (gibberish or empty)
- **Result:** Generic fallback responses

### After Fixes:
- Model downloads: ✅
- Model loads: ✅
- Prompt formatting: ✅ (correct ChatML format)
- Inference: ✅ (proper autoregressive)
- Output: ✅ (real LLM responses)
- **Result:** Working AI responses

---

## 🧪 Testing Plan

### Test 1: Verify Tokenizer
```kotlin
// Add to initialize()
val testTokens = tokenizer.encode("<|im_start|>test<|im_end|>")
Log.i(TAG, "Special tokens test: ${testTokens.ids.toList()}")
// Should see distinct IDs for special tokens, not UNKNOWNs
```

### Test 2: Verify Chat Format
```kotlin
// Add logging in generate()
Log.i(TAG, "Chat prompt:\n$chatPrompt")
// Check output uses <|im_start|> not <|system|>
```

### Test 3: Verify Inference
```kotlin
// Add to runInference()
Log.i(TAG, "Generated tokens: ${outputIds.toList()}")
Log.i(TAG, "Decoded text: $decodedText")
// Should see varied tokens, not repetition
```

### Test 4: End-to-End
```
Input: "Hello"
Expected: "Hello! I'm AILive, your on-device AI assistant. How can I help you today?"
Not: "I can help with that." (fallback)
```

---

## 📁 Files Requiring Changes

1. **LLMManager.kt** (CRITICAL)
   - Line 285-294: Fix chat template
   - Line 326-362: Fix inference loop
   - Line 358-383: Fix sampling

2. **tokenizer.json** (CRITICAL)
   - Replace with SmolLM2 tokenizer

3. **PersonalityEngine.kt** (HIGH)
   - Line 360-399: Add error logging

4. **README.md** (DOCUMENTATION)
   - Update with correct model info
   - Document chat template format

---

## ✅ Success Criteria

Fix is successful when:
1. ✅ User says "Hello" → Gets real LLM response (not fallback)
2. ✅ Logs show proper tokenization with special tokens
3. ✅ Inference generates varied, coherent tokens
4. ✅ No exceptions or fallback triggers
5. ✅ Response quality is good (on-topic, helpful)

---

## 🚨 Critical Path

**MUST FIX IN THIS ORDER:**

1. Download correct SmolLM2 tokenizer
2. Update chat template format
3. Fix autoregressive generation
4. Test thoroughly
5. Document changes

**DO NOT:**
- Skip tokenizer replacement
- Use TinyLlama format with SmolLM2
- Leave broken inference loop

---

**Report Status:** Complete
**Confidence:** Very High
**Recommended Action:** Implement all 5 fixes immediately
