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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import com.ailive.R
import com.ailive.ai.llm.ModelDownloadManager
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
    private var downloadDialog: AlertDialog? = null
    private var progressHandler: Handler? = null
    private var isProcessingDownload = false  // Track if we're in file-move phase
    private var pendingImportCallback: ((Boolean, String) -> Unit)? = null  // Store import callback

    /**
     * Check if model setup is needed (first run without model)
     */
    fun isSetupNeeded(): Boolean {
        val setupDone = prefs.getBoolean(KEY_MODEL_SETUP_DONE, false)
        val modelAvailable = modelDownloadManager.isModelAvailable()
        return !setupDone || !modelAvailable
    }

    /**
     * Show first-run setup dialog (like Layla's welcome screen)
     */
    fun showFirstRunDialog(onComplete: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Welcome to AILive!")
            .setMessage(
                "To get started, AILive needs an AI model for on-device intelligence.\n\n" +
                "You can:\n" +
                "• Download SmolLM2-360M GGUF (~180MB, recommended)\n" +
                "• Import a GGUF model from your device\n" +
                "• Download a smaller GGUF model for testing\n\n" +
                "GGUF models are smaller and faster than ONNX.\n" +
                "All models run 100% on your device - no internet needed after download."
            )
            .setPositiveButton("Download Model") { _, _ ->
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
     * Show model selection dialog with recommendations
     */
    private fun showModelSelectionDialog(onComplete: () -> Unit) {
        val models = arrayOf(
            "SmolLM2-360M GGUF Q4 (~180MB) - Recommended",
            "SmolLM2-135M GGUF Q4 (~70MB) - Smaller/Faster",
            "SmolLM2-360M ONNX INT8 (~348MB) - Legacy"
        )

        // BUGFIX: Don't use .setMessage() with .setItems() - causes items to not display
        AlertDialog.Builder(activity)
            .setTitle("Select AI Model to Download")
            .setItems(models) { _, which ->
                when (which) {
                    0 -> downloadModel(
                        ModelDownloadManager.DEFAULT_MODEL_URL,
                        ModelDownloadManager.DEFAULT_MODEL_NAME,
                        onComplete
                    )
                    1 -> downloadModel(
                        ModelDownloadManager.ALT_MODEL_URL,
                        ModelDownloadManager.ALT_MODEL_NAME,
                        onComplete
                    )
                    2 -> downloadModel(
                        ModelDownloadManager.ONNX_360M_URL,
                        ModelDownloadManager.ONNX_360M_NAME,
                        onComplete
                    )
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                onComplete()
            }
            .show()
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
            if (percent >= 100) {
                isProcessingDownload = true
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
     */
    private fun showFilePickerDialog(onComplete: () -> Unit) {
        Toast.makeText(
            activity,
            "Select a .gguf model file (or .onnx for legacy support)",
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
            .setPositiveButton("Download New Model") { _, _ ->
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
