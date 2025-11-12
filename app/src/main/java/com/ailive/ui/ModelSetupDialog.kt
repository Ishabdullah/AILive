package com.ailive.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import com.ailive.R
import com.ailive.ai.llm.ModelDownloadManager
import com.ailive.settings.AISettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ModelSetupDialog - User-friendly dialogs for model management
 *
 * Inspired by Layla AI's UX - simple, clear, and helpful
 *
 * Features:
 * - First-run dialog to download or import model
 * - Download progress tracking
 * - File picker for custom models
 * - Model selection with recommendations
 *
 * @author AILive Team
 * @since Phase 7.2
 * @updated Phase 7.5 - Fixed lifecycle registration issues
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
    private var isProcessingDownload = false  // Track if we're in file-move phase
    private var pendingImportCallback: ((Boolean, String) -> Unit)? = null  // Store import callback

    /**
     * Check if model setup is needed (first run without model)
     * Returns true if no models are available (checks for ANY model, not just default)
     */
    fun isSetupNeeded(): Boolean {
        val setupDone = prefs.getBoolean(KEY_MODEL_SETUP_DONE, false)
        val modelAvailable = modelDownloadManager.isModelAvailable(modelName = null)  // Check for ANY model

        if (modelAvailable && !setupDone) {
            // Models exist but setup wasn't marked done - fix that
            Log.i(TAG, "Models found but setup not marked complete - fixing")
            markSetupComplete()
        }

        return !modelAvailable  // Only need setup if NO models at all
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
                    Log.i(TAG, "AI name set to: $name")
                    Toast.makeText(activity, "AI named: $name", Toast.LENGTH_SHORT).show()
                }
                // Proceed to model setup
                showFirstRunDialog(onComplete)
            }
            .setNegativeButton("Skip") { _, _ ->
                // Use default name
                showFirstRunDialog(onComplete)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Show first-run setup dialog (like Layla's welcome screen)
     */
    fun showFirstRunDialog(onComplete: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Welcome to AILive!")
            .setMessage(
                "To get started, AILive needs AI models for on-device intelligence.\n\n" +
                "You can:\n" +
                "• Download necessary models (~1.7GB total)\n" +
                "  - Memory Model (TinyLlama-1.1B, 700MB) - For intelligent memory\n" +
                "  - Main AI (Qwen2-VL-2B, 986MB) - For conversation & vision\n" +
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
     * Show model selection dialog with recommendations (GGUF models)
     * Lists each model individually, then "All Models" option
     */
    private fun showModelSelectionDialog(onComplete: () -> Unit) {
        val models = arrayOf(
            "1. Memory Model (TinyLlama-1.1B) - 700MB",
            "2. Main AI (Qwen2-VL-2B) - 986MB",
            "3. All Models - Download both (~1.7GB) ⭐ Recommended"
        )

        AlertDialog.Builder(activity)
            .setTitle("Select Models to Download")
            .setMessage("Choose individual models or download all for full functionality:")
            .setItems(models) { _, which ->
                when (which) {
                    0 -> downloadMemoryModelOnly(onComplete)  // Memory model only
                    1 -> downloadQwenVLModel(onComplete)  // Qwen only
                    2 -> downloadAllModels(onComplete)  // All models (recommended)
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                onComplete()
            }
            .show()
    }

    /**
     * Download all necessary models (Memory Model + Qwen2-VL)
     * Downloads in optimal order for best user experience
     */
    private fun downloadAllModels(onComplete: () -> Unit) {
        Log.i(TAG, "Starting download of all necessary models")
        Toast.makeText(activity, "Downloading necessary models (~1.7GB)...", Toast.LENGTH_SHORT).show()

        isProcessingDownload = false  // Reset state
        var currentModelName = ""
        var currentModelNum = 0
        var totalModels = 2
        var overallPercent = 0

        modelDownloadManager.downloadAllModels(
            onProgress = { modelName, modelNum, total, percent ->
                activity.runOnUiThread {
                    currentModelName = modelName
                    currentModelNum = modelNum
                    totalModels = total
                    overallPercent = percent
                    updateMultiModelDownloadProgress(modelName, modelNum, total, percent)
                }
            },
            onComplete = { success, errorMessage ->
                activity.runOnUiThread {
                    // Dismiss progress dialog when callback is invoked
                    downloadDialog?.dismiss()
                    progressHandler?.removeCallbacksAndMessages(null)
                    isProcessingDownload = false

                    if (success) {
                        Log.i(TAG, "All models downloaded successfully")
                        Toast.makeText(activity, "All models downloaded successfully! AILive is ready.", Toast.LENGTH_SHORT).show()
                        markSetupComplete()
                        onComplete()
                    } else {
                        Log.e(TAG, "Model download failed: $errorMessage")
                        Toast.makeText(activity, "Download failed: $errorMessage", Toast.LENGTH_LONG).show()
                        // Allow user to try again
                        showFirstRunDialog(onComplete)
                    }
                }
            }
        )

        // Show multi-model progress dialog
        showMultiModelDownloadProgressDialog()
    }

    /**
     * Download Memory Model only (TinyLlama-1.1B)
     */
    private fun downloadMemoryModelOnly(onComplete: () -> Unit) {
        Log.i(TAG, "Starting Memory Model download")
        Toast.makeText(activity, "Downloading Memory Model...", Toast.LENGTH_SHORT).show()

        isProcessingDownload = false  // Reset state

        modelDownloadManager.downloadMemoryModel(
            onProgress = { fileName, fileNum, total ->
                activity.runOnUiThread {
                    updateBatchDownloadProgress(fileName, fileNum, total, "Memory Model")
                }
            },
            onComplete = { success, errorMessage ->
                activity.runOnUiThread {
                    downloadDialog?.dismiss()
                    progressHandler?.removeCallbacksAndMessages(null)
                    isProcessingDownload = false

                    if (success) {
                        Log.i(TAG, "Memory Model download complete")
                        Toast.makeText(activity, "Memory Model downloaded successfully!", Toast.LENGTH_SHORT).show()
                        markSetupComplete()
                        onComplete()
                    } else {
                        Log.e(TAG, "Memory Model download failed: $errorMessage")
                        Toast.makeText(activity, "Download failed: $errorMessage", Toast.LENGTH_LONG).show()
                        showFirstRunDialog(onComplete)
                    }
                }
            }
        )

        // Show progress dialog
        showBatchDownloadProgressDialog("Memory Model (TinyLlama-1.1B)")
    }

    /**
     * Download Qwen2-VL GGUF model with progress tracking
     */
    private fun downloadQwenVLModel(onComplete: () -> Unit) {
        Log.i(TAG, "Starting Qwen2-VL GGUF download")
        Toast.makeText(activity, "Downloading Qwen2-VL model...", Toast.LENGTH_SHORT).show()

        isProcessingDownload = false  // Reset state

        modelDownloadManager.downloadQwenVLModel(
            onProgress = { fileName, fileNum, total ->
                activity.runOnUiThread {
                    updateBatchDownloadProgress(fileName, fileNum, total, "Qwen2-VL")
                }
            },
            onComplete = { success, errorMessage ->
                activity.runOnUiThread {
                    // Dismiss progress dialog when callback is invoked
                    downloadDialog?.dismiss()
                    progressHandler?.removeCallbacksAndMessages(null)
                    isProcessingDownload = false

                    if (success) {
                        Log.i(TAG, "Qwen2-VL GGUF download complete")
                        Toast.makeText(activity, "Model downloaded successfully!", Toast.LENGTH_SHORT).show()
                        markSetupComplete()
                        onComplete()
                    } else {
                        Log.e(TAG, "Qwen2-VL download failed: $errorMessage")
                        Toast.makeText(activity, "Download failed: $errorMessage", Toast.LENGTH_LONG).show()
                        // Allow user to try again
                        showFirstRunDialog(onComplete)
                    }
                }
            }
        )

        // Show progress dialog
        showBatchDownloadProgressDialog("Qwen2-VL")
    }

    /**
     * Show batch download progress dialog (for multiple files)
     */
    private fun showBatchDownloadProgressDialog(modelTitle: String = "Qwen2-VL") {
        val message = "Downloading $modelTitle...\n\nThis may take several minutes depending on your connection.\n\nPlease wait..."

        downloadDialog = AlertDialog.Builder(activity)
            .setTitle("Downloading $modelTitle")
            .setMessage(message)
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ ->
                modelDownloadManager.cancelDownload()
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
                "Model 1/2: Memory Model (TinyLlama-1.1B, ~700MB)\n" +
                "Model 2/2: Main AI (Qwen2-VL-2B, ~986MB)\n\n" +
                "Total: ~1.7GB\n\n" +
                "This may take 10-20 minutes depending on your connection.\n\n" +
                "Please keep the app open while downloading..."

        downloadDialog = AlertDialog.Builder(activity)
            .setTitle("Downloading Necessary Models")
            .setMessage(message)
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ ->
                modelDownloadManager.cancelDownload()
            }
            .create()

        downloadDialog?.show()

        // Update progress every second
        progressHandler = Handler(Looper.getMainLooper())
        updateMultiModelDownloadProgress("", 0, 2, 0)
    }

    /**
     * Update multi-model download progress
     */
    private fun updateMultiModelDownloadProgress(modelName: String, modelNum: Int, totalModels: Int, overallPercent: Int) {
        val progress = modelDownloadManager.getDownloadProgress()

        val message = if (progress != null) {
            val (downloaded, total) = progress
            val percent = if (total > 0) {
                ((downloaded.toDouble() / total) * 100).toInt()
            } else 0

            val downloadedMB = downloaded / 1024 / 1024
            val totalMB = total / 1024 / 1024

            val modelInfo = when (modelNum) {
                1 -> "Memory Model (TinyLlama-1.1B)"
                2 -> "Main AI (Qwen2-VL-2B)"
                else -> "Model"
            }

            "Downloading Model $modelNum/$totalModels:\n" +
                    "$modelInfo\n\n" +
                    "$downloadedMB MB / $totalMB MB ($percent%)\n\n" +
                    "Overall Progress: $overallPercent%\n\n" +
                    "Please wait..."
        } else {
            "Preparing to download models...\n\nModel $modelNum/$totalModels\n\nPlease wait..."
        }

        downloadDialog?.setMessage(message)

        // Schedule next update in 1 second
        progressHandler?.postDelayed({
            updateMultiModelDownloadProgress(modelName, modelNum, totalModels, overallPercent)
        }, 1000)
    }

    /**
     * Update batch download progress (shows which file is downloading)
     */
    private fun updateBatchDownloadProgress(fileName: String, fileNum: Int, totalFiles: Int, modelTitle: String = "Model") {
        val progress = modelDownloadManager.getDownloadProgress()

        val message = if (progress != null && fileName.isNotEmpty()) {
            val (downloaded, total) = progress
            val percent = if (total > 0) {
                ((downloaded.toDouble() / total) * 100).toInt()
            } else 0

            val downloadedMB = downloaded / 1024 / 1024
            val totalMB = total / 1024 / 1024

            "Downloading file $fileNum/$totalFiles:\n$fileName\n\n" +
                    "$downloadedMB MB / $totalMB MB ($percent%)\n\n" +
                    "Please wait..."
        } else {
            "Downloading $modelTitle...\n\nFile $fileNum/$totalFiles\n\nPlease wait..."
        }

        downloadDialog?.setMessage(message)

        // Schedule next update in 1 second
        progressHandler?.postDelayed({
            updateBatchDownloadProgress(fileName, fileNum, totalFiles, modelTitle)
        }, 1000)
    }

    /**
     * Start model download with progress tracking
     */
    private fun downloadModel(url: String, modelName: String, onComplete: () -> Unit) {
        Log.i(TAG, "Starting download: $modelName")
        Toast.makeText(activity, "Downloading $modelName...", Toast.LENGTH_SHORT).show()

        isProcessingDownload = false  // Reset state

        modelDownloadManager.downloadModel(url, modelName) { success, errorMessage ->
            activity.runOnUiThread {
                // Dismiss progress dialog when callback is invoked
                downloadDialog?.dismiss()
                progressHandler?.removeCallbacksAndMessages(null)
                isProcessingDownload = false

                if (success) {
                    Log.i(TAG, "Download complete: $modelName")
                    Toast.makeText(activity, "Model downloaded successfully!", Toast.LENGTH_SHORT).show()
                    markSetupComplete()
                    onComplete()
                } else {
                    Log.e(TAG, "Download failed: $errorMessage")
                    Toast.makeText(activity, "Download failed: $errorMessage", Toast.LENGTH_LONG).show()
                    // Allow user to try again
                    showFirstRunDialog(onComplete)
                }
            }
        }

        // Show progress dialog
        showDownloadProgressDialog(modelName)
    }

    /**
     * Show download progress dialog (updates every second)
     */
    private fun showDownloadProgressDialog(modelName: String) {
        val dialogView = LayoutInflater.from(activity).inflate(
            android.R.layout.select_dialog_item, // Use simple Android layout
            null
        )

        // Create progress message
        val message = "Downloading $modelName...\n\nThis may take a few minutes depending on your connection."

        downloadDialog = AlertDialog.Builder(activity)
            .setTitle("Downloading Model")
            .setMessage(message)
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ ->
                modelDownloadManager.cancelDownload()
            }
            .create()

        downloadDialog?.show()

        // Update progress every second
        progressHandler = Handler(Looper.getMainLooper())
        updateDownloadProgress()
    }

    /**
     * Update download progress (called every second)
     */
    private fun updateDownloadProgress() {
        val progress = modelDownloadManager.getDownloadProgress()

        if (progress != null) {
            val (downloaded, total) = progress
            val percent = if (total > 0) {
                ((downloaded.toDouble() / total) * 100).toInt()
            } else 0

            val downloadedMB = downloaded / 1024 / 1024
            val totalMB = total / 1024 / 1024

            val message = "Downloading...\n\n" +
                    "$downloadedMB MB / $totalMB MB ($percent%)\n\n" +
                    "This may take a few minutes."

            downloadDialog?.setMessage(message)

            // Check if we hit 100% - next update will be processing phase
            if (percent >= 100 && !isProcessingDownload) {
                Log.i(TAG, "📥 Download reached 100% - triggering manual completion check")
                isProcessingDownload = true
                // Manually trigger completion check after short delay
                progressHandler?.postDelayed({
                    Log.i(TAG, "📥 Manual completion check - calling manualCheckDownloadComplete")
                    modelDownloadManager.manualCheckDownloadComplete()
                }, 2000)  // Wait 2 seconds for file to finish writing
            }

            // Continue updating
            progressHandler?.postDelayed({ updateDownloadProgress() }, 1000)
        } else {
            // Download finished downloading, but may still be processing (moving file)
            if (isProcessingDownload) {
                // Show processing message and keep dialog open
                val message = "Processing...\n\n" +
                        "Moving model to app storage.\n\n" +
                        "This may take a moment for large models."

                downloadDialog?.setMessage(message)

                // Continue checking (dialog will be dismissed by completion callback)
                progressHandler?.postDelayed({ updateDownloadProgress() }, 1000)
            } else {
                // Download was cancelled by user
                downloadDialog?.dismiss()
                progressHandler?.removeCallbacksAndMessages(null)
            }
        }
    }

    /**
     * Show file picker to import model from device
     * BUGFIX Phase 7.5: Use ActivityResultLauncher instead of deprecated startActivityForResult
     * BUGFIX Phase 7.6: Store onComplete callback so it can be invoked after import
     * Phase 9.0: GGUF/ONNX support
     */
    private fun showFilePickerDialog(onComplete: () -> Unit) {
        Toast.makeText(
            activity,
            "Select a model file (.gguf, .onnx, .bin)",
            Toast.LENGTH_SHORT
        ).show()

        // Store the completion callback for use after import
        pendingImportCallback = { success, message ->
            if (success) {
                Log.i(TAG, "Import successful: $message")
                markSetupComplete()
                onComplete()
            } else {
                Log.e(TAG, "Import failed: $message")
                // Allow retry
                showFirstRunDialog(onComplete)
            }
        }

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"  // All files
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/octet-stream",  // .gguf, .onnx
                "*/*"  // Fallback
            ))
        }

        try {
            // Use modern ActivityResultLauncher (no lifecycle registration issues)
            filePickerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open file picker", e)
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
     * Handle file picker result (called from activity's onActivityResult)
     * BUGFIX Phase 7.6: Use stored callback instead of requiring onComplete parameter
     */
    fun handleFilePickerResult(uri: Uri, onComplete: () -> Unit) {
        Log.i(TAG, "Importing model from: $uri")
        Toast.makeText(activity, "Importing model...", Toast.LENGTH_SHORT).show()

        // Use stored callback if available, otherwise fall back to parameter
        val callback = pendingImportCallback ?: { success, result ->
            if (success) {
                Log.i(TAG, "Import complete: $result")
                Toast.makeText(
                    activity,
                    "Model imported successfully: $result",
                    Toast.LENGTH_SHORT
                ).show()
                markSetupComplete()
                onComplete()
            } else {
                Log.e(TAG, "Import failed: $result")
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
                pendingImportCallback = null  // Clear after use
            }
        }
    }

    /**
     * Show model management dialog (for settings)
     */
    fun showModelManagementDialog() {
        val availableModels = modelDownloadManager.getAvailableModels()

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
                        // TODO: Switch to this model
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
        val models = modelDownloadManager.getAvailableModels()
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
        Log.i(TAG, "Model setup marked complete")
    }

    /**
     * Cleanup
     */
    fun cleanup() {
        downloadDialog?.dismiss()
        progressHandler?.removeCallbacksAndMessages(null)
    }
}
