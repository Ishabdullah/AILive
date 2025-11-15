package com.ailive.ai.vision

import android.graphics.Bitmap
import android.util.Log
import com.ailive.ai.llm.LLMBridge
import java.io.ByteArrayOutputStream

/**
 * Manages multimodal vision capabilities, integrating with the LLMBridge
 * to send image data and text prompts to the LLaVA model.
 */
class VisionManager(private val llmBridge: LLMBridge) {
    private val TAG = "VisionManager"

    /**
     * Generates a text response from the LLaVA model given an image and a text prompt.
     *
     * @param image The Bitmap image to analyze.
     * @param prompt The text prompt to accompany the image.
     * @param maxTokens The maximum number of tokens to generate in the response.
     * @return The generated text response, or an error message if generation fails.
     */
    fun generateResponseWithImage(image: Bitmap, prompt: String, maxTokens: Int = 256): String {
        if (!llmBridge.isReady()) {
            Log.e(TAG, "LLMBridge is not ready. Model not loaded.")
            return "[ERROR: LLM model not loaded for vision]"
        }

        // Convert Bitmap to JPEG byte array
        val outputStream = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, 80, outputStream) // Compress to JPEG with 80% quality
        val imageBytes = outputStream.toByteArray()

        if (imageBytes.isEmpty()) {
            Log.e(TAG, "Failed to convert bitmap to byte array.")
            return "[ERROR: Image conversion failed]"
        }

        Log.i(TAG, "Sending image (${imageBytes.size} bytes) and prompt to LLaVA model.")
        return llmBridge.nativeGenerateWithImage(prompt, imageBytes, maxTokens)
    }
}
