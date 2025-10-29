# AILive Model Setup Guide

This document explains how to set up the language model for AILive's intelligent responses.

## 📦 Required Model

**TinyLlama-1.1B-Chat ONNX** (Phase 2.6)
- Size: ~637MB (quantized)
- Format: ONNX
- Purpose: Language generation for intelligent AI responses

## 🔽 Download Instructions

### Option 1: Hugging Face (Recommended)

```bash
cd ~/AILive/app/src/main/assets/
mkdir -p models

# Download TinyLlama ONNX model
wget https://huggingface.co/TinyLlama/TinyLlama-1.1B-Chat-v1.0-ONNX/resolve/main/tinyllama-1.1b-chat.onnx \
  -O models/tinyllama-1.1b-chat.onnx

# Verify download
ls -lh models/tinyllama-1.1b-chat.onnx
```

### Option 2: Direct from Hugging Face Hub

1. Visit: https://huggingface.co/TinyLlama/TinyLlama-1.1B-Chat-v1.0-ONNX
2. Download the ONNX model file
3. Place in: `~/AILive/app/src/main/assets/models/tinyllama-1.1b-chat.onnx`

### Option 3: Using Python (Alternative)

```python
from huggingface_hub import hf_hub_download

model_path = hf_hub_download(
    repo_id="TinyLlama/TinyLlama-1.1B-Chat-v1.0-ONNX",
    filename="tinyllama-1.1b-chat.onnx",
    local_dir="~/AILive/app/src/main/assets/models/"
)
```

## 📁 File Structure

After setup, your models directory should look like:

```
~/AILive/app/src/main/assets/models/
├── mobilenet_v3_small.tflite    (13.3MB - Vision)
├── labels.txt                    (ImageNet labels)
└── tinyllama-1.1b-chat.onnx     (637MB - Language) ← New!
```

## ✅ Verification

After downloading, rebuild the app:

```bash
cd ~/AILive
./gradlew assembleDebug
```

Check logs for successful initialization:

```bash
adb logcat -s LLMManager

# Expected output:
# LLMManager: 🤖 Initializing LLM (ONNX Runtime)...
# LLMManager: 📂 Loading model: tinyllama-1.1b-chat.onnx (637MB)
# LLMManager: ✅ LLM initialized successfully!
```

## 🎮 Usage

Once the model is loaded, AILive will:
- Generate intelligent responses instead of using hardcoded text
- Provide agent-specific personalities (MotorAI, EmotionAI, etc.)
- Answer user questions contextually
- Maintain conversation flow

### Example:

**Before (Phase 2.4):**
- User: "Why is the sky blue?"
- AI: "Looking around with my camera. I can see my surroundings." ❌

**After (Phase 2.6):**
- User: "Why is the sky blue?"
- AI: "The sky appears blue because molecules in Earth's atmosphere scatter blue light from the sun more than other colors..." ✅

## ⚠️ Fallback Mode

If the model is not found or fails to load:
- AILive will continue to work with enhanced fallback responses
- You'll see: "⚠️ LLM not available, using fallback responses"
- All features except intelligent language generation will work normally

## 🔧 Troubleshooting

### Model Not Found Error

```
❌ Model file not found: /data/user/0/com.ailive/files/models/tinyllama-1.1b-chat.onnx
```

**Solution:**
1. Verify file location: `~/AILive/app/src/main/assets/models/`
2. File must be in `assets` folder (packaged with APK)
3. Rebuild app after adding model

### Out of Memory Error

```
❌ Failed to initialize LLM: OutOfMemoryError
```

**Solution:**
- Ensure device has at least 2GB free RAM
- Close other apps before starting AILive
- Model requires ~1GB RAM during inference

### Slow Inference

If responses take >10 seconds:
- This is normal on first inference (model loading)
- Subsequent responses should be 2-3 seconds
- CPU inference is slower than GPU (intentional for compatibility)

## 📊 Model Specifications

### TinyLlama-1.1B-Chat ONNX

| Property | Value |
|----------|-------|
| Parameters | 1.1 billion |
| Size | 637MB (quantized) |
| Format | ONNX (optimized) |
| Threads | 4 (CPU) |
| Max Tokens | 150 |
| Temperature | 0.7 |
| Top-P | 0.9 |
| Inference Time | 2-3 seconds/response |
| Memory Usage | ~1GB RAM |

### Agent Personalities

Each agent has a unique personality prompt:

- **MotorAI**: Technical, action-oriented (device control & vision)
- **EmotionAI**: Warm, empathetic (emotional intelligence)
- **MemoryAI**: Thoughtful, detailed (information storage)
- **PredictiveAI**: Logical, analytical (forecasting)
- **RewardAI**: Positive, energetic (goals & motivation)
- **MetaAI**: Authoritative, strategic (planning & coordination)

## 🚀 Next Steps

After setting up the model:

1. Build APK: `./gradlew assembleDebug`
2. Install on device
3. Say "Hey AILive"
4. Ask real questions and get intelligent responses!

## 📚 Resources

- **TinyLlama Model**: https://huggingface.co/TinyLlama/TinyLlama-1.1B-Chat-v1.0
- **ONNX Runtime**: https://onnxruntime.ai/docs/get-started/with-android.html
- **AILive Repo**: https://github.com/Ishabdullah/AILive

---

**Model Setup Complete!** 🎉

Your AILive assistant now has real intelligence powered by TinyLlama.
