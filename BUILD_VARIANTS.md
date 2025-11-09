# AILive Build Variants Guide

**Version 1.1** introduces GPU and CPU build variants that can be installed side-by-side on your device.

---

## 📦 Available Variants

| Variant | Package ID | GPU Support | Speed | Requirements |
|---------|-----------|-------------|-------|--------------|
| **GPU** | `com.ailive.gpu` | ✅ OpenCL (Adreno 750) | 20-30 tok/s | OpenCL setup |
| **CPU** | `com.ailive.cpu` | ❌ CPU only | 7-8 tok/s | None |

Both variants can be installed simultaneously on the same device!

---

## 🚀 Quick Start

### Option 1: Simple Build Scripts (Recommended)

```bash
# GPU variant (requires OpenCL setup first)
./scripts/setup_opencl.sh  # Only needed once
./build_gpu.sh

# CPU variant (no setup needed)
./build_cpu.sh
```

### Option 2: Manual Gradle Commands

```bash
# GPU variant
ENABLE_GPU=true ./gradlew assembleGpuDebug -PENABLE_GPU=true

# CPU variant
./gradlew assembleCpuDebug
```

### Option 3: Android Studio

1. Open Android Studio
2. Go to **Build** → **Select Build Variant**
3. Choose variant:
   - `gpuDebug` - GPU-enabled debug build
   - `cpuDebug` - CPU-only debug build
   - `gpuRelease` - GPU-enabled release build
   - `cpuRelease` - CPU-only release build
4. For GPU variants: Set environment variable `ENABLE_GPU=true` in Run Configuration
5. Click **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**

---

## 🔧 GPU Build Setup (One-Time)

Before building GPU variants, install OpenCL dependencies:

```bash
# Clone repo and navigate to project
cd ~/AILive

# Install OpenCL headers and libraries for Android
chmod +x scripts/setup_opencl.sh
./scripts/setup_opencl.sh

# Expected time: 3-5 minutes
# This installs OpenCL SDK to your Android NDK sysroot
```

**What this does**:
- Downloads OpenCL headers from Khronos
- Builds OpenCL ICD Loader for Android arm64-v8a
- Installs to `$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr`
- Verifies installation

**You only need to run this once per development machine.**

---

## 📱 Building APKs

### GPU Variant (Adreno 750 Optimized)

```bash
# 1. Setup OpenCL (skip if already done)
./scripts/setup_opencl.sh

# 2. Build GPU APK
./build_gpu.sh

# 3. Install on device
adb install app/build/outputs/apk/gpu/debug/app-gpu-debug.apk

# 4. Verify GPU detection in logs
adb logcat | grep -E "GPU|OpenCL|Adreno"
```

**Expected output in logs**:
```
LLMManager: 🔍 Detecting GPU acceleration...
LLMManager: GPU detected: Adreno (TM) 750
LLMManager: ✅ Using OpenCL GPU acceleration
```

**Performance**: 20-30 tokens/second (3-5x faster than CPU)

---

### CPU Variant (Universal)

```bash
# 1. Build CPU APK (no setup needed)
./build_cpu.sh

# 2. Install on device
adb install app/build/outputs/apk/cpu/debug/app-cpu-debug.apk

# 3. Verify CPU fallback in logs
adb logcat | grep LLMManager
```

**Expected output in logs**:
```
LLMManager: 🔍 Detecting GPU acceleration...
LLMManager: GPU fallback: OpenCL support not compiled in build
LLMManager: ✅ Using CPU inference
```

**Performance**: 7-8 tokens/second (baseline)

---

## 🔄 Side-by-Side Installation

Both variants can be installed simultaneously:

```bash
# Install GPU variant
adb install app/build/outputs/apk/gpu/debug/app-gpu-debug.apk

# Install CPU variant (doesn't replace GPU variant)
adb install app/build/outputs/apk/cpu/debug/app-cpu-debug.apk
```

**Why this works**:
- GPU variant package ID: `com.ailive.gpu`
- CPU variant package ID: `com.ailive.cpu`
- Android treats them as separate apps

**Benefits**:
- Compare GPU vs CPU performance directly
- Switch between versions for testing
- Keep stable CPU version while testing GPU features

---

## 📊 APK Output Locations

After building, APKs are located at:

```
app/build/outputs/apk/
├── cpu/
│   ├── debug/
│   │   └── app-cpu-debug.apk         (CPU debug, ~50MB)
│   └── release/
│       └── app-cpu-release-unsigned.apk
└── gpu/
    ├── debug/
    │   └── app-gpu-debug.apk         (GPU debug, ~50MB)
    └── release/
        └── app-gpu-release-unsigned.apk
```

---

## 🎯 Which Variant Should I Use?

### Use GPU Variant If:
- ✅ You have a compatible device (Adreno 750, Snapdragon 8 Gen 3)
- ✅ You want maximum performance (3-5x faster)
- ✅ You're willing to setup OpenCL dependencies
- ✅ Battery life is less critical (GPU uses more power)

**Recommended devices**:
- Samsung S24 Ultra
- OnePlus 12
- Xiaomi 14 Pro
- Any Snapdragon 8 Gen 3 device

### Use CPU Variant If:
- ✅ You want universal compatibility
- ✅ You don't want to setup OpenCL
- ✅ Battery life is critical
- ✅ You're testing on emulator or older devices

---

## 🛠️ Build Configurations

