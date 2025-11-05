# 🚀 START HERE - Next Session Quick Reference

**Last Updated**: November 5, 2025
**Session Status**: LLM tokenization implemented, waiting for test

---

## ⚡ Quick Status

### ✅ What's Working
- Model downloads (348MB SmolLM2-360M INT8)
- App stable, no crashes
- Proper HuggingFace BPE tokenization implemented

### 🔄 What Needs Testing
- **CRITICAL**: Real LLM responses (tokenization code added but not tested yet)

### 📦 Latest Commit
```
3e8eb45 - fix: compilation errors in LLMManager
```

---

## 🎯 First Thing To Do Next Session

### Test LLM Responses (5 minutes)

1. **Download APK** from GitHub Actions
2. **Install on S24 Ultra**: `adb install -r AILive-debug.apk`
3. **Test these prompts**:
   - "Hello, how are you?" → Should get real LLM response
   - "What is 2+2?" → Should get "4"
   - "Tell me a joke" → Should get actual joke

**Expected**: Real LLM-generated text
**NOT Expected**: "I'm here to help. I can see through your camera..."

### If LLM Works ✅
→ Continue to Performance Measurement (see below)

### If LLM Doesn't Work ❌
→ Check Troubleshooting section (see below)

---

## 🔍 How to Check Logs

```bash
# Real-time LLM logs
adb logcat | grep -E "LLMManager|PersonalityEngine"

# Look for these SUCCESS indicators:
✅ "Tokenizer loaded successfully"
✅ "Token count: X, First 10 tokens: [...]"
✅ "LLM generated response in Xms"

# Look for these FAILURE indicators:
❌ "LLM generation failed, using fallback"
❌ "Tokenizer not initialized"
❌ Exception stack traces
```

---

## 📊 Performance Measurement (If LLM Works)

### Measure These Metrics

```bash
# Watch for timing in logs
adb logcat | grep -E "LLM|generated|response"

# Key metrics to record:
```

1. **Model Load Time**: First startup only
2. **First Token Latency**: Time to first word
3. **Tokens/Second**: Overall generation speed
4. **Total Response Time**: User input → spoken output

### Performance Targets
- STT → LLM → TTS: **<3 seconds total**
- LLM inference: **25-60 tokens/sec**
- First token: **<1 second**

### Test Prompts for Benchmarking
1. "Hello" (short response)
2. "What is the capital of France?" (medium)
3. "Explain how photosynthesis works" (long)

---

## 🛠️ Troubleshooting Guide

### Issue 1: Still Getting Fallback Responses

**Symptoms**: "I'm here to help. I can see through your camera..."

**Check**:
```bash
adb logcat | grep "LLM generation failed"
```

**Possible Causes**:
1. Tokenizer failed to load
2. ONNX model path incorrect
3. Model inference exception

**Fix**:
```bash
# Check if tokenizer.json exists in app
adb shell ls -la /data/data/com.ailive/files/tokenizer.json

# Should see: 2.1MB file
# If not found: tokenizer copy failed
```

### Issue 2: App Crashes on Send

**Check**:
```bash
adb logcat | grep "FATAL"
```

**Possible Causes**:
1. Tokenizer initialization failed
2. ONNX session null
3. OutOfMemoryError

**Quick Fix**: Add more error logging in LLMManager.kt

### Issue 3: Very Slow Responses (>10 seconds)

**Possible Causes**:
1. NNAPI not enabled (using CPU only)
2. Model not optimized
3. No KV cache (expected for now)

**Check**:
```bash
adb logcat | grep "NNAPI"
# Should see: "NNAPI GPU acceleration enabled"
```

---

## 🔧 Quick Fixes Reference

### Fix 1: Rebuild Tokenizer Loading

If tokenizer fails to load, edit `LLMManager.kt:150-166`:

