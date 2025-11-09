# GPT-2 Troubleshooting Guide

## Problem: Getting Fallback Responses Instead of Real AI Responses

If you're seeing the generic fallback message instead of real AI responses from GPT-2, follow these diagnostic steps.

---

## Quick Diagnostic (Termux/ADB)

### 1. Run Quick Check Script
```bash
./quick_check.sh
```

This will show you:
- ✓ If model file exists and correct size
- ✓ Recent errors
- ✓ LLM initialization status
- ✓ Generation attempts
- ✓ Fallback response occurrences

### 2. Run Full Diagnostic
```bash
./diagnose_gpt2.sh
```

This provides detailed analysis of:
- Model file integrity (size should be ~653MB)
- Tokenizer presence (should be ~1.3MB)
- LLM initialization logs
- Inference attempts and errors
- ONNX Runtime errors
- PersonalityEngine behavior
- Live log monitoring

---

## Manual Checks

### Check Model File
```bash
adb shell "run-as com.ailive ls -lh /data/data/com.ailive/files/models/"
```

**Expected:**
```
-rw------- 1 u0_a123 u0_a123 653M 2025-11-09 XX:XX gpt2-decoder.onnx
```

### Check Tokenizer
```bash
adb shell "run-as com.ailive ls -lh /data/data/com.ailive/files/tokenizer.json"
```

**Expected:**
```
-rw------- 1 u0_a123 u0_a123 1.3M 2025-11-09 XX:XX tokenizer.json
```

### Monitor Live Logs
```bash
# Clear old logs first
adb logcat -c

# Monitor LLM activity
adb logcat -s LLMManager:* PersonalityEngine:*
```

---

## Common Issues & Solutions

### Issue 1: Model Not Downloaded
**Symptom:** Model file doesn't exist or is 0 bytes

**Solution:**
1. Open AILive app
2. Go to first-run dialog
3. Tap "Download Model"
4. Select "GPT-2 ONNX (~653MB)"
5. Wait for download to complete
6. Check file size: `adb shell "run-as com.ailive stat -c %s /data/data/com.ailive/files/models/gpt2-decoder.onnx"`

### Issue 2: Model Download Incomplete
**Symptom:** Model file exists but is < 600MB

**Solution:**
1. Delete corrupted file: `adb shell "run-as com.ailive rm /data/data/com.ailive/files/models/gpt2-decoder.onnx"`
2. Re-download from app
3. Verify size after download

### Issue 3: ONNX Runtime Error
**Symptom:** Logs show `OrtException` or `onnxruntime error`

**Check logs:**
```bash
adb logcat -d | grep -i "OrtException\|onnxruntime error"
```

**Common errors:**
- **"not a registered function/op"** - Model uses unsupported operators
- **"invalid model"** - Model file corrupted
- **"out of memory"** - Not enough RAM (GPT-2 needs ~1.5GB)

### Issue 4: Tokenizer Not Found
**Symptom:** Logs show "Tokenizer not initialized"

**Solution:**
1. Check if tokenizer exists
2. Re-install app (tokenizer is in APK assets)
3. Verify it gets copied to `/data/data/com.ailive/files/`

### Issue 5: Inference Not Running
**Symptom:** No "Starting inference" messages in logs

**Check:**
```bash
adb logcat -d -s LLMManager:* | grep "Starting inference"
```

**If no results:**
- LLM might not be initialized
- PersonalityEngine not calling LLM
- Falling back before reaching LLM

### Issue 6: Inference Runs But Generates Nothing
**Symptom:** See "Starting inference" but "Generated 0 tokens"

**Causes:**
- Model outputs wrong shape
- Sampling function failing
- All logits are NaN/Inf

**Debug:**
Look for these log messages:
```
🔍 Model outputs: X tensors
🔍 Vocab size: XXXXX (expected: 50257)
```

If vocab size != 50257, wrong model loaded.

---

## Enhanced Logging (Version 2025-11-09)

The latest version includes detailed debug logging:

### What to Look For:

**1. Initialization:**
```
🤖 Initializing LLM (ONNX-only mode)...
🔍 Verifying model integrity...
✅ Model integrity verified successfully
🔷 Loading with ONNX Runtime...
✅ NNAPI GPU acceleration enabled
✅ Tokenizer loaded successfully
✅ LLM initialized successfully!
```

**2. Generation Start:**
```
🔍 Starting inference with X input tokens
   Input tokens: [token_ids...]
🔍 First step - running model with inputs: [input_ids, attention_mask]
```

**3. Model Output Info:**
```
🔍 Model outputs: 1 tensors
   Output 'logits': shape [1, X, 50257]
🔍 Vocab size: 50257 (expected: 50257)
```

**4. Token Generation:**
```
Step 0: Generated token 12345
Step 1: Generated token 67890
Step 2: Generated token 23456
...
✓ GPT-2 EOS token detected at step 42, stopping generation
✓ Generated 42 tokens total
   Generated token IDs: [12345, 67890, ...]
```

**5. Success:**
```
✨ LLM generated response in XXXXms
Generated text (decoded): "Your AI response here..."
```

---

## Interpreting Results

### ✅ Good Signs:
- Model size exactly ~653MB
- Tokenizer exists (~1.3MB)
- "LLM initialized successfully"
- "Starting inference" messages
- "Generated X tokens" where X > 0
- Vocab size = 50257
- See actual token IDs being generated

### ❌ Bad Signs:
- Model missing or wrong size
- "initialization failed"
- "OrtException" errors
- "Generated 0 tokens"
- Only seeing "fallback" in logs
- No "Starting inference" messages
- Vocab size != 50257

---

## Getting Help

If you still have issues after checking all of the above:

1. **Capture full logs:**
   ```bash
   adb logcat -d > ailive_logs.txt
   ```

2. **Get model info:**
   ```bash
   adb shell "run-as com.ailive ls -lh /data/data/com.ailive/files/models/" > model_info.txt
   ```

3. **Share:**
   - `ailive_logs.txt`
   - `model_info.txt`
   - What you typed as input
   - What response you got
   - Device model and Android version

---

## Expected Behavior (Working Correctly)

When everything works:

1. **User types:** "Hello"
2. **Logs show:**
   ```
   📝 Processing text command: 'Hello'
   🔍 Starting inference with 15 input tokens
   🔍 First step - running model
   Step 0: Generated token 15496
   Step 1: Generated token 11
   ...
   ✓ Generated 25 tokens total
   ✨ LLM generated response in 2500ms
   ✅ PersonalityEngine generated response: 'Hello! I'm AILive...'
   ```
3. **User sees:** Real AI response (not fallback)

---

## Files Modified for Diagnostics

- `LLMManager.kt` - Added detailed debug logging
- `quick_check.sh` - Quick diagnostic script
- `diagnose_gpt2.sh` - Comprehensive diagnostic script
- `GPT2_TROUBLESHOOTING.md` - This file

## Version History

- **2025-11-09:** Initial diagnostic tooling
  - Added enhanced logging to runInference()
  - Created diagnostic scripts
  - Documented troubleshooting process
