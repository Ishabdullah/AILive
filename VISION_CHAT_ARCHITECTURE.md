# Vision-Chat Module Architecture

**Document Version:** 1.0
**Date:** 2025-11-09
**Model:** Qwen2-VL-2B-Instruct (Q4F16)

---

## Overview

The Vision-Chat module provides multimodal AI capabilities (vision + text) integrated into AILive's modular architecture. It maintains clear interfaces with PersonalityEngine, MemoryRetrievalTool, and other system components.

---

## Module Definition

### Vision-Chat Module: `LLMManager`

**Purpose**: Unified multimodal AI inference engine
**Location**: `app/src/main/java/com/ailive/ai/llm/LLMManager.kt`
**Model**: Qwen2-VL-2B-Instruct (Q4F16 quantized, ~3.7GB)

**Core Interface**:
```kotlin
suspend fun generate(
    prompt: String,
    image: Bitmap? = null,  // NEW: Optional vision input
    agentName: String = "AILive"
): String
```

**Capabilities**:
- Text-only conversation (backward compatible)
- Visual Question Answering (VQA)
- Image captioning and description
- Context-aware vision + text reasoning

---

## Architectural Integration

### 1. PersonalityEngine Integration

**Current Call** (Text-only):
```kotlin
val response = llmManager.generate(prompt, agentName = "AILive")
```

**New Call** (Vision + text):
```kotlin
val response = llmManager.generate(
    prompt = "What do you see in this image?",
    image = cameraBitmap,
    agentName = "AILive"
)
```

**Backward Compatibility**: ✅ Maintained
- All existing text-only calls continue to work
- `image` parameter is optional (`null` by default)
- PersonalityEngine doesn't need immediate changes

---

### 2. Tool Integration

#### analyze_vision Tool

**Before** (GPT-2 era):
```kotlin
// analyze_vision had no real vision model
// Just returned placeholder text
```

**After** (Qwen2-VL):
```kotlin
class VisionAnalysisTool : BaseTool() {
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val image = params["image"] as? Bitmap
        val question = params["question"] as? String ?: "Describe this image"

        // Call LLMManager with vision input
        val description = llmManager.generate(
            prompt = question,
            image = image,
            agentName = "VisionAnalysisTool"
        )

        return ToolResult.success(description)
    }
}
```

**Benefits**:
- Real vision understanding (not just placeholders)
- Consistent interface with other tools
- Automatic memory logging via PersonalityEngine

---

### 3. Memory Integration

**Vision Context Metadata**:
When `image != null`, responses include vision metadata:

```kotlin
// Memory storage format
{
    "timestamp": "2025-11-09T10:30:00",
    "user_query": "What's in this photo?",
    "ai_response": "I see a cat sleeping on a couch.",
    "metadata": {
        "has_vision_input": true,
        "image_hash": "a3f2b8...",  // For deduplication
        "model": "Qwen2-VL-2B",
        "agent": "AILive"
    }
}
```

**MemoryRetrievalTool** can query:
- Text-only conversations: `has_vision_input: false`
- Vision conversations: `has_vision_input: true`
- Specific image discussions: `image_hash: "..."`

---

### 4. MessageBus Integration

**Event Flow**:
```
User Input (text + image)
    ↓
PersonalityEngine receives input
    ↓
MessageBus: EVENT_LLM_INFERENCE_START
    ↓
LLMManager.generate(prompt, image)
    ↓
[Vision Preprocessing] → [Text Encoding] → [Inference] → [Decoding]
    ↓
MessageBus: EVENT_LLM_INFERENCE_COMPLETE
    ↓
PersonalityEngine processes response
    ↓
Memory storage (with vision metadata)
    ↓
TTS output / UI display
```

**New Events**:
- `EVENT_VISION_PREPROCESSING_START`: Vision encoder starting
- `EVENT_VISION_PREPROCESSING_COMPLETE`: Vision features ready
- `EVENT_MULTIMODAL_INFERENCE`: Combined vision+text inference

---

## Technical Implementation

### Model Architecture

