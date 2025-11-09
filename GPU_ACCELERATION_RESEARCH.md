# GPU Acceleration Research for AILive v1.1

**Date**: November 9, 2025
**Target Device**: Samsung S24 Ultra (Snapdragon 8 Gen 3, Adreno 750 GPU)
**Research Duration**: 2 hours
**Status**: ✅ Research Complete - Implementation Plan Ready

---

## Executive Summary

After extensive research into GPU acceleration options for llama.cpp on Android, **OpenCL is the recommended path** for AILive v1.1, not Vulkan. OpenCL is officially designed for and tested on Adreno 750 GPUs with Snapdragon 8 Gen 3 chipsets - exactly our target hardware.

### Key Findings

| Aspect | Vulkan | OpenCL | Winner |
|--------|--------|--------|--------|
| **Adreno Support** | ❌ Known issues, crashes, 15x slower | ✅ Designed for Adreno | **OpenCL** |
| **Samsung S24 Ultra** | ⚠️ Not verified | ✅ Verified on Adreno 750 | **OpenCL** |
| **Performance** | 24-25s vs 1.5s CPU (slower!) | Expected 3-5x speedup | **OpenCL** |
| **Official Status** | Experimental, problematic | Stable, optimized | **OpenCL** |
| **Documentation** | Limited Android docs | Complete Android guide | **OpenCL** |

---

## Research Findings

### 1. Vulkan Backend Analysis

#### Current Status (2025)
- **Desktop**: Fully supported, good performance
- **Android**: Experimental, severe limitations

#### Critical Issues on Android

**Performance Problems**:
- GitHub Discussion #9464: Vulkan is **15x slower** than CPU on Android
  - CPU inference: 1500-1700ms
  - Vulkan inference: 24000-25000ms
  - This is the **opposite** of desired GPU acceleration

**Adreno GPU Specific Issues**:
- Qualcomm Adreno GPUs fail to load models entirely
- ARM Mali GPUs can load but perform worse than CPU
- Only Samsung Exynos RDNA GPUs show acceptable performance

**Compilation Challenges**:
- Cross-compilation from Linux has bugs
- Requires building `vulkan-shaders-gen` separately on host
- NDK Vulkan headers are outdated and must be manually updated
- Complex Docker-based workarounds needed

**Memory Constraints**:
- Adreno GPUs have limited VRAM (~1GB)
- Requires manual feature disabling
- Often results in slower inference than CPU

**Source**:
- https://github.com/ggml-org/llama.cpp/discussions/9464
- https://github.com/ggml-org/llama.cpp/discussions/8874
- https://github.com/ggml-org/llama.cpp/issues/8705

**Verdict**: ❌ **DO NOT USE VULKAN** for Adreno 750 on Android

---

### 2. OpenCL Backend Analysis

#### Current Status (2025)
- **Officially designed for Qualcomm Adreno GPUs** (llama.cpp/docs/backend/OPENCL.md:20)
- **Actively maintained**: Recent bug fix for Adreno (commit 51f5a45fb, Jan 2025)
- **Production-ready** for Snapdragon 8 Gen 3 devices

#### Official Device Support

From `llama.cpp/docs/backend/OPENCL.md`:

**Verified OS**:
- ✅ Android - Snapdragon 8 Gen 3, Snapdragon 8 Elite
- ✅ Windows 11 Arm64 - Snapdragon X Elite
- ✅ Linux - Intel GPUs (Ubuntu 22.04 WSL2)

**Verified Adreno GPUs**:
- ✅ **Adreno 750** (Snapdragon 8 Gen 3) - **OUR TARGET DEVICE**
- ✅ Adreno 830 (Snapdragon 8 Elite)
- ✅ Adreno X85 (Snapdragon X Elite)

**Supported Quantization Types**:
- ✅ Q4_0 - **Fully supported and optimized**
- ✅ Q6_K - Supported but not optimized
- ✅ Q8_0 - Fully supported
- ✅ MXFP4 - Supported (for MoE models)

Our current model uses **Q4_K_M** quantization, which is similar to Q4_0. We may need to re-quantize to pure Q4_0 for optimal performance.

