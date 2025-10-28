package com.ailive.meta

import android.util.Log
import com.ailive.core.messaging.*
import com.ailive.core.state.StateManager
import com.ailive.core.types.AgentType
import com.ailive.meta.arbitration.ActionDecision
import com.ailive.meta.arbitration.DecisionEngine
import com.ailive.meta.arbitration.EmotionContext
import com.ailive.meta.planning.Goal
import com.ailive.meta.planning.GoalStack
import com.ailive.meta.resources.ResourceAllocator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/**
 * Meta AI - AILive's orchestrator and decision-maker.
 * Implements rule-based planning and resource management.
 */
class MetaAI(
    private val messageBus: MessageBus,
    private val stateManager: StateManager
) {
    private val TAG = "MetaAI"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Core components
    private val goalStack = GoalStack()
    private val decisionEngine = DecisionEngine()
    private val resourceAllocator = ResourceAllocator()
    
    // State
    private var currentGoalContext: com.ailive.meta.planning.GoalContext? = null
    private var emotionContext = EmotionContext()
    private var isRunning = false
    
    /**
     * Start Meta AI.
     */
    fun start() {
        if (isRunning) return
        
        isRunning = true
        Log.i(TAG, "Meta AI starting...")
        
        // Subscribe to messages
        subscribeToMessages()
        
        // Start decision loop
        scope.launch {
            decisionLoop()
        }
        
        // Publish startup
        scope.launch {
            messageBus.publish(
                AIMessage.System.AgentStarted(source = AgentType.META_AI)
            )
        }
        
        Log.i(TAG, "Meta AI started")
    }
    
    /**
     * Stop Meta AI.
     */
    fun stop() {
        if (!isRunning) return
        
        Log.i(TAG, "Meta AI stopping...")
        
        goalStack.clear()
        scope.cancel()
        isRunning = false
        
        Log.i(TAG, "Meta AI stopped")
    }
    
    /**
     * Add a new goal to the stack.
     */
    suspend fun addGoal(goal: Goal) {
        goalStack.push(goal)
        
        // Publish goal set message
        messageBus.publish(
            AIMessage.Control.GoalSet(
                goal = goal.description,
                subGoals = if (goal is Goal.Compound) {
                    goal.subGoals.map { it.description }
                } else emptyList(),
                deadline = goal.deadline
            )
        )
        
        // Update state
        stateManager.updateMeta { meta ->
            meta.copy(
                currentGoal = goal.description,
                subGoals = if (goal is Goal.Compound) {
                    goal.subGoals.map { it.description }
                } else emptyList()
            )
        }
        
        Log.i(TAG, "Goal added: ${goal.description}")
    }
    
    /**
     * Main decision loop.
     */
    private suspend fun decisionLoop() {
        while (isRunning) {
            try {
                // Check for next goal
                if (currentGoalContext == null) {
                    selectNextGoal()
                }
                
                // Process current goal
                currentGoalContext?.let { processGoal(it) }
                
                // Monitor resources
                monitorResources()
                
                delay(100) // 10Hz decision rate
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in decision loop", e)
                delay(1000)
            }
        }
    }
    
    /**
     * Select next goal from stack.
     */
    private suspend fun selectNextGoal() {
        val candidates = goalStack.getPendingGoals()
        val systemLoad = resourceAllocator.getCurrentUsage().cpuUtilization
        
        val selected = decisionEngine.selectNextGoal(
            candidates,
            emotionContext,
            systemLoad
        )
        
        if (selected != null) {
            currentGoalContext = selected
            Log.i(TAG, "Executing goal: ${selected.goal.description}")
            
            // Update state
            stateManager.updateMeta { meta ->
                meta.copy(currentGoal = selected.goal.description)
            }
        }
    }
    
    /**
     * Process current goal.
     */
    private suspend fun processGoal(context: com.ailive.meta.planning.GoalContext) {
        val goal = context.goal
        
        when (goal) {
            is Goal.Atomic -> {
                // Create action request
                val request = AIMessage.Control.ActionRequest(
                    source = AgentType.META_AI,
                    actionType = ActionType.valueOf(goal.actionType),
                    params = goal.parameters,
                    expectedFeedback = emptyMap()
                )
                
                // Publish action request
                messageBus.publish(request)
                
                // Mark in progress
                currentGoalContext = context.copy(
                    goal = goal.copy(status = com.ailive.meta.planning.GoalStatus.IN_PROGRESS)
                )
            }
            is Goal.Compound -> {
                // Compound goals are handled by subgoals
                // Check if all subgoals completed
                if (areSubGoalsCompleted(goal)) {
                    goalStack.markCompleted(goal.id)
                    currentGoalContext = null
                    Log.i(TAG, "Compound goal completed: ${goal.description}")
                }
            }
            is Goal.Conditional -> {
                // Conditional goals already evaluated during push
                currentGoalContext = null
            }
        }
    }
    
    /**
     * Check if all subgoals are completed.
     */
    private fun areSubGoalsCompleted(goal: Goal.Compound): Boolean {
        return goal.subGoals.all { subGoal ->
            subGoal.status == com.ailive.meta.planning.GoalStatus.COMPLETED
        }
    }
    
    /**
     * Subscribe to all relevant messages.
     */
    private fun subscribeToMessages() {
        // Action requests from other agents
        scope.launch {
            messageBus.subscribe(AIMessage.Control.ActionRequest::class.java)
                .collect { request ->
                    handleActionRequest(request)
                }
        }
        
        // Action execution results
        scope.launch {
            messageBus.subscribe(AIMessage.Motor.ActionExecuted::class.java)
                .collect { result ->
                    handleActionResult(result)
                }
        }
        
        // Emotion updates
        scope.launch {
            messageBus.subscribe(AIMessage.Perception.EmotionVector::class.java)
                .collect { emotion ->
                    emotionContext = EmotionContext(
                        valence = emotion.valence,
                        arousal = emotion.arousal,
                        urgency = emotion.urgency
                    )
                }
        }
        
        // Safety violations
        scope.launch {
            messageBus.subscribe(AIMessage.System.SafetyViolation::class.java)
                .collect { violation ->
                    handleSafetyViolation(violation)
                }
        }
    }
    
    /**
     * Handle action requests from other agents.
     */
    private suspend fun handleActionRequest(request: AIMessage.Control.ActionRequest) {
        if (request.source == AgentType.META_AI) return // Ignore own requests
        
        val resourceAvailable = checkResourceAvailability(request)
        
        val decision = decisionEngine.evaluateAction(
            request,
            currentGoalContext,
            emotionContext,
            resourceAvailable
        )
        
        when (decision) {
            is ActionDecision.Approve -> {
                Log.d(TAG, "Approved action: ${request.actionType} (confidence: ${decision.confidence})")
                messageBus.publish(
                    AIMessage.Control.ActionApproved(
                        requestId = request.id,
                        approvedParams = request.params
                    )
                )
            }
            is ActionDecision.Reject -> {
                Log.d(TAG, "Rejected action: ${request.actionType} (${decision.reason})")
                messageBus.publish(
                    AIMessage.Control.ActionRejected(
                        requestId = request.id,
                        reason = decision.reason
                    )
                )
            }
            is ActionDecision.Defer -> {
                Log.d(TAG, "Deferred action: ${request.actionType} (${decision.reason})")
                // Re-queue for later (simplified - just log for now)
            }
        }
    }
    
    /**
     * Handle action execution results.
     */
    private suspend fun handleActionResult(result: AIMessage.Motor.ActionExecuted) {
        if (result.success) {
            currentGoalContext?.let { context ->
                goalStack.markCompleted(context.goal.id, result.feedback)
                currentGoalContext = null
            }
        } else {
            currentGoalContext?.let { context ->
                goalStack.markFailed(context, result.feedback["error"]?.toString() ?: "Unknown error")
                currentGoalContext = null
            }
        }
    }
    
    /**
     * Handle safety violations.
     */
    private suspend fun handleSafetyViolation(violation: AIMessage.System.SafetyViolation) {
        Log.e(TAG, "SAFETY VIOLATION: ${violation.attemptedAction} - ${violation.violationType}")
        
        // Cancel current goal if unsafe
        currentGoalContext?.let { context ->
            goalStack.cancel(context.goal.id)
            currentGoalContext = null
        }
    }
    
    /**
     * Check resource availability for action.
     */
    private fun checkResourceAvailability(request: AIMessage.Control.ActionRequest): Boolean {
        val usage = resourceAllocator.getCurrentUsage()
        
        // Simple heuristic: check if we have headroom
        return usage.cpuUtilization < 0.8f && usage.memoryUtilization < 0.8f
    }
    
    /**
     * Monitor and adjust resources.
     */
    private suspend fun monitorResources() {
        val usage = resourceAllocator.getCurrentUsage()
        
        // Throttle if overloaded
        if (usage.cpuUtilization > 0.9f || usage.memoryUtilization > 0.9f) {
            resourceAllocator.throttleAll(0.7f)
            Log.w(TAG, "System overloaded - throttling resources")
        }
        
        // Update state
        stateManager.updateMeta { meta ->
            meta.copy(
                activeAgents = setOf(AgentType.META_AI), // TODO: Track all active agents
                resourceAllocations = emptyMap() // TODO: Get from allocator
            )
        }
    }
    
    /**
     * Get current statistics.
     */
    fun getStats(): MetaStats {
        return MetaStats(
            goalStackStats = goalStack.getStats(),
            resourceUsage = resourceAllocator.getCurrentUsage(),
            currentGoal = currentGoalContext?.goal?.description,
            emotionContext = emotionContext
        )
    }
}

data class MetaStats(
    val goalStackStats: com.ailive.meta.planning.StackStats,
    val resourceUsage: com.ailive.meta.resources.ResourceUsage,
    val currentGoal: String?,
    val emotionContext: EmotionContext
)
