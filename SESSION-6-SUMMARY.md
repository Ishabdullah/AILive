# Session 6 Summary: Phase 7 Model Download System

**Date:** October 30, 2025
**Focus:** Implementing on-demand model downloads for SmolLM2-360M
**Status:** Partially Complete - Critical bugs fixed, runtime crash needs investigation

---

## What Was Accomplished

### Phase 7.2: Model Download Infrastructure ✅
**Files Created:**
- `app/src/main/java/com/ailive/ai/llm/ModelDownloadManager.kt` (336 lines)

**Features Implemented:**
- Android DownloadManager integration for HuggingFace downloads
- Model file management (check, get path, delete, list)
- Download progress tracking (bytes downloaded / total bytes)
- Model import from device storage via file picker
- Broadcast receiver for download completion notifications

**URLs Configured:**
- SmolLM2-360M (348MB): `https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-ONNX/resolve/main/smollm2-360m-instruct-int8.onnx`
- SmolLM2-135M (135MB): `https://huggingface.co/HuggingFaceTB/SmolLM2-135M-Instruct-ONNX/resolve/main/smollm2-135m-instruct-int8.onnx`

**Key Methods:**
```kotlin
fun downloadModel(url: String, modelName: String, onComplete: (Boolean, String) -> Unit)
fun isModelAvailable(modelName: String = DEFAULT_MODEL_NAME): Boolean
fun getModelPath(modelName: String = DEFAULT_MODEL_NAME): String
fun getDownloadProgress(): Pair<Long, Long>?
suspend fun importModelFromStorage(uri: Uri, onComplete: (Boolean, String) -> Unit)
fun getAvailableModels(): List<File>
fun deleteModel(modelName: String): Boolean
```

### Phase 7.3: User Interface Dialogs ✅
**Files Created:**
- `app/src/main/java/com/ailive/ui/ModelSetupDialog.kt` (384 lines)

**Dialogs Implemented:**
1. **First-Run Welcome Dialog**
   - 3 options: Download Model / Import from Device / Skip for Now
   - Non-cancellable until action taken
   - Inspired by Layla AI UX

2. **Model Selection Dialog**
   - Lists available models with sizes
   - Recommendations (360M recommended, 135M smaller/faster)

3. **Download Progress Dialog**
   - Real-time progress updates (every 1 second)
   - Shows MB downloaded / total MB and percentage
   - Cancellable with cleanup

4. **File Picker Dialog**
   - Opens Android document picker
   - Filters for .gguf and .onnx files
   - Handles import flow

5. **Model Management Dialog**
   - Lists all downloaded models
   - Options: Use / Delete / View Details
   - Download new or import additional models

**Key Features:**
- SharedPreferences to track setup completion
- Lifecycle-aware (proper cleanup in onDestroy)
- Error handling with user-friendly messages
- Retry logic on download failures

### Phase 7.4: MainActivity Integration ✅
**Files Modified:**
- `app/src/main/java/com/ailive/MainActivity.kt`

**Changes:**
- Added ModelSetupDialog and ModelDownloadManager initialization
- Check model availability on app startup
- Show first-run dialog if no model found
- Created `continueInitialization()` method to resume after model setup
- Added cleanup in `onDestroy()`

**Integration Flow:**
```
onCreate()
  → Check isSetupNeeded()
  → If needed: Show dialog, wait for completion
  → If not: Continue with normal initialization
  → After setup: continueInitialization() → startModels()
```

### Phase 7.5: Critical Bugfixes ✅
**Issues Fixed:**

1. **Dialog List Items Not Showing**
   - Problem: Model selection dialog only showed Cancel button
   - Root Cause: Using `.setMessage()` with `.setItems()` prevents list display
   - Fix: Removed `.setMessage()`, kept only `.setTitle()`
   - Result: Both model options now visible

2. **Lifecycle Registration Error & Crash**
   - Problem: "LifecycleOwner attempting to register while current state is RESUMED"
   - Root Cause: Deprecated `startActivityForResult()` API
   - Fix: Refactored to modern `ActivityResultContracts` API
     - Register `ActivityResultLauncher` at beginning of `onCreate()`
     - Pass launcher to `ModelSetupDialog` constructor
     - Use `launcher.launch(intent)` instead of `startActivityForResult()`
   - Result: No more lifecycle errors, file picker works correctly

3. **Compilation Error**
   - Problem: Unresolved reference to `REQUEST_CODE_FILE_PICKER`
   - Root Cause: Old `onActivityResult()` method not removed during refactoring
   - Fix: Removed deprecated method entirely
   - Result: Build compiles successfully

