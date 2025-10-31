package com.ailive.ai.llm

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * ModelDownloadManager - Handles downloading and importing LLM models
 *
 * Features:
 * - Download models from HuggingFace
 * - Import models from user's storage
 * - Track download progress
 * - Validate model files
 *
 * @author AILive Team
 * @since Phase 7.2
 */
class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloadManager"

        // GGUF models (recommended - smaller and faster)
        // Using bartowski's repo for better quantization options
        const val DEFAULT_MODEL_NAME = "SmolLM2-360M-Instruct-Q4_K_M.gguf"
        const val DEFAULT_MODEL_URL = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf"

        const val ALT_MODEL_NAME = "SmolLM2-135M-Instruct-Q4_K_M.gguf"
        const val ALT_MODEL_URL = "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf"

        // ONNX models (legacy support)
        const val ONNX_360M_NAME = "smollm2-360m-int8.onnx"
        const val ONNX_360M_URL = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-ONNX/resolve/main/smollm2-360m-instruct-int8.onnx"

        private const val MODELS_DIR = "models"
    }

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId: Long = -1
    private var downloadCompleteCallback: ((Boolean, String) -> Unit)? = null

    /**
     * Check if a model file exists in app storage
     */
    fun isModelAvailable(modelName: String = DEFAULT_MODEL_NAME): Boolean {
        val modelFile = File(context.filesDir, "$MODELS_DIR/$modelName")
        val exists = modelFile.exists()
        if (exists) {
            Log.i(TAG, "✅ Model found: ${modelFile.absolutePath} (${modelFile.length() / 1024 / 1024}MB)")
        } else {
            Log.i(TAG, "❌ Model not found: ${modelFile.absolutePath}")
        }
        return exists
    }

    /**
     * Get the path to a model file
     */
    fun getModelPath(modelName: String = DEFAULT_MODEL_NAME): String {
        return File(context.filesDir, "$MODELS_DIR/$modelName").absolutePath
    }

    /**
     * Download a model from HuggingFace
     *
     * @param modelUrl URL to download from (defaults to SmolLM2-360M)
     * @param modelName Name to save as
     * @param onComplete Callback with (success, errorMessage)
     */
    fun downloadModel(
        modelUrl: String = DEFAULT_MODEL_URL,
        modelName: String = DEFAULT_MODEL_NAME,
        onComplete: (Boolean, String) -> Unit
    ) {
        Log.i(TAG, "📥 Starting download: $modelName")
        Log.i(TAG, "   URL: $modelUrl")

        downloadCompleteCallback = onComplete

        // Register broadcast receiver for download completion
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    context.unregisterReceiver(this)
                    handleDownloadComplete(modelName)
                }
            }
        }

        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_NOT_EXPORTED
        )

        try {
            // Use app's external files directory - no storage permission required on Android 10+
            val request = DownloadManager.Request(modelUrl.toUri())
                .setTitle("Downloading AI Model")
                .setDescription("Downloading $modelName for AILive...")
                .setMimeType("application/octet-stream")
                .setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                )
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, null, "models/$modelName")

            downloadId = downloadManager.enqueue(request)
            Log.i(TAG, "✅ Download queued with ID: $downloadId")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start download", e)
            onComplete(false, "Failed to start download: ${e.message}")
        }
    }

    /**
     * Check download progress
     * @return Pair of (bytesDownloaded, totalBytes) or null if not downloading
     */
    fun getDownloadProgress(): Pair<Long, Long>? {
        if (downloadId == -1L) return null

        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val bytesDownloaded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                )
                val totalBytes = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                )
                return Pair(bytesDownloaded, totalBytes)
            }
        }
        return null
    }

    /**
     * Handle download completion - move file to app storage
     */
    private fun handleDownloadComplete(modelName: String) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                )

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val uri = cursor.getString(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                        )
                        Log.i(TAG, "✅ Download complete: $uri")

                        // Move file from Downloads to app storage
                        moveDownloadedFile(Uri.parse(uri), modelName)
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                        )
                        Log.e(TAG, "❌ Download failed with reason: $reason")
                        downloadCompleteCallback?.invoke(false, "Download failed (code: $reason)")
                    }
                    else -> {
                        Log.w(TAG, "⚠️ Download status: $status")
                        downloadCompleteCallback?.invoke(false, "Unexpected download status: $status")
                    }
                }
            }
        }
    }

    /**
     * Move downloaded file from Downloads to app's private storage
     */
    private fun moveDownloadedFile(sourceUri: Uri, modelName: String) {
        try {
            val modelsDir = File(context.filesDir, MODELS_DIR)
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
            }

            val destFile = File(modelsDir, modelName)

            Log.i(TAG, "📁 Moving file to: ${destFile.absolutePath}")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead

                        // Log progress every 50MB
                        if (totalBytes % (50 * 1024 * 1024) == 0L) {
                            Log.d(TAG, "   Moved: ${totalBytes / 1024 / 1024}MB...")
                        }
                    }

                    Log.i(TAG, "✅ File moved successfully: ${totalBytes / 1024 / 1024}MB")
                }
            }

            // Delete original download file
            try {
                context.contentResolver.delete(sourceUri, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete original download: ${e.message}")
            }

            downloadCompleteCallback?.invoke(true, "")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to move file", e)
            downloadCompleteCallback?.invoke(false, "Failed to save file: ${e.message}")
        }
    }

    /**
     * Import a model from user's storage (file picker)
     *
     * @param uri URI of the file selected by user
     * @param onComplete Callback with (success, errorMessage)
     */
    suspend fun importModelFromStorage(
        uri: Uri,
        onComplete: (Boolean, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            var fileName = DEFAULT_MODEL_NAME

            // Get original filename
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            Log.i(TAG, "📥 Importing model from storage: $fileName")

            // Validate model format - support both GGUF (llama.cpp) and ONNX formats
            val isValidFormat = fileName.endsWith(".gguf", ignoreCase = true) ||
                               fileName.endsWith(".onnx", ignoreCase = true)

            if (!isValidFormat) {
                Log.e(TAG, "❌ Invalid model format: $fileName")
                withContext(Dispatchers.Main) {
                    onComplete(false, "Invalid model format.\n\nSupported formats: .gguf (recommended) or .onnx\n\nPlease select a valid model file.")
                }
                return@withContext
            }

            val modelType = if (fileName.endsWith(".gguf", ignoreCase = true)) "GGUF" else "ONNX"
            Log.i(TAG, "✓ Valid $modelType model detected")

            // Ensure models directory exists
            val modelsDir = File(context.filesDir, MODELS_DIR)
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
            }

            val destFile = File(modelsDir, fileName)

            // Copy file
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead

                        if (totalBytes % (50 * 1024 * 1024) == 0L) {
                            Log.d(TAG, "   Copied: ${totalBytes / 1024 / 1024}MB...")
                        }
                    }

                    Log.i(TAG, "✅ Model imported successfully: ${totalBytes / 1024 / 1024}MB")
                    Log.i(TAG, "   Location: ${destFile.absolutePath}")
                }
            }

            withContext(Dispatchers.Main) {
                onComplete(true, fileName)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to import model", e)
            withContext(Dispatchers.Main) {
                onComplete(false, "Failed to import: ${e.message}")
            }
        }
    }

    /**
     * Cancel ongoing download
     */
    fun cancelDownload() {
        if (downloadId != -1L) {
            downloadManager.remove(downloadId)
            downloadId = -1
            Log.i(TAG, "🛑 Download cancelled")
        }
    }

    /**
     * Get list of available models in storage
     */
    fun getAvailableModels(): List<File> {
        val modelsDir = File(context.filesDir, MODELS_DIR)
        if (!modelsDir.exists()) return emptyList()

        return modelsDir.listFiles()?.filter {
            it.isFile && (it.name.endsWith(".onnx") || it.name.endsWith(".gguf"))
        } ?: emptyList()
    }

    /**
     * Delete a model file
     */
    fun deleteModel(modelName: String): Boolean {
        val modelFile = File(context.filesDir, "$MODELS_DIR/$modelName")
        return if (modelFile.exists()) {
            val deleted = modelFile.delete()
            if (deleted) {
                Log.i(TAG, "🗑️ Deleted model: $modelName")
            }
            deleted
        } else {
            false
        }
    }
}