#### Build Requirements

**For Android (from official docs)**:

```bash
# CMake flags
-DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake
-DANDROID_ABI=arm64-v8a
-DANDROID_PLATFORM=android-28
-DBUILD_SHARED_LIBS=OFF
-DGGML_OPENCL=ON
```

**CMake Options**:
- `GGML_OPENCL_EMBED_KERNELS=ON` (default) - Embed kernels into executable
- `GGML_OPENCL_USE_ADRENO_KERNELS=ON` (default) - Use Adreno-optimized kernels

#### Performance Expectations

**Expected Speedup**: 3-5x faster than CPU (estimated)
- Current CPU performance: 7-8 tokens/second
- Expected GPU performance: **20-30 tokens/second**

**Optimization Recommendations**:
- Use pure Q4_0 quantization for best performance
- Add `--pure` flag to llama-quantize tool
- This makes all weights Q4_0 (vs mixed Q4_K_M/Q6_K)

**Known Limitations**:
- Flash attention does not always improve performance
- Q6_K not yet optimized (TODO in roadmap)
- Q4_K support/optimization planned but not yet implemented

**Verdict**: ✅ **RECOMMENDED** - Use OpenCL for GPU acceleration

---

### 3. Alternative Frameworks Considered

During research, these alternatives were mentioned in community discussions:

| Framework | Notes | Recommendation |
|-----------|-------|----------------|
| **MLC-LLM** | Broader device compatibility, different architecture | Consider for v2.0+ |
| **MediaPipe** | Google's solution, may have better Android integration | Investigate later |
| **ExecuTorch** | Meta's on-device inference framework | Future consideration |

**Verdict**: Stick with llama.cpp + OpenCL for v1.1, evaluate alternatives in v2.0+

---

## Implementation Plan for v1.1

### Prerequisites

**Hardware Requirements**:
- ✅ Samsung S24 Ultra (Snapdragon 8 Gen 3, Adreno 750)
- ✅ minSdk 33 (Android 13+) - already configured
- ✅ NDK 27 - we have 26.3.11579264, should upgrade to 27.x

**Software Requirements**:
- OpenCL headers (from Khronos Group)
- OpenCL ICD Loader library
- CMake 3.22.1+ (we have 3.22.1)
- Android Studio (current version)

### Phase 1: Environment Setup (Day 1)

**Step 1.1**: Update NDK version
- Current: 26.3.11579264
- Target: 27.x (latest stable)
- Update in `app/build.gradle.kts`

**Step 1.2**: Install OpenCL dependencies
- Clone OpenCL-Headers from Khronos Group
- Clone OpenCL-ICD-Loader from Khronos Group
- Build OpenCL ICD Loader with Android toolchain
- Copy headers to NDK sysroot
- Copy libOpenCL.so to NDK lib directory

**Step 1.3**: Verify environment
- Check NDK installation path
- Verify OpenCL headers are accessible
- Test OpenCL library compilation

### Phase 2: Build Configuration (Days 2-3)

**Step 2.1**: Update CMakeLists.txt

File: `external/llama.cpp/examples/llama.android/llama/src/main/cpp/CMakeLists.txt`

Add OpenCL flags:
```cmake
# Enable OpenCL for Adreno GPU
set(GGML_OPENCL ON)
set(GGML_OPENCL_EMBED_KERNELS ON)
set(GGML_OPENCL_USE_ADRENO_KERNELS ON)

# Load llama.cpp with OpenCL enabled
add_subdirectory(../../../../../../../../llama.cpp build-llama)
```

**Step 2.2**: Update build.gradle.kts

File: `app/build.gradle.kts`

Add CMake arguments:
```kotlin
android {
    defaultConfig {
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DGGML_OPENCL=ON",
                    "-DGGML_OPENCL_USE_ADRENO_KERNELS=ON",
                    "-DANDROID_PLATFORM=android-28"
                )
            }
        }
    }
}
```

**Step 2.3**: Configure llama.cpp submodule
- Navigate to root llama.cpp directory
- Ensure CMake can find OpenCL headers
- Configure with GGML_OPENCL=ON

### Phase 3: Model Optimization (Day 4)

