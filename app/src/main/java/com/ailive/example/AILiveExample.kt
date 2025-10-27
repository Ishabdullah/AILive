package com.ailive.example

import com.ailive.core.messaging.*
import com.ailive.core.state.StateManager
import com.ailive.core.types.AgentType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/**
 * Example demonstrating MessageBus and StateManager usage.
 */
class AILiveExample {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val messageBus = MessageBus(scope)
    private val stateManager = StateManager()
    
    fun start() {
        // Start the message bus
        messageBus.start()
        
        // Subscribe to all messages
        scope.launch {
            messageBus.subscribe().collect { message ->
                handleMessage(message)
            }
        }
        
        // Subscribe to specific message types
        scope.launch {
            messageBus.subscribe(AIMessage.Perception.VisualDetection::class.java)
                .collect { detection ->
                    println("Visual object detected: ${detection.objects.size} objects")
                }
        }
        
        // Subscribe to specific agent
        scope.launch {
            messageBus.subscribeToAgent(AgentType.EMOTION_AI)
                .collect { message ->
                    println("Emotion AI message: $message")
                }
        }
        
        // Simulate publishing messages
        simulateAgents()
    }
    
    private fun simulateAgents() {
        scope.launch {
            // Visual AI detects something
            val visualMsg = AIMessage.Perception.VisualDetection(
                objects = listOf(
                    DetectedObject("person", BoundingBox(100, 100, 50, 100), 0.95f)
                ),
                imageEmbeddingId = "emb_12345",
                confidence = 0.95f
            )
            messageBus.publish(visualMsg)
            
            // Update state
            stateManager.updatePerception { perception ->
                perception.copy(visualObjects = perception.visualObjects + visualMsg)
            }
            
            delay(100)
            
            // Meta AI sets a goal
            val goalMsg = AIMessage.Control.GoalSet(
                goal = "Take a picture of the person",
                subGoals = listOf("Center object", "Focus camera", "Capture"),
                deadline = System.currentTimeMillis() + 5000
            )
            messageBus.publish(goalMsg)
            
            stateManager.updateMeta { meta ->
                meta.copy(currentGoal = goalMsg.goal, subGoals = goalMsg.subGoals)
            }
            
            delay(100)
            
            // Motor AI executes action
            val actionMsg = AIMessage.Motor.ActionExecuted(
                actionId = "capture_001",
                success = true,
                feedback = mapOf("image_path" to "/storage/img_001.jpg")
            )
            messageBus.publish(actionMsg)
            
            // Print stats
            delay(500)
            val stats = messageBus.getStats()
            println("Bus stats: $stats")
            println("State snapshot: ${stateManager.getSnapshot()}")
        }
    }
    
    private fun handleMessage(message: AIMessage) {
        // Central message handler (acts like Meta AI orchestrator)
        when (message) {
            is AIMessage.Perception.VisualDetection -> {
                println("[${message.timestamp}] Visual: ${message.objects.size} objects detected")
            }
            is AIMessage.Perception.EmotionVector -> {
                println("[${message.timestamp}] Emotion: valence=${message.valence}, urgency=${message.urgency}")
            }
            is AIMessage.Control.GoalSet -> {
                println("[${message.timestamp}] Goal: ${message.goal}")
            }
            is AIMessage.System.ErrorOccurred -> {
                println("[${message.timestamp}] ERROR in ${message.source}: ${message.error.message}")
            }
            else -> {
                // Handle other message types
            }
        }
    }
    
    fun stop() {
        messageBus.stop()
        scope.cancel()
    }
}

// Run it
fun main() {
    val example = AILiveExample()
    example.start()
    Thread.sleep(2000)
    example.stop()
}
