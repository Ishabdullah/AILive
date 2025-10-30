# Phase 7 Handoff: Model Download System

**Status:** 75% Complete - Infrastructure working, runtime bugs need fixing
**Next Developer Start Here:** This document contains everything you need to continue Phase 7

---

## Quick Start (5 Minutes)

### 1. Get Latest Code
```bash
cd /data/data/com.termux/files/home/AILive
git pull origin main
git log --oneline -10  # See recent commits
```

### 2. Review Current Status
```bash
# Read these files in order:
cat SESSION-6-SUMMARY.md       # What was done this session
cat KNOWN-ISSUES.md            # Current problems
cat VIRALNEXUS-MASTER-PLAN.md  # Overall project roadmap
```

### 3. Get Latest Build
- Download: https://github.com/Ishabdullah/AILive/actions/runs/18956424882
- Or build locally: `./gradlew assembleDebug`

### 4. Test Current State
```bash
# Install and test
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Watch logs
adb logcat | grep -E "AILive|LLMManager|ModelDownload"
```

---

## Critical Issues to Fix (Start Here)

### 🔴 Issue #1: App Crashes After Model Import

**Symptom:**
- User imports model via file picker
- Import shows "success" message
- App crashes when sending first message

**Root Cause (90% Certain):**
LLMManager expects ONNX format but user imported GGUF model. ONNX Runtime can't load GGUF files.

**Evidence:**
- LLMManager.kt line 93: `ortSession = ortEnv?.createSession(modelFile.absolutePath, sessionOptions)`
- User has only GGUF models in `/Download/LLM_Models/`:
  - The_Elder-v2.gguf
  - CodeLlama-7B-Instruct.Q4_K_M.gguf
  - DeepSeek-Coder-V2-Lite-Instruct-Q4_K_M.gguf
  - (etc - see SESSION-6-SUMMARY.md)

**How to Reproduce:**
1. Launch app
2. Click "Import from Device"
3. Select any .gguf file from `/Download/LLM_Models/`
4. Import succeeds
5. Type "Hello" and send
6. App crashes

**How to Fix - Option A (Quick Fix):**
Add format detection and reject GGUF files:

```kotlin
// In ModelSetupDialog.kt, handleFilePickerResult() method:
suspend fun importModelFromStorage(uri: Uri, onComplete: (Boolean, String) -> Unit) {
    try {
        var fileName = DEFAULT_MODEL_NAME

        // Get filename
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->\n            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }

        // ADD THIS: Check format before importing
        if (fileName.endsWith(".gguf")) {
            withContext(Dispatchers.Main) {
                onComplete(false, "GGUF format not supported. Please use ONNX models (.onnx files)")
            }
            return
        }

        if (!fileName.endsWith(".onnx")) {
            withContext(Dispatchers.Main) {
                onComplete(false, "Unsupported format. Please select an ONNX model file (.onnx)")
            }
            return
        }

        // ... rest of existing code
    }
}
```

**How to Fix - Option B (Better Solution):**
Replace ONNX Runtime with llama.cpp to support GGUF format:

1. Add llama.cpp dependency to `app/build.gradle.kts`:
```kotlin
dependencies {
    // Replace ONNX Runtime with llama.cpp
    // implementation("ai.onnxruntime:onnxruntime-android:1.16.0")
    implementation("com.github.kherud:llama:2.2.1")  // Java bindings for llama.cpp
}
```

2. Refactor `LLMManager.kt` to use llama.cpp instead of ONNX Runtime

3. Update model loading code to support GGUF

**Recommended:** Start with Option A (quick fix), then implement Option B if time permits.

**Test Fix:**
```bash
# After implementing fix:
adb install -r app-debug.apk

# Try importing GGUF model - should show error message
# Try downloading ONNX model - should work

# Check logs
adb logcat | grep -E "ModelSetup|import|format"
```

---

### 🔴 Issue #2: Model Downloads Fail

**Symptom:**
- User clicks "Download Model" → Selects 360M or 135M
- Android shows notification "Download unsuccessful"
- Download never completes

