# Changelog

All notable changes to AILive will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.5.0] - 2025-10-29

### Added - Phase 2.6: Intelligent Language Generation 🧠
- ONNX Runtime 1.16.0 for LLM inference
- LLMManager.kt for language model lifecycle management
- TinyLlama-1.1B-Chat integration (637MB ONNX model)
- Intelligent AI-generated responses replacing all hardcoded text
- Agent-specific personality prompts for contextual conversations
- Fallback response system when model is unavailable
- CPU-optimized inference (4 threads, 2-3 seconds per response)
- Temperature and top-p sampling for response generation

### Changed
- CommandRouter now uses LLM for all agent responses
- All handlers (Vision, Emotion, Memory, Predictive, Reward, Meta) generate intelligent text
- AILiveCore initializes LLMManager in background thread
- Voice conversation now has real intelligence, not just pattern matching

### Fixed
- Removed dependency on hardcoded response strings
- Improved response quality and context awareness
- Better handling of unknown commands with AI generation

### Documentation
- Added models/MODEL_SETUP.md for model download instructions
- Updated README.md to v0.5.0 with Phase 2.6 complete
- Updated EXECUTIVE_HANDOFF.md with Phase 2.6 status
- Updated SESSION_HANDOFF with Phase 2.6 implementation details

### Technical Details
- Model: TinyLlama-1.1B-Chat (Apache 2.0 license)
- Format: ONNX (optimized for mobile)
- Inference: CPU, 4 threads
- Max tokens: 150 (voice-optimized)
- Temperature: 0.7, Top-P: 0.9
- Memory: ~1GB RAM during inference
- Total active models: 650MB (TinyLlama + MobileNetV3)

## [0.4.0] - 2025-10-28

### Added - Phase 2.4: Text-to-Speech Responses 🗣️
- TTSManager with Android TTS engine integration
- 6 unique agent voice personalities (pitch + speed variations)
- Audio feedback on wake word detection ("Yes?")
- Voice responses for all commands
- TTS state monitoring in MainActivity UI
- Priority-based speech queue
- Full voice conversation loop

### Agent Voice Characteristics
- MotorAI: Lower pitch (0.9), faster (1.1x) - robotic
- EmotionAI: Higher pitch (1.1), slower (0.95x) - warm
- MemoryAI: Normal pitch, slower (0.9x) - thoughtful
- PredictiveAI: Slightly higher (1.05), normal speed
- RewardAI: Higher pitch (1.1), faster (1.1x) - energetic
- MetaAI: Lower pitch (0.95), slower (0.95x) - authoritative

## [0.3.0] - 2025-10-28

### Added - Phase 2.3: Audio Integration 🎤
- AudioManager for microphone capture (16kHz PCM)
- SpeechProcessor with Android SpeechRecognizer wrapper
- WakeWordDetector with pattern matching for "Hey AILive"
- CommandRouter for natural language command parsing
- Voice command routing to all 6 agents
- Real-time transcription display in UI
- Continuous listening with auto-retry

### Changed
- MainActivity now displays voice transcription
- UI updates show wake word detection status
- Commands automatically route to appropriate agents

## [0.2.0] - 2025-10-28

### Added - Phase 2.2: Camera Integration 📷
- CameraManager with CameraX integration
- Camera preview in MainActivity
- Image analysis pipeline (deferred due to device issues)

### Added - Phase 2.1: TensorFlow Lite Vision 👁️
- ModelManager with GPU acceleration (Adreno 750)
- MobileNetV2 integration (1000 ImageNet classes)
- Real-time image classification pipeline
- 13.3MB model with TensorFlow Lite 2.14.0

## [0.1.1] - 2025-10-28 05:00 AM EDT

### Added
- Basic UI layout (`activity_main.xml`) with status display
- `setContentView()` call in MainActivity to keep app alive
- Comprehensive documentation updates
- CHANGELOG.md file
- Detailed lessons learned from debugging session

### Fixed
- **Critical:** MainActivity now has UI, preventing immediate app termination
- Message.kt data class field mismatches in MemoryAI, MetaAI, MotorAI, PredictiveAI
- MemoryRecalled now properly constructs MemoryResult objects
- Duplicate `violationType` parameter in MotorAI.kt
- Various sed-script-induced syntax errors

### Changed
- Updated EXECUTIVE_HANDOFF.md with Phase 1.1 status
- Updated README.md with current build status
- Improved build stability and CI/CD reliability

### Documentation
- Added "Resolved Issues" section to EXECUTIVE_HANDOFF.md
- Added "Lessons Learned" from Oct 27-28 debugging marathon
- Updated project status dashboard
- Enhanced handoff checklist

## [0.1.0] - 2025-10-27

### Added
- Initial release of AILive Phase 1
- Complete cognitive architecture with 8 AI agents
- Message bus with priority queuing and pub/sub
- Blackboard state management system
- Goal planning and arbitration
- Memory system with vector database
- Safety policies and enforcement
- Resource allocation framework
- Integration test suite (6 scenarios)
- All 5 AI models downloaded (727 MB total)
- GitHub Actions CI/CD pipeline
- Comprehensive documentation

### Agents Implemented
- Meta AI (Orchestrator)
- Visual AI (Object Detection)
- Language AI (Speech Recognition)
- Memory AI (Storage & Recall)
- Emotion AI (Sentiment Analysis)
- Predictive AI (Forecasting)
- Reward AI (Value Learning)
- Motor AI (Action Execution & Safety)

### Documentation
- EXECUTIVE_HANDOFF.md (complete project overview)
- README.md (quick start guide)
- CREDITS.md (model attributions)
- LICENSE (non-commercial terms)

## [Unreleased]

### Planned for v0.2.0 (Phase 2)
- TensorFlow Lite integration
- ONNX Runtime integration
- llama.cpp integration
- Real AI model inference (replace placeholders)
- Performance optimization

### Planned for v0.3.0 (Phase 3)
- Material Design 3 dashboard
- Agent status visualization
- Memory browser UI
- Goal management interface
- Settings panel

### Planned for v1.0.0 (Phase 6)
- Self-training system
- Knowledge Scout agent
- Autonomous fine-tuning
- LoRA adapter training

### Planned for v2.0.0 (Phase 7)
- Artificial Desire framework
- Interest mapping
- Reward system
- Proactive engagement
