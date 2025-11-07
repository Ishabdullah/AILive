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

        // ONNX models (TEMPORARY: ONNX-only support in Phase 7.10)
        // INT8 quantized models for optimal size/performance balance
        const val ONNX_360M_NAME = "smollm2-360m-int8.onnx"
        const val ONNX_360M_URL = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct/resolve/main/onnx/model_int8.onnx"

        const val ONNX_135M_NAME = "smollm2-135m-int8.onnx"
        const val ONNX_135M_URL = "https://huggingface.co/HuggingFaceTB/SmolLM2-135M-Instruct/resolve/main/onnx/model_int8.onnx"

        // GGUF support disabled (native library not built yet)
        // Will be re-enabled in future phase when llama.cpp JNI is ready
        // const val DEFAULT_MODEL_NAME = "SmolLM2-360M-Instruct-Q4_K_M.gguf"
        // const val DEFAULT_MODEL_URL = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf"

        private const val MODELS_DIR = "models"

        // Minimum valid model size (1MB) - models smaller than this are likely corrupted
        private const val MIN_MODEL_SIZE_BYTES = 1024 * 1024L
    }

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId: Long = -1
    private var downloadCompleteCallback: ((Boolean, String) -> Unit)? = null
    private var downloadReceiver: BroadcastReceiver? = null  // Keep reference to unregister later
    private var currentModelName: String? = null  // Track current download model name
    private var completionCheckScheduled = false  // Prevent multiple manual checks

    /**
     * Check if a model file exists in app storage
     * If modelName is not specified, checks if ANY model exists
     */
    fun isModelAvailable(modelName: String? = null): Boolean {
        // If specific model requested, check for it
        if (modelName != null) {
            val modelFile = File(context.filesDir, "$MODELS_DIR/$modelName")
            val exists = modelFile.exists()
            if (exists) {
                Log.i(TAG, "✅ Model found: ${modelFile.absolutePath} (${modelFile.length() / 1024 / 1024}MB)")
            } else {
                Log.i(TAG, "❌ Model not found: ${modelFile.absolutePath}")
            }
            return exists
        }

        // Otherwise, check if ANY model exists
        val availableModels = getAvailableModels()
        if (availableModels.isNotEmpty()) {
            Log.i(TAG, "✅ Found ${availableModels.size} model(s):")
            availableModels.forEach { model ->
                Log.i(TAG, "   - ${model.name} (${model.length() / 1024 / 1024}MB)")
            }
            return true
        } else {
            Log.i(TAG, "❌ No models found in ${context.filesDir}/$MODELS_DIR")
            return false
        }
    }

    /**
     * Get the path to a model file
     */
    fun getModelPath(modelName: String = ONNX_360M_NAME): String {
        return File(context.filesDir, "$MODELS_DIR/$modelName").absolutePath
    }

    /**
     * Download a model from HuggingFace (ONNX-only)
     *
     * @param modelUrl URL to download from (defaults to SmolLM2-360M ONNX)
     * @param modelName Name to save as
     * @param onComplete Callback with (success, errorMessage)
     */
    fun downloadModel(
        modelUrl: String = ONNX_360M_URL,
        modelName: String = ONNX_360M_NAME,
        onComplete: (Boolean, String) -> Unit
    ) {
        Log.i(TAG, "📥 Starting download: $modelName")
        Log.i(TAG, "   URL: $modelUrl")

        // Unregister any existing receiver
        downloadReceiver?.let {
            try {
                context.unregisterReceiver(it)
                Log.i(TAG, "   Unregistered previous receiver")
            } catch (e: Exception) {
                Log.w(TAG, "   Could not unregister previous receiver: ${e.message}")
            }
        }

        downloadCompleteCallback = onComplete
        currentModelName = modelName
        completionCheckScheduled = false

        // Register broadcast receiver for download completion
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                Log.i(TAG, "🔔 BroadcastReceiver triggered! Download ID: $id, Expected: $downloadId")

                if (id == downloadId) {
                    Log.i(TAG, "   ✅ ID matches! Processing completion...")
                    try {
                        ctx.unregisterReceiver(this)
                        Log.i(TAG, "   Unregistered receiver")
                    } catch (e: Exception) {
                        Log.w(TAG, "   Could not unregister receiver: ${e.message}")
                    }
                    downloadReceiver = null
                    handleDownloadComplete(modelName)
                } else {
                    Log.w(TAG, "   ⚠️ ID mismatch - ignoring")
                }
            }
        }

        try {
            context.registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
            Log.i(TAG, "✅ BroadcastReceiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to register receiver", e)
            onComplete(false, "Failed to register download listener: ${e.message}")
            return
        }

        try {
            // Download to public Downloads folder (like SmolChat does)
            // This approach is more reliable than setDestinationInExternalFilesDir
            val request = DownloadManager.Request(modelUrl.toUri())
                .setTitle("AILive Model Download")
                .setDescription("Downloading $modelName...")
                .setMimeType("application/octet-stream")
                .setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                )
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, modelName)

            downloadId = downloadManager.enqueue(request)
            Log.i(TAG, "✅ Download queued with ID: $downloadId")
            Log.i(TAG, "   Destination: Downloads/$modelName")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start download", e)
            // Unregister receiver on failure
            downloadReceiver?.let {
                try {
                    context.unregisterReceiver(it)
                } catch (ex: Exception) {}
            }
            downloadReceiver = null
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
        Log.i(TAG, "📥 handleDownloadComplete called for: $modelName")

        try {
            val query = DownloadManager.Query().setFilterById(downloadId)
            downloadManager.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )
                    Log.i(TAG, "   Download status: $status")

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val uriString = cursor.getString(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                            )
                            Log.i(TAG, "✅ Download complete: $uriString")

                            if (uriString != null) {
                                // Move file from Downloads to app storage
                                moveDownloadedFile(Uri.parse(uriString), modelName)
                            } else {
                                Log.e(TAG, "❌ URI is null!")
                                downloadCompleteCallback?.invoke(false, "Download URI is null")
                            }
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reason = cursor.getInt(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                            )
                            val errorMessage = getDownloadErrorMessage(reason)
                            Log.e(TAG, "❌ Download failed with reason: $reason - $errorMessage")
                            downloadCompleteCallback?.invoke(false, errorMessage)
                        }
                        else -> {
                            Log.w(TAG, "⚠️ Download status: $status")
                            downloadCompleteCallback?.invoke(false, "Unexpected download status: $status")
                        }
                    }
                } else {
                    Log.e(TAG, "❌ Cursor is empty!")
                    downloadCompleteCallback?.invoke(false, "Could not query download status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling download completion", e)
            downloadCompleteCallback?.invoke(false, "Error: ${e.message}")
        }
    }

    /**
     * Move downloaded file from Downloads to app's private storage
     */
    private fun moveDownloadedFile(sourceUri: Uri, modelName: String) {
        Log.i(TAG, "📂 moveDownloadedFile called")
        Log.i(TAG, "   Source URI: $sourceUri")
        Log.i(TAG, "   Model name: $modelName")

        try {
            // Create models directory in app's private storage
            val modelsDir = File(context.filesDir, MODELS_DIR)
            if (!modelsDir.exists()) {
                val created = modelsDir.mkdirs()
                Log.i(TAG, "   Models dir created: $created at ${modelsDir.absolutePath}")
            } else {
                Log.i(TAG, "   Models dir exists at ${modelsDir.absolutePath}")
            }

            val destFile = File(modelsDir, modelName)
            Log.i(TAG, "📁 Copying file to: ${destFile.absolutePath}")

            // Open input stream from Downloads
            val inputStream = context.contentResolver.openInputStream(sourceUri)
            if (inputStream == null) {
                Log.e(TAG, "❌ Could not open input stream from URI: $sourceUri")
                downloadCompleteCallback?.invoke(false, "Could not read downloaded file")
                return
            }

            // Copy file to app's private storage
            inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead

                        // Log progress every 50MB
                        if (totalBytes % (50 * 1024 * 1024) == 0L) {
                            Log.d(TAG, "   Copied: ${totalBytes / 1024 / 1024}MB...")
                        }
                    }

                    Log.i(TAG, "✅ File copied successfully: ${totalBytes / 1024 / 1024}MB")
                    Log.i(TAG, "   Final size: ${destFile.length() / 1024 / 1024}MB")
                    Log.i(TAG, "   Destination: ${destFile.absolutePath}")
                }
            }

            // Validate file size
            if (destFile.length() < MIN_MODEL_SIZE_BYTES) {
                Log.e(TAG, "❌ Downloaded file is too small (${destFile.length()} bytes)")
                destFile.delete()
                downloadCompleteCallback?.invoke(false, "Downloaded file is corrupted or incomplete. Please try again.")
                return
            }

            // Delete original download file from Downloads folder
            try {
                val downloadsFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), modelName)
                if (downloadsFile.exists() && downloadsFile.delete()) {
                    Log.i(TAG, "🗑️ Deleted original download from Downloads folder")
                } else {
                    Log.w(TAG, "⚠️ Could not delete original download (may not exist or no permission)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete original download: ${e.message}")
            }

            Log.i(TAG, "✅ Invoking success callback")
            downloadCompleteCallback?.invoke(true, "")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to copy file", e)
            Log.e(TAG, "   Exception: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            downloadCompleteCallback?.invoke(false, "Failed to save file: ${e.message}")
        }
    }

    /**
     * Import a model from user's storage (file picker) - ONNX-only
     *
     * @param uri URI of the file selected by user
     * @param onComplete Callback with (success, errorMessage)
     */
    suspend fun importModelFromStorage(
        uri: Uri,
        onComplete: (Boolean, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            var fileName = ONNX_360M_NAME

            // Get original filename
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            Log.i(TAG, "📥 Importing model from storage: $fileName")

            // Validate model format - ONNX only (TEMPORARY)
            val isValidFormat = fileName.endsWith(".onnx", ignoreCase = true)

            if (!isValidFormat) {
                Log.e(TAG, "❌ Invalid model format: $fileName")
                withContext(Dispatchers.Main) {
                    onComplete(false, "Invalid model format.\n\nThis version only supports .onnx format.\n\nGGUF support coming in future update.")
                }
                return@withContext
            }

            Log.i(TAG, "✓ Valid ONNX model detected")

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

            // Validate file size
            if (destFile.length() < MIN_MODEL_SIZE_BYTES) {
                Log.e(TAG, "❌ Imported file is too small (${destFile.length()} bytes)")
                destFile.delete()
                withContext(Dispatchers.Main) {
                    onComplete(false, "File is too small or corrupted. Please select a valid model file.")
                }
                return@withContext
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
     * Manually check if download is complete (fallback for when BroadcastReceiver doesn't fire)
     * Call this when progress reaches 100% as a backup
     */
    fun manualCheckDownloadComplete() {
        if (downloadId == -1L || currentModelName == null) {
            Log.w(TAG, "⚠️ manualCheckDownloadComplete called but no active download")
            return
        }

        if (completionCheckScheduled) {
            Log.w(TAG, "⚠️ Completion check already scheduled, skipping")
            return
        }

        completionCheckScheduled = true
        Log.i(TAG, "🔍 Manually checking download completion for ID: $downloadId")

        try {
            val query = DownloadManager.Query().setFilterById(downloadId)
            downloadManager.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )
                    Log.i(TAG, "   Status: $status (${getStatusString(status)})")

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        Log.i(TAG, "   ✅ Download complete! Triggering handleDownloadComplete")
                        // Unregister receiver since we're handling manually
                        downloadReceiver?.let {
                            try {
                                context.unregisterReceiver(it)
                                Log.i(TAG, "   Unregistered receiver (manual)")
                            } catch (e: Exception) {
                                Log.w(TAG, "   Could not unregister: ${e.message}")
                            }
                        }
                        downloadReceiver = null
                        handleDownloadComplete(currentModelName!!)
                    } else if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) {
                        Log.i(TAG, "   ⏳ Still downloading, will check again")
                        completionCheckScheduled = false
                    } else {
                        Log.e(TAG, "   ❌ Download failed with status: $status")
                        downloadCompleteCallback?.invoke(false, "Download failed")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in manual completion check", e)
            completionCheckScheduled = false
        }
    }

    private fun getStatusString(status: Int): String {
        return when (status) {
            DownloadManager.STATUS_PENDING -> "PENDING"
            DownloadManager.STATUS_RUNNING -> "RUNNING"
            DownloadManager.STATUS_PAUSED -> "PAUSED"
            DownloadManager.STATUS_SUCCESSFUL -> "SUCCESSFUL"
            DownloadManager.STATUS_FAILED -> "FAILED"
            else -> "UNKNOWN($status)"
        }
    }

    /**
     * Cancel ongoing download
     */
    fun cancelDownload() {
        if (downloadId != -1L) {
            downloadManager.remove(downloadId)
            downloadId = -1
            currentModelName = null
            completionCheckScheduled = false
            Log.i(TAG, "🛑 Download cancelled")
        }
    }

    /**
     * Get list of available models in storage (ONNX-only)
     */
    fun getAvailableModels(): List<File> {
        val modelsDir = File(context.filesDir, MODELS_DIR)
        if (!modelsDir.exists()) return emptyList()

        // ONNX-only filter
        return modelsDir.listFiles()?.filter {
            it.isFile && it.name.endsWith(".onnx", ignoreCase = true)
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

    /**
     * Get human-readable error message for download failure reason
     */
    private fun getDownloadErrorMessage(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME ->
                "Download cannot be resumed. Please try again."
            DownloadManager.ERROR_DEVICE_NOT_FOUND ->
                "No external storage found. Please check your device storage."
            DownloadManager.ERROR_FILE_ALREADY_EXISTS ->
                "File already exists. Please delete it and try again."
            DownloadManager.ERROR_FILE_ERROR ->
                "File system error. Please check available storage space."
            DownloadManager.ERROR_HTTP_DATA_ERROR ->
                "Network error. Please check your internet connection and try again."
            DownloadManager.ERROR_INSUFFICIENT_SPACE ->
                "Insufficient storage space. Please free up space and try again."
            DownloadManager.ERROR_TOO_MANY_REDIRECTS ->
                "Too many redirects. The download URL may be invalid."
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE ->
                "Server error. Please try again later."
            DownloadManager.ERROR_UNKNOWN ->
                "Unknown error. Please check your internet connection and try again."
            else ->
                "Download failed (error code: $reason). Please try again."
        }
    }
}
