# AILive - Brain-Inspired AI System

**Version:** 0.1.1  
**Status:** Phase 1 Complete ✅ | Phase 2 Starting 🔄  
**Platform:** Android 8.0+  
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

- ✅ 5,200+ lines of Kotlin code
- ✅ All 8 agents implemented
- ✅ Message bus coordination
- ✅ Memory system with vector database
- ✅ Safety policies enforcement
- ✅ Integration tests (6 scenarios)
- ✅ Basic UI (status display)
- ✅ CI/CD pipeline (GitHub Actions)
- ✅ All 5 AI models downloaded (727 MB)

**Phase 2: Model Integration** 🔄 **STARTING NOW**

- Integrate TensorFlow Lite
- Integrate ONNX Runtime
- Integrate llama.cpp
- Replace placeholder AI with real models
- Performance optimization

---

## 🤖 AI Models (All Commercially Licensed)

| Model | Purpose | Size | License | Status |
|-------|---------|------|---------|--------|
| Whisper-Tiny | Speech recognition | 75 MB | MIT ✅ | Downloaded |
| SmolLM2-360M | Language understanding | 259 MB | Apache 2.0 ✅ | Downloaded |
| MobileNetV3-Small | Object detection | 10 MB | Apache 2.0 ✅ | Downloaded |
| BGE-small-en-v1.5 | Text embeddings | 127 MB | MIT ✅ | Downloaded |
| DistilBERT-sentiment | Emotion analysis | 256 MB | Apache 2.0 ✅ | Downloaded |

**Total:** 727 MB  
**Commercial Use:** 100% permitted for all models ✅

---

## 🗺️ Roadmap

- **Phase 1:** Foundation Architecture ✅ (Complete)
- **Phase 2:** Model Integration 🔄 (Starting Oct 28, 2025)
- **Phase 3:** UI Development 📱 (Planned Nov 2025)
- **Phase 4:** Advanced Features 🚀 (Planned Dec 2025)
- **Phase 5:** Performance Optimization 📊 (Planned Dec 2025)
- **Phase 6:** Self-Training System 🧪 (Planned Q1 2026)
- **Phase 7:** Artificial Desire Framework 💭 (Planned Q2 2026)

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
