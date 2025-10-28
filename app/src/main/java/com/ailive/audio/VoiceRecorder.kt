package com.ailive.audio

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import java.io.File

/**
 * VoiceRecorder - Records voice samples for wake word training
 */
class VoiceRecorder(private val context: Context) {
    private val TAG = "VoiceRecorder"
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    
    /**
     * Start recording to file
     */
    fun startRecording(filename: String): Boolean {
        return try {
            val outputFile = File(context.filesDir, "voice_samples/$filename")
            outputFile.parentFile?.mkdirs()
            
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            
            isRecording = true
            Log.i(TAG, "Recording started: $filename")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            false
        }
    }
    
    /**
     * Stop recording
     */
    fun stopRecording() {
        if (!isRecording) return
        
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            Log.i(TAG, "Recording stopped")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }
    
    /**
     * Get recorded file
     */
    fun getRecordingFile(filename: String): File {
        return File(context.filesDir, "voice_samples/$filename")
    }
    
    fun isRecording() = isRecording
}
