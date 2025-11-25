package com.ailive.ai.embeddings

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * AssetExtractor - Handles extraction of bundled model assets from APK to internal storage
 * 
 * This utility extracts the BGE embedding model files that are bundled in the APK
 * assets directory to the app's internal storage where they can be loaded by ONNX Runtime.
 * 
 * Features:
 * - Extracts BGE model files on first app launch
 * - Checks for existing files to avoid redundant extraction
 * - Provides progress feedback during extraction
 * - Handles errors gracefully with fallback behavior
 */
class AssetExtractor(private val context: Context) {
    
    companion object {
        private const val TAG = "AssetExtractor"
        
        // BGE model files bundled in assets
        const val BGE_MODEL_FILE = "model_quantized.onnx"
        const val BGE_TOKENIZER_FILE = "tokenizer.json"
        const val BGE_CONFIG_FILE = "config.json"
        
        // Assets directory path
        private const val ASSETS_EMBEDDINGS_DIR = "embeddings"
        
        // Minimum expected file sizes (for validation)
        private const val MIN_MODEL_SIZE_BYTES = 50 * 1024 * 1024L  // 50MB minimum for ONNX model
        private const val MIN_JSON_SIZE_BYTES = 1 * 1024L  // 1KB minimum for JSON files
    }
    
    private val modelsDir = context.filesDir.resolve("models")
    
    /**
     * Extract all BGE embedding model assets to internal storage
     * 
     * This should be called once on app startup to ensure the BGE model
     * is available for the embedding system.
     * 
     * @param onProgress Progress callback (fileName, current, total)
     * @return true if extraction successful or files already exist, false on error
     */
    suspend fun extractBGEAssets(
        onProgress: (String, Int, Int) -> Unit = { _, _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🔍 Checking BGE embedding assets...")
            
            // Ensure models directory exists
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
                Log.i(TAG, "📁 Created models directory: ${modelsDir.absolutePath}")
            }
            
            // Check if all files already exist and are valid
            val filesToExtract = mutableListOf<String>()
            val bgeFiles = listOf(BGE_MODEL_FILE, BGE_TOKENIZER_FILE, BGE_CONFIG_FILE)
            
            for (i in bgeFiles.indices) {
                val fileName = bgeFiles[i]
                val targetFile = modelsDir.resolve(fileName)
                
                if (!isFileValid(targetFile, fileName)) {
                    filesToExtract.add(fileName)
                    Log.i(TAG, "📋 Need to extract: $fileName")
                } else {
                    Log.i(TAG, "✅ Already exists and valid: $fileName")
                }
                
                onProgress(fileName, i + 1, bgeFiles.size)
            }
            
            if (filesToExtract.isEmpty()) {
                Log.i(TAG, "✅ All BGE assets already available")
                return@withContext true
            }
            
            Log.i(TAG, "📦 Extracting ${filesToExtract.size} BGE asset files...")
            
            // Extract each file
            for ((index, fileName) in filesToExtract.withIndex()) {
                val assetPath = "$ASSETS_EMBEDDINGS_DIR/$fileName"
                val targetFile = modelsDir.resolve(fileName)
                
                Log.d(TAG, "📤 Extracting: $assetPath")
                onProgress(fileName, index + 1, filesToExtract.size)
                
                if (!extractAsset(assetPath, targetFile)) {
                    Log.e(TAG, "❌ Failed to extract: $fileName")
                    return@withContext false
                }
                
                Log.d(TAG, "✅ Extracted: $fileName (${targetFile.length() / 1024 / 1024}MB)")
            }
            
            Log.i(TAG, "✅ BGE assets extraction complete!")
            Log.i(TAG, "   Location: ${modelsDir.absolutePath}")
            Log.i(TAG, "   Model: ${modelsDir.resolve(BGE_MODEL_FILE).length() / 1024 / 1024}MB")
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ BGE assets extraction failed", e)
            false
        }
    }
    
    /**
     * Check if BGE assets are available and valid
     */
    fun areBGEAssetsAvailable(): Boolean {
        val bgeFiles = listOf(BGE_MODEL_FILE, BGE_TOKENIZER_FILE, BGE_CONFIG_FILE)
        
        for (fileName in bgeFiles) {
            val file = modelsDir.resolve(fileName)
            if (!isFileValid(file, fileName)) {
                Log.d(TAG, "❌ Missing or invalid: $fileName")
                return false
            }
        }
        
        Log.d(TAG, "✅ All BGE assets available and valid")
        return true
    }
    
    /**
     * Get the path to the BGE model files in internal storage
     */
    fun getBGEModelPath(): String = modelsDir.resolve(BGE_MODEL_FILE).absolutePath
    fun getBGETokenizerPath(): String = modelsDir.resolve(BGE_TOKENIZER_FILE).absolutePath
    fun getBGEConfigPath(): String = modelsDir.resolve(BGE_CONFIG_FILE).absolutePath
    
    /**
     * Clean up extracted BGE assets (for testing or reset)
     */
    fun cleanupBGEAssets() {
        val bgeFiles = listOf(BGE_MODEL_FILE, BGE_TOKENIZER_FILE, BGE_CONFIG_FILE)
        
        for (fileName in bgeFiles) {
            val file = modelsDir.resolve(fileName)
            if (file.exists() && file.delete()) {
                Log.i(TAG, "🗑️ Deleted: $fileName")
            }
        }
    }
    
    // ===== PRIVATE HELPER METHODS =====
    
    /**
     * Extract a single asset file to internal storage
     */
    private fun extractAsset(assetPath: String, targetFile: File): Boolean {
        return try {
            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream, 8 * 1024)  // 8KB buffer
                }
            }
            
            // Verify file was extracted correctly
            isFileValid(targetFile, targetFile.name)
            
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
    private fun isFileValid(file: File, fileName: String): Boolean {
        if (!file.exists()) {
            return false
        }
        
        val minSize = when (fileName) {
            BGE_MODEL_FILE -> MIN_MODEL_SIZE_BYTES
            BGE_TOKENIZER_FILE, BGE_CONFIG_FILE -> MIN_JSON_SIZE_BYTES
            else -> return true  // No validation for unknown files
        }
        
        if (file.length() < minSize) {
            Log.w(TAG, "File too small: $fileName (${file.length()} bytes < $minSize bytes minimum)")
            return false
        }
        
        return true
    }
}