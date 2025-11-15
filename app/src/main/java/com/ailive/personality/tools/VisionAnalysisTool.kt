package com.ailive.personality.tools

import android.graphics.Bitmap
import android.util.Log
import com.ailive.camera.CameraManager
import com.ailive.ai.vision.VisionManager

/**
 * VisionAnalysisTool - Analyzes camera vision using multimodal LLaVA models.
 *
 * Provides real-time visual context understanding by sending camera frames
 * and text prompts to a LLaVA-enabled LLM.
 */
class VisionAnalysisTool(
    private val visionManager: VisionManager,
    private val cameraManager: CameraManager
) : BaseTool() {

    companion object {
        private const val TAG = "VisionAnalysisTool"
    }

    override val name: String = "analyze_vision"

    override val description: String =
        "Analyzes what the camera sees using multimodal AI. " +
        "Provides detailed descriptions and answers questions about visual context."

    override val requiresPermissions: Boolean = true

    override suspend fun isAvailable(): Boolean {
        // Check if camera is initialized and the LLM for vision is ready
        return cameraManager.isInitialized() && visionManager.llmBridge.isReady()
    }

    override fun validateParams(params: Map<String, Any>): String? {
        // Optional: Can add parameters for specific questions about the image
        return null
    }

    override suspend fun executeInternal(params: Map<String, Any>): ToolResult {
        try {
            Log.d(TAG, "Analyzing vision from camera with LLaVA...")

            // Get latest frame from camera
            val latestFrame = cameraManager.getLatestFrame()

            if (latestFrame == null) {
                return ToolResult.Unavailable(
                    reason = "No camera frame available. Camera may be off or not capturing. Enable camera to use vision capabilities."
                )
            }

            // Formulate a default prompt for the LLaVA model
            val visionPrompt = params["prompt"] as? String ?: "What do you see in this image?"

            val startTime = System.currentTimeMillis()
            val llavaResponse = visionManager.generateResponseWithImage(latestFrame, visionPrompt)
            val duration = System.currentTimeMillis() - startTime

            latestFrame.recycle() // Recycle the bitmap after use

            if (llavaResponse.isBlank() || llavaResponse.contains("[ERROR")) {
                return ToolResult.Failure(
                    error = Exception("LLaVA inference failed"),
                    reason = "Failed to analyze image with LLaVA: $llavaResponse",
                    recoverable = true
                )
            }

            Log.i(TAG, "✓ LLaVA analysis complete in ${duration}ms. Response: ${llavaResponse.take(100)}...")

            return ToolResult.Success(
                data = VisionAnalysisResult(
                    response = llavaResponse,
                    inferenceTimeMs = duration
                ),
                context = mapOf(
                    "vision_available" to true,
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
     * Result of vision analysis
     */
    data class VisionAnalysisResult(
        val response: String,
        val inferenceTimeMs: Long
    )
}
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
