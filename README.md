# AILive - Brain-Inspired AI System

**Version:** 1.0.0-beta (Phase 5 Part 3)
**Status:** PersonalityEngine + 6 Tools ✅ | Unified Intelligence Active 🧠
**Platform:** Android 8.0+ (API 26+)
**License:** Non-commercial (See LICENSE)

---

## 🧠 What is AILive?

AILive is an **on-device, unified AI intelligence system** that runs entirely on your Android phone. Unlike cloud-based AI assistants or separate agent systems, AILive features:

- ✅ **100% Private** - All data stays on your device
- ✅ **Zero Latency** - No internet required for core functions
- ✅ **Unified Intelligence** - One cohesive personality, not separate agents
- ✅ **Tool-Based Architecture** - Seamless capability access through PersonalityEngine
- ✅ **Continuous Learning** - Pattern analysis, memory storage, feedback tracking
- ✅ **Open Source** - Fully transparent, commercially-licensed models

---

## 🏗️ Architecture

AILive features a **PersonalityEngine** that provides unified intelligence through 6 specialized tools:

```
┌─────────────────────────────────────────────────────────┐
│            PersonalityEngine (Unified AI)               │
│   ONE personality - ONE voice - Coherent responses      │
└─────────────────────────────────────────────────────────┘
                          ↕
┌─────────────────────────────────────────────────────────┐
│                    Tool Registry                         │
│   analyze_sentiment | control_device | retrieve_memory  │
│   analyze_vision | analyze_patterns | track_feedback    │
└─────────────────────────────────────────────────────────┘
                          ↕
┌─────────────────────────────────────────────────────────┐
│              MESSAGE BUS (Neural Network)                │
│         Priority Queue - Pub/Sub - Event Flow           │
└─────────────────────────────────────────────────────────┘
                          ↕
┌──────────────────────────────────────────────────────────┐
│                  Legacy Agents (Tools)                   │
│   EmotionAI | MotorAI | MemoryAI | VisionAI | etc.     │
└──────────────────────────────────────────────────────────┘
```

### The 6 AI Tools

| Tool | Function | Technology | Status |
|------|----------|------------|--------|
| **analyze_sentiment** | Emotion/sentiment analysis | EmotionAI | Active ✅ |
| **control_device** | Device control & safety | MotorAI + Android APIs | Active ✅ |
| **retrieve_memory** | On-device memory storage & search | JSON storage + keyword search | Active ✅ |
| **analyze_vision** | Computer vision & object detection | MobileNetV3 + ONNX | Active ✅ |
| **analyze_patterns** | User behavior predictions | Pattern analysis + time-based | Active ✅ |
| **track_feedback** | Learning from user reactions | Feedback tracking + satisfaction | Active ✅ |

---

## 🚀 Quick Start

### Download Pre-built APK

