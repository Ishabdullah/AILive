# LLM Quantization Guide for AILive

**Status**: 📋 GUIDE (Model Not Yet Quantized)
**Purpose**: Document how to add quantized LLM for optimal performance

---

## 🎯 Goal

Replace the current full-precision TinyLlama model with a quantized version to achieve:
- **10-20x faster inference** (target: <500ms vs current 2-3s)
- **4x smaller model size** (~250MB vs ~1GB)
- **Lower memory usage** (<500MB vs >1GB)
- **Better mobile battery life**

---

## 📊 Current vs Target State

### Current (Not Implemented Yet):
- Model: TinyLlama-1.1B (ONNX)
- Precision: FP32 (full precision)
- Size: ~1GB
- Inference: 2-3 seconds (CPU-only in testing)
- Status: ⚠️ **Model file not present** in project

### Target (Phase 4 Goal):
- Model: TinyLlama-1.1B-Chat (Quantized ONNX)
- Precision: INT8 or INT4 quantization
- Size: ~250-500MB
- Inference: <500ms (GPU + quantization)
- Status: 📋 To be implemented

---

## 🔧 Quantization Options

### Option 1: ONNX Runtime Quantization (Recommended)
**Advantages:**
- Already using ONNX Runtime
- Built-in quantization support
- NNAPI GPU acceleration ✅ (already enabled)
- Good Android compatibility

**Steps:**
1. Download TinyLlama-1.1B-Chat ONNX model
2. Use ONNX Runtime quantization tools
3. Convert to INT8 or INT4
4. Place in `app/src/main/assets/models/`

**Commands** (run on development machine):
```python
# Install ONNX Runtime Tools
pip install onnxruntime-tools

# Quantize the model
from onnxruntime.quantization import quantize_dynamic, QuantType

model_fp32 = 'tinyllama-1.1b-chat.onnx'
model_quant = 'tinyllama-1.1b-chat-int8.onnx'

quantize_dynamic(
    model_fp32,
    model_quant,
    weight_type=QuantType.QUInt8  # or QInt8
)
```

**Integration:**
- Update `MODEL_PATH` in LLMManager.kt
- No code changes needed (ONNX Runtime handles quantized models)

---

### Option 2: Pre-Quantized Models (Fastest to Deploy)
**Advantages:**
- Ready to use
- Already optimized
- Tested by community

**Sources:**
1. **Hugging Face ONNX Models**
   - https://huggingface.co/models?library=onnx&sort=downloads
   - Look for INT8 or quantized versions

2. **ONNX Model Zoo**
   - https://github.com/onnx/models
   - Pre-optimized mobile models

3. **Microsoft ONNX Runtime Models**
   - https://github.com/microsoft/onnxruntime-inference-examples
   - Optimized for mobile

**Recommended Model:**
- **Phi-2 INT8 ONNX** (~1.4GB quantized, very high quality)
- **SmolLM2-360M INT8** (~180MB, fast, good for mobile)
- **TinyLlama-1.1B INT8** (~637MB quantized, balanced)

---

### Option 3: TensorFlow Lite Conversion
**Advantages:**
- Excellent Android optimization
- Very small model sizes
- Good GPU delegate support

**Steps:**
1. Convert TinyLlama to TensorFlow
2. Use TFLite converter with quantization
3. Replace ONNX Runtime with TFLite interpreter

**Note:** Requires more code changes (switch from ONNX to TFLite API)

---

## 📦 Implementation Steps

### Step 1: Download or Create Quantized Model

#### Option A: Download Pre-Quantized (Recommended)
```bash
# Download from Hugging Face
cd /data/data/com.termux/files/home/AILive
mkdir -p app/src/main/assets/models

# Example: SmolLM2-360M (small, fast)
wget https://huggingface.co/.../smollm2-360m-instruct-int8.onnx \
     -O app/src/main/assets/models/smollm2-360m-int8.onnx
```

#### Option B: Quantize Existing Model
```python
# On development machine with Python
from onnxruntime.quantization import quantize_dynamic, QuantType

quantize_dynamic(
    'tinyllama-1.1b-chat.onnx',
    'tinyllama-1.1b-chat-int8.onnx',
    weight_type=QuantType.QUInt8,
    optimize_model=True
)
```

---

### Step 2: Update LLMManager.kt

