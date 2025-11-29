package com.ailive.ai.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * WhisperAssetExtractor - Handles extraction of Whisper model from APK assets
 * 
 * Extracts the Whisper GGML model file bundled in the APK to internal storage
 * where it can be loaded by whisper.cpp native library.
 * 
 * Features:
 * - Extracts Whisper model on first app launch
 * - Checks for existing files to avoid redundant extraction
 * - Provides progress feedback during extraction
 * - Validates extracted files
 */
class WhisperAssetExtractor(private val context: Context) {
    
    companion object {
        private const val TAG = "WhisperAssetExtractor"
        
        // Whisper model file bundled in assets
        // Using small.en model for English-only recognition
        const val WHISPER_MODEL_FILE = "ggml-small.en.bin"
        
        // Assets directory path
        private const val ASSETS_WHISPER_DIR = "models/whisper"
        
        // Minimum expected file size (for validation)
        private const val MIN_MODEL_SIZE_BYTES = 100 * 1024 * 1024L  // 100MB minimum
    }
    
    private val modelsDir = context.filesDir.resolve("models/whisper")
    
    /**
     * Extract Whisper model from assets to internal storage
     * 
     * @param onProgress Progress callback (fileName, current, total)
     * @return true if extraction successful or file already exists, false on error
     */
    suspend fun extractWhisperModel(
        onProgress: (String, Int, Int) -> Unit = { _, _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🔍 Checking Whisper model...")
            
            // Ensure models directory exists
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
                Log.i(TAG, "📁 Created models directory: ${modelsDir.absolutePath}")
            }
            
            val targetFile = modelsDir.resolve(WHISPER_MODEL_FILE)
            
            // Check if file already exists and is valid
            if (isFileValid(targetFile)) {
                Log.i(TAG, "✅ Whisper model already exists and is valid")
                Log.i(TAG, "   Path: ${targetFile.absolutePath}")
                Log.i(TAG, "   Size: ${targetFile.length() / 1024 / 1024}MB")
                onProgress(WHISPER_MODEL_FILE, 1, 1)
                return@withContext true
            }
            
            Log.i(TAG, "📦 Extracting Whisper model from assets...")
            Log.i(TAG, "   Source: $ASSETS_WHISPER_DIR/$WHISPER_MODEL_FILE")
            Log.i(TAG, "   Target: ${targetFile.absolutePath}")
            
            onProgress(WHISPER_MODEL_FILE, 0, 1)
            
            // Extract the model file
            val assetPath = "$ASSETS_WHISPER_DIR/$WHISPER_MODEL_FILE"
            if (!extractAsset(assetPath, targetFile)) {
                Log.e(TAG, "❌ Failed to extract Whisper model")
                return@withContext false
            }
            
            Log.i(TAG, "✅ Whisper model extraction complete!")
            Log.i(TAG, "   Location: ${targetFile.absolutePath}")
            Log.i(TAG, "   Size: ${targetFile.length() / 1024 / 1024}MB")
            
            onProgress(WHISPER_MODEL_FILE, 1, 1)
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Whisper model extraction failed", e)
            false
        }
    }
    
    /**
     * Check if Whisper model is available and valid
     */
    fun isWhisperModelAvailable(): Boolean {
        val file = modelsDir.resolve(WHISPER_MODEL_FILE)
        val available = isFileValid(file)
        
        if (available) {
            Log.d(TAG, "✅ Whisper model available: ${file.absolutePath}")
        } else {
            Log.d(TAG, "❌ Whisper model not available or invalid")
        }
        
        return available
    }
    
    /**
     * Get the path to the Whisper model in internal storage
     */
    fun getWhisperModelPath(): String {
        val path = modelsDir.resolve(WHISPER_MODEL_FILE).absolutePath
        Log.d(TAG, "📍 Whisper model path: $path")
        return path
    }
    
    /**
     * Clean up extracted Whisper model (for testing or reset)
     */
    fun cleanupWhisperModel() {
        val file = modelsDir.resolve(WHISPER_MODEL_FILE)
        if (file.exists() && file.delete()) {
            Log.i(TAG, "🗑️ Deleted Whisper model: ${file.name}")
        }
    }
    
    // ===== PRIVATE HELPER METHODS =====
    
    /**
     * Extract a single asset file to internal storage
     */
    private fun extractAsset(assetPath: String, targetFile: File): Boolean {
        return try {
            Log.d(TAG, "📤 Extracting: $assetPath")
            
            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    val buffer = ByteArray(8 * 1024)  // 8KB buffer
                    var bytesRead: Int
                    var totalBytes = 0L
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        
                        // Log progress every 10MB
                        if (totalBytes % (10 * 1024 * 1024) == 0L) {
                            Log.d(TAG, "   Progress: ${totalBytes / 1024 / 1024}MB extracted...")
                        }
                    }
                    
                    Log.d(TAG, "   Total extracted: ${totalBytes / 1024 / 1024}MB")
                }
            }
            
            // Verify file was extracted correctly
            val valid = isFileValid(targetFile)
            if (valid) {
                Log.d(TAG, "✅ Extraction verified: ${targetFile.name}")
            } else {
                Log.e(TAG, "❌ Extraction verification failed: ${targetFile.name}")
            }
            
            valid
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract asset: $assetPath", e)
            // Clean up partial file
            if (targetFile.exists()) {
                targetFile.delete()
            }
            false
        }
    }
    
    /**
     * Validate that a file exists and meets minimum size requirements
     */
    private fun isFileValid(file: File): Boolean {
        if (!file.exists()) {
            Log.d(TAG, "File does not exist: ${file.absolutePath}")
            return false
        }
        
        if (file.length() < MIN_MODEL_SIZE_BYTES) {
            Log.w(TAG, "File too small: ${file.name} (${file.length()} bytes < $MIN_MODEL_SIZE_BYTES bytes minimum)")
            return false
        }
        
        return true
    }
}