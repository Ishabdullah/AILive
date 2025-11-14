# AILive: Privacy-First On-Device AI Assistant

<div align="center">

[![Build Status](https://github.com/Ishabdullah/AILive/actions/workflows/android.yml/badge.svg)](https://github.com/Ishabdullah/AILive/actions)
[![License: CC BY-NC-SA 4.0](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-13%2B-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org)
[![API](https://img.shields.io/badge/API-33%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=33)

**A fully on-device AI companion for Android that respects your privacy**

[Features](#-features) • [Installation](#-installation) • [Architecture](#-architecture) • [Contributing](#-contributing)

</div>

---

## 🎯 Overview

AILive is a lightweight, privacy-focused AI assistant that runs **100% on your Android device**. No cloud dependencies, no data uploads, no subscriptions—just you and your personal AI.

### Core Capabilities

- 🎤 **Wake Word Detection** - Activate with "Hey AILive" hands-free
- 👁️ **Real-Time Vision** - Analyzes camera feed for object detection
- 💬 **Streaming Responses** - Natural conversation with voice and text
- 🧠 **Conversation Memory** - Persistent history across sessions
- 🔧 **AI Tools** - Autonomous tool selection (vision, location, search, etc.)
- 📊 **Live Dashboard** - Real-time statistics and tool execution monitoring
- 🔒 **Complete Privacy** - All processing happens locally

### Why AILive?

| Feature | Cloud Assistants | AILive |
|---------|------------------|--------|
| **Privacy** | Data sent to servers | 100% on-device |
| **Offline** | Requires internet | Works without network |
| **Cost** | Subscription fees | Free forever |
| **Control** | Closed source | Open source |
| **Customization** | Limited | Fully hackable |

---

## ✨ Features

### Voice & Audio

- ✅ **Wake word detection** with customizable phrase
- ✅ **Continuous listening** mode for natural conversation flow
- ✅ **Text-to-speech** with incremental streaming playback
- ✅ **Intelligent sentence detection** (handles abbreviations like "Dr.", "U.S.")
- ✅ **Debounced TTS** prevents choppy audio overlaps
- ✅ **Audio state machine** (IDLE → LISTENING → PROCESSING → SPEAKING)

### Vision & Camera

- ✅ **Lazy camera initialization** (only starts when toggled ON)
- ✅ **Real-time image classification** via TensorFlow Lite
- ✅ **Vision analysis tool** integrated with AI responses
- ✅ **Lifecycle-aware processing** (pauses when app backgrounded)
- ✅ **Permission-gated** camera access with clear user controls

### AI & Intelligence

- ✅ **On-device LLM** powered by llama.cpp (GGUF model support)
- ✅ **Streaming token generation** with Flow-based architecture
- ✅ **Token buffering** prevents subword garbling
- ✅ **Context management** with conversation history
- ✅ **Error handling** with automatic history rollback on failures
- ✅ **Cancellable generation** preserves conversation integrity

### Tools & Integration

AILive includes multiple AI tools that execute autonomously based on user intent:

1. **VisionAnalysisTool** - Camera-based object detection and scene understanding
2. **LocationTool** - GPS coordinates and geocoding
3. **WebSearchTool** - DuckDuckGo integration for web queries
4. **SentimentAnalysisTool** - Emotional context detection
5. **DeviceControlTool** - System-level operations
6. **MemoryRetrievalTool** - Semantic memory recall
7. **PatternAnalysisTool** - User behavior insights
8. **FeedbackTrackingTool** - Satisfaction monitoring

### User Experience

- ✅ **Offline mode detection** with helpful setup guidance
- ✅ **Fragment state preservation** (dashboard stats survive config changes)
- ✅ **Multi-window support** (split-screen, freeform on Android 14+)
- ✅ **Permission management** with rationale dialogs
- ✅ **Model download wizard** for first-time setup
- ✅ **Material Design** UI with dark mode support
- ✅ **Real-time dashboard** showing tool execution statistics

### Production Ready

- ✅ **Crash reporting** infrastructure (Firebase Crashlytics stub)
- ✅ **ProGuard obfuscation** for release builds
- ✅ **Global exception handler** for production stability
- ✅ **Unit test outlines** for ModelManager, LLMManager, PersonalityEngine
- ✅ **Configuration change handling** (rotation, screen size)
- ✅ **Resource optimization** (minification, shrinking enabled)

---

## 🏗️ Architecture

### High-Level Design

```
┌─────────────────────────────────────────────────────┐
│                   MainActivity                       │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────┐│
│  │Chat Interface│  │  Dashboard   │  │  Settings  ││
│  │(Voice/Text)  │  │  (Fragment)  │  │  Activity  ││
│  └──────┬───────┘  └──────┬───────┘  └─────┬──────┘│
└─────────┼──────────────────┼─────────────────┼───────┘
          │                  │                 │
    ┌─────▼──────────────────▼─────────────────▼──────┐
    │              AILiveCore (Central Hub)            │
    │  ┌───────────────────────────────────────────┐  │
    │  │    PersonalityEngine (AI Orchestrator)    │  │
    │  │  • Tool selection & execution             │  │
    │  │  • Streaming response generation          │  │
    │  │  • Conversation history management        │  │
    │  └───────────────────────────────────────────┘  │
    └───┬──────────┬──────────┬──────────┬────────────┘
        │          │          │          │
   ┌────▼───┐ ┌───▼────┐ ┌───▼────┐ ┌──▼─────────┐
   │  LLM   │ │  TTS   │ │ Camera │ │   Audio    │
   │Manager │ │Manager │ │Manager │ │  Manager   │
   └────┬───┘ └────────┘ └───┬────┘ └──┬─────────┘
        │                    │          │
   ┌────▼─────────┐    ┌─────▼────┐ ┌──▼─────────────┐
   │  llama.cpp   │    │TensorFlow│ │SpeechProcessor │
   │ (GGUF/Q4)    │    │   Lite   │ │WakeWordDetector│
   │              │    │          │ │                │
   │ Downloads/   │    │ Models/  │ │ Android Speech │
   │ *.gguf       │    │ *.tflite │ │  Recognizer    │
   └──────────────┘    └──────────┘ └────────────────┘
```

### Key Components

| Component | File | Purpose |
|-----------|------|---------|
| **MainActivity** | `MainActivity.kt` (1551 lines) | Entry point, UI management, lifecycle coordination |
| **AILiveCore** | `core/AILiveCore.kt` | Central orchestrator for all AI components |
| **PersonalityEngine** | `personality/PersonalityEngine.kt` | Unified AI intelligence with tool calling |
| **LLMManager** | `ai/llm/LLMManager.kt` | llama.cpp integration, streaming generation |
| **CameraManager** | `camera/CameraManager.kt` | CameraX lifecycle, vision analysis |
| **AudioManager** | `audio/AudioManager.kt` | Audio I/O, wake word, TTS coordination |
| **SpeechProcessor** | `audio/SpeechProcessor.kt` | Android SpeechRecognizer wrapper |
| **WakeWordDetector** | `audio/WakeWordDetector.kt` | Wake phrase detection logic |
| **SentenceDetector** | `utils/SentenceDetector.kt` | Smart sentence boundary detection |
| **DashboardFragment** | `ui/dashboard/DashboardFragment.kt` | Real-time tool statistics UI |
| **ModelSetupDialog** | `ui/ModelSetupDialog.kt` | Model download wizard |
| **AILiveApplication** | `AILiveApplication.kt` | Application class with crash reporting |

**Total:** 117 Kotlin files, ~26,000+ lines of production code

### Technology Stack

**Core:**
- **Language:** Kotlin 1.9.22 with Coroutines 1.7.3
- **Min SDK:** Android 13 (API 33) | Target SDK: 35
- **Build System:** Gradle 8.9 with Kotlin DSL

**AI & ML:**
- **LLM Inference:** llama.cpp Android module (official bindings)
- **Model Format:** GGUF (quantized Q4_0, Q4_1, Q8_0)
- **Embeddings:** ONNX Runtime 1.17.1 (BGE-small-en-v1.5)
- **Vision:** TensorFlow Lite (MobileNetV2, EfficientNet)

**Android Jetpack:**
- **Lifecycle:** LiveData, ViewModel, lifecycleScope, repeatOnLifecycle
- **UI:** XML layouts, Material Components 1.11.0
- **Camera:** CameraX 1.3.1 (camera2, lifecycle, view)
- **Database:** Room 2.6.1 for persistent memory
- **Fragment:** Fragment-KTX 1.6.2 with state preservation

**Networking & Services:**
- **HTTP:** OkHttp 4.12.0 with logging interceptor
- **REST:** Retrofit 2.11.0 with Moshi converter
- **JSON:** Moshi 1.15.1 with Kotlin codegen
- **Location:** Play Services Location 21.0.1
- **Caching:** Caffeine 3.1.8 (high-performance in-memory cache)

**Visualization:**
- **Charts:** MPAndroidChart v3.1.0 for dashboard graphs

**Testing (Outlines):**
- **Unit Tests:** JUnit 4.13.2, Mockito 5.11.0
- **Coroutines:** kotlinx-coroutines-test 1.7.3
- **Assertions:** Google Truth 1.4.2

---

## 📥 Installation

### Prerequisites

- **Android Device:** Android 13+ (API 33), 4GB+ RAM
- **Storage:** 5GB free (3-4GB for GGUF models)
- **Permissions:** Camera, Microphone, Location, Storage

### Option 1: Download APK (Recommended)

```bash
# Download latest release
wget https://github.com/Ishabdullah/AILive/releases/download/v1.0/app-cpu-release.apk

# Install via ADB
adb install -r app-cpu-release.apk

# Or manually: Settings > Apps > Install Unknown Apps
```

### Option 2: Build from Source

**Requirements:**
- Android Studio Hedgehog (2023.1.1+)
- JDK 17
- Android SDK 34
- NDK 26.3.11579264 (for llama.cpp)

**Steps:**

```bash
# Clone repository
git clone https://github.com/Ishabdullah/AILive.git
cd AILive

# Build debug APK
./gradlew assembleCpuDebug

# Build release APK (minified, ProGuard enabled)
./gradlew assembleCpuRelease

# Output: app/build/outputs/apk/cpu/debug/app-cpu-debug.apk
```

**Build Variants:**
- `cpuDebug` - CPU-only, no minification (fast builds)
- `cpuRelease` - CPU-only, minified with ProGuard
- `gpuDebug` - OpenCL Adreno GPU acceleration (experimental)
- `gpuRelease` - GPU-accelerated release build

### Option 3: GitHub Actions

Every push triggers automated builds:

1. Go to [Actions](https://github.com/Ishabdullah/AILive/actions)
2. Select latest successful workflow
3. Download `app-cpu-release.apk` artifact

---

## 🚀 Quick Start

### First Launch

1. **Grant Permissions**
   - Camera, Microphone, Location requested on first run
   - Rationale dialogs explain each permission's purpose
   - App works with limited functionality if permissions denied

2. **Download AI Model**
   - If no models detected, ModelSetupDialog appears
   - Choose GGUF model to download (recommended: Qwen2.5-1.5B-Instruct-Q4_0, ~900MB)
   - Models download to `Downloads/*.gguf`
   - Or import existing model via file picker

3. **Customize Settings**
   - Set AI name (default: "AILive")
   - Configure wake phrase (default: "hey ai live")
   - Adjust streaming settings

4. **Start Using**
   - Toggle microphone ON (🎤 button)
   - Say wake phrase: **"Hey AILive"**
   - Speak command or type in text field
   - Toggle camera ON (📷 button) for vision features

### Example Interactions

**Voice Commands:**
```
You: "Hey AILive, what time is it?"
AILive: [Streams response] "It's currently 2:45 PM Pacific Standard Time."

You: "Hey AILive, what do you see?"
AILive: [Activates camera] "I'm analyzing the image... I see a laptop,
        coffee mug, and a notebook on a wooden desk."
```

**Text Commands:**
```
Type: "Tell me about machine learning"
AILive: [Streams token-by-token with TTS]
        "Machine learning is a subset of artificial intelligence that
         enables systems to learn from data without explicit programming..."
```

**Offline Mode:**
```
[Disconnect from Wi-Fi]
AILive: [Shows dialog]
        "Offline mode: Using local models
         Web search unavailable until connection restored."
```

### Troubleshooting

**No models found:**
- Ensure you downloaded a GGUF model (Settings > Download Models)
- Check Downloads folder for `*.gguf` files
- Try manual import via file picker

**Wake word not detected:**
- Verify microphone permission granted
- Check wake phrase matches settings (case insensitive)
- Ensure microphone toggle is ON (🎤 button green)

**Camera not working:**
- Verify camera permission granted
- Toggle camera ON (📷 button)
- Check if another app is using camera

**App crashes on startup:**
- Clear app data: Settings > Apps > AILive > Storage > Clear Data
- Reinstall from APK
- Check logcat for crash details: `adb logcat -s AILiveApp MainActivity`

---

## 🧪 Testing

### Manual Testing Checklist

```bash
# 1. Clean install and first-time setup
adb uninstall com.ailive.cpu
adb install -r app/build/outputs/apk/cpu/debug/app-cpu-debug.apk
adb shell am start -n com.ailive.cpu/.MainActivity
# Expected: Permission dialogs, model setup wizard

# 2. Test wake word detection
adb logcat -c && adb logcat -s MainActivity SpeechProcessor WakeWordDetector
# Say "Hey AILive" near device
# Expected: Logs show wake word detected, state transition to LISTENING_COMMAND

# 3. Test camera lazy initialization
adb logcat | grep -E "Camera|startCamera|stopCamera"
# Toggle camera button OFF → ON → OFF
# Expected: startCamera() only called when toggle ON, not on app launch

# 4. Test offline mode
adb shell svc wifi disable
adb shell am force-stop com.ailive.cpu
adb shell am start -n com.ailive.cpu/.MainActivity
# Expected: Dialog appears if no models, or Toast "Offline mode: Using local models"
adb shell svc wifi enable

# 5. Test fragment state preservation
adb shell am start -n com.ailive.cpu/.MainActivity
# Open dashboard, wait for execution stats to populate
# Toggle dashboard OFF → ON
# Rotate device (Ctrl+F12 in emulator)
# Expected: Stats persist across toggles and rotation

# 6. Test streaming response cancellation
# Send text command, click Cancel mid-stream
adb logcat | grep -E "CancellationException|pending.*turn|Cleaned up"
# Expected: History rollback, pending turns removed
```

### Unit Tests (Outlines Available)

```bash
# Run all tests
./gradlew test

# Run specific test suite
./gradlew testCpuDebugUnitTest

# Generate coverage report
./gradlew jacocoTestReport
# Output: app/build/reports/jacoco/test/html/index.html
```

**Test Coverage:**
- `ModelManagerTest.kt` - 6 test scenarios (initialization, loading, caching, cleanup)
- `LLMManagerTest.kt` - 8 test scenarios (streaming, token limits, context management)
- `PersonalityEngineTest.kt` - 8 test scenarios (tool calling, history, listeners)

*Note: Tests are skeleton implementations with TODO comments. PRs welcome!*

---

## 🗺️ Roadmap

### v1.0 - Current Release ✅

- ✅ Wake word detection with customizable phrase
- ✅ Unified voice/text streaming (no more dual paths)
- ✅ Camera lazy initialization (battery optimization)
- ✅ Fragment state preservation across config changes
- ✅ Offline mode detection and graceful degradation
- ✅ Crashlytics infrastructure (Firebase stub ready)
- ✅ ProGuard obfuscation for release builds
- ✅ Multi-window support (split-screen, freeform)
- ✅ Intelligent sentence detection (handles abbreviations)
- ✅ Streaming TTS with debouncing (smooth audio)
- ✅ History corruption fixes (rollback on error/cancel)

### v1.5 - Quality & Polish (Target: 4-6 weeks)

- [ ] **Unit Tests:** Achieve 60% code coverage
- [ ] **Integration Tests:** Espresso UI tests for critical flows
- [ ] **Vision-Language Model:** Add Qwen-VL for camera descriptions
- [ ] **Advanced Embeddings:** Upgrade to sentence-transformers
- [ ] **Battery Optimization:** Profile and reduce background drain
- [ ] **Onboarding Flow:** Interactive tutorial for first-time users
- [ ] **Error Recovery:** Automatic retry with exponential backoff
- [ ] **Accessibility:** TalkBack support, larger touch targets

### v2.0 - Advanced Features (Target: 3-4 months)

- [ ] **Web Search Tool:** Enhance with fact verification
- [ ] **Cloud Sync (Opt-in):** Backup conversations to user's cloud
- [ ] **Multi-Language:** Support for Spanish, French, German
- [ ] **Custom Model Training:** Fine-tune on user's conversations
- [ ] **Plugin System:** Third-party tool integration API
- [ ] **Voice Cloning:** Personalized TTS voice
- [ ] **Multi-User Profiles:** Family sharing on one device
- [ ] **Play Store Release:** Public beta on Google Play

### Future Considerations

- Advanced memory graphs (knowledge graph visualization)
- Calendar/Contacts integration tools
- Code execution sandbox tool
- Weather forecasting tool
- Math solver with step-by-step explanations
- Note-taking and reminders
- Smart home device control

---

## 🤝 Contributing

AILive is open source and welcomes contributions! Whether you're fixing bugs, adding features, writing tests, or improving documentation—every contribution matters.

### How to Contribute

1. **Fork the repository**
   ```bash
   git clone https://github.com/YOUR_USERNAME/AILive.git
   cd AILive
   ```

2. **Create a feature branch**
   ```bash
   git checkout -b feature/amazing-feature
   ```

3. **Make your changes**
   - Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
   - Add KDoc comments for public APIs
   - Write self-documenting code
   - Keep files under 1500 lines

4. **Test your changes**
   ```bash
   ./gradlew test
   ./gradlew assembleCpuDebug
   ```

5. **Commit with clear message**
   ```bash
   git commit -m "feat: add voice cloning support"
   # Use conventional commits: feat, fix, docs, test, refactor
   ```

6. **Push and open PR**
   ```bash
   git push origin feature/amazing-feature
   # Open Pull Request on GitHub
   ```

### Areas We Need Help

- 🐛 **Bug Fixes** - See [Issues](https://github.com/Ishabdullah/AILive/issues)
- 🧪 **Testing** - Implement unit test TODOs, add integration tests
- 🎨 **UI/UX** - Improve Material Design, add animations
- 📚 **Documentation** - Write tutorials, API docs, code examples
- 🌍 **Internationalization** - Translate UI strings to other languages
- 🔧 **Tools** - Create new AITool implementations (calendar, contacts, etc.)
- ⚡ **Performance** - Profile and optimize battery, memory, CPU usage
- 🤖 **Models** - Integrate new GGUF models, fine-tune existing ones

### Code Style Guidelines

```kotlin
// ✅ Good: Self-documenting, clear intent
private fun checkOfflineMode() {
    val hasNetwork = isNetworkAvailable()
    val hasLLMModel = downloadsDir?.listFiles()?.any {
        it.name.endsWith(".gguf", ignoreCase = true)
    } ?: false

    if (!hasNetwork && !hasLLMModel) {
        showOfflineModeDialog()
    }
}

// ❌ Bad: Unclear, no docs, magic numbers
private fun check() {
    if (!a() && !b()) c()
}
```

**Best Practices:**
- Use descriptive variable names (`hasNetwork` not `flag1`)
- Extract magic numbers to constants (`DEBOUNCE_MS = 300`)
- Add comments for complex logic, not obvious code
- Prefer composition over inheritance
- Use sealed classes for state machines
- Leverage Kotlin null safety (avoid `!!`)

---

## 📄 License

AILive is released under the **Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License (CC BY-NC-SA 4.0)**.

### You May:

- ✅ **Share** - Copy and redistribute in any medium or format
- ✅ **Adapt** - Remix, transform, build upon the material
- ✅ **Personal Use** - Use for learning and personal projects
- ✅ **Research** - Use for academic and non-profit research
- ✅ **Open Source** - Fork and contribute back

### You Must:

- 📝 **Attribution** - Give credit, link to license, indicate changes
- 🚫 **NonCommercial** - Not for commercial purposes without permission
- 🔄 **ShareAlike** - Distribute derivatives under same license

### Commercial Use

For commercial licensing, enterprise deployment, or commercial applications:
- **Email:** ismail.t.abdullah@gmail.com
- **Subject:** "AILive Commercial License Inquiry"

Full license: [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode)

```
Copyright (c) 2025 Ismail Abdullah

This work is licensed under the Creative Commons Attribution-NonCommercial-ShareAlike
4.0 International License. To view a copy of this license, visit:
http://creativecommons.org/licenses/by-nc-sa/4.0/
```

---

## 🙏 Acknowledgments

AILive stands on the shoulders of giants:

- **[llama.cpp](https://github.com/ggerganov/llama.cpp)** - Georgi Gerganov's GGUF inference engine
- **[ONNX Runtime](https://onnxruntime.ai/)** - Microsoft's cross-platform ML framework
- **[Android NNAPI](https://developer.android.com/ndk/guides/neuralnetworks)** - Google's neural network acceleration
- **[CameraX](https://developer.android.com/training/camerax)** - Modern Android camera API
- **[Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)** - JetBrains' async programming
- **[Room Database](https://developer.android.com/training/data-storage/room)** - SQLite persistence layer
- **[DuckDuckGo](https://duckduckgo.com/)** - Privacy-focused search API
- **[Qwen Models](https://huggingface.co/Qwen)** - Alibaba's open-weight LLMs

Special thanks to the open source community for making privacy-preserving AI accessible to everyone.

---

## 📞 Contact & Support

**Developer:** Ismail Abdullah
**Email:** ismail.t.abdullah@gmail.com
**GitHub:** [@Ishabdullah](https://github.com/Ishabdullah)
**HuggingFace:** [@Ishymoto](https://huggingface.co/Ishymoto)

**Project Links:**
- **Issues:** [Report Bugs](https://github.com/Ishabdullah/AILive/issues)
- **Discussions:** [Ask Questions](https://github.com/Ishabdullah/AILive/discussions)
- **CI/CD:** [Build Status](https://github.com/Ishabdullah/AILive/actions)

---

## ⭐ Support the Project

If you find AILive useful, please consider:

- ⭐ **Star this repository** - Increases visibility
- 🐛 **Report bugs** - Helps improve quality
- 💡 **Suggest features** - Shapes the roadmap
- 🔧 **Contribute code** - Accelerates development
- 📢 **Share with others** - Grows the community
- ☕ **Buy me a coffee** - Sustains development

Every contribution, big or small, makes a difference!

---

<div align="center">

**Built with ❤️ for privacy, autonomy, and the open source community**

**Version:** 1.0.0 | **Status:** Production Ready | **Last Updated:** November 2025

[⬆ Back to Top](#ailive-privacy-first-on-device-ai-assistant)

</div>
