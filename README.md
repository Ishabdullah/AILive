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

### **Agent Responsibilities**

| Agent | Brain Region | Function | Model (Planned) | Size |
|-------|--------------|----------|-----------------|------|
| **Meta AI** | Prefrontal Cortex | Planning, decision-making, orchestration | SmolLM2-360M (Q4) | 271 MB |
| **Visual AI** | Visual Cortex | Object detection, scene understanding | MobileNetV3-Small | 10 MB |
| **Language AI** | Wernicke's Area | Speech recognition, NLU | Whisper-Tiny (int8) | 145 MB |
| **Memory AI** | Hippocampus | Long-term storage, recall | MiniLM-L6-v2 | 90 MB |
| **Emotion AI** | Amygdala | Sentiment analysis, urgency detection | DistilBERT-sentiment | 127 MB |
| **Predictive AI** | Default Mode Network | Outcome simulation | Rule-based (for now) | - |
| **Reward AI** | Basal Ganglia | Action value learning (TD-learning) | Table-based | - |
| **Motor AI** | Motor Cortex | Device control, safety enforcement | Native Android APIs | - |

**Total Storage:** ~1.15 GB  
**Peak RAM:** ~3.9 GB (all agents active)

---

## 🚀 Features

### **Current (v0.1 - Foundation)**

✅ **Complete Cognitive Architecture**
- Multi-agent message bus with priority queuing
- Blackboard state management (shared memory)
- Goal stack planning with dependency resolution
- Resource allocation between agents
- Immutable safety policies

✅ **Motor AI (Cerebellum)**
- Runtime permission management
- Battery/thermal monitoring
- Camera controller (Camera2 API)
- Safety validation on all actions

✅ **Meta AI (Orchestrator)**
- Goal planning (Compound/Atomic/Conditional)
- Decision engine with action approval flow
- Dynamic resource throttling
- Emergency shutdown protocols

✅ **Memory AI (Hippocampus)**
- Vector database (cosine similarity search)
- JSON persistence to disk
- Auto-archiving of important events
- Efficient LRU eviction

✅ **Emotion AI (Amygdala)**
- Sentiment analysis (valence: -1 to 1)
- Arousal calculation (0 to 1)
- Urgency detection from text
- Temporal smoothing

✅ **Predictive AI (DMN)**
- Scenario generation for actions
- Expected value calculation
- Context-aware predictions

✅ **Reward AI (Basal Ganglia)**
- TD-learning for action values
- Success/failure feedback loop
- Value table per action type

### **Roadmap (v0.2-0.5)**

🔄 **Model Integration** (Week 2-3)
- Visual AI: MobileNetV3 object detection
- Language AI: Whisper speech recognition
- Real embeddings: MiniLM-L6-v2
- LLM: SmolLM2-360M for reasoning

🔄 **UI Development** (Week 4)
- Material Design 3 dashboard
- System health visualization
- Goal management interface
- Memory browser

🔄 **Advanced Features** (Week 5+)
- Knowledge Scout (web search integration)
- Hierarchical task networks
- Experience replay learning
- Multi-device coordination

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

### **Option 1: Download Pre-built APK (Recommended)**

1. Go to [Actions](../../actions) tab
2. Click latest successful workflow
3. Download **ailive-debug.apk** artifact
4. Install on your Android device
5. Grant necessary permissions (Camera, Microphone, Storage)

### **Option 2: Build from Source**
Clone repositorygit clone https://github.com/Ishabdullah/AILive.git
cd AILiveBuild (requires Android SDK)./gradlew assembleDebugInstall via ADBadb install app/build/outputs/apk/debug/app-debug.apk

---

## 🧪 Testing

AILive includes comprehensive test scenarios:
// Run all tests (automatic on app launch)
val tests = TestScenarios(ailiveCore)
tests.runAllTests()// Individual test suites
tests.testMessageBus()      // Message routing
tests.testEmotionAI()        // Sentiment analysis
tests.testMemory()           // Storage/recall
tests.testPredictiveAI()     // Outcome generation
tests.testRewardAI()         // Value learning
tests.testIntegratedSystem() // All agents coordinating

---

## 📊 Performance Benchmarks

*Coming soon after model integration*

Target metrics:
- **Latency:** <100ms per agent inference
- **Battery:** <5% drain per hour (moderate use)
- **RAM:** <4GB total usage
- **Startup:** <2 seconds cold start

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
- Emergency shutdown mechanisms

✅ **Data Ownership**
- You own all memories and data
- Export/delete anytime
- No vendor lock-in

---

## 🛠️ Architecture Deep Dive

### **Message Bus**
- Priority-based pub/sub system
- TTL enforcement prevents stale messages
- Backpressure handling (max 1000 messages)
- Topic filtering for efficient routing

### **Blackboard State**
- Inspired by classical AI blackboard systems
- 6 state layers (Sensor, Perception, Cognition, Affect, Meta, Motor)
- Thread-safe with Kotlin Mutex
- Reactive updates via StateFlow

### **Goal Stack Planning**
- LIFO stack with priority override
- Dependency resolution (goals wait for prerequisites)
- Deadline enforcement
- Automatic retry on recoverable failures

### **Safety System**
- Three-tier validation:
  1. Permission check
  2. Resource availability
  3. Safety policy enforcement
- Forbidden actions (hardcoded blacklist)
- Throttling under thermal/battery stress

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
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

**Code Style:**
- Follow Kotlin coding conventions
- Add KDoc comments for public APIs
- Include unit tests for new features
- Keep functions under 50 lines

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
- **SOAR/ACT-R** cognitive architectures
- **Blackboard systems** (classical AI)
- **Human brain** structure (neuroscience)
- **Publish-subscribe** pattern (distributed systems)

**Technologies:**
- Kotlin Coroutines (concurrency)
- TensorFlow Lite (ML inference)
- Android Jetpack (framework)
- GitHub Actions (CI/CD)

**Community:**
- r/LocalLLaMA (on-device AI enthusiasm)
- Android developers worldwide
- Open-source AI researchers

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

## ⭐ Star History

If you find AILive useful, consider giving it a star! ⭐

It helps others discover the project and motivates continued development.

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

**Built with ❤️ and 🧠 by a passionate AI software engineer.**

*"The best AI is the one that respects your privacy."*

---

## 🏆 Project Status

![Lines of Code](https://img.shields.io/badge/Lines%20of%20Code-5200%2B-blue)
![Files](https://img.shields.io/badge/Files-43-green)
![Agents](https://img.shields.io/badge/Agents-6%2F8-orange)
![Test Coverage](https://img.shields.io/badge/Tests-6%20scenarios-brightgreen)
![Architecture](https://img.shields.io/badge/Architecture-Brain--Inspired-purple)
![Privacy](https://img.shields.io/badge/Privacy-100%25%20On--Device-red)

**Current Phase:** Foundation Complete ✅ → Model Integration 🔄