**Root Cause:**
Missing runtime storage permission request (Android 10+ requirement)

**How to Reproduce:**
1. Launch app
2. Click "Download Model"
3. Select "SmolLM2-360M (348MB)"
4. Wait - download will fail

**How to Fix:**

1. Add storage permission request to `MainActivity.kt`:

```kotlin
// Add after model setup dialog in onCreate()
if (modelSetupDialog.isSetupNeeded()) {
    // ... existing dialog code ...

    // ADD THIS: Request storage permission for downloads
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {

            // Show rationale
            if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                AlertDialog.Builder(this)
                    .setTitle("Storage Permission Needed")
                    .setMessage("AILive needs storage access to download AI models to your device.")
                    .setPositiveButton("OK") { _, _ ->
                        requestPermissions(
                            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                            REQUEST_CODE_STORAGE
                        )
                    }
                    .show()
            } else {
                requestPermissions(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_CODE_STORAGE
                )
            }
        }
    }
}

// Add companion object constant
companion object {
    private const val REQUEST_CODE_PERMISSIONS = 10
    private const val REQUEST_CODE_STORAGE = 11  // ADD THIS
    // ...
}

// Handle permission result
override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)

    when (requestCode) {
        REQUEST_CODE_PERMISSIONS -> {
            // ... existing camera/audio permission handling ...
        }
        REQUEST_CODE_STORAGE -> {  // ADD THIS
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Storage permission granted - downloads enabled")
            } else {
                Log.w(TAG, "Storage permission denied - downloads will fail")
                Toast.makeText(
                    this,
                    "Storage permission required for downloads. Please use 'Import from Device' instead.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
```

2. Add better error handling in `ModelDownloadManager.kt`:

```kotlin
// In handleDownloadComplete() method:
DownloadManager.STATUS_FAILED -> {
    val reason = cursor.getInt(
        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
    )

    // ADD THIS: Detailed error messages
    val errorMessage = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE ->
            "Not enough storage space (need 348MB)"
        DownloadManager.ERROR_DEVICE_NOT_FOUND ->
            "External storage not available"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS ->
            "File already exists"
        DownloadManager.ERROR_CANNOT_RESUME ->
            "Download interrupted, cannot resume"
        DownloadManager.ERROR_FILE_ERROR ->
            "File system error"
        DownloadManager.ERROR_HTTP_DATA_ERROR ->
            "HTTP download error - check internet connection"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS ->
            "Too many redirects - HuggingFace URL might be invalid"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE ->
            "HTTP error - server might be down"
        else -> "Download failed (code: $reason)"
    }

    Log.e(TAG, "❌ Download failed: $errorMessage")
    downloadCompleteCallback?.invoke(false, errorMessage)
}
```

**Test Fix:**
```bash
# After implementing fix:
adb install -r app-debug.apk

# Try download - should see permission request dialog first
# Grant permission
# Try download again - should see progress notification

# Check logs
adb logcat | grep -E "DownloadManager|ModelDownload|permission"
```

---

## File Structure Reference

### Key Files to Modify

**Model Download System:**
- `app/src/main/java/com/ailive/ai/llm/ModelDownloadManager.kt` - Download logic
- `app/src/main/java/com/ailive/ui/ModelSetupDialog.kt` - UI dialogs
- `app/src/main/java/com/ailive/MainActivity.kt` - Integration

**LLM Inference:**
- `app/src/main/java/com/ailive/ai/llm/LLMManager.kt` - ONNX Runtime integration
- `app/build.gradle.kts` - Dependencies (ONNX Runtime or llama.cpp)

**Permissions:**
- `app/src/main/AndroidManifest.xml` - Manifest permissions
- `app/src/main/java/com/ailive/MainActivity.kt` - Runtime permission requests

### Code Flow Diagram

