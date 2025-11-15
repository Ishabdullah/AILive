# AILive Complete File-Level Dependency Map

**Generated:** 2025-11-15
**Total Kotlin Files:** 125+ files
**Total Lines of Code:** ~6,874 lines
**Project Root:** `/home/user/AILive/app/src/main/java/com/ailive/`

---

## Executive Summary

AILive is a sophisticated on-device AI assistant built with a modular, layered architecture. The system consists of:

- **Entry Points (2)**: MainActivity, SetupActivity
- **Core Framework (7)**: AILiveCore, MessageBus, StateManager, PersonalityEngine, etc.
- **AI Modules (14)**: LLMManager, AudioManager, VisionManager, MemoryAI, etc.
- **Personality Engine Tools (9)**: Core capability system for unified intelligence
- **Memory System (13)**: Database, managers, embeddings, vector storage
- **Web Search System (20+)**: Multiple providers, caching, decision engine
- **Supporting Systems (20+)**: Camera, permissions, location, statistics, TTS, etc.

**Architecture Pattern**: Event-driven message bus with agent-based design transitioning to PersonalityEngine-based unified intelligence.

---

## Module Breakdown by Layer

### LAYER 1: ENTRY POINTS (UI Activity Classes)

#### Files:
1. **MainActivity.kt** (1003 lines)
   - Root activity for the application
   - Primary UI entry point

2. **SetupActivity.kt** (199 lines)
   - Initial setup/onboarding flow
   - Name and wake phrase recording

---

### LAYER 2: CORE FRAMEWORK & MESSAGE BUS

#### Files:
1. **core/AILiveCore.kt** (278 lines)
   - **Purpose**: Central coordinator for all AI agents
   - **Public APIs**: 
     - `messageBus`: MessageBus for inter-agent communication
     - `ttsManager`: Text-to-Speech manager
     - `llmManager`: Language model manager
     - `personalityEngine`: Unified intelligence engine
     - `memoryManager`: Unified memory system
   - **Imports**:
     - com.ailive.motor.MotorAI
     - com.ailive.emotion.EmotionAI
     - com.ailive.memory.MemoryAI
     - com.ailive.predictive.PredictiveAI
     - com.ailive.reward.RewardAI
     - com.ailive.meta.MetaAI
     - com.ailive.personality.PersonalityEngine
     - com.ailive.audio.TTSManager
     - com.ailive.ai.llm.LLMManager
   - **Dependencies**: 8 AI agents + PersonalityEngine + 8 tools

2. **core/messaging/MessageBus.kt** (implementation details needed)
   - **Purpose**: Central event bus for agent communication
   - **Public Methods**: publish(), subscribe(), start(), stop()
   - **Message Types**: AIMessage sealed class hierarchy
   - **Imports**: 
     - com.ailive.core.types.AgentType
     - kotlinx.coroutines.*

3. **core/messaging/Message.kt** (210+ lines)
   - **Purpose**: Message type definitions for inter-agent communication
   - **Message Categories**:
     - **Perception**: VisualDetection, AudioTranscript, EmotionVector
     - **Cognition**: MemoryStored, MemoryRecalled, PredictionGenerated, RewardUpdate
     - **Control**: GoalSet, ResourceAllocation, ActionRequest, ActionApproved, ActionRejected
     - **Motor**: ActionExecuted, SensorUpdate
     - **System**: AgentStarted, AgentStopped, ErrorOccurred, SafetyViolation
   - **Imports**: com.ailive.core.types.AgentType

4. **core/messaging/MessagePriority.kt**
   - Priority enumeration for messages

5. **core/state/StateManager.kt**
   - **Purpose**: Manage system state for all agents
   - **State Types**: Meta, perception, cognition, motor

6. **core/state/BlackboardState.kt**
   - Shared state data structure for agents

7. **core/types/AgentType.kt**
   - Enumeration: VISUAL_AI, LANGUAGE_AI, EMOTION_AI, MEMORY_AI, MOTOR_AI, REWARD_AI, META_AI, PREDICTIVE_AI

---

### LAYER 3: PERSONALITY ENGINE & TOOLS (NEW UNIFIED INTELLIGENCE)