**Qwen2-VL-2B Components**:
```
┌─────────────────────────────────────┐
│    User Input (Text + Image?)       │
└─────────────────┬───────────────────┘
                  ↓
      ┌──────────────────────┐
      │  QwenVLTokenizer      │
      │  - Text → Token IDs   │
      │  - BPE encoding       │
      │  - Chat format        │
      └──────────┬────────────┘
                 ↓
   ┌─────────────────────────────┐
   │  Vision Preprocessing        │  (if image provided)
   │  - Resize to 224x224         │
   │  - Normalize RGB values      │
   │  - Convert to tensor         │
   └──────────┬──────────────────┘
              ↓
┌─────────────────────────────────────┐
│      ONNX Runtime Inference         │
│                                     │
│  1. QwenVL_B_q4f16.onnx            │
│     Vision Encoder (if image)      │
│     234 MB                          │
│                                     │
│  2. QwenVL_E_q4f16.onnx            │
│     Text Decoder + Cross-Attention │
│     997 MB                          │
│                                     │
│  3. QwenVL_A_q4f16.onnx            │
│     Output projection              │
│     1.33 GB                         │
│                                     │
│  + embeddings_bf16.bin (467 MB)    │
└─────────────┬───────────────────────┘
              ↓
      ┌──────────────────┐
      │  QwenVLTokenizer  │
      │  Token IDs → Text │
      └──────────┬────────┘
                 ↓
         ┌──────────────┐
         │   Response    │
         └───────────────┘
```

### File Structure

**Model Files** (in Downloads folder):
```
/storage/emulated/0/Download/
├── QwenVL_A_q4f16.onnx      (1.33 GB) - Output projection
├── QwenVL_B_q4f16.onnx      (234 MB)  - Vision encoder
├── QwenVL_E_q4f16.onnx      (997 MB)  - Text decoder
├── embeddings_bf16.bin      (467 MB)  - Token embeddings
├── vocab.json               (2.78 MB) - Vocabulary
└── merges.txt               (1.67 MB) - BPE merges
```

**Code Files**:
```
app/src/main/java/com/ailive/ai/llm/
├── LLMManager.kt              - Main inference engine (UPDATED)
├── QwenVLTokenizer.kt         - Qwen tokenizer (NEW)
├── ModelDownloadManager.kt    - Download management (UPDATED)
└── SimpleGPT2Tokenizer.kt     - Legacy (deprecated)
```

---

## Usage Examples

### Example 1: Text-Only Chat (Backward Compatible)

```kotlin
// PersonalityEngine or any component
val response = llmManager.generate(
    prompt = "Hello, how are you?",
    agentName = "AILive"
)
// Output: "I'm doing well, thank you for asking! How can I help you today?"
```

### Example 2: Visual Question Answering

```kotlin
// User shows camera to AI
val cameraImage: Bitmap = cameraManager.captureFrame()

val response = llmManager.generate(
    prompt = "What objects do you see?",
    image = cameraImage,
    agentName = "AILive"
)
// Output: "I see a laptop, a coffee mug, and a notebook on a desk."
```

### Example 3: Image Captioning

```kotlin
val photo: Bitmap = loadImageFromGallery()

val response = llmManager.generate(
    prompt = "Describe this image in detail.",
    image = photo,
    agentName = "VisionAnalysisTool"
)
// Output: "This is a sunset over the ocean. The sky is painted with..."
```

### Example 4: Vision Tool Integration

```kotlin
// analyze_vision tool
override suspend fun execute(params: Map<String, Any>): ToolResult {
    val image = params["image"] as? Bitmap
    val question = params["question"] as? String ?: "What is this?"

    val analysis = llmManager.generate(
        prompt = question,
        image = image,
        agentName = "VisionAnalysisTool"
    )

    // Automatically logged to memory by PersonalityEngine
    return ToolResult.success(analysis)
}
```

---

## Migration Path

### Phase 1: Core Update (Current)
- ✅ Updated ModelDownloadManager for Qwen2-VL
- ✅ Created QwenVLTokenizer
- ✅ Updated README documentation
- ⏳ Update LLMManager for multimodal inference
- ⏳ Add vision preprocessing pipeline

### Phase 2: Integration
- ⏳ Update PersonalityEngine for vision context
- ⏳ Update analyze_vision tool implementation
- ⏳ Add vision metadata to memory storage

