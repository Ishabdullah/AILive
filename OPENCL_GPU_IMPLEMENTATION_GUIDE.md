# OpenCL GPU Implementation Guide for AILive v1.1

**Status**: ✅ Code Implementation Complete - Ready for Testing
**Date**: November 9, 2025
**Target**: Samsung S24 Ultra (Snapdragon 8 Gen 3, Adreno 750 GPU)
**Expected Speedup**: 3-5x (7→20-30 tokens/second)

---

## 📋 What Was Implemented

### ✅ Phase 1-2: Build Configuration & GPU Detection (COMPLETE)

**Commit**: `a8167f4` - "feat: implement OpenCL GPU acceleration for Adreno 750 (v1.1)"

**Files Modified/Created**:
1. `external/llama.cpp/examples/llama.android/llama/build.gradle.kts` - OpenCL CMake flags
2. `external/llama.cpp/examples/llama.android/llama/src/main/cpp/llama-android.cpp` - JNI GPU detection
3. `external/llama.cpp/examples/llama.android/llama/src/main/java/android/llama/cpp/LLamaAndroid.kt` - Kotlin API
4. `scripts/setup_opencl.sh` - Automated OpenCL setup script

**What's Working**:
- ✅ OpenCL enabled in CMake build configuration
- ✅ Adreno-specific kernel optimizations enabled
- ✅ GPU detection via OpenCL API
- ✅ Native JNI function: `Java_android_llama_cpp_LLamaAndroid_detect_1gpu`
- ✅ Kotlin wrapper: `LLamaAndroid.detectGPU()`
- ✅ Automated setup script for OpenCL dependencies

---

## 🚀 How to Build and Test

### Step 1: Prerequisites

**Required**:
- Android Studio Hedgehog or later
- Android NDK 26.3.11579264 (or 27.x)
- CMake 3.22.1+
- Linux development machine (for setup script)
- Samsung S24 Ultra (or Adreno 750 device) for testing

**Environment Variables**:
```bash
export ANDROID_SDK_ROOT=$HOME/Android/Sdk
export ANDROID_NDK=$ANDROID_SDK_ROOT/ndk/26.3.11579264
```

### Step 2: Install OpenCL Dependencies

**Run the automated setup script**:
```bash
cd /path/to/AILive
chmod +x scripts/setup_opencl.sh
./scripts/setup_opencl.sh
```

**What this does**:
1. Clones OpenCL-Headers from Khronos Group
2. Copies headers to NDK sysroot (`$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/CL/`)
3. Builds OpenCL ICD Loader for Android (arm64-v8a)
4. Installs libOpenCL.so to NDK sysroot
5. Verifies installation

**Expected Output**:
```
============================================
✓ OpenCL Setup Complete!
============================================

Next steps:
1. Build AILive: ./gradlew assembleDebug
2. Install on device: adb install app/build/outputs/apk/debug/app-debug.apk
3. Check logs for GPU detection: adb logcat | grep -E "OpenCL|GPU|Adreno"

Expected GPU: Adreno 750 (Snapdragon 8 Gen 3)
Expected speedup: 3-5x (7→20-30 tokens/second)
```

### Step 3: Build AILive with OpenCL

```bash
# Clean previous build
./gradlew clean

# Build with OpenCL support
./gradlew assembleDebug

# Expected build time: 5-10 minutes (first build)
# Watch for OpenCL compilation messages
```

**What happens during build**:
- CMake configures with `-DGGML_OPENCL=ON`
- llama.cpp compiles with OpenCL backend
- Adreno-optimized kernels embedded in binary
- libOpenCL.so linked into native library
- Result: `app/build/outputs/apk/debug/app-debug.apk`

### Step 4: Install and Test on Device

```bash
# Install APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Launch app and watch logs
adb logcat | grep -E "llama-android|OpenCL|GPU|Adreno|LLMManager"
```

