package com.ailive.core.messaging

/**
 * Priority levels for message routing.
 * Higher values = higher priority in queue.
 */
enum class MessagePriority(val value: Int) {
    LOW(1),
    NORMAL(5),
    HIGH(7),
    CRITICAL(10);
    
    companion object {
        fun fromValue(value: Int): MessagePriority {
            return values().find { it.value == value } ?: NORMAL
        }
    }
}
