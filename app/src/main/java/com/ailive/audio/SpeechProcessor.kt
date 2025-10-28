package com.ailive.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * SpeechProcessor - Android Speech Recognition wrapper
 * Phase 2.3: Converts speech to text for AILive
 */
class SpeechProcessor(private val context: Context) : RecognitionListener {
    private val TAG = "SpeechProcessor"

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    // Callbacks
    var onPartialResult: ((String) -> Unit)? = null
    var onFinalResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onReadyForSpeech: (() -> Unit)? = null

    /**
     * Initialize speech recognizer
     */
    fun initialize(): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "❌ Speech recognition not available on this device")
            onError?.invoke("Speech recognition not available")
            return false
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(this)

            Log.i(TAG, "✓ SpeechProcessor initialized")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize SpeechRecognizer", e)
            onError?.invoke("Initialization failed: ${e.message}")
            return false
        }
    }

    /**
     * Start continuous listening
     */
    fun startListening(continuous: Boolean = true) {
        if (isListening) {
            Log.w(TAG, "Already listening")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

            // Enable continuous recognition if supported
            if (continuous) {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            }
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            Log.i(TAG, "🎤 Listening started (continuous: $continuous)")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start listening", e)
            onError?.invoke("Failed to start: ${e.message}")
        }
    }

    /**
     * Stop listening
     */
    fun stopListening() {
        if (!isListening) return

        try {
            speechRecognizer?.stopListening()
            isListening = false
            Log.i(TAG, "🛑 Listening stopped")

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping listening", e)
        }
    }

    /**
     * Cancel current recognition
     */
    fun cancel() {
        try {
            speechRecognizer?.cancel()
            isListening = false
            Log.i(TAG, "Recognition cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling", e)
        }
    }

    /**
     * Release resources
     */
    fun release() {
        stopListening()

        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            Log.i(TAG, "✓ SpeechProcessor released")

        } catch (e: Exception) {
            Log.e(TAG, "Error releasing", e)
        }
    }

    // RecognitionListener callbacks

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "Ready for speech")
        onReadyForSpeech?.invoke()
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "Speech started")
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Audio level changed - can be used for visualization
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        // Raw audio buffer - not typically used
    }

    override fun onEndOfSpeech() {
        Log.d(TAG, "Speech ended")
        isListening = false
    }

    override fun onError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error: $error"
        }

        // Don't treat "no match" and "speech timeout" as hard errors
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            Log.d(TAG, "⚠️ $errorMessage (will retry)")
        } else {
            Log.e(TAG, "❌ Error: $errorMessage")
            onError?.invoke(errorMessage)
        }

        isListening = false
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

        if (!matches.isNullOrEmpty()) {
            val transcription = matches[0]
            Log.i(TAG, "✅ Final result: '$transcription'")
            onFinalResult?.invoke(transcription)
        }

        isListening = false
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

        if (!matches.isNullOrEmpty()) {
            val partial = matches[0]
            Log.d(TAG, "📝 Partial: '$partial'")
            onPartialResult?.invoke(partial)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        // Custom events - not typically used
    }

    /**
     * Get listening status
     */
    fun isActive(): Boolean = isListening

    /**
     * Get processor info
     */
    fun getInfo(): String {
        return "Speech Recognition: ${if (SpeechRecognizer.isRecognitionAvailable(context)) "Available" else "Not Available"}"
    }
}
