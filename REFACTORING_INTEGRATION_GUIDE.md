# AILive PersonalityEngine - Integration Guide

## Overview

This guide documents the strategic refactoring of AILive from six separate agent personalities into ONE unified intelligence via the PersonalityEngine architecture.

**Status**: Phase 1 (Foundation) - COMPLETE ✅

## What We've Built

### Core Architecture

1. **PersonalityEngine** (`personality/PersonalityEngine.kt`)
   - Central orchestrator for unified intelligence
   - Single entry point for all user interactions
   - Tool selection and execution
   - Coherent response generation
   - Unified voice output

2. **Tool Interface** (`personality/tools/AITool.kt`)
   - Base interface for all capabilities
   - Success/Failure/Blocked/Unavailable result types
   - ToolRegistry for managing available tools
   - Async execution with proper error handling

3. **Implemented Tools**:
   - `SentimentAnalysisTool` - Emotional context analysis (from EmotionAI)
   - `DeviceControlTool` - Hardware control (from MotorAI)
   - `MemoryRetrievalTool` - Memory storage/retrieval (placeholder)

4. **Unified System Prompt** (`personality/prompts/UnifiedPrompt.kt`)
   - Single personality definition
   - Context-aware prompt generation
   - Natural language tool result formatting
   - Emotional context integration

5. **Updated Core Systems**:
   - `LLMManager.kt` - Now uses unified personality (no more per-agent prompts)
   - `TTSManager.kt` - speakAsAgent() deprecated, unified voice enforced

## Integration Steps

### Step 1: Initialize PersonalityEngine in Your Application

Add to your `MainActivity.kt` or `AILiveCore.kt`:

```kotlin
import com.ailive.personality.PersonalityEngine
import com.ailive.personality.tools.*

class MainActivity : AppCompatActivity() {

    private lateinit var personalityEngine: PersonalityEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize core components (existing code)
        val messageBus = MessageBus()
        val stateManager = StateManager()
        val llmManager = LLMManager(this)
        val ttsManager = TTSManager(this)

        // Initialize agents (keep existing for now)
        val emotionAI = EmotionAI(messageBus, stateManager)
        val motorAI = MotorAI(this, this, messageBus, stateManager)
        val memoryAI = MemoryAI(messageBus, stateManager)

        // Create PersonalityEngine
        personalityEngine = PersonalityEngine(
            context = this,
            messageBus = messageBus,
            stateManager = stateManager,
            llmManager = llmManager,
            ttsManager = ttsManager
        )

        // Register tools
        personalityEngine.registerTool(SentimentAnalysisTool(emotionAI))
        personalityEngine.registerTool(DeviceControlTool(motorAI))
        personalityEngine.registerTool(MemoryRetrievalTool(memoryAI))

        // Start PersonalityEngine
        personalityEngine.start()

        // Start MessageBus
        messageBus.start()
    }
}
```

### Step 2: Route User Input Through PersonalityEngine

**Before (Old Way)**:
```kotlin
// Multiple agents handling input separately
metaAI.addGoal(Goal.Atomic("respond to user", ...))
emotionAI.analyzeText(userInput)
motorAI.executeAction(...)
```

**After (New Way)**:
```kotlin
// Single unified entry point
lifecycleScope.launch {
    val response = personalityEngine.processInput(
        input = userInput,
        inputType = InputType.TEXT
    )

    // Response is automatically spoken with unified voice
    Log.d("AILive", "Response: ${response.text}")
}
```

### Step 3: Handle Voice Input

PersonalityEngine automatically subscribes to `AudioTranscript` messages:

```kotlin
// Your speech recognition code (unchanged)
speechRecognizer.setRecognitionListener(object : RecognitionListener {
    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val transcript = matches?.get(0) ?: return

        // Publish to MessageBus - PersonalityEngine will handle it
        lifecycleScope.launch {
            messageBus.publish(
                AIMessage.Perception.AudioTranscript(
                    transcript = transcript,
                    confidence = 1.0f
                )
            )
        }
    }
})
```

### Step 4: Backward Compatibility During Transition

The old agent classes still exist. You can gradually migrate:

**Phase 1 (Current)**: Both systems coexist
- PersonalityEngine uses existing agents via tools
- Old code still works
- New code uses PersonalityEngine

**Phase 2 (Next)**: Migrate old code
- Replace direct agent calls with PersonalityEngine
- Update UI to use unified responses
- Remove deprecated speakAsAgent() calls

**Phase 3 (Future)**: Remove old agents
- Delete deprecated agent personality code
- Keep tool implementations
- Clean up codebase

## Example Usage Scenarios

### Scenario 1: Simple Question

```kotlin
lifecycleScope.launch {
    val response = personalityEngine.processInput("What can you see?")
    // PersonalityEngine will:
    // 1. Detect vision intent
    // 2. Call DeviceControlTool to capture image
    // 3. Generate unified response like:
    //    "I can see you're in a bright room with a desk to your left"
    // 4. Speak with unified voice
}
```

### Scenario 2: Emotional Context

```kotlin
lifecycleScope.launch {
    val response = personalityEngine.processInput("I'm feeling overwhelmed")
    // PersonalityEngine will:
    // 1. Detect emotion intent
    // 2. Call SentimentAnalysisTool to analyze sentiment
    // 3. Generate empathetic response:
    //    "I can tell you're feeling stressed. Take a deep breath. How can I help?"
    // 4. Speak with unified voice (empathetic tone in LLM response, not voice change)
}
```

### Scenario 3: Device Control

```kotlin
lifecycleScope.launch {
    val response = personalityEngine.processInput("Turn on the flashlight")
    // PersonalityEngine will:
    // 1. Detect device control intent
    // 2. Call DeviceControlTool(action="enable_flashlight")
    // 3. Generate confirmation:
    //    "Flashlight is on"
    // 4. Speak with unified voice
}
```

## Testing Your Integration

### Test 1: Unified Voice
All responses should sound the same (pitch=1.0, rate=1.0):

```kotlin
personalityEngine.processInput("Hello")
delay(2000)
personalityEngine.processInput("What can you see?")
delay(2000)
personalityEngine.processInput("I'm happy today")
// All should use same voice, different content
```

### Test 2: Tool Execution
Verify tools are being called:

```kotlin
val response = personalityEngine.processInput("Capture a photo")
Log.d("Test", "Used tools: ${response.usedTools}")
// Should show ["control_device"]
```

### Test 3: Conversation Context
Check that context is maintained:

```kotlin
personalityEngine.processInput("My name is Alex")
delay(1000)
personalityEngine.processInput("What's my name?")
// Should remember "Alex" from conversation history
```

## Migration Checklist

### For Existing Code

- [ ] Initialize PersonalityEngine in main activity
- [ ] Register all available tools
- [ ] Start PersonalityEngine after MessageBus
- [ ] Route new user input through processInput()
- [ ] Update voice input handling to use MessageBus
- [ ] Test unified voice across all interactions
- [ ] Remove direct speakAsAgent() calls
- [ ] Verify conversation context works

### For New Features

- [ ] Use PersonalityEngine.processInput() for all user interactions
- [ ] Create new tools instead of new agents
- [ ] Use unified system prompt
- [ ] Never reference separate "agents" in UI
- [ ] Always use unified voice (no pitch/rate changes)

## Architecture Diagrams

### Before (6 Agents)
```
User Input
  ↓
MetaAI (coordinator)
  ↓
├─ MotorAI (pitch=0.9, rate=1.1)
├─ EmotionAI (pitch=1.1, rate=0.95)
├─ MemoryAI (pitch=1.0, rate=0.9)
├─ PredictiveAI (pitch=1.05, rate=1.0)
├─ RewardAI (pitch=1.1, rate=1.1)
└─ MetaAI (pitch=0.95, rate=0.95)
  ↓
6 Different Voices
```

### After (PersonalityEngine)
```
User Input
  ↓
PersonalityEngine (unified LLM)
  ↓
Intent Analysis
  ↓
Tool Selection
  ↓
├─ DeviceControlTool
├─ SentimentAnalysisTool
├─ MemoryRetrievalTool
├─ PatternAnalysisTool
└─ FeedbackTrackingTool
  ↓
Unified Response Generation
  ↓
ONE Unified Voice (pitch=1.0, rate=1.0)
```

## Key Principles

1. **One Personality**: Users should NEVER know there are separate tools/agents
2. **Consistent Voice**: pitch=1.0, speechRate=1.0 for ALL responses
3. **Natural Language**: Never say "my vision system" or "emotion module"
4. **Context Aware**: Use conversation history to maintain coherence
5. **Tool Abstraction**: Tools are invisible - only capabilities matter

## Common Mistakes to Avoid

❌ **Don't**: Call speakAsAgent() with different agent names
✅ **Do**: Use PersonalityEngine which handles unified voice

❌ **Don't**: Create separate UI for different agents
✅ **Do**: Single chat interface for unified personality

❌ **Don't**: Expose tool names in responses ("My DeviceControlTool activated...")
✅ **Do**: Natural language ("I've turned on the flashlight")

❌ **Don't**: Change voice parameters based on context
✅ **Do**: Keep voice consistent, vary LLM response tone

## Performance Considerations

- **Tool Execution**: Tools run in parallel when possible (Dispatchers.IO)
- **Conversation History**: Limited to last 20 turns (configurable)
- **LLM Calls**: One per user input (unified generation)
- **Memory Footprint**: Tools are lightweight wrappers around existing agents

## Next Steps (Priority 2+)

1. **Implement Additional Tools**:
   - PatternAnalysisTool (from PredictiveAI)
   - FeedbackTrackingTool (from RewardAI)
   - VisionAnalysisTool (camera + MobileNetV3)

2. **Optimize LLM Performance**:
   - Implement 4-bit quantization
   - Enable GPU acceleration (NNAPI)
   - Consider SmolLM2-360M for speed

3. **Implement Memory System**:
   - Integrate vector database (ChromaDB/FAISS)
   - RAG for conversation continuity
   - Persistent memory storage

4. **Remove Deprecated Code**:
   - Delete speakAsAgent() method
   - Remove per-agent personality prompts
   - Clean up old agent coordination code

## Troubleshooting

### Issue: PersonalityEngine not responding
**Check**:
- MessageBus is started before PersonalityEngine
- Tools are registered
- LLMManager is initialized
- TTS is initialized

### Issue: Voice still varies
**Check**:
- Remove all speakAsAgent() calls
- Verify personalityEngine uses speakUnified()
- Check TTSManager pitch/rate are 1.0f

### Issue: Tools not executing
**Check**:
- Tool.isAvailable() returns true
- Parameters are valid
- Permissions granted (for DeviceControlTool)
- Check logs for tool errors

## Documentation

- **Architecture**: `PERSONALITY_ENGINE_DESIGN.md`
- **Vision**: `VISION.md`
- **API Reference**: See inline KDoc in source files

## Support

For questions or issues:
1. Review this guide
2. Check `PERSONALITY_ENGINE_DESIGN.md`
3. Review inline documentation in source files
4. Check MessageBus logs for debugging

## Success Metrics

✅ **User Experience**:
- [ ] Users perceive ONE AI, not multiple agents
- [ ] Voice is consistent across all interactions
- [ ] Responses are coherent and contextual

✅ **Technical**:
- [ ] All interactions route through PersonalityEngine
- [ ] No speakAsAgent() calls remain
- [ ] Tools execute successfully
- [ ] Conversation context maintained
- [ ] No critical bugs

✅ **Code Quality**:
- [ ] Clean architecture
- [ ] Well-documented
- [ ] Integration tests passing
- [ ] Performance meets targets (<500ms LLM)

---

**Created**: October 29, 2025
**Phase**: 1 (Foundation) - COMPLETE
**Status**: Ready for Integration
**Next Phase**: Tool Expansion + Performance Optimization
