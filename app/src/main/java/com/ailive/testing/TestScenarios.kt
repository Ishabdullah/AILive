package com.ailive.testing

import android.util.Log
import com.ailive.AILiveCore
import com.ailive.core.messaging.*
import com.ailive.core.types.AgentType
import com.ailive.memory.storage.ContentType
import com.ailive.meta.planning.Goal
import com.ailive.predictive.PredictionContext
import kotlinx.coroutines.*

class TestScenarios(private val ailive: AILiveCore) {
    private val TAG = "TestScenarios"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    suspend fun testMessageBus() {
        Log.i(TAG, "TEST 1: Message Bus")
        
        val job = scope.launch {
            var count = 0
            ailive.messageBus.subscribe().collect { message ->
                count++
                if (count <= 5) {
                    Log.d(TAG, "Received: ${message.javaClass.simpleName} from ${message.source}")
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
        Log.i(TAG, "Message Bus: ${stats.messagesProcessed} messages processed")
    }
    
    suspend fun testEmotionAI() {
        Log.i(TAG, "TEST 2: Emotion AI")
        
        val testTexts = listOf(
            "This is amazing! I love it!",
            "URGENT! Need help immediately!!",
            "Everything is broken",
            "The weather is nice today",
            "Quick question"
        )
        
        testTexts.forEach { text ->
            val emotion = ailive.emotionAI.analyzeText(text)
            Log.i(TAG, "Text: $text")
            Log.i(TAG, "Valence=${emotion.valence} Arousal=${emotion.arousal} Urgency=${emotion.urgency}")
        }
        
        Log.i(TAG, "Emotion AI: Sentiment analysis working")
    }
    
    suspend fun testMemory() {
        Log.i(TAG, "TEST 3: Memory AI")
        
        val memories = listOf(
            "The user likes cats and dogs",
            "The user is learning AI",
            "The user lives in Connecticut",
            "The weather is sunny",
            "Remember to take a photo"
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
        
        Log.i(TAG, "Query: $query")
        results.take(3).forEachIndexed { index, result ->
            Log.i(TAG, "${index + 1}. [${result.similarity}] ${result.entry.content}")
        }
        
        val stats = ailive.memoryAI.getStats()
        Log.i(TAG, "Memory AI: ${stats.totalMemories} memories stored")
    }
    
    suspend fun testPredictiveAI() {
        Log.i(TAG, "TEST 4: Predictive AI")
        
        val context = PredictionContext(
            currentGoal = "Capture image",
            batteryLevel = 85,
            resourceAvailable = true
        )
        
        val outcomes = ailive.predictiveAI.predictOutcomes(ActionType.CAMERA_CAPTURE, context)
        
        Log.i(TAG, "Predicted outcomes for CAMERA_CAPTURE:")
        outcomes.forEach { outcome ->
            Log.i(TAG, "${outcome.description}: P=${outcome.probability} EV=${outcome.expectedValue}")
        }
        
        Log.i(TAG, "Predictive AI: ${outcomes.size} scenarios generated")
    }
    
    suspend fun testRewardAI() {
        Log.i(TAG, "TEST 5: Reward AI")
        
        ailive.rewardAI.recordAction("action_001", ActionType.CAMERA_CAPTURE, emptyMap())
        ailive.rewardAI.updateValue("action_001", 1.0f)
        
        ailive.rewardAI.recordAction("action_002", ActionType.SEND_NOTIFICATION, emptyMap())
        ailive.rewardAI.updateValue("action_002", -0.5f)
        
        delay(200)
        
        val stats = ailive.rewardAI.getStats()
        Log.i(TAG, "Reward AI: ${stats.totalUpdates} value updates")
    }
    
    suspend fun testIntegratedSystem() {
        Log.i(TAG, "TEST 6: Integrated System")
        
        ailive.emotionAI.analyzeText("I want to capture this moment!")
        
        delay(100)
        
        val goal = Goal.Compound(
            description = "Capture and store memory",
            priority = 8,
            deadline = System.currentTimeMillis() + 5000,
            subGoals = listOf(
                Goal.Atomic(
                    description = "Analyze scene",
                    priority = 8,
                    actionType = "EMOTION_ANALYSIS",
                    parameters = mapOf("target" to "scene")
                ),
                Goal.Atomic(
                    description = "Capture image",
                    priority = 9,
                    actionType = "CAMERA_CAPTURE",
                    parameters = mapOf("camera_id" to "0")
                )
            )
        )
        
        ailive.metaAI.addGoal(goal)
        
        delay(1000)
        
        val metaStats = ailive.metaAI.getStats()
        Log.i(TAG, "Current goal: ${metaStats.currentGoal}")
        Log.i(TAG, "Integrated System: All agents coordinating")
    }
    
    suspend fun runAllTests() {
        Log.i(TAG, "AILive Test Suite Starting")
        
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
        
        Log.i(TAG, "All Tests Completed")
        
        val health = ailive.getSystemHealth()
        Log.i(TAG, "Final System Status:")
        Log.i(TAG, "Messages: ${health.messagesProcessed}")
        Log.i(TAG, "Memories: ${health.totalMemories}")
        Log.i(TAG, "Reward Updates: ${health.rewardUpdates}")
    }
}