#### Core Files:
1. **personality/PersonalityEngine.kt** (787 lines)
   - **Purpose**: Main unified intelligence system replacing 6 separate agents
   - **Key Methods**:
     - `generateStreamingResponse()`: Streaming text generation with full context
     - `processInput()`: Process user input with intent detection
     - `registerTool()`: Register capabilities
   - **Uses Tools**: 
     - SentimentAnalysisTool
     - DeviceControlTool
     - MemoryRetrievalTool
     - PatternAnalysisTool
     - FeedbackTrackingTool
     - LocationTool
     - WebSearchTool
     - UserCorrectionTool
     - VisionAnalysisTool
   - **Imports**:
     - com.ailive.ai.llm.LLMManager
     - com.ailive.audio.TTSManager
     - com.ailive.memory.managers.UnifiedMemoryManager
     - com.ailive.location.LocationManager
     - com.ailive.settings.AISettings
   - **Returns**: `Flow<String>` for streaming responses

2. **personality/prompts/UnifiedPrompt.kt**
   - **Purpose**: Create contextual prompts with:
     - AI name, temporal context, location, conversation history, memory

3. **personality/ToolExecutionListener.kt**
   - Listener for tool execution events

#### Tool Files (9 total):
1. **personality/tools/AITool.kt** (158 lines)
   - Base interface for all tools
   - ToolResult sealed class (Success, Failure, Blocked, Unavailable)
   - BaseTool abstract implementation
   - ToolRegistry for managing tools

2. **personality/tools/SentimentAnalysisTool.kt**
   - Analyzes emotional context of input

3. **personality/tools/DeviceControlTool.kt**
   - Controls device features (flashlight, notifications, etc.)

4. **personality/tools/MemoryRetrievalTool.kt**
   - Retrieves relevant memories from long-term storage

5. **personality/tools/VisionAnalysisTool.kt**
   - Analyzes camera input for visual understanding

6. **personality/tools/LocationTool.kt**
   - Provides location context (GPS, geocoding)

7. **personality/tools/PatternAnalysisTool.kt**
   - Analyzes usage patterns for predictions

8. **personality/tools/FeedbackTrackingTool.kt**
   - Tracks user feedback for learning

9. **personality/tools/UserCorrectionTool.kt**
   - Handles user corrections to improve accuracy

#### Supporting Type Files:
- ConversationTurn (role, content, timestamp)
- Role enum (USER, ASSISTANT, SYSTEM)
- EmotionContext (valence, arousal, urgency)
- Intent, IntentType, InputType
- Response, ToolExecutionResult

---

### LAYER 4: CORE AI MODULES

#### 4A: Language Model (LLM)

1. **ai/llm/LLMManager.kt** (727 lines)
   - **Purpose**: Official llama.cpp Android integration
   - **Model**: Qwen2-VL-2B-Instruct Q4_K_M GGUF
   - **Key Methods**:
     - `initialize()`: Load model (10-15 seconds)
     - `generate()`: Text generation
     - `generateStreaming()`: Token-by-token generation
     - `detectGPUSupport()`: GPU acceleration detection
   - **Features**:
     - GPU acceleration (OpenCL)
     - Performance monitoring
     - Automatic CPU fallback
   - **Imports**:
     - com.ailive.ai.llm.LLMBridge (native)
     - com.ailive.ai.llm.ModelSettings
     - com.ailive.ai.llm.ModelDownloadManager

2. **ai/llm/LLMBridge.kt** (implementation)
   - **Purpose**: JNI bridge to native llama.cpp
   - **Methods**: loadModel(), generate(), free()
   - **External Dependency**: Native llama.cpp Android library

3. **ai/llm/ModelDownloadManager.kt** (400+ lines)
   - **Purpose**: Download and manage GGUF model files
   - **Supported Models**:
     - Qwen2-VL-2B-Instruct
     - GPT-2 variants
     - Alternative GGUF files
   - **Key Methods**:
     - `isModelAvailable()`: Check for model files
     - `getModelPath()`: Get path to model file
     - `downloadModel()`: Download from remote

4. **ai/llm/ModelSettings.kt**
   - **Purpose**: Configuration (context size, max tokens, temperature)
   - **Persistence**: SharedPreferences

5. **ai/llm/ModelIntegrityVerifier.kt**
   - **Purpose**: Verify downloaded model files (checksums)

6. **ai/llm/VisionPreprocessor.kt**
   - **Purpose**: Preprocess images for vision models

7. **ai/llm/QwenVLTokenizer.kt**
   - **Purpose**: Tokenizer for Qwen2-VL model

