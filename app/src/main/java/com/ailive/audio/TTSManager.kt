package com.ailive.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * TTSManager - Offline, high-quality Text-to-Speech engine for AILive using Piper.
 * Provides voice output for all AI agents by synthesizing audio locally.
 */
class TTSManager(private val context: Context) {
    private val TAG = "TTSManager"

    private var audioTrack: AudioTrack? = null
    private var isInitialized = false
    private val speechQueue = Channel<SpeechRequest>(Channel.UNLIMITED)
    private var playbackJob: Job? = null

    private val _state = MutableStateFlow(TTSState.INITIALIZING)
    val state: StateFlow<TTSState> = _state

    data class SpeechRequest(val text: String, val priority: Priority = Priority.NORMAL)
    enum class Priority { NORMAL, URGENT }
    enum class TTSState { INITIALIZING, READY, SPEAKING, ERROR, SHUTDOWN }

    companion object {
        // This should ideally be fetched from the model, but we'll use a common Piper sample rate.
        private const val SAMPLE_RATE = 22050
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    init {
        // Load native library
        System.loadLibrary("ailive_llm")
    }

    // --- Native JNI Functions for Piper ---
    private external fun nativeInitPiper(modelPath: String): Boolean
    private external fun nativeSynthesize(text: String): ShortArray?
    private external fun nativeReleasePiper()

    /**
     * Initialize the Piper TTS engine with a voice model.
     */
    fun initialize(modelPath: String): Boolean {
        Log.i(TAG, "Initializing Piper TTS engine...")
        if (!nativeInitPiper(modelPath)) {
            Log.e(TAG, "Failed to initialize Piper native component.")
            _state.value = TTSState.ERROR
            return false
        }

        val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        isInitialized = true
        _state.value = TTSState.READY
        startPlaybackQueue()
        Log.i(TAG, "✓ Piper TTS engine initialized successfully.")
        return true
    }

    private fun startPlaybackQueue() {
        playbackJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val request = speechQueue.receive() // This suspends until a request is available
                
                _state.value = TTSState.SPEAKING
                
                val audioData = nativeSynthesize(request.text)
                
                if (audioData != null) {
                    Log.d(TAG, "Playing ${audioData.size} samples for text: ${request.text.take(50)}...")
                    audioTrack?.play()
                    audioTrack?.write(audioData, 0, audioData.size)
                    audioTrack?.stop() // Use stop() to let buffer finish, not pause()
                } else {
                    Log.e(TAG, "Synthesis failed for text: ${request.text}")
                }

                _state.value = TTSState.READY
            }
        }
    }

    fun speak(text: String, priority: Priority = Priority.NORMAL) {
        if (!isInitialized || text.isBlank()) return

        val request = SpeechRequest(text, priority)
        
        if (priority == Priority.URGENT) {
            // Clear the queue and stop current playback
            stop()
            speechQueue.trySend(request)
        } else {
            speechQueue.trySend(request)
        }
    }

    fun speakIncremental(text: String) {
        // With this architecture, incremental streaming is handled by just adding to the queue.
        // The playback of each sentence will be sequential.
        speak(text, Priority.NORMAL)
    }

    fun stop() {
        // Clear pending requests
        while (speechQueue.tryReceive().isSuccess) { /* clear channel */ }
        
        // Stop current playback
        audioTrack?.let {
            if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                it.stop()
                it.flush()
            }
        }
        _state.value = TTSState.READY
    }

    fun isSpeaking(): Boolean = _state.value == TTSState.SPEAKING

    fun shutdown() {
        Log.i(TAG, "Shutting down Piper TTS...")
        stop()
        playbackJob?.cancel()
        audioTrack?.release()
        nativeReleasePiper()
        isInitialized = false
        _state.value = TTSState.SHUTDOWN
        Log.i(TAG, "TTS shutdown complete")
    }
}