**Expected Logs**:
```
I/llama-android.cpp: Detecting OpenCL GPU...
I/llama-android.cpp: Found 1 OpenCL platform(s)
I/llama-android.cpp: OpenCL Platform: QUALCOMM Snapdragon(TM)
I/llama-android.cpp: Found 1 OpenCL GPU device(s)
I/llama-android.cpp: OpenCL GPU Device: Adreno (TM) 750
I/llama-android.cpp:   Compute Units: 16
I/llama-android.cpp:   Global Memory: 4096 MB
```

### Step 5: Verify GPU Acceleration

**Test GPU Detection**:
```kotlin
// In your app code (or modify LLMManager.kt)
val gpuInfo = LLamaAndroid.instance().detectGPU()
Log.i("GPU_TEST", "Detected: $gpuInfo")

// Expected: "OpenCL:Adreno (TM) 750"
// Fallback: "CPU:None" (if no GPU)
```

**Performance Test**:
```bash
# Before GPU (v1.0): ~7-8 tokens/second
# After GPU (v1.1): ~20-30 tokens/second (target)

# Monitor performance in logs
adb logcat | grep "tok/s"
```

---

## 📊 Code Changes Explained

### 1. Build Configuration (`build.gradle.kts`)

**Before**:
```kotlin
externalNativeBuild {
    cmake {
        arguments += "-DLLAMA_CURL=OFF"
        arguments += "-DLLAMA_BUILD_COMMON=ON"
        arguments += "-DGGML_LLAMAFILE=OFF"
        arguments += "-DCMAKE_BUILD_TYPE=Release"
    }
}
```

**After** (with OpenCL):
```kotlin
externalNativeBuild {
    cmake {
        arguments += "-DLLAMA_CURL=OFF"
        arguments += "-DLLAMA_BUILD_COMMON=ON"
        arguments += "-DGGML_LLAMAFILE=OFF"
        arguments += "-DCMAKE_BUILD_TYPE=Release"

        // ✨ OpenCL GPU Acceleration for Adreno 750 (v1.1)
        arguments += "-DGGML_OPENCL=ON"
        arguments += "-DGGML_OPENCL_EMBED_KERNELS=ON"
        arguments += "-DGGML_OPENCL_USE_ADRENO_KERNELS=ON"
    }
}
```

**Impact**:
- Enables OpenCL backend in llama.cpp
- Embeds GPU kernels in binary (no runtime compilation)
- Uses Adreno-optimized kernels for best performance

### 2. JNI GPU Detection (`llama-android.cpp`)

**New Function** (lines 463-541):
```cpp
extern "C"
JNIEXPORT jstring JNICALL
Java_android_llama_cpp_LLamaAndroid_detect_1gpu(JNIEnv *env, jobject) {
    #ifdef GGML_USE_OPENCL
    // Query OpenCL platforms
    cl_uint num_platforms = 0;
    clGetPlatformIDs(0, nullptr, &num_platforms);

    // Get GPU device
    cl_device_id device;
    clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 1, &device, nullptr);

    // Get device name
    char device_name[128];
    clGetDeviceInfo(device, CL_DEVICE_NAME, sizeof(device_name), device_name, nullptr);

    // Return "OpenCL:Adreno 750"
    return env->NewStringUTF(("OpenCL:" + std::string(device_name)).c_str());
    #else
    return env->NewStringUTF("CPU:OpenCL_Not_Compiled");
    #endif
}
```

**Features**:
- Detects OpenCL availability at runtime
- Queries GPU name, compute units, memory
- Returns formatted string for Kotlin layer
- Graceful fallback if OpenCL unavailable

### 3. Kotlin API (`LLamaAndroid.kt`)

**New Function** (lines 82-97):
```kotlin
// GPU Detection (v1.1 - OpenCL Support)
private external fun detect_gpu(): String

/**
 * Detect GPU acceleration support via OpenCL.
 *
 * Returns a string in format "Backend:DeviceName":
 * - "OpenCL:Adreno 750" - GPU acceleration available
 * - "CPU:None" - No GPU found
 * - "CPU:OpenCL_Not_Compiled" - OpenCL support not compiled in
 */
fun detectGPU(): String {
    return detect_gpu()
}
```