8. **ai/llm/SimpleGPT2Tokenizer.kt**
   - **Purpose**: Fallback tokenizer for GPT-2 models

#### 4B: Vision System

1. **ai/vision/VisionManager.kt**
   - **Purpose**: Multimodal vision understanding
   - **Method**: generateResponseWithImage(bitmap, prompt)
   - **Dependencies**: LLMBridge for vision queries

#### 4C: Model Management

1. **ai/models/ModelManager.kt**
   - **Purpose**: General model lifecycle management
   - **Note**: Deprecated - functionality moved to LLMManager

#### 4D: Memory AI Components

1. **ai/memory/MemoryModelManager.kt** (100+ lines)
   - **Purpose**: Fact extraction and summarization
   - **Uses**: Qwen via LLMManager (solves singleton conflict)
   - **Methods**: 
     - `extractFacts()`: Extract facts from text
     - `summarizeConversation()`: Summarize conversations

2. **ai/memory/EmbeddingModelManager.kt** (100+ lines)
   - **Purpose**: Text embeddings for semantic search
   - **Uses**: ONNX Runtime with BGE model
   - **Method**: `embed(text) -> FloatArray`

3. **ai/memory/ExtractedFact.kt**
   - Data class for extracted facts

---

### LAYER 5: AGENT SYSTEM (LEGACY - BACKWARD COMPATIBILITY)

**Note**: These agents are kept for backward compatibility but are being replaced by PersonalityEngine

1. **emotion/EmotionAI.kt** (250+ lines)
   - **Purpose**: Emotional context understanding
   - **TODO**: Replace with DistilBERT sentiment model
   - **Imports**: com.ailive.core.messaging.*

2. **memory/MemoryAI.kt** (200+ lines)
   - **Purpose**: Short-term conversation memory
   - **Imports**: com.ailive.memory.storage.*

3. **motor/MotorAI.kt** (200+ lines)
   - **Purpose**: Device control and action execution
   - **Imports**:
     - com.ailive.motor.actuators.*
     - com.ailive.motor.monitors.*
     - com.ailive.motor.permissions.*
     - com.ailive.motor.safety.*

4. **predictive/PredictiveAI.kt**
   - **Purpose**: Predict user needs
   - **TODO**: ML-based prediction

5. **reward/RewardAI.kt**
   - **Purpose**: Reward signaling for learning

6. **meta/MetaAI.kt** (200+ lines)
   - **Purpose**: Goal planning and arbitration
   - **Imports**:
     - com.ailive.meta.planning.Goal
     - com.ailive.meta.planning.GoalStack
     - com.ailive.meta.arbitration.DecisionEngine
     - com.ailive.meta.resources.ResourceAllocator

---

### LAYER 6: UNIFIED MEMORY SYSTEM

#### Database Layer:
1. **memory/database/MemoryDatabase.kt** (Room)
   - **Entities**:
     - UserProfileEntity
     - ConversationEntity, ConversationTurnEntity
     - LongTermFactEntity
   - **DAOs**:
     - UserProfileDao
     - ConversationDao
     - LongTermFactDao
   - **Converters**: Converters.kt

#### Storage Layer:
1. **memory/storage/MemoryStore.kt**
   - File-based JSON storage

2. **memory/storage/MemoryEntry.kt**
   - Data class for memory entries

3. **memory/storage/VectorDB.kt**
   - In-memory vector database for semantic search

#### Manager Layer:
1. **memory/managers/UnifiedMemoryManager.kt** (200+ lines)
   - **Purpose**: Central memory coordinator
   - **Composes**:
     - ConversationMemoryManager (working memory)
     - LongTermMemoryManager (important facts)
     - UserProfileManager (user info)
     - MemoryModelManager (AI-based extraction)
   - **Key Methods**:
     - `recordConversationTurn()`: Add to conversation
     - `extractAndStore()`: Extract facts from text
     - `generateContextForPrompt()`: Get memory context for LLM

2. **memory/managers/ConversationMemoryManager.kt** (Room-based)
   - Manage conversation history (working memory)

3. **memory/managers/LongTermMemoryManager.kt** (Room-based)
   - Manage important facts (fact categories)

4. **memory/managers/UserProfileManager.kt** (Room-based)
   - Manage user profile information

5. **memory/managers/MemoryManager.kt** (legacy)
   - Old memory interface (deprecated)