1. Go to [GitHub Actions](https://github.com/Ishabdullah/AILive/actions)
2. Click latest successful build (green checkmark ✅)
3. Download `ailive-debug` or `ailive-release` artifact
4. Extract `app-debug.apk` or `app-release.apk`
5. Install on your Android phone
6. Grant required permissions (camera, microphone, storage)
7. Say "Hey AILive" to start conversation

### Build from Source

```bash
# Clone repository
git clone https://github.com/Ishabdullah/AILive.git
cd AILive

# Build with Gradle
./gradlew assembleDebug

# Install on device
adb install app/build/outputs/apk/debug/app-debug.apk

# View logs
adb logcat | grep "AILive"
```

---

## 📊 Current Status

**Phase 1-3: Foundation & Refactoring** ✅ **COMPLETE**

- ✅ 6 specialized agents implemented
- ✅ Message bus coordination
- ✅ State management system
- ✅ Camera and audio integration
- ✅ Tool-based architecture design

**Phase 4: Performance Optimization** ✅ **COMPLETE** (Oct 29, 2025)

- ✅ LLM optimizations (80 tokens, 0.9 temperature, NNAPI GPU)
- ✅ Intent-based fallback system (varied responses)
- ✅ Regression fixes (no more repetitive responses)
- ✅ Response quality improvements

**Phase 5: Tool Expansion** ✅ **COMPLETE** (Oct 30, 2025)

**Part 1: Vision Analysis**
- ✅ VisionAnalysisTool with real-time object detection
- ✅ Frame buffering in CameraManager
- ✅ ONNX-based vision processing

**Part 2: Vision Integration & Regression Fix**
- ✅ VisionAnalysisTool registration in MainActivity
- ✅ Critical regression fix (LLMManager fallback)
- ✅ Restored varied, contextual responses

**Part 3: Advanced Tools**
- ✅ PatternAnalysisTool - User behavior predictions
  - Time-based patterns (morning/afternoon/evening habits)
  - Frequency analysis (most common requests)
  - Sequential patterns (what follows what)
  - Stores in user_patterns.json (max 100 entries)
- ✅ MemoryRetrievalTool Enhancement - Real on-device storage
  - Keyword-based memory search with relevance scoring
  - Store, retrieve, clear operations
  - Stores in memories.json (max 200 entries)
- ✅ FeedbackTrackingTool - Learning from user reactions
  - Tracks positive/negative feedback, corrections, preferences
  - Analyzes satisfaction rates and trends
  - Intent-based performance tracking
  - Stores in user_feedback.json (max 500 entries)

**What Works Now:**
- ✅ Unified AI personality across all interactions
- ✅ Natural conversation with context understanding
- ✅ Voice interaction ("Hey AILive" wake word)
- ✅ Visual perception through camera
- ✅ Pattern learning from user behavior
- ✅ On-device memory storage and recall
- ✅ Feedback-based improvement
- ✅ Device control capabilities
- ✅ Emotional context awareness
- ✅ All 6 tools working seamlessly together

---

## 🤖 AI Models (All Commercially Licensed)

| Model | Purpose | Size | License | Status |
|-------|---------|------|---------|--------|
| SmolLM2-360M-INT8 | Language generation | 365 MB | Apache 2.0 ✅ | Ready (excluded from git) |
| MobileNetV3-Small | Object detection | 13 MB | Apache 2.0 ✅ | **ACTIVE** |

**Note:** Large model files (>100MB) are excluded from git. Download separately and place in `app/src/main/assets/models/`.

---

## 🗺️ Roadmap

- **Phase 1-3:** Foundation & Refactoring ✅ (Complete)
- **Phase 4:** Performance Optimization ✅ (Complete Oct 29, 2025)
- **Phase 5:** Tool Expansion ✅ (Complete Oct 30, 2025)
  - Part 1: Vision Analysis ✅
  - Part 2: Integration & Regression Fix ✅
  - Part 3: Advanced Tools ✅
- **Phase 6:** UI/UX Improvements 🎯 (Next - Planned)
  - Visual dashboard for tool activity
  - Real-time pattern visualization
  - Memory browser interface
  - Feedback indicators
- **Phase 7:** Advanced Features 🚀 (Q1 2026)
  - Proactive suggestions based on patterns
  - Multi-turn conversation memory
  - Cross-tool coordination
  - Adaptive learning rates

See [PHASE5_TOOL_EXPANSION.md](PHASE5_TOOL_EXPANSION.md) for complete Phase 5 details.

---

## 📖 Documentation

### Main Documentation
- **[README.md](README.md)** - This file
- **[CHANGELOG.md](CHANGELOG.md)** - Version history
- **[CREDITS.md](CREDITS.md)** - Model attributions and licenses
- **[LICENSE](LICENSE)** - Non-commercial license terms

### Technical Documentation
- **[PHASE5_TOOL_EXPANSION.md](PHASE5_TOOL_EXPANSION.md)** - Tool expansion implementation details
- **[PHASE4_COMPLETE.md](PHASE4_COMPLETE.md)** - Performance optimization summary
- **[PERSONALITY_ENGINE_DESIGN.md](PERSONALITY_ENGINE_DESIGN.md)** - Unified intelligence architecture
- **[REFACTORING_INTEGRATION_GUIDE.md](REFACTORING_INTEGRATION_GUIDE.md)** - Agent-to-tool migration guide
- **[LLM_QUANTIZATION_GUIDE.md](LLM_QUANTIZATION_GUIDE.md)** - Model optimization guide
- **[QUICK_TEST_GUIDE.md](QUICK_TEST_GUIDE.md)** - Testing instructions

### Archived Documentation
See [docs/archive/](docs/archive/) for historical session handoffs and phase documentation.

---

## 🛠️ Development

**Requirements:**
- Android 8.0+ (API 26+)
- JDK 17
- Gradle 8.0.2
- Android Studio Hedgehog OR Termux

**Tech Stack:**
- Kotlin 1.9.0
- Kotlin Coroutines
- Material Design 3
- TensorFlow Lite
- ONNX Runtime
- CameraX
- Android Speech APIs

**Build:**
```bash
./gradlew assembleDebug      # Build debug APK
./gradlew test                # Run unit tests
./gradlew assembleRelease     # Build release APK (requires keystore)
```

**Useful Scripts:**
See [scripts/](scripts/) directory for development utilities.

---

## 🎯 Key Features

### Unified Intelligence
- ONE cohesive personality, not separate agents
- Seamless tool integration
- Context-aware responses
- Natural conversation flow

### Privacy-First
- 100% on-device processing
- No cloud dependencies
- All data stored locally
- Full user control

### Continuous Learning
- Pattern recognition from user behavior
- Memory storage for context
- Feedback-based improvements
- Adaptive responses

### Tool-Based Architecture
- 6 specialized tools
- Easy to extend
- Clear separation of concerns
- Modular design

---

## 📞 Contact

**Project Lead:** Ishabdullah (Ismail T. Abdullah)
**Email:** ismail.t.abdullah@gmail.com
**GitHub:** [@Ishabdullah](https://github.com/Ishabdullah)

**Repository:** https://github.com/Ishabdullah/AILive
**Issues:** https://github.com/Ishabdullah/AILive/issues
**Discussions:** https://github.com/Ishabdullah/AILive/discussions

---

## 📄 License

**Non-commercial use only.** See [LICENSE](LICENSE) for details.

All AI models are commercially licensed (MIT + Apache 2.0) - see [CREDITS.md](CREDITS.md).

---

## 🏆 Acknowledgments

Inspired by ACT-R and SOAR cognitive architectures, human neuroscience, and the r/LocalLLaMA community.

Models from: Hugging Face, Google Research, BAAI.

Special thanks to Claude Code for development assistance.

---

**Built with ❤️ and 🧠 by an AI engineer who believes the best AI respects your privacy.**

*"The future of AI is local, unified, and truly intelligent."*