### Phase 3: UI/UX
- ⏳ Add camera button in chat UI
- ⏳ Show image thumbnails in conversation
- ⏳ Add "Send image" option in settings

### Phase 4: Optimization
- ⏳ Image caching for repeated queries
- ⏳ Vision preprocessing optimization
- ⏳ Memory management for large images

---

## Performance Expectations

**Inference Time** (on mid-range Android):
- Text-only: ~2.5s per token (~100s for 40 tokens)
- Vision + text: ~4-5s per token (~160-200s for 40 tokens)
  - Vision encoding: ~30-40s (one-time per image)
  - Text generation: ~2.5s per token (same as before)

**Memory Usage**:
- Text-only: ~2GB RAM
- Vision + text: ~3-3.5GB RAM (vision encoder adds ~1GB)

**Recommendations**:
- Use MAX_LENGTH = 40 for mobile
- Cache vision embeddings for repeated questions about same image
- Consider smaller responses for vision queries (20-30 tokens)

---

## Error Handling

**Missing Vision Files**:
```kotlin
if (image != null && !isVisionModelLoaded()) {
    throw IllegalStateException("Vision model files missing. Please download QwenVL_B_q4f16.onnx")
}
```

**Image Too Large**:
```kotlin
if (image.width > 1024 || image.height > 1024) {
    image = resizeImage(image, maxSize = 1024)
}
```

**OOM (Out of Memory)**:
```kotlin
try {
    return generateWithVision(prompt, image)
} catch (e: OutOfMemoryError) {
    Log.e(TAG, "OOM during vision inference, falling back to text-only")
    return generate(prompt, image = null)  // Fallback
}
```

---

## Future Enhancements

1. **Video Understanding**: Process video frames sequentially
2. **Multi-Image Queries**: "Compare these two images"
3. **Image Generation**: Add diffusion model for image output
4. **Vision Fine-Tuning**: Custom vision adapters for specific use cases
5. **Edge TPU**: Hardware acceleration for vision encoder

---

## API Reference

### LLMManager

#### `suspend fun initialize(): Boolean`
Loads Qwen2-VL model files from Downloads folder.

**Returns**: `true` if successful, `false` otherwise

**Example**:
```kotlin
val success = llmManager.initialize()
if (success) {
    Log.i(TAG, "Vision-chat module ready!")
}
```

---

#### `suspend fun generate(prompt: String, image: Bitmap? = null, agentName: String = "AILive"): String`
Generates response from text input, optionally with vision input.

**Parameters**:
- `prompt`: User's text input or question
- `image`: Optional image for vision understanding
- `agentName`: Identifier for logging/memory (default: "AILive")

**Returns**: AI-generated response text

**Throws**:
- `IllegalStateException`: If not initialized
- `OutOfMemoryError`: If image too large or insufficient RAM

**Example**:
```kotlin
val response = llmManager.generate(
    prompt = "What's in this photo?",
    image = bitmap,
    agentName = "AILive"
)
```

---

#### `fun isInitialized(): Boolean`
Checks if vision-chat module is ready.

**Returns**: `true` if initialized, `false` otherwise

---

#### `fun isVisionCapable(): Boolean`
Checks if vision encoder is loaded.

**Returns**: `true` if vision model files present, `false` otherwise

---

#### `fun close()`
Releases model resources and ONNX sessions.

**Example**:
```kotlin
override fun onDestroy() {
    llmManager.close()
    super.onDestroy()
}
```

---

## Conclusion

The Vision-Chat module (LLMManager) provides a clean, modular interface for multimodal AI within AILive's architecture. It maintains backward compatibility while adding powerful vision capabilities, integrates seamlessly with existing tools and memory systems, and follows AILive's design principles of modularity and clear interfaces.

**Key Takeaways**:
- ✅ **Modular**: Clear boundaries with PersonalityEngine and tools
- ✅ **Backward Compatible**: Text-only calls still work
- ✅ **Integrated**: Works with memory, MessageBus, and tools
- ✅ **Persistent**: Models in Downloads folder survive uninstalls
- ✅ **Extensible**: Easy to add new vision capabilities

**Next Steps**: Update LLMManager implementation to match this architecture.