**Files Modified in Phase 7.5:**
- `ModelSetupDialog.kt`: Removed `.setMessage()`, added launcher parameter, updated file picker
- `MainActivity.kt`: Registered launcher, removed deprecated methods

**Commits:**
- `fix(phase7.5): Fix dialog display and lifecycle registration errors` (2a997d6)
- `fix(phase7.5): Remove deprecated onActivityResult method` (f246492)

---

## Current Status

### ✅ Working
- App builds and installs successfully
- Welcome dialog appears on first run
- Model selection dialog shows both options (360M & 135M)
- File picker opens correctly for model import
- No lifecycle registration errors
- Model import reports "success"

### ❌ Not Working
1. **Model Download Fails**
   - Android notification: "Download unsuccessful"
   - Likely causes:
     - Missing runtime storage permissions (Android 10+ requires explicit permission for Downloads folder)
     - Network permission might need runtime request
     - HuggingFace URL might require authentication or redirect handling

2. **App Crashes After Model Import**
   - Import shows success message
   - App crashes when user sends first message ("Hello")
   - Likely causes:
     - GGUF model imported but LLMManager expects ONNX format
     - Model file corrupted during copy
     - ONNX Runtime failing to load the model
     - Missing model metadata or tokenizer files

### 🔍 Needs Investigation
- Crash logs not captured (ADB connection issues)
- Model format compatibility (GGUF vs ONNX)
- LLMManager initialization with imported models
- Tokenizer availability for imported models

---

## Technical Details

### Model Storage Locations
- **App Internal Storage:** `/data/data/com.ailive/files/models/`
- **User Models:** `/storage/emulated/0/Download/LLM_Models/`
- **Download Destination:** `/storage/emulated/0/Download/` (then moved to app storage)

### Permissions in AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
```

**Note:** These are manifest permissions. Android 10+ requires runtime permission requests for storage access.

### LLMManager Expectations
From `LLMManager.kt`:
- Expects `.onnx` model files
- Looks for model at: `modelDownloadManager.getModelPath()`
- Default model name: `"smollm2-360m-int8.onnx"`
- Uses ONNX Runtime with NNAPI GPU acceleration

**Critical Issue:** LLMManager only supports ONNX format, but user has GGUF models:
- The_Elder-v2.gguf (1.1GB)
- CodeLlama-7B-Instruct.Q4_K_M.gguf (3.9GB)
- DeepSeek-Coder-V2-Lite-Instruct-Q4_K_M.gguf (9.7GB)
- Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf (4.6GB)
- Phi-3-mini-4k-instruct-q4.gguf (2.3GB)
- Qwen2-1.5B-Instruct.Q4_K_M.gguf (941MB)

**This is likely causing the crash!** The import succeeds in copying the file, but LLMManager can't load GGUF format with ONNX Runtime.

---

## Files Modified This Session

### New Files Created (2)
1. `app/src/main/java/com/ailive/ai/llm/ModelDownloadManager.kt`
2. `app/src/main/java/com/ailive/ui/ModelSetupDialog.kt`

### Existing Files Modified (2)
1. `app/src/main/java/com/ailive/ai/llm/LLMManager.kt`
   - Added ModelDownloadManager integration
   - Removed asset-based model loading
   - Added public methods: `isModelAvailable()`, `getDownloadManager()`

2. `app/src/main/java/com/ailive/MainActivity.kt`
   - Added model setup dialog integration
   - Added ActivityResultLauncher for file picker
   - Added continueInitialization() method
   - Removed deprecated onActivityResult()

### Permissions Added
- `AndroidManifest.xml`: Added INTERNET and storage permissions

---

## Git History (Phase 7)

```
f246492 fix(phase7.5): Remove deprecated onActivityResult method
2a997d6 fix(phase7.5): Fix dialog display and lifecycle registration errors
44a3571 feat(phase7.4): Integrate model setup dialog into MainActivity
206e368 feat(phase7.3): Add model setup dialog UI
678a070 feat(phase7.2): Add model download infrastructure
584d2fb fix(phase7): Configure Gradle to include large ONNX model in APK (ABANDONED)
9fbb8f2 fix(phase7): Copy model from assets to filesDir on first run (ABANDONED)
e8e9a4d feat(phase7): Integrate SmolLM2-360M model for on-device inference
```

**Latest Build:** https://github.com/Ishabdullah/AILive/actions/runs/18956424882

---

## Next Steps (Priority Order)

### 🔴 Critical - Must Fix
1. **Investigate Crash After Model Import**
   - Get crash logs via ADB: `adb logcat -d -s AndroidRuntime:E`
   - Check LLMManager logs: `adb logcat -d | grep LLMManager`
   - Verify model file format (GGUF vs ONNX)
   - **Likely Solution:** Either:
     - Convert GGUF models to ONNX format, OR
     - Replace ONNX Runtime with llama.cpp for GGUF support

2. **Fix Model Download Failure**
   - Add runtime permission request for storage (Android 10+)
   - Test HuggingFace URL accessibility
   - Add better error handling in DownloadManager callback
   - Log download failure reason codes

### 🟡 High Priority
3. **Add Model Format Detection**
   - Detect if imported file is GGUF or ONNX
   - Show error message if unsupported format
   - Guide user to download ONNX models instead

4. **Improve Error Messages**
   - Show specific error when LLM initialization fails
   - Add "Model not compatible" dialog
   - Provide link to download correct format

5. **Add Storage Permission Request**
   - Request WRITE_EXTERNAL_STORAGE at runtime
   - Show rationale dialog explaining why needed
   - Handle permission denial gracefully

### 🟢 Medium Priority
6. **Model Format Support**
   - **Option A:** Keep ONNX, guide users to ONNX models only
   - **Option B:** Add llama.cpp library for GGUF support (large refactor)
   - **Option C:** Support both formats with runtime detection

7. **Download Progress Improvements**
   - Show download in notification with progress
   - Add pause/resume functionality
   - Retry failed downloads automatically

8. **Testing**
   - Test with actual ONNX model from HuggingFace
   - Test import with ONNX file
   - Test download on different Android versions
   - Test with poor network conditions

---

## Code Snippets for Next Developer

### How to Get Crash Logs
```bash
# Get most recent crash
adb logcat -d -s AndroidRuntime:E | tail -100