#### Embedding Layer:
1. **memory/embeddings/TextEmbedder.kt**
   - Generate embeddings using EmbeddingModelManager

---

### LAYER 7: AUDIO SYSTEM

1. **audio/AudioManager.kt** (100+ lines)
   - **Purpose**: Low-level microphone capture
   - **Uses**: AudioRecord for PCM 16-bit 16kHz mono
   - **Exports**: Raw audio bytes via callback

2. **audio/WhisperProcessor.kt** (200+ lines)
   - **Purpose**: Speech-to-text using Whisper model
   - **Features**: 
     - Continuous listening
     - Wake word detection
     - VAD (voice activity detection)
   - **Callbacks**: onFinalResult, onReadyForSpeech, onError

3. **audio/WakeWordDetector.kt** (100+ lines)
   - **Purpose**: Wake word pattern matching
   - **TODO**: ML-based detection (currently string matching)
   - **Uses**: TTSManager for feedback

4. **audio/VoiceRecorder.kt** (80+ lines)
   - **Purpose**: Record voice samples to file
   - **Format**: 3GPP audio

5. **audio/TTSManager.kt** (200+ lines)
   - **Purpose**: Text-to-speech synthesis
   - **Features**: 
     - Queueing system
     - Priority levels
     - Streaming output via AudioTrack
   - **State Flow**: StateFlow for speaking status

6. **audio/CommandRouter.kt** (100+ lines)
   - **Purpose**: Route voice commands to PersonalityEngine
   - **Imports**: com.ailive.core.AILiveCore

---

### LAYER 8: CAMERA & VISION

1. **camera/CameraManager.kt** (200+ lines)
   - **Purpose**: Camera frame capture and preview
   - **Uses**: CameraX library
   - **Exports**: Bitmap frames via callback

2. **motor/actuators/CameraController.kt** (100+ lines)
   - **Purpose**: Camera control (flash, zoom)
   - **Dependencies**: camera2 APIs

3. **diagnostics/CameraDiagnostics.kt**
   - Camera capability detection

---

### LAYER 9: MOTOR/DEVICE CONTROL

1. **motor/MotorAI.kt**
   - Orchestrator for device actions

2. **motor/ActionResult.kt**
   - Result data class for actions

3. **motor/actuators/DeviceActuator.kt**
   - Abstract device control interface

4. **motor/actuators/CameraController.kt**
   - Camera control implementation

5. **motor/monitors/BatteryMonitor.kt**
   - Battery status monitoring via BroadcastReceiver

6. **motor/monitors/ThermalMonitor.kt**
   - Device temperature monitoring (API 29+)

7. **motor/permissions/PermissionManager.kt**
   - Runtime permission management
   - ActivityResultLauncher for modern permission flow

8. **motor/safety/SafetyPolicy.kt**
   - Action safety policy enforcement

9. **motor/safety/SafetyViolation.kt**
   - Safety violation reporting

---

### LAYER 10: WEB SEARCH SYSTEM

#### Core Components (6 files):
1. **websearch/WebSearchManager.kt** (200+ lines)
   - Central orchestrator for web search
   - Imports all providers and managers

2. **websearch/core/SearchProvider.kt**
   - Abstract interface for search providers
   - Methods: search(query: String) -> List<SearchResultItem>

3. **websearch/core/SearchQuery.kt**
   - Query data structure with UUID

4. **websearch/core/SearchIntent.kt**
   - Intent type enumeration

5. **websearch/core/SearchResponse.kt**
   - Response data structure

6. **websearch/core/SearchResultItem.kt**
   - Individual search result

7. **websearch/core/ProviderResult.kt**
   - Provider-specific result wrapper

8. **websearch/core/SearchResultItem.kt**
   - Result item structure

#### Search Providers (6 files):
1. **websearch/providers/general/DuckDuckGoInstantProvider.kt**
   - Uses: OkHttp, Moshi, instant answer API

2. **websearch/providers/general/SerpApiProvider.kt**
   - Uses: SerpApi service

3. **websearch/providers/news/NewsApiProvider.kt**
   - Uses: NewsApi.org

4. **websearch/providers/weather/OpenWeatherProvider.kt**
   - Uses: OpenWeatherMap API

5. **websearch/providers/weather/WttrProvider.kt**
   - Uses: wttr.in service

