# AILive - Unified AI Intelligence System

**Version:** 0.7.5-beta
**Status:** ⚠️ 75% Complete - Phase 7 In Progress (2 Critical Bugs)
**Platform:** Android 8.0+ (API 26+)
**License:** Non-commercial (See LICENSE)
**Latest Build:** [Download APK](https://github.com/Ishabdullah/AILive/actions/runs/18956424882)

---

## 🧠 What is AILive?

AILive is an **on-device, unified AI intelligence system** that runs entirely on your Android phone. Unlike cloud-based AI assistants, AILive features:

- ✅ **100% Private** - All data stays on your device
- ✅ **Zero Latency** - No internet required for core functions
- ✅ **Unified Intelligence** - One cohesive personality with PersonalityEngine
- ✅ **Tool-Based Architecture** - Seamless capability access through 6 specialized tools
- ✅ **Continuous Learning** - Pattern analysis, memory storage, feedback tracking
- ✅ **Real-Time Dashboard** - Live monitoring of tool activity and performance
- ✅ **Data Visualization** - Interactive charts for patterns and feedback

---

## 🏗️ Architecture

AILive features a **PersonalityEngine** (606 lines) that provides unified intelligence through 6 specialized tools:

```
┌─────────────────────────────────────────────────────────┐
│            PersonalityEngine (Unified AI)               │
│   ONE personality - ONE voice - Coherent responses      │
│   11,425 lines of functional code                       │
└─────────────────────────────────────────────────────────┘
                          ↕
┌─────────────────────────────────────────────────────────┐
│                    Tool Registry                         │
│   analyze_sentiment | control_device | retrieve_memory  │
│   analyze_vision | analyze_patterns | track_feedback    │
│   Total: ~2,200 lines across 6 tools                    │
└─────────────────────────────────────────────────────────┘
                          ↕
┌─────────────────────────────────────────────────────────┐
│              MESSAGE BUS (Event Coordination)            │
│         Priority Queue - Pub/Sub - Real-Time Events     │
└─────────────────────────────────────────────────────────┘
                          ↕
┌──────────────────────────────────────────────────────────┐
│                  Core Systems                             │
│   LLMManager | TTSManager | CameraManager | StateManager│
└──────────────────────────────────────────────────────────┘
```

### The 6 AI Tools

| Tool | Lines | Function | Status |
|------|-------|----------|--------|
| **analyze_patterns** | 444 | User behavior patterns, time-based predictions | ✅ Active |
| **track_feedback** | 399 | User feedback tracking, satisfaction analysis | ✅ Active |
| **retrieve_memory** | 274 | On-device memory storage & JSON-based search | ✅ Active |
| **control_device** | 287 | Android device control (flashlight, notifications) | ✅ Active |
| **analyze_vision** | ~180 | Computer vision & image analysis framework | ✅ Active |
| **analyze_sentiment** | ~160 | Emotion/sentiment detection from text | ✅ Active |

**Total Tool Code**: ~1,744 lines of substantial implementations (not stubs)

**Architecture Note**: Some tools (SentimentAnalysisTool, DeviceControlTool, MemoryRetrievalTool) use legacy AI agents (EmotionAI, MotorAI, MemoryAI) as backend engines. This hybrid approach provides:
- Unified interface through PersonalityEngine
- Battle-tested capabilities from legacy agents
- Consistent UX without requiring complete rewrites

---

## 🚀 Quick Start

### ⚠️ Important: Known Issues

**Before installing, be aware:**
- ❌ Model downloads currently fail (missing storage permission)
- ❌ Model import causes crash (GGUF vs ONNX format issue)
- ✅ Dashboard and visualization features work perfectly

**Recommendation:** Wait for next release with fixes, or see [PHASE-7-HANDOFF.md](PHASE-7-HANDOFF.md) to implement fixes yourself.

### Download Pre-built APK

1. **Latest Build:** [Download APK here](https://github.com/Ishabdullah/AILive/actions/runs/18956424882)
2. Or go to [GitHub Actions](https://github.com/Ishabdullah/AILive/actions)
3. Click latest successful build (green checkmark ✅)
4. Download `ailive-debug` artifact
5. Extract `app-debug.apk`
6. Install on your Android phone:
   ```bash
   adb install app-debug.apk
   ```
7. Grant required permissions (camera, microphone)
8. **Note:** Model functionality not working yet - see [KNOWN-ISSUES.md](KNOWN-ISSUES.md)

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

## 📊 What's Actually Working

### ✅ Completed Features (Phase 1-6.2)

**Core Intelligence**
- ✅ PersonalityEngine unified orchestration (606 lines)
- ✅ LLMManager for inference (295 lines)
- ✅ TTSManager for voice output (308 lines)
- ✅ MessageBus event coordination (232 lines)
- ✅ State management system

**6 Specialized Tools**
- ✅ PatternAnalysisTool - Behavior patterns and predictions
- ✅ FeedbackTrackingTool - User satisfaction tracking
- ✅ MemoryRetrievalTool - Persistent memory storage
- ✅ DeviceControlTool - Android API integration
- ✅ VisionAnalysisTool - Camera and image analysis
- ✅ SentimentAnalysisTool - Emotion detection

**Data Persistence**
- ✅ JSON storage for patterns, feedback, memories
- ✅ File system based storage
- ✅ Cross-session persistence

**User Interface (Phase 6)**
- ✅ Real-time tool dashboard (DashboardFragment - 267 lines)
- ✅ Live status cards for all 6 tools
- ✅ Execution statistics (total, success rate, active count)
- ✅ Auto-refresh every 2 seconds
- ✅ Material Design 3 dark theme
- ✅ Pattern visualizations (bar + pie charts)
- ✅ Feedback visualizations (line + bar charts)
- ✅ Auto-generated test data

**Performance**
- ✅ LLM optimization (maxTokens: 80, temperature: 0.9)
- ✅ NNAPI GPU acceleration framework (code exists, needs testing)
- ✅ Fallback response system
- ✅ Error handling

---

## 🚧 In Progress / Needs Enhancement

**ML Model Integration**
- ⚠️ Model files need to be downloaded/integrated
- ⚠️ GPU acceleration needs performance testing
- ⚠️ Some tool capabilities are basic implementations

**Advanced Features**
- ⚠️ Vector search for memory (framework exists, needs BGE embeddings)
- ⚠️ Advanced pattern recognition algorithms
- ⚠️ Voice personality system
- ⚠️ Cross-session learning

**Production Hardening**
- ⚠️ More comprehensive error handling
- ⚠️ Edge case testing
- ⚠️ Battery optimization validation
- ⚠️ Security audit

---

## 📈 Development Progress

**Current Completion**: ~75%

| Phase | Status | Description |
|-------|--------|-------------|
| Phase 1-3 | ✅ Complete | Foundation & initial architecture |
| Phase 4 | ✅ Complete | Performance optimization |
| Refactoring | ✅ Complete | Multi-agent → Unified intelligence |
| Phase 5 | ✅ Complete | Tool expansion (6 tools implemented) |
| Phase 6.1 | ✅ Complete | Real-time dashboard |
| Phase 6.2 | ✅ Complete | Data visualization with charts |
| Phase 6.3-6.4 | 🔄 Planned | Interactive features & polish |
| Phase 7 | ⚠️ 75% Complete | Model download system (2 critical bugs) |

**Phase 7 Status:**
- ✅ Model download infrastructure (ModelDownloadManager)
- ✅ Model setup UI dialogs (ModelSetupDialog)
- ✅ MainActivity integration with ActivityResultLauncher
- ✅ HuggingFace download URLs configured
- ✅ File picker for model import
- ❌ **CRITICAL BUG #1:** App crashes after importing model (GGUF vs ONNX format mismatch)
- ❌ **CRITICAL BUG #2:** Downloads fail (missing runtime storage permission)

**Next Developer:**  Start with [PHASE-7-HANDOFF.md](PHASE-7-HANDOFF.md)

**For detailed history**: See [SESSION-6-SUMMARY.md](SESSION-6-SUMMARY.md) and [KNOWN-ISSUES.md](KNOWN-ISSUES.md)

---

## 🎯 Key Features

### Unified Intelligence
- **ONE personality** - Coherent responses across all interactions
- **ONE voice** - Consistent TTS output
- **Contextual awareness** - Memory of past conversations
- **Tool coordination** - Seamless capability access

### Real-Time Dashboard
- **Live monitoring** - See tool execution in real-time
- **Status indicators** - Color-coded states (Ready, Executing, Success, Error)
- **Statistics** - Total tools, active count, executions, success rate
- **Auto-refresh** - Updates every 2 seconds

### Data Visualization
- **Pattern Charts** - Activity by time of day (bar chart)
- **Request Analysis** - Top 5 common requests (pie chart)
- **Satisfaction Tracking** - User feedback over time (line chart)
- **Performance Metrics** - Intent performance by tool (bar chart)

### Privacy & Performance
- **100% On-Device** - No cloud dependencies
- **Local Storage** - JSON files in app directory
- **Fast Inference** - Optimized LLM with 80-token responses
- **Battery Conscious** - Efficient processing

---

## 📚 Documentation

### Essential Reading
- **README.md** - This file (overview & quickstart)
- **[DEVELOPMENT_HISTORY.md](DEVELOPMENT_HISTORY.md)** - Complete project history
- **[AUDIT_VERIFICATION_REPORT.md](AUDIT_VERIFICATION_REPORT.md)** - Codebase audit findings
- **[CHANGELOG.md](CHANGELOG.md)** - Version history

### Technical Guides
- **[PERSONALITY_ENGINE_DESIGN.md](PERSONALITY_ENGINE_DESIGN.md)** - Architecture details
- **[REFACTORING_INTEGRATION_GUIDE.md](REFACTORING_INTEGRATION_GUIDE.md)** - Migration guide
- **[LLM_QUANTIZATION_GUIDE.md](LLM_QUANTIZATION_GUIDE.md)** - Model optimization
- **[QUICK_TEST_GUIDE.md](QUICK_TEST_GUIDE.md)** - Testing instructions
- **[DOWNLOAD_AND_TEST.md](DOWNLOAD_AND_TEST.md)** - Installation guide

### Archived Documentation
- **docs/archive/phases/** - Historical phase documentation
- **docs/archive/** - Legacy session handoffs
- **logs/** - Debug reports

---

## 🛠️ Technical Stack

**Language**: Kotlin
**Platform**: Android 8.0+ (API 26+)
**Build System**: Gradle 8.9
**Min SDK**: 26
**Target SDK**: 34

**Key Dependencies**:
- ONNX Runtime 1.16.0 (ML inference)
- TensorFlow Lite 2.14.0 (ML models)
- CameraX 1.3.1 (Camera integration)
- MPAndroidChart 3.1.0 (Data visualization)
- Kotlin Coroutines 1.7.3 (Async operations)
- Material Design 3 (UI components)

---

## 🔬 Testing the Dashboard

After installation:

1. **Open App** - Launch AILive
2. **Tap Orange FAB** - Top right corner to open dashboard
3. **View Tool Status** - See all 6 tools with real-time status
4. **Scroll Down** - View "Data Insights" section
5. **Check Charts** - Pattern and feedback visualizations
6. **Observe Updates** - Dashboard refreshes every 2 seconds

**Test Data**: Auto-generated on first dashboard load (50 patterns, 40 feedback entries)

---

## 🤝 Contributing

AILive is under active development. See [DEVELOPMENT_HISTORY.md](DEVELOPMENT_HISTORY.md) for current priorities.

### Current Focus
- Enabling GPU acceleration
- Integrating ML model files
- Enhancing tool capabilities
- Production hardening

---

## 📝 License

Non-commercial use only. See [LICENSE](LICENSE) for details.

All ML models used are commercially-licensed and open source.

---

## 🙏 Credits

See [CREDITS.md](CREDITS.md) for acknowledgments and attributions.

---

## 📞 Links

- **Repository**: https://github.com/Ishabdullah/AILive
- **Issues**: https://github.com/Ishabdullah/AILive/issues
- **Actions**: https://github.com/Ishabdullah/AILive/actions

---

## ⚡ Quick Stats

- **Total Codebase**: 11,425 lines (verified October 30, 2025)
- **Kotlin Files**: 63 files
- **Completion**: ~70%
- **Tools Implemented**: 6/6 (100%)
- **Core Systems**: Functional
- **UI Components**: Complete (Phase 6.1 + 6.2)

---

**Last Updated**: October 30, 2025 (Version 0.7.0-beta)

**Status**: Active Development - Ready for Testing

---

*For a complete understanding of AILive's evolution, architecture decisions, and implementation details, see [DEVELOPMENT_HISTORY.md](DEVELOPMENT_HISTORY.md)*