```kotlin
// Add more error handling
try {
    tokenizer = HuggingFaceTokenizer.newInstance(tokenizerFile.toPath())
    Log.i(TAG, "✅ Tokenizer loaded: ${tokenizerFile.length()} bytes")
} catch (e: Exception) {
    Log.e(TAG, "❌ Tokenizer failed to load", e)
    throw e  // Don't silently fail
}
```

### Fix 2: Increase Max Tokens

If responses are cut off, edit `LLMManager.kt:35`:

```kotlin
// Change from 80 to 150
private const val MAX_LENGTH = 150
```

### Fix 3: Adjust Temperature

If responses are too random/creative, edit `LLMManager.kt:39`:

```kotlin
// Lower temperature for more focused responses
private const val TEMPERATURE = 0.7f  // Was 0.9f
```

---

## 📁 Key Files & Line Numbers

### LLM Core
- `LLMManager.kt:165` - Tokenizer initialization
- `LLMManager.kt:248` - tokenize() function
- `LLMManager.kt:262` - decode() function
- `LLMManager.kt:211` - generateONNX() main inference

### Response Generation
- `PersonalityEngine.kt:335` - generateResponse()
- `PersonalityEngine.kt:362` - Calls LLMManager.generate()
- `PersonalityEngine.kt:374` - Fallback handler (catches exceptions)

### Error Handling
- `PersonalityEngine.kt:500` - generateFallbackResponse()
  - This is where "I'm here to help..." comes from
  - If you see this, LLM failed

---

## 🎬 Alternative: Switch to GGUF

If ONNX tokenization continues to be problematic:

### Enable GGUF Support

1. **Uncomment in `build.gradle.kts`**:
   - Lines 20-36: NDK configuration
   - Lines 49-56: CMake build

2. **Uncomment in `LLMManager.kt`**:
   - Line 51-52: llamaBridge initialization
   - Search for "GGUF support disabled" comments

3. **Push to GitHub**:
```bash
git add app/build.gradle.kts app/src/main/java/com/ailive/ai/llm/LLMManager.kt
git commit -m "enable: GGUF support with llama.cpp"
git push origin main
```

4. **Download GGUF model**: SmolLM2-360M-Q4_K_M.gguf (220MB)

**Advantages**:
- Proven approach (SmolChat uses this)
- Built-in tokenization
- Smaller model size
- Potentially faster

**Disadvantages**:
- Requires native library build (may fail on GitHub Actions)
- More complex setup

---

## 📝 Session Continuation Checklist

```
[ ] Download latest APK from GitHub Actions
[ ] Install on S24 Ultra
[ ] Test LLM with 3 prompts
[ ] Check logs for success/failure
[ ] If works: Measure performance
[ ] If fails: Check troubleshooting guide
[ ] Document results
[ ] Plan next steps
```

---

## 🔗 Quick Links

- **GitHub Actions**: https://github.com/Ishabdullah/AILive/actions
- **Last Session Summary**: `SESSION-COMPLETE-2025-11-05.md`
- **Tokenization Fix Docs**: `LLM-TOKENIZATION-FIX.md`
- **Build Artifacts**: Check latest workflow run

---

## 💡 Expected Outcomes This Session

### Best Case ✅
- LLM generates real responses
- Performance meets targets (<3s total)
- No errors or crashes
→ **Next**: Optimize performance, add features

### Good Case ✅
- LLM generates real responses
- Performance slower than target
→ **Next**: Optimize inference, enable KV cache

### Needs Work ⚠️
- LLM works but responses are poor quality
→ **Next**: Tune temperature, max_tokens, prompts

### Worst Case ❌
- Still getting fallback responses
→ **Next**: Debug tokenizer, consider GGUF alternative

---

## 🎯 Success Criteria

**Minimum**: LLM generates ANY real text (not hardcoded fallback)
**Target**: LLM generates coherent, contextual responses
**Stretch**: Sub-3s latency with high-quality responses

---

**Remember**: The tokenization IS implemented - we just need to verify it works!

**Most likely outcome**: It should work! 🎉