6. **websearch/providers/wiki/WikipediaProvider.kt**
   - Uses: Wikipedia API

#### Intelligence Layer (4 files):
1. **websearch/intelligence/SearchDecisionEngine.kt** (150+ lines)
   - Decides which provider to use
   - Imports: WebSearchManager context

2. **websearch/intelligence/SearchHistoryManager.kt** (150+ lines)
   - Tracks search history in SharedPreferences

3. **websearch/intelligence/KnowledgeConfidenceAnalyzer.kt**
   - Analyzes knowledge confidence levels

#### Infrastructure (3 files):
1. **websearch/cache/CacheLayer.kt**
   - Uses: Caffeine cache library
   - TTL-based caching

2. **websearch/ratelimit/RateLimiter.kt**
   - Rate limiting per provider

3. **websearch/network/HttpClientFactory.kt**
   - OkHttp client configuration
   - Connection pooling

#### Integration:
1. **websearch/integration/WebSearchTool.kt** (200+ lines)
   - Personality tool for web search
   - Extends BaseTool
   - Imports all providers

2. **websearch/summarizer/ResultSummarizer.kt** (100+ lines)
   - Summarize search results

3. **websearch/verification/FactVerifier.kt**
   - Fact checking logic

---

### LAYER 11: LOCATION & CONTEXT

1. **location/LocationManager.kt** (200+ lines)
   - **Purpose**: GPS and geocoding
   - **Uses**: Play Services Fused Location
   - **Methods**: 
     - `getLocationContext()`: Get location info for context
     - Geocoding (address lookup)
   - **Imports**: com.google.android.gms.location.*

---

### LAYER 12: SETTINGS & STATISTICS

1. **settings/AISettings.kt** (100+ lines)
   - **Purpose**: User settings persistence
   - **Uses**: SharedPreferences
   - **Properties**:
     - aiName: Custom AI name
     - wakePhrase: Wake word
     - locationAwarenessEnabled: Boolean
     - streamingSpeechEnabled: Boolean

2. **stats/StatisticsManager.kt** (100+ lines)
   - **Purpose**: Track usage statistics
   - **Collects**: Response times, token counts, performance metrics

---

### LAYER 13: UI COMPONENTS

#### Activities (3 files):
1. **MainActivity.kt** (1003 lines)
   - Main UI activity
   - Initializes all systems

2. **SetupActivity.kt** (199 lines)
   - Setup/onboarding flow

3. **ui/MemoryActivity.kt**
   - Memory browsing interface

#### Dialogs & Components:
1. **ui/ModelSetupDialog.kt** (200+ lines)
   - Model download/setup UI

2. **ui/ModelSettingsActivity.kt**
   - LLM settings interface

#### Fragments:
1. **ui/dashboard/DashboardFragment.kt**
   - System dashboard

#### ViewModels:
1. **ui/viewmodel/MemoryViewModel.kt**
   - Memory UI state management

2. **ui/viewmodel/MemoryViewModelFactory.kt**
   - ViewModel factory

#### Visualizations:
1. **ui/visualizations/FeedbackChartView.kt**
   - Chart for feedback visualization

2. **ui/visualizations/PatternGraphView.kt**
   - Graph for pattern visualization

3. **ui/visualizations/ChartUtils.kt**
   - Chart utility functions

#### Theme:
1. **ui/theme/Theme.kt**
   - Material Design theme

#### Dashboard Components:
1. **ui/dashboard/ToolStatus.kt**
   - Tool status data

2. **ui/dashboard/ToolStatusCard.kt**
   - UI card for tool status

---

### LAYER 14: UTILITIES & TESTING

1. **utils/TestDataGenerator.kt**
   - Generate test conversation data

2. **testing/TestScenarios.kt**
   - Test scenario runner

3. **example/AILiveExample.kt**
   - Example usage of MessageBus

4. **example/AILiveSystemDemo.kt**
   - System demonstration code

---

## DEPENDENCY CHAIN ANALYSIS

