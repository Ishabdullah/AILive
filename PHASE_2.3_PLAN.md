# Phase 2.3: Audio Integration

## Objective
Give AILive "hearing" - enable voice commands, wake word detection, and speech-to-text perception

## Why Audio Before Fixing Camera?
- **No hardware quirks**: Android AudioRecord is stable across devices
- **Immediate value**: Voice commands = natural AI interaction
- **Feeds more agents**: Audio data benefits EmotionAI (tone), MemoryAI (conversations), MetaAI (context)
- **Multi-modal perception**: Vision + hearing = richer environmental understanding
- **Camera works**: Preview displays, can revisit analyzer issue later

## Components to Build

### 1. AudioManager
- Captures microphone input via Android AudioRecord
- Continuous listening with circular buffer
- Handles RECORD_AUDIO permission
- Provides audio data to wake word detector and speech recognizer

### 2. WakeWordDetector
- Simple keyword matching initially (upgradeable to ML later)
- Listens for custom wake phrase (e.g., "Hey AILive")
- Low-power continuous monitoring
- Triggers speech recognition when detected

### 3. SpeechProcessor
- Uses Android's built-in SpeechRecognizer
- Converts speech to text
- Provides transcription to CommandRouter

### 4. CommandRouter
- Parses user commands from transcribed text
- Routes to appropriate agents:
  - "What do you see?" → MotorAI + ModelManager
  - "How am I feeling?" → EmotionAI
  - "Remember this..." → MemoryAI
  - "What should I do?" → MetaAI
- Provides text-to-speech feedback

### 5. Update MainActivity
- Add microphone permission
- Add audio status indicator
- Display transcription + command results
- Show wake word detection status

## Pipeline Flow
```
Microphone → AudioManager → WakeWordDetector → "Detected!"
                                ↓
                         SpeechProcessor → Transcription
                                ↓
                          CommandRouter → Route to Agents
                                ↓
                           Agent Response → Text-to-Speech
```

## Implementation Steps

**Step 1:** Create AudioManager.kt for microphone capture
**Step 2:** Implement WakeWordDetector.kt with simple pattern matching
**Step 3:** Create SpeechProcessor.kt using Android SpeechRecognizer
**Step 4:** Build CommandRouter.kt to parse and route commands
**Step 5:** Update MainActivity to integrate audio pipeline
**Step 6:** Add RECORD_AUDIO permission to AndroidManifest.xml
**Step 7:** Update UI with audio status and transcription display

## Expected Result
- Open app → "Listening for 'Hey AILive'..." status
- Say wake word → Beep + "Listening..." indicator
- Speak command → Transcription appears + agent responds
- Example: "Hey AILive, what do you see?" → MotorAI describes camera view

## Future Enhancements (Phase 2.4+)
- Custom wake word training with ML models
- Emotion detection from voice tone
- Multi-language support
- Voice synthesis for agent responses
- Ambient sound classification (baby crying, doorbell, etc.)
