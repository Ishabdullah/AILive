package com.ailive.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * WhisperProcessor - High-performance, on-device speech-to-text using whisper.cpp
 *
 * This class handles raw audio recording, buffering, and passes the data
 * to the native whisper.cpp library for transcription.
 */
class WhisperProcessor(private val context: Context) {
    private val TAG = "WhisperProcessor"

    // Callbacks
    var onFinalResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onReadyForSpeech: (() -> Unit)? = null

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isListening = false

    companion object {
        private const val SAMPLE_RATE = 16000 // Whisper requires 16kHz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    // Load native library
    init {
        System.loadLibrary("ailive_llm")
    }

    // --- Native JNI Functions ---
    private external fun nativeInit(modelPath: String): Boolean
    private external fun nativeProcess(audioData: FloatArray): String
    private external fun nativeRelease()

    /**
     * Initialize the Whisper model. Must be called before starting to listen.
     * @param modelPath The file path to the Whisper GGUF model.
     */
    fun initialize(modelPath: String): Boolean {
        Log.i(TAG, "Initializing Whisper model...")
        val success = nativeInit(modelPath)
        if (!success) {
            onError?.invoke("Failed to initialize Whisper model.")
        }
        return success
    }

    /**
     * Start listening for speech.
     * Records audio and processes it when a chunk is ready.
     */
    fun startListening() {
        if (isListening) {
            Log.w(TAG, "Already listening.")
            return
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError?.invoke("RECORD_AUDIO permission not granted.")
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        isListening = true
        audioRecord?.startRecording()
        onReadyForSpeech?.invoke()
        Log.i(TAG, "🎤 Listening started with Whisper.")

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            // We'll process audio in chunks of 5 seconds for this example
            val chunkSizeBytes = SAMPLE_RATE * 2 * 5 // 5 seconds of 16-bit audio
            val buffer = ShortArray(chunkSizeBytes / 2)

            while (isListening) {
                val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readSize > 0) {
                    processAudioChunk(buffer, readSize)
                }
            }
        }
    }

    private fun processAudioChunk(audioData: ShortArray, size: Int) {
        // 1. Convert ShortArray to FloatArray (and normalize)
        val floatBuffer = FloatArray(size)
        for (i in 0 until size) {
            floatBuffer[i] = audioData[i] / 32768.0f
        }

        // 2. Call native function
        val transcription = nativeProcess(floatBuffer)

        // 3. Invoke callback on the main thread
        if (transcription.isNotBlank()) {
            CoroutineScope(Dispatchers.Main).launch {
                onFinalResult?.invoke(transcription)
            }
        }
    }

    /**
     * Stop listening for speech.
     */
    fun stopListening() {
        if (!isListening) return

        isListening = false
        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Log.i(TAG, "🛑 Listening stopped.")
    }

    /**
     * Release all resources used by Whisper.
     */
    fun release() {
        stopListening()
        nativeRelease()
        Log.i(TAG, "✓ WhisperProcessor released.")
    }

    fun isActive(): Boolean = isListening
}
