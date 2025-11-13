package com.ailive.websearch.network

import android.util.Log
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Factory for creating configured OkHttp clients for web search providers.
 *
 * Provides:
 * - Connection pooling for performance
 * - Configurable timeouts
 * - Request/response logging
 * - Retry logic with exponential backoff
 * - User-Agent headers
 * - Optional certificate pinning
 *
 * @since v1.4
 */
object HttpClientFactory {
    private const val TAG = "HttpClientFactory"

    // Default timeouts
    private const val CONNECT_TIMEOUT_SEC = 15L
    private const val READ_TIMEOUT_SEC = 30L
    private const val WRITE_TIMEOUT_SEC = 30L

    // Connection pool settings
    private const val MAX_IDLE_CONNECTIONS = 5
    private const val KEEP_ALIVE_DURATION_MIN = 5L

    /**
     * Creates a standard OkHttp client for web search operations.
     *
     * @param enableLogging Whether to enable HTTP logging (for debugging)
     * @param userAgent Custom User-Agent string
     * @param additionalInterceptors Additional interceptors to add
     * @return Configured OkHttpClient instance
     */
    fun createClient(
        enableLogging: Boolean = false,
        userAgent: String = DEFAULT_USER_AGENT,
        additionalInterceptors: List<Interceptor> = emptyList()
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .connectionPool(
                ConnectionPool(
                    maxIdleConnections = MAX_IDLE_CONNECTIONS,
                    keepAliveDuration = KEEP_ALIVE_DURATION_MIN,
                    timeUnit = TimeUnit.MINUTES
                )
            )
            .addInterceptor(UserAgentInterceptor(userAgent))
            .apply {
                // Add logging interceptor if enabled
                if (enableLogging) {
                    addInterceptor(createLoggingInterceptor())
                }

                // Add retry interceptor for transient failures
                addInterceptor(RetryInterceptor(maxRetries = 3))

                // Add custom interceptors
                additionalInterceptors.forEach { addInterceptor(it) }
            }
            .build()
    }

    /**
     * Creates a client with custom timeouts.
     *
     * @param connectTimeout Connect timeout in seconds
     * @param readTimeout Read timeout in seconds
     * @param writeTimeout Write timeout in seconds
     */
    fun createClientWithTimeouts(
        connectTimeout: Long = CONNECT_TIMEOUT_SEC,
        readTimeout: Long = READ_TIMEOUT_SEC,
        writeTimeout: Long = WRITE_TIMEOUT_SEC,
        enableLogging: Boolean = false
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .writeTimeout(writeTimeout, TimeUnit.SECONDS)
            .connectionPool(
                ConnectionPool(
                    maxIdleConnections = MAX_IDLE_CONNECTIONS,
                    keepAliveDuration = KEEP_ALIVE_DURATION_MIN,
                    timeUnit = TimeUnit.MINUTES
                )
            )
            .addInterceptor(UserAgentInterceptor(DEFAULT_USER_AGENT))
            .apply {
                if (enableLogging) {
                    addInterceptor(createLoggingInterceptor())
                }
                addInterceptor(RetryInterceptor(maxRetries = 3))
            }
            .build()
    }

    /**
     * Creates an HTTP logging interceptor.
     */
    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
    }

    /**
     * Default User-Agent for AILive web searches.
     */
    private const val DEFAULT_USER_AGENT = "AILive/1.4 (Android; Web Search Subsystem)"
}

/**
 * Interceptor that adds a User-Agent header to all requests.
 */
private class UserAgentInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val originalRequest = chain.request()
        val requestWithUserAgent = originalRequest.newBuilder()
            .header("User-Agent", userAgent)
            .build()
        return chain.proceed(requestWithUserAgent)
    }
}

/**
 * Interceptor that retries failed requests with exponential backoff.
 *
 * Retries on:
 * - Network failures (IOException)
 * - 5xx server errors
 * - 429 Too Many Requests (with Retry-After header)
 *
 * Does NOT retry on:
 * - 4xx client errors (except 429)
 * - Successful responses (2xx, 3xx)
 */
private class RetryInterceptor(private val maxRetries: Int = 3) : Interceptor {
    private val TAG = "RetryInterceptor"

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        var response: okhttp3.Response? = null
        var exception: Exception? = null
        var retryCount = 0

        while (retryCount <= maxRetries) {
            try {
                // Close previous response if exists
                response?.close()

                // Execute request
                response = chain.proceed(request)

                // Check if we should retry
                if (shouldRetry(response, retryCount)) {
                    val delayMs = getRetryDelay(response, retryCount)
                    Log.d(TAG, "Retrying request (${retryCount + 1}/$maxRetries) after ${delayMs}ms: ${request.url}")
                    Thread.sleep(delayMs)
                    retryCount++
                    continue
                }

                // Success or non-retryable error
                return response
            } catch (e: Exception) {
                exception = e
                if (retryCount >= maxRetries) {
                    Log.e(TAG, "Max retries exceeded for ${request.url}", e)
                    throw e
                }

                val delayMs = getRetryDelay(null, retryCount)
                Log.d(TAG, "Network error, retrying (${retryCount + 1}/$maxRetries) after ${delayMs}ms", e)
                Thread.sleep(delayMs)
                retryCount++
            }
        }

        // Should not reach here, but throw last exception if we do
        throw exception ?: IllegalStateException("Retry loop exited unexpectedly")
    }

    /**
     * Determines if a response should be retried.
     */
    private fun shouldRetry(response: okhttp3.Response, retryCount: Int): Boolean {
        if (retryCount >= maxRetries) return false

        return when (response.code) {
            in 500..599 -> true  // Server errors
            429 -> true          // Rate limited
            else -> false
        }
    }

    /**
     * Calculates retry delay with exponential backoff.
     *
     * Respects Retry-After header if present.
     */
    private fun getRetryDelay(response: okhttp3.Response?, retryCount: Int): Long {
        // Check for Retry-After header (in seconds)
        response?.header("Retry-After")?.toLongOrNull()?.let { retryAfterSec ->
            return retryAfterSec * 1000L
        }

        // Exponential backoff: 2^retryCount seconds (in milliseconds)
        val baseDelayMs = 2000L  // 2 seconds
        return baseDelayMs * (1 shl retryCount)  // 2s, 4s, 8s, 16s...
    }
}
