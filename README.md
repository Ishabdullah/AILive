# AILive - Brain-Inspired AI System

**Version:** 0.5.1
**Status:** Phase 2.6+ ✅ | Manual Control UI Added 🎛️
**Platform:** Android 8.0+ (API 26+)
**License:** Non-commercial (See LICENSE)

---

## 🧠 What is AILive?

AILive is an **on-device, brain-inspired artificial intelligence system** that runs entirely on your Android phone. Unlike cloud-based AI assistants, AILive:

- ✅ **100% Private** - All data stays on your device
- ✅ **Zero Latency** - No internet required for core functions
- ✅ **Autonomous Learning** - Continuously improves itself (Phase 6-7)
- ✅ **Proactive Engagement** - Exhibits curiosity and initiative (Phase 6-7)
- ✅ **Open Source** - Fully transparent, commercially-licensed models

---

## 🏗️ Architecture

AILive mimics the human brain with **8 specialized AI agents** coordinating through a central message bus:
┌─────────────────────────────────────────────────────────┐
│              META AI (Prefrontal Cortex)                │
│         Planning -  Decision Making -  Orchestration      │
└─────────────────────────────────────────────────────────┘
↕
┌──────────────────────────────────────┐
│       MESSAGE BUS (Neural Network)   │
│    Priority Queue -  Pub/Sub -  TTL    │
└──────────────────────────────────────┘
↕
┌─────────────┬─────────────┬─────────────┬─────────────┐
│  PERCEPTION │  COGNITION  │   AFFECT    │    MOTOR    │
├─────────────┼─────────────┼─────────────┼─────────────┤
│ Visual AI   │ Memory AI   │ Emotion AI  │ Motor AI    │
│ Language AI │ Predictive  │             │ Safety      │
│             │ Reward AI   │             │ Policies    │
└─────────────┴─────────────┴─────────────┴─────────────┘

### The 8 AI Agents

| Agent | Function | Model | Status |
|-------|----------|-------|--------|
| **Meta AI** | Planning & orchestration | SmolLM2-360M | Placeholder |
| **Visual AI** | Object detection | MobileNetV3 | Placeholder |
| **Language AI** | Speech recognition | Whisper-Tiny | Placeholder |
| **Memory AI** | Long-term storage & recall | BGE-small | Placeholder |
| **Emotion AI** | Sentiment analysis | DistilBERT | Placeholder |
| **Predictive AI** | Outcome forecasting | Rule-based | Working ✅ |
| **Reward AI** | Value learning | Table-based | Working ✅ |
| **Motor AI** | Device control & safety | Android APIs | Working ✅ |

---

## 🚀 Quick Start

### Option A: Download Pre-built APK