### Entry Point Flow: MainActivity
```
MainActivity
├── AILiveCore (core/AILiveCore.kt)
│   ├── MessageBus (core/messaging/MessageBus.kt)
│   ├── StateManager (core/state/StateManager.kt)
│   ├── LLMManager (ai/llm/LLMManager.kt)
│   │   ├── LLMBridge (native)
│   │   ├── ModelDownloadManager (ai/llm/ModelDownloadManager.kt)
│   │   └── ModelSettings (ai/llm/ModelSettings.kt)
│   ├── PersonalityEngine (personality/PersonalityEngine.kt)
│   │   ├── UnifiedPrompt (personality/prompts/UnifiedPrompt.kt)
│   │   ├── LocationManager (location/LocationManager.kt)
│   │   ├── AISettings (settings/AISettings.kt)
│   │   └── 9 Tools (see Tool List below)
│   ├── UnifiedMemoryManager (memory/managers/UnifiedMemoryManager.kt)
│   │   ├── ConversationMemoryManager
│   │   ├── LongTermMemoryManager
│   │   ├── UserProfileManager
│   │   ├── MemoryDatabase (memory/database/MemoryDatabase.kt)
│   │   └── MemoryModelManager (ai/memory/MemoryModelManager.kt)
│   ├── TTSManager (audio/TTSManager.kt)
│   ├── StatisticsManager (stats/StatisticsManager.kt)
│   └── Legacy Agents:
│       ├── MotorAI (motor/MotorAI.kt)
│       ├── EmotionAI (emotion/EmotionAI.kt)
│       ├── MemoryAI (memory/MemoryAI.kt)
│       ├── PredictiveAI (predictive/PredictiveAI.kt)
│       ├── RewardAI (reward/RewardAI.kt)
│       └── MetaAI (meta/MetaAI.kt)
│
├── CameraManager (camera/CameraManager.kt)
│   └── Uses: CameraX library
│
├── AudioManager (audio/AudioManager.kt) + WhisperProcessor + WakeWordDetector
│
├── CommandRouter (audio/CommandRouter.kt)
│   └── Uses AILiveCore.personalityEngine
│
├── ModelDownloadManager (ai/llm/ModelDownloadManager.kt)
│   └── Handles model downloads/imports
│
└── VisionManager (ai/vision/VisionManager.kt)
    └── Multimodal vision processing
```

### Personality Engine Tool Chain
```
PersonalityEngine.processInput()
├── analyzeIntent() → Intent classification
├── selectTools(intent) → Tool registry lookup
│   ├── SentimentAnalysisTool (emotion context)
│   ├── DeviceControlTool (device actions)
│   ├── MemoryRetrievalTool (search memory)
│   ├── VisionAnalysisTool (image analysis)
│   ├── LocationTool (GPS context)
│   ├── PatternAnalysisTool (predictions)
│   ├── FeedbackTrackingTool (learning)
│   ├── UserCorrectionTool (corrections)
│   └── WebSearchTool (web search)
├── executeTools() → Parallel execution
├── generateResponse() → LLM generation with tool context
├── speakResponse() → TTSManager
└── updateState() → StateManager
```

### Memory System Chain
```
UnifiedMemoryManager
├── recordConversationTurn()
│   └── ConversationMemoryManager → MemoryDatabase
├── extractAndStore()
│   ├── MemoryModelManager (uses LLMManager.Qwen)
│   └── LongTermMemoryManager → MemoryDatabase
├── generateContextForPrompt()
│   ├── TextEmbedder → EmbeddingModelManager (ONNX BGE)
│   ├── VectorDB (semantic search)
│   └── LongTermMemoryManager (fact retrieval)
└── userProfile
    └── UserProfileManager
```

### Web Search Chain
```
WebSearchTool (Personality tool)
├── SearchIntentDetector → Detect search intent
├── SearchDecisionEngine → Select provider(s)
├── Provider selection:
│   ├── DuckDuckGoInstantProvider (general)
│   ├── SerpApiProvider (general)
│   ├── OpenWeatherProvider (weather)
│   ├── NewsApiProvider (news)
│   ├── WikipediaProvider (knowledge)
│   └── WttrProvider (weather)
├── CacheLayer (Caffeine TTL cache)
├── RateLimiter (per provider)
├── ResultSummarizer → Summarize results
└── FactVerifier → Verify results
```

---

## DEPENDENCY SUMMARY BY FILE

### HIGH-LEVEL DEPENDENCIES (More than 5 internal imports)

**Most Connected Files:**
1. **MainActivity** (23 imports)
2. **AILiveCore** (21 imports)
3. **PersonalityEngine** (18 imports)
4. **UnifiedMemoryManager** (10 imports)
5. **WebSearchTool** (18 imports)

