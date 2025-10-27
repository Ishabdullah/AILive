package com.ailive.reward

import android.util.Log
import com.ailive.core.messaging.*
import com.ailive.core.state.StateManager
import com.ailive.core.types.AgentType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/**
 * Reward AI - AILive's basal ganglia for action valuation and learning.
 * Uses simple TD-learning to update action preferences based on outcomes.
 */
class RewardAI(
    private val messageBus: MessageBus,
    private val stateManager: StateManager
) {
    private val TAG = "RewardAI"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Value estimator
    private val valueEstimator = SimpleValueEstimator()
    
    // Action history for TD-learning
    private val actionHistory = mutableMapOf<String, ActionRecord>()
    
    private var isRunning = false
    
    /**
     * Start Reward AI.
     */
    fun start() {
        if (isRunning) return
        
        isRunning = true
        Log.i(TAG, "Reward AI starting...")
        
        subscribeToMessages()
        
        scope.launch {
            messageBus.publish(
                AIMessage.System.AgentStarted(source = AgentType.REWARD_AI)
            )
        }
        
        Log.i(TAG, "Reward AI started")
    }
    
    /**
     * Stop Reward AI.
     */
    fun stop() {
        if (!isRunning) return
        
        Log.i(TAG, "Reward AI stopping...")
        scope.cancel()
        isRunning = false
        Log.i(TAG, "Reward AI stopped")
    }
    
    /**
     * Estimate value of an action.
     */
    fun estimateValue(actionType: ActionType, context: Map<String, Any>): Float {
        return valueEstimator.estimate(actionType, context)
    }
    
    /**
     * Record action execution (for learning).
     */
    fun recordAction(actionId: String, actionType: ActionType, context: Map<String, Any>) {
        actionHistory[actionId] = ActionRecord(
            actionId = actionId,
            actionType = actionType,
            context = context,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Update value based on observed reward.
     */
    suspend fun updateValue(actionId: String, observedReward: Float) {
        val record = actionHistory[actionId] ?: return
        
        // TD-learning update
        val learningRate = 0.1f
        valueEstimator.update(
            actionType = record.actionType,
            context = record.context,
            reward = observedReward,
            learningRate = learningRate
        )
        
        // Publish reward update
        messageBus.publish(
            AIMessage.Cognition.RewardUpdate(
                actionId = actionId,
                rewardValue = observedReward,
                learningRate = learningRate
            )
        )
        
        Log.d(TAG, "Updated value for ${record.actionType}: reward=$observedReward")
        
        // Clean up old records
        actionHistory.remove(actionId)
    }
    
    /**
     * Subscribe to action results for learning.
     */
    private fun subscribeToMessages() {
        scope.launch {
            messageBus.subscribe(AIMessage.Motor.ActionExecuted::class.java)
                .collect { execution ->
                    // Calculate reward from success/failure
                    val reward = if (execution.success) 1.0f else -0.5f
                    updateValue(execution.actionId, reward)
                }
        }
    }
    
    /**
     * Get statistics.
     */
    fun getStats(): RewardStats {
        return RewardStats(
            totalUpdates = valueEstimator.totalUpdates,
            actionValues = valueEstimator.getActionValues()
        )
    }
}

/**
 * Record of an executed action.
 */
data class ActionRecord(
    val actionId: String,
    val actionType: ActionType,
    val context: Map<String, Any>,
    val timestamp: Long
)

/**
 * Simple value estimator using table lookup + TD-learning.
 * TODO: Replace with neural network (tiny MLP).
 */
class SimpleValueEstimator {
    
    // Action value table (action type -> estimated value)
    private val valueTable = mutableMapOf<ActionType, Float>()
    
    var totalUpdates = 0L
        private set
    
    init {
        // Initialize with default values
        ActionType.values().forEach { action ->
            valueTable[action] = 0.5f // Neutral initial value
        }
    }
    
    /**
     * Estimate value of an action.
     */
    fun estimate(actionType: ActionType, context: Map<String, Any>): Float {
        return valueTable[actionType] ?: 0.5f
    }
    
    /**
     * Update value using TD-learning.
     */
    fun update(
        actionType: ActionType,
        context: Map<String, Any>,
        reward: Float,
        learningRate: Float
    ) {
        val currentValue = valueTable[actionType] ?: 0.5f
        
        // TD update: V(s) = V(s) + α * (R - V(s))
        val tdError = reward - currentValue
        val newValue = currentValue + learningRate * tdError
        
        valueTable[actionType] = newValue.coerceIn(0f, 1f)
        totalUpdates++
    }
    
    /**
     * Get all action values.
     */
    fun getActionValues(): Map<ActionType, Float> {
        return valueTable.toMap()
    }
    
    /**
     * Get best action based on current values.
     */
    fun getBestAction(): Pair<ActionType, Float>? {
        return valueTable.maxByOrNull { it.value }?.toPair()
    }
}

data class RewardStats(
    val totalUpdates: Long,
    val actionValues: Map<ActionType, Float>
)
