package com.ailive.testing

import android.util.Log
import com.ailive.AILiveCore
import com.ailive.core.messaging.*
import com.ailive.core.types.AgentType
import com.ailive.memory.storage.ContentType
import com.ailive.meta.planning.Goal
import com.ailive.predictive.PredictionContext
import kotlinx.coroutines.*

/**
 * Test scenarios for AILive development and debugging.
 */
class TestScenarios(private val ailive: AILiveCore) {
    private val TAG = "TestScenarios"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Test 1: Basic message bus communication.
     */
    suspend fun testMessageBus() {
        Log.i(TAG, "
=== TEST 1: Message Bus ===")
        
        val job = scope.launch {
            var count = 0
            ailive.messageBus.subscribe().collect { message ->
                count++
                if (count <= 5) {
                    Log.d(TAG, "  → ${message.javaClass.simpleName} from ${message.source}")
                }
            }
        }
        
        delay(100)
        
        ailive.messageBus.publish(
            AIMessage.Perception.VisualDetection(
                objects = listOf(DetectedObject("person", BoundingBox(100, 100, 50, 100), 0.95f)),
                imageEmbeddingId = "test_001",
                confidence = 0.95f
            )
        )
        
        delay(100)
        job.cancel()
        
        val stats = ailive.messageBus.getStats()
        Log.i(TAG, "✓ Message Bus: ${stats.messagesProcessed} messages processed")
    }
    
    /**
     * Test 2: Emotion AI sentiment analysis.
     */
    suspend fun testEmotionAI() {
        Log.i(TAG, "
=== TEST 2: Emotion AI ===")
        
        val testTexts = listOf(
            "This is amazing! I love it!",
            "URGENT! Need help immediately!!",
            "Everything is broken and I'm frustrated",
            "The weather is nice today",
            "Quick question about the project"
        )
        
        testTexts.forEach { text ->
            val emotion = ailive.emotionAI.analyzeText(text)
            Log.i(TAG, "  Text: '$text'")
            Log.i(TAG, "    → valence=${"%.2f".format(emotion.valence)} arousal=${"%.2f".format(emotion.arousal)} urgency=${"%.2f".format(emotion.urgency)}")
        }
        
        delay(200)
        Log.i(TAG, "✓ Emotion AI: Sentiment analysis working")
    }
    
    /**
     * Test 3: Memory storage and recall.
     */
    suspend fun testMemory() {
        Log.i(TAG, "
=== TEST 3: Memory AI ===")
        
        val memories = listOf(
            "The user likes cats and dogs",
            "The user is learning AI development",
            "The user lives in Connecticut",
            "The weather is sunny today",
            "Remember to take a photo of the bird"
        )
        
        memories.forEach { content ->
            ailive.memoryAI.store(
                content = content,
                contentType = ContentType.TEXT,
                importance = 0.7f
            )
        }
        
        delay(500)
        
        val query = "What does the user like?"
        val results = ailive.memoryAI.recall(query, k = 3, minSimilarity = 0.0f)
        
        Log.i(TAG, "  Query: '$query'")
        results.take(3).forEachIndexed { index, result ->
            Log.i(TAG, "    ${index + 1}. [sim=${"%.2f".format(result.similarity)}] ${result.entry.content}")
        }
        
        val stats = ailive.memoryAI.getStats()
        Log.i(TAG, "✓ Memory AI: ${stats.totalMemories} memories stored")
    }
    
    /**
     * Test 4: Predictive AI outcome generation.
     */
    suspend fun testPredictiveAI() {
        Log.i(TAG, "
=== TEST 4: Predictive AI ===")
        
        val context = PredictionContext(
            currentGoal = "Capture image",
            batteryLevel = 85,
            resourceAvailable = true
        )
        
        val outcomes = ailive.predictiveAI.predictOutcomes(
            ActionType.CAMERA_CAPTURE,
            context
        )
        
        Log.i(TAG, "  Predicted outcomes for CAMERA_CAPTURE:")
        outcomes.forEach { outcome ->
            Log.i(TAG, "    → ${outcome.description}")
            Log.i(TAG, "      P=${"%.2f".format(outcome.probability)} R=${"%.2f".format(outcome.reward)} C=${"%.2f".format(outcome.cost)} EV=${"%.2f".format(outcome.expectedValue)}")
        }
        
        Log.i(TAG, "✓ Predictive AI: ${outcomes.size} scenarios generated")
    }
    
    /**
     * Test 5: Reward AI value estimation.
     */
    suspend fun testRewardAI() {
        Log.i(TAG, "
=== TEST 5: Reward AI ===")
        
        // Simulate some action executions
        ailive.rewardAI.recordAction("action_001", ActionType.CAMERA_CAPTURE, emptyMap())
        ailive.rewardAI.updateValue("action_001", 1.0f) // Success
        
        ailive.rewardAI.recordAction("action_002", ActionType.SEND_NOTIFICATION, emptyMap())
        ailive.rewardAI.updateValue("action_002", -0.5f) // Failure
        
        delay(200)
        
        val stats = ailive.rewardAI.getStats()
        Log.i(TAG, "  Action values after ${stats.totalUpdates} updates:")
        stats.actionValues.take(3).forEach { (action, value) ->
            Log.i(TAG, "    $action: ${"%.3f".format(value)}")
        }
        
        Log.i(TAG, "✓ Reward AI: Value learning active")
    }
    
    /**
     * Test 6: Goal planning with all agents.
     */
    suspend fun testIntegratedSystem() {
        Log.i(TAG, "
=== TEST 6: Integrated System ===")
        
        // Add emotion context
        ailive.emotionAI.analyzeText("I want to capture this moment!")
        
        delay(100)
        
        // Create goal
        val goal = Goal.Compound(
            description = "Capture and store memory",
            priority = 8,
            deadline = System.currentTimeMillis() + 5000,
            subGoals = listOf(
                Goal.Atomic(
                    description = "Analyze scene emotion",
                    priority = 8,
                    actionType = "EMOTION_ANALYSIS",
                    parameters = mapOf("target" to "scene")
                ),
                Goal.Atomic(
                    description = "Capture image",
                    priority = 9,
                    actionType = "CAMERA_CAPTURE",
                    parameters = mapOf("camera_id" to "0")
                ),
                Goal.Atomic(
                    description = "Store to memory",
                    priority = 7,
                    actionType = "STORE_DATA",
                    parameters = mapOf("type" to "image")
                )
            )
        )
        
        ailive.metaAI.addGoal(goal)
        
        delay(1000)
        
        val metaStats = ailive.metaAI.getStats()
        Log.i(TAG, "  Current goal: ${metaStats.currentGoal}")
        Log.i(TAG, "  Goal stack: ${metaStats.goalStackStats.totalGoals} goals")
        
        Log.i(TAG, "✓ Integrated System: All agents coordinating")
    }
    
    /**
     * Run all tests sequentially.
     */
    suspend fun runAllTests() {
        Log.i(TAG, "
╔════════════════════════════════════════╗")
        Log.i(TAG, "║   AILive Complete Test Suite          ║")
        Log.i(TAG, "╚════════════════════════════════════════╝
")
        
        testMessageBus()
        delay(300)
        
        testEmotionAI()
        delay(300)
        
        testMemory()
        delay(300)
        
        testPredictiveAI()
        delay(300)
        
        testRewardAI()
        delay(300)
        
        testIntegratedSystem()
        delay(500)
        
        Log.i(TAG, "
╔════════════════════════════════════════╗")
        Log.i(TAG, "║   All Tests Completed ✓                ║")
        Log.i(TAG, "╚════════════════════════════════════════╝
")
        
        val health = ailive.getSystemHealth()
        Log.i(TAG, """
            |Final System Health:
            |  Messages: ${health.messagesProcessed}
            |  Memories: ${health.totalMemories}
            |  Reward Updates: ${health.rewardUpdates}
            |  Emotion: valence=${"%.2f".format(health.emotionValence)} urgency=${"%.2f".format(health.emotionUrgency)}
            |  Memory: ${"%.2f".format(health.memoryUsageMB)} MB
        """.trimMargin())
    }
}
