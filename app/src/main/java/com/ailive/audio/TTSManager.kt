package com.ailive.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * TTSManager - Text-to-Speech engine for AILive
 * Provides voice output for all AI agents
 */
class TTSManager(private val context: Context) : TextToSpeech.OnInitListener {
    private val TAG = "TTSManager"

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val speechQueue = ConcurrentLinkedQueue<SpeechRequest>()

    // TTS state
    private val _state = MutableStateFlow(TTSState.INITIALIZING)
    val state: StateFlow<TTSState> = _state

    // TTS settings
    var speechRate: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.5f, 2.0f)
            tts?.setSpeechRate(field)
        }

    var pitch: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.5f, 2.0f)
            tts?.setPitch(field)
        }

    data class SpeechRequest(
        val text: String,
        val priority: Priority = Priority.NORMAL,
        val callback: ((Boolean) -> Unit)? = null
    )

    enum class Priority {
        LOW,
        NORMAL,
        HIGH,
        URGENT  // Interrupts current speech
    }

    enum class TTSState {
        INITIALIZING,
        READY,
        SPEAKING,
        ERROR,
        SHUTDOWN
    }

    init {
        initialize()
    }

    /**
     * Initialize TTS engine
     */
    private fun initialize() {
        try {
            Log.i(TAG, "Initializing TTS engine...")
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TTS", e)
            _state.value = TTSState.ERROR
        }
    }

    /**
     * Called when TTS engine is initialized
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                // Set default language
                val result = engine.setLanguage(Locale.US)

                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported")
                    _state.value = TTSState.ERROR
                    return
                }

                // Configure TTS
                engine.setSpeechRate(speechRate)
                engine.setPitch(pitch)

                // Set utterance progress listener
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "Speech started: $utteranceId")
                        _state.value = TTSState.SPEAKING
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "Speech completed: $utteranceId")
                        _state.value = TTSState.READY

                        // Process next queued speech if any
                        processNextInQueue()
                    }

                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "Speech error: $utteranceId")
                        _state.value = TTSState.READY
                        processNextInQueue()
                    }
                })

                isInitialized = true
                _state.value = TTSState.READY
                Log.i(TAG, "✓ TTS engine initialized successfully")
            }
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
            _state.value = TTSState.ERROR
        }
    }

    /**
     * Speak text immediately (interrupts current speech if urgent)
     */
    fun speak(
        text: String,
        priority: Priority = Priority.NORMAL,
        callback: ((Boolean) -> Unit)? = null
    ) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized, cannot speak")
            callback?.invoke(false)
            return
        }

        if (text.isBlank()) {
            Log.w(TAG, "Empty text, ignoring speak request")
            callback?.invoke(false)
            return
        }

        val request = SpeechRequest(text, priority, callback)

        when (priority) {
            Priority.URGENT -> {
                // Interrupt current speech
                stop()
                speakNow(request)
            }
            Priority.HIGH -> {
                // Add to front of queue
                speechQueue.add(request)
                if (_state.value != TTSState.SPEAKING) {
                    processNextInQueue()
                }
            }
            else -> {
                // Add to queue
                speechQueue.add(request)
                if (_state.value != TTSState.SPEAKING) {
                    processNextInQueue()
                }
            }
        }
    }

    /**
     * Speak text immediately without queueing
     */
    private fun speakNow(request: SpeechRequest) {
        tts?.let { engine ->
            val utteranceId = UUID.randomUUID().toString()
            val result = engine.speak(
                request.text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId
            )

            if (result == TextToSpeech.SUCCESS) {
                Log.d(TAG, "Speaking: ${request.text}")
                request.callback?.invoke(true)
            } else {
                Log.e(TAG, "Failed to speak: ${request.text}")
                request.callback?.invoke(false)
            }
        }
    }

    /**
     * Process next speech request in queue
     */
    private fun processNextInQueue() {
        val request = speechQueue.poll() ?: return
        speakNow(request)
    }

    /**
     * Stop current speech
     */
    fun stop() {
        tts?.stop()
        _state.value = TTSState.READY
    }

    /**
     * Clear all queued speech
     */
    fun clearQueue() {
        speechQueue.clear()
    }

    /**
     * Check if currently speaking
     */
    fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }

    /**
     * Speak with different voices/styles for different agents
     *
     * @deprecated As of PersonalityEngine refactoring, AILive uses ONE unified voice.
     * Use speak() directly instead. This method is kept for backward compatibility
     * during transition but will be removed in future versions.
     */
    @Deprecated(
        message = "Use speak() with unified voice instead. AILive now has one personality, not separate agent voices.",
        replaceWith = ReplaceWith("speak(text, Priority.NORMAL, callback)"),
        level = DeprecationLevel.WARNING
    )
    fun speakAsAgent(agentName: String, text: String, callback: ((Boolean) -> Unit)? = null) {
        // REFACTORING: Force unified voice regardless of agent name
        // All responses should sound like ONE character
        pitch = 1.0f
        speechRate = 1.0f

        Log.w(TAG, "⚠️ speakAsAgent() is deprecated. Use speak() for unified voice.")

        speak(text, Priority.NORMAL, callback)
    }

    /**
     * Speak with unified AILive personality voice
     *
     * This is the preferred method for PersonalityEngine.
     * Uses consistent voice parameters (pitch=1.0, rate=1.0) to maintain
     * the illusion of a single unified character.
     */
    fun speakUnified(text: String, callback: ((Boolean) -> Unit)? = null) {
        // Ensure unified voice settings
        pitch = 1.0f
        speechRate = 1.0f

        speak(text, Priority.NORMAL, callback)
    }

    /**
     * Play audio feedback (short beep/tone)
     */
    fun playFeedback(feedbackType: FeedbackType) {
        when (feedbackType) {
            FeedbackType.WAKE_WORD_DETECTED -> {
                speak("Yes?", Priority.URGENT)
            }
            FeedbackType.LISTENING -> {
                // Short tone or "Listening"
                speak("Listening", Priority.HIGH)
            }
            FeedbackType.COMMAND_RECEIVED -> {
                // Quick acknowledgment
                speak("Got it", Priority.HIGH)
            }
            FeedbackType.ERROR -> {
                speak("Sorry, I didn't understand that", Priority.HIGH)
            }
        }
    }

    enum class FeedbackType {
        WAKE_WORD_DETECTED,
        LISTENING,
        COMMAND_RECEIVED,
        ERROR
    }

    /**
     * Shutdown TTS engine
     */
    fun shutdown() {
        Log.i(TAG, "Shutting down TTS...")
        stop()
        clearQueue()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _state.value = TTSState.SHUTDOWN
        Log.i(TAG, "TTS shutdown complete")
    }
}
