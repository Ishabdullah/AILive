package com.ailive.ai.embeddings

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BGEInitializer - Handles first-time setup of built-in BGE embedding model
 * 
 * This utility ensures the BGE model files are extracted from APK assets
 * to internal storage on first app launch, making them available for
 * the EmbeddingModelManager.
 * 
 * Features:
 * - Extracts BGE model files from assets/embeddings/ to internal storage
 * - Checks if files already exist to avoid redundant extraction
 * - Provides progress feedback during extraction
 * - Handles errors gracefully with fallback behavior
 * 
 * @author AILive Team
 * @since v1.7 - BGE Model Bundling
 */
class BGEInitializer(private val context: Context) {
    
    companion object {
        private const val TAG = "BGEInitializer"
        private const val PREF_NAME = "BGE_PREFS"
        private const val KEY_BGE_INITIALIZED = "bge_initialized"
    }
    
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val assetExtractor = AssetExtractor(context)
    
    /**
     * Initialize BGE model if needed (extract from APK assets)
     * 
     * This should be called once on app startup to ensure the BGE model
     * is available for the embedding system.
     * 
     * @param onProgress Progress callback (fileName, current, total)
     * @return true if initialization successful or already completed, false on error
     */
    suspend fun initializeIfNeeded(
        onProgress: (String, Int, Int) -> Unit = { _, _, _ -> }
    ): Boolean {
        // Check if BGE is already initialized
        if (prefs.getBoolean(KEY_BGE_INITIALIZED, false)) {
            Log.d(TAG, "✅ BGE model already initialized")
            return true
        }
        
        // Check if assets are already available
        if (assetExtractor.areBGEAssetsAvailable()) {
            Log.d(TAG, "✅ BGE assets already available, marking as initialized")
            markInitialized()
            return true
        }
        
        Log.i(TAG, "🔧 Initializing BGE embedding model (built-in to APK)...")
        
        return try {
            // Extract BGE assets from APK to internal storage
            val success = assetExtractor.extractBGEAssets { fileName, current, total ->
                Log.d(TAG, "📦 Extracting: $fileName ($current/$total)")
                onProgress(fileName, current, total)
            }
            
            if (success) {
                markInitialized()
                Log.i(TAG, "✅ BGE embedding model initialized successfully!")
                Log.i(TAG, "   Model: BGE-small-en-v1.5 (built-in)")
                Log.i(TAG, "   Location: ${assetExtractor.getBGEModelPath()}")
                Log.i(TAG, "🎯 Semantic embeddings ready!")
                true
            } else {
                Log.w(TAG, "⚠️  BGE initialization failed - app will use fallback embeddings")
                false  // Non-critical - app can run without it
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ BGE initialization failed", e)
            Log.w(TAG, "   App will continue with deterministic random embeddings")
            false
        }
    }
    
    /**
     * Check if BGE model is initialized and ready
     */
    fun isInitialized(): Boolean {
        return prefs.getBoolean(KEY_BGE_INITIALIZED, false) && 
               assetExtractor.areBGEAssetsAvailable()
    }
    
    /**
     * Force re-initialization (useful for testing or recovery)
     */
    suspend fun forceReinitialize(onProgress: (String, Int, Int) -> Unit = { _, _, _ -> }): Boolean {
        Log.i(TAG, "🔄 Forcing BGE re-initialization...")
        
        // Clear the flag
        prefs.edit().remove(KEY_BGE_INITIALIZED).apply()
        
        // Clean up existing assets
        assetExtractor.cleanupBGEAssets()
        
        // Re-initialize
        return initializeIfNeeded(onProgress)
    }
    
    /**
     * Get BGE model status information
     */
    fun getStatus(): BGEStatus {
        val isInitialized = isInitialized()
        val assetsAvailable = assetExtractor.areBGEAssetsAvailable()
        
        return if (isInitialized && assetsAvailable) {
            BGEStatus.READY
        } else if (assetsAvailable) {
            BGEStatus.ASSETS_AVAILABLE
        } else {
            BGEStatus.NOT_INITIALIZED
        }
    }
    
    /**
     * Get model file paths
     */
    fun getModelPaths(): BGEModelPaths? {
        return if (assetExtractor.areBGEAssetsAvailable()) {
            BGEModelPaths(
                modelPath = assetExtractor.getBGEModelPath(),
                tokenizerPath = assetExtractor.getBGETokenizerPath(),
                configPath = assetExtractor.getBGEConfigPath()
            )
        } else {
            null
        }
    }
    
    // ===== PRIVATE METHODS =====
    
    private fun markInitialized() {
        prefs.edit().putBoolean(KEY_BGE_INITIALIZED, true).apply()
    }
    
    /**
     * Initialize BGE model asynchronously
     */
    fun initializeAsync(
        onProgress: (String, Int, Int) -> Unit = { _, _, _ -> },
        onComplete: (Boolean) -> Unit = {}
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val success = initializeIfNeeded(onProgress)
            CoroutineScope(Dispatchers.Main).launch {
                onComplete(success)
            }
        }
    }
}

/**
 * BGE initialization status
 */
enum class BGEStatus {
    NOT_INITIALIZED,    // First launch, assets not extracted
    ASSETS_AVAILABLE,   // Assets extracted but not marked ready
    READY               // Fully initialized and ready to use
}

/**
 * BGE model file paths
 */
data class BGEModelPaths(
    val modelPath: String,
    val tokenizerPath: String,
    val configPath: String
)