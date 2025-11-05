# Phase 7.10 - Testing Instructions for S24 Ultra
**Build:** GitHub Actions (latest)
**Device:** Samsung Galaxy S24 Ultra
**Goal:** Test ONNX-only implementation with <3s response time

---

## 🚀 Quick Start

### Step 1: Download APK from GitHub Actions

1. Go to: https://github.com/Ishabdullah/AILive/actions
2. Click on the **latest successful workflow run** (green checkmark ✅)
3. Scroll down to "Artifacts"
4. Download **ailive-debug** artifact
5. Extract `app-debug.apk` from the zip file

### Step 2: Install on S24 Ultra

```bash
# If using ADB from computer
adb install -r app-debug.apk

# Or transfer APK to phone and install manually
# (Enable "Install from Unknown Sources" in Settings)
```

### Step 3: Grant Permissions

On first launch, AILive will request:
- ✅ Camera permission (for vision features)
- ✅ Microphone permission (for voice input)
- ✅ Storage permission (Android 9 only - automatic on S24 Ultra with Android 14)

**Important:** Grant all permissions for full functionality.

---

## 📥 Test 1: Model Download (CRITICAL)

**Goal:** Verify ONNX model downloads successfully

### Steps:

1. **Launch AILive**
   - You should see "Welcome to AILive!" dialog

2. **Select "Download Model"**
   - Choose **"SmolLM2-360M ONNX INT8 (~348MB) - Recommended"**
   - This is optimized for S24 Ultra with NNAPI

3. **Monitor Download**
   - Download progress dialog should appear
   - Shows MB downloaded / total MB
   - Download time: ~2-5 minutes depending on WiFi

4. **Verify Success**
   - Should see "Model downloaded successfully!" toast
   - App should continue to main screen

### Expected Behavior:
- ✅ Progress updates every second
- ✅ Download completes without errors
- ✅ File moves to app storage automatically
- ✅ No crashes

### If Download Fails:
Check logcat for errors:
```bash
adb logcat | grep -E "ModelDownload|DownloadManager"
```

Common issues:
- Network timeout → Retry download
- Storage full → Need 500MB free space
- Permission denied → Should NOT happen on Android 14

---

## 💬 Test 2: LLM Inference (CRITICAL)

**Goal:** Verify model loads and generates responses

### Steps:

1. **Wait for Model Loading**
   - After download, app initializes model
   - Watch for "LLM initialized successfully!" in status

2. **Send Test Message**
   - Type: **"Hello, what can you do?"**
   - Tap send button

3. **Observe Response Time**
   - ⏱️ Target: **<2 seconds first response**
   - Response should appear in chat

4. **Test Multiple Messages**
   ```
   Message 1: "Hello, what can you do?"
   Message 2: "What's the weather like?"
   Message 3: "Tell me a joke"
   Message 4: "Help me with coding"
   ```

### Expected Behavior:
- ✅ Model loads within 10 seconds
- ✅ First response: <2s (with NNAPI GPU)
- ✅ Subsequent responses: <1.5s
- ✅ Responses are coherent (not gibberish)
- ✅ No crashes

### Performance Benchmarks (S24 Ultra + NNAPI):
- **Model load:** 5-10 seconds (one-time)
- **First token:** 0.5-1.0 seconds
- **Tokens/second:** 25-40 tokens/sec
- **80-token response:** 1.5-2.5 seconds total
- **Memory usage:** 300-450 MB
- **Battery drain:** <5% per hour of active use

### If Inference Fails:
Check logcat:
```bash
adb logcat | grep -E "LLMManager|ONNX|NNAPI"
```

Look for:
- ✅ "NNAPI GPU acceleration enabled" (good)
- ⚠️ "NNAPI not available, using CPU" (slower but works)
- ❌ "ONNX initialization failed" (critical error)

---

## 🎯 Test 3: S24 Ultra-Specific Optimizations

**Goal:** Verify GPU acceleration and performance

### Check GPU Acceleration:

1. **Watch Logcat During Model Load**
   ```bash
   adb logcat | grep -E "NNAPI|GPU"
   ```

2. **Look for:**
   ```
   ✅ NNAPI GPU acceleration enabled
   ```

3. **If CPU Fallback:**
   - Still works, just slower (3-5s vs 1.5-2s)
   - Check device thermal state (may throttle)

### Performance Monitoring:

**During inference, monitor:**

```bash
# CPU usage
adb shell top -n 1 | grep ailive

# Memory usage
adb shell dumpsys meminfo com.ailive | head -20

# Battery temperature
adb shell dumpsys battery
```

### Expected S24 Ultra Performance:

**With NNAPI (GPU):**
- CPU: 15-30% usage
- Memory: 350-450 MB
- Temperature: <40°C
- Response time: 1.5-2.5s

**CPU-only fallback:**
- CPU: 60-90% usage
- Memory: 300-400 MB
- Temperature: 40-45°C
- Response time: 3-5s

---

## 🔬 Test 4: Stress Testing

**Goal:** Verify stability under continuous use

### Steps:

1. **Send 20 Messages Rapidly**
   - Use the test messages above
   - Send back-to-back without waiting

2. **Check for:**
   - ✅ No crashes
   - ✅ No memory leaks
   - ✅ Performance stays consistent
   - ✅ Temperature stays reasonable

3. **Monitor System:**
   ```bash
   # Watch for crashes
   adb logcat -s AndroidRuntime:E

   # Watch for OOM (out of memory)
   adb logcat | grep -i "out of memory"
   ```

