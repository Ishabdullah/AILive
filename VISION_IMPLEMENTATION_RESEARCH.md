# Vision Support Research - Qwen2-VL Implementation with llama.cpp

## Research Date: 2025-11-09

---

## Summary of Findings

### ✅ Good News
1. **Qwen2-VL is officially supported in llama.cpp** (as of PR #9246, completed)
2. **Native multimodal support exists** via `libmtmd` library
3. **Pre-quantized GGUF models available** from `ggml-org` and `bartowski`
4. **mmproj files are available** for vision encoding

### ❌ Challenge
**The official llama.cpp Android bindings do NOT support vision/multimodal yet.**
- Current implementation: Text-only, basic inference
- No mmproj loading capability
- No image input methods
- No integration with `libmtmd`

---

## Official Qwen2-VL Documentation

### Image Preprocessing (from Qwen docs)

**Supported Input Formats**:
- Local file paths: `file:///path/to/image.jpg`
- URLs: `http://path/to/image.jpg`
- Base64: `data:image;base64,/9j/...`

**Resolution Handling**:
- **Dynamic Resolution**: "Naive Dynamic Resolution" automatically adjusts visual tokens
- **Token Range**: 4-16,384 visual tokens per image (configurable via `min_pixels`/`max_pixels`)
- **Aspect Ratio**: Preserved within pixel constraints
- **Size Rounding**: Dimensions rounded to nearest multiple of 28

**Prompt Structure**:
```python
messages = [
    {
        "role": "user",
        "content": [
            {"type": "image", "image": "image_path_or_url"},
            {"type": "text", "text": "Describe this image"}
        ]
    }
]
```

**Vision Tokens**: Model uses special tokens:
- `<|vision_start|>` - Begin visual content region
- `<|image_pad|>` - Image padding tokens (dynamic count)
- `<|vision_end|>` - End visual content region

**Architecture**:
- **Vision Encoder**: Vision Transformer (ViT) with ~600M parameters
- **M-ROPE**: Multimodal Rotary Position Embedding
  - Captures 1D textual positions
  - Captures 2D visual positions
  - Captures 3D video positions (for video support)

---

## llama.cpp Multimodal Support

### Current Implementation (as of 2025-11)

**Tools Supporting Vision**:
1. `llama-mtmd-cli` - Command-line interface
2. `llama-server` - HTTP API server with OpenAI-compatible `/chat/completions`

**NOT YET SUPPORTED**:
- ❌ Android bindings (llama.android)
- ❌ iOS bindings

### Usage with Desktop llama.cpp

**Method 1: Using Pre-Quantized Models**
```bash
llama-mtmd-cli -hf ggml-org/Qwen2-VL-2B-Instruct-GGUF
llama-server -hf ggml-org/Qwen2-VL-2B-Instruct-GGUF
```

**Method 2: Using Local Files**
```bash
llama-mtmd-cli -m model.gguf --mmproj mmproj-file.gguf
llama-server -m Qwen2-VL-2B-Instruct-Q4_K_M.gguf --mmproj mmproj-Qwen2-VL-2B-Instruct-f32.gguf
```

**Example with Image**:
```bash
llama-mtmd-cli -m model.gguf --mmproj mmproj.gguf -p "Describe this image" --image photo.jpg
```

### How mmproj Works

**Architecture**:
```
Image Input
    ↓
Vision Encoder (mmproj)
    ↓
Image Embeddings (visual tokens)
    ↓
Language Model (model.gguf)
    ↓
Text Output
```

**Two Files Required**:
1. **model.gguf** - Language model (e.g., `Qwen2-VL-2B-Instruct-Q4_K_M.gguf`, 940MB)
2. **mmproj.gguf** - Vision encoder (e.g., `mmproj-Qwen2-VL-2B-Instruct-f32.gguf`, ~1.5GB)

**mmproj File Details**:
- Format: GGUF (same as model)
- Size: ~1.5GB for Qwen2-VL-2B (f32 precision)
- Purpose: Converts image pixels → visual tokens for LLM
- Source: Can download pre-made from bartowski/Mungert repos OR convert from HuggingFace using `convert_hf_to_gguf.py --mmproj`

---

## Android Implementation Analysis

### Current State of llama.android

**File**: `external/llama.cpp/examples/llama.android/llama/src/main/java/android/llama/cpp/LLamaAndroid.kt`

**Capabilities**:
- ✅ Load single GGUF model
- ✅ Text-only inference
- ✅ Streaming token generation
- ✅ Context management
- ✅ KV cache operations

**Missing for Vision**:
- ❌ mmproj file loading
- ❌ Image input methods (Bitmap → visual tokens)
- ❌ Multimodal context initialization
- ❌ libmtmd integration

### Current JNI Methods (C++)

**File**: `external/llama.cpp/examples/llama.android/llama/src/main/cpp/llama-android.cpp`

**Existing JNI Functions**:
```cpp
load_model(filename)           // Loads text model only
new_context(model)             // Text-only context
completion_init(text)          // Text prompt only
completion_loop()              // Text generation only
```

**What's Needed for Vision**:
```cpp
// NEW METHODS REQUIRED:
load_mmproj(filename)          // Load vision encoder
encode_image(bitmap)           // Convert image to embeddings
completion_init_multimodal()   // Init with text + image tokens
```

---

## Implementation Options

### Option A: Wait for Official Support ⏳ **RECOMMENDED**
**Description**: Wait for llama.cpp team to add multimodal support to Android bindings

**Pros**:
- ✅ Clean integration when available
- ✅ Maintained by upstream team
- ✅ Best long-term solution
- ✅ Likely to support all vision models (not just Qwen)

**Cons**:
- ❌ Unknown timeline (could be weeks/months)
- ❌ Feature not available now

**Status Check**:
- Watch: https://github.com/ggml-org/llama.cpp/tree/master/examples/llama.android
- Issue tracker: https://github.com/ggml-org/llama.cpp/issues

**Estimated Timeline**: Unknown (possibly Q1-Q2 2025)

---

### Option B: Custom JNI Implementation 🔧 **MOST WORK, BEST RESULT**
**Description**: Extend llama-android.cpp with custom vision support using libmtmd

**Implementation Steps**:

#### 1. Add libmtmd to Android Build
**File**: `external/llama.cpp/examples/llama.android/llama/src/main/cpp/CMakeLists.txt`

```cmake
# Add mtmd library
add_subdirectory(../../../../../../../../llama.cpp/tools/mtmd build-mtmd)

target_link_libraries(${CMAKE_PROJECT_NAME}
    llama
    common
    mtmd        # ADD THIS
    android
    log
)
```

#### 2. Extend Kotlin API
**File**: `external/llama.cpp/examples/llama.android/llama/src/main/java/android/llama/cpp/LLamaAndroid.kt`

```kotlin
// Add to LLamaAndroid class:

private external fun load_mmproj(filename: String): Long
private external fun free_mmproj(mmproj: Long)
private external fun encode_image(mmprojPtr: Long, imageBytes: ByteArray, width: Int, height: Int): LongArray
private external fun completion_init_with_image(
    context: Long,
    batch: Long,
    text: String,
    imageEmbeddings: LongArray,
    formatChat: Boolean,
    nLen: Int
): Int

// Modified State to include mmproj
private sealed interface State {
    data object Idle: State
    data class Loaded(
        val model: Long,
        val context: Long,
        val batch: Long,
        val sampler: Long,
        val mmproj: Long? = null  // ADD THIS
    ): State
}

// New load method with mmproj
suspend fun loadWithVision(modelPath: String, mmprojPath: String) {
    withContext(runLoop) {
        when (threadLocalState.get()) {
            is State.Idle -> {
                val model = load_model(modelPath)
                if (model == 0L) throw IllegalStateException("load_model() failed")

                val mmproj = load_mmproj(mmprojPath)
                if (mmproj == 0L) throw IllegalStateException("load_mmproj() failed")

                val context = new_context(model)
                if (context == 0L) throw IllegalStateException("new_context() failed")

                val batch = new_batch(512, 0, 1)
                if (batch == 0L) throw IllegalStateException("new_batch() failed")

                val sampler = new_sampler()
                if (sampler == 0L) throw IllegalStateException("new_sampler() failed")

                Log.i(tag, "Loaded model with vision support")
                threadLocalState.set(State.Loaded(model, context, batch, sampler, mmproj))
            }
            else -> throw IllegalStateException("Model already loaded")
        }
    }
}

// New send method with image
fun sendWithImage(message: String, image: Bitmap, formatChat: Boolean = false): Flow<String> = flow {
    when (val state = threadLocalState.get()) {
        is State.Loaded -> {
            if (state.mmproj == null) {
                throw IllegalStateException("Vision not enabled. Use loadWithVision()")
            }

            // Convert Bitmap to byte array
            val stream = ByteArrayOutputStream()
            image.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            val imageBytes = stream.toByteArray()

            // Encode image to embeddings
            val imageEmbeddings = encode_image(
                state.mmproj,
                imageBytes,
                image.width,
                image.height
            )

            // Initialize with text + image
            val ncur = IntVar(completion_init_with_image(
                state.context,
                state.batch,
                message,
                imageEmbeddings,
                formatChat,
                nlen
            ))

            // Generate response
            while (ncur.value <= nlen) {
                val str = completion_loop(state.context, state.batch, state.sampler, nlen, ncur)
                if (str == null) break
                emit(str)
            }
            kv_cache_clear(state.context)
        }
        else -> {}
    }
}.flowOn(runLoop)
```

#### 3. Implement JNI Methods
**File**: `external/llama.cpp/examples/llama.android/llama/src/main/cpp/llama-android.cpp`

```cpp
#include "mtmd.h"  // Add mtmd header

// Global mmproj state
static mtmd_context * g_mmproj_ctx = nullptr;

extern "C"
JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_load_1mmproj(JNIEnv *env, jobject, jstring filename) {
    const char* path = env->GetStringUTFChars(filename, 0);
    LOGi("Loading mmproj from %s", path);

    // Load mmproj using libmtmd
    mtmd_params params = mtmd_params_default();
    g_mmproj_ctx = mtmd_load_from_file(path, params);

    env->ReleaseStringUTFChars(filename, path);

    if (!g_mmproj_ctx) {
        LOGe("load_mmproj() failed");
        return 0;
    }

    return reinterpret_cast<jlong>(g_mmproj_ctx);
}

extern "C"
JNIEXPORT jlongArray JNICALL
Java_android_llama_cpp_LLamaAndroid_encode_1image(
    JNIEnv *env,
    jobject,
    jlong mmproj_ptr,
    jbyteArray imageBytes,
    jint width,
    jint height
) {
    auto mmproj = reinterpret_cast<mtmd_context *>(mmproj_ptr);

    // Get byte array
    jbyte* bytes = env->GetByteArrayElements(imageBytes, nullptr);
    jsize len = env->GetArrayLength(imageBytes);

    // Decode image and encode to embeddings using libmtmd
    // TODO: Implement image decoding and embedding extraction
    // This requires stb_image or similar for JPEG decoding
    // Then call mtmd_process_image() to get visual tokens

    // For now, return placeholder
    jlongArray result = env->NewLongArray(256);  // Placeholder

    env->ReleaseByteArrayElements(imageBytes, bytes, JNI_ABORT);
    return result;
}

extern "C"
JNIEXPORT jint JNICALL
Java_android_llama_cpp_LLamaAndroid_completion_1init_1with_1image(
    JNIEnv *env,
    jobject,
    jlong context_pointer,
    jlong batch_pointer,
    jstring jtext,
    jlongArray imageEmbeddings,
    jboolean format_chat,
    jint n_len
) {
    // Similar to completion_init but also processes image embeddings
    // Combine text tokens + visual tokens into batch
    // TODO: Implement multimodal batch creation

    return 0;  // Placeholder
}
```

#### 4. Update LLMManager
**File**: `app/src/main/java/com/ailive/ai/llm/LLMManager.kt`

```kotlin
suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
    try {
        val modelPath = modelDownloadManager.getModelPath(ModelDownloadManager.QWEN_VL_MODEL_GGUF)
        val mmprojPath = modelDownloadManager.getModelPath(ModelDownloadManager.QWEN_VL_MMPROJ)

        if (modelDownloadManager.isMMProjAvailable()) {
            Log.i(TAG, "✅ Loading with vision support...")
            llamaAndroid.loadWithVision(modelPath, mmprojPath)
        } else {
            Log.i(TAG, "⚠️ Loading text-only (mmproj not found)")
            llamaAndroid.load(modelPath)
        }

        isInitialized = true
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize", e)
        false
    }
}

suspend fun generateWithVision(prompt: String, image: Bitmap): String = withContext(Dispatchers.IO) {
    val response = StringBuilder()

    llamaAndroid.sendWithImage(prompt, image)
        .collect { token ->
            response.append(token)
        }

    return@withContext response.toString()
}
```

**Pros**:
- ✅ Full control over implementation
- ✅ Optimal performance (native code)
- ✅ Can support all vision models
- ✅ Works now (once implemented)

**Cons**:
- ❌ Significant C++ development required
- ❌ Complex: JNI, image decoding, libmtmd integration
- ❌ Maintenance burden (keep in sync with upstream)
- ❌ Need to handle image format conversions (JPEG/PNG → embeddings)

**Estimated Timeline**: 7-14 days for experienced C++/JNI developer

---

### Option C: Use llama-server (HTTP API) 📡 **QUICKEST WORKING SOLUTION**
**Description**: Run `llama-server` with vision support, call it via HTTP from Android

**Architecture**:
```
Android App
    ↓ HTTP
llama-server (local or remote)
    ↓
Qwen2-VL GGUF + mmproj
```

**Implementation**:

#### 1. Setup llama-server
On device or remote server:
```bash
# Download model and mmproj
wget https://huggingface.co/bartowski/Qwen2-VL-2B-Instruct-GGUF/resolve/main/Qwen2-VL-2B-Instruct-Q4_K_M.gguf
wget https://huggingface.co/bartowski/Qwen2-VL-2B-Instruct-GGUF/resolve/main/mmproj-Qwen2-VL-2B-Instruct-f32.gguf

# Start server with vision support
llama-server -m Qwen2-VL-2B-Instruct-Q4_K_M.gguf \
  --mmproj mmproj-Qwen2-VL-2B-Instruct-f32.gguf \
  --host 0.0.0.0 \
  --port 8080 \
  -c 4096
```

#### 2. Add HTTP Client to Android
**File**: `app/build.gradle.kts`

```kotlin
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
}
```

#### 3. Create Vision API Client
**New File**: `app/src/main/java/com/ailive/ai/llm/VisionAPIClient.kt`

```kotlin
class VisionAPIClient(private val baseUrl: String = "http://localhost:8080") {
    private val client = OkHttpClient()

    suspend fun chat(prompt: String, imageBase64: String): String = withContext(Dispatchers.IO) {
        val json = """
        {
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "image", "image": "data:image/jpeg;base64,$imageBase64"},
                        {"type": "text", "text": "$prompt"}
                    ]
                }
            ],
            "temperature": 0.7,
            "max_tokens": 512
        }
        """.trimIndent()

        val request = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw IOException("Empty response")
            // Parse JSON and extract message content
            extractMessage(body)
        }
    }

    fun imageToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
```

**Pros**:
- ✅ Works immediately with existing llama.cpp vision support
- ✅ No C++/JNI development needed
- ✅ Easy to test and debug
- ✅ Can run server remotely (cloud) or locally (Termux)
- ✅ OpenAI-compatible API

**Cons**:
- ❌ Not truly "on-device" if using remote server
- ❌ Requires network connection
- ❌ Higher latency (HTTP overhead + image encoding)
- ❌ Need to run separate llama-server process

**For Local On-Device**:
- Run llama-server in Termux app on same Android device
- Connect via localhost (127.0.0.1:8080)
- Still technically on-device, but requires two apps

**Estimated Timeline**: 1-2 days

---

### Option D: Alternative VLM Library 🔄 **FALLBACK OPTION**
**Description**: Use a different vision model library with better Android support

**Options**:

#### D1: MLC-LLM (Machine Learning Compilation)
- **Supports**: Many vision models including Qwen2-VL
- **Platform**: Android, iOS, Web
- **Status**: Actively maintained
- **Link**: https://github.com/mlc-ai/mlc-llm

**Pros**:
- ✅ Official Android support
- ✅ GPU acceleration built-in
- ✅ Vision models work out-of-box

**Cons**:
- ❌ Different model format (not GGUF)
- ❌ Need to reconvert/download models
- ❌ Complete rewrite of inference layer

#### D2: llama.rn (React Native)
- **Supports**: llama.cpp on React Native
- **Platform**: Android, iOS
- **Vision**: Experimental multimodal support
- **Link**: https://github.com/mybigday/llama.rn

**Pros**:
- ✅ Uses llama.cpp under the hood
- ✅ Claims vision support

**Cons**:
- ❌ Requires React Native
- ❌ Experimental/community-maintained
- ❌ May have limited docs

---

## Recommendation

### Immediate Action (Next 1-2 Weeks): **Option C - HTTP API**
Use `llama-server` with Termux for quick vision testing:
1. Install Termux on Samsung S24 Ultra
2. Build/install llama-server in Termux
3. Download model + mmproj files to Termux storage
4. Run server locally on device
5. Connect Android app via localhost HTTP

**Rationale**:
- ✅ Can test vision features **immediately**
- ✅ Proves value of vision capabilities
- ✅ No complex C++ development
- ✅ Easy to demo to stakeholders
- ✅ Completely on-device (via Termux)

### Medium-Term (Next 1-3 Months): **Monitor Option A**
Watch llama.cpp Android development:
- Check for multimodal support in llama.android
- Follow PRs and issues
- Be ready to migrate when available

### Long-Term (If Upstream Too Slow): **Option B**
If official support doesn't arrive:
- Implement custom JNI vision support
- Contribute back to llama.cpp project
- Maintain as fork until merged upstream

---

## Files to Download for Vision

### Required Files
1. **Text Model** (Already have):
   - `Qwen2-VL-2B-Instruct-Q4_K_M.gguf` - 940MB ✅

2. **Vision Encoder** (Need to download):
   - `mmproj-Qwen2-VL-2B-Instruct-f32.gguf` - ~1.5GB
   - URL: https://huggingface.co/bartowski/Qwen2-VL-2B-Instruct-GGUF/resolve/main/mmproj-Qwen2-VL-2B-Instruct-f32.gguf

### Alternative mmproj Precisions
- **f32** (1.5GB) - Full precision, best quality ← Recommended
- **f16** (750MB) - Half precision, good balance
- **q8_0** (400MB) - 8-bit quantized, slight quality loss

---

## Testing Checklist (Once Implemented)

### Vision Capabilities to Test
- [ ] Load model with mmproj successfully
- [ ] Describe a simple photo (dog, cat, car)
- [ ] Read text from screenshot (OCR)
- [ ] Identify multiple objects in complex image
- [ ] Answer questions about image content
- [ ] Compare before/after photos
- [ ] Analyze UI screenshots (for screen understanding feature)

### Performance Benchmarks
- [ ] Model + mmproj load time
- [ ] First token latency with image
- [ ] Tokens/second with image input
- [ ] Memory usage with vision vs text-only
- [ ] Battery impact of vision inference

---

## Next Steps

1. **Immediate**: Review this research document
2. **Decision**: Choose implementation option (A, B, C, or D)
3. **If Option C**: Set up Termux + llama-server
4. **If Option B**: Begin JNI implementation planning
5. **Update**: NEXT_STEPS.md with chosen approach

---

## References

### Documentation
- Qwen2-VL Official: https://qwen.readthedocs.io/en/latest/
- Qwen2-VL Blog: https://qwenlm.github.io/blog/qwen2-vl/
- llama.cpp Multimodal: https://github.com/ggml-org/llama.cpp/blob/master/docs/multimodal.md
- libmtmd README: https://github.com/ggml-org/llama.cpp/blob/master/tools/mtmd/README.md

### Model Downloads
- bartowski GGUF: https://huggingface.co/bartowski/Qwen2-VL-2B-Instruct-GGUF
- ggml-org Official: https://huggingface.co/ggml-org/Qwen2-VL-2B-Instruct-GGUF

### Related Projects
- llama.rn: https://github.com/mybigday/llama.rn
- MLC-LLM: https://github.com/mlc-ai/mlc-llm
- Termux: https://termux.dev/

---

**Status**: Research Complete ✅
**Recommended Path**: Option C (HTTP via Termux) → Option A (await upstream) → Option B (custom JNI if needed)
**Estimated Timeline to Working Vision**: 2-3 days with Option C