### IMPORT STATISTICS

**External Dependencies Used:**
- **Android Framework**: android.app, android.content, android.media, android.graphics, etc.
- **AndroidX**: androidx.appcompat, androidx.camera, androidx.core, androidx.fragment, androidx.lifecycle, androidx.room
- **Google Play Services**: com.google.android.gms.location
- **Networking**: okhttp3, com.squareup.moshi
- **ML/AI**: 
  - ai.onnxruntime (ONNX Runtime for embeddings)
  - Native llama.cpp (via JNI LLMBridge)
- **Caching**: com.github.benmanes.caffeine
- **Coroutines**: kotlinx.coroutines (Flow, StateFlow, Channel)
- **JSON**: org.json, com.squareup.moshi
- **Room DB**: androidx.room

---

## IDENTIFIED MISSING FILES

**Referenced but not found (placeholder/interface files):**

None critically missing - all referenced classes exist. However, the following have minimal implementations:

1. **com.ailive.core.messaging.ActionType** - Used but type definition unclear
2. **com.ailive.emotion.EmotionVector** - Imported but appears to be defined in Message.kt
3. **com.ailive.memory.storage.ContentType** - Enum type
4. **com.ailive.meta.arbitration.ActionDecision** - Referenced but minimal implementation
5. **com.ailive.meta.planning.GoalContext** - Referenced but implementation needed
6. **com.ailive.websearch.core.Attribution** - Referenced in ResultSummarizer
7. **com.ailive.websearch.core.IntentDetectionResult** - Used in SearchIntentDetector
8. **com.ailive.websearch.intelligence.LocationInfo** - Used in WebSearchTool
9. **com.ailive.websearch.intelligence.QueryContext** - Used in SearchDecisionEngine
10. **com.ailive.websearch.ratelimit.RateLimiterManager** - Imported but file unknown location

---

## INCOMPLETE FEATURES (TODOs & NOTIMPLEMENTEDERRORs)

### HIGH PRIORITY (Core Functionality)

#### 1. **WakeWordDetector.kt**
```
TODO: Implement ML-based wake word detection
Currently: Simple string matching
Impact: Wake word accuracy is limited
Location: Line 102
```

#### 2. **MotorAI.kt**
```
TODO: Implement CPU monitoring (line 152)
TODO: Implement notification system (line 166)
TODO: Implement data storage (line 170)
Impact: CPU monitoring, notifications not functional
```

#### 3. **EmotionAI.kt**
```
TODO: Replace with DistilBERT sentiment model
Currently: Simple keyword-based sentiment analysis
Impact: Limited emotional understanding
Location: Line 227
```

### MEDIUM PRIORITY (Enhancement)

#### 4. **EmbeddingModelManager.kt**
```
Uses: ONNX Runtime with model file
Status: Functional but may need optimization
```

#### 5. **Memory Model Manager**
```
Uses: LLMManager (Qwen) for fact extraction
Status: Working but fallback to regex if LLM not ready
```

### LOW PRIORITY (Future)

#### 6. **Vision Support**
```
Status: Text-only inference implemented
TODO: Add mmproj file for true vision understanding
Note: Infrastructure in place, waiting for mmproj
Location: LLMManager.kt lines 293-296
```

#### 7. **Predictive System**
```
Status: Basic placeholder
TODO: Implement pattern-based or ML-based predictions
Location: predictive/PredictiveAI.kt
```

---

## ARCHITECTURAL LAYERS SUMMARY

```
┌─────────────────────────────────────────────────┐
│ LAYER 0: ENTRY POINTS (Activities)              │
│ MainActivity, SetupActivity                     │
└──────────────┬──────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────┐
│ LAYER 1: UNIFIED INTELLIGENCE (PersonalityEngine)
│ + 9 Tools (Vision, Device, Memory, etc.)       │
│ + Message Bus + State Manager                  │
└──────────────┬──────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────┐
│ LAYER 2: CORE AI (LLMManager, MemoryManager)   │
│ + VisionManager, AudioManager, CameraManager   │
│ + LocationManager, StatisticsManager           │
└──────────────┬──────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────┐
│ LAYER 3: LEGACY AGENTS (Backward Compatibility)
│ MotorAI, EmotionAI, MemoryAI, etc.             │
└──────────────┬──────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────┐
│ LAYER 4: SPECIALIZED SYSTEMS                    │
│ + Memory Database, Web Search, Motor Control   │
│ + Audio Processing, Device Monitoring          │
└──────────────┬──────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────┐
│ LAYER 5: UI & UTILITIES                         │
│ Activities, Fragments, ViewModels, Dialogs     │
└─────────────────────────────────────────────────┘
```