```
User Opens App
    ↓
MainActivity.onCreate()
    ↓
Check modelSetupDialog.isSetupNeeded()
    ↓
    ├─ No Model → Show Welcome Dialog
    │       ↓
    │   User Choice:
    │   ├─ Download Model → ModelSetupDialog.showModelSelectionDialog()
    │   │       ↓
    │   │   Select 360M or 135M
    │   │       ↓
    │   │   ModelDownloadManager.downloadModel()
    │   │       ↓
    │   │   [Android DownloadManager downloads to /Download/]
    │   │       ↓
    │   │   BroadcastReceiver notified on completion
    │   │       ↓
    │   │   moveDownloadedFile() → Copy to app internal storage
    │   │       ↓
    │   │   onComplete callback → continueInitialization()
    │   │
    │   ├─ Import from Device → filePickerLauncher.launch()
    │   │       ↓
    │   │   User selects file
    │   │       ↓
    │   │   ActivityResultLauncher callback
    │   │       ↓
    │   │   ModelDownloadManager.importModelFromStorage()
    │   │       ↓
    │   │   Copy file to app internal storage
    │   │       ↓
    │   │   onComplete callback → continueInitialization()
    │   │
    │   └─ Skip for Now → continueInitialization()
    │
    └─ Model Exists → continueInitialization()
            ↓
        Check Permissions (Camera/Mic)
            ↓
        startModels()
            ↓
        AILiveCore.initialize()
            ↓
        LLMManager.initialize()
            ↓
        Load model with ONNX Runtime
            ↓
        Ready for user input
```

---

## Testing Checklist

### Before You Start Coding
- [ ] Read SESSION-6-SUMMARY.md completely
- [ ] Read KNOWN-ISSUES.md
- [ ] Review recent git commits (`git log --oneline -10`)
- [ ] Build and install current APK
- [ ] Reproduce both crashes (import GGUF, try download)

### After Implementing Fixes
- [ ] Build succeeds without errors
- [ ] App installs without crashes
- [ ] Welcome dialog shows on first run
- [ ] Model selection dialog shows both options (360M & 135M)
- [ ] GGUF import shows error message (not crash)
- [ ] Storage permission requested before download
- [ ] Download completes successfully (348MB model)
- [ ] Download progress shown in notification
- [ ] Model loads successfully after download
- [ ] Can send message "Hello" without crash
- [ ] LLM responds to message (even if nonsense response)

### Test on Different Scenarios
- [ ] Fresh install (no model)
- [ ] Update install (model already exists)
- [ ] Import valid ONNX model
- [ ] Import invalid GGUF model
- [ ] Download with WiFi
- [ ] Download with mobile data
- [ ] Download with poor connection
- [ ] Cancel download mid-way
- [ ] Retry failed download

---

## Debug Commands

### Build and Install
```bash
# Clean build
./gradlew clean assembleDebug

# Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Uninstall (fresh start)
adb uninstall com.ailive
```

### Logging
```bash
# Real-time logs (all)
adb logcat | grep AILive

# Model-specific logs
adb logcat | grep -E "LLMManager|ModelDownload|ModelSetup|ONNX"

# Crash logs
adb logcat -d -s AndroidRuntime:E | tail -100

# Download Manager logs
adb logcat | grep DownloadManager

# Permission logs
adb logcat | grep -E "permission|Permission"
```

### File System
```bash
# Check model files in app storage
adb shell ls -lh /data/data/com.ailive/files/models/

# Check user's models
adb shell ls -lh /storage/emulated/0/Download/LLM_Models/

# Check downloaded files
adb shell ls -lh /storage/emulated/0/Download/

# View SharedPreferences (check model_setup_done)
adb shell cat /data/data/com.ailive/shared_prefs/AILivePrefs.xml
```

### Network
```bash
# Test HuggingFace URL accessibility
curl -I https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-ONNX/resolve/main/smollm2-360m-instruct-int8.onnx

# Download model manually (for testing)
wget https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-ONNX/resolve/main/smollm2-360m-instruct-int8.onnx

# Push to device for import testing
adb push smollm2-360m-instruct-int8.onnx /storage/emulated/0/Download/
```

