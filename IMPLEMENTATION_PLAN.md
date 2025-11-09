# Qwen2-VL Vision Pipeline Implementation Plan

**Based on:** Official `infer.py` from HuggingFace
**Current Status:** Text-only mode working, vision pipeline incomplete
**Goal:** Implement full 5-stage multimodal pipeline

---

## Official vs Our Implementation

### Official Python Implementation (infer.py)

```python
# 1. Load all 5 models
models = ['A', 'B', 'C', 'D', 'E']
sessions = {m: ort.InferenceSession(f'QwenVL_{m}_q4f16.onnx', sess_options) for m in models}

# 2. Format prompt with vision tokens
formatted_prompt = f"\n<|im_start|>user\n<|vision_start|><|vision_end|>{prompt}<|im_end|>\n<|im_start|>assistant\n"

# 3. Tokenize
input_ids = tokenizer(formatted_prompt, return_tensors='pt')['input_ids']

# 4. Pipeline:
#    - Model B: Token embedding
#    - Model C: Batch size calculation
#    - Model A: Image feature extraction
#    - Model D: Vision-text fusion
#    - Model E: Iterative token generation (max 12 iterations)

# 5. KV Cache management
key_cache = np.zeros((num_layers, num_key_value_heads, max_length, head_dim), dtype=np.float16)
value_cache = key_cache.copy()
```

### Our Current Implementation (LLMManager.kt)

```kotlin
// 1. Only loads Model E (text decoder)
ortSession = ortEnv?.createSession(modelEPath, sessionOptions)

// 2. Simple prompt format (no vision tokens)
val chatPrompt = createChatPrompt(prompt, agentName)

// 3. Tokenize
val inputIds = tokenize(chatPrompt)

// 4. Pipeline:
//    - TODO comment at line 395
//    - Only Model E inference
//    - No vision integration

// 5. No KV cache
```

---

## Implementation Phases

### Phase 1: Load All Models ⏳ NEXT

**File:** `LLMManager.kt` - `initializeONNX()`

**Changes needed:**

```kotlin
// Add private fields
private var ortSessionA: OrtSession? = null  // Image processor
private var ortSessionB: OrtSession? = null  // Token embedder (currently lazy-loaded)
private var ortSessionC: OrtSession? = null  // Batch calculator
private var ortSessionD: OrtSession? = null  // Vision-text fusion
private var ortSessionE: OrtSession? = null  // Token generator (currently ortSession)

// In initializeONNX(), load all 5 models:
suspend fun initializeONNX(): Boolean {
    // ... existing tokenizer loading ...

    // Load all 5 ONNX models
    val modelPaths = mapOf(
        'A' to modelDownloadManager.getModelPath(ModelDownloadManager.QWEN_VL_MODEL_A),
        'B' to modelDownloadManager.getModelPath(ModelDownloadManager.QWEN_VL_MODEL_B),
        'C' to modelDownloadManager.getModelPath(ModelDownloadManager.QWEN_VL_MODEL_C),
        'D' to modelDownloadManager.getModelPath(ModelDownloadManager.QWEN_VL_MODEL_D),
        'E' to modelDownloadManager.getModelPath(ModelDownloadManager.QWEN_VL_MODEL_E)
    )

    ortSessionA = ortEnv?.createSession(modelPaths['A'], sessionOptions)
    ortSessionB = ortEnv?.createSession(modelPaths['B'], sessionOptions)
    ortSessionC = ortEnv?.createSession(modelPaths['C'], sessionOptions)
    ortSessionD = ortEnv?.createSession(modelPaths['D'], sessionOptions)
    ortSessionE = ortEnv?.createSession(modelPaths['E'], sessionOptions)

    Log.i(TAG, "✅ All 5 Qwen2-VL models loaded successfully")
}
```

**Memory Impact:** ~2.6GB total (A: 1.33GB, B: 234MB, C: 6KB, D: 25KB, E: 997MB)

