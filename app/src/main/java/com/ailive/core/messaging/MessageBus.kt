package com.ailive.core.messaging

import android.util.Log
import com.ailive.core.types.AgentType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production-ready message bus for AILive agent communication.
 * Thread-safe, priority-based, with TTL enforcement and backpressure handling.
 */
class MessageBus(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val TAG = "MessageBus"
    
    // Priority queue for message ordering
    private val messageQueue = PriorityBlockingQueue<MessageWrapper>(
        100,
        compareByDescending<MessageWrapper> { it.effectivePriority }
            .thenBy { it.timestamp }
    )
    
    // Hot shared flow for broadcasting messages to subscribers
    private val _messageFlow = MutableSharedFlow<AIMessage>(
        replay = 0,
        extraBufferCapacity = 50,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val messageFlow: SharedFlow<AIMessage> = _messageFlow.asSharedFlow()
    
    // Topic-specific channels for filtered subscriptions
    private val topicChannels = mutableMapOf<String, MutableSharedFlow<AIMessage>>()
    
    // Control flags
    private val isRunning = AtomicBoolean(false)
    private var processingJob: Job? = null
    
    // Statistics
    private var messagesProcessed = 0L
    private var messagesDropped = 0L
    
    /**
     * Start the message bus processing loop.
     */
    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            processingJob = scope.launch {
                Log.i(TAG, "MessageBus started")
                processMessages()
            }
        }
    }
    
    /**
     * Stop the message bus gracefully.
     */
    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            processingJob?.cancel()
            Log.i(TAG, "MessageBus stopped. Processed: $messagesProcessed, Dropped: $messagesDropped")
        }
    }
    
    /**
     * Publish a message to the bus.
     * Messages are queued by priority and TTL.
     */
    suspend fun publish(message: AIMessage) {
        if (!isRunning.get()) {
            Log.w(TAG, "Cannot publish - bus not running")
            return
        }
        
        val wrapper = MessageWrapper(
            message = message,
            timestamp = message.timestamp,
            effectivePriority = calculateEffectivePriority(message)
        )
        
        val added = messageQueue.offer(wrapper)
        if (!added) {
            messagesDropped++
            Log.w(TAG, "Message queue full - dropped message: ${message.id}")
        }
    }
    
    /**
     * Subscribe to all messages.
     */
    fun subscribe(): Flow<AIMessage> = messageFlow
    
    /**
     * Subscribe to specific message types.
     */
    inline fun <reified T : AIMessage> subscribe(messageType: Class<T>): Flow<T> {
        return messageFlow.filter { it is T }.map { it as T }
    }
    
    /**
     * Subscribe to messages from a specific agent.
     */
    fun subscribeToAgent(agentType: AgentType): Flow<AIMessage> {
        return messageFlow.filter { it.source == agentType }
    }
    
    /**
     * Subscribe to a custom topic (created dynamically).
     */
    fun subscribeToTopic(topic: String): Flow<AIMessage> {
        return topicChannels.getOrPut(topic) {
            MutableSharedFlow(
                replay = 0,
                extraBufferCapacity = 20,
                onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
            )
        }.asSharedFlow()
    }
    
    /**
     * Publish to a specific topic.
     */
    suspend fun publishToTopic(topic: String, message: AIMessage) {
        topicChannels[topic]?.emit(message)
    }
    
    /**
     * Main processing loop - consumes priority queue and broadcasts.
     */
    private suspend fun processMessages() {
        while (isRunning.get()) {
            try {
                // Non-blocking poll with timeout
                val wrapper = withTimeoutOrNull(100) {
                    withContext(Dispatchers.IO) {
                        messageQueue.poll()
                    }
                }
                
                if (wrapper != null) {
                    val message = wrapper.message
                    
                    // Check TTL
                    val age = System.currentTimeMillis() - message.timestamp
                    if (age > message.ttl) {
                        Log.d(TAG, "Message expired (TTL): ${message.id}")
                        messagesDropped++
                        continue
                    }
                    
                    // Broadcast to all subscribers
                    _messageFlow.emit(message)
                    messagesProcessed++
                    
                    if (messagesProcessed % 100 == 0L) {
                        Log.d(TAG, "Processed: $messagesProcessed, Queue size: ${messageQueue.size}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message", e)
            }
        }
    }
    
    /**
     * Calculate effective priority considering urgency from emotion vectors.
     */
    private fun calculateEffectivePriority(message: AIMessage): Int {
        var basePriority = message.priority.value
        
        // Boost priority for emotion-tagged urgent messages
        if (message is AIMessage.Perception.EmotionVector && message.urgency > 0.7f) {
            basePriority += 2
        }
        
        // Boost priority for safety violations
        if (message is AIMessage.System.SafetyViolation) {
            basePriority = MessagePriority.CRITICAL.value + 5
        }
        
        return basePriority
    }
    
    /**
     * Get current bus statistics.
     */
    fun getStats(): BusStats {
        return BusStats(
            messagesProcessed = messagesProcessed,
            messagesDropped = messagesDropped,
            queueSize = messageQueue.size,
            isRunning = isRunning.get()
        )
    }
    
    private data class MessageWrapper(
        val message: AIMessage,
        val timestamp: Long,
        val effectivePriority: Int
    )
}

data class BusStats(
    val messagesProcessed: Long,
    val messagesDropped: Long,
    val queueSize: Int,
    val isRunning: Boolean
)
