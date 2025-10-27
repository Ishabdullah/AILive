package com.ailive.meta.planning

import android.util.Log
import java.util.*

/**
 * Goal Stack Planning implementation for AILive.
 * LIFO stack with priority override and dependency resolution.
 */
class GoalStack {
    private val TAG = "GoalStack"
    
    // Main stack - LIFO but priority-aware
    private val stack = LinkedList<GoalContext>()
    
    // Completed goals for dependency checking
    private val completedGoals = mutableMapOf<String, GoalContext>()
    
    // Failed goals for retry logic
    private val failedGoals = mutableMapOf<String, GoalContext>()
    
    /**
     * Push a goal onto the stack.
     * If goal has subgoals, push them in reverse order (so first executes first).
     */
    fun push(goal: Goal) {
        when (goal) {
            is Goal.Compound -> {
                // Push parent goal
                stack.push(GoalContext(goal))
                // Push subgoals in reverse order
                goal.subGoals.reversed().forEach { subGoal ->
                    push(subGoal)
                }
                Log.d(TAG, "Pushed compound goal: ${goal.description} with ${goal.subGoals.size} subgoals")
            }
            is Goal.Atomic -> {
                stack.push(GoalContext(goal))
                Log.d(TAG, "Pushed atomic goal: ${goal.description}")
            }
            is Goal.Conditional -> {
                // Evaluate condition and push appropriate goal
                val chosenGoal = if (goal.condition()) {
                    Log.d(TAG, "Condition true for: ${goal.description}")
                    goal.thenGoal
                } else {
                    Log.d(TAG, "Condition false for: ${goal.description}")
                    goal.elseGoal
                }
                chosenGoal?.let { push(it) }
            }
        }
    }
    
    /**
     * Pop the top goal from stack.
     * Returns null if stack empty or top goal has unmet dependencies.
     */
    fun pop(): GoalContext? {
        if (stack.isEmpty()) return null
        
        val context = stack.peek()
        val goal = context.goal
        
        // Check dependencies
        if (!areDependenciesMet(goal)) {
            Log.d(TAG, "Goal blocked by dependencies: ${goal.description}")
            // Mark as blocked and try next goal
            updateGoalStatus(goal.id, GoalStatus.BLOCKED)
            return null
        }
        
        // Check deadline
        if (isDeadlineExpired(goal)) {
            Log.w(TAG, "Goal deadline expired: ${goal.description}")
            stack.pop()
            markFailed(context, "Deadline expired")
            return null
        }
        
        return stack.pop()
    }
    
    /**
     * Peek at top goal without removing.
     */
    fun peek(): GoalContext? = stack.peek()
    
    /**
     * Get all pending goals sorted by priority.
     */
    fun getPendingGoals(): List<GoalContext> {
        return stack.filter { it.goal.status == GoalStatus.PENDING }
            .sortedByDescending { it.goal.priority }
    }
    
    /**
     * Get highest priority goal that's ready to execute.
     */
    fun getHighestPriorityReady(): GoalContext? {
        return stack.asSequence()
            .filter { it.goal.status == GoalStatus.PENDING }
            .filter { areDependenciesMet(it.goal) }
            .filter { !isDeadlineExpired(it.goal) }
            .maxByOrNull { it.goal.priority }
    }
    
    /**
     * Mark a goal as completed.
     */
    fun markCompleted(goalId: String, result: Any? = null) {
        val context = stack.find { it.goal.id == goalId }
        if (context != null) {
            stack.remove(context)
            val updated = context.copy(
                goal = updateGoalStatus(context.goal, GoalStatus.COMPLETED),
                metadata = context.metadata + ("result" to (result ?: "success"))
            )
            completedGoals[goalId] = updated
            Log.i(TAG, "Goal completed: ${context.goal.description}")
        }
    }
    
    /**
     * Mark a goal as failed.
     */
    fun markFailed(context: GoalContext, reason: String) {
        stack.remove(context)
        val updated = context.copy(
            goal = updateGoalStatus(context.goal, GoalStatus.FAILED),
            lastError = reason
        )
        failedGoals[context.goal.id] = updated
        Log.w(TAG, "Goal failed: ${context.goal.description} - $reason")
    }
    
    /**
     * Cancel a goal.
     */
    fun cancel(goalId: String) {
        val context = stack.find { it.goal.id == goalId }
        if (context != null) {
            stack.remove(context)
            Log.i(TAG, "Goal cancelled: ${context.goal.description}")
        }
    }
    
    /**
     * Clear all goals (emergency stop).
     */
    fun clear() {
        stack.clear()
        Log.w(TAG, "Goal stack cleared")
    }
    
    /**
     * Check if goal dependencies are met.
     */
    private fun areDependenciesMet(goal: Goal): Boolean {
        return goal.dependencies.all { depId ->
            completedGoals.containsKey(depId)
        }
    }
    
    /**
     * Check if goal deadline has expired.
     */
    private fun isDeadlineExpired(goal: Goal): Boolean {
        val deadline = goal.deadline ?: return false
        return System.currentTimeMillis() > deadline
    }
    
    /**
     * Update goal status immutably.
     */
    private fun updateGoalStatus(goal: Goal, newStatus: GoalStatus): Goal {
        return when (goal) {
            is Goal.Atomic -> goal.copy(status = newStatus)
            is Goal.Compound -> goal.copy(status = newStatus)
            is Goal.Conditional -> goal.copy(status = newStatus)
        }
    }
    
    private fun updateGoalStatus(goalId: String, newStatus: GoalStatus) {
        val index = stack.indexOfFirst { it.goal.id == goalId }
        if (index >= 0) {
            val context = stack[index]
            stack[index] = context.copy(goal = updateGoalStatus(context.goal, newStatus))
        }
    }
    
    /**
     * Get statistics.
     */
    fun getStats(): StackStats {
        return StackStats(
            totalGoals = stack.size,
            pendingGoals = stack.count { it.goal.status == GoalStatus.PENDING },
            inProgressGoals = stack.count { it.goal.status == GoalStatus.IN_PROGRESS },
            blockedGoals = stack.count { it.goal.status == GoalStatus.BLOCKED },
            completedGoals = completedGoals.size,
            failedGoals = failedGoals.size
        )
    }
    
    fun isEmpty(): Boolean = stack.isEmpty()
    fun size(): Int = stack.size
}

data class StackStats(
    val totalGoals: Int,
    val pendingGoals: Int,
    val inProgressGoals: Int,
    val blockedGoals: Int,
    val completedGoals: Int,
    val failedGoals: Int
)