### Expected Behavior:
- ✅ App handles 20+ consecutive messages
- ✅ Response time stays consistent
- ✅ No thermal throttling (S24U has good cooling)
- ✅ Memory usage plateaus (doesn't grow unbounded)

---

## 📊 Test 5: Dashboard & Visualization

**Goal:** Verify UI works correctly

### Steps:

1. **Open Dashboard**
   - Tap orange FAB (Floating Action Button) in top-right

2. **Check Tool Status Cards**
   - Should see 6 tool cards
   - Each shows: Total executions, Success rate, Active count

3. **Check Charts**
   - Scroll to "Data Insights" section
   - Pattern charts (bar + pie)
   - Feedback charts (line + bar)

### Expected Behavior:
- ✅ Dashboard opens smoothly
- ✅ Data updates every 2 seconds
- ✅ Charts render correctly
- ✅ Material Design 3 dark theme

---

## 🐛 Common Issues & Solutions

### Issue 1: "No ONNX models found"
**Cause:** Download failed or file not moved to app storage
**Solution:**
1. Check if file exists: `adb shell ls -l /data/data/com.ailive/files/models/`
2. Re-download model
3. Check logs for download errors

### Issue 2: "ONNX initialization failed"
**Cause:** Corrupted model file or incompatible ONNX version
**Solution:**
1. Delete model: Settings → Manage Models → Delete
2. Re-download fresh copy
3. Check file size (should be ~348MB)

### Issue 3: Slow Responses (>5s)
**Cause:** CPU-only fallback (no NNAPI) or thermal throttling
**Solution:**
1. Check for NNAPI in logs
2. Let device cool down
3. Close other apps (free RAM)
4. Try 135M model (smaller, faster)

### Issue 4: App Crashes on Message Send
**Cause:** Model not loaded or ONNX Runtime error
**Solution:**
1. Get crash logs: `adb logcat -d -s AndroidRuntime:E | tail -100`
2. Check LLMManager logs
3. Verify model file integrity

---

## 📝 What to Report Back

After testing, please provide:

### 1. Download Test Results:
- ✅ / ❌ Download completed
- Time taken: ___ minutes
- Any errors: ___

### 2. Inference Performance:
- ✅ / ❌ Model loaded successfully
- First response time: ___ seconds
- Average response time: ___ seconds
- ✅ / ❌ NNAPI GPU enabled
- Responses quality: (coherent / gibberish / mixed)

### 3. Stability:
- ✅ / ❌ Passed 20-message stress test
- Any crashes: ___
- Memory leaks: ___

### 4. S24 Ultra Specifics:
- CPU usage: ____%
- Memory usage: ___ MB
- Battery temperature: ___°C
- Battery drain rate: ___% per hour

### 5. Logs (if errors):
```bash
# Copy and paste:
adb logcat -d | grep -E "AILive|LLM|ONNX|NNAPI" > ailive_test_logs.txt
```

---

## 🎯 Success Criteria for Phase 7 Completion

**Must Pass:**
- ✅ Model downloads successfully
- ✅ Model loads without crash
- ✅ LLM generates coherent responses
- ✅ Response time <3 seconds
- ✅ No crashes in stress test

**Nice to Have:**
- ✅ NNAPI GPU acceleration working
- ✅ Response time <2 seconds
- ✅ Battery efficient (<5% per hour)
- ✅ Temperature stays reasonable

**If All Pass → Phase 7 Complete! Ready for Phase 8 (Advanced Intelligence)**

---

## 🚀 Next Steps After Testing

### If Tests Pass:
1. Mark Phase 7 as complete
2. Create SESSION-7-COMPLETE.md
3. Start Phase 8: Advanced Intelligence Features
   - Semantic memory search
   - Pattern predictions
   - Proactive suggestions

### If Tests Fail:
1. Paste error logs (from above commands)
2. Describe what's not working
3. I'll diagnose and fix issues
4. Re-build on GitHub
5. Re-test

---

## 📞 Quick Commands Reference

```bash
# Install APK
adb install -r app-debug.apk

# Watch logs in real-time
adb logcat | grep -E "AILive|LLM|ONNX"

# Get crash logs
adb logcat -d -s AndroidRuntime:E | tail -100

# Check model files
adb shell ls -lh /data/data/com.ailive/files/models/

# Monitor performance
adb shell top -n 1 | grep ailive
adb shell dumpsys meminfo com.ailive

# Clear app data (fresh start)
adb shell pm clear com.ailive

# Uninstall
adb uninstall com.ailive
```

---

## 🎓 Understanding ONNX Runtime + NNAPI

**What's Happening:**
1. **ONNX Runtime** = Microsoft's inference engine
2. **NNAPI** = Android Neural Networks API (hardware acceleration)
3. **S24 Ultra** has Adreno 750 GPU + Snapdragon 8 Gen 3 NPU
4. NNAPI delegates compute to GPU/NPU automatically

**Why This Matters:**
- Without NNAPI: CPU-only, 3-5s response time
- With NNAPI: GPU/NPU, 1.5-2.5s response time
- S24 Ultra is perfect for this (powerful GPU)

**Expected Flow:**
```
User message
  → ONNX Runtime receives input
  → Checks for NNAPI availability
  → If available: Offloads to Adreno 750 GPU
  → GPU processes layers in parallel
  → Returns result in 1.5-2.5s
  → AILive displays response
```

---

**Ready to test! Download the APK from GitHub Actions and let me know how it goes! 🚀**

**GitHub Actions:** https://github.com/Ishabdullah/AILive/actions

---

**Created:** 2025-11-05
**For:** Samsung Galaxy S24 Ultra Testing
**By:** Claude Code (Phase 7.10 - ONNX-only deployment)