**Optimization:** Can lazy-load A, B, C, D when image is provided (keep E always loaded for text)

---

### Phase 2: Vision Prompt Format

**File:** `LLMManager.kt` - `createChatPrompt()`

**Official format from infer.py:**

```python
formatted_prompt = f"\n<|im_start|>user\n<|vision_start|><|vision_end|>{prompt}<|im_end|>\n<|im_start|>assistant\n"
```

**Implementation:**

```kotlin
private fun createChatPrompt(prompt: String, agentName: String, hasImage: Boolean): String {
    return if (hasImage) {
        // Vision mode: Use official Qwen2-VL vision format
        "\n<|im_start|>user\n<|vision_start|><|vision_end|>$prompt<|im_end|>\n<|im_start|>assistant\n"
    } else {
        // Text-only mode: Use simple chat format
        "<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n"
    }
}
```

**Key tokens:**
- `<|im_start|>` - Message start
- `<|vision_start|>` - Vision content start
- `<|vision_end|>` - Vision content end
- `<|im_end|>` - Message end

---

### Phase 3: 5-Stage Pipeline

**File:** `LLMManager.kt` - `generateONNX()`

**Official pipeline from infer.py:**

```python
# Stage 1: Model B - Token embedding
hidden_states = sessions['B'].run(
    [outputs['B']],
    {inputs['B'][0]: tokens, inputs['B'][1]: input_lengths}
)[0]

# Stage 2: Model C - Batch size calculation
batch_size = np.array(0, dtype=np.int32)
batch_size, = sessions['C'].run([outputs['C']], {inputs['C']: batch_size})

# Stage 3: Model A - Image feature extraction (960x960 RGB → features)
image_features = sessions['A'].run([outputs['A']], {inputs['A']: image_array})[0]

# Stage 4: Model D - Vision-text fusion
hidden_states, batch_size = sessions['D'].run(
    outputs['D'],
    dict(zip(inputs['D'], [hidden_states, image_features, input_lengths, tokens_to_stop, remaining_tokens]))
)

# Stage 5: Model E - Token generation (iterative, max 12)
for i in range(12):
    token, key_cache, value_cache = sessions['E'].run(...)
    if token in [151643, 151645]:  # EOS tokens
        break
```

**Android/Kotlin implementation:**

```kotlin
private fun generateONNX(chatPrompt: String, image: Bitmap?): String {
    // Tokenize input
    val inputIds = tokenize(chatPrompt)
    val inputLength = inputIds.size.toLong()

    // Prepare tokens array (max_length = 1024)
    val maxLength = 1024
    val tokens = LongArray(maxLength)
    inputIds.copyInto(tokens)

    // Stage 1: Model B - Token embedding
    val hiddenStates = runModelB(tokens, inputLength)

    // Stage 2: Model C - Batch size calculation
    var batchSize = runModelC()

    if (image != null) {
        // Stage 3: Model A - Image feature extraction
        val imageArray = VisionPreprocessor.preprocessImage(image)
        val imageFeatures = runModelA(imageArray)

        // Calculate vision parameters
        val totalIds = 100  // 10 * 10 grid from original factors
        val newInputLength = inputLength + totalIds
        val remainingTokens = maxLength - newInputLength - totalIds
        val tokensToStop = newInputLength - 5

        // Stage 4: Model D - Vision-text fusion
        val (fusedHiddenStates, newBatchSize) = runModelD(
            hiddenStates, imageFeatures, newInputLength,
            tokensToStop, remainingTokens
        )

        hiddenStates = fusedHiddenStates
        batchSize = newBatchSize
    }

    // Stage 5: Model E - Iterative token generation
    return generateTokensIterative(hiddenStates, batchSize, inputLength, image != null)
}
```

---

### Phase 4: KV Cache Implementation

**Based on official implementation:**