1. Go to [GitHub Actions](https://github.com/Ishabdullah/AILive/actions)
2. Click latest successful build (green checkmark ✅)
3. Download `ailive-debug` artifact
4. Extract `app-debug.apk`
5. Install on your Android phone
6. Grant required permissions
7. Open app - you'll see "AILive Running..." ✅

### Option B: Build from Source
Clone repositorygit clone https://github.com/Ishabdullah/AILive.git
cd AILiveBuild with Gradle./gradlew assembleDebugInstall on deviceadb install app/build/outputs/apk/debug/app-debug.apkView logsadb logcat | grep "AILive"

### Option C: Build on Android with Termux
Install Termux from F-DroidInstall dependenciespkg install git gradle openjdk-17Clone and buildgit clone https://github.com/Ishabdullah/AILive.git
cd AILive
gradle assembleDebugCopy APK to Downloads and install manuallycp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/View logslogcat | grep "AILive"

---

## 📊 Current Status

**Phase 1: Foundation Architecture** ✅ **COMPLETE**

- ✅ All 6 agents implemented and operational
- ✅ Message bus coordination
- ✅ State management (blackboard pattern)
- ✅ Integration tests (6 scenarios)
- ✅ CI/CD pipeline (GitHub Actions)

**Phase 2.1: TensorFlow Lite Vision** ✅ **COMPLETE**

- ✅ ModelManager with GPU acceleration
- ✅ MobileNetV2 integration (1000 ImageNet classes)
- ✅ Real-time image classification pipeline
- ✅ 13.3MB model running on Adreno 750 GPU

**Phase 2.2: Camera Integration** ⚠️ **PARTIAL**

- ✅ Camera preview working
- ✅ CameraX integration
- ⚠️ ImageAnalysis callback issue (deferred)

**Phase 2.3: Audio Integration** ✅ **COMPLETE** (Oct 28, 2025)

- ✅ AudioManager for microphone capture (16kHz PCM)
- ✅ SpeechProcessor with Android SpeechRecognizer
- ✅ WakeWordDetector ("Hey AILive")
- ✅ CommandRouter for natural language parsing
- ✅ Voice command routing to all 6 agents
- ✅ Real-time transcription display
- ✅ Continuous listening with auto-retry

**Phase 2.4: Text-to-Speech Responses** ✅ **COMPLETE** (Oct 28, 2025)

- ✅ TTSManager with Android TTS engine
- ✅ 6 unique agent voice personalities (pitch + speed variations)
- ✅ Audio feedback on wake word detection ("Yes?")
- ✅ Voice responses for all commands
- ✅ TTS state monitoring in UI
- ✅ Priority-based speech queue
- ✅ Full voice conversation loop

**Phase 2.6: Intelligent Language Generation** ✅ **COMPLETE** (Oct 29, 2025)

- ✅ ONNX Runtime integration for LLM inference
- ✅ LLMManager with TinyLlama-1.1B support
- ✅ Intelligent AI-generated responses (no more hardcoded text!)
- ✅ Agent-specific personality prompts
- ✅ Context-aware conversation
- ✅ Fallback system when model unavailable
- ✅ CPU-optimized inference (4 threads, 2-3s per response)

**What Works Now:**
- Say "Hey AILive" → AI responds "Yes?" with audio
- Ask real questions → Get intelligent AI-generated answers
- **NEW:** Manual control panel with toggle buttons (mic, camera, test)
- **NEW:** Text input field to send commands without voice
- 6 agents with unique voices and personalities
- Full voice conversation with context understanding
- Vision pipeline ready (TensorFlow Lite)
- All 6 AI agents communicating via MessageBus

**Manual Controls (NEW in v0.5.1):**
- 🎤 **MIC ON/OFF** - Toggle microphone to control voice input
- 📷 **CAM ON/OFF** - Toggle camera to control vision pipeline
- 🧪 **TEST** - Quick test button (sends "what do you see")
- 📝 **Text Input** - Type commands directly without voice recognition
- All controls accessible in top-right and bottom-center of UI

---

## 🤖 AI Models (All Commercially Licensed)

| Model | Purpose | Size | License | Status |
|-------|---------|------|---------|--------|
| TinyLlama-1.1B-Chat | Language generation | 637 MB | Apache 2.0 ✅ | **ACTIVE (Phase 2.6)** |
| MobileNetV3-Small | Object detection | 13 MB | Apache 2.0 ✅ | **ACTIVE (Phase 2.1)** |
| Whisper-Tiny | Speech recognition | 39 MB | MIT ✅ | Available |
| BGE-small-en-v1.5 | Text embeddings | 133 MB | MIT ✅ | Available |
| DistilBERT-sentiment | Emotion analysis | 66 MB | Apache 2.0 ✅ | Available |

**Total Models:** 888 MB
**Currently Active:** 650 MB (TinyLlama + MobileNetV3)
**Commercial Use:** 100% permitted for all models ✅

**Note:** See [models/MODEL_SETUP.md](models/MODEL_SETUP.md) for model download instructions.

---

## 🗺️ Roadmap

- **Phase 1:** Foundation Architecture ✅ (Complete Oct 27, 2025)
- **Phase 2.1:** TensorFlow Lite Vision ✅ (Complete Oct 27, 2025)
- **Phase 2.2:** Camera Integration ⚠️ (Partial - deferred)
- **Phase 2.3:** Audio Integration ✅ (Complete Oct 28, 2025)
- **Phase 2.4:** Text-to-Speech Responses ✅ (Complete Oct 28, 2025)
- **Phase 2.6:** Intelligent Language Generation ✅ (Complete Oct 29, 2025)
- **Phase 2.6+:** Manual Control UI ✅ (Complete Oct 29, 2025)
- **Phase 2.7:** Vision-Language Integration 🎯 (Next)
- **Phase 2.5:** Custom Wake Word Training 🎤 (Planned)
- **Phase 3:** Enhanced UI & Visualization 📊 (Planned)
- **Phase 4:** Self-Training System 🧪 (Planned Q1 2026)
- **Phase 5:** Artificial Curiosity Framework 💭 (Planned Q2 2026)

See [EXECUTIVE_HANDOFF.md](EXECUTIVE_HANDOFF.md) for complete roadmap details.

---

## 📖 Documentation

- **[EXECUTIVE_HANDOFF.md](EXECUTIVE_HANDOFF.md)** - Complete project overview and handoff guide
- **[CREDITS.md](CREDITS.md)** - Model attributions and licenses
- **[LICENSE](LICENSE)** - Non-commercial license terms

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
- TensorFlow Lite (Phase 2)
- ONNX Runtime (Phase 2)

**Build:**
./gradlew assembleDebug      # Build debug APK
./gradlew test                # Run unit tests
./gradlew assembleRelease     # Build release APK

---

## 🐛 Known Issues

- Models not integrated yet (Phase 2)
- Basic UI only - full dashboard coming in Phase 3
- Placeholder AI logic - will be replaced with real models

See [EXECUTIVE_HANDOFF.md](EXECUTIVE_HANDOFF.md) for complete list.

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

Models from: OpenAI, Hugging Face, Google Research, BAAI.

---

**Built with ❤️ and 🧠 by an AI engineer who believes the best AI respects your privacy.**

*"The future of AI is local, autonomous, and truly intelligent."*
