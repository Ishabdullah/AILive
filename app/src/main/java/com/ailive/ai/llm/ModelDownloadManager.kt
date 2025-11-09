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

        // Qwen2-VL-2B-Instruct ONNX models (Q4F16 quantized for mobile)
        // Vision-Language Model: Can understand both images and text
        // Perfect for conversational AI with camera input

        // Base URL for Qwen2-VL-2B-Instruct-ONNX-Q4-F16
        private const val QWEN_VL_BASE_URL = "https://huggingface.co/pdufour/Qwen2-VL-2B-Instruct-ONNX-Q4-F16/resolve/main"

        // Model files (Q4F16 quantized - ~3.7GB total)
        // A: Output projection (1.33GB), B: Vision encoder (234MB)
        // C: Batch size computation (6KB), D: Vision-text fusion (25KB)
        // E: Text decoder (997MB)
        const val QWEN_VL_MODEL_A = "QwenVL_A_q4f16.onnx"
        const val QWEN_VL_MODEL_B = "QwenVL_B_q4f16.onnx"
        const val QWEN_VL_MODEL_C = "QwenVL_C_q4f16.onnx"
        const val QWEN_VL_MODEL_D = "QwenVL_D_q4f16.onnx"
        const val QWEN_VL_MODEL_E = "QwenVL_E_q4f16.onnx"
        const val QWEN_VL_EMBEDDINGS = "embeddings_bf16.bin"

        // URLs for each model file
        const val QWEN_VL_URL_A = "$QWEN_VL_BASE_URL/onnx/$QWEN_VL_MODEL_A"
        const val QWEN_VL_URL_B = "$QWEN_VL_BASE_URL/onnx/$QWEN_VL_MODEL_B"
        const val QWEN_VL_URL_C = "$QWEN_VL_BASE_URL/onnx/$QWEN_VL_MODEL_C"
        const val QWEN_VL_URL_D = "$QWEN_VL_BASE_URL/onnx/$QWEN_VL_MODEL_D"
        const val QWEN_VL_URL_E = "$QWEN_VL_BASE_URL/onnx/$QWEN_VL_MODEL_E"
        const val QWEN_VL_URL_EMBEDDINGS = "$QWEN_VL_BASE_URL/$QWEN_VL_EMBEDDINGS"

        // Tokenizer files
        const val QWEN_VL_VOCAB = "vocab.json"
        const val QWEN_VL_MERGES = "merges.txt"
        const val QWEN_VL_URL_VOCAB = "$QWEN_VL_BASE_URL/$QWEN_VL_VOCAB"
        const val QWEN_VL_URL_MERGES = "$QWEN_VL_BASE_URL/$QWEN_VL_MERGES"

        // Model info:
        // - 2B parameters (instruction-tuned for conversation)
        // - Multimodal: image + text input → text output
        // - Supports VQA (visual question answering), image captioning
        // - Q4F16 quantization: 4-bit weights, fp16 activations
        // - Total size: ~3.7GB (manageable for modern Android devices)
        //
        // File sizes:
        // - QwenVL_A_q4f16.onnx: 1.33 GB
        // - QwenVL_B_q4f16.onnx: 234 MB
        // - QwenVL_E_q4f16.onnx: 997 MB
        // - embeddings_bf16.bin: 467 MB

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
     * Check if Qwen2-VL model files exist in models folder (all required files)
     */
    fun isQwenVLModelAvailable(): Boolean {
        val downloadsDir = getModelsDir()
        val requiredFiles = listOf(
            QWEN_VL_MODEL_A,
            QWEN_VL_MODEL_B,
            QWEN_VL_MODEL_C,
            QWEN_VL_MODEL_D,
            QWEN_VL_MODEL_E,
            QWEN_VL_EMBEDDINGS,
            QWEN_VL_VOCAB,
            QWEN_VL_MERGES
        )

        var allExist = true
        requiredFiles.forEach { fileName ->
            val file = File(downloadsDir, fileName)
            if (!file.exists()) {
                Log.i(TAG, "❌ Missing required file in Downloads: $fileName")
                allExist = false
            } else {
                Log.d(TAG, "✓ Found in Downloads: $fileName (${file.length() / 1024 / 1024}MB)")
            }
        }

        if (allExist) {
            Log.i(TAG, "✅ All Qwen2-VL model files present in Downloads")
        }

        return allExist
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
    fun getModelPath(modelName: String = QWEN_VL_MODEL_A): String {
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
     * Get list of available ONNX model files in Downloads folder
     */
    fun getAvailableModelsInDownloads(): List<File> {
        val downloadsDir = getModelsDir()
        if (!downloadsDir.exists()) return emptyList()

        return downloadsDir.listFiles()?.filter {
            it.isFile && (it.name.endsWith(".onnx", ignoreCase = true) || it.name.endsWith(".bin", ignoreCase = true))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Download all Qwen2-VL model files (batch download)
     * Downloads: 5 ONNX models (A,B,C,D,E), embeddings, vocab, merges
     * Total: ~3.7GB
     *
     * Pipeline: Image → A → B → C → D → E → Output
     */
    fun downloadQwenVLModel(onProgress: (String, Int, Int) -> Unit, onComplete: (Boolean, String) -> Unit) {
        Log.i(TAG, "📥 Starting Qwen2-VL model batch download (8 files)...")

        val filesToDownload = listOf(
            // Download small files first (tokenizer + tiny models)
            Pair(QWEN_VL_URL_VOCAB, QWEN_VL_VOCAB),        // 2.78 MB
            Pair(QWEN_VL_URL_MERGES, QWEN_VL_MERGES),      // 1.67 MB
            Pair(QWEN_VL_URL_C, QWEN_VL_MODEL_C),          // 6 KB (batch size)
            Pair(QWEN_VL_URL_D, QWEN_VL_MODEL_D),          // 25 KB (vision-text fusion)
            // Then medium/large files
            Pair(QWEN_VL_URL_B, QWEN_VL_MODEL_B),          // 234 MB (vision encoder)
            Pair(QWEN_VL_URL_EMBEDDINGS, QWEN_VL_EMBEDDINGS), // 467 MB
            Pair(QWEN_VL_URL_E, QWEN_VL_MODEL_E),          // 997 MB (text decoder)
            Pair(QWEN_VL_URL_A, QWEN_VL_MODEL_A)           // 1.33 GB (output projection)
        )

        var currentFileIndex = 0

        fun downloadNext() {
            if (currentFileIndex >= filesToDownload.size) {
                Log.i(TAG, "✅ All Qwen2-VL files downloaded successfully!")
                onComplete(true, "")
                return
            }

            val (url, fileName) = filesToDownload[currentFileIndex]
            val fileNum = currentFileIndex + 1
            val totalFiles = filesToDownload.size

            Log.i(TAG, "📥 Downloading file $fileNum/$totalFiles: $fileName")
            onProgress(fileName, fileNum, totalFiles)

            downloadModel(url, fileName) { success, error ->
                if (success) {
                    currentFileIndex++
                    downloadNext()
                } else {
                    Log.e(TAG, "❌ Failed to download $fileName: $error")
                    onComplete(false, "Failed to download $fileName: $error")
                }
            }
        }

        downloadNext()
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
     */
    private fun handleDownloadComplete(modelName: String) {
        Log.i(TAG, "📥 handleDownloadComplete called for: $modelName")

        // Stop polling (in case called from BroadcastReceiver)
        stopPollingDownloadStatus()

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

                                if (modelFile.exists() && modelFile.length() >= MIN_MODEL_SIZE_BYTES) {
                                    Log.i(TAG, "✅ File verified in Downloads: ${modelFile.absolutePath}")
                                    Log.i(TAG, "   Size: ${modelFile.length() / 1024 / 1024}MB")
                                    downloadCompleteCallback?.invoke(true, "")
                                } else {
                                    Log.e(TAG, "❌ File missing or too small in Downloads!")
                                    downloadCompleteCallback?.invoke(false, "Download verification failed")
                                }
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
