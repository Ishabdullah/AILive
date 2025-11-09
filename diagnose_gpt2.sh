#!/bin/bash
# AILive GPT-2 Diagnostics Script
# Run this in Termux after installing and starting the app

echo "================================"
echo "AILive GPT-2 Diagnostics"
echo "================================"
echo ""

# Check if model file exists
echo "1. Checking model file..."
adb shell "run-as com.ailive ls -lh /data/data/com.ailive/files/models/" 2>/dev/null
if [ $? -eq 0 ]; then
    echo "✓ Model directory accessible"
else
    echo "✗ Cannot access model directory"
fi
echo ""

# Check model size
echo "2. Checking model file size..."
MODEL_SIZE=$(adb shell "run-as com.ailive stat -c %s /data/data/com.ailive/files/models/gpt2-decoder.onnx" 2>/dev/null)
if [ ! -z "$MODEL_SIZE" ]; then
    MODEL_SIZE_MB=$((MODEL_SIZE / 1024 / 1024))
    echo "✓ Model size: ${MODEL_SIZE_MB} MB"
    if [ $MODEL_SIZE_MB -lt 600 ]; then
        echo "⚠️  WARNING: Model is too small! Expected ~653MB"
    elif [ $MODEL_SIZE_MB -gt 700 ]; then
        echo "⚠️  WARNING: Model is too large!"
    else
        echo "✓ Model size looks correct"
    fi
else
    echo "✗ Model file not found: gpt2-decoder.onnx"
fi
echo ""

# Check tokenizer
echo "3. Checking tokenizer..."
TOK_SIZE=$(adb shell "run-as com.ailive stat -c %s /data/data/com.ailive/files/tokenizer.json" 2>/dev/null)
if [ ! -z "$TOK_SIZE" ]; then
    TOK_SIZE_KB=$((TOK_SIZE / 1024))
    echo "✓ Tokenizer size: ${TOK_SIZE_KB} KB"
else
    echo "✗ Tokenizer not found"
fi
echo ""

# Check for LLM initialization
echo "4. Checking LLM initialization logs..."
adb logcat -d -s LLMManager:* | grep -E "Initializing|initialized|ONNX|tokenizer" | tail -20
echo ""

# Check for inference attempts
echo "5. Checking for inference attempts..."
adb logcat -d -s LLMManager:* | grep -E "Generating|generate|inference|Step" | tail -20
echo ""

# Check for errors
echo "6. Checking for ERRORS..."
adb logcat -d -s LLMManager:E PersonalityEngine:E | tail -30
echo ""

# Check for OrtException
echo "7. Checking for ONNX Runtime errors..."
adb logcat -d | grep -i "OrtException\|onnxruntime" | tail -20
echo ""

# Check PersonalityEngine
echo "8. Checking PersonalityEngine responses..."
adb logcat -d -s PersonalityEngine:* | grep -E "generated response|fallback" | tail -15
echo ""

# Live monitoring option
echo "================================"
echo "Would you like to monitor live logs? (Ctrl+C to stop)"
echo "Running: adb logcat -s LLMManager:* PersonalityEngine:* AILiveCore:*"
echo "================================"
sleep 2
adb logcat -c
adb logcat -s LLMManager:* PersonalityEngine:* AILiveCore:*
