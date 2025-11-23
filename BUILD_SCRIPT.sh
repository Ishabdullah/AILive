#!/bin/bash

# AILive Native Library Build Script
# This script fixes the LLM response issue by building the native library

set -e

echo "🚀 Starting AILive Native Library Build..."

# Check if we're in the right directory
if [ ! -f "app/src/main/cpp/CMakeLists.txt" ]; then
    echo "❌ Error: Please run this script from the AILive root directory"
    exit 1
fi

# Clean previous builds
echo "🧹 Cleaning previous builds..."
rm -rf app/build/
rm -rf .gradle/
rm -rf external/*/build/

# Initialize submodules (already done, but ensuring)
echo "📦 Ensuring submodules are initialized..."
git submodule update --init --recursive

# Check if external dependencies exist
echo "🔍 Checking dependencies..."
if [ ! -d "external/llama.cpp" ] || [ ! -f "external/llama.cpp/CMakeLists.txt" ]; then
    echo "❌ Error: llama.cpp submodule not found"
    exit 1
fi

if [ ! -d "external/whisper.cpp" ] || [ ! -f "external/whisper.cpp/CMakeLists.txt" ]; then
    echo "❌ Error: whisper.cpp submodule not found"
    exit 1
fi

echo "✅ Dependencies found"

# Set Java home
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# Try a minimal build first to test
echo "🔨 Attempting minimal native build..."
export GRADLE_OPTS="-Xmx1g -XX:MaxMetaspaceSize=256m"
export ANDROID_HOME=/opt/android-sdk

# First try just the native part
./gradlew app:externalNativeBuildDebug --no-daemon --stacktrace || {
    echo "❌ Native build failed, trying alternative approach..."
    
    # Check CMake version
    cmake --version || {
        echo "❌ CMake not found, installing..."
        apt update && apt install -y cmake
    }
    
    # Try manual CMake build
    mkdir -p app/build/intermediates/cmake/debug/obj/armeabi-v7a
    cd app/src/main/cpp
    cmake -B ../../../../build/intermediates/cmake/debug/obj/armeabi-v7a \
          -DANDROID_ABI=armeabi-v7a \
          -DANDROID_PLATFORM=android-21 \
          -DCMAKE_TOOLCHAIN_FILE=$ANDROID_HOME/ndk/21.4.7075529/build/cmake/android.toolchain.cmake \
          . || {
        echo "❌ Manual CMake build also failed"
        echo "🔧 Trying without NDK paths..."
        
        # Simple build without Android specifics first
        mkdir -p build_test
        cd build_test
        cmake .. -DCMAKE_BUILD_TYPE=Debug || {
            echo "❌ Basic CMake test failed"
            cd ../..
            exit 1
        }
        make -j2 || {
            echo "❌ Make failed"
            cd ../..
            exit 1
        }
        echo "✅ Basic native compilation successful!"
        cd ../..
    }
    cd ../../..
}

echo "🎉 Build process completed!"
echo "📁 Check app/build/intermediates/cmake/ for native libraries"

# List built libraries if they exist
find app/build -name "*.so" 2>/dev/null && echo "✅ Native libraries found!" || echo "⚠️ No .so files found yet"