---

## FILE STATISTICS

### By Module:
- **Core Framework**: 7 files, ~1,200 LOC
- **Personality Engine**: 12 files, ~1,000 LOC
- **AI Modules (LLM/Vision/Memory)**: 14 files, ~1,500 LOC
- **Memory System**: 13 files, ~800 LOC
- **Audio System**: 6 files, ~600 LOC
- **Web Search**: 20+ files, ~1,500 LOC
- **Legacy Agents**: 6 files, ~1,000 LOC
- **Motor/Device Control**: 9 files, ~600 LOC
- **UI Components**: 12 files, ~1,200 LOC
- **Utilities/Testing**: 5 files, ~400 LOC
- **Database/Location**: 8 files, ~500 LOC

**Total**: 125+ files, ~6,874 LOC

---

## DATA FLOW EXAMPLES

### User Text Input Flow:
```
MainActivity.processTextCommand()
  ↓
PersonalityEngine.generateStreamingResponse()
  ├─ LLMManager.generateStreaming(prompt)
  │   ├─ LLMBridge.generate() [native]
  │   └─ emit tokens via Flow
  ├─ TTSManager.speakIncremental() [parallel]
  └─ UIUpdate()
```

### Memory Recording Flow:
```
PersonalityEngine.addToHistory()
  ↓
UnifiedMemoryManager.recordConversationTurn()
  ├─ ConversationMemoryManager.insert()
  │   └─ MemoryDatabase.conversationDao.insert()
  ├─ Extract facts (if enabled)
  │   ├─ MemoryModelManager.extractFacts() [LLMManager]
  │   └─ LongTermMemoryManager.insert()
  └─ Update embeddings
      ├─ TextEmbedder.embed() [ONNX BGE]
      └─ VectorDB.add()
```

### Web Search Flow:
```
PersonalityEngine.selectTools() [intent: CONVERSATION + search keywords]
  ↓
WebSearchTool.execute()
  ├─ SearchIntentDetector.detectIntent()
  ├─ SearchDecisionEngine.selectProvider()
  ├─ CacheLayer.lookup()
  ├─ Provider.search() [if cache miss]
  │   └─ RateLimiter.checkLimit()
  ├─ ResultSummarizer.summarize()
  ├─ CacheLayer.store()
  └─ Return results
```

---

## CRITICAL DEPENDENCIES

### Must Be Initialized (in order):
1. **AILiveCore.initialize()**
   - MessageBus
   - StateManager
   - TTSManager
   - LLMManager (background, 10-15 seconds)
2. **AILiveCore.start()**
   - All agents/PersonalityEngine
3. **Audio/Camera initialization** (on demand)
4. **Memory system initialization** (depends on LLMManager)

### Optional But Recommended:
- LocationManager (for context)
- StatisticsManager (for metrics)
- WebSearch (for knowledge)

---

## INTEGRATION POINTS

### Message Bus Topics:
- `AIMessage.Perception.AudioTranscript` → PersonalityEngine
- `AIMessage.Perception.EmotionVector` → PersonalityEngine
- `AIMessage.Motor.ActionExecuted` → StateManager

### Broadcast Receivers:
- Battery status changes → BatteryMonitor

### File Storage:
- Models: `/data/data/.../files/models/`
- Memories: `/data/data/.../files/memories/` (JSON)
- Settings: SharedPreferences

### Network Calls:
- Model downloads: Remote URL
- Web search: Multiple API endpoints
- Location: FusedLocationProvider

---

## CONCLUSION

AILive features a sophisticated, layered architecture with:
- **Clear separation of concerns**: UI → Intelligence → AI → Storage
- **Event-driven communication**: MessageBus for loose coupling
- **Extensible tool system**: Easy to add new capabilities
- **Memory persistence**: Multi-layered memory with semantic search
- **Streaming intelligence**: Real-time token generation + TTS
- **Graceful fallbacks**: CPU/GPU, LLM/regex, online/offline

The transition from 6 separate agents to PersonalityEngine-based unified intelligence is complete, with legacy agents maintained for backward compatibility.

