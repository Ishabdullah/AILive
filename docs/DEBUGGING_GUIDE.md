# AILive Debugging Guide

**Complete guide to debugging every aspect of AILive using ADB and Termux**

**Version:** 1.4
**Last Updated:** 2025-11-12

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Start](#quick-start)
3. [Real-Time Log Monitoring](#real-time-log-monitoring)
4. [Debugging by Component](#debugging-by-component)
5. [Common Issues & Solutions](#common-issues--solutions)
6. [Advanced Debugging](#advanced-debugging)
7. [Log Analysis Tools](#log-analysis-tools)

---

## Prerequisites

### Install ADB (Android Debug Bridge)

**On Linux/Mac:**
```bash
# Ubuntu/Debian
sudo apt-get install android-tools-adb

# Mac (via Homebrew)
brew install android-platform-tools
```

**On Termux (Android):**
```bash
pkg install android-tools
```

### Enable USB Debugging on Phone
1. Settings → About Phone
2. Tap "Build Number" 7 times
3. Settings → Developer Options
4. Enable "USB Debugging"

### Connect Device
```bash
# Via USB
adb devices

# Via WiFi (phone and computer on same network)
adb tcpip 5555
adb connect <phone-ip>:5555
```

---

## Quick Start

### View All AILive Logs
```bash
# All AILive logs in real-time
adb logcat | grep -E "AILive|PersonalityEngine|LLMManager|MemoryModel|LocationTool|WebSearch"
```

### View Only Errors
```bash
adb logcat *:E | grep AILive
```

### Clear Logs and Start Fresh
```bash
adb logcat -c && adb logcat | grep AILive
```

---

## Real-Time Log Monitoring

### Monitor Everything (Comprehensive)
```bash
adb logcat | grep -E "AILive|PersonalityEngine|LLMManager|MemoryModelManager|EmbeddingModelManager|LocationTool|WebSearchTool|UserCorrectionTool|ModelDownloadManager|UnifiedMemoryManager|TextEmbedder"
```

### Save Logs to File
```bash
adb logcat | grep AILive > ailive_debug.log
```

### Watch Specific Component
```bash
# Replace TAG with component name
adb logcat | grep "TAG_NAME"
```

---

## Debugging by Component

### 1. Core System (AILiveCore)

**Monitor initialization:**
```bash
adb logcat | grep "AILiveCore"
```

**Expected output:**
```
AILiveCore: Initializing AILive system...
AILiveCore: ✓ Context managers initialized (location + statistics + memory)
AILiveCore: ✓ AILive initialized successfully (PersonalityEngine + 8 tools + legacy agents)
AILiveCore: ✓ AILive system fully operational (PersonalityEngine mode)
```

**Debug commands:**
```bash
# Check if all tools registered
adb logcat | grep "Tools:"

# Check initialization errors
adb logcat *:E | grep "AILiveCore"
```

---

### 2. LLM (Qwen Model)

**Monitor model loading:**
```bash
adb logcat | grep "LLMManager"
```

**Expected output:**
```
LLMManager: 🤖 Initializing Qwen2-VL with llama.cpp Android...
LLMManager: ✅ Qwen2-VL initialized successfully!
LLMManager: 🎉 AI is ready!
```

**Common issues:**
```bash
# Check for "model already loaded" error
adb logcat | grep "already loaded"

# Check model file exists
adb shell ls -lh /storage/emulated/0/Android/data/com.ailive/files/Download/*.gguf

# Check initialization status
adb logcat | grep "LLM ready\|LLM not available"
```

**Debug generation:**
```bash
# Watch live token generation
adb logcat | grep "🚀 Starting generation\|✅ Generation complete"

# Monitor performance
adb logcat | grep "tok/s"
```

---

### 3. Memory System

**Monitor memory initialization:**
```bash
adb logcat | grep -E "UnifiedMemoryManager|MemoryModelManager|EmbeddingModelManager"
```

**Expected output:**
```
UnifiedMemoryManager: Initializing unified memory system...
EmbeddingModelManager: 🔢 Initializing Embedding Model (BGE-small-en-v1.5)...
EmbeddingModelManager: ✅ Embedding Model initialized successfully!
```

**Debug embeddings:**
```bash
# Check if real embeddings are working
adb logcat | grep "Real semantic embeddings enabled\|fallback random embeddings"

# Monitor embedding generation
adb logcat | grep "TextEmbedder"
```

**Debug memory operations:**
```bash
# Check fact extraction
adb logcat | grep "extractFacts\|learnFact"

# Check memory retrieval
adb logcat | grep "retrieve_memory\|Memory context"
```

---

### 4. Location/GPS (LocationTool)

**Monitor location queries:**
```bash
adb logcat | grep "LocationTool"
```

**Expected output:**
```
LocationTool: 🌍 Getting current location...
LocationTool: 📍 Got coordinates: 41.xxxx, -72.xxxx
LocationTool: ✅ Location: Weathersfield, Connecticut, United States
```

**Debug location issues:**
```bash
# Check GPS permissions
adb shell dumpsys package com.ailive | grep "android.permission.ACCESS_FINE_LOCATION"

# Check location service status
adb shell settings get secure location_providers_allowed

# Force location update
adb logcat | grep "LocationManager.*update"
```

---

### 5. Web Search (WebSearchTool)

**Monitor web searches:**
```bash
adb logcat | grep "WebSearchTool\|SearchDecisionEngine"
```

**Expected output:**
```
WebSearchTool: 🔍 Executing web search...
SearchDecisionEngine: Query needs search: true
WebSearchTool: ✅ Search completed
```

**Debug search issues:**
```bash
# Check internet permission
adb shell dumpsys package com.ailive | grep "android.permission.INTERNET"

# Monitor search requests
adb logcat | grep "search.*query\|SearchProvider"

# Check API keys (if using paid providers)
adb logcat | grep "API.*key\|authentication"
```

---

### 6. User Corrections (UserCorrectionTool)

**Monitor corrections:**
```bash
adb logcat | grep "UserCorrectionTool"
```

**Expected output:**
```
UserCorrectionTool: 📝 Recording user correction:
UserCorrectionTool:    Type: wrong_tool
UserCorrectionTool:    What went wrong: Did not use location tool
UserCorrectionTool:    Correct approach: Use get_location tool
UserCorrectionTool: ✅ Correction saved to long-term memory
```

**Verify corrections stored:**
```bash
# Check if correction saved to database
adb logcat | grep "learnFact.*BEHAVIORAL"
```

---

### 7. Model Downloads

**Monitor downloads:**
```bash
adb logcat | grep "ModelDownloadManager"
```

**Expected output:**
```
ModelDownloadManager: 📥 Starting BGE Embedding Model download...
ModelDownloadManager: ✅ BGE model file downloaded (1/3)
ModelDownloadManager: ✅ All models downloaded successfully! (3/3)
```

**Debug download issues:**
```bash
# Check download progress
adb logcat | grep "Download.*progress\|MB\/"

# Check download failures
adb logcat *:E | grep "Download.*failed"

# Check available storage
adb shell df -h | grep "/data"

# List downloaded models
adb shell ls -lh /storage/emulated/0/Android/data/com.ailive/files/Download/
```

---

### 8. PersonalityEngine & Tool Execution

**Monitor tool execution:**
```bash
adb logcat | grep "PersonalityEngine"
```

**Expected output:**
```
PersonalityEngine: 🎯 Processing query: "What town am I in?"
PersonalityEngine: 🔧 Tool selected: get_location
PersonalityEngine: ✅ Tool execution successful
PersonalityEngine: 💬 Generated response
```

**Debug tool routing:**
```bash
# Check which tools are being called
adb logcat | grep "Tool selected:\|Executing tool:"

# Check tool execution errors
adb logcat *:E | grep "Tool.*failed\|execution error"

# Monitor tool registration
adb logcat | grep "registerTool\|Tools:"
```

---

## Common Issues & Solutions

### Issue 1: "I'm sorry, but I'm not able to assist with that"

**Cause:** Tool not being called (prompt issue or tool registration)

**Debug:**
```bash
# Check if tools are registered
adb logcat | grep "Tools:"

# Check prompt mentions tools
adb logcat | grep "YOUR CAPABILITIES"

# Check tool execution
adb logcat | grep "Tool selected"
```

**Solution:**
- Verify all 8 tools registered in AILiveCore
- Check system prompt describes capabilities
- Restart app to reload prompt

---

### Issue 2: "Model already loaded" error

**Cause:** Both TinyLlama and Qwen trying to load simultaneously

**Debug:**
```bash
# Check which models are loading
adb logcat | grep "Initializing.*Model\|already loaded"
```

**Solution:**
- TinyLlama should be disabled (see UnifiedMemoryManager)
- Only Qwen and BGE should load
- BGE uses ONNX (separate from llama.cpp)

---

### Issue 3: Location returns "not available"

**Cause:** GPS not acquired yet or permissions denied

**Debug:**
```bash
# Check location permissions
adb shell dumpsys package com.ailive | grep "ACCESS.*LOCATION"

# Check GPS status
adb shell dumpsys location

# Force GPS update
adb logcat | grep "location.*update"
```

**Solution:**
```bash
# Grant permissions manually
adb shell pm grant com.ailive android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.ailive android.permission.ACCESS_COARSE_LOCATION

# Restart app
adb shell am force-stop com.ailive
adb shell am start -n com.ailive/.MainActivity
```

---

### Issue 4: Embeddings using random fallback

**Cause:** BGE model not downloaded or not initialized

**Debug:**
```bash
# Check BGE model status
adb logcat | grep "BGE.*available\|BGE.*unavailable"

# Check BGE files exist
adb shell ls -lh /storage/emulated/0/Android/data/com.ailive/files/Download/model_quantized.onnx
adb shell ls -lh /storage/emulated/0/Android/data/com.ailive/files/Download/tokenizer.json
adb shell ls -lh /storage/emulated/0/Android/data/com.ailive/files/Download/config.json

# Check ONNX Runtime
adb logcat | grep "ONNX"
```

**Solution:**
- Download BGE model via settings
- Check all 3 files present (model, tokenizer, config)
- Restart app

---

## Advanced Debugging

### Filter by Priority Level
```bash
# Errors only
adb logcat *:E

# Warnings and errors
adb logcat *:W

# Info and above
adb logcat *:I

# Debug and above
adb logcat *:D

# Everything (verbose)
adb logcat *:V
```

### Continuous Monitoring Script
```bash
#!/bin/bash
# save as ailive_monitor.sh
while true; do
    echo "=== $(date) ==="
    adb logcat -d | grep -E "AILive|PersonalityEngine" | tail -20
    sleep 5
done
```

### Performance Monitoring
```bash
# CPU usage
adb shell top | grep com.ailive

# Memory usage
adb shell dumpsys meminfo com.ailive

# Battery usage
adb shell dumpsys batterystats | grep com.ailive
```

### Database Inspection
```bash
# Pull database for inspection
adb pull /data/data/com.ailive/databases/ailive_memory.db

# Use sqlite3 to query
sqlite3 ailive_memory.db "SELECT * FROM facts ORDER BY timestamp DESC LIMIT 10;"
```

### Network Debugging
```bash
# Monitor network requests (web search)
adb logcat | grep "http\|https\|WebSearchProvider"

# Check network connectivity
adb shell ping -c 3 8.8.8.8
```

---

## Log Analysis Tools

### Quick Log Summary
```bash
# Count errors
adb logcat -d | grep AILive | grep -c "ERROR"

# Count tool executions
adb logcat -d | grep "Tool selected" | wc -l

# Find most recent error
adb logcat -d | grep AILive | grep "ERROR" | tail -1
```

### Extract Specific Session
```bash
# From app start to now
adb logcat -d | grep "AILive initialized" -A 1000
```

### Performance Analysis
```bash
# Average response time
adb logcat -d | grep "Generation complete" | awk '{print $NF}' | sed 's/ms//' | awk '{sum+=$1; n++} END {print sum/n "ms average"}'
```

---

## Component Tags Reference

| Component | Log Tag | Purpose |
|-----------|---------|---------|
| Core System | `AILiveCore` | Overall system initialization |
| LLM | `LLMManager` | Qwen model loading & inference |
| Personality | `PersonalityEngine` | Unified intelligence & tool routing |
| Memory AI Model | `MemoryModelManager` | TinyLlama for memory ops (disabled) |
| Embeddings | `EmbeddingModelManager` | BGE ONNX embeddings |
| Location | `LocationTool` | GPS & geocoding |
| Web Search | `WebSearchTool` | Internet search |
| Corrections | `UserCorrectionTool` | User feedback system |
| Model Downloads | `ModelDownloadManager` | Model file downloads |
| Memory System | `UnifiedMemoryManager` | Persistent memory |
| Text Embedding | `TextEmbedder` | Embedding generation |

---

## Quick Reference Commands

```bash
# Start monitoring everything
adb logcat | grep -E "AILive|PersonalityEngine|LLMManager"

# Check current status
adb logcat -d | grep "✅\|❌" | tail -10

# Clear and restart logging
adb logcat -c && adb logcat | grep AILive

# Save detailed log
adb logcat -d > ailive_full_debug_$(date +%Y%m%d_%H%M%S).log

# Restart AILive
adb shell am force-stop com.ailive && adb shell am start -n com.ailive/.MainActivity

# Check app is running
adb shell ps | grep com.ailive

# View crash reports
adb logcat -d | grep "FATAL\|AndroidRuntime"
```

---

## Getting Help

If you encounter issues not covered here:

1. **Capture full log:**
   ```bash
   adb logcat -d > ailive_issue.log
   ```

2. **Describe the problem:**
   - What you asked AILive
   - What it responded
   - What you expected

3. **Share relevant logs:**
   - Error messages
   - Tool execution attempts
   - Model initialization status

---

**Pro Tip:** Keep a terminal window open with continuous monitoring during development:
```bash
adb logcat | grep -E "AILive.*:|PersonalityEngine:|Tool selected:|❌|✅" --line-buffered | tee ailive_session.log
```

This gives you real-time visibility into AILive's decision-making process!
