package com.ailive.ai.llm

import android.content.Context
import android.net.Uri  // Added missing import for Uri in importModelFromStorage
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.pow

/**
 * ModelDownloadManager - Handles downloading and importing LLM models
 *
 * REFACTORED v2.1: Switched to OkHttp for reliable HF downloads (fixes tokenizer.json redirects)
 * - ✅ OkHttp for better redirect/MIME handling on Hugging Face URLs
 * - ✅ Retained retries, mutex, progress, and validation
 * - ✅ Simplified pause/resume (restarts with progress tracking)
 * - ✅ No more DownloadManager broadcasts/polling
 *
 * @author AILive Team
 * @since Phase 9.6 (OkHttp integration for HF stability)
 */
class ModelDownloadManager(private val context: Context) {

    class DownloadFailedException(message: String) : IOException(message)

    companion object {
        private const val TAG = "ModelDownloadManager"

        // Qwen2-VL-2B-Instruct GGUF
        private const val QWEN_VL_BASE_URL = "https://huggingface.co/bartowski/Qwen2-VL-2B-Instruct-GGUF/resolve/main"
        const val QWEN_VL_MODEL_GGUF = "Qwen2-VL-2B-Instruct-Q4_K_M.gguf"
        const val QWEN_VL_MODEL_URL = "$QWEN_VL_BASE_URL/$QWEN_VL_MODEL_GGUF"

        // SmolLM2-360M-Instruct GGUF
        private const val SMOLLM2_BASE_URL = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main"
        const val SMOLLM2_MODEL_GGUF = "SmolLM2-360M-Instruct-Q4_K_M.gguf"
        const val SMOLLM2_MODEL_URL = "$SMOLLM2_BASE_URL/$SMOLLM2_MODEL_GGUF"

        // TinyLlama-1.1B
        private const val TINYLLAMA_BASE_URL = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main"
        const val MEMORY_MODEL_GGUF = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"
        const val MEMORY_MODEL_URL = "$TINYLLAMA_BASE_URL/$MEMORY_MODEL_GGUF"

        // BGE Embeddings - FIXED URLS
        private const val BGE_BASE_URL = "https://huggingface.co/Xenova/bge-small-en-v1.5/resolve/main"
        const val BGE_MODEL_ONNX = "model_quantized.onnx"
        const val BGE_TOKENIZER_JSON = "tokenizer.json"
        const val BGE_CONFIG_JSON = "config.json"
        const val BGE_MODEL_URL = "$BGE_BASE_URL/onnx/$BGE_MODEL_ONNX"
        const val BGE_TOKENIZER_URL = "$BGE_BASE_URL/$BGE_TOKENIZER_JSON"
        const val BGE_CONFIG_URL = "$BGE_BASE_URL/$BGE_CONFIG_JSON"

        // Whisper Speech-to-Text
        private const val WHISPER_BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"
        const val WHISPER_MODEL_GGUF = "ggml-tiny.en.bin"
        const val WHISPER_MODEL_URL = "$WHISPER_BASE_URL/$WHISPER_MODEL_GGUF"

        private const val MODELS_DIR = "models"
        private const val MIN_MODEL_SIZE_BYTES = 10 * 1024 * 1024L
        private const val MIN_GGUF_SIZE_BYTES = 100 * 1024 * 1024L

        const val DOWNLOAD_STATUS_OK = "OK"
        const val DOWNLOAD_STATUS_EXISTS = "EXISTS"
        const val DOWNLOAD_STATUS_PAUSED = "PAUSED"
        
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 5000L  // Increased for HF throttling
        private const val RETRY_BACKOFF_MULTIPLIER = 2.0
        
        private const val PREF_NAME = "model_download_state"
        private const val KEY_PAUSED_DOWNLOADS = "paused_downloads"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; SM-S928U Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/octet-stream, application/json, */*")
                    .build()
            )
        }
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    
    private val downloadMutex = Mutex()
    private var isPaused = false
    private var currentDownloadUrl: String? = null
    private var currentFileName: String? = null

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun getModelsDir(): File {
        val appPrivateDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        return (appPrivateDir ?: context.filesDir).also { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
                Log.i(TAG, "📁 Created models directory: ${dir.absolutePath}")
            }
        }
    }

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
        return modelFile.exists() && modelFile.length() >= MIN_MODEL_SIZE_BYTES
    }

    fun isMemoryModelAvailable(): Boolean {
        val downloadsDir = getModelsDir()
        val modelFile = File(downloadsDir, MEMORY_MODEL_GGUF)
        return modelFile.exists() && modelFile.length() >= MIN_GGUF_SIZE_BYTES
    }

    fun isBGEModelAvailable(): Boolean {
        val downloadsDir = getModelsDir()
        val modelFile = File(downloadsDir, BGE_MODEL_ONNX)
        val tokenizerFile = File(downloadsDir, BGE_TOKENIZER_JSON)
        val configFile = File(downloadsDir, BGE_CONFIG_JSON)
        val allExist = modelFile.exists() && tokenizerFile.exists() && configFile.exists()
        val validSize = modelFile.length() >= MIN_MODEL_SIZE_BYTES
        return allExist && validSize
    }

    fun isWhisperModelAvailable(): Boolean {
        val downloadsDir = getModelsDir()
        val modelFile = File(downloadsDir, WHISPER_MODEL_GGUF)
        return modelFile.exists() && modelFile.length() >= MIN_MODEL_SIZE_BYTES
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

        prefs.getString("active_model_path", null)?.let { path ->
            val file = File(path)
            if (file.exists() && file.length() >= MIN_GGUF_SIZE_BYTES) {
                return file
            }
            prefs.edit().remove("active_model_path").apply()
        }

        val defaultModel = File(downloadsDir, QWEN_VL_MODEL_GGUF)
        if (defaultModel.exists() && defaultModel.length() >= MIN_GGUF_SIZE_BYTES) {
            return defaultModel
        }

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

    suspend fun downloadQwenVLModel(onProgress: (String, Int, Int) -> Unit) {
        Log.i(TAG, "📥 Downloading Qwen2-VL (986MB)...")
        onProgress(QWEN_VL_MODEL_GGUF, 0, 100)
        downloadWithRetry(QWEN_VL_MODEL_URL, QWEN_VL_MODEL_GGUF, "Qwen2-VL", onProgress)
    }

    suspend fun downloadSmolLM2Model(onProgress: (String, Int, Int) -> Unit) {
        Log.i(TAG, "📥 Downloading SmolLM2-360M (271MB)...")
        onProgress(SMOLLM2_MODEL_GGUF, 0, 100)
        downloadWithRetry(SMOLLM2_MODEL_URL, SMOLLM2_MODEL_GGUF, "SmolLM2", onProgress)
    }

    suspend fun downloadMemoryModel(onProgress: (String, Int, Int) -> Unit) {
        Log.i(TAG, "📥 Downloading TinyLlama (700MB)...")
        onProgress(MEMORY_MODEL_GGUF, 0, 100)
        downloadWithRetry(MEMORY_MODEL_URL, MEMORY_MODEL_GGUF, "Memory Model", onProgress)
    }

    suspend fun downloadBGEModel(onProgress: (String, Int, Int) -> Unit): String {
        Log.i(TAG, "📥 Downloading BGE Embeddings (133MB)...")
        var filesAlreadyExisted = 0

        onProgress(BGE_MODEL_ONNX, 1, 3)
        val modelStatus = downloadWithRetry(BGE_MODEL_URL, BGE_MODEL_ONNX, "BGE Model", { _, progress, total -> onProgress(BGE_MODEL_ONNX, progress, total) })
        if (modelStatus == DOWNLOAD_STATUS_EXISTS) filesAlreadyExisted++
        delay(1000)

        onProgress(BGE_TOKENIZER_JSON, 2, 3)
        val tokenizerStatus = downloadWithRetry(BGE_TOKENIZER_URL, BGE_TOKENIZER_JSON, "BGE Tokenizer", { _, progress, total -> onProgress(BGE_TOKENIZER_JSON, progress, total) })
        if (tokenizerStatus == DOWNLOAD_STATUS_EXISTS) filesAlreadyExisted++
        delay(1000)

        onProgress(BGE_CONFIG_JSON, 3, 3)
        val configStatus = downloadWithRetry(BGE_CONFIG_URL, BGE_CONFIG_JSON, "BGE Config", { _, progress, total -> onProgress(BGE_CONFIG_JSON, progress, total) })
        if (configStatus == DOWNLOAD_STATUS_EXISTS) filesAlreadyExisted++

        return if (filesAlreadyExisted == 3) {
            Log.i(TAG, "ℹ️ BGE already exists")
            DOWNLOAD_STATUS_EXISTS
        } else {
            Log.i(TAG, "✅ BGE downloaded!")
            DOWNLOAD_STATUS_OK
        }
    }

    suspend fun downloadWhisperModel(onProgress: (String, Int, Int) -> Unit): String {
        Log.i(TAG, "📥 Downloading Whisper STT Model (39MB)...")
        onProgress(WHISPER_MODEL_GGUF, 0, 100)
        val result = downloadWithRetry(WHISPER_MODEL_URL, WHISPER_MODEL_GGUF, "Whisper", onProgress = { _, progress, total -> onProgress(WHISPER_MODEL_GGUF, progress, total) })
        Log.i(TAG, if (result == DOWNLOAD_STATUS_EXISTS) "ℹ️ Whisper already exists" else "✅ Whisper downloaded!")
        return result
    }

    suspend fun downloadAllModels(onProgress: (String, Int, Int, Int) -> Unit): String = downloadMutex.withLock {
        Log.i(TAG, "📥 Starting sequential download of all models...")
        var modelsAlreadyExisted = 0
        val totalModels = 5

        try {
            // 1. SmolLM2
            onProgress("SmolLM2 Chat Model", 1, totalModels, 0)
            val smolStatus = try {
                downloadSmolLM2Model { fileName, progress, total ->
                    onProgress(fileName, 1, totalModels, (progress * 5 / total).toInt())
                }
                DOWNLOAD_STATUS_OK
            } catch (e: Exception) {
                if (e.message?.contains("already exists") == true) {
                    modelsAlreadyExisted++
                    DOWNLOAD_STATUS_EXISTS
                } else throw e
            }
            if (smolStatus == DOWNLOAD_STATUS_EXISTS) modelsAlreadyExisted++
            delay(1500)

            // 2. BGE
            onProgress("BGE Embeddings", 2, totalModels, 15)
            val bgeStatus = downloadBGEModel { fileName, fileNum, totalFiles ->
                val percent = 15 + (10 * fileNum) / totalFiles
                onProgress(fileName, 2, totalModels, percent)
            }
            if (bgeStatus == DOWNLOAD_STATUS_EXISTS) modelsAlreadyExisted++
            delay(1500)

            // 3. Memory Model
            onProgress("Memory Model", 3, totalModels, 30)
            val memStatus = try {
                downloadMemoryModel { fileName, progress, total ->
                    onProgress(fileName, 3, totalModels, 30 + (20 * progress / total).toInt())
                }
                DOWNLOAD_STATUS_OK
            } catch (e: Exception) {
                if (e.message?.contains("already exists") == true) {
                    modelsAlreadyExisted++
                    DOWNLOAD_STATUS_EXISTS
                } else throw e
            }
            if (memStatus == DOWNLOAD_STATUS_EXISTS) modelsAlreadyExisted++
            delay(1500)

            // 4. Whisper
            onProgress("Whisper STT Model", 4, totalModels, 60)
            val whisperStatus = downloadWhisperModel { fileName, progress, total ->
                onProgress(fileName, 4, totalModels, 60 + (15 * progress / total).toInt())
            }
            if (whisperStatus == DOWNLOAD_STATUS_EXISTS) modelsAlreadyExisted++
            delay(1500)

            // 5. Qwen
            onProgress("Qwen2-VL Model", 5, totalModels, 80)
            val qwenStatus = try {
                downloadQwenVLModel { fileName, progress, total ->
                    onProgress(fileName, 5, totalModels, 80 + (20 * progress / total).toInt())
                }
                DOWNLOAD_STATUS_OK
            } catch (e: Exception) {
                if (e.message?.contains("already exists") == true) {
                    modelsAlreadyExisted++
                    DOWNLOAD_STATUS_EXISTS
                } else throw e
            }
            if (qwenStatus == DOWNLOAD_STATUS_EXISTS) modelsAlreadyExisted++

            return if (modelsAlreadyExisted == totalModels) {
                Log.i(TAG, "ℹ️ All models already exist")
                DOWNLOAD_STATUS_EXISTS
            } else {
                Log.i(TAG, "✅ All models downloaded successfully!")
                DOWNLOAD_STATUS_OK
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Download sequence failed: ${e.message}", e)
            throw DownloadFailedException("Failed to download all models: ${e.message}")
        }
    }

    private suspend fun downloadWithRetry(
        modelUrl: String,
        modelName: String,
        displayName: String,
        onProgress: (String, Int, Int) -> Unit = { _, _, _ -> }
    ): String {
        currentDownloadUrl = modelUrl
        currentFileName = modelName
        isPaused = false

        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                Log.i(TAG, "📥 Attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS: $displayName")
                val result = downloadViaOkHttp(modelUrl, modelName, onProgress)
                currentDownloadUrl = null
                currentFileName = null
                return result
            } catch (e: Exception) {
                if (e.message?.contains("already exists") == true) {
                    Log.i(TAG, "✓ $displayName already exists, skipping")
                    currentDownloadUrl = null
                    currentFileName = null
                    return DOWNLOAD_STATUS_EXISTS
                }

                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    val delayMs = (RETRY_DELAY_MS * RETRY_BACKOFF_MULTIPLIER.pow(attempt.toDouble())).toLong()
                    Log.w(TAG, "⚠️ Attempt ${attempt + 1} failed for $displayName, retrying in ${delayMs}ms...")
                    delay(delayMs)
                } else {
                    Log.e(TAG, "❌ All retry attempts failed for $displayName")
                }
                throw DownloadFailedException("$displayName download failed after $MAX_RETRY_ATTEMPTS attempts: ${e.message}")
            }
        }
        currentDownloadUrl = null
        currentFileName = null
        return DOWNLOAD_STATUS_PAUSED  // Fallback if all fail
    }

    private suspend fun downloadViaOkHttp(
        url: String,
        fileName: String,
        onProgress: (String, Int, Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        if (isPaused) throw DownloadFailedException(DOWNLOAD_STATUS_PAUSED)

        val downloadsDir = getModelsDir()
        val destFile = File(downloadsDir, fileName)
        val minSize = if (fileName.endsWith(".gguf")) MIN_GGUF_SIZE_BYTES else MIN_MODEL_SIZE_BYTES

        if (destFile.exists() && destFile.length() >= minSize) {
            Log.i(TAG, "✓ Already exists: $fileName (${destFile.length() / 1024 / 1024}MB)")
            return@withContext DOWNLOAD_STATUS_EXISTS
        } else if (destFile.exists()) {
            Log.w(TAG, "⚠️ Deleting incomplete/corrupted file: $fileName")
            destFile.delete()
        }

        // HEAD request for content length (for progress)
        val headRequest = Request.Builder().url(url).head().build()
        val headResponse = okHttpClient.newCall(headRequest).execute()
        val contentLength = try {
            headResponse.use { it.body?.contentLength() ?: -1 }
        } finally {
            headResponse.close()
        }

        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        try {
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message} for $url")
            }

            val body = response.body ?: throw DownloadFailedException("Empty response body for $fileName")

            body.byteStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesCopied = 0L
                    var bufferSize = input.read(buffer)
                    while (bufferSize != -1) {
                        if (isPaused) throw DownloadFailedException(DOWNLOAD_STATUS_PAUSED)
                        output.write(buffer, 0, bufferSize)
                        bytesCopied += bufferSize
                        if (contentLength > 0) {
                            val progress = (bytesCopied * 100 / contentLength).toInt()
                            onProgress(fileName, progress, 100)
                        }
                        bufferSize = input.read(buffer)
                    }
                }
            }

            if (destFile.length() < minSize) {
                destFile.delete()
                throw IOException("File too small or corrupted (${destFile.length()} bytes)")
            }

            Log.i(TAG, "✅ Downloaded: $fileName (${destFile.length() / 1024 / 1024}MB)")
            DOWNLOAD_STATUS_OK
        } finally {
            response.close()
        }
    }

    suspend fun importModelFromStorage(uri: Uri, onComplete: (Boolean, String) -> Unit) = withContext(Dispatchers.IO) {
        var fileName: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            if (fileName == null) throw IOException("Could not get file name")

            val isValid = fileName!!.endsWith(".gguf", true) ||
                         fileName!!.endsWith(".onnx", true) ||
                         fileName!!.endsWith(".bin", true) ||
                         fileName!!.endsWith(".json", true)  // Added for tokenizer/config

            if (!isValid) throw IOException("Invalid format. Supported: .gguf, .onnx, .bin, .json")

            val destFile = File(getModelsDir(), fileName!!)
            
            Log.i(TAG, "📥 Importing model: $fileName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, 8192)
                }
            }

            val minSize = if (fileName!!.endsWith(".gguf")) MIN_GGUF_SIZE_BYTES else MIN_MODEL_SIZE_BYTES
            if (destFile.length() < minSize) {
                destFile.delete()
                throw IOException("File too small or corrupted (${destFile.length()} bytes)")
            }

            Log.i(TAG, "✅ Model imported successfully: $fileName (${destFile.length() / 1024 / 1024}MB)")
            withContext(Dispatchers.Main) { onComplete(true, fileName!!) }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Import failed", e)
            withContext(Dispatchers.Main) { onComplete(false, "Failed: ${e.message}") }
        }
    }

    fun cancelDownload() {
        isPaused = true  // Triggers pause in ongoing streams
        currentDownloadUrl = null
        currentFileName = null
        Log.i(TAG, "🛑 Download cancelled")
    }

    fun deleteModel(modelName: String): Boolean {
        val modelFile = File(getModelsDir(), modelName)
        return if (modelFile.exists()) {
            modelFile.delete().also { deleted ->
                if (deleted) {
                    Log.i(TAG, "🗑️ Deleted: $modelName")
                } else {
                    Log.e(TAG, "❌ Failed to delete: $modelName")
                }
            }
        } else {
            Log.w(TAG, "⚠️ Model not found: $modelName")
            false
        }
    }

    fun pauseDownload(): Boolean {
        if (currentDownloadUrl == null) {
            Log.w(TAG, "⚠️ No active download to pause")
            return false
        }
        isPaused = true
        prefs.edit()
            .putString("paused_url", currentDownloadUrl)
            .putString("paused_filename", currentFileName)
            .apply()
        Log.i(TAG, "⏸️ Download paused: $currentFileName")
        return true
    }

    fun resumeDownload(): Boolean {
        val pausedUrl = prefs.getString("paused_url", null)
        val pausedFile = prefs.getString("paused_filename", null)
        if (pausedUrl == null || pausedFile == null) {
            Log.w(TAG, "⚠️ No paused download found")
            return false
        }
        isPaused = false
        currentDownloadUrl = pausedUrl
        currentFileName = pausedFile
        prefs.edit().remove("paused_url").remove("paused_filename").apply()
        Log.i(TAG, "▶️ Download resumed: $pausedFile")
        return true
    }

    fun hasPausedDownload(): Boolean {
        return prefs.contains("paused_url")
    }

    fun getDownloadProgress(): Pair<Long, Long>? {
        currentFileName?.let { fileName ->
            val file = File(getModelsDir(), fileName)
            return if (file.exists()) Pair(file.length(), 0L) else null  // Partial size if interrupted; total unknown without HEAD
        }
        return null
    }

    fun getDownloadStatus(): String? {
        if (currentDownloadUrl == null) return null
        return if (isPaused) DOWNLOAD_STATUS_PAUSED else "Downloading"  // Simplified; no advanced polling
    }

    fun cleanup() {
        Log.i(TAG, "🧹 Cleaning up ModelDownloadManager")
        cancelDownload()
        prefs.edit().remove("paused_url").remove("paused_filename").apply()
    }
}