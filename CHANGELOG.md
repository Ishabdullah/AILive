# Changelog

All notable changes to AILive will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2025-11-11

### Added - Personalization & Context Awareness ✨

**Custom AI Name ✏️**
- First-run setup dialog for naming your AI assistant
- Persistent name storage across sessions via AISettings.kt
- Automatic wake phrase generation ("Hey [YourAI]")
- Name used throughout UI and all system prompts
- ModelSetupDialog.kt enhanced with name customization flow

**Temporal Awareness ⏱️**
- Real-time date and time awareness in all AI interactions
- UnifiedPrompt.kt provides temporal context automatically
- Format: "Current Time: 3:45 PM on Tuesday, November 11, 2025"
- Contextual time understanding for scheduling and time-based queries

**GPS/Location Awareness 📍**
- LocationManager.kt - New location tracking system (230+ lines)
- Real-time GPS via FusedLocationProviderClient
- Reverse geocoding (GPS coordinates → City, State, Country)
- 5-minute location caching for battery efficiency
- Privacy-respecting toggle in settings (opt-in)
- Format: "You're currently in New York, NY, United States"
- Integrated into PersonalityEngine for location-aware responses

**Working Statistics 📊**
- StatisticsManager.kt - New usage tracking system (180+ lines)
- Track total conversations, messages, tokens processed
- Average response time tracking (lifetime + recent 50)
- Real-time memory usage monitoring
- Session-level statistics with reset capability
- Persistent storage via SharedPreferences
- Infrastructure ready for dashboard visualization

**Real-Time Streaming Speech 🗣️**
- Token-to-speech streaming with 300-500ms latency
- TTSManager.kt enhanced with speakIncremental() method
- Sentence buffering for natural speech flow in MainActivity.kt
- Incremental TTS using QUEUE_ADD mode for seamless continuation
- Configurable buffer delay (0.1-2.0 seconds) in settings
- Toggle in settings to enable/disable streaming speech
- Smart sentence detection (periods, exclamation marks, question marks)

**System Improvements**
- AILive Unified Directive - Comprehensive system instruction (84 lines)
  - Self-awareness of role and capabilities
  - Safety and stop control mechanisms
  - Autonomy discipline and response standards
  - Response control module to prevent rambling
- Fixed model loading failures by switching to app-private storage
- Permission flow optimized - requests BEFORE model operations
- Settings button moved to left side for better UX
- Enhanced MainActivity permission handling with buildPermissionList()

### Changed
- UnifiedPrompt.kt now includes temporal and location context in all prompts
- PersonalityEngine.kt integrated with LocationManager, StatisticsManager, AISettings
- MainActivity.kt refactored permission flow for better user experience
- Model storage migrated to app-private external storage (all Android versions)
- activity_model_settings.xml updated with streaming speech and location toggles
- ModelSettingsActivity.kt wired to new settings (streaming + location)

### Fixed
- **Critical:** ModelDownloadManager.kt infinite recursion bug (line 106)
  - Was calling getModelsDir() from within itself on Android 12-
  - Fixed by using proper Environment.getExternalStoragePublicDirectory()
- **Critical:** Model loading failures with llama.cpp
  - Root cause: Models in public Downloads couldn't be accessed by native code
  - Solution: ALL Android versions now use app-private storage
  - Models stored in getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
  - Compatible with scoped storage AND llama.cpp file access
- Permission request timing - now happens before model setup, not after

### Security
- Models now stored in app-private storage for better security
- Location sharing is opt-in, disabled by default
- Permissions properly scoped and requested at appropriate times

### Documentation
- README.md updated to reflect v1.2 features and status
- CHANGELOG.md updated with comprehensive v1.2 release notes
- Updated version timeline (v1.2 Complete, v1.3 In Progress)
- Quick Stats updated with new feature highlights

### Technical Details
- **New Files:**
  - LocationManager.kt (230+ lines) - GPS and reverse geocoding
  - StatisticsManager.kt (180+ lines) - Usage tracking and analytics
- **Enhanced Files:**
  - UnifiedPrompt.kt - Temporal context, location integration
  - PersonalityEngine.kt - Context manager integration
  - TTSManager.kt - Streaming speech support
  - MainActivity.kt - Permission flow, streaming TTS buffering
  - AISettings.kt - New settings (streaming, location, buffer delay)
  - ModelDownloadManager.kt - Fixed storage path logic
- **Dependencies:**
  - Google Play Services Location for FusedLocationProviderClient
  - Android Geocoder API for reverse geocoding
- **Performance:**
  - Location caching reduces battery impact
  - Streaming speech adds <100ms overhead
  - Statistics tracking has negligible performance cost

## [0.5.1] - 2025-10-29

### Added - Manual Control UI for Testing & Debugging 🎛️
- Control panel with 3 buttons (top right):
  - 🎤 MIC ON/OFF - Toggle microphone manually
  - 📷 CAM ON/OFF - Toggle camera manually
  - 🧪 TEST - Quick test with "what do you see" command
- Command input panel (bottom center):
  - Text field to type commands directly
  - SEND button to submit typed commands
  - Enter key support for quick command sending
- Text-based command processing bypassing voice recognition
- Real-time button state updates with color indicators (green=on, red=off)
- Comprehensive logging for all manual control actions

### Changed
- MainActivity now supports dual input: voice commands AND text commands
- Audio and camera can be independently controlled by user
- Test commands can be sent without voice recognition
- UI provides more control for debugging intermittent voice issues

### Fixed
- Improved TTS synchronization (now properly waits for TTS to finish)
- Better audio resource management between TTS and speech recognition
- Reduced likelihood of command freeze by allowing manual mic restart

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