### GPU Variant Configuration

**App module** (`app/build.gradle.kts`):
```kotlin
productFlavors {
    create("gpu") {
        dimension = "acceleration"
        applicationIdSuffix = ".gpu"
        versionNameSuffix = "-GPU"
        buildConfigField("boolean", "GPU_ENABLED", "true")
    }
}
```

**Llama module** (`llama/build.gradle.kts`):
```kotlin
// When ENABLE_GPU=true is set:
arguments += "-DGGML_OPENCL=ON"
arguments += "-DGGML_OPENCL_EMBED_KERNELS=ON"
arguments += "-DGGML_OPENCL_USE_ADRENO_KERNELS=ON"
```

### CPU Variant Configuration

**App module**:
```kotlin
productFlavors {
    create("cpu") {
        dimension = "acceleration"
        applicationIdSuffix = ".cpu"
        versionNameSuffix = "-CPU"
        buildConfigField("boolean", "GPU_ENABLED", "false")
    }
}
```

**Llama module**: No OpenCL flags (CPU-only build)

---

## 🔍 Troubleshooting

### GPU Build Fails with "OpenCL not found"

**Problem**: CMake can't find OpenCL headers/libraries

**Solution**:
```bash
# Run OpenCL setup script
./scripts/setup_opencl.sh

# Verify installation
ls $NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/CL
# Should see: cl.h, cl_platform.h, opencl.h

# Retry build
./build_gpu.sh
```

### GPU APK Installed But Using CPU

**Problem**: App detects CPU fallback instead of GPU

**Possible causes**:
1. Built with CPU variant instead of GPU
2. Device doesn't have OpenCL support
3. OpenCL driver not available on device

**Solution**:
```bash
# Check build variant
adb shell dumpsys package com.ailive.gpu | grep versionName
# Should show: versionName=1.0-GPU

# Check device OpenCL support
adb shell getprop | grep -i opencl

# Rebuild with GPU variant
./build_gpu.sh
adb install -r app/build/outputs/apk/gpu/debug/app-gpu-debug.apk
```

### Both Variants Installed, Can't Tell Them Apart

**Solution**: App names include variant suffix

- GPU variant: "AILive GPU"
- CPU variant: "AILive CPU"

Check in logs:
```bash
# GPU variant logs
adb logcat | grep "com.ailive.gpu"

# CPU variant logs
adb logcat | grep "com.ailive.cpu"
```

---

## 📚 Advanced Usage

### Building Both Variants at Once

```bash
# Build all variants
./gradlew assembleCpuDebug assembleCpuRelease assembleGpuDebug assembleGpuRelease -PENABLE_GPU=true

# Install both debug variants
adb install app/build/outputs/apk/cpu/debug/app-cpu-debug.apk
adb install app/build/outputs/apk/gpu/debug/app-gpu-debug.apk
```

### Environment Variables

| Variable | Purpose | Values |
|----------|---------|--------|
| `ENABLE_GPU` | Enable OpenCL in llama.cpp | `true`, `false` |
| `NDK_ROOT` | Android NDK path | Auto-detected or manual |

### Gradle Properties

```bash
# Enable GPU via gradle property
./gradlew assembleGpuDebug -PENABLE_GPU=true

# Disable GPU (CPU only)
./gradlew assembleCpuDebug
```

---

## 🎓 For Developers

### Adding New Variants

Edit `app/build.gradle.kts`:

```kotlin
flavorDimensions += "acceleration"
productFlavors {
    create("yourVariant") {
        dimension = "acceleration"
        applicationIdSuffix = ".yourvariant"
        versionNameSuffix = "-YourVariant"
        buildConfigField("boolean", "GPU_ENABLED", "true")
    }
}
```

### Checking Current Variant in Code

```kotlin
import com.ailive.BuildConfig

if (BuildConfig.GPU_ENABLED) {
    Log.d(TAG, "Running GPU variant: ${BuildConfig.BUILD_VARIANT}")
} else {
    Log.d(TAG, "Running CPU variant: ${BuildConfig.BUILD_VARIANT}")
}
```

---

## 📈 Performance Comparison

| Metric | CPU Variant | GPU Variant | Improvement |
|--------|-------------|-------------|-------------|
| Tokens/sec | 7-8 | 20-30 | **3-5x faster** |
| First token | ~1.5s | ~0.5s | **3x faster** |
| Memory | ~1.2GB | ~1.5GB | +300MB |
| Battery | ~3%/hour | ~5%/hour | +2%/hour |
| Build time | 5 min | 8 min | +3 min |

**Tested on**: Samsung S24 Ultra (Snapdragon 8 Gen 3, Adreno 750, 12GB RAM)

---

## ✅ Summary

**GPU Variant** (`com.ailive.gpu`):
- ✅ 3-5x faster inference
- ✅ Adreno 750 optimized
- ⚠️ Requires OpenCL setup
- ⚠️ Higher battery usage

**CPU Variant** (`com.ailive.cpu`):
- ✅ Universal compatibility
- ✅ No setup required
- ✅ Lower battery usage
- ⚠️ Slower inference

**Recommendation**: Install both and compare!

---

**Need help?** See:
- `GPU_IMPLEMENTATION_STATUS.md` - Implementation details
- `OPENCL_GPU_IMPLEMENTATION_GUIDE.md` - Deep dive into OpenCL
- `scripts/setup_opencl.sh` - OpenCL installation script
