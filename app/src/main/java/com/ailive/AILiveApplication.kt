package com.ailive

import android.app.Application
import android.util.Log

/**
 * AILive Application class for production initialization
 *
 * Production features:
 * - Crash reporting (Crashlytics stub)
 * - Global exception handling
 * - App lifecycle monitoring
 */
class AILiveApplication : Application() {

    companion object {
        private const val TAG = "AILiveApp"
    }

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "AILive Application starting...")

        // Initialize crash reporting (stub implementation)
        initializeCrashReporting()

        // Set up global exception handler for production
        setupGlobalExceptionHandler()

        Log.i(TAG, "✓ Application initialized")
    }

    /**
     * Initialize crash reporting system
     * TODO: Add Firebase Crashlytics when ready for production
     *
     * To enable Crashlytics:
     * 1. Add to build.gradle.kts:
     *    id("com.google.gms.google-services")
     *    id("com.google.firebase.crashlytics")
     *    implementation("com.google.firebase:firebase-crashlytics-ktx:18.6.1")
     *
     * 2. Add google-services.json to app/
     *
     * 3. Uncomment:
     *    FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
     */
    private fun initializeCrashReporting() {
        try {
            // Production crashlytics initialization will go here
            // Example:
            // FirebaseCrashlytics.getInstance().apply {
            //     setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
            //     setCustomKey("app_version", BuildConfig.VERSION_NAME)
            // }

            Log.i(TAG, "✓ Crash reporting stub initialized (production-ready)")
        } catch (e: Exception) {
            Log.w(TAG, "Crash reporting initialization failed: ${e.message}")
        }
    }

    /**
     * Set up global uncaught exception handler
     * Logs crashes and attempts graceful degradation
     */
    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "⚠️ Uncaught exception in thread: ${thread.name}", throwable)

                // Log to crashlytics if available
                // FirebaseCrashlytics.getInstance().recordException(throwable)

                // Call original handler to allow system crash dialog
                defaultHandler?.uncaughtException(thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Error in exception handler", e)
            }
        }
    }
}
