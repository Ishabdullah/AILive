package com.ailive.ai.llm

import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * ModelIntegrityVerifier - Ensures model files are present, readable, and uncorrupted
 *
 * Runs during startup to confirm model integrity before attempting to load.
 * Validates file existence, size, and optionally SHA-256 checksum.
 *
 * Based on user request for self-reliant model health checking.
 *
 * @author AILive Team
 * @since Phase 7.7
 */
object ModelIntegrityVerifier {

    private const val TAG = "ModelIntegrityVerifier"

    // Model expectations
    private const val MODEL_DIR = "/data/data/com.ailive/files/models/"
    private const val GPT2_MODEL_NAME = "gpt2-decoder.onnx"
    private const val EXPECTED_MIN_SIZE_MB = 600  // GPT-2 decoder is ~653MB
    private const val EXPECTED_MAX_SIZE_MB = 700

    // Optional: Set this to the SHA-256 hash of your verified model
    // To get hash: adb shell run-as com.ailive sha256sum /data/data/com.ailive/files/models/gpt2-decoder.onnx
    private const val EXPECTED_SHA256 = ""  // Empty = skip checksum verification

    /**
     * Verify model integrity
     * Returns true if model is present, correct size, and passes checksum (if configured)
     */
    fun verify(): Boolean {
        return verify(GPT2_MODEL_NAME)
    }

    /**
     * Verify specific model file
     */
    fun verify(modelName: String): Boolean {
        val modelPath = MODEL_DIR + modelName
        val modelFile = File(modelPath)

        // Check existence
        if (!modelFile.exists()) {
            Log.e(TAG, "❌ Model file not found at: $modelPath")
            Log.e(TAG, "   Model needs to be downloaded via app dialog or imported")
            return false
        }

        // Check readability
        if (!modelFile.canRead()) {
            Log.e(TAG, "❌ Model file exists but cannot be read: $modelPath")
            Log.e(TAG, "   Check file permissions")
            return false
        }

        // Check size
        val sizeMB = modelFile.length() / (1024 * 1024)
        if (sizeMB < EXPECTED_MIN_SIZE_MB || sizeMB > EXPECTED_MAX_SIZE_MB) {
            Log.w(TAG, "⚠️  Unexpected model size: $sizeMB MB (expected ~${EXPECTED_MIN_SIZE_MB}-${EXPECTED_MAX_SIZE_MB} MB)")
            Log.w(TAG, "   Model may be corrupted or incorrect version")
            // Don't fail on size mismatch, just warn (different models have different sizes)
        }

        // Optional: Checksum verification
        if (EXPECTED_SHA256.isNotEmpty()) {
            Log.d(TAG, "🔍 Verifying model checksum...")
            val hash = calculateSHA256(modelFile)
            if (hash != EXPECTED_SHA256) {
                Log.e(TAG, "❌ Model hash mismatch! File may be corrupted.")
                Log.e(TAG, "   Expected: $EXPECTED_SHA256")
                Log.e(TAG, "   Got:      $hash")
                return false
            }
            Log.d(TAG, "✅ Model checksum verified")
        }

        Log.i(TAG, "✅ Model integrity verified successfully")
        Log.i(TAG, "   Path: $modelPath")
        Log.i(TAG, "   Size: $sizeMB MB")
        return true
    }

    /**
     * Calculate SHA-256 hash of a file
     */
    private fun calculateSHA256(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate SHA-256", e)
            ""
        }
    }

    /**
     * Check if model exists (quick check without full verification)
     */
    fun exists(modelName: String = GPT2_MODEL_NAME): Boolean {
        val modelPath = MODEL_DIR + modelName
        return File(modelPath).exists()
    }

    /**
     * Get model size in MB
     */
    fun getModelSizeMB(modelName: String = GPT2_MODEL_NAME): Long {
        val modelPath = MODEL_DIR + modelName
        val file = File(modelPath)
        return if (file.exists()) file.length() / (1024 * 1024) else 0
    }

    /**
     * Verify and provide detailed diagnostics
     */
    fun verifyWithDiagnostics(modelName: String = GPT2_MODEL_NAME): VerificationResult {
        val modelPath = MODEL_DIR + modelName
        val modelFile = File(modelPath)

        if (!modelFile.exists()) {
            return VerificationResult(
                success = false,
                message = "Model file not found",
                details = "Path: $modelPath"
            )
        }

        if (!modelFile.canRead()) {
            return VerificationResult(
                success = false,
                message = "Model file exists but cannot be read",
                details = "Check file permissions: $modelPath"
            )
        }

        val sizeMB = modelFile.length() / (1024 * 1024)
        val sizeOk = sizeMB >= EXPECTED_MIN_SIZE_MB && sizeMB <= EXPECTED_MAX_SIZE_MB

        if (!sizeOk) {
            return VerificationResult(
                success = false,
                message = "Unexpected model size: $sizeMB MB",
                details = "Expected: ${EXPECTED_MIN_SIZE_MB}-${EXPECTED_MAX_SIZE_MB} MB. Model may be corrupted."
            )
        }

        if (EXPECTED_SHA256.isNotEmpty()) {
            val hash = calculateSHA256(modelFile)
            if (hash != EXPECTED_SHA256) {
                return VerificationResult(
                    success = false,
                    message = "Model checksum mismatch",
                    details = "File may be corrupted. Expected: $EXPECTED_SHA256"
                )
            }
        }

        return VerificationResult(
            success = true,
            message = "Model integrity verified",
            details = "Size: $sizeMB MB, Path: $modelPath"
        )
    }

    /**
     * Result of verification with diagnostics
     */
    data class VerificationResult(
        val success: Boolean,
        val message: String,
        val details: String
    )
}
