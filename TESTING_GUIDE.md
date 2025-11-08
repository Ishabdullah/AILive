# AILive Testing Guide - GPT-2 Migration

**Date:** 2025-11-08
**Branch:** `claude/ailive-code-review-011CUseJ8kG4zVw12eyx4BsZ`
**Changes:** Switched from SmolLM2 to GPT-2 for ONNX compatibility

---

## 🎯 What Changed

We switched from SmolLM2 to GPT-2 because SmolLM2 uses custom Microsoft ONNX operators (`RotaryEmbedding`) that are not supported in standard Android ONNX Runtime.

**Key Changes:**
- ✅ Model: GPT-2 ONNX (548MB) from HuggingFace Optimum
- ✅ Tokenizer: Official GPT-2 BPE tokenizer (1.3MB)
- ✅ Chat format: Simple text format (no special tokens)
- ✅ Model Integrity Verifier: Checks file health at startup
- ✅ UI updated to show GPT-2 options

---

## 📱 Testing Checklist

### 1. Installation & First Launch

```bash
# Build and install
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Clear data for fresh test
adb shell pm clear com.ailive
```

**Expected:**
- Welcome dialog appears
- Option to "Download GPT-2 ONNX (~548MB)" visible
- Can select model and start download

**Test:** Tap "Download Model" and verify:
- Progress dialog shows download percentage
- File downloads to `/data/data/com.ailive/files/models/gpt2-onnx.onnx`
- "Model downloaded successfully!" toast appears

---

### 2. Model Integrity Verification

```bash
# Check if model exists and verify size
adb shell run-as com.ailive ls -lh /data/data/com.ailive/files/models/
```

**Expected Output:**
```
-rw------- 1 u0_a341 u0_a341 548M 2025-11-08 XX:XX gpt2-onnx.onnx
```

**Verify in logs:**
```bash
adb logcat -s LLMManager:* | grep "integrity"
```

**Expected Logs:**
```
I LLMManager: 🔍 Verifying model integrity...
I ModelIntegrityVerifier: ✅ Model integrity verified successfully
I ModelIntegrityVerifier:    Path: /data/data/com.ailive/files/models/gpt2-onnx.onnx
I ModelIntegrityVerifier:    Size: 548 MB
```

---

### 3. LLM Initialization Test

```bash
# Watch LLM initialization
adb logcat -c && adb logcat -s LLMManager:* AILiveCore:*
```

**Expected Logs (in order):**
```
I LLMManager: 🤖 Initializing LLM (ONNX-only mode)...
I LLMManager: ⏱️  This may take 5-10 seconds for model loading...
I LLMManager: 📂 Loading model: gpt2-onnx.onnx (548MB)
I LLMManager:    Format: ONNX (ONNX Runtime with NNAPI)
I LLMManager: 🔍 Verifying model integrity...
I LLMManager: 🔷 Loading with ONNX Runtime...
I LLMManager: ✅ NNAPI GPU acceleration enabled
I LLMManager: ✅ Tokenizer loaded successfully
I LLMManager: ✅ LLM initialized successfully!
I LLMManager:    Model: gpt2-onnx.onnx
I LLMManager:    Size: 548MB
I LLMManager:    Engine: ONNX Runtime
I AILiveCore: 🎉 AI responses are now powered by the language model!
```

**Test:**
- Wait for "Language model loaded..." voice notification (~10 seconds)
- ✅ = Initialization successful
- ❌ = Check for errors in logs

---

### 4. Chat Template Format Test

Send a simple message and verify the prompt format:

```bash
adb logcat -s LLMManager:* | grep -A 5 "Tokenizing"
```

**Expected Format (GPT-2 style - NO special tokens):**
```
System: You are AILive, a unified on-device AI companion...

User: Hello