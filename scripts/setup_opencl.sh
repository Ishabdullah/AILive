#!/bin/bash

# OpenCL Setup Script for AILive v1.1 GPU Acceleration
# This script installs OpenCL headers and libraries for Android NDK
# Target: Adreno 750 GPU (Samsung S24 Ultra, Snapdragon 8 Gen 3)
#
# Usage: ./scripts/setup_opencl.sh
# Requirements: NDK 26.3 or later, CMake, Ninja

set -e  # Exit on error

echo "============================================"
echo "AILive v1.1 - OpenCL Setup for Android"
echo "============================================"
echo ""

# Configuration
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
ANDROID_NDK="${ANDROID_NDK:-$ANDROID_SDK_ROOT/ndk/26.3.11579264}"
WORK_DIR="${HOME}/dev/llm"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}Configuration:${NC}"
echo "  Android SDK: $ANDROID_SDK_ROOT"
echo "  Android NDK: $ANDROID_NDK"
echo "  Work Dir: $WORK_DIR"
echo ""

# Check prerequisites
echo -e "${BLUE}Checking prerequisites...${NC}"

if [ ! -d "$ANDROID_NDK" ]; then
    echo -e "${YELLOW}Warning: NDK not found at $ANDROID_NDK${NC}"
    echo "Please set ANDROID_NDK environment variable or install NDK 26.3+"
    echo "Example: export ANDROID_NDK=$HOME/Android/Sdk/ndk/26.3.11579264"
    exit 1
fi

command -v cmake >/dev/null 2>&1 || { echo "cmake is required but not installed. Aborting." >&2; exit 1; }
command -v ninja >/dev/null 2>&1 || { echo "ninja is required but not installed. Aborting." >&2; exit 1; }
command -v git >/dev/null 2>&1 || { echo "git is required but not installed. Aborting." >&2; exit 1; }

echo -e "${GREEN}✓ All prerequisites found${NC}"
echo ""

# Create work directory
mkdir -p "$WORK_DIR"
cd "$WORK_DIR"

echo -e "${BLUE}Step 1/3: Installing OpenCL Headers${NC}"
echo "----------------------------------------"

if [ -d "OpenCL-Headers" ]; then
    echo "OpenCL-Headers directory exists, updating..."
    cd OpenCL-Headers
    git pull
    cd ..
else
    echo "Cloning OpenCL-Headers from Khronos Group..."
    git clone https://github.com/KhronosGroup/OpenCL-Headers
fi

echo "Copying OpenCL headers to NDK sysroot..."
cp -r OpenCL-Headers/CL "$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/"

echo -e "${GREEN}✓ OpenCL headers installed${NC}"
echo ""

echo -e "${BLUE}Step 2/3: Building OpenCL ICD Loader${NC}"
echo "----------------------------------------"

if [ -d "OpenCL-ICD-Loader" ]; then
    echo "OpenCL-ICD-Loader directory exists, removing..."
    rm -rf OpenCL-ICD-Loader
fi

echo "Cloning OpenCL-ICD-Loader from Khronos Group..."
git clone https://github.com/KhronosGroup/OpenCL-ICD-Loader

cd OpenCL-ICD-Loader
mkdir -p build_ndk26
cd build_ndk26

echo "Configuring OpenCL ICD Loader for Android..."
cmake .. -G Ninja -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
  -DOPENCL_ICD_LOADER_HEADERS_DIR="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=28 \
  -DANDROID_STL=c++_shared

echo "Building OpenCL ICD Loader..."
ninja

echo "Installing libOpenCL.so to NDK sysroot..."
cp libOpenCL.so "$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/"

cd "$WORK_DIR"

echo -e "${GREEN}✓ OpenCL ICD Loader built and installed${NC}"
echo ""

echo -e "${BLUE}Step 3/3: Verification${NC}"
echo "----------------------------------------"

HEADER_PATH="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/CL"
LIB_PATH="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libOpenCL.so"

if [ -d "$HEADER_PATH" ]; then
    echo -e "${GREEN}✓ OpenCL headers found:${NC} $HEADER_PATH"
    ls "$HEADER_PATH"/*.h | head -5
else
    echo -e "${YELLOW}✗ OpenCL headers NOT found${NC}"
    exit 1
fi

if [ -f "$LIB_PATH" ]; then
    echo -e "${GREEN}✓ OpenCL library found:${NC} $LIB_PATH"
    ls -lh "$LIB_PATH"
else
    echo -e "${YELLOW}✗ OpenCL library NOT found${NC}"
    exit 1
fi

echo ""
echo "============================================"
echo -e "${GREEN}✓ OpenCL Setup Complete!${NC}"
echo "============================================"
echo ""
echo "Next steps:"
echo "1. Build AILive: ./gradlew assembleDebug"
echo "2. Install on device: adb install app/build/outputs/apk/debug/app-debug.apk"
echo "3. Check logs for GPU detection: adb logcat | grep -E \"OpenCL|GPU|Adreno\""
echo ""
echo "Expected GPU: Adreno 750 (Snapdragon 8 Gen 3)"
echo "Expected speedup: 3-5x (7→20-30 tokens/second)"
echo ""