**Step 3.1**: Re-quantize model to pure Q4_0
- Current: `qwen2.5-vl-2b-instruct-q4_k_m.gguf` (940MB)
- Target: `qwen2.5-vl-2b-instruct-q4_0-pure.gguf`
- Use `llama-quantize --pure` flag
- Expected size: Similar (940MB)
- Expected quality: Minimal difference
- Expected performance: **Optimized for Adreno**

**Step 3.2**: Update model download
- Add new Q4_0 pure model to Hugging Face (or build locally)
- Update ModelDownloadManager to use new model
- Keep backward compatibility with Q4_K_M

**Step 3.3**: Test both quantizations
- Benchmark Q4_K_M with OpenCL
- Benchmark Q4_0 pure with OpenCL
- Compare quality and speed
- Document findings

### Phase 4: Code Integration (Days 5-7)

**Step 4.1**: Add GPU detection

File: `app/src/main/java/com/ailive/ai/llm/LLMManager.kt`

```kotlin
class LLMManager(private val context: Context) {

    data class GPUInfo(
        val isAvailable: Boolean,
        val backend: String, // "OpenCL", "CPU", etc.
        val deviceName: String
    )

    private external fun detectGPU(): String

    fun getGPUInfo(): GPUInfo {
        val info = detectGPU()
        return parseGPUInfo(info)
    }

    private fun parseGPUInfo(info: String): GPUInfo {
        // Parse native GPU detection results
        // Format: "OpenCL:Adreno 750" or "CPU:None"
        val parts = info.split(":")
        return GPUInfo(
            isAvailable = parts[0] != "CPU",
            backend = parts[0],
            deviceName = parts.getOrNull(1) ?: "Unknown"
        )
    }
}
```

**Step 4.2**: Add JNI GPU detection function

File: `external/llama.cpp/examples/llama.android/llama/src/main/cpp/llama-android.cpp`

```cpp
#include <CL/cl.h>

extern "C"
JNIEXPORT jstring JNICALL
Java_android_llama_cpp_LLamaAndroid_detectGPU(JNIEnv *env, jobject /* this */) {
    cl_uint num_platforms = 0;
    cl_int ret = clGetPlatformIDs(0, nullptr, &num_platforms);

    if (ret != CL_SUCCESS || num_platforms == 0) {
        return env->NewStringUTF("CPU:None");
    }

    cl_platform_id platform;
    ret = clGetPlatformIDs(1, &platform, nullptr);

    if (ret != CL_SUCCESS) {
        return env->NewStringUTF("CPU:None");
    }

    cl_uint num_devices = 0;
    ret = clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 0, nullptr, &num_devices);

    if (ret != CL_SUCCESS || num_devices == 0) {
        return env->NewStringUTF("CPU:None");
    }

    cl_device_id device;
    ret = clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 1, &device, nullptr);

    if (ret != CL_SUCCESS) {
        return env->NewStringUTF("CPU:None");
    }

    char device_name[128];
    ret = clGetDeviceInfo(device, CL_DEVICE_NAME, sizeof(device_name), device_name, nullptr);

    if (ret != CL_SUCCESS) {
        return env->NewStringUTF("OpenCL:Unknown");
    }

    std::string result = "OpenCL:" + std::string(device_name);
    return env->NewStringUTF(result.c_str());
}
```

**Step 4.3**: Update initialization logging

```kotlin
suspend fun initialize(): Boolean {
    val gpuInfo = getGPUInfo()
    Log.i(TAG, "GPU Detection: ${gpuInfo.backend} - ${gpuInfo.deviceName}")

    if (gpuInfo.isAvailable) {
        Log.i(TAG, "✅ GPU acceleration enabled via ${gpuInfo.backend}")
    } else {
        Log.i(TAG, "ℹ️ Using CPU inference (GPU not available)")
    }

    // Continue with normal initialization
    val modelPath = modelDownloadManager.getModelPath(...)
    llamaAndroid.load(modelPath)

    return true
}
```

### Phase 5: Performance Monitoring (Days 8-10)

**Step 5.1**: Add benchmarking infrastructure

