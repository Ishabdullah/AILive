# Known Issues - AILive Phase 7

**Last Updated:** 2025-10-30 22:50 UTC
**Current Build:** https://github.com/Ishabdullah/AILive/actions/runs/18956424882

---

## 🔴 Critical Issues (App Unusable)

### Issue #1: App Crashes After Importing Model
**Status:** UNRESOLVED - Needs immediate attention
**Severity:** Critical - Blocks all LLM functionality

**Description:**
- User successfully imports a model via "Import from Device"
- Import shows success toast message
- User types and sends a message (e.g., "Hello")
- App immediately crashes

**Root Cause:**
LLMManager.kt uses ONNX Runtime which only supports .onnx format models. User has GGUF models which are incompatible with ONNX Runtime. The import succeeds in copying the file, but ONNX Runtime fails when trying to load the GGUF file.

**Affected Code:**
- `app/src/main/java/com/ailive/ai/llm/LLMManager.kt` (line 93)
- `app/src/main/java/com/ailive/ui/ModelSetupDialog.kt` (line 247)

**Reproduction Steps:**
1. Launch AILive app
2. Click "Welcome to AILive!" dialog
3. Choose "Import from Device"
4. Navigate to `/Download/LLM_Models/`
5. Select any .gguf file (e.g., The_Elder-v2.gguf)
6. See "Model imported successfully" toast
7. Type "Hello" in text input
8. Click send button
9. App crashes

**Logs Needed:**
```bash
adb logcat -d -s AndroidRuntime:E | tail -100
adb logcat -d | grep -E "LLMManager|ONNX" | tail -50
```

**Workaround:**
None currently. User must download a valid ONNX model from HuggingFace (but download also fails - see Issue #2).

**Proposed Fix:**
Add format validation before importing:
```kotlin
if (fileName.endsWith(".gguf")) {
    onComplete(false, "GGUF format not supported. Please use ONNX models.")
    return
}
```

See PHASE-7-HANDOFF.md for complete fix instructions.

---

### Issue #2: Model Downloads Fail
**Status:** UNRESOLVED - Needs immediate attention
**Severity:** Critical - Blocks primary model acquisition method

**Description:**
- User selects "Download Model" from welcome dialog
- Chooses either SmolLM2-360M or SmolLM2-135M
- Android shows notification "Download unsuccessful"
- Download never completes

**Root Cause:**
Android 10+ requires runtime permission request for writing to external storage. App has manifest permission but doesn't request it at runtime.

**Affected Code:**
- `app/src/main/java/com/ailive/MainActivity.kt` (onCreate - missing permission request)
- `app/src/main/AndroidManifest.xml` (has manifest permission, needs runtime request)

**Reproduction Steps:**
1. Launch AILive app
2. Click "Welcome to AILive!" dialog
3. Choose "Download Model"
4. Select "SmolLM2-360M (348MB) - Recommended"
5. Wait ~30 seconds
6. See Android notification "Download unsuccessful"
7. Check Downloads folder - file not present

**Additional Info:**
- Download URL is valid and accessible
- File size: 348MB for 360M model, 135MB for 135M model
- Download destination: `/storage/emulated/0/Download/`
- Should then move to: `/data/data/com.ailive/files/models/`

**Workaround:**
User can manually download ONNX model and use "Import from Device" (but this also has issues due to format incompatibility).

**Proposed Fix:**
Add runtime permission request in MainActivity.onCreate():
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

See PHASE-7-HANDOFF.md for complete fix instructions.

---

## 🟡 High Priority Issues (Functionality Limited)

### Issue #3: No Error Feedback on Download Failure
**Status:** UNRESOLVED
**Severity:** High - Poor user experience

**Description:**
When download fails, user only sees generic Android notification "Download unsuccessful" with no explanation of why it failed or how to fix it.

**Affected Code:**
- `app/src/main/java/com/ailive/ai/llm/ModelDownloadManager.kt` (handleDownloadComplete)

**Proposed Fix:**
Add detailed error messages based on DownloadManager failure codes:
- ERROR_INSUFFICIENT_SPACE
- ERROR_DEVICE_NOT_FOUND
- ERROR_HTTP_DATA_ERROR
- etc.

### Issue #4: No Format Validation on Import
**Status:** UNRESOLVED
**Severity:** High - Leads to crashes

**Description:**
App accepts any file format during import, including incompatible GGUF files, which causes crashes later.

**Affected Code:**
- `app/src/main/java/com/ailive/ui/ModelSetupDialog.kt` (importModelFromStorage)

**Proposed Fix:**
Validate file extension before importing and reject unsupported formats with clear error message.

---

## 🟢 Medium Priority Issues (Minor Inconveniences)

### Issue #5: No Download Progress in UI
**Status:** UNRESOLVED
**Severity:** Medium - Poor UX but not blocking

**Description:**
Download progress dialog created but never shown to user. Progress tracking works but isn't visible.

**Affected Code:**
- `app/src/main/java/com/ailive/ui/ModelSetupDialog.kt` (showDownloadProgressDialog)

**Notes:**
Progress is tracked in `getDownloadProgress()` but UI isn't updated properly.

### Issue #6: Can't Pause/Cancel Downloads
**Status:** UNRESOLVED
**Severity:** Medium

**Description:**
Download dialog has Cancel button but doesn't work correctly. No pause functionality.

**Affected Code:**
- `app/src/main/java/com/ailive/ui/ModelSetupDialog.kt` (showDownloadProgressDialog)

### Issue #7: Gradle Deprecated API Warning
**Status:** UNRESOLVED
**Severity:** Low - Cosmetic

**Description:**
Build warning: `'packagingOptions' is deprecated. Renamed to packaging`

**Affected Code:**
- `app/build.gradle.kts` (line 32)

**Fix:**
Rename `packagingOptions` to `packaging`

---

## ✅ Fixed Issues (This Session)

### ~~Issue: Dialog Shows Only Cancel Button~~
**Status:** FIXED in commit 2a997d6
**Severity:** Was Critical

**Description:**
Model selection dialog only showed Cancel button, no list of models.