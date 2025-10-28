# Phase 2.3 Audio Integration - Testing Guide

## 🎯 What's New in This Build

AILive now has **HEARING**! The app can listen for voice commands and route them to the appropriate AI agents.

### New Capabilities
- ✅ Wake word detection ("Hey AILive")
- ✅ Continuous speech recognition
- ✅ Natural language command parsing
- ✅ Voice command routing to all 6 agents
- ✅ Real-time transcription display
- ✅ Auto-retry on speech timeout

---

## 📱 Installation

### Download Latest APK
```bash
# Check build status
cd ~/AILive && gh run list --limit 1

# Once completed, download artifact
gh run download --name ailive-debug

# Install on device via ADB
adb install -r app-debug.apk
```

### Launch App
```bash
adb shell am start -n com.ailive/.MainActivity
```

---

## 🧪 Testing the Audio Pipeline

### Test 1: Wake Word Detection

**What to test:** Basic wake word activation

**Steps:**
1. Open AILive app
2. Wait for "🎤 Say 'hey ailive'" indicator (bottom left)
3. Say clearly: **"Hey AILive"**
4. Watch for:
   - Transcription appears: "hey ailive" or "hey ai live"
   - Status changes to "🎤 Activated! Listening..."
   - Bottom right shows "● VOICE ACTIVE"

**Expected behavior:**
- Wake word detected within 1 second
- Automatically switches to command listening mode
- Audio status updates in real-time

**Troubleshooting:**
- If not detected, try: "Hey AI Live", "Hey A Live", "AILive"
- Speak clearly and wait for recognition service to start
- Check logs: `adb logcat -s WakeWordDetector SpeechProcessor`

---

### Test 2: Vision Commands → MotorAI

**What to test:** Camera-related commands

**Steps:**
1. Activate with: "Hey AILive"
2. Say: **"What do you see?"**
3. Check logs for routing to MotorAI
4. Response should appear in main text area

**Other commands to try:**
- "Look at this"
- "What is that?"
- "Identify this object"
- "Use your camera"

**Expected logs:**
```
CommandRouter: 🧠 Processing command: 'what do you see'
CommandRouter: → Routing to MotorAI (Vision)
MotorAI: Received COMMAND message
```

---

### Test 3: Emotion Commands → EmotionAI

**What to test:** Emotional state queries

**Commands to try:**
- "How do I feel?"
- "What's my mood?"
- "Am I happy?"
- "Check my emotions"

**Expected logs:**
```
CommandRouter: → Routing to EmotionAI
EmotionAI: Received QUERY message
```

---

### Test 4: Memory Commands → MemoryAI

**What to test:** Memory storage and recall

**Store memory:**
- "Remember I like coffee"
- "Remember my birthday is tomorrow"
- "Save this information"

**Recall memory:**
- "What did I tell you?"
- "Do you remember anything?"
- "Recall my memories"

**Expected logs:**
```
CommandRouter: → Routing to MemoryAI
MemoryAI: Received STORE message (for "remember")
MemoryAI: Received RECALL message (for queries)
```

---

### Test 5: Prediction Commands → PredictiveAI

**Commands:**
- "What will happen next?"
- "Predict the future"
- "What's going to happen?"

---

### Test 6: Planning Commands → MetaAI

**Commands:**
- "What should I do?"
- "Help me decide"
- "Make a plan"
- "Give me advice"

---

### Test 7: Status Query

**Command:**
- "How are you?"
- "System status"
- "What's your status?"

**Expected response:**
- "All systems operational. 6 agents active."

---

### Test 8: Unknown Command Handling

**What to test:** Fallback to MetaAI

**Commands:**
- "Tell me a joke"
- "What's the weather?"
- "Random question"

**Expected:**
- Routes to MetaAI for general processing
- Response: "I heard: '[command]'. Routing to general processing..."

---

### Test 9: Continuous Listening

**What to test:** Auto-restart after timeout

**Steps:**
1. Activate: "Hey AILive"
2. Don't say anything for 5 seconds
3. Watch audio status return to wake word listening
4. Try activating again