```kotlin
data class InferenceStats(
    val tokensPerSecond: Float,
    val totalTokens: Int,
    val timeMs: Long,
    val backend: String,
    val modelName: String
)

class PerformanceMonitor {
    private val stats = mutableListOf<InferenceStats>()

    fun recordInference(
        tokens: Int,
        timeMs: Long,
        backend: String,
        model: String
    ) {
        val tps = (tokens.toFloat() / timeMs) * 1000
        stats.add(InferenceStats(tps, tokens, timeMs, backend, model))
    }

    fun getAverageSpeed(): Float {
        return stats.map { it.tokensPerSecond }.average().toFloat()
    }

    fun getStats(): List<InferenceStats> = stats.toList()
}
```

**Step 5.2**: Integrate monitoring into generate()

```kotlin
suspend fun generate(prompt: String, image: Bitmap? = null): String {
    val startTime = System.currentTimeMillis()
    var tokenCount = 0

    val response = StringBuilder()
    llamaAndroid.send(prompt, formatChat = false)
        .collect { token ->
            response.append(token)
            tokenCount++
        }

    val endTime = System.currentTimeMillis()
    val duration = endTime - startTime

    performanceMonitor.recordInference(
        tokens = tokenCount,
        timeMs = duration,
        backend = gpuInfo.backend,
        model = "qwen2.5-vl-2b-q4_0"
    )

    Log.d(TAG, "Inference: $tokenCount tokens in ${duration}ms = ${tokenCount * 1000f / duration} tok/s")

    return response.toString().trim()
}
```

**Step 5.3**: Create benchmark UI screen (optional)
- Display current backend (OpenCL/CPU)
- Show average tokens/second
- Show inference history
- Add manual benchmark button

### Phase 6: Testing & Validation (Days 11-14)

**Step 6.1**: Functional Testing
- ✅ App builds successfully with OpenCL
- ✅ GPU detection works correctly
- ✅ Model loads on GPU (or falls back to CPU)
- ✅ Inference produces correct outputs
- ✅ Performance monitoring logs accurate stats

**Step 6.2**: Performance Testing
- 📊 Benchmark CPU vs GPU speed
- 📊 Verify 3x minimum speedup (7→21+ tok/s)
- 📊 Test with various prompt lengths
- 📊 Test with long conversations (context management)

**Step 6.3**: Battery & Thermal Testing
- 🔋 8-hour usage test with GPU enabled
- 🔋 Measure battery drain rate
- 🔋 Target: <5% per hour
- 🌡️ Monitor device temperature
- 🌡️ Check for thermal throttling
- 🌡️ Ensure no overheating warnings

**Step 6.4**: Stability Testing
- 💪 24-hour continuous usage test
- 💪 Memory leak detection
- 💪 Crash reporting
- 💪 Background/foreground transitions
- 💪 App lifecycle management

**Step 6.5**: Edge Cases
- ⚠️ GPU unavailable (older devices)
- ⚠️ GPU fails to initialize
- ⚠️ Out of memory errors
- ⚠️ Very long prompts (>2048 tokens)
- ⚠️ Rapid consecutive requests

### Phase 7: Documentation (Days 13-14)

**Step 7.1**: Create GPU implementation document
- File: `GPU_ACCELERATION_IMPLEMENTATION.md`
- Document all changes made
- Include build instructions
- Add troubleshooting guide
- Include performance benchmarks

**Step 7.2**: Update README.md
- Add GPU acceleration section
- Document hardware requirements
- Show performance improvements
- Update installation instructions
- Add FAQ section

**Step 7.3**: Update VERSION_ROLLOUT_PLAN.md
- Mark v1.1 Week 1-2 as complete
- Update timeline if needed
- Document actual vs estimated effort

**Step 7.4**: Update BUILD_LOG.md
- Add OpenCL implementation section
- Document any build issues encountered
- Include solutions and workarounds

---

## Risk Assessment

### High Priority Risks

**Risk 1**: OpenCL drivers missing on some Android 13+ devices
- **Mitigation**: Graceful fallback to CPU
- **Detection**: Check OpenCL availability at runtime
- **User impact**: Low (app still works, just slower)

