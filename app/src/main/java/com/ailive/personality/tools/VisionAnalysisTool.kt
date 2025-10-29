package com.ailive.personality.tools

import android.graphics.Bitmap
import android.util.Log
import com.ailive.ai.models.ClassificationResult
import com.ailive.ai.models.ModelManager
import com.ailive.camera.CameraManager

/**
 * VisionAnalysisTool - Analyzes camera vision using computer vision models
 *
 * Converted from MotorAI's vision capabilities to a tool for PersonalityEngine.
 * Provides real-time object detection without separate personality.
 *
 * Phase 5: Tool Expansion
 */
class VisionAnalysisTool(
    private val modelManager: ModelManager,
    private val cameraManager: CameraManager
) : BaseTool() {

    companion object {
        private const val TAG = "VisionAnalysisTool"
    }

    override val name: String = "analyze_vision"

    override val description: String =
        "Analyzes what the camera sees using computer vision. " +
        "Detects objects, scenes, and provides visual context understanding."

    override val requiresPermissions: Boolean = true

    override suspend fun isAvailable(): Boolean {
        // Check if camera is initialized and model is ready
        return cameraManager.isInitialized() && modelManager != null
    }

    override fun validateParams(params: Map<String, Any>): String? {
        // No required parameters - uses latest camera frame
        return null
    }

    override suspend fun executeInternal(params: Map<String, Any>): ToolResult {
        try {
            Log.d(TAG, "Analyzing vision from camera...")

            // Get latest frame from camera
            val latestFrame = cameraManager.getLatestFrame()

            if (latestFrame == null) {
                return ToolResult.Unavailable(
                    reason = "No camera frame available. Camera may be off or not capturing.",
                    suggestion = "Enable camera to use vision capabilities"
                )
            }

            // Classify the image
            val startTime = System.currentTimeMillis()
            val classification = modelManager.classifyImage(latestFrame)
            val duration = System.currentTimeMillis() - startTime

            if (classification == null) {
                return ToolResult.Failure(
                    error = Exception("Model inference failed"),
                    reason = "Failed to analyze image",
                    recoverable = true
                )
            }

            Log.i(TAG, "✓ Vision analysis complete: ${classification.topLabel} " +
                    "(${(classification.confidence * 100).toInt()}%) in ${duration}ms")

            // Build natural language description
            val description = buildVisionDescription(classification)

            return ToolResult.Success(
                data = VisionAnalysisResult(
                    primaryObject = classification.topLabel,
                    confidence = classification.confidence,
                    allDetections = classification.allResults.take(3).map {
                        Detection(it.first, it.second)
                    },
                    description = description,
                    inferenceTimeMs = classification.inferenceTimeMs
                ),
                context = mapOf(
                    "vision_available" to true,
                    "model_confidence" to classification.confidence,
                    "processing_time_ms" to duration
                )
            )

        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission denied", e)
            return ToolResult.Blocked(
                reason = "Camera permission not granted",
                requiredAction = "Grant camera permission to use vision capabilities"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Vision analysis failed", e)
            return ToolResult.Failure(
                error = e,
                reason = "Failed to analyze vision: ${e.message}",
                recoverable = true
            )
        }
    }

    /**
     * Build natural language description from classification results
     */
    private fun buildVisionDescription(result: ClassificationResult): String {
        val topLabel = result.topLabel
        val confidence = (result.confidence * 100).toInt()

        // Get secondary detections
        val secondaryObjects = result.allResults
            .drop(1)
            .take(2)
            .filter { it.second > 0.1f }  // Filter low confidence
            .map { it.first }

        return when {
            confidence >= 70 && secondaryObjects.isNotEmpty() -> {
                "I can see a $topLabel ($confidence% confident). " +
                "I also notice: ${secondaryObjects.joinToString(", ")}."
            }
            confidence >= 70 -> {
                "I can see a $topLabel with $confidence% confidence."
            }
            confidence >= 40 -> {
                "I see what might be a $topLabel (about $confidence% confident)."
            }
            else -> {
                "I'm looking, but the scene is unclear. " +
                "Possibly a $topLabel, but I'm not very confident."
            }
        }
    }

    /**
     * Result of vision analysis
     */
    data class VisionAnalysisResult(
        val primaryObject: String,
        val confidence: Float,
        val allDetections: List<Detection>,
        val description: String,
        val inferenceTimeMs: Long
    )

    /**
     * Individual detection result
     */
    data class Detection(
        val label: String,
        val confidence: Float
    )
}
