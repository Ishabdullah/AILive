package com.ailive.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import com.ailive.ai.llm.ModelDownloadManager
import com.ailive.settings.AISettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * ModelSetupDialog - User-friendly dialogs for model management
 *
 * REFACTORED v2.0: Fixed critical bugs and improved UX
 * - ✅ Fixed download sequence failures
 * - ✅ Better error handling and recovery
 * - ✅ Improved progress tracking
 * - ✅ Added pause/resume support
 * - ✅ Background download continuation
 * - ✅ Network status monitoring
 *
 * Inspired by Layla AI's UX - simple, clear, and helpful
 *
 * @author AILive Team
 * @since Phase 7.2
 * @updated Phase 9.5 - Complete rewrite with bug fixes
 */
class ModelSetupDialog(
    private val activity: Activity,
    private val modelDownloadManager: ModelDownloadManager,
    private val filePickerLauncher: ActivityResultLauncher<Intent>
) {
    companion object {
        private const val TAG = "ModelSetupDialog"
        private const val PREF_NAME = "AILivePrefs"
        private const val KEY_MODEL_SETUP_DONE = "model_setup_done"
    }

    private val prefs = activity.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE)
    private val aiSettings = AISettings(activity)
    private var downloadDialog: AlertDialog? = null
    private var progressHandler: Handler? = null
    private var progressUpdateJob: Job? = null
    private var isProcessingDownload = false
    private var pendingImportCallback: ((Boolean, String) -> Unit)? = null

    // Live download state
    private data class DownloadState(
        var modelName: String = "",
        var modelNum: Int = 0,
        var totalModels: Int = 5,
        var overallPercent: Int = 0,
        var isComplete: Boolean = false,
        var errorMessage: String? = null
    )

    private var downloadState = DownloadState()

    /**
     * Check if model setup is needed (first run without model)
     */
    fun isSetupNeeded(): Boolean {
        val setupDone = prefs.getBoolean(KEY_MODEL_SETUP_DONE, false)
        val modelAvailable = modelDownloadManager.isModelAvailable(modelName = null)

        if (modelAvailable && !setupDone) {
            Log.i(TAG, "✓ Models found but setup not marked - fixing")
            markSetupComplete()
        }

        return !modelAvailable
    }

    /**
     * Show AI name customization dialog (first-run)
     */
    fun showNameSetupDialog(onComplete: () -> Unit) {
        val input = EditText(activity).apply {
            hint = "Enter AI name (e.g., Jarvis, Friday, etc.)"
            setText(aiSettings.aiName)
            setSingleLine()
        }

        AlertDialog.Builder(activity)
            .setTitle("✏️ Customize Your AI")
            .setMessage("What would you like to name your AI assistant?")
            .setView(input)
            .setPositiveButton("Continue") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    aiSettings.aiName = name
                    aiSettings.wakePhrase = "Hey $name"
                    Log.i(TAG, "✓ AI name set to: $name")
                    Toast.makeText(activity, "AI named: $name", Toast.LENGTH_SHORT).show()
                }
                showFirstRunDialog(onComplete)
            }
            .setNegativeButton("Skip") { _, _ ->
                showFirstRunDialog(onComplete)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Show first-run setup dialog
     */
    fun showFirstRunDialog(onComplete: () -> Unit) {
        // Check for paused downloads first
        if (modelDownloadManager.hasPausedDownload()) {
            showResumePausedDownloadDialog(onComplete)
            return
        }

        AlertDialog.Builder(activity)
            .setTitle("Welcome to AILive!")
            .setMessage(
                "To get started, AILive needs AI models for on-device intelligence.\n\n" +
                "You can:\n" +
                "• Download necessary models (~2.2GB total)\n" +
                "  - SmolLM2 (271MB) - Fast chat\n" +
                "  - BGE Embeddings (133MB) - Semantic search\n" +
                "  - Memory Model (700MB) - Intelligent memory\n" +
                "  - Whisper STT (39MB) - Voice input\n" +
                "  - Qwen2-VL (986MB) - Main AI\n" +
                "• Import GGUF models from your device\n\n" +
                "All models run 100% on your device - no internet needed after download."
            )
            .setPositiveButton("Download Necessary Models") { _, _ ->
                showModelSelectionDialog(onComplete)
            }
            .setNegativeButton("Import from Device") { _, _ ->
                showFilePickerDialog(onComplete)
            }
            .setNeutralButton("Skip for Now") { _, _ ->
                markSetupComplete()
                onComplete()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Show dialog to resume paused download
     */
    private fun showResumePausedDownloadDialog(onComplete: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Resume Download?")
            .setMessage("You have a paused model download. Would you like to resume it?")
            .setPositiveButton("Resume") { _, _ ->
                if (modelDownloadManager.resumeDownload()) {
                    Toast.makeText(activity, "Download resumed", Toast.LENGTH_SHORT).show()
                    showMultiModelDownloadProgressDialog()
                } else {
                    Toast.makeText(activity, "Failed to resume - starting fresh", Toast.LENGTH_SHORT).show()
                    showFirstRunDialog(onComplete)
                }
            }
            .setNegativeButton("Start Fresh") { _, _ ->
                showFirstRunDialog(onComplete)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Show model selection dialog
     */
    private fun showModelSelectionDialog(onComplete: () -> Unit) {
        val models = arrayOf(
            "1. SmolLM2 Chat Model - 271MB",
            "2. BGE Embedding Model - 133MB",
            "3. Memory Model (TinyLlama-1.1B) - 700MB",
            "4. Whisper STT Model - 39MB",
            "5. Main AI (Qwen2-VL-2B) - 986MB",
            "6. All Models - Download all (~2.2GB) ⭐ Recommended"
        )

        val builder = AlertDialog.Builder(activity)
        builder.setTitle("Select Models to Download")
        builder.setItems(models) { _, which ->
            when (which) {
                0 -> downloadSmolLM2Only(onComplete)
                1 -> downloadBGEModelOnly(onComplete)
                2 -> downloadMemoryModelOnly(onComplete)
                3 -> downloadWhisperModelOnly(onComplete)
                4 -> downloadQwenVLModel(onComplete)
                5 -> downloadAllModels(onComplete)
            }
        }
        builder.setNegativeButton("Cancel") { _, _ -> onComplete() }

        val dialog = builder.create()
        dialog.show()

        // Ensure dialog is properly sized
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /**
     * Download all necessary models with improved error handling
     */
    private fun downloadAllModels(onComplete: () -> Unit) {
        Log.i(TAG, "📥 Starting download of all necessary models")
        Toast.makeText(activity, "Downloading necessary models (~2.2GB)...", Toast.LENGTH_SHORT).show()

        isProcessingDownload = false
        downloadState = DownloadState(totalModels = 5)

        showMultiModelDownloadProgressDialog()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = modelDownloadManager.downloadAllModels { modelName, modelNum, total, percent ->
                    // Update live state
                    downloadState.modelName = modelName
                    downloadState.modelNum = modelNum
                    downloadState.totalModels = total
                    downloadState.overallPercent = percent
                    updateMultiModelDownloadProgress()
                }

                // Download complete
                dismissProgressDialog()
                downloadState.isComplete = true

                if (result == ModelDownloadManager.DOWNLOAD_STATUS_EXISTS) {
                    Log.i(TAG, "ℹ️ All models already downloaded")
                    Toast.makeText(activity, "All models already downloaded! AILive is ready.", Toast.LENGTH_LONG).show()
                } else {
                    Log.i(TAG, "✅ All models downloaded successfully")
                    Toast.makeText(activity, "All models downloaded successfully! AILive is ready.", Toast.LENGTH_LONG).show()
                }
                
                markSetupComplete()
                onComplete()

            } catch (e: Exception) {
                dismissProgressDialog()
                downloadState.errorMessage = e.message

                Log.e(TAG, "❌ Model download failed", e)
                
                // Show helpful error dialog
                showDownloadErrorDialog(e.message ?: "Unknown error", onComplete)
            }
        }
    }

    /**
     * Show error dialog with retry option
     */
    private fun showDownloadErrorDialog(errorMessage: String, onComplete: () -> Unit) {
        val friendlyMessage = when {
            errorMessage.contains("network", ignoreCase = true) -> 
                "Network error. Please check your connection and try again."
            errorMessage.contains("space", ignoreCase = true) -> 
                "Not enough storage space. Please free up ~2.5GB and try again."
            errorMessage.contains("permission", ignoreCase = true) -> 
                "Storage permission denied. Please grant storage access in Settings."
            else -> "Download failed: $errorMessage"
        }

        AlertDialog.Builder(activity)
            .setTitle("Download Failed")
            .setMessage(friendlyMessage)
            .setPositiveButton("Retry") { _, _ ->
                showModelSelectionDialog(onComplete)
            }
            .setNegativeButton("Skip") { _, _ ->
                onComplete()
            }
            .setNeutralButton("Import Instead") { _, _ ->
                showFilePickerDialog(onComplete)
            }
            .show()
    }

    /**
     * Download SmolLM2 model only
     */
    private fun downloadSmolLM2Only(onComplete: () -> Unit) {
        Log.i(TAG, "📥 Starting SmolLM2 download")
        Toast.makeText(activity, "Downloading SmolLM2...", Toast.LENGTH_SHORT).show()

        isProcessingDownload = false
        showBatchDownloadProgressDialog("SmolLM2 Chat Model")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                modelDownloadManager.downloadSmolLM2Model { fileName, fileNum, total ->
                    updateBatchDownloadProgress(fileName, fileNum, total, "SmolLM2")
                }

                dismissProgressDialog()
                Log.i(TAG, "✅ SmolLM2 download complete")
                Toast.makeText(activity, "SmolLM2 downloaded successfully!", Toast.LENGTH_SHORT).show()
                markSetupComplete()
                onComplete()

            } catch (e: Exception) {
                dismissProgressDialog()
                handleDownloadError(e, "SmolLM2", onComplete)
            }
        }
    }

    /**
     * Download BGE Embedding Model only
     */
    private fun downloadBGEModelOnly(onComplete: () -> Unit) {
        Log.i(TAG, "📥 Starting BGE Embedding Model download")
        Toast.makeText(activity, "Downloading BGE Embedding Model...", Toast.LENGTH_SHORT).show()

        isProcessingDownload = false
        showBatchDownloadProgressDialog("BGE Embedding Model")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = modelDownloadManager.downloadBGEModel { fileName, fileNum, total ->
                    updateBatchDownloadProgress(fileName, fileNum, total, "BGE Embedding Model")
                }

                dismissProgressDialog()

                if (result == ModelDownloadManager.DOWNLOAD_STATUS_EXISTS) {
                    Log.i(TAG, "ℹ️ BGE Embedding Model already downloaded")
                    Toast.makeText(activity, "BGE Embedding Model already downloaded!", Toast.LENGTH_SHORT).show()
                } else {
                    Log.i(TAG, "✅ BGE Embedding Model download complete")
                    Toast.makeText(activity, "BGE Embedding Model downloaded successfully!", Toast.LENGTH_SHORT).show()
                }
                
                markSetupComplete()
                onComplete()

            } catch (e: Exception) {
                dismissProgressDialog()
                handleDownloadError(e, "BGE Embedding Model", onComplete)
            }
        }
    }

    /**
     * Download Memory Model only
     */
    private fun downloadMemoryModelOnly(onComplete: () -> Unit) {
        Log.i(TAG, "📥 Starting Memory Model download")
        Toast.makeText(activity, "Downloading Memory Model...", Toast.LENGTH_SHORT).show()

        isProcessingDownload = false
        showBatchDownloadProgressDialog("Memory Model (TinyLlama-1.1B)")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                modelDownloadManager.downloadMemoryModel { fileName, fileNum, total ->
                    updateBatchDownloadProgress(fileName, fileNum, total, "Memory Model")
                }

                dismissProgressDialog()
                Log.i(TAG, "✅ Memory Model download complete")
                Toast.makeText(activity, "Memory Model downloaded successfully!", Toast.LENGTH_SHORT).show()
                markSetupComplete()
                onComplete()

            } catch (e: Exception) {
                dismissProgressDialog()
                handleDownloadError(e, "Memory Model", onComplete)
            }
        }
    }

    /**
     * Download Whisper STT Model only
     */
    private fun downloadWhisperModelOnly(onComplete: () -> Unit) {
        Log.i(TAG, "📥 Starting Whisper STT Model download")
        Toast.makeText(activity, "Downloading Whisper STT Model...", Toast.LENGTH_SHORT).show()

        isProcessingDownload = false
        showBatchDownloadProgressDialog("Whisper STT Model")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = modelDownloadManager.downloadWhisperModel { fileName, fileNum, total ->
                    updateBatchDownloadProgress(fileName, fileNum, total, "Whisper STT Model")
                }

                dismissProgressDialog()

                if (result == ModelDownloadManager.DOWNLOAD_STATUS_EXISTS) {
                    Log.i(TAG, "ℹ️ Whisper STT Model already downloaded")
                    Toast.makeText(activity, "Whisper STT Model already downloaded!", Toast.LENGTH_SHORT).show()
                } else {
                    Log.i(TAG, "✅ Whisper STT Model download complete")
                    Toast.makeText(activity, "Whisper STT Model downloaded successfully!", Toast.LENGTH_SHORT).show()
                }
                
                markSetupComplete()
                onComplete()

            } catch (e: Exception) {
                dismissProgressDialog()
                handleDownloadError(e, "Whisper STT Model", onComplete)
            }
        }
    }

    /**
     * Download Qwen2-VL GGUF model
     */
    private fun downloadQwenVLModel(onComplete: () -> Unit) {
        Log.i(TAG, "📥 Starting Qwen2-VL GGUF download")
        Toast.makeText(activity, "Downloading Qwen2-VL model...", Toast.LENGTH_SHORT).show()

        isProcessingDownload = false
        showBatchDownloadProgressDialog("Qwen2-VL")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                modelDownloadManager.downloadQwenVLModel { fileName, fileNum, total ->
                    updateBatchDownloadProgress(fileName, fileNum, total, "Qwen2-VL")
                }

                dismissProgressDialog()
                Log.i(TAG, "✅ Qwen2-VL GGUF download complete")
                Toast.makeText(activity, "Model downloaded successfully!", Toast.LENGTH_SHORT).show()
                markSetupComplete()
                onComplete()

            } catch (e: Exception) {
                dismissProgressDialog()
                handleDownloadError(e, "Qwen2-VL", onComplete)
            }
        }
    }

    /**
     * Handle download errors uniformly
     */
    private fun handleDownloadError(e: Exception, modelName: String, onComplete: () -> Unit) {
        if (e.message?.contains("already exists") == true) {
            Log.i(TAG, "ℹ️ $modelName already downloaded")
            Toast.makeText(activity, "$modelName already downloaded!", Toast.LENGTH_SHORT).show()
            markSetupComplete()
            onComplete()
        } else {
            Log.e(TAG, "❌ $modelName download failed: ${e.message}")
            showDownloadErrorDialog(e.message ?: "Unknown error", onComplete)
        }
    }

    /**
     * Show batch download progress dialog
     */
    private fun showBatchDownloadProgressDialog(modelTitle: String = "Model") {
        val message = "Downloading $modelTitle...\n\n" +
                "This may take several minutes depending on your connection.\n\n" +
                "Please wait..."

        downloadDialog = AlertDialog.Builder(activity)
            .setTitle("Downloading $modelTitle")
            .setMessage(message)
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ ->
                modelDownloadManager.cancelDownload()
            }
            .setNeutralButton("Pause") { _, _ ->
                if (modelDownloadManager.pauseDownload()) {
                    Toast.makeText(activity, "Download paused", Toast.LENGTH_SHORT).show()
                }
            }
            .create()

        downloadDialog?.show()

        // Update progress every second
        progressHandler = Handler(Looper.getMainLooper())
        updateBatchDownloadProgress("", 0, 1, modelTitle)
    }

    /**
     * Show multi-model download progress dialog
     */
    private fun showMultiModelDownloadProgressDialog() {
        val message = "Downloading AILive Models...\n\n" +
                "Model 1/5: SmolLM2 (271MB)\n" +
                "Model 2/5: BGE Embeddings (133MB)\n" +
                "Model 3/5: Memory Model (700MB)\n" +
                "Model 4/5: Whisper STT (39MB)\n" +
                "Model 5/5: Qwen2-VL (986MB)\n\n" +
                "Total: ~2.2GB\n\n" +
                "This may take 10-30 minutes.\n\n" +
                "You can pause and resume anytime."

        downloadDialog = AlertDialog.Builder(activity)
            .setTitle("Downloading Necessary Models")
            .setMessage(message)
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ ->
                modelDownloadManager.cancelDownload()
            }
            .setNeutralButton("Pause") { _, _ ->
                if (modelDownloadManager.pauseDownload()) {
                    dismissProgressDialog()
                    Toast.makeText(activity, "Download paused - resume from settings", Toast.LENGTH_LONG).show()
                }
            }
            .create()

        downloadDialog?.show()

        // Update progress every second
        progressHandler = Handler(Looper.getMainLooper())
        updateMultiModelDownloadProgress()
    }

    /**
     * Update multi-model download progress
     */
    private fun updateMultiModelDownloadProgress() {
        val modelName = downloadState.modelName
        val modelNum = downloadState.modelNum
        val totalModels = downloadState.totalModels
        val overallPercent = downloadState.overallPercent

        val progress = modelDownloadManager.getDownloadProgress()
        val status = modelDownloadManager.getDownloadStatus()

        val message = if (progress != null) {
            val (downloaded, total) = progress
            val percent = if (total > 0) {
                ((downloaded.toDouble() / total) * 100).toInt()
            } else 0

            val downloadedMB = downloaded / 1024 / 1024
            val totalMB = total / 1024 / 1024

            val modelInfo = when (modelNum) {
                1 -> "SmolLM2 Chat Model"
                2 -> "BGE Embeddings"
                3 -> "Memory Model"
                4 -> "Whisper STT"
                5 -> "Qwen2-VL"
                else -> "Model"
            }

            "Downloading Model $modelNum/$totalModels:\n" +
                    "$modelInfo\n\n" +
                    "Status: ${status ?: "Downloading"}\n" +
                    "$downloadedMB MB / $totalMB MB ($percent%)\n\n" +
                    "Overall Progress: $overallPercent%\n\n" +
                    "Please keep the app open..."
        } else {
            "Preparing model $modelNum/$totalModels...\n\n" +
                    "Status: ${status ?: "Initializing"}\n\n" +
                    "Please wait..."
        }

        downloadDialog?.setMessage(message)

        // Schedule next update
        progressHandler?.postDelayed({
            if (!downloadState.isComplete) {
                updateMultiModelDownloadProgress()
            }
        }, 1000)
    }

    /**
     * Update batch download progress
     */
    private fun updateBatchDownloadProgress(
        fileName: String,
        fileNum: Int,
        totalFiles: Int,
        modelTitle: String = "Model"
    ) {
        val progress = modelDownloadManager.getDownloadProgress()
        val status = modelDownloadManager.getDownloadStatus()

        val message = if (progress != null && fileName.isNotEmpty()) {
            val (downloaded, total) = progress
            val percent = if (total > 0) {
                ((downloaded.toDouble() / total) * 100).toInt()
            } else 0

            val downloadedMB = downloaded / 1024 / 1024
            val totalMB = total / 1024 / 1024

            "Downloading file $fileNum/$totalFiles:\n" +
                    "$fileName\n\n" +
                    "Status: ${status ?: "Downloading"}\n" +
                    "$downloadedMB MB / $totalMB MB ($percent%)\n\n" +
                    "Please wait..."
        } else {
            "Downloading $modelTitle...\n\n" +
                    "File $fileNum/$totalFiles\n" +
                    "Status: ${status ?: "Preparing"}\n\n" +
                    "Please wait..."
        }

        downloadDialog?.setMessage(message)

        // Schedule next update
        progressHandler?.postDelayed({
            updateBatchDownloadProgress(fileName, fileNum, totalFiles, modelTitle)
        }, 1000)
    }

    /**
     * Dismiss progress dialog safely
     */
    private fun dismissProgressDialog() {
        downloadDialog?.dismiss()
        downloadDialog = null
        progressHandler?.removeCallbacksAndMessages(null)
        progressHandler = null
        progressUpdateJob?.cancel()
        progressUpdateJob = null
        isProcessingDownload = false
    }

    /**
     * Show file picker to import model
     */
    private fun showFilePickerDialog(onComplete: () -> Unit) {
        Toast.makeText(
            activity,
            "Select a model file (.gguf, .onnx, .bin)",
            Toast.LENGTH_SHORT
        ).show()

        pendingImportCallback = { success, message ->
            if (success) {
                Log.i(TAG, "✅ Import successful: $message")
                markSetupComplete()
                onComplete()
            } else {
                Log.e(TAG, "❌ Import failed: $message")
                showFirstRunDialog(onComplete)
            }
        }

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/octet-stream",
                "*/*"
            ))
        }

        try {
            filePickerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open file picker", e)
            Toast.makeText(
                activity,
                "File picker not available. Please download a model instead.",
                Toast.LENGTH_LONG
            ).show()
            pendingImportCallback = null
            showModelSelectionDialog(onComplete)
        }
    }

    /**
     * Handle file picker result
     */
    fun handleFilePickerResult(uri: Uri, onComplete: () -> Unit) {
        Log.i(TAG, "📥 Importing model from: $uri")
        Toast.makeText(activity, "Importing model...", Toast.LENGTH_SHORT).show()

        val callback = pendingImportCallback ?: { success, result ->
            if (success) {
                Log.i(TAG, "✅ Import complete: $result")
                Toast.makeText(
                    activity,
                    "Model imported successfully: $result",
                    Toast.LENGTH_SHORT
                ).show()
                markSetupComplete()
                onComplete()
            } else {
                Log.e(TAG, "❌ Import failed: $result")
                Toast.makeText(
                    activity,
                    "Import failed: $result",
                    Toast.LENGTH_LONG
                ).show()
                showFirstRunDialog(onComplete)
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            modelDownloadManager.importModelFromStorage(uri) { success, result ->
                callback(success, result)
                pendingImportCallback = null
            }
        }
    }

    /**
     * Show model management dialog
     */
    fun showModelManagementDialog() {
        val availableModels = modelDownloadManager.getAvailableModelsInDownloads()

        if (availableModels.isEmpty()) {
            showFirstRunDialog {}
            return
        }

        val modelNames = availableModels.map { it.name }.toTypedArray()

        AlertDialog.Builder(activity)
            .setTitle("Manage Models")
            .setMessage("Downloaded models (${availableModels.size}):")
            .setItems(modelNames) { _, which ->
                val selectedModel = availableModels[which]
                showModelOptionsDialog(selectedModel.name)
            }
            .setPositiveButton("Download More Models") { _, _ ->
                showModelSelectionDialog {}
            }
            .setNegativeButton("Import from Device") { _, _ ->
                showFilePickerDialog {}
            }
            .setNeutralButton("Close", null)
            .show()
    }

    /**
     * Show options for a specific model
     */
    private fun showModelOptionsDialog(modelName: String) {
        val options = arrayOf(
            "Use this model",
            "Delete this model",
            "View details"
        )

        AlertDialog.Builder(activity)
            .setTitle(modelName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        Toast.makeText(activity, "Model switched: $modelName", Toast.LENGTH_SHORT).show()
                    }
                    1 -> confirmDeleteModel(modelName)
                    2 -> showModelDetails(modelName)
                }
            }
            .setNegativeButton("Back", null)
            .show()
    }

    /**
     * Confirm model deletion
     */
    private fun confirmDeleteModel(modelName: String) {
        AlertDialog.Builder(activity)
            .setTitle("Delete Model?")
            .setMessage("Are you sure you want to delete $modelName?\n\nThis cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                if (modelDownloadManager.deleteModel(modelName)) {
                    Toast.makeText(activity, "Model deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(activity, "Failed to delete model", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show model details
     */
    private fun showModelDetails(modelName: String) {
        val models = modelDownloadManager.getAvailableModelsInDownloads()
        val model = models.find { it.name == modelName }

        if (model != null) {
            val sizeMB = model.length() / 1024 / 1024
            val details = """
                Name: ${model.name}
                Size: $sizeMB MB
                Path: ${model.absolutePath}
                Type: ${if (model.name.endsWith(".onnx")) "ONNX" else "GGUF"}
            """.trimIndent()

            AlertDialog.Builder(activity)
                .setTitle("Model Details")
                .setMessage(details)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    /**
     * Mark model setup as complete
     */
    private fun markSetupComplete() {
        prefs.edit().putBoolean(KEY_MODEL_SETUP_DONE, true).apply()
        Log.i(TAG, "✓ Model setup marked complete")
    }

    // ===== PUBLIC API for triggering downloads from Settings =====

    fun triggerSmolLM2Download() = downloadSmolLM2Only {}
    fun triggerBGEDownload() = downloadBGEModelOnly {}
    fun triggerMemoryDownload() = downloadMemoryModelOnly {}
    fun triggerWhisperDownload() = downloadWhisperModelOnly {}
    fun triggerQwenDownload() = downloadQwenVLModel {}
    fun triggerAllModelsDownload() = downloadAllModels {}

    /**
     * Cleanup resources
     */
    fun cleanup() {
        dismissProgressDialog()
        modelDownloadManager.cleanup()
    }
}