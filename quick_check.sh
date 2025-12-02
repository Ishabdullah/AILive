#!/bin/bash
# Quick AILive Check - Run in Termux

echo "=== QUICK AILIVE CHECK ==="
echo ""

# 1. Model exists?
echo "1. Model file:"
adb shell "run-as com.ailive ls -lh /data/data/com.ailive/files/models/gpt2-decoder.onnx" 2>&1 | grep -v "No such"
echo ""

# 2. Recent errors
echo "2. Recent ERRORS (last 10):"
adb logcat -d | grep -E "LLMManager.*❌|PersonalityEngine.*❌|Error|Exception" | grep -v "Suppressed" | tail -10
echo ""

# 3. Is LLM initialized?
echo "3. LLM Status:"
adb logcat -d -s LLMManager:* | grep -E "initialized successfully|initialization failed" | tail -3
echo ""

# 4. Last generation attempt:
echo "4. Last generation attempt:"
adb logcat -d -s LLMManager:* | grep -E "Generating response|Generated|generation" | tail -5
echo ""

# 5. Fallback responses
echo "5. Fallback responses (if any):"
adb logcat -d -s PersonalityEngine:* | grep -i "fallback" | tail -5
echo ""

echo "=== END ==="
echo ""
echo "To see LIVE logs, run:"
echo "adb logcat -c && adb logcat -s LLMManager:* PersonalityEngine:*"
