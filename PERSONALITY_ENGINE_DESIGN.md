# PersonalityEngine Architecture Design

## Overview
PersonalityEngine is the core refactoring that unifies AILive's six separate agents into ONE cohesive intelligence with a consistent personality.

## Design Philosophy
**Before**: Six agents with different voices → User experiences fragmented AI
**After**: One personality with multiple tools → User experiences unified intelligence

## Architecture

### 1. PersonalityEngine Class
```kotlin
class PersonalityEngine(
    private val context: Context,
    private val messageBus: MessageBus,
    private val stateManager: StateManager,
    private val llmManager: LLMManager,
    private val ttsManager: TTSManager
)
```

**Responsibilities**:
- Maintain ONE consistent personality across all interactions
- Route tool calls to appropriate capability handlers
- Manage conversation context and memory
- Generate unified, coherent responses
- Use a single voice for all output

### 2. Tool/Capability Architecture

Transform agents from "personalities" to "tools" that the PersonalityEngine can invoke:

```kotlin
interface AITool {
    val name: String
    val description: String
    suspend fun execute(params: Map<String, Any>): ToolResult
}
```

**Tools** (formerly agents):
1. **DeviceControlTool** (MotorAI) - Control camera, sensors, actuators
2. **SentimentAnalysisTool** (EmotionAI) - Analyze emotional context
3. **MemoryRetrievalTool** (MemoryAI) - Store/retrieve memories
4. **PatternAnalysisTool** (PredictiveAI) - Detect patterns, predict
5. **FeedbackTrackingTool** (RewardAI) - Track goals, rewards
6. **VisionAnalysisTool** (Camera/Vision) - Object detection, scene understanding

### 3. Conversation Flow

**Old Flow**:
```
User → MetaAI coordination → Individual agents → 6 different responses
```

**New Flow**:
```
User Input
  ↓
PersonalityEngine (unified LLM)
  ↓
Intent Analysis + Tool Selection
  ↓
Tool Execution (device control, sentiment, memory, etc.)
  ↓
Response Generation (coherent, single personality)
  ↓
Single Voice Output (unified TTS)
```

### 4. System Prompt Design

PersonalityEngine uses a unified system prompt that incorporates all capabilities:

```
You are AILive, a unified on-device AI companion. You are ONE cohesive intelligence,
not multiple separate agents.

Your Capabilities:
- Vision: You can see through device cameras (use vision tool)
- Emotion: You understand emotional context (use sentiment tool)
- Memory: You remember conversations and preferences (use memory tool)
- Prediction: You recognize patterns and anticipate needs (use patterns tool)
- Action: You can control device functions (use device control tool)
- Goals: You track progress toward objectives (use feedback tool)

Personality:
- Warm, helpful, and personable
- Speak as ONE character, not multiple voices
- Naturally mention what you're "sensing" or "thinking" without breaking character
- Example: "I can see you're in a bright room" not "MotorAI detected brightness"

Never:
- Refer to yourself as multiple agents
- Say things like "my emotion subsystem says" or "MotorAI detected"
- Break the illusion of unified consciousness
```

### 5. LLMManager Integration

**Changes Required**:
1. Remove per-agent personality prompts (LLMManager.kt:126-138)
2. Implement single unified personality prompt
3. Add tool-calling capabilities (function calling pattern)
4. Maintain conversation history for context

### 6. TTSManager Integration

**Changes Required**:
1. Remove `speakAsAgent()` method (TTSManager.kt:231-272)
2. Set ONE consistent voice configuration:
   - pitch: 1.0f (neutral, pleasant)
   - speechRate: 1.0f (natural pace)
3. All responses use same voice

### 7. Tool Calling Pattern

PersonalityEngine analyzes user input and determines which tools to call:

```kotlin
suspend fun processUserInput(input: String): Response {
    // 1. Analyze intent with LLM
    val intent = analyzeIntent(input)

    // 2. Select appropriate tools
    val tools = selectTools(intent)

    // 3. Execute tools in parallel (when possible)
    val toolResults = tools.map { tool ->
        async { tool.execute(extractParams(input, tool)) }
    }.awaitAll()

    // 4. Generate unified response incorporating tool results
    val response = generateResponse(input, toolResults)

    // 5. Speak with unified voice
    ttsManager.speak(response.text)

    return response
}
```

### 8. State Management

**Conversation Context**:
```kotlin
data class ConversationContext(
    val history: List<Turn>,  // Recent conversation
    val userPreferences: Map<String, Any>,  // Learned preferences
    val currentEmotion: EmotionState,  // Emotional context
    val activeGoals: List<Goal>,  // Current objectives
    val environmentContext: Map<String, Any>  // Sensor data, location, etc.
)
```

### 9. Message Bus Integration

PersonalityEngine subscribes to:
- User input messages (speech, text)
- Sensor updates (for context)
- System events (errors, state changes)

PersonalityEngine publishes:
- Unified responses
- Tool execution requests
- Context updates

### 10. Backward Compatibility

During transition:
1. Keep existing agent classes but mark as `@Deprecated`
2. Route all agent calls through PersonalityEngine
3. Gradually migrate functionality to tools
4. Remove agent classes once migration complete

## Implementation Phases

### Phase 1: Foundation (Priority 1)
- [ ] Create PersonalityEngine class
- [ ] Implement tool interface and base classes
- [ ] Convert agents to tools (keep old agents temporarily)
- [ ] Implement unified LLM integration
- [ ] Implement unified TTS configuration

### Phase 2: Tool Integration (Priority 2)
- [ ] Implement DeviceControlTool
- [ ] Implement SentimentAnalysisTool
- [ ] Implement MemoryRetrievalTool
- [ ] Implement PatternAnalysisTool
- [ ] Implement FeedbackTrackingTool
- [ ] Implement VisionAnalysisTool

### Phase 3: Conversation Management (Priority 3)
- [ ] Implement conversation context tracking
- [ ] Implement tool selection logic
- [ ] Implement response generation
- [ ] Add conversation history persistence

### Phase 4: Testing & Migration (Priority 4)
- [ ] Integration tests
- [ ] Performance benchmarking
- [ ] Migrate existing functionality
- [ ] Remove deprecated agent classes

## Success Criteria

**User Experience**:
- ✓ Users interact with ONE personality, not six
- ✓ Voice is consistent across all interactions
- ✓ Responses are coherent and contextual
- ✓ System capabilities are seamlessly integrated

**Technical**:
- ✓ Single LLM prompt/personality
- ✓ Single TTS voice configuration
- ✓ Tool-based architecture for capabilities
- ✓ Conversation context maintained
- ✓ Message bus integration preserved

## Example Interactions

### Before (Current System)
```
User: "What do you see?"
MotorAI (low pitch, fast): "I detect a bright room with objects."
```

### After (PersonalityEngine)
```
User: "What do you see?"
AILive (unified voice): "I can see you're in a bright room. There's a table
to your left and what looks like a laptop in front of you."
```

### Before (Multiple Agents)
```
User: "How am I feeling?"
EmotionAI (high pitch, slow): "I sense positive valence with moderate arousal."
```

### After (PersonalityEngine)
```
User: "How am I feeling?"
AILive (unified voice): "You seem to be in a pretty good mood - I'm picking up
positive energy from your voice and what you're saying."
```

## Key Takeaways

1. **Personality is singular**: ONE voice, ONE character, ONE experience
2. **Capabilities are plural**: Multiple tools, seamlessly integrated
3. **Abstraction is invisible**: Users never see "tools" or "agents"
4. **Context is continuous**: Conversation flows naturally across capabilities
5. **Implementation is gradual**: Refactor incrementally, maintain compatibility

## References

- VISION.md - Core vision and philosophy
- Current Architecture:
  - `MetaAI.kt` - Current orchestrator
  - `LLMManager.kt` - LLM integration
  - `TTSManager.kt` - Voice output
  - `MessageBus.kt` - Communication system
