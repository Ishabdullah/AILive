package com.ailive.meta.resources

import android.util.Log
import com.ailive.core.types.AgentType
import com.ailive.motor.safety.SafetyPolicy

/**
 * Manages resource allocation between agents.
 * Uses budget-based allocation with dynamic adjustment.
 */
class ResourceAllocator {
    private val TAG = "ResourceAllocator"
    
    // Total available resources
    private val totalCpuPercent = 80 // Leave 20% for Android
    private val totalMemoryMB = 4000 // Max 4GB for AILive
    private val totalGpuSlots = 2 // Max 2 models can use GPU simultaneously
    
    // Current allocations
    private val allocations = mutableMapOf<AgentType, ResourceAllocation>()
    
    // Default allocations for each agent type
    private val defaultAllocations = mapOf(
        AgentType.META_AI to ResourceAllocation(10, 500, false),
        AgentType.VISUAL_AI to ResourceAllocation(15, 300, true),
        AgentType.LANGUAGE_AI to ResourceAllocation(20, 1000, false),
        AgentType.EMOTION_AI to ResourceAllocation(10, 300, false),
        AgentType.MEMORY_AI to ResourceAllocation(10, 400, false),
        AgentType.PREDICTIVE_AI to ResourceAllocation(5, 200, false),
        AgentType.REWARD_AI to ResourceAllocation(5, 100, false),
        AgentType.MOTOR_AI to ResourceAllocation(10, 200, false)
    )
    
    init {
        // Initialize with default allocations
        allocations.putAll(defaultAllocations)
    }
    
    /**
     * Allocate resources to an agent.
     * Returns true if allocation successful.
     */
    fun allocate(agent: AgentType, requested: ResourceAllocation): AllocationResult {
        val current = getCurrentUsage()
        
        // Check if allocation would exceed limits
        if (current.cpuPercent + requested.cpuPercent > totalCpuPercent) {
            Log.w(TAG, "CPU allocation would exceed limit for $agent")
            return AllocationResult.Denied("CPU limit exceeded")
        }
        
        if (current.memoryMB + requested.memoryMB > totalMemoryMB) {
            Log.w(TAG, "Memory allocation would exceed limit for $agent")
            return AllocationResult.Denied("Memory limit exceeded")
        }
        
        if (requested.gpuAllowed) {
            val currentGpuUsers = allocations.count { it.value.gpuAllowed }
            if (currentGpuUsers >= totalGpuSlots) {
                Log.w(TAG, "GPU slots full for $agent")
                return AllocationResult.Denied("GPU slots full")
            }
        }
        
        // Allocate
        allocations[agent] = requested
        Log.i(TAG, "Allocated resources to $agent: CPU=${requested.cpuPercent}%, Mem=${requested.memoryMB}MB, GPU=${requested.gpuAllowed}")
        
        return AllocationResult.Granted(requested)
    }
    
    /**
     * Deallocate agent resources.
     */
    fun deallocate(agent: AgentType) {
        allocations.remove(agent)
        Log.d(TAG, "Deallocated resources from $agent")
    }
    
    /**
     * Get current allocation for an agent.
     */
    fun getAllocation(agent: AgentType): ResourceAllocation? {
        return allocations[agent]
    }
    
    /**
     * Get total current resource usage.
     */
    fun getCurrentUsage(): ResourceUsage {
        val totalCpu = allocations.values.sumOf { it.cpuPercent }
        val totalMemory = allocations.values.sumOf { it.memoryMB }
        val gpuUsers = allocations.count { it.value.gpuAllowed }
        
        return ResourceUsage(
            cpuPercent = totalCpu,
            memoryMB = totalMemory,
            gpuUsers = gpuUsers,
            cpuAvailable = totalCpuPercent - totalCpu,
            memoryAvailable = totalMemoryMB - totalMemory,
            gpuSlotsAvailable = totalGpuSlots - gpuUsers
        )
    }
    
    /**
     * Throttle resources when system under stress.
     */
    fun throttleAll(factor: Float) {
        val clamped = factor.coerceIn(0.1f, 1.0f)
        allocations.replaceAll { agent, allocation ->
            allocation.copy(
                cpuPercent = (allocation.cpuPercent * clamped).toInt(),
                memoryMB = (allocation.memoryMB * clamped).toInt()
            )
        }
        Log.w(TAG, "Throttled all agents by ${(1 - clamped) * 100}%")
    }
    
    /**
     * Boost priority agent resources.
     */
    fun boost(agent: AgentType, factor: Float) {
        val current = allocations[agent] ?: return
        val boosted = current.copy(
            cpuPercent = (current.cpuPercent * factor).toInt().coerceAtMost(50),
            memoryMB = (current.memoryMB * factor).toInt()
        )
        
        if (allocate(agent, boosted) is AllocationResult.Granted) {
            Log.i(TAG, "Boosted $agent resources by ${(factor - 1) * 100}%")
        }
    }
    
    /**
     * Reset to default allocations.
     */
    fun reset() {
        allocations.clear()
        allocations.putAll(defaultAllocations)
        Log.i(TAG, "Resources reset to defaults")
    }
}

data class ResourceAllocation(
    val cpuPercent: Int,
    val memoryMB: Int,
    val gpuAllowed: Boolean
)

data class ResourceUsage(
    val cpuPercent: Int,
    val memoryMB: Int,
    val gpuUsers: Int,
    val cpuAvailable: Int,
    val memoryAvailable: Int,
    val gpuSlotsAvailable: Int
) {
    val cpuUtilization: Float
        get() = cpuPercent / 80f
    
    val memoryUtilization: Float
        get() = memoryMB / 4000f
}

sealed class AllocationResult {
    data class Granted(val allocation: ResourceAllocation) : AllocationResult()
    data class Denied(val reason: String) : AllocationResult()
}