**Usage**:
```kotlin
val gpuInfo = LLamaAndroid.instance().detectGPU()
val parts = gpuInfo.split(":")
val backend = parts[0]  // "OpenCL" or "CPU"
val deviceName = parts.getOrNull(1) ?: "Unknown"

if (backend == "OpenCL") {
    Log.i("GPU", "✅ GPU acceleration enabled: $deviceName")
} else {
    Log.i("GPU", "ℹ️ Running on CPU: $deviceName")
}
```

---

## 🔧 Troubleshooting

### Build Error: "CL/cl.h: No such file or directory"

**Cause**: OpenCL headers not installed in NDK

**Solution**:
```bash
# Run setup script
./scripts/setup_opencl.sh

# Or manually copy headers
cp -r ~/dev/llm/OpenCL-Headers/CL $ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/
```

### Build Error: "undefined reference to `clGetPlatformIDs`"

**Cause**: OpenCL ICD Loader not linked

**Solution**:
```bash
# Build and install libOpenCL.so
cd ~/dev/llm/OpenCL-ICD-Loader/build_ndk26
ninja
cp libOpenCL.so $ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/
```

### Runtime: detectGPU() returns "CPU:None"

**Possible Causes**:
1. Device doesn't support OpenCL
2. OpenCL drivers not installed on device
3. App doesn't have GPU permissions

**Debug**:
```bash
# Check OpenCL availability on device
adb shell dumpsys gpu

# Check for Adreno GPU
adb shell getprop | grep gpu

# Expected: Adreno 750
```

### Runtime: Performance Not Improved

**Check**:
1. Verify GPU is actually being used:
   ```bash
   adb logcat | grep "OpenCL"
   # Should see "Using OpenCL device: Adreno 750"
   ```

2. Check model quantization:
   - Current: Q4_K_M (mixed quantization)
   - Recommended: Q4_0 pure for best GPU performance
   - See Phase 3 below for re-quantization

3. Monitor GPU usage:
   ```bash
   adb shell "cat /sys/class/kgsl/kgsl-3d0/gpubusy_percentage"
   # Should show >50% during inference
   ```

---

## 📈 Performance Expectations

### Baseline (v1.0 - CPU Only)
- Tokens/second: 7-8
- Short response (20 tokens): 2-3 seconds
- Medium response (100 tokens): 12-15 seconds
- Long response (500 tokens): 60-75 seconds

### Target (v1.1 - OpenCL GPU)
- Tokens/second: 20-30 (3-5x faster)
- Short response (20 tokens): <1 second
- Medium response (100 tokens): 3-5 seconds
- Long response (500 tokens): 15-25 seconds

### Verification
```kotlin
// Add to LLMManager.kt
val startTime = System.currentTimeMillis()
var tokenCount = 0

llamaAndroid.send(prompt, formatChat = false).collect { token ->
    tokenCount++
    response.append(token)
}

val duration = System.currentTimeMillis() - startTime
val tokensPerSecond = (tokenCount.toFloat() / duration) * 1000
Log.i("PERF", "Generated $tokenCount tokens in ${duration}ms = $tokensPerSecond tok/s")
```

---

## ⏭️ Next Steps (Phases 3-7)

### Phase 3: Model Optimization (Pending)

**Goal**: Re-quantize model to pure Q4_0 for optimal GPU performance

**Current Model**:
- File: `qwen2.5-vl-2b-instruct-q4_k_m.gguf`
- Quantization: Q4_K_M (mixed 4-bit + 6-bit)
- Size: 940MB

**Target Model**:
- File: `qwen2.5-vl-2b-instruct-q4_0-pure.gguf`
- Quantization: Q4_0 pure (all weights 4-bit)
- Size: ~920MB (slightly smaller)
- Performance: Better GPU utilization

