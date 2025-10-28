package com.ailive.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AudioManager - Low-level microphone capture for AILive
 * Phase 2.3: Continuous audio listening for wake word detection
 */
class AudioManager(private val context: Context) {
    private val TAG = "AudioManager"

    // Audio configuration optimized for speech
    private val SAMPLE_RATE = 16000  // 16kHz standard for speech
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    ) * 2  // Double buffer for safety

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val recordingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Callbacks for audio data
    var onAudioData: ((ByteArray, Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    /**
     * Check if microphone permission is granted
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Initialize AudioRecord
     */
    fun initialize(): Boolean {
        if (!hasPermission()) {
            Log.e(TAG, "❌ RECORD_AUDIO permission not granted")
            onError?.invoke("Microphone permission required")
            return false
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                BUFFER_SIZE
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioRecord failed to initialize")
                onError?.invoke("AudioRecord initialization failed")
                return false
            }

            Log.i(TAG, "✓ AudioManager initialized")
            Log.i(TAG, "Sample rate: ${SAMPLE_RATE}Hz")
            Log.i(TAG, "Buffer size: $BUFFER_SIZE bytes")

            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Initialize failed", e)
            onError?.invoke("Audio initialization error: ${e.message}")
            return false
        }
    }

    /**
     * Start continuous recording
     */
    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        if (audioRecord == null) {
            Log.e(TAG, "AudioRecord not initialized")
            return
        }

        try {
            audioRecord?.startRecording()
            isRecording = true

            Log.i(TAG, "🎤 Recording started")

            // Start reading audio data in background
            recordingScope.launch {
                readAudioData()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start recording", e)
            onError?.invoke("Recording failed: ${e.message}")
        }
    }

    /**
     * Stop recording
     */
    fun stopRecording() {
        if (!isRecording) return

        isRecording = false

        try {
            audioRecord?.stop()
            Log.i(TAG, "🛑 Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }

    /**
     * Read audio data continuously
     */
    private suspend fun readAudioData() {
        val buffer = ByteArray(BUFFER_SIZE)
        var totalBytesRead = 0

        Log.i(TAG, "📡 Audio data loop started")

        while (isRecording) {
            try {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                if (bytesRead > 0) {
                    totalBytesRead += bytesRead

                    // Invoke callback with audio data
                    withContext(Dispatchers.Default) {
                        onAudioData?.invoke(buffer.copyOf(bytesRead), bytesRead)
                    }

                    // Log every 100 buffers (~6 seconds at 16kHz)
                    if (totalBytesRead % (BUFFER_SIZE * 100) < BUFFER_SIZE) {
                        Log.d(TAG, "Audio flowing: ${totalBytesRead / 1024} KB captured")
                    }

                } else if (bytesRead < 0) {
                    Log.e(TAG, "AudioRecord read error: $bytesRead")
                    onError?.invoke("Audio read error")
                    break
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error reading audio", e)
                onError?.invoke("Audio read exception: ${e.message}")
                break
            }
        }

        Log.i(TAG, "📡 Audio data loop ended. Total: ${totalBytesRead / 1024} KB")
    }

    /**
     * Convert audio bytes to float array (for ML models)
     */
    fun bytesToFloatArray(audioData: ByteArray): FloatArray {
        val shorts = ShortArray(audioData.size / 2)
        ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

        return FloatArray(shorts.size) { i ->
            shorts[i] / 32768.0f  // Normalize to [-1.0, 1.0]
        }
    }

    /**
     * Get audio format info
     */
    fun getAudioInfo(): String {
        return "Sample Rate: ${SAMPLE_RATE}Hz, Format: PCM 16-bit Mono, Buffer: ${BUFFER_SIZE}B"
    }

    /**
     * Release resources
     */
    fun release() {
        stopRecording()

        try {
            audioRecord?.release()
            audioRecord = null
            recordingScope.cancel()

            Log.i(TAG, "✓ AudioManager released")

        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioManager", e)
        }
    }
}