# Get LLMManager logs
adb logcat -d | grep -E "LLMManager|ONNX|Model" | tail -50

# Get real-time logs
adb logcat | grep -E "AILive|LLMManager"
```

### Where to Add Runtime Permission Request
In `MainActivity.onCreate()`, after model setup:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            REQUEST_CODE_STORAGE
        )
    }
}
```

### How to Detect Model Format
```kotlin
fun detectModelFormat(file: File): String {
    return when {
        file.name.endsWith(".onnx") -> "ONNX"
        file.name.endsWith(".gguf") -> "GGUF"
        else -> "UNKNOWN"
    }
}
```

### Where LLM Initialization Happens
- `MainActivity.startModels()` (line ~300)
- Calls `aiLiveCore.initialize()` which initializes `LLMManager`
- `LLMManager.initialize()` loads the model with ONNX Runtime

---

## User Feedback Received

1. "When i pushed download now there was no options to download the recommended model just had a cancel button" ✅ FIXED
2. "Error: LifecycleOwner attempting to register while current state is RESUMED" ✅ FIXED
3. "download still unsuccessful" ❌ NEEDS FIX
4. "import said it was successful but then crashed the app when i sent a message" ❌ CRITICAL - NEEDS FIX

---

## Key Learnings

1. **Android Dialog Best Practices:** Don't use `.setMessage()` with `.setItems()` - items won't display
2. **Lifecycle Registration:** ActivityResultLauncher MUST be registered before setContentView() or STARTED state
3. **Deprecated APIs:** Avoid startActivityForResult(), use ActivityResultContracts instead
4. **Model Format Matters:** ONNX Runtime can't load GGUF files - format compatibility is critical
5. **Storage Permissions:** Android 10+ requires runtime permission requests, not just manifest declarations

---

## Project Statistics

- **Total Lines Added (Phase 7.2-7.5):** ~850
- **Files Created:** 2
- **Files Modified:** 4
- **Commits:** 7
- **Build Success Rate:** 5/6 (83%)
- **Time Spent:** ~3 hours
- **Current Progress:** Phase 7 - 75% complete

---

## Resources for Next Developer

### Documentation
- Android DownloadManager: https://developer.android.com/reference/android/app/DownloadManager
- ActivityResultContracts: https://developer.android.com/training/basics/intents/result
- ONNX Runtime Android: https://onnxruntime.ai/docs/tutorials/mobile/
- Storage Permissions: https://developer.android.com/training/data-storage/shared/documents-files

### SmolLM2 Models
- HuggingFace Repo: https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-ONNX
- Model Card: https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct
- GGUF Alternative: https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF

### Helpful Commands
```bash
# Build APK locally
./gradlew assembleDebug

# Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# View logs
adb logcat | grep AILive

# Check downloaded builds
gh run list --limit 5
```

---

**Created by:** Claude (Anthropic)
**Last Updated:** 2025-10-30 22:45 UTC
**Next Session Should Start With:** PHASE-7-HANDOFF.md
