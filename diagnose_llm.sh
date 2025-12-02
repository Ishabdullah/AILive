#!/bin/bash
# AILive LLM Diagnostic Script

echo "=== AILive LLM Diagnostics ==="
echo ""

echo "1. Checking if app is installed..."
adb shell pm list packages | grep com.ailive
echo ""

echo "2. Checking if model file exists..."
adb shell ls -lh /data/data/com.ailive/files/models/
echo ""

echo "3. Checking tokenizer file..."
adb shell ls -lh /data/data/com.ailive/files/tokenizer.json
echo ""

echo "4. Getting recent LLM logs..."
echo "--- LLMManager logs ---"
adb logcat -d -s LLMManager:* | tail -50
echo ""

echo "--- AILiveCore logs ---"
adb logcat -d -s AILiveCore:* | tail -30
echo ""

echo "--- PersonalityEngine logs ---"
adb logcat -d -s PersonalityEngine:* | tail -30
echo ""

echo "5. Checking for initialization errors..."
adb logcat -d | grep -i "llm\|onnx\|tokenizer" | grep -i "error\|failed\|exception" | tail -20
echo ""

echo "=== Diagnostic Complete ==="
echo ""
echo "Please share this output to diagnose the issue."
