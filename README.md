# AILive - Synthetic Brain for Android

A modular AI system that runs entirely on-device using small, specialized models.

## Architecture
┌─────────────────────────────────────────┐
│            Meta AI (Orchestrator)       │
│  - Goal planning                        │
│  - Decision making                      │
│  - Resource allocation                  │
└─────────────────────────────────────────┘
↕
┌───────────────────────────────┐
│      Message Bus              │
│  - Priority queuing           │
│  - Pub/sub messaging          │
└───────────────────────────────┘
↕
┌─────────┬──────────┬──────────┬─────────┐
│ Visual  │ Language │  Memory  │  Motor  │
│   AI    │    AI    │    AI    │   AI    │
└─────────┴──────────┴──────────┴─────────┘

## Current Status

### ✅ Completed Components

1. **Core Infrastructure**
   - Message Bus (priority-based pub/sub)
   - Blackboard State (shared memory)
   - State Manager (thread-safe)

2. **Motor AI** (Device Control)
   - Safety policies
   - Permission manager
   - Battery/thermal monitoring
   - Camera controller stub

3. **Meta AI** (Orchestrator)
   - Goal stack planning
   - Decision engine
   - Resource allocator
   - Conflict resolver

4. **Memory AI** (Long-term Memory)
   - Vector database (in-memory)
   - Text embeddings (placeholder)
   - Persistent storage (JSON)
   - Similarity search

### 🚧 In Progress

- Visual AI (MobileNetV3 integration)
- Language AI (Whisper + DistilBERT)
- Emotion AI (sentiment analysis)
- LLM integration (SmolLM2-360M)

## Testing in Termux

### Prerequisites
pkg install kotlin openjdk-17

### Quick Start
cd ~/AILive
./build_and_run.sh

This will:
1. Compile all Kotlin code
2. Run the test suite
3. Show system statistics

### Manual Testing
Compilecd ~/AILive/app/src/main/java
kotlinc -include-runtime -d ~/AILive/ailive.jar com/ailive/**/*.ktRunjava -jar ~/AILive/ailive.jar com.ailive.testing.TermuxRunner

## File Structure
AILive/
├── app/src/main/java/com/ailive/
│   ├── core/              # Core infrastructure
│   │   ├── messaging/     # Message bus & types
│   │   ├── state/         # Blackboard state
│   │   └── types/         # Agent types
│   ├── motor/             # Device control
│   │   ├── safety/        # Safety policies
│   │   ├── permissions/   # Permission manager
│   │   ├── monitors/      # Battery/thermal
│   │   └── actuators/     # Camera/sensors
│   ├── meta/              # Orchestrator
│   │   ├── planning/      # Goal stack
│   │   ├── arbitration/   # Decision engine
│   │   └── resources/     # Resource allocator
│   ├── memory/            # Long-term memory
│   │   ├── storage/       # Vector DB
│   │   └── embeddings/    # Text embedder
│   ├── testing/           # Test suite
│   └── AILiveCore.kt      # Main coordinator
├── build_and_run.sh       # Build script
└── README.md              # This file

## Next Steps

1. **Add Visual AI** - MobileNetV3 for object detection
2. **Add Language AI** - Whisper + DistilBERT
3. **Integrate LLM** - SmolLM2-360M for reasoning
4. **Build APK** - Package for Android deployment

## Notes

- **Termux Mode**: Android-specific features are stubbed out
- **Production Mode**: Requires compilation to APK with proper Android context
- **Models**: Currently using placeholder embeddings (replace with real TFLite models)

## License

Open source - your project for AI software engineering studies.
