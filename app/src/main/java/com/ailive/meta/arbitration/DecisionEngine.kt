package com.ailive.meta.arbitration

import android.util.Log
import com.ailive.core.messaging.AIMessage
import com.ailive.core.messaging.MessagePriority
import com.ailive.core.types.AgentType
import com.ailive.meta.planning.Goal
import com.ailive.meta.planning.GoalContext

/**
 * Rule-based decision engine for Meta AI.
 * Evaluates action requests and makes approval/rejection decisions.
 */
class DecisionEngine {
    private val TAG = "DecisionEngine"
    
    /**
     * Evaluate an action request and decide approval.
     */
    fun evaluateAction(
        request: AIMessage.Control.ActionRequest,
        currentGoal: GoalContext?,
        emotionState: EmotionContext,
        resourceAvailable: Boolean
    ): ActionDecision {
        
        Log.d(TAG, "Evaluating action: ${request.actionType} from ${request.source}")
        
        // Rule 1: Check if action aligns with current goal
        if (currentGoal != null && !isActionRelevant(request, currentGoal.goal)) {
            return ActionDecision.Reject("Action not relevant to current goal: ${currentGoal.goal.description}")
        }
        
        // Rule 2: Check resource availability
        if (!resourceAvailable) {
            return ActionDecision.Defer("Insufficient resources", retryAfter = 5000)
        }
        
        // Rule 3: Check urgency from emotion
        if (emotionState.urgency > 0.8f && request.priority.value < MessagePriority.HIGH.value) {
            return ActionDecision.Defer("Higher priority actions needed due to urgency")
        }
        
        // Rule 4: Check if action is from trusted agent
        val trustScore = getAgentTrust(request.source)
        if (trustScore < 0.5f) {
            return ActionDecision.Reject("Agent trust score too low: $trustScore")
        }
        
        // Rule 5: Time-based decisions
        if (currentGoal != null) {
            val timeRemaining = (currentGoal.goal.deadline ?: Long.MAX_VALUE) - System.currentTimeMillis()
            if (timeRemaining < 1000 && request.priority.value < MessagePriority.CRITICAL.value) {
                return ActionDecision.Defer("Goal deadline approaching, prioritizing critical actions")
            }
        }
        
        // Default: Approve
        val confidence = calculateConfidence(request, currentGoal, emotionState)
        return ActionDecision.Approve(
            confidence = confidence,
            reasoning = "Action aligns with current context"
        )
    }
    
    /**
     * Select next goal from candidates.
     */
    fun selectNextGoal(
        candidates: List<GoalContext>,
        emotionState: EmotionContext,
        systemLoad: Float
    ): GoalContext? {
        
        if (candidates.isEmpty()) return null
        
        // Score each goal
        val scored = candidates.map { context ->
            val score = scoreGoal(context, emotionState, systemLoad)
            Pair(context, score)
        }
        
        // Return highest scored
        val selected = scored.maxByOrNull { it.second }
        Log.d(TAG, "Selected goal: ${selected?.first?.goal?.description} (score: ${selected?.second})")
        
        return selected?.first
    }
    
    /**
     * Score a goal based on context.
     */
    private fun scoreGoal(
        context: GoalContext,
        emotionState: EmotionContext,
        systemLoad: Float
    ): Float {
        var score = context.goal.priority.toFloat()
        
        // Boost urgent goals
        if (emotionState.urgency > 0.7f) {
            score *= (1 + emotionState.urgency)
        }
        
        // Penalize high-resource goals when system loaded
        if (systemLoad > 0.7f) {
            val goal = context.goal
            if (goal is Goal.Atomic && goal.actionType == "CAMERA_CAPTURE") {
                score *= 0.5f // Camera is resource-intensive
            }
        }
        
        // Penalize goals with many failed attempts
        if (context.attempts > 2) {
            score *= (1.0f / (context.attempts + 1))
        }
        
        // Boost goals near deadline
        context.goal.deadline?.let { deadline ->
            val timeRemaining = deadline - System.currentTimeMillis()
            if (timeRemaining in 1..10000) {
                score *= 2.0f // Double score if deadline within 10 seconds
            }
        }
        
        return score
    }
    
    /**
     * Check if action is relevant to current goal.
     */
    private fun isActionRelevant(request: AIMessage.Control.ActionRequest, goal: Goal): Boolean {
        return when (goal) {
            is Goal.Atomic -> goal.actionType == request.actionType.name
            is Goal.Compound -> goal.subGoals.any { isActionRelevant(request, it) }
            is Goal.Conditional -> true // Always relevant for conditional goals
        }
    }
    
    /**
     * Get agent trust score (static for now, could be learned).
     */
    private fun getAgentTrust(agent: AgentType): Float {
        return when (agent) {
            AgentType.MOTOR_AI -> 1.0f // Always trust motor
            AgentType.VISUAL_AI -> 0.9f
            AgentType.LANGUAGE_AI -> 0.9f
            AgentType.EMOTION_AI -> 0.8f
            AgentType.MEMORY_AI -> 0.95f
            AgentType.PREDICTIVE_AI -> 0.7f
            AgentType.REWARD_AI -> 0.8f
            AgentType.KNOWLEDGE_SCOUT -> 0.6f
            else -> 0.5f
        }
    }
    
    /**
     * Calculate decision confidence.
     */
    private fun calculateConfidence(
        request: AIMessage.Control.ActionRequest,
        currentGoal: GoalContext?,
        emotionState: EmotionContext
    ): Float {
        var confidence = 0.7f // Base confidence
        
        // Boost if aligns with goal
        if (currentGoal != null && isActionRelevant(request, currentGoal.goal)) {
            confidence += 0.2f
        }
        
        // Reduce if high urgency (stress reduces confidence)
        if (emotionState.urgency > 0.8f) {
            confidence -= 0.1f
        }
        
        return confidence.coerceIn(0f, 1f)
    }
}

sealed class ActionDecision {
    data class Approve(
        val confidence: Float,
        val reasoning: String
    ) : ActionDecision()
    
    data class Reject(val reason: String) : ActionDecision()
    
    data class Defer(
        val reason: String,
        val retryAfter: Long? = null
    ) : ActionDecision()
}

data class EmotionContext(
    val valence: Float = 0f,
    val arousal: Float = 0f,
    val urgency: Float = 0f
)
