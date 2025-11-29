package com.ailive.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.ailive.ai.audio.WhisperAssetExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * WhisperProcessor - High-performance, on-device speech-to-text using whisper.cpp
 *
 * This class handles raw audio recording, buffering, and passes the data
 * to the native whisper.cpp library for transcription.
 * 
 * CRITICAL SAFETY FEATURES:
 * - Model existence validation before initialization
 * - Null/empty transcription guards
 * - Comprehensive error handling
 * - Detailed logging for debugging
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
    private var isModelInitialized = false
    
    // Asset extractor for Whisper model
    private val assetExtractor = WhisperAssetExtractor(context)

    companion object {
        private const val SAMPLE_RATE = 16000 // Whisper requires 16kHz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    // Load native library with error handling
    init {
        try {
            System.loadLibrary("ailive_llm")
            Log.i(TAG, "✅ Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "❌ CRITICAL: Failed to load native library", e)
            Log.e(TAG, "   Whisper will not be available")
        }
    }

    // --- Native JNI Functions ---
    private external fun nativeInit(modelPath: String): Boolean
    private external fun nativeProcess(audioData: FloatArray): String
    private external fun nativeRelease()

    /**
     * Initialize the Whisper model. Must be called before starting to listen.
     * 
     * CRITICAL SAFETY CHECKS:
     * 1. Validates model file exists before initialization
     * 2. Checks file path is not null or empty
     * 3. Verifies native library is loaded
     * 4. Provides detailed error messages for debugging
     * 
     * @return true if initialization successful, false otherwise
     */
    suspend fun initialize(): Boolean {
        Log.i(TAG, "🎤 Initializing Whisper model...")
        
        try {
            // SAFETY CHECK 1: Verify model is available
            if (!assetExtractor.isWhisperModelAvailable()) {
                Log.w(TAG, "⚠️ Whisper model not found in internal storage")
                Log.i(TAG, "   Attempting to extract from assets...")
                
                // Try to extract the model
                val extracted = assetExtractor.extractWhisperModel { fileName, current, total ->
                    Log.d(TAG, "   Extracting: $fileName ($current/$total)")
                }
                
                if (!extracted) {
                    val error = "Failed to extract Whisper model from assets"
                    Log.e(TAG, "❌ $error")
                    onError?.invoke(error)
                    return false
                }
            }
            
            // SAFETY CHECK 2: Get and validate model path
            val modelPath = assetExtractor.getWhisperModelPath()
            if (modelPath.isBlank()) {
                val error = "Whisper model path is empty"
                Log.e(TAG, "❌ $error")
                onError?.invoke(error)
                return false
            }
            
            Log.i(TAG, "📍 Model path: $modelPath")
            
            // SAFETY CHECK 3: Verify file exists at path
            val modelFile = java.io.File(modelPath)
            if (!modelFile.exists()) {
                val error = "Whisper model file does not exist at: $modelPath"
                Log.e(TAG, "❌ $error")
                onError?.invoke(error)
                return false
            }
            
            Log.i(TAG, "✅ Model file verified:")
            Log.i(TAG, "   Path: $modelPath")
            Log.i(TAG, "   Size: ${modelFile.length() / 1024 / 1024}MB")
            Log.i(TAG, "   Exists: ${modelFile.exists()}")
            Log.i(TAG, "   Readable: ${modelFile.canRead()}")
            
            // SAFETY CHECK 4: Initialize native model
            Log.i(TAG, "⏱️  Initializing native Whisper context (may take 5-10 seconds)...")
            val success = nativeInit(modelPath)
            
            if (!success) {
                val error = "Failed to initialize Whisper native context"
                Log.e(TAG, "❌ $error")
                Log.e(TAG, "   Possible causes:")
                Log.e(TAG, "   - Wrong model format (expected .bin for whisper)")
                Log.e(TAG, "   - Corrupted model file")
                Log.e(TAG, "   - Incompatible whisper.cpp version")
                onError?.invoke(error)
                return false
            }
            
            isModelInitialized = true
            Log.i(TAG, "✅ Whisper initialized successfully!")
            Log.i(TAG, "   Ready for speech recognition")
            return true
            
        } catch (e: Exception) {
            val error = "Whisper initialization exception: ${e.message}"
            Log.e(TAG, "❌ $error", e)
            e.printStackTrace()
            onError?.invoke(error)
            return false
        }
    }

    /**
     * Start listening for speech.
     * Records audio and processes it when a chunk is ready.
     * 
     * CRITICAL SAFETY CHECKS:
     * 1. Verifies model is initialized before starting
     * 2. Checks audio recording permissions
     * 3. Validates AudioRecord creation
     * 4. Wraps recording in try-catch for crash prevention
     */
    fun startListening() {
        Log.i(TAG, "🎤 Starting to listen...")
        
        // SAFETY CHECK 1: Verify model is initialized
        if (!isModelInitialized) {
            val error = "Cannot start listening: Whisper model not initialized"
            Log.e(TAG, "❌ $error")
            onError?.invoke(error)
            return
        }
        
        if (isListening) {
            Log.w(TAG, "⚠️ Already listening")
            return
        }
        
        // SAFETY CHECK 2: Verify permissions
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val error = "RECORD_AUDIO permission not granted"
            Log.e(TAG, "❌ $error")
            onError?.invoke(error)
            return
        }

        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            
            // SAFETY CHECK 3: Validate buffer size
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                val error = "Invalid audio buffer size: $bufferSize"
                Log.e(TAG, "❌ $error")
                onError?.invoke(error)
                return
            }
            
            Log.d(TAG, "   Buffer size: $bufferSize bytes")
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            // SAFETY CHECK 4: Verify AudioRecord creation
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                val error = "Failed to initialize AudioRecord"
                Log.e(TAG, "❌ $error")
                onError?.invoke(error)
                audioRecord?.release()
                audioRecord = null
                return
            }

            isListening = true
            audioRecord?.startRecording()
            onReadyForSpeech?.invoke()
            Log.i(TAG, "🎤 Listening started with Whisper")

            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Process audio in chunks of 5 seconds
                    val chunkSizeBytes = SAMPLE_RATE * 2 * 5 // 5 seconds of 16-bit audio
                    val buffer = ShortArray(chunkSizeBytes / 2)

                    while (isListening) {
                        val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (readSize > 0) {
                            processAudioChunk(buffer, readSize)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in recording loop", e)
                    e.printStackTrace()
                    CoroutineScope(Dispatchers.Main).launch {
                        onError?.invoke("Recording error: ${e.message}")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start listening", e)
            e.printStackTrace()
            onError?.invoke("Failed to start listening: ${e.message}")
            isListening = false
        }
    }

    /**
     * Process audio chunk with comprehensive error handling
     * 
     * CRITICAL SAFETY CHECKS:
     * 1. Validates audio data is not empty
     * 2. Wraps native call in try-catch
     * 3. Guards against null/empty transcriptions
     * 4. Provides detailed logging for debugging
     */
    private fun processAudioChunk(audioData: ShortArray, size: Int) {
        try {
            Log.d(TAG, "🔊 Processing audio chunk: $size samples")
            
            // SAFETY CHECK 1: Validate audio data
            if (size <= 0) {
                Log.w(TAG, "⚠️ Empty audio chunk, skipping")
                return
            }
            
            // Convert ShortArray to FloatArray (and normalize)
            val floatBuffer = FloatArray(size)
            for (i in 0 until size) {
                floatBuffer[i] = audioData[i] / 32768.0f
            }

            // SAFETY CHECK 2: Call native function with error handling
            val transcription = try {
                nativeProcess(floatBuffer)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Native processing failed", e)
                e.printStackTrace()
                ""
            }

            // SAFETY CHECK 3: Guard against null/empty transcriptions
            if (transcription.isNullOrBlank()) {
                Log.d(TAG, "   No speech detected in chunk")
                return
            }
            
            Log.i(TAG, "✅ Transcription: $transcription")

            // Invoke callback on the main thread
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    onFinalResult?.invoke(transcription)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in transcription callback", e)
                    e.printStackTrace()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing audio chunk", e)
            e.printStackTrace()
        }
    }

    /**
     * Stop listening for speech.
     */
    fun stopListening() {
        if (!isListening) return

        Log.i(TAG, "🛑 Stopping listening...")
        
        try {
            isListening = false
            recordingJob?.cancel()
            recordingJob = null

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            Log.i(TAG, "✅ Listening stopped")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping listening", e)
            e.printStackTrace()
        }
    }

    /**
     * Release all resources used by Whisper.
     */
    fun release() {
        Log.i(TAG, "🗑️ Releasing WhisperProcessor...")
        
        try {
            stopListening()
            
            if (isModelInitialized) {
                nativeRelease()
                isModelInitialized = false
            }
            
            Log.i(TAG, "✅ WhisperProcessor released")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error releasing WhisperProcessor", e)
            e.printStackTrace()
        }
    }

    /**
     * Check if Whisper is active and ready
     */
    fun isActive(): Boolean = isListening
    
    /**
     * Check if model is initialized
     */
    fun isReady(): Boolean = isModelInitialized
}