```python
# Initialize caches
num_layers = 28  # Qwen2-VL-2B config
num_key_value_heads = 4
head_dim = 128
max_length = 1024

key_cache = np.zeros((num_layers, num_key_value_heads, max_length, head_dim), dtype=np.float16)
value_cache = key_cache.copy()
```

**Android implementation:**

```kotlin
// Add to LLMManager class
private val NUM_LAYERS = 28
private val NUM_KEY_VALUE_HEADS = 4
private val HEAD_DIM = 128
private val MAX_LENGTH = 1024

private var keyCache: FloatArray? = null
private var valueCache: FloatArray? = null

private fun initializeKVCache() {
    val cacheSize = NUM_LAYERS * NUM_KEY_VALUE_HEADS * MAX_LENGTH * HEAD_DIM
    keyCache = FloatArray(cacheSize) { 0f }
    valueCache = FloatArray(cacheSize) { 0f }
    Log.i(TAG, "KV cache initialized: ${cacheSize * 2 * 2 / 1024 / 1024}MB")  // float16 = 2 bytes
}
```

---

### Phase 5: Iterative Generation

**Official loop:**

```python
for i in range(12):  # MAX_ITERATIONS
    token, key_cache, value_cache = sessions['E'].run(
        outputs['E'],
        dict(zip(inputs['E'], [
            hidden_states,
            np.array([-65504. if i==0 else 0.], dtype=np.float16),  # Attention mask
            key_cache, value_cache, position, input_lengths, batch_size,
            np.array([1-total_ids+10 if i==0 else position[0]+1], dtype=np.float16)
        ]))
    )

    if token in [151643, 151645]:  # End tokens
        break

    # Update position and input_lengths
    if i < 1:
        position += input_lengths[0]
        input_lengths[0] = 1
    else:
        position += 1

    # Re-run Model B for next token
    tokens[0] = token
    hidden_states = sessions['B'].run([outputs['B']], {inputs['B'][0]: tokens, inputs['B'][1]: input_lengths})[0]

    print(tokenizer.decode(token), end='', flush=True)
```

**Android implementation:**

```kotlin
private fun generateTokensIterative(
    initialHiddenStates: OnnxTensor,
    batchSize: Int,
    initialInputLength: Long,
    hasImage: Boolean
): String {
    val MAX_ITERATIONS = 12
    val outputTokens = mutableListOf<Long>()

    var hiddenStates = initialHiddenStates
    var position = 0L
    var inputLength = initialInputLength
    val totalIds = if (hasImage) 100 else 0

    for (i in 0 until MAX_ITERATIONS) {
        // Create attention mask
        val attentionMask = if (i == 0) -65504f else 0f

        // Create position offset
        val positionOffset = if (i == 0) {
            (1 - totalIds + 10).toFloat()
        } else {
            (position + 1).toFloat()
        }

        // Run Model E
        val (token, newKeyCache, newValueCache) = runModelE(
            hiddenStates, attentionMask, keyCache!!, valueCache!!,
            position, inputLength, batchSize, positionOffset
        )

        // Check for end tokens
        if (token == 151643L || token == 151645L) {
            Log.i(TAG, "EOS token detected: $token")
            break
        }

        outputTokens.add(token)

        // Update position and input length
        if (i < 1) {
            position += inputLength
            inputLength = 1
        } else {
            position += 1
        }

        // Update caches
        keyCache = newKeyCache
        valueCache = newValueCache

        // Re-run Model B for next token
        val tokens = LongArray(MAX_LENGTH) { 0 }
        tokens[0] = token
        hiddenStates = runModelB(tokens, inputLength)

        // Log progress
        Log.d(TAG, "Generated token $i: ${decode(listOf(token))}")
    }

    return decode(outputTokens)
}
```

---

## Model Input/Output Specifications

### Model A (Image Processor)
**Input:**
- `pixel_values`: float32[1, 3, 960, 960] - Preprocessed RGB image

