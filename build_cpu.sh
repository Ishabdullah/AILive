#!/bin/bash
# Build CPU-only variant of AILive
# No OpenCL dependencies required

set -e

echo "============================================"
echo "Building AILive CPU Variant"
echo "============================================"
echo ""
echo "Requirements:"
echo "  - Android NDK configured"
echo "  - No OpenCL setup needed"
echo ""

# Build CPU debug variant
echo "Building CPU Debug APK..."
./gradlew assembleCpuDebug

# Build CPU release variant (optional)
# ./gradlew assembleCpuRelease

echo ""
echo "============================================"
echo "✅ Build Complete!"
echo "============================================"
echo ""
echo "Output APKs:"
echo "  Debug:   app/build/outputs/apk/cpu/debug/app-cpu-debug.apk"
echo "  Package: com.ailive.cpu"
echo "  Version: 1.0-CPU"
echo ""
echo "Install with:"
echo "  adb install app/build/outputs/apk/cpu/debug/app-cpu-debug.apk"
echo ""
echo "Features:"
echo "  ✅ CPU-only inference (universal compatibility)"
echo "  ✅ 7-8 tokens/second (baseline)"
echo "  ✅ Can install alongside GPU variant"
echo "  ✅ No OpenCL dependencies required"
echo ""
