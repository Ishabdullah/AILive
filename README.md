# 🧠 AILive - Brain-Inspired AI System for Android

[![License: Non-Commercial](https://img.shields.io/badge/License-Non--Commercial-blue.svg)](LICENSE)
[![Android CI](https://github.com/Ishabdullah/AILive/actions/workflows/android-build.yml/badge.svg)](https://github.com/Ishabdullah/AILive/actions)
[![API Level](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-purple.svg)](https://kotlinlang.org/)

**AILive** is a modular, brain-inspired artificial intelligence system that runs entirely on-device on Android. It implements a cognitive architecture inspired by human neuroscience, coordinating multiple specialized AI agents to provide intelligent, privacy-preserving assistance.

---

## 🎯 What Makes AILive Different?

| Feature | AILive | Cloud AI (GPT-4, Claude) | Other Mobile AI |
|---------|--------|--------------------------|-----------------|
| **Privacy** | 100% On-Device | ❌ Cloud-dependent | ⚠️ Hybrid |
| **Architecture** | Brain-Inspired Multi-Agent | ❌ Monolithic | ❌ Single-model |
| **Safety** | Immutable Policies | ⚠️ Prompt-based | ❌ Limited |
| **Latency** | <100ms | 200-500ms | 50-200ms |
| **Cost** | $0 (after models) | $20/month | $0-10/month |
| **Offline** | ✅ Full capability | ❌ Internet required | ⚠️ Limited |
| **Coordination** | 6+ Specialized Agents | ❌ Single LLM | ❌ N/A |
| **Open Source** | ✅ Full transparency | ❌ Proprietary | ⚠️ Partial |

---

## 🧠 Brain-Inspired Architecture

AILive mirrors the human brain's modular structure:
┌─────────────────────────────────────────────────────────┐
│              META AI (Prefrontal Cortex)                │
│    -  Goal Planning  -  Decision Making  -  Resource Mgmt  │
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
│ (V1 Cortex) │(Hippocampus)│ (Amygdala)  │ (Cerebellum)│
├─────────────┼─────────────┼─────────────┼─────────────┤
│ Language AI │Predictive AI│             │ Safety      │
│ (Wernicke's)│ (DMN)       │             │ Policies    │
├─────────────┼─────────────┤             │             │
│             │ Reward AI   │             │             │
│             │(Basal Gang.)│             │             │
└─────────────┴─────────────┴─────────────┴─────────────┘

### Agent Responsibilities

| Agent | Brain Region | Function | Model | License | Size |
|-------|--------------|----------|-------|---------|------|
| **Meta AI** | Prefrontal Cortex | Planning, orchestration | SmolLM2-360M (Q4) | Apache 2.0 | 271 MB |
| **Visual AI** | Visual Cortex | Object detection | MobileNetV3-Small | Apache 2.0 | 10 MB |
| **Language AI** | Wernicke's Area | Speech recognition | Whisper-Tiny (int8) | MIT | 145 MB |
| **Memory AI** | Hippocampus | Long-term storage | BGE-small-en-v1.5 | MIT | 133 MB |
| **Emotion AI** | Amygdala | Sentiment analysis | DistilBERT-sentiment | Apache 2.0 | 127 MB |
| **Predictive AI** | Default Mode Network | Outcome simulation | Rule-based | N/A | - |
| **Reward AI** | Basal Ganglia | Value learning (TD) | Table-based | N/A | - |
| **Motor AI** | Motor Cortex | Device control | Android APIs | N/A | - |

**Total Storage:** ~1.19 GB  
**Peak RAM:** ~3.9 GB (all agents active)  
**All Models:** 100% Commercial-Safe (MIT + Apache 2.0)

---

## 🚀 Features

### Current (v0.1 - Foundation)

✅ **Complete Cognitive Architecture**
- Multi-agent message bus with priority queuing
- Blackboard state management (shared memory)
- Goal stack planning with dependency resolution
- Resource allocation between agents
- Immutable safety policies

✅ **Motor AI** - Device control with safety validation  
✅ **Meta AI** - Goal planning and decision engine  
✅ **Memory AI** - Vector database with persistence  
✅ **Emotion AI** - Sentiment and urgency detection  
✅ **Predictive AI** - Outcome scenario generation  
✅ **Reward AI** - TD-learning for action values

### Roadmap (v0.2-0.5)

🔄 **Model Integration** (Week 2-3)
- Visual AI: MobileNetV3 object detection
- Language AI: Whisper speech recognition
- Real embeddings: BGE-small-en-v1.5
- LLM: SmolLM2-360M for reasoning

🔄 **UI Development** (Week 4)
- Material Design 3 dashboard
- System health visualization
- Goal management interface

🔄 **Advanced Features** (Week 5+)
- Knowledge Scout (web search)
- Hierarchical task networks
- Experience replay learning

---

## 📱 System Requirements

**Minimum:**
- Android 8.0 (API 26) or higher
- 4 GB RAM
- 2 GB free storage
- ARMv8-A processor

**Recommended:**
- Android 12+ (API 31)
- 8+ GB RAM (Samsung S21+, Pixel 6+)
- 5 GB free storage
- Snapdragon 8 Gen 1+ or equivalent

**Tested On:**
- Samsung Galaxy S24 Ultra (primary target)

---

## 🔧 Installation

### Option 1: Download Pre-built APK (Recommended)

1. Go to [Actions](../../actions) tab
2. Click latest successful workflow
3. Download **ailive-debug.apk** artifact
4. Install on your Android device
5. Grant necessary permissions (Camera, Microphone, Storage)

### Option 2: Build from Source
git clone https://github.com/Ishabdullah/AILive.git
cd AILive
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk

---

## 🧪 Testing

AILive includes comprehensive test scenarios:
val tests = TestScenarios(ailiveCore)
tests.runAllTests()

Test suites:
- Message bus routing
- Sentiment analysis
- Memory storage/recall
- Outcome prediction
- Value learning
- Integrated coordination

---

## 🔒 Privacy & Security

**AILive is privacy-first by design:**

✅ **No Cloud Communication**
- All processing happens on-device
- Zero telemetry or analytics
- No user data ever leaves your phone

✅ **Safety Guarantees**
- Immutable SafetyPolicy (cannot be bypassed)
- Resource limits enforced architecturally
- Permission checks on every action

✅ **Data Ownership**
- You own all memories and data
- Export/delete anytime
- No vendor lock-in

---

## 📦 Model Licenses

All AI models used in AILive are **commercially-licensed** (MIT + Apache 2.0).

For detailed model attribution and license texts, see [CREDITS.md](CREDITS.md).

**Quick Summary:**
- ✅ Whisper (MIT) - Speech recognition
- ✅ SmolLM2 (Apache 2.0) - LLM reasoning
- ✅ MobileNetV3 (Apache 2.0) - Object detection
- ✅ BGE-small (MIT) - Text embeddings
- ✅ DistilBERT (Apache 2.0) - Sentiment analysis

**All models: 100% Commercial-Safe** 🎉

---

## 🤝 Contributing

**This project is open for non-commercial use and contributions!**

We welcome:
- 🐛 Bug reports
- 💡 Feature suggestions
- 📝 Documentation improvements
- 🧪 Test coverage
- 🎨 UI/UX enhancements
- 🔬 Research collaborations

**How to contribute:**
1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Open a Pull Request

---

## 📄 License

**AILive Non-Commercial License v1.0**

✅ **You CAN:**
- Download and use for personal projects
- Modify for learning and research
- Use in academic work (with attribution)
- Contribute improvements back

❌ **You CANNOT:**
- Use for commercial purposes
- Sell or monetize the software
- Include in paid products/services
- Remove attribution

📧 **Commercial licensing available:** ismail.t.abdullah@gmail.com

See [LICENSE](LICENSE) for full terms.

---

## 🙏 Acknowledgments

**Inspiration:**
- SOAR/ACT-R cognitive architectures
- Blackboard systems (classical AI)
- Human brain structure (neuroscience)
- Publish-subscribe pattern (distributed systems)

**Technologies:**
- Kotlin Coroutines (concurrency)
- TensorFlow Lite (ML inference)
- Android Jetpack (framework)
- GitHub Actions (CI/CD)

---

## 📞 Contact

**Project Author:** Ishabdullah (Ismail T. Abdullah)

- 📧 Email: ismail.t.abdullah@gmail.com
- 💼 GitHub: [@Ishabdullah](https://github.com/Ishabdullah)
- 🔗 Project: [AILive](https://github.com/Ishabdullah/AILive)

**For:**
- 💼 Commercial licensing inquiries
- 🤝 Research collaborations
- 🐛 Critical bug reports
- 💡 Partnership opportunities

---

## 📚 Citation

If you use AILive in academic work, please cite:
@software{ailive2025,
author = {Abdullah, Ismail T.},
title = {AILive: Brain-Inspired Multi-Agent AI System for Android},
year = {2025},
url = {https://github.com/Ishabdullah/AILive},
note = {Non-commercial open-source project}
}

---

## 🗺️ Roadmap

**Q4 2025:**
- ✅ Core architecture complete
- 🔄 Model integration (Visual, Language, LLM)
- 🔄 UI development

**Q1 2026:**
- Performance optimization
- Advanced planning algorithms
- Knowledge Scout agent
- Multi-device sync

**Q2 2026:**
- App store release
- Developer SDK
- Plugin ecosystem
- Enterprise features

---

## 🏆 Project Status

![Lines of Code](https://img.shields.io/badge/Lines%20of%20Code-5200%2B-blue)
![Files](https://img.shields.io/badge/Files-43-green)
![Agents](https://img.shields.io/badge/Agents-6%2F8-orange)
![Test Coverage](https://img.shields.io/badge/Tests-6%20scenarios-brightgreen)
![Architecture](https://img.shields.io/badge/Architecture-Brain--Inspired-purple)
![Privacy](https://img.shields.io/badge/Privacy-100%25%20On--Device-red)

**Current Phase:** Foundation Complete ✅ → Model Integration 🔄

---

**Built with ❤️ and 🧠 by a passionate AI software engineer.**

*"The best AI is the one that respects your privacy."*