**Output:**
- `image_features`: float32[1, 100, hidden_size] - Image embeddings (100 = 10×10 grid)

### Model B (Token Embedder)
**Inputs:**
- `input_ids`: int64[max_length] - Token IDs
- `input_lengths`: int64[1] - Number of valid tokens

**Output:**
- `hidden_states`: float32[1, input_length, hidden_size]

### Model C (Batch Calculator)
**Input:**
- `batch_size`: int32 - Initial batch size (typically 0)

**Output:**
- `batch_size`: int32 - Calculated batch size

### Model D (Vision-Text Fusion)
**Inputs:**
- `hidden_states`: float32[1, input_length, hidden_size]
- `image_features`: float32[1, 100, hidden_size]
- `input_lengths`: int64[1]
- `tokens_to_stop`: int32
- `remaining_tokens`: int32

**Outputs:**
- `hidden_states`: float32[1, new_length, hidden_size] - Fused features
- `batch_size`: int32

### Model E (Token Generator)
**Inputs:**
- `hidden_states`: float32[1, 1, hidden_size]
- `attention_mask`: float16[1] - Attention bias
- `key_cache`: float16[num_layers, num_kv_heads, max_length, head_dim]
- `value_cache`: float16[num_layers, num_kv_heads, max_length, head_dim]
- `position`: int64[1] - Current position
- `input_lengths`: int64[1]
- `batch_size`: int32
- `position_offset`: float16[1]

**Outputs:**
- `token`: int64 - Next token ID
- `key_cache`: float16[...] - Updated key cache
- `value_cache`: float16[...] - Updated value cache

---

## Testing Plan

### Phase 1 Testing
- ✅ Verify all 5 models load without errors
- ✅ Check memory usage (~2.6GB)
- ✅ Ensure no OOM crashes

### Phase 2 Testing
- ✅ Verify vision prompt format tokenizes correctly
- ✅ Check token counts match expected

### Phase 3 Testing
- ✅ Test each model independently
- ✅ Verify tensor shapes match specifications
- ✅ Test full pipeline with sample image

### Phase 4 Testing
- ✅ Verify KV cache allocation
- ✅ Check cache updates work correctly

### Phase 5 Testing
- ✅ Test iterative generation
- ✅ Verify EOS token detection
- ✅ Test text-only vs vision mode
- ✅ Benchmark inference speed

---

## Estimated Complexity

| Phase | Lines of Code | Estimated Time | Difficulty |
|-------|---------------|----------------|------------|
| 1. Load Models | ~50 | 30 min | Easy |
| 2. Prompt Format | ~20 | 15 min | Easy |
| 3. Pipeline | ~150 | 3-4 hours | Hard |
| 4. KV Cache | ~50 | 1 hour | Medium |
| 5. Iterative Gen | ~80 | 2 hours | Medium-Hard |
| **Total** | **~350** | **7-8 hours** | **Medium-Hard** |

---

## Dependencies Needed

**Current:**
- ✅ ONNX Runtime 1.19.2
- ✅ VisionPreprocessor (correct implementation)
- ✅ Tokenizer (Hugging Face)

**Missing:**
- ❌ Model input/output name introspection (use `session.getInputs()` / `getOutputs()`)
- ❌ Tensor creation utilities (use `OnnxTensor.createTensor()`)
- ❌ Multi-dimensional array handling (FloatArray/LongArray)

---

## Next Step

**Recommend:** Start with **Phase 1** - Load all 5 models and verify they initialize correctly.

This is low-risk and will immediately show if there are any compatibility issues with the models.

```kotlin
// Quick test in initializeONNX()
Log.i(TAG, "Loading all 5 Qwen2-VL models...")
ortSessionA = ortEnv?.createSession(modelAPath, sessionOptions)
Log.i(TAG, "✅ Model A loaded: ${File(modelAPath).length() / 1024 / 1024}MB")
// ... repeat for B, C, D, E
```

Would you like me to implement Phase 1 now?
