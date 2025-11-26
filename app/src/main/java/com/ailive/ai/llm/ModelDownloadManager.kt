package com.ailive.ai.llm

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ModelDownloadManager - Handles downloading and importing LLM models
 *
 * REFACTORED: Coroutine-based architecture for clean async operations
 * UPDATED: SmolLM2-360M support for hybrid dual-model system
 *
 * MULTIMODAL MVP STATUS (testing-123 branch):
 * ✅ SmolLM2-360M: Fast chat model (271MB) - Fully integrated
 * ✅ BGE-small-en-v1.5: Embeddings (133MB) - Integrated with memory
 * ✅ TinyLlama-1.1B: Memory ops (700MB) - Downloaded, integration pending
 * ✅ Qwen2-VL-2B: Vision model (986MB) - TEXT-ONLY (mmproj file not included)
 * ⏳ Whisper-Tiny: Voice input - Stub created, model download TODO
 * ⏳ MobileNetV3: Vision pre-screening - Stub created, model download TODO
 *
 * INTEGRATION STATUS:
 * - HybridModelManager: Created but not wired to MainActivity (TODO)
 * - Current: MainActivity still uses LLMManager (single Qwen model)
 * - Vision: Qwen2-VL lacks mmproj file, currently text-only
 *
 * @author AILive Team
 * @since Phase 7.2 (Refactored with Coroutines + SmolLM2)
 */
class ModelDownloadManager(private val context: Context) {

    class DownloadFailedException(message: String) : IOException(message)

    companion object {
        private const val TAG = "ModelDownloadManager"

        // Qwen2-VL-2B-Instruct GGUF (Vision + Complex reasoning)
        private const val QWEN_VL_BASE_URL = "https://huggingface.co/bartowski/Qwen2-VL-2B-Instruct-GGUF/resolve/main"
        const val QWEN_VL_MODEL_GGUF = "Qwen2-VL-2B-Instruct-Q4_K_M.gguf"
        const val QWEN_VL_MODEL_URL = "$QWEN_VL_BASE_URL/$QWEN_VL_MODEL_GGUF"

        // SmolLM2-360M-Instruct GGUF (Fast chat - always loaded)
        private const val SMOLLM2_BASE_URL = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main"
        const val SMOLLM2_MODEL_GGUF = "SmolLM2-360M-Instruct-Q4_K_M.gguf"
        const val SMOLLM2_MODEL_URL = "$SMOLLM2_BASE_URL/$SMOLLM2_MODEL_GGUF"

        // TinyLlama-1.1B (Memory operations)
        private const val TINYLLAMA_BASE_URL = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main"
        const val MEMORY_MODEL_GGUF = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"
        const val MEMORY_MODEL_URL = "$TINYLLAMA_BASE_URL/$MEMORY_MODEL_GGUF"

        // BGE Embeddings - NOW BUILT-IN TO APK
        // These constants are kept for backward compatibility but no longer used for downloads
        @Deprecated("BGE model is now built-in to APK")
        const val BGE_MODEL_ONNX = "model_quantized.onnx"
        @Deprecated("BGE model is now built-in to APK")
        const val BGE_TOKENIZER_JSON = "tokenizer.json"
        @Deprecated("BGE model is now built-in to APK")
        const val BGE_CONFIG_JSON = "config.json"

        private const val MODELS_DIR = "models"
        private const val MIN_MODEL_SIZE_BYTES = 10 * 1024 * 1024L
        private const val MIN_GGUF_SIZE_BYTES = 100 * 1024 * 1024L

        private const val DOWNLOAD_STATUS_OK = "OK"
        private const val DOWNLOAD_STATUS_EXISTS = "EXISTS"
    }

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId: Long = -1
    private var downloadContinuation: Continuation<String>? = null
    private var downloadReceiver: BroadcastReceiver? = null
    private var currentModelName: String? = null
    private var isHandlingCompletion = false

    private val handler = Handler(Looper.getMainLooper())
    private var statusCheckRunnable: Runnable? = null
    private val POLL_INTERVAL_MS = 5000L