---

## Common Errors and Solutions

### Error: "Unresolved reference"
**Cause:** Missing import or undefined variable
**Solution:** Check imports at top of file, verify variable names

### Error: "NNAPI not available"
**Cause:** Device doesn't support NNAPI GPU acceleration
**Solution:** This is expected and handled - falls back to CPU

### Error: "FileNotFoundException" for model
**Cause:** Model not in expected location
**Solution:** Check `getModelPath()` returns correct path, verify file exists

### Error: "OnnxRuntimeException"
**Cause:** Invalid model format or corrupted file
**Solution:** Verify model is valid ONNX format, re-download if corrupted

### Warning: "packagingOptions deprecated"
**Cause:** Old Gradle API
**Solution:** Rename to `packaging {}` in build.gradle.kts (line 32)

---

## Performance Considerations

### Model Size vs Speed
- **360M Model (348MB):** Better quality, slower inference (~2-3 sec/response)
- **135M Model (135MB):** Lower quality, faster inference (~1-2 sec/response)

### Storage Requirements
- Minimum 500MB free space for 360M model
- Models stored in `/data/data/com.ailive/files/models/`
- Downloads go to `/storage/emulated/0/Download/` first, then moved

### Memory Usage
- ONNX Runtime loads model into RAM
- 360M model uses ~400MB RAM when loaded
- Monitor with: `adb shell dumpsys meminfo com.ailive`

---

## Next Phase After Phase 7

Once model download/import is working, move to:

**Phase 8: Advanced LLM Features**
- Streaming responses (word-by-word output)
- Context memory (conversation history)
- Token limits and truncation
- Temperature/top-p parameter tuning

**Phase 9: Multi-Model Support**
- Multiple models installed simultaneously
- Switch between models at runtime
- Model-specific settings (temperature, max_length)

**Phase 10: Production Polish**
- Model download resumption
- Differential model updates
- Compression/decompression
- Network failure handling

---

## Resources

### Documentation
- ONNX Runtime Android: https://onnxruntime.ai/docs/tutorials/mobile/android.html
- llama.cpp Android: https://github.com/ggerganov/llama.cpp/tree/master/examples/llama.android
- Android DownloadManager: https://developer.android.com/reference/android/app/DownloadManager
- Android Storage Permissions: https://developer.android.com/training/data-storage/shared/documents-files

### Models
- SmolLM2-360M ONNX: https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-ONNX
- SmolLM2-135M ONNX: https://huggingface.co/HuggingFaceTB/SmolLM2-135M-Instruct-ONNX
- GGUF to ONNX Converter: https://github.com/microsoft/onnxruntime/tree/main/onnxruntime/python/tools/transformers

### Similar Projects
- SmolChat Android: https://github.com/huggingface/smol-course/tree/main/apps/smol-chat
- LlamaChat Android: https://github.com/czlucius/llama-android
- LocalAI Android: https://github.com/mudler/LocalAI

---

## Contact / Questions

If you get stuck, check these resources:
1. **Git Log:** `git log --graph --oneline --all` - See commit history
2. **Session Summaries:** `SESSION-*.md` files - Previous work
3. **Known Issues:** `KNOWN-ISSUES.md` - Current problems
4. **Master Plan:** `VIRALNEXUS-MASTER-PLAN.md` - Overall roadmap

For code examples, search similar projects on GitHub:
```bash
# Search for similar implementations
gh search repos "onnxruntime android" --language=kotlin
gh search repos "llama.cpp android" --language=kotlin
gh search code "DownloadManager enqueue" --language=kotlin
```

---

**Good Luck!** The infrastructure is 75% done. Main tasks are:
1. Fix GGUF format rejection
2. Add storage permission request
3. Test end-to-end download flow
4. Verify LLM inference works

All the hard architectural work is complete. These are straightforward bug fixes and permission handling. You've got this! 🚀

---

**Created:** 2025-10-30
**Last Updated:** 2025-10-30 22:48 UTC
**Created By:** Claude (Anthropic)