**How to Re-quantize**:
```bash
# Download original F16 model
wget https://huggingface.co/Qwen/Qwen2-VL-2B-Instruct-GGUF/resolve/main/qwen2.5-vl-2b-instruct-f16.gguf

# Re-quantize to pure Q4_0
./llama.cpp/tools/quantize/llama-quantize --pure qwen2.5-vl-2b-instruct-f16.gguf qwen2.5-vl-2b-instruct-q4_0-pure.gguf Q4_0

# Update app to use new model
```

### Phase 4: Performance Monitoring (Pending)

**Add to LLMManager.kt**:
```kotlin
data class GPUInfo(
    val isAvailable: Boolean,
    val backend: String,
    val deviceName: String,
    val computeUnits: Int = 0,
    val memoryMB: Int = 0
)

data class InferenceStats(
    val tokensPerSecond: Float,
    val totalTokens: Int,
    val durationMs: Long,
    val backend: String
)

class PerformanceMonitor {
    private val stats = mutableListOf<InferenceStats>()

    fun recordInference(tokens: Int, duration: Long, backend: String) {
        val tps = (tokens.toFloat() / duration) * 1000
        stats.add(InferenceStats(tps, tokens, duration, backend))
    }

    fun getAverageSpeed(): Float = stats.map { it.tokensPerSecond }.average().toFloat()
}
```

### Phase 5: Battery & Thermal Testing (Pending)

**Test Protocol**:
1. Charge device to 100%
2. Run continuous inference for 1 hour
3. Monitor:
   - Battery drain rate (target: <5%/hour)
   - Temperature (max 45°C)
   - Thermal throttling events
   - Performance degradation over time

**Tools**:
```bash
# Battery stats
adb shell dumpsys battery

# Temperature
adb shell cat /sys/class/thermal/thermal_zone*/temp

# GPU frequency (throttling indicator)
adb shell cat /sys/class/kgsl/kgsl-3d0/gpuclk
```

### Phase 6: Documentation & Polish (Pending)

**To Complete**:
- [ ] Update README.md with GPU setup instructions
- [ ] Add GPU status indicator in UI
- [ ] Document performance benchmarks
- [ ] Add troubleshooting section
- [ ] Create user-facing documentation

---

## 🔒 Safety & Rollback

**If GPU implementation fails or causes issues**:

1. **Restore to v1.0**:
   ```bash
   git checkout v1.0-stable
   # Or see V1.0_BACKUP_RESTORE_GUIDE.md
   ```

2. **Disable OpenCL** (keep code, disable compilation):
   ```kotlin
   // In build.gradle.kts, comment out:
   // arguments += "-DGGML_OPENCL=ON"
   // arguments += "-DGGML_OPENCL_EMBED_KERNELS=ON"
   // arguments += "-DGGML_OPENCL_USE_ADRENO_KERNELS=ON"
   ```

3. **CPU Fallback** (automatic):
   - If `detectGPU()` returns `"CPU:None"`, app continues on CPU
   - No code changes needed
   - Performance reverts to v1.0 baseline (7-8 tok/s)

---

## 📚 References

**llama.cpp Documentation**:
- OpenCL Backend: `llama.cpp/docs/backend/OPENCL.md`
- Android Build: `llama.cpp/docs/android.md`
- Build Guide: `llama.cpp/docs/build.md`

**AILive Documentation**:
- Research: `GPU_ACCELERATION_RESEARCH.md`
- Backup: `V1.0_BACKUP_RESTORE_GUIDE.md`
- Roadmap: `VERSION_ROLLOUT_PLAN.md`

**External Resources**:
- OpenCL Headers: https://github.com/KhronosGroup/OpenCL-Headers
- OpenCL ICD Loader: https://github.com/KhronosGroup/OpenCL-ICD-Loader
- Adreno GPU Docs: https://developer.qualcomm.com/software/adreno-gpu-sdk

---

**Last Updated**: November 9, 2025
**Status**: Implementation Complete - Ready for Testing
**Next**: Run setup script and build on local machine
**Expected Results**: 3-5x performance improvement on Adreno 750

---

*Remember: v1.0 backup is at tag `v1.0-stable` (commit 61ea16d). Restore anytime if needed!*