```kotlin
companion object {
    private const val TAG = "LLMManager"

    // UPDATED: Use quantized model
    private const val MODEL_PATH = "models/tinyllama-1.1b-chat-int8.onnx"
    // OR
    private const val MODEL_PATH = "models/smollm2-360m-int8.onnx"

    // ... rest of constants
}
```

---

### Step 3: No Other Code Changes Needed!

ONNX Runtime automatically handles quantized models. The code changes already made in Phase 4 are sufficient:
- ✅ NNAPI GPU acceleration enabled
- ✅ Optimized session options
- ✅ Reduced MAX_LENGTH (80 tokens)
- ✅ Increased TEMPERATURE (0.9)

---

### Step 4: Test Performance

After adding quantized model:
```bash
# Build and install
adb install -r app-debug.apk

# Monitor logs
adb logcat | grep LLMManager

# Look for:
# - "NNAPI GPU acceleration enabled"
# - Inference time logs
# - Model size confirmation
```

**Expected Results:**
- Model loads in <5 seconds
- Inference time: <500ms per generation
- Memory usage: <500MB
- Varied responses (not repetitive)

---

## 🎯 Recommended Next Steps

### Immediate (Can Do Now):
1. ✅ **Prompt optimization** - DONE (removed vision keyword bias)
2. ✅ **NNAPI GPU acceleration** - DONE (enabled in code)
3. ✅ **Generation parameters** - DONE (reduced tokens, increased temperature)
4. 🔄 **Re-enable LLM** - Next step (with fallback)
5. 🧪 **Test with fallback** - Verify prompt fixes work

### Future (Requires Model Download):
6. 📥 **Download quantized model** - ~250-500MB download
7. 📦 **Add to APK assets** - Update MODEL_PATH
8. 🧪 **Performance testing** - Measure actual inference time
9. 🚀 **Production deployment** - If performance meets targets

---

## 📈 Expected Performance Improvements

| Metric | Current (No Model) | With Full Model | With Quantized Model (Target) |
|--------|-------------------|-----------------|------------------------------|
| Model Size | N/A | ~1GB | ~250-500MB ✅ |
| Inference Time | N/A (fallback) | 2-3s ⚠️ | <500ms ✅ |
| Memory Usage | <100MB | >1GB | <500MB ✅ |
| Response Quality | Simple fallbacks | LLM-generated | LLM-generated (similar) |
| Response Variety | Good (rule-based) | Variable | Good (prompt fixed) |
| GPU Utilization | 0% | 0% (no NNAPI) | 50-80% ✅ |

---

## 🔍 Model Size Comparison

### Option 1: SmolLM2-360M (Recommended for Mobile)
- **Full precision**: ~1.4GB
- **INT8 quantized**: ~180MB ✅
- **Quality**: Good for chat
- **Speed**: Very fast (<300ms)
- **Best for**: Mobile devices, fast responses

### Option 2: TinyLlama-1.1B
- **Full precision**: ~4.4GB
- **ONNX optimized**: ~1GB
- **INT8 quantized**: ~637MB
- **INT4 quantized**: ~250MB ✅
- **Quality**: Better than SmolLM2
- **Speed**: Fast (<500ms with GPU)
- **Best for**: Balance of quality and speed

### Option 3: Phi-2 (Best Quality)
- **Full precision**: ~5.2GB
- **INT8 quantized**: ~1.4GB
- **Quality**: Excellent
- **Speed**: Slower (~800ms)
- **Best for**: When quality > speed

---

## 🚀 Quick Start (When Ready to Add Model)

1. **Choose a model** from options above
2. **Download** quantized INT8 version
3. **Place** in `app/src/main/assets/models/`
4. **Update** MODEL_PATH in LLMManager.kt
5. **Build** and test: `./gradlew assembleDebug`
6. **Verify** inference time in logs

---

## 📝 Current Status

✅ **Phase 4 Code Optimizations Complete:**
- Prompt bias fixed (no vision keyword issue)
- NNAPI GPU enabled
- Generation parameters optimized
- LLM ready to be re-enabled

⏳ **Pending:**
- Model download/quantization (user can do when needed)
- Performance testing with actual model

🎯 **Next Action:**
- Re-enable LLM in PersonalityEngine with optimized prompt
- Test with fallback system
- Add quantized model when performance testing is needed

---

**Document Status**: ✅ Complete
**Model Status**: ⏳ Not yet added (optional future step)
**Code Status**: ✅ Ready for quantized model

---

*Guide created during Phase 4: Performance Optimization*
*Date: October 29, 2025*
