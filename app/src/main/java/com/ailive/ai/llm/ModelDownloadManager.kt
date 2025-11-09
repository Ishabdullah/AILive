package com.ailive.ai.llm

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
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

        // Qwen2-VL-2B-Instruct GGUF model (Q4_K_M quantized for mobile)
        // Vision-Language Model: Can understand both images and text
        // Perfect for conversational AI with camera input
        // Switched from ONNX to GGUF for better Android compatibility

        // Base URL for Qwen2-VL-2B-Instruct GGUF (bartowski quantization)
        private const val QWEN_VL_BASE_URL = "https://huggingface.co/bartowski/Qwen2-VL-2B-Instruct-GGUF/resolve/main"

        // Main model file (Q4_K_M quantization - balanced quality/size)
        // Single file - much simpler than 8-file ONNX approach!
        const val QWEN_VL_MODEL_GGUF = "Qwen2-VL-2B-Instruct-Q4_K_M.gguf"
        const val QWEN_VL_MODEL_URL = "$QWEN_VL_BASE_URL/$QWEN_VL_MODEL_GGUF"

        // Multimodal projection file (required for vision support)
        // TODO: Add when implementing vision - for now, text-only
        const val QWEN_VL_MMPROJ = "mmproj-Qwen2-VL-2B-Instruct-f32.gguf"
        const val QWEN_VL_MMPROJ_URL = "$QWEN_VL_BASE_URL/$QWEN_VL_MMPROJ"

        // Model info:
        // - 2B parameters (instruction-tuned for conversation)
        // - GGUF format: Better mobile support than ONNX
        // - Q4_K_M quantization: 4-bit with medium quality
        // - Single file: 986MB (vs 3.7GB for ONNX)
        // - Built-in tokenizer (no separate vocab files needed)
        //
        // Quantization quality ladder (from bartowski):
        // Q2_K: 676MB (lowest quality)
        // Q4_K_M: 986MB (recommended for mobile - good balance)
        // Q5_K_M: 1.13GB (higher quality)
        // Q6_K: 1.27GB (very high quality)

        private const val MODELS_DIR = "models"

        // Minimum valid model size
        private const val MIN_MODEL_SIZE_BYTES = 100 * 1024 * 1024L  // 100MB (GGUF models are large)
    }

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId: Long = -1
    private var downloadCompleteCallback: ((Boolean, String) -> Unit)? = null
    private var downloadReceiver: BroadcastReceiver? = null  // Keep reference to unregister later
    private var currentModelName: String? = null  // Track current download model name
    private var completionCheckScheduled = false  // Prevent multiple manual checks
    private var isHandlingCompletion = false  // Guard against duplicate handleDownloadComplete() calls

    // Polling mechanism for download status (fallback if BroadcastReceiver doesn't fire)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var statusCheckRunnable: Runnable? = null
    private val POLL_INTERVAL_MS = 5000L  // Check every 5 seconds

    /**
     * Get the directory where model files are stored.
     *
     * Android 13+ (API 33+): App-private external storage (no permissions needed)
     *   Path: /Android/data/com.ailive/files/Download/
     *   Files deleted when app uninstalled
     *
     * Android 12- (API 32-): Public Downloads folder (requires READ_EXTERNAL_STORAGE)
     *   Path: /storage/emulated/0/Download/
     *   Files persist after app uninstall
     */
    private fun getModelsDir(): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: Use app-private external storage (scoped storage compliant)
            val appPrivateDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            appPrivateDir ?: context.filesDir.also {
                Log.w(TAG, "⚠️ External storage not available, using internal storage")
            }
        } else {
            // Android 12-: Use public Downloads (legacy behavior)
            getModelsDir()
        }.also { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
                Log.i(TAG, "📁 Created models directory: ${dir.absolutePath}")
            }
        }
    }

    /**
     * Check if Qwen2-VL GGUF model exists in models folder
     * Much simpler than ONNX - just one file!
     */
    fun isQwenVLModelAvailable(): Boolean {
        val downloadsDir = getModelsDir()
        val modelFile = File(downloadsDir, QWEN_VL_MODEL_GGUF)

        if (!modelFile.exists()) {
            Log.i(TAG, "❌ Missing required file: $QWEN_VL_MODEL_GGUF")
            return false
        }

        val sizeMB = modelFile.length() / 1024 / 1024
        Log.d(TAG, "✓ Found GGUF model: $QWEN_VL_MODEL_GGUF (${sizeMB}MB)")

        if (modelFile.length() < MIN_MODEL_SIZE_BYTES) {
            Log.e(TAG, "❌ Model file too small (${sizeMB}MB), likely corrupted")
            return false
        }

        Log.i(TAG, "✅ Qwen2-VL GGUF model available (${sizeMB}MB)")
        return true
    }

    /**
     * Check if a model file exists in Downloads folder
     * If modelName is not specified, checks if Qwen2-VL model exists
     */
    fun isModelAvailable(modelName: String? = null): Boolean {
        val downloadsDir = getModelsDir()

        // If specific model requested, check for it
        if (modelName != null) {
            val modelFile = File(downloadsDir, modelName)
            val exists = modelFile.exists()
            if (exists) {
                Log.i(TAG, "✅ Model found in Downloads: ${modelFile.absolutePath} (${modelFile.length() / 1024 / 1024}MB)")
            } else {
                Log.i(TAG, "❌ Model not found in Downloads: ${modelFile.absolutePath}")
            }
            return exists
        }

        // Otherwise, check if Qwen2-VL model exists
        return isQwenVLModelAvailable()
    }

    /**
     * Get the path to a model file in Downloads folder
     */
    fun getModelPath(modelName: String = QWEN_VL_MODEL_GGUF): String {
        val downloadsDir = getModelsDir()
        return File(downloadsDir, modelName).absolutePath
    }

    /**
     * Get the Downloads directory path (where models are stored)
     */
    fun getModelsDirectory(): String {
        return getModelsDir().absolutePath
    }

    /**
     * Get list of available GGUF model files in Downloads folder
     */
    fun getAvailableModelsInDownloads(): List<File> {
        val downloadsDir = getModelsDir()
        if (!downloadsDir.exists()) return emptyList()

        return downloadsDir.listFiles()?.filter {
            it.isFile && it.name.endsWith(".gguf", ignoreCase = true)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Download Qwen2-VL GGUF model
     * Much simpler than ONNX - just ONE file!
     * Size: 986MB (Q4_K_M quantization)
     */
    fun downloadQwenVLModel(onProgress: (String, Int, Int) -> Unit, onComplete: (Boolean, String) -> Unit) {
        Log.i(TAG, "📥 Starting Qwen2-VL GGUF model download...")
        Log.i(TAG, "   Model: $QWEN_VL_MODEL_GGUF (986MB)")
        Log.i(TAG, "   Quantization: Q4_K_M (balanced quality/size)")

        // Report progress (file 1 of 1)
        onProgress(QWEN_VL_MODEL_GGUF, 1, 1)

        // Download the single GGUF file
        downloadModel(QWEN_VL_MODEL_URL, QWEN_VL_MODEL_GGUF) { success, error ->
            if (success) {
                Log.i(TAG, "✅ Qwen2-VL GGUF model downloaded successfully!")
                onComplete(true, "")
            } else {
                Log.e(TAG, "❌ Failed to download GGUF model: $error")
                onComplete(false, "Failed to download model: $error")
            }
        }
    }

    /**
     * Download a single model file from HuggingFace (ONNX/BIN)
     *
     * Used internally by downloadQwenVLModel() for batch downloads.
     * For new downloads, use downloadQwenVLModel() instead.
     *
     * @param modelUrl URL to download from
     * @param modelName Name to save as
     * @param onComplete Callback with (success, errorMessage)
     */
    fun downloadModel(
        modelUrl: String,
        modelName: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        Log.i(TAG, "📥 Starting download: $modelName")
        Log.i(TAG, "   URL: $modelUrl")

        // Check if file already exists in Downloads
        val downloadsDir = getModelsDir()
        val existingFile = File(downloadsDir, modelName)
        if (existingFile.exists()) {
            val fileSizeMB = existingFile.length() / 1024 / 1024
            Log.i(TAG, "✓ File already exists: $modelName (${fileSizeMB}MB)")
            Log.i(TAG, "   Skipping download")
            onComplete(true, "")
            return
        }

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
        isHandlingCompletion = false  // Reset for new download

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
            // Download location depends on Android version (scoped storage compliance)
            val request = DownloadManager.Request(modelUrl.toUri())
                .setTitle("AILive Model Download")
                .setDescription("Downloading $modelName...")
                .setMimeType("application/octet-stream")
                .setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                )
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

            // Set download destination based on Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+: Download to app-private external storage (no permissions needed)
                request.setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    modelName
                )
                Log.i(TAG, "📁 Download destination: App-private storage (Android 13+)")
            } else {
                // Android 12-: Download to public Downloads folder (requires permission)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, modelName)
                Log.i(TAG, "📁 Download destination: Public Downloads (Android 12-)")
            }

            downloadId = downloadManager.enqueue(request)
            Log.i(TAG, "✅ Download queued with ID: $downloadId")
            Log.i(TAG, "   Destination: ${getModelsDir().absolutePath}/$modelName")

            // Start polling download status as fallback (in case BroadcastReceiver doesn't fire)
            startPollingDownloadStatus()

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
     * Start polling download status (fallback if BroadcastReceiver doesn't fire)
     */
    private fun startPollingDownloadStatus() {
        // Cancel any existing polling
        stopPollingDownloadStatus()

        statusCheckRunnable = object : Runnable {
            override fun run() {
                checkDownloadStatus()
                // Schedule next check
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }

        // Start polling after initial delay
        handler.postDelayed(statusCheckRunnable!!, POLL_INTERVAL_MS)
        Log.d(TAG, "📊 Started polling download status (every ${POLL_INTERVAL_MS / 1000}s)")
    }

    /**
     * Stop polling download status
     */
    private fun stopPollingDownloadStatus() {
        statusCheckRunnable?.let {
            handler.removeCallbacks(it)
            statusCheckRunnable = null
            Log.d(TAG, "📊 Stopped polling download status")
        }
    }

    /**
     * Check current download status (called by polling mechanism)
     */
    private fun checkDownloadStatus() {
        if (downloadId == -1L) {
            stopPollingDownloadStatus()
            return
        }

        try {
            val query = DownloadManager.Query().setFilterById(downloadId)
            downloadManager.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            Log.i(TAG, "📊 Polling detected: Download SUCCESS")
                            stopPollingDownloadStatus()
                            // Trigger completion handler
                            currentModelName?.let { handleDownloadComplete(it) }
                        }
                        DownloadManager.STATUS_FAILED -> {
                            Log.e(TAG, "📊 Polling detected: Download FAILED")
                            stopPollingDownloadStatus()
                            currentModelName?.let { handleDownloadComplete(it) }
                        }
                        DownloadManager.STATUS_RUNNING,
                        DownloadManager.STATUS_PENDING -> {
                            // Still downloading - keep polling
                            val bytesDownloaded = cursor.getLong(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            )
                            val totalBytes = cursor.getLong(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            )
                            if (totalBytes > 0) {
                                val progress = (bytesDownloaded * 100) / totalBytes
                                Log.d(TAG, "📊 Download progress: $progress% (${bytesDownloaded / 1024 / 1024}MB / ${totalBytes / 1024 / 1024}MB)")
                            }
                        }
                        DownloadManager.STATUS_PAUSED -> {
                            Log.w(TAG, "📊 Download PAUSED")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking download status", e)
        }
    }

    /**
     * Handle download completion - verify file in Downloads folder
     * BUGFIX: Prevent duplicate invocations from both BroadcastReceiver and polling
     */
    private fun handleDownloadComplete(modelName: String) {
        // Guard against duplicate calls (can happen when both BroadcastReceiver and polling fire)
        if (isHandlingCompletion) {
            Log.w(TAG, "⚠️ handleDownloadComplete already in progress, ignoring duplicate call")
            return
        }

        isHandlingCompletion = true
        Log.i(TAG, "📥 handleDownloadComplete called for: $modelName")

        // Stop polling (in case called from BroadcastReceiver)
        stopPollingDownloadStatus()

        // Unregister BroadcastReceiver if still registered
        downloadReceiver?.let {
            try {
                context.unregisterReceiver(it)
                Log.d(TAG, "   Unregistered BroadcastReceiver in handleDownloadComplete")
            } catch (e: Exception) {
                // Already unregistered - that's fine
                Log.d(TAG, "   BroadcastReceiver already unregistered")
            }
            downloadReceiver = null
        }

        // Store callback locally and clear it immediately to prevent duplicate invocations
        val callback = downloadCompleteCallback
        downloadCompleteCallback = null

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
                                // Verify file exists in Downloads folder
                                val downloadsDir = getModelsDir()
                                val modelFile = File(downloadsDir, modelName)

                                // GGUF models are always large (hundreds of MB)
                                if (modelFile.exists() && modelFile.length() >= MIN_MODEL_SIZE_BYTES) {
                                    val sizeMB = modelFile.length() / 1024 / 1024
                                    Log.i(TAG, "✅ File verified in Downloads: ${modelFile.absolutePath}")
                                    Log.i(TAG, "   Size: ${sizeMB}MB")

                                    // Reset state BEFORE callback (callback may trigger next download)
                                    downloadId = -1
                                    currentModelName = null
                                    isHandlingCompletion = false

                                    callback?.invoke(true, "")
                                } else {
                                    val sizeMB = modelFile.length() / 1024 / 1024
                                    Log.e(TAG, "❌ File missing or too small in Downloads!")
                                    Log.e(TAG, "   Expected at least ${MIN_MODEL_SIZE_BYTES / 1024 / 1024}MB, got ${sizeMB}MB")

                                    // Reset state BEFORE callback
                                    downloadId = -1
                                    currentModelName = null
                                    isHandlingCompletion = false

                                    callback?.invoke(false, "Download verification failed")
                                }
                            } else {
                                Log.e(TAG, "❌ URI is null!")

                                // Reset state BEFORE callback
                                downloadId = -1
                                currentModelName = null
                                isHandlingCompletion = false

                                callback?.invoke(false, "Download URI is null")
                            }
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reason = cursor.getInt(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                            )
                            val errorMessage = getDownloadErrorMessage(reason)
                            Log.e(TAG, "❌ Download failed with reason: $reason - $errorMessage")

                            // Reset state BEFORE callback
                            downloadId = -1
                            currentModelName = null
                            isHandlingCompletion = false

                            callback?.invoke(false, errorMessage)
                        }
                        else -> {
                            Log.w(TAG, "⚠️ Download status: $status")

                            // Reset state BEFORE callback
                            downloadId = -1
                            currentModelName = null
                            isHandlingCompletion = false

                            callback?.invoke(false, "Unexpected download status: $status")
                        }
                    }
                } else {
                    Log.e(TAG, "❌ Cursor is empty!")

                    // Reset state BEFORE callback
                    downloadId = -1
                    currentModelName = null
                    isHandlingCompletion = false

                    callback?.invoke(false, "Could not query download status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling download completion", e)

            // Reset state BEFORE callback
            downloadId = -1
            currentModelName = null
            isHandlingCompletion = false

            callback?.invoke(false, "Error: ${e.message}")
        } finally {
            // Only reset the guard flag in finally (state reset happens before callbacks now)
            // This prevents duplicate calls but allows batch downloads to proceed
            isHandlingCompletion = false
            Log.d(TAG, "📥 Completion handling finished")
        }
    }


    /**
     * Import a model from user's storage (file picker) to Downloads folder - ONNX-only
     *
     * @param uri URI of the file selected by user
     * @param onComplete Callback with (success, errorMessage)
     */
    suspend fun importModelFromStorage(
        uri: Uri,
        onComplete: (Boolean, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            var fileName = QWEN_VL_MODEL_A

            // Get original filename
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            Log.i(TAG, "📥 Importing model to Downloads: $fileName")

            // Validate model format - ONNX/BIN files
            val isValidFormat = fileName.endsWith(".onnx", ignoreCase = true) ||
                                fileName.endsWith(".bin", ignoreCase = true)

            if (!isValidFormat) {
                Log.e(TAG, "❌ Invalid model format: $fileName")
                withContext(Dispatchers.Main) {
                    onComplete(false, "Invalid model format.\n\nSupported formats: .onnx, .bin")
                }
                return@withContext
            }

            Log.i(TAG, "✓ Valid model file detected")

            // Ensure Downloads directory exists
            val downloadsDir = getModelsDir()
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val destFile = File(downloadsDir, fileName)

            // Copy file to Downloads
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
     * Get list of available ONNX models in Downloads folder (legacy method)
     */
    fun getAvailableModels(): List<File> {
        return getAvailableModelsInDownloads()
    }

    /**
     * Delete a model file from Downloads folder
     */
    fun deleteModel(modelName: String): Boolean {
        val downloadsDir = getModelsDir()
        val modelFile = File(downloadsDir, modelName)
        return if (modelFile.exists()) {
            val deleted = modelFile.delete()
            if (deleted) {
                Log.i(TAG, "🗑️ Deleted model from Downloads: $modelName")
            }
            deleted
        } else {
            Log.w(TAG, "⚠️ Model not found in Downloads: $modelName")
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
