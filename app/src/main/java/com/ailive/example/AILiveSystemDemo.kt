package com.ailive.example

import com.ailive.core.messaging.*
import com.ailive.core.state.StateManager
import com.ailive.meta.MetaAI
import com.ailive.meta.planning.Goal
import com.ailive.motor.MotorAI
import kotlinx.coroutines.*

/**
 * Complete AILive system demo with Meta AI.
 */
class AILiveSystemDemo(activity: androidx.fragment.app.FragmentActivity) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val messageBus = MessageBus(scope)
    private val stateManager = StateManager()
    
    private val motorAI = MotorAI(activity, activity, messageBus, stateManager)
    private val metaAI = MetaAI(messageBus, stateManager)
    
    fun start() {
        // Start components in order
        messageBus.start()
        motorAI.start()
        metaAI.start()
        
        // Demo: Add a compound goal
        scope.launch {
            delay(1000)
            
            val captureGoal = Goal.Compound(
                description = "Take a picture of a person",
                priority = 8,
                deadline = System.currentTimeMillis() + 10000,
                subGoals = listOf(
                    Goal.Atomic(
                        description = "Detect person",
                        priority = 8,
                        actionType = "VISUAL_DETECTION",
                        parameters = mapOf("target" to "person")
                    ),
                    Goal.Atomic(
                        description = "Capture image",
                        priority = 8,
                        actionType = "CAMERA_CAPTURE",
                        parameters = mapOf("camera_id" to "0")
                    )
                )
            )
            
            metaAI.addGoal(captureGoal)
            
            // Print stats every 2 seconds
            repeat(10) {
                delay(2000)
                val stats = metaAI.getStats()
                println("=== AILive Stats ===")
                println("Current Goal: ${stats.currentGoal}")
                println("Goals: ${stats.goalStackStats}")
                println("Resources: ${stats.resourceUsage}")
                println("Emotion: urgency=${stats.emotionContext.urgency}")
                println()
            }
        }
    }
    
    fun stop() {
        metaAI.stop()
        motorAI.stop()
        messageBus.stop()
        scope.cancel()
    }
}
