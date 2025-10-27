package com.ailive.meta.planning

import com.ailive.core.types.AgentType
import java.util.UUID

/**
 * Represents a goal in AILive's goal stack.
 * Goals can have subgoals and dependencies.
 */
sealed class Goal {
    abstract val id: String
    abstract val description: String
    abstract val priority: Int
    abstract val deadline: Long?
    abstract val dependencies: List<String>
    abstract val assignedAgent: AgentType?
    abstract val status: GoalStatus
    
    data class Compound(
        override val id: String = UUID.randomUUID().toString(),
        override val description: String,
        override val priority: Int,
        override val deadline: Long? = null,
        override val dependencies: List<String> = emptyList(),
        override val assignedAgent: AgentType? = null,
        override val status: GoalStatus = GoalStatus.PENDING,
        val subGoals: List<Goal>
    ) : Goal()
    
    data class Atomic(
        override val id: String = UUID.randomUUID().toString(),
        override val description: String,
        override val priority: Int,
        override val deadline: Long? = null,
        override val dependencies: List<String> = emptyList(),
        override val assignedAgent: AgentType? = null,
        override val status: GoalStatus = GoalStatus.PENDING,
        val actionType: String,
        val parameters: Map<String, Any>
    ) : Goal()
    
    data class Conditional(
        override val id: String = UUID.randomUUID().toString(),
        override val description: String,
        override val priority: Int,
        override val deadline: Long? = null,
        override val dependencies: List<String> = emptyList(),
        override val assignedAgent: AgentType? = null,
        override val status: GoalStatus = GoalStatus.PENDING,
        val condition: () -> Boolean,
        val thenGoal: Goal,
        val elseGoal: Goal?
    ) : Goal()
}

enum class GoalStatus {
    PENDING,
    IN_PROGRESS,
    BLOCKED,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Goal with execution context.
 */
data class GoalContext(
    val goal: Goal,
    val startedAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val lastError: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)