**Expected:**
- Gracefully handles timeout
- Auto-restarts wake word listening
- No crashes or freezes

---

### Test 10: Multi-Turn Conversation

**Steps:**
1. "Hey AILive" → "What do you see?"
2. Wait 3 seconds for response
3. "Hey AILive" → "How do I feel?"
4. Wait 3 seconds
5. "Hey AILive" → "Remember I tested you"

**Expected:**
- Each wake word detection works
- Commands route to different agents
- Status resets between turns

---

## 📊 Monitoring & Logs

### Real-time Monitoring
```bash
# Full audio pipeline logs
adb logcat -s MainActivity SpeechProcessor WakeWordDetector CommandRouter

# Agent message routing
adb logcat -s MessageBus MotorAI EmotionAI MemoryAI PredictiveAI RewardAI MetaAI

# Audio-only logs
adb logcat -s SpeechProcessor WakeWordDetector
```

### Key Log Messages to Look For

**Audio Initialization:**
```
MainActivity: === Initializing Audio Pipeline ===
SpeechProcessor: ✓ SpeechProcessor initialized
MainActivity: ✓ Phase 2.3: Audio pipeline operational
```

**Wake Word Detection:**
```
WakeWordDetector: 🎯 Wake word detected (exact): 'hey ailive'
MainActivity: 🎯 Wake word detected!
```

**Command Processing:**
```
CommandRouter: 🧠 Processing command: 'what do you see'
CommandRouter: → Routing to MotorAI (Vision)
```

**Speech Recognition Errors (normal):**
```
SpeechProcessor: ⚠️ No speech match (will retry)
SpeechProcessor: ⚠️ No speech input (will retry)
```

---

## 🐛 Known Issues & Workarounds

### Issue: Wake word not detected

**Cause:** Android SpeechRecognizer requires network connection for best accuracy

**Workaround:**
- Ensure WiFi/data is enabled
- Speak clearly and distinctly
- Try alternative phrases: "Hey AI Live", "AILive"

---

### Issue: "Speech recognition not available"

**Cause:** Google app or speech services not installed

**Workaround:**
```bash
# Check if Google app is installed
adb shell pm list packages | grep google

# If missing, install from Play Store
```

---

### Issue: Continuous timeout/restart loop

**Cause:** Microphone permission not granted or hardware issue

**Solution:**
```bash
# Check permission
adb shell dumpsys package com.ailive | grep RECORD_AUDIO

# Grant manually if needed
adb shell pm grant com.ailive android.permission.RECORD_AUDIO

# Restart app
adb shell am force-stop com.ailive
adb shell am start -n com.ailive/.MainActivity
```

---

## ✅ Success Criteria

Phase 2.3 is successful if:

- [x] Wake word "Hey AILive" activates command listening
- [x] Voice commands route to correct agents
- [x] Transcription displays in real-time
- [x] Audio status indicators update correctly
- [x] Auto-retry works after timeout
- [x] No crashes during continuous use
- [x] All 6 agents receive commands via MessageBus

---

## 📈 Next Steps After Testing

### If Everything Works:
- Document successful command examples
- Train custom wake word (future Phase 2.4)
- Add text-to-speech responses
- Implement voice tone emotion detection

### If Issues Found:
- Capture full logcat output
- Note specific failure scenario
- Check Android version compatibility
- Test on different network conditions

---

## 🎤 Command Reference Quick Sheet

| Intent | Command Examples | Routes To |
|--------|-----------------|-----------|
| **Vision** | "What do you see?", "Look at this" | MotorAI |
| **Emotion** | "How do I feel?", "What's my mood?" | EmotionAI |
| **Memory** | "Remember this", "What did I say?" | MemoryAI |
| **Prediction** | "What will happen?", "Predict" | PredictiveAI |
| **Goals** | "Show my progress", "My goals" | RewardAI |
| **Planning** | "What should I do?", "Help me" | MetaAI |
| **Status** | "How are you?", "System status" | System |

---

**Happy Testing! 🎉**

Report issues in logs and we'll iterate on the audio pipeline.