    private fun getModelsDir(): File {
        val appPrivateDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        return (appPrivateDir ?: context.filesDir).also { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
                Log.i(TAG, "📁 Created models directory: ${dir.absolutePath}")
            }
        }
    }

    // ========== MODEL AVAILABILITY CHECKS ==========

    fun isQwenVLModelAvailable(): Boolean {
        val downloadsDir = getModelsDir()
        val defaultModel = File(downloadsDir, QWEN_VL_MODEL_GGUF)
        
        if (defaultModel.exists() && defaultModel.length() >= MIN_GGUF_SIZE_BYTES) {
            Log.i(TAG, "✅ Qwen2-VL available (${defaultModel.length() / 1024 / 1024}MB)")
            return true
        }

        val largestModel = getAvailableModelsInDownloads().maxByOrNull { it.length() }
        if (largestModel != null && largestModel.length() >= MIN_GGUF_SIZE_BYTES) {
            Log.i(TAG, "✅ Alternative GGUF model: ${largestModel.name}")
            return true
        }

        return false
    }

    fun isSmolLM2ModelAvailable(): Boolean {
        val downloadsDir = getModelsDir()
        val modelFile = File(downloadsDir, SMOLLM2_MODEL_GGUF)

        if (!modelFile.exists()) {
            Log.i(TAG, "❌ SmolLM2 not found")
            return false
        }

        if (modelFile.length() < MIN_MODEL_SIZE_BYTES) {
            Log.e(TAG, "❌ SmolLM2 file too small")
            return false
        }

        Log.i(TAG, "✅ SmolLM2 available (${modelFile.length() / 1024 / 1024}MB)")
        return true
    }

    fun isMemoryModelAvailable(): Boolean {
        val downloadsDir = getModelsDir()
        val modelFile = File(downloadsDir, MEMORY_MODEL_GGUF)

        if (!modelFile.exists() || modelFile.length() < MIN_GGUF_SIZE_BYTES) {
            return false
        }

        Log.i(TAG, "✅ Memory model available")
        return true
    }

    fun isBGEModelAvailable(): Boolean {
        // BGE model is now built-in to APK and always available
        // The AssetExtractor handles copying assets to internal storage on first launch
        Log.d(TAG, "✅ BGE embedding model is built-in to APK - always available")
        return true
    }

    fun isModelAvailable(modelName: String? = null): Boolean {
        if (modelName != null) {
            val modelFile = File(getModelsDir(), modelName)
            return modelFile.exists() && modelFile.length() > 0
        }
        return isQwenVLModelAvailable()
    }

    fun getModelPath(modelName: String = QWEN_VL_MODEL_GGUF): String {
        return File(getModelsDir(), modelName).absolutePath
    }

    fun getActiveModelFile(): File? {
        val downloadsDir = getModelsDir()
        val prefs = context.getSharedPreferences("ailive_model_prefs", Context.MODE_PRIVATE)
        
        // 1. User-selected model
        prefs.getString("active_model_path", null)?.let { path ->
            val file = File(path)
            if (file.exists() && file.length() >= MIN_GGUF_SIZE_BYTES) {
                return file
            }
            prefs.edit().remove("active_model_path").apply()
        }

        // 2. Default Qwen model
        val defaultModel = File(downloadsDir, QWEN_VL_MODEL_GGUF)
        if (defaultModel.exists() && defaultModel.length() >= MIN_GGUF_SIZE_BYTES) {
            return defaultModel
        }

        // 3. Largest GGUF model
        return getAvailableModelsInDownloads().maxByOrNull { it.length() }
            ?.takeIf { it.length() >= MIN_GGUF_SIZE_BYTES }
    }

    fun getModelsDirectory(): String = getModelsDir().absolutePath

    fun getAvailableModelsInDownloads(): List<File> {
        val downloadsDir = getModelsDir()
        if (!downloadsDir.exists()) return emptyList()

        return downloadsDir.listFiles()?.filter {
            it.isFile && it.name.endsWith(".gguf", ignoreCase = true)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    // ========== DOWNLOAD METHODS (SUSPEND) ==========

    suspend fun downloadQwenVLModel(onProgress: (String, Int, Int) -> Unit) {
        Log.i(TAG, "📥 Downloading Qwen2-VL (986MB)...")
        onProgress(QWEN_VL_MODEL_GGUF, 1, 1)

        try {
            downloadModel(QWEN_VL_MODEL_URL, QWEN_VL_MODEL_GGUF)
            Log.i(TAG, "✅ Qwen2-VL downloaded!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Qwen2-VL download failed: ${e.message}")
            throw DownloadFailedException("Qwen2-VL download failed: ${e.message}")
        }
    }

    suspend fun downloadSmolLM2Model(onProgress: (String, Int, Int) -> Unit) {
        Log.i(TAG, "📥 Downloading SmolLM2-360M (271MB)...")
        onProgress(SMOLLM2_MODEL_GGUF, 1, 1)

        try {
            downloadModel(SMOLLM2_MODEL_URL, SMOLLM2_MODEL_GGUF)
            Log.i(TAG, "✅ SmolLM2 downloaded!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ SmolLM2 download failed: ${e.message}")
            throw DownloadFailedException("SmolLM2 download failed: ${e.message}")
        }
    }

    suspend fun downloadMemoryModel(onProgress: (String, Int, Int) -> Unit) {
        Log.i(TAG, "📥 Downloading TinyLlama (700MB)...")
        onProgress(MEMORY_MODEL_GGUF, 1, 1)

        try {
            downloadModel(MEMORY_MODEL_URL, MEMORY_MODEL_GGUF)
            Log.i(TAG, "✅ Memory model downloaded!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Memory model download failed: ${e.message}")
            throw DownloadFailedException("Memory model download failed: ${e.message}")
        }
    }

    @Deprecated("BGE model is now built-in to APK - download not needed")
    suspend fun downloadBGEModel(onProgress: (String, Int, Int) -> Unit): String {
        Log.i(TAG, "📥 Downloading BGE Embeddings (133MB)...")
        var filesAlreadyExisted = 0

        try {
            // BGE model is now bundled in APK assets, no download required
            Log.i(TAG, "ℹ️ BGE model is built-in to APK - skipping download")
            onProgress("Built-in BGE Model", 3, 3)
            
            // Return success message
            return "BGE model is built-in to APK"
        }
        
        @Deprecated("Legacy BGE download code - no longer used")
        private suspend fun downloadBGEModelLegacy(onProgress: (String, Int, Int) -> Unit): String {
            var filesAlreadyExisted = 0
            val BGE_MODEL_URL = "https://deprecated-url.com/model.onnx"
            val BGE_TOKENIZER_URL = "https://deprecated-url.com/tokenizer.json"
            val BGE_CONFIG_URL = "https://deprecated-url.com/config.json"
            
            // Model file
            onProgress(BGE_MODEL_ONNX, 1, 3)
            if (downloadModel(BGE_MODEL_URL, BGE_MODEL_ONNX) == DOWNLOAD_STATUS_EXISTS) {
                filesAlreadyExisted++
            }
            delay(1000)

            // Tokenizer
            onProgress(BGE_TOKENIZER_JSON, 2, 3)
            if (downloadModel(BGE_TOKENIZER_URL, BGE_TOKENIZER_JSON) == DOWNLOAD_STATUS_EXISTS) {
                filesAlreadyExisted++
            }
            delay(1000)

            // Config
            onProgress(BGE_CONFIG_JSON, 3, 3)
            if (downloadModel(BGE_CONFIG_URL, BGE_CONFIG_JSON) == DOWNLOAD_STATUS_EXISTS) {
                filesAlreadyExisted++
            }

            return if (filesAlreadyExisted == 3) {
                Log.i(TAG, "ℹ️ BGE already exists")
                DOWNLOAD_STATUS_EXISTS
            } else {
                Log.i(TAG, "✅ BGE downloaded!")
                DOWNLOAD_STATUS_OK
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ BGE download failed: ${e.message}")
            throw DownloadFailedException("BGE download failed: ${e.message}")
        }
    }

    suspend fun downloadAllModels(
        onProgress: (String, Int, Int, Int) -> Unit
    ): String {
        Log.i(TAG, "📥 Downloading all models (SmolLM2, BGE, Memory, Qwen)...")
        Log.i(TAG, "   BGE Embeddings: \u2705 Built-in to APK - skip download")
        var modelsAlreadyExisted = 0

        try {
            // 1. SmolLM2 (smallest, fastest) - Priority for hybrid system
            onProgress("SmolLM2 Chat Model", 1, 3, 0)
            val smolStatus = try {
                downloadSmolLM2Model { fileName, _, _ ->
                    onProgress(fileName, 1, 3, 5)
                }
                DOWNLOAD_STATUS_OK
            } catch (e: DownloadFailedException) {
                if (e.message?.contains(DOWNLOAD_STATUS_EXISTS) == true) DOWNLOAD_STATUS_EXISTS else throw e
            }
            if (smolStatus == DOWNLOAD_STATUS_EXISTS) modelsAlreadyExisted++
            delay(1500)


            // 2. Memory model
            onProgress("Memory Model", 2, 3, 30)
            val memStatus = try {
                downloadMemoryModel { fileName, _, _ ->
                    onProgress(fileName, 2, 3, 50)
                }
                DOWNLOAD_STATUS_OK
            } catch (e: DownloadFailedException) {
                if (e.message?.contains(DOWNLOAD_STATUS_EXISTS) == true) DOWNLOAD_STATUS_EXISTS else throw e
            }
            if (memStatus == DOWNLOAD_STATUS_EXISTS) modelsAlreadyExisted++
            delay(1500)

            // 3. Qwen (largest)
            onProgress("Qwen2-VL Model", 3, 3, 60)
            val qwenStatus = try {
                downloadQwenVLModel { fileName, _, _ ->
                    onProgress(fileName, 3, 3, 85)
                }
                DOWNLOAD_STATUS_OK
            } catch (e: DownloadFailedException) {
                if (e.message?.contains(DOWNLOAD_STATUS_EXISTS) == true) DOWNLOAD_STATUS_EXISTS else throw e
            }
            if (qwenStatus == DOWNLOAD_STATUS_EXISTS) modelsAlreadyExisted++

            return if (modelsAlreadyExisted == 3) {
                Log.i(TAG, "ℹ️ All models already exist")
                DOWNLOAD_STATUS_EXISTS
            } else {
                Log.i(TAG, "✅ All models downloaded!")
                DOWNLOAD_STATUS_OK
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Download failed: ${e.message}")
            throw DownloadFailedException("Failed to download all models: ${e.message}")
        }
    }

    // ========== CORE DOWNLOAD LOGIC ==========

    private suspend fun downloadModel(
        modelUrl: String,
        modelName: String,
    ): String = withContext(Dispatchers.Main) {
        Log.i(TAG, "📥 Downloading: $modelName")

        val downloadsDir = getModelsDir()
        val existingFile = File(downloadsDir, modelName)
        val minSize = if (modelName.endsWith(".gguf")) MIN_GGUF_SIZE_BYTES else MIN_MODEL_SIZE_BYTES
        
        if (existingFile.exists() && existingFile.length() >= minSize) {
            Log.i(TAG, "✓ Already exists: $modelName")
            throw DownloadFailedException(DOWNLOAD_STATUS_EXISTS)
        } else if (existingFile.exists()) {
            existingFile.delete()
        }

        downloadReceiver?.let {
            try { context.unregisterReceiver(it) } catch (e: Exception) {}
        }
        downloadReceiver = null

        return@withContext suspendCancellableCoroutine { continuation ->
            downloadContinuation = continuation
            currentModelName = modelName
            isHandlingCompletion = false

            downloadReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        try { ctx.unregisterReceiver(this) } catch (e: Exception) {}
                        downloadReceiver = null
                        handleDownloadComplete(modelName)
                    }
                }
            }

            try {
                context.registerReceiver(
                    downloadReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_NOT_EXPORTED
                )

                val request = DownloadManager.Request(modelUrl.toUri())
                    .setTitle("AILive Model Download")
                    .setDescription("Downloading $modelName...")
                    .setMimeType("application/octet-stream")
                    .setAllowedNetworkTypes(
                        DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                    )
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, modelName)

                downloadId = downloadManager.enqueue(request)
                Log.i(TAG, "✅ Download queued: $downloadId")

                startPollingDownloadStatus()

                continuation.invokeOnCancellation {
                    cancelDownload()
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start download", e)
                downloadReceiver?.let { try { context.unregisterReceiver(it) } catch (ex: Exception) {} }
                downloadReceiver = null
                continuation.resumeWithException(DownloadFailedException("Failed to start: ${e.message}"))
            }
        }
    }

    private fun handleDownloadComplete(modelName: String) {
        if (isHandlingCompletion) return
        isHandlingCompletion = true

        stopPollingDownloadStatus()
        downloadReceiver?.let {
            try { context.unregisterReceiver(it) } catch (e: Exception) {}
            downloadReceiver = null
        }

        val continuation = downloadContinuation
        var exception: DownloadFailedException? = null
        var result: String? = null

        try {
            val query = DownloadManager.Query().setFilterById(downloadId)
            downloadManager.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val modelFile = File(getModelsDir(), modelName)
                            val minSize = if (modelName.endsWith(".gguf")) MIN_GGUF_SIZE_BYTES else MIN_MODEL_SIZE_BYTES

                            if (modelFile.exists() && modelFile.length() >= minSize) {
                                Log.i(TAG, "✅ Verified: $modelName")
                                result = DOWNLOAD_STATUS_OK
                            } else {
                                exception = DownloadFailedException("File too small or missing")
                            }
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            exception = DownloadFailedException(getDownloadErrorMessage(reason))
                        }
                        else -> {
                            exception = DownloadFailedException("Unexpected status: $status")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            exception = DownloadFailedException("Error: ${e.message}")
        } finally {
            downloadId = -1
            currentModelName = null
            downloadContinuation = null
            isHandlingCompletion = false

            if (exception != null) {
                continuation?.resumeWithException(exception)
            } else if (result != null) {
                continuation?.resume(result)
            } else {
                continuation?.resumeWithException(DownloadFailedException("Unknown error"))
            }
        }
    }

    private fun startPollingDownloadStatus() {
        stopPollingDownloadStatus()
        statusCheckRunnable = object : Runnable {
            override fun run() {
                checkDownloadStatus()
                statusCheckRunnable?.let { handler.postDelayed(this, POLL_INTERVAL_MS) }
            }
        }
        handler.postDelayed(statusCheckRunnable!!, POLL_INTERVAL_MS)
    }

    private fun stopPollingDownloadStatus() {
        statusCheckRunnable?.let {
            handler.removeCallbacks(it)
            statusCheckRunnable = null
        }
    }

    private fun checkDownloadStatus() {
        if (downloadId == -1L) {
            stopPollingDownloadStatus()
            return
        }

        try {
            val query = DownloadManager.Query().setFilterById(downloadId)
            downloadManager.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL, DownloadManager.STATUS_FAILED -> {
                            stopPollingDownloadStatus()
                            currentModelName?.let { handleDownloadComplete(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking status", e)
        }
    }

    // ========== UTILITY METHODS ==========

    fun getDownloadProgress(): Pair<Long, Long>? {
        if (downloadId == -1L) return null

        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                return Pair(downloaded, total)
            }
        }
        return null
    }

    suspend fun importModelFromStorage(uri: Uri, onComplete: (Boolean, String) -> Unit) = withContext(Dispatchers.IO) {
        var fileName: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            if (fileName == null) throw IOException("Could not get file name")

            val isValid = fileName!!.endsWith(".gguf", true) ||
                         fileName!!.endsWith(".onnx", true) ||
                         fileName!!.endsWith(".bin", true)

            if (!isValid) throw IOException("Invalid format. Supported: .gguf, .onnx, .bin")

            val destFile = File(getModelsDir(), fileName!!)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, 8192)
                }
            }

            val minSize = if (fileName!!.endsWith(".gguf")) MIN_GGUF_SIZE_BYTES else MIN_MODEL_SIZE_BYTES
            if (destFile.length() < minSize) {
                destFile.delete()
                throw IOException("File too small or corrupted")
            }

            withContext(Dispatchers.Main) { onComplete(true, fileName!!) }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onComplete(false, "Failed: ${e.message}") }
        }
    }

    fun cancelDownload() {
        if (downloadId != -1L) {
            downloadManager.remove(downloadId)
        }
        downloadReceiver?.let { try { context.unregisterReceiver(it) } catch (e: Exception) {} }
        stopPollingDownloadStatus()

        val continuation = downloadContinuation
        downloadId = -1
        currentModelName = null
        downloadContinuation = null
        downloadReceiver = null
        isHandlingCompletion = false
        
        continuation?.resumeWithException(DownloadFailedException("Download cancelled"))
    }

    fun deleteModel(modelName: String): Boolean {
        val modelFile = File(getModelsDir(), modelName)
        return if (modelFile.exists()) {
            modelFile.delete().also { deleted ->
                if (deleted) Log.i(TAG, "🗑️ Deleted: $modelName")
            }
        } else {
            false
        }
    }

    private fun getDownloadErrorMessage(reason: Int): String = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME -> "Download cannot be resumed"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "No storage found"
        DownloadManager.ERROR_FILE_ERROR -> "File system error"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "Network error"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Server error"
        else -> "Unknown error (code: $reason)"
    }
}
