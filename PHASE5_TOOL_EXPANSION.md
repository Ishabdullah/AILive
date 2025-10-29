# Phase 5: Tool Expansion

**Status**: 🚧 IN PROGRESS
**Priority**: 3 (Medium - enhances capabilities)
**Goal**: Expand PersonalityEngine capabilities with vision, prediction, and learning tools

---

## 🎯 Objectives

Currently, PersonalityEngine has 3 basic tools:
1. ✅ SentimentAnalysisTool (emotion understanding)
2. ✅ DeviceControlTool (camera, sensors, flashlight)
3. ✅ MemoryRetrievalTool (placeholder, returns empty)

This phase will add 3 more tools and enhance existing ones:
1. 🔄 **PatternAnalysisTool** - Predictive capabilities (from PredictiveAI)
2. 🔄 **VisionAnalysisTool** - Camera vision processing (from MotorAI)
3. 🔄 **FeedbackTrackingTool** - Learning from user feedback (from RewardAI)
4. 🔄 **Enhance MemoryRetrievalTool** - Add actual memory storage

**Success Criteria**:
- All 6 tools implemented and registered
- Camera frames processed by VisionAnalysisTool
- Pattern recognition working
- Memory persistence working
- Feedback tracking functional
- No performance regression

---

## 📋 Tools to Implement

### 1. PatternAnalysisTool (Priority: High)
**Purpose**: Analyze patterns and make predictions

**Current State**:
- PredictiveAI exists but separated
- Has pattern recognition capabilities
- Not integrated with PersonalityEngine

**Implementation**:
```kotlin
class PatternAnalysisTool(
    private val predictiveAI: PredictiveAI
) : BaseTool() {

    override val name = "analyze_patterns"
    override val description = "Analyzes patterns in user behavior and makes predictions"

    override suspend fun executeInternal(params: Map<String, Any>): ToolResult {
        val data = params["data"] as? List<*>
        val timeWindow = params["time_window"] as? Long ?: 3600000L // 1 hour

        // Analyze patterns
        val patterns = predictiveAI.analyzePatterns(data, timeWindow)

        return ToolResult.Success(
            data = PatternAnalysisResult(
                patterns = patterns,
                predictions = generatePredictions(patterns),
                confidence = calculateConfidence(patterns)
            )
        )
    }
}
```

**Integration Points**:
- Intent: PREDICTION
- User queries: "What will happen?", "Predict...", "What's next?"
- Use cases: Predict user needs, anticipate actions

---

### 2. VisionAnalysisTool (Priority: Critical)
**Purpose**: Process camera frames and understand visual context

**Current State**:
- CameraManager exists and captures frames
- MobileNetV3 model available
- ImageAnalysis callback not connected to PersonalityEngine

**Implementation**:
```kotlin
class VisionAnalysisTool(
    private val context: Context,
    private val modelManager: ModelManager
) : BaseTool() {

    override val name = "analyze_vision"
    override val description = "Analyzes what the camera sees using computer vision"

    override suspend fun executeInternal(params: Map<String, Any>): ToolResult {
        val imageData = params["image"] as? ByteArray

        if (imageData == null) {
            return ToolResult.Failure(
                error = IllegalArgumentException("No image data"),
                reason = "Camera image required",
                recoverable = true
            )
        }

        // Run inference
        val results = modelManager.classifyImage(imageData)

        return ToolResult.Success(
            data = VisionAnalysisResult(
                objects = results.map { it.label },
                confidence = results.maxOfOrNull { it.confidence } ?: 0f,
                description = generateDescription(results)
            )
        )
    }
}
```

**Integration Points**:
- Intent: VISION
- User queries: "What do you see?", "Look at this", "Identify this"
- Camera pipeline: Connect ImageAnalysis → VisionAnalysisTool → PersonalityEngine

**Camera Pipeline Flow**:
```
Camera Frame → CameraManager.ImageAnalysis.Analyzer
            → Store latest frame in buffer
            → PersonalityEngine requests vision
            → VisionAnalysisTool processes frame
            → Returns object detection results
            → UnifiedPrompt includes vision context
```

---

### 3. FeedbackTrackingTool (Priority: Medium)
**Purpose**: Learn from user feedback (thumbs up/down, corrections)

**Current State**:
- RewardAI exists with feedback tracking
- Not integrated with PersonalityEngine
- No UI for feedback collection

**Implementation**:
```kotlin
class FeedbackTrackingTool(
    private val rewardAI: RewardAI
) : BaseTool() {

    override val name = "track_feedback"
    override val description = "Records user feedback to improve responses"

    override suspend fun executeInternal(params: Map<String, Any>): ToolResult {
        val responseId = params["response_id"] as? String
        val feedback = params["feedback"] as? String // "positive" or "negative"
        val correction = params["correction"] as? String // Optional correction

        // Record feedback
        rewardAI.recordFeedback(responseId, feedback, correction)

        // Update reward model
        val rewardUpdate = rewardAI.updateRewards(responseId, feedback)

        return ToolResult.Success(
            data = FeedbackResult(
                recorded = true,
                rewardDelta = rewardUpdate,
                message = "Feedback recorded, thank you!"
            )
        )
    }
}
```

**Integration Points**:
- Future UI: Add thumbs up/down buttons
- Implicit feedback: Track if user repeats query (negative signal)
- Learning: Adjust response strategies based on feedback

---

### 4. Enhanced MemoryRetrievalTool (Priority: High)
**Purpose**: Actually store and retrieve memories

**Current State**:
- Placeholder that returns empty list
- MemoryAI exists but not used

**Enhancement**:
```kotlin
class MemoryRetrievalTool(
    private val memoryAI: MemoryAI
) : BaseTool() {

    override suspend fun executeInternal(params: Map<String, Any>): ToolResult {
        val query = params["query"] as? String
        val limit = params["limit"] as? Int ?: 5

        // ENHANCED: Actually retrieve from memory
        val memories = memoryAI.search(query ?: "", limit)

        // ENHANCED: Store current interaction
        if (params.containsKey("store")) {
            val content = params["store"] as String
            memoryAI.store(content, System.currentTimeMillis())
        }

        return ToolResult.Success(
            data = MemoryResult(
                memories = memories,
                count = memories.size,
                relevance = calculateRelevance(memories, query)
            )
        )
    }
}
```

**Integration Points**:
- Intent: MEMORY
- User queries: "Remember this", "What did I say?", "Recall..."
- Auto-storage: Store important conversations automatically

---

## 🔧 Implementation Plan

### Step 1: VisionAnalysisTool (Camera Integration)
**Why First**: Most visible feature, camera already working

**Tasks**:
1. Create VisionAnalysisTool.kt
2. Connect to existing ModelManager
3. Add frame buffering to CameraManager
4. Connect ImageAnalysis callback to buffer
5. PersonalityEngine requests latest frame on VISION intent
6. Test with "What do you see?" queries

**Files**:
- `app/src/main/java/com/ailive/personality/tools/VisionAnalysisTool.kt` (new)
- `app/src/main/java/com/ailive/camera/CameraManager.kt` (modify)
- `app/src/main/java/com/ailive/core/AILiveCore.kt` (register tool)

---

### Step 2: PatternAnalysisTool (Predictions)
**Why Second**: Adds predictive capability

**Tasks**:
1. Create PatternAnalysisTool.kt
2. Connect to existing PredictiveAI
3. Add pattern data collection
4. Implement prediction generation
5. Register with PersonalityEngine
6. Test with "What will happen?" queries

**Files**:
- `app/src/main/java/com/ailive/personality/tools/PatternAnalysisTool.kt` (new)
- `app/src/main/java/com/ailive/core/AILiveCore.kt` (register tool)

---

### Step 3: Enhanced MemoryRetrievalTool (Storage)
**Why Third**: Enables conversation continuity

**Tasks**:
1. Enhance MemoryRetrievalTool.kt
2. Add actual MemoryAI integration
3. Implement storage logic
4. Add auto-storage of important conversations
5. Test with "Remember this" queries

**Files**:
- `app/src/main/java/com/ailive/personality/tools/MemoryRetrievalTool.kt` (modify)

---

### Step 4: FeedbackTrackingTool (Learning)
**Why Last**: Nice-to-have, requires UI changes

**Tasks**:
1. Create FeedbackTrackingTool.kt
2. Connect to RewardAI
3. Add feedback recording
4. (Optional) Add UI buttons for thumbs up/down
5. Register with PersonalityEngine

**Files**:
- `app/src/main/java/com/ailive/personality/tools/FeedbackTrackingTool.kt` (new)
- `app/src/main/java/com/ailive/core/AILiveCore.kt` (register tool)

---

## 📊 Tool Selection Logic Updates

**Current** (PersonalityEngine.kt:224-258):
```kotlin
private fun selectTools(intent: Intent): List<AITool> {
    return when (intent.primary) {
        IntentType.VISION -> listOfNotNull(
            toolRegistry.getTool("control_device")  // For camera
        )
        IntentType.PREDICTION -> listOfNotNull(
            toolRegistry.getTool("analyze_sentiment")  // Placeholder
        )
        // ...
    }
}
```

**Updated**:
```kotlin
private fun selectTools(intent: Intent): List<AITool> {
    return when (intent.primary) {
        IntentType.VISION -> listOfNotNull(
            toolRegistry.getTool("analyze_vision"),    // NEW!
            toolRegistry.getTool("control_device")
        )
        IntentType.PREDICTION -> listOfNotNull(
            toolRegistry.getTool("analyze_patterns"),  // NEW!
            toolRegistry.getTool("analyze_sentiment")
        )
        IntentType.MEMORY -> listOfNotNull(
            toolRegistry.getTool("retrieve_memory")    // Enhanced!
        )
        IntentType.CONVERSATION -> listOfNotNull(
            toolRegistry.getTool("analyze_sentiment"),
            toolRegistry.getTool("retrieve_memory")    // Add context
        )
        // ...
    }
}
```

---

## 🎯 Success Criteria

### Functional Requirements:
1. ✅ VisionAnalysisTool processes camera frames
2. ✅ PatternAnalysisTool generates predictions
3. ✅ MemoryRetrievalTool stores and retrieves
4. ✅ FeedbackTrackingTool records feedback
5. ✅ All tools registered and working
6. ✅ No crashes or errors

### Performance Requirements:
1. ✅ Vision processing: <200ms per frame
2. ✅ Pattern analysis: <500ms
3. ✅ Memory retrieval: <100ms
4. ✅ Feedback tracking: <50ms
5. ✅ No UI lag or freezing

### Quality Requirements:
1. ✅ Vision: Accurate object detection
2. ✅ Patterns: Meaningful predictions
3. ✅ Memory: Relevant retrieval
4. ✅ Feedback: Proper recording
5. ✅ Integration: Seamless with PersonalityEngine

---

## 📈 Expected Improvements

| Capability | Before | After |
|------------|--------|-------|
| Vision | Camera captures but unused | Real-time object detection |
| Prediction | Basic sentiment analysis | Pattern-based predictions |
| Memory | No storage | Persistent conversation memory |
| Learning | No feedback loop | User feedback tracking |
| Tool Count | 3 tools | 6 tools |
| Intelligence | Limited | Multi-modal AI |

---

## 🧪 Testing Plan

### Test 1: Vision Integration
```
User: "What do you see?"
Expected:
- Camera frame captured
- VisionAnalysisTool processes
- Returns: "I can see a [detected objects]"
```

### Test 2: Pattern Prediction
```
User: "What will I do next?"
Expected:
- PatternAnalysisTool analyzes history
- Returns: "Based on patterns, you might [prediction]"
```

### Test 3: Memory Storage
```
User: "Remember that my favorite color is blue"
Expected:
- MemoryRetrievalTool stores
- Returns: "I'll remember that your favorite color is blue"

Later:
User: "What's my favorite color?"
Expected:
- MemoryRetrievalTool retrieves
- Returns: "Your favorite color is blue"
```

### Test 4: Feedback Tracking
```
User gives thumbs down on response
Expected:
- FeedbackTrackingTool records
- RewardAI updates
- Future responses adjust
```

---

## 📝 Files to Create/Modify

### New Files:
1. `VisionAnalysisTool.kt`
2. `PatternAnalysisTool.kt`
3. `FeedbackTrackingTool.kt`

### Modified Files:
1. `MemoryRetrievalTool.kt` (enhance)
2. `PersonalityEngine.kt` (update tool selection)
3. `CameraManager.kt` (add frame buffering)
4. `AILiveCore.kt` (register new tools)

### Documentation:
1. `PHASE5_TOOL_EXPANSION.md` (this file)
2. `PHASE5_COMPLETE.md` (after completion)

---

## 🚀 Quick Start

**Start with VisionAnalysisTool** (highest impact, camera already working):

1. Create `VisionAnalysisTool.kt`
2. Add frame buffering to `CameraManager`
3. Register in `AILiveCore`
4. Update tool selection in `PersonalityEngine`
5. Test with "What do you see?"

Then proceed with other tools in order of priority.

---

**Phase 5 Status**: 🚧 IN PROGRESS
**Current Step**: Creating VisionAnalysisTool
**Next Steps**: PatternAnalysisTool, MemoryRetrievalTool, FeedbackTrackingTool

---

*Phase 5 started: October 29, 2025 @ 22:10 UTC*