**Risk 2**: Performance worse than expected
- **Mitigation**: Benchmark early, optimize if needed
- **Fallback**: Keep CPU path optimized
- **Success criteria**: Minimum 2x speedup (anything less is not worth the complexity)

**Risk 3**: Build complexity increases significantly
- **Mitigation**: Document all steps thoroughly
- **Fallback**: Can disable OpenCL with single CMake flag
- **Testing**: Ensure GitHub Actions can build successfully

### Medium Priority Risks

**Risk 4**: Battery drain higher than CPU-only
- **Mitigation**: Monitor power consumption during testing
- **Fallback**: Add user setting to disable GPU
- **Threshold**: If >8% per hour, reconsider GPU by default

**Risk 5**: NDK/OpenCL version conflicts
- **Mitigation**: Use exact versions from official docs
- **Testing**: Test on multiple NDK versions if needed

### Low Priority Risks

**Risk 6**: Q4_0 pure quantization affects quality
- **Mitigation**: A/B test Q4_K_M vs Q4_0
- **Fallback**: If quality degradation is noticeable, use Q4_K_M
- **Measurement**: Subjective quality assessment + perplexity if available

---

## Success Criteria

### Must Have (Go/No-Go)
- ✅ App builds successfully with OpenCL enabled
- ✅ GPU detection works correctly
- ✅ Inference produces correct outputs (same quality as CPU)
- ✅ No crashes or stability issues
- ✅ Battery drain <8% per hour

### Should Have (Performance)
- 📊 Minimum 2x speedup over CPU (14+ tok/s)
- 📊 Target 3-5x speedup (20-30 tok/s)
- 📊 Consistent performance across long conversations
- 🔋 Battery drain <5% per hour
- 🌡️ No thermal throttling under normal use

### Nice to Have (Polish)
- 🎨 GPU status indicator in UI
- 📈 Performance statistics screen
- ⚙️ User setting to enable/disable GPU
- 📊 Detailed benchmarking tools
- 📝 Comprehensive troubleshooting guide

---

## Timeline Estimate

| Phase | Days | Start | End |
|-------|------|-------|-----|
| 1. Environment Setup | 1 | Nov 11 | Nov 11 |
| 2. Build Configuration | 2 | Nov 12 | Nov 13 |
| 3. Model Optimization | 1 | Nov 14 | Nov 14 |
| 4. Code Integration | 3 | Nov 15 | Nov 17 |
| 5. Performance Monitoring | 3 | Nov 18 | Nov 20 |
| 6. Testing & Validation | 4 | Nov 21 | Nov 24 |
| 7. Documentation | 2 | Nov 23 | Nov 24 |
| **Total** | **14 days** | **Nov 11** | **Nov 24** |

**Buffer**: 2-3 days for unexpected issues
**Target Completion**: November 27, 2025 (before end of month)

---

## Next Steps

1. ✅ Complete this research document
2. ⏭️ Begin Phase 1: Environment Setup
3. ⏭️ Create OpenCL branch for development
4. ⏭️ Update todos in tracking system
5. ⏭️ Start implementation on Monday, Nov 11

---

## References

### Official Documentation
- llama.cpp OpenCL Backend: `llama.cpp/docs/backend/OPENCL.md`
- llama.cpp Build Guide: `llama.cpp/docs/build.md`
- llama.cpp Android Guide: `llama.cpp/docs/android.md`
- Android NDK Vulkan Guide: https://developer.android.com/ndk/guides/graphics/getting-started

### Community Discussions
- Vulkan Android Performance Issues: https://github.com/ggml-org/llama.cpp/discussions/9464
- Building Vulkan for Android: https://github.com/ggml-org/llama.cpp/discussions/8874
- GPU Acceleration on Android: https://github.com/ggml-org/llama.cpp/issues/8705

### Hardware Specifications
- Samsung S24 Ultra: Snapdragon 8 Gen 3, Adreno 750 GPU
- OpenCL Support: Adreno 750 officially verified
- Android Version: 13+ (API 33+)

---

**Document Version**: 1.0
**Last Updated**: November 9, 2025
**Author**: Claude (AILive Development Team)
**Status**: Ready for Implementation
