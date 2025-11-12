package com.ailive.stats

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * StatisticsManager - Tracks AILive usage statistics
 *
 * Metrics tracked:
 * - Total conversations
 * - Total messages (user + AI)
 * - Total tokens generated
 * - Average response time
 * - Current memory usage
 * - Session statistics
 */
class StatisticsManager(private val context: Context) {
    private val TAG = "StatisticsManager"

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ailive_statistics", Context.MODE_PRIVATE)

    // Recent response times for averaging (last 50)
    private val recentResponseTimes = ConcurrentLinkedQueue<Long>()
    private val MAX_RESPONSE_TIMES = 50

    // Session statistics (reset on app restart)
    private var sessionMessages = 0
    private var sessionTokens = 0
    private var sessionStartTime = System.currentTimeMillis()

    companion object {
        // SharedPreferences keys
        private const val KEY_TOTAL_CONVERSATIONS = "total_conversations"
        private const val KEY_TOTAL_MESSAGES = "total_messages"
        private const val KEY_TOTAL_TOKENS = "total_tokens"
        private const val KEY_TOTAL_RESPONSE_TIME = "total_response_time"
        private const val KEY_RESPONSE_COUNT = "response_count"
    }

    /**
     * Get total number of conversations
     */
    fun getTotalConversations(): Int {
        return prefs.getInt(KEY_TOTAL_CONVERSATIONS, 0)
    }

    /**
     * Get total number of messages (user + AI)
     */
    fun getTotalMessages(): Int {
        return prefs.getInt(KEY_TOTAL_MESSAGES, 0)
    }

    /**
     * Get total number of tokens generated
     */
    fun getTotalTokens(): Long {
        return prefs.getLong(KEY_TOTAL_TOKENS, 0L)
    }

    /**
     * Get average response time in milliseconds
     */
    fun getAverageResponseTime(): Long {
        val totalTime = prefs.getLong(KEY_TOTAL_RESPONSE_TIME, 0L)
        val count = prefs.getInt(KEY_RESPONSE_COUNT, 0)
        return if (count > 0) totalTime / count else 0L
    }

    /**
     * Get recent average response time (last 50 responses)
     */
    fun getRecentAverageResponseTime(): Long {
        if (recentResponseTimes.isEmpty()) return getAverageResponseTime()
        return recentResponseTimes.sum() / recentResponseTimes.size
    }

    /**
     * Get current memory usage in MB
     */
    fun getCurrentMemoryUsage(): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        return usedMemory.toInt()
    }

    /**
     * Get total device RAM in MB
     */
    fun getTotalDeviceMemory(): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return (memInfo.totalMem / (1024 * 1024)).toInt()
    }

    /**
     * Get session statistics
     */
    fun getSessionStats(): SessionStats {
        val uptime = System.currentTimeMillis() - sessionStartTime
        return SessionStats(
            messages = sessionMessages,
            tokens = sessionTokens,
            uptimeMs = uptime
        )
    }

    /**
     * Increment conversation count
     */
    fun incrementConversations() {
        val current = getTotalConversations()
        prefs.edit().putInt(KEY_TOTAL_CONVERSATIONS, current + 1).apply()
        Log.d(TAG, "Conversations: ${current + 1}")
    }

    /**
     * Increment message count (called for each user or AI message)
     */
    fun incrementMessages(count: Int = 1) {
        val current = getTotalMessages()
        prefs.edit().putInt(KEY_TOTAL_MESSAGES, current + count).apply()
        sessionMessages += count
        Log.d(TAG, "Messages: ${current + count}")
    }

    /**
     * Record tokens generated
     */
    fun recordTokens(tokenCount: Int) {
        val current = getTotalTokens()
        prefs.edit().putLong(KEY_TOTAL_TOKENS, current + tokenCount).apply()
        sessionTokens += tokenCount
        Log.d(TAG, "Tokens: ${current + tokenCount} (session: $sessionTokens)")
    }

    /**
     * Record response time
     * @param responseTimeMs Response time in milliseconds
     */
    fun recordResponseTime(responseTimeMs: Long) {
        // Update lifetime average
        val currentTotal = prefs.getLong(KEY_TOTAL_RESPONSE_TIME, 0L)
        val currentCount = prefs.getInt(KEY_RESPONSE_COUNT, 0)

        prefs.edit()
            .putLong(KEY_TOTAL_RESPONSE_TIME, currentTotal + responseTimeMs)
            .putInt(KEY_RESPONSE_COUNT, currentCount + 1)
            .apply()

        // Update recent average
        recentResponseTimes.add(responseTimeMs)
        if (recentResponseTimes.size > MAX_RESPONSE_TIMES) {
            recentResponseTimes.poll()
        }

        Log.d(TAG, "Response time recorded: ${responseTimeMs}ms (avg: ${getRecentAverageResponseTime()}ms)")
    }

    /**
     * Record a complete generation (convenience method)
     * @param tokenCount Number of tokens generated
     * @param responseTimeMs Time taken in milliseconds
     */
    fun recordGeneration(tokenCount: Int, responseTimeMs: Long) {
        incrementMessages(2) // User message + AI response
        recordTokens(tokenCount)
        recordResponseTime(responseTimeMs)
    }

    /**
     * Get formatted statistics summary
     */
    fun getStatsSummary(): String {
        return buildString {
            appendLine("=== AILive Statistics ===")
            appendLine("Conversations: ${getTotalConversations()}")
            appendLine("Messages: ${getTotalMessages()}")
            appendLine("Tokens: ${getTotalTokens()}")
            appendLine("Avg Response: ${getAverageResponseTime()}ms")
            appendLine("Recent Avg Response: ${getRecentAverageResponseTime()}ms")
            appendLine("Memory: ${getCurrentMemoryUsage()}MB / ${getTotalDeviceMemory()}MB")
            val session = getSessionStats()
            appendLine("\n=== Session ===")
            appendLine("Messages: ${session.messages}")
            appendLine("Tokens: ${session.tokens}")
            appendLine("Uptime: ${session.uptimeMs / 1000}s")
        }
    }

    /**
     * Clear all statistics (for testing or reset)
     */
    fun clearStats() {
        prefs.edit().clear().apply()
        recentResponseTimes.clear()
        sessionMessages = 0
        sessionTokens = 0
        sessionStartTime = System.currentTimeMillis()
        Log.i(TAG, "Statistics cleared")
    }

    /**
     * Session statistics data class
     */
    data class SessionStats(
        val messages: Int,
        val tokens: Int,
        val uptimeMs: Long
    )
}
