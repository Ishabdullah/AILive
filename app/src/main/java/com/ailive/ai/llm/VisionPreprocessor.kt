package com.ailive.ai.llm

import android.graphics.Bitmap
import android.util.Log
import java.nio.FloatBuffer

/**
 * Vision preprocessing utilities for Qwen2-VL
 *
 * Handles image preprocessing for vision encoder:
 * - Resize to 224x224
 * - RGB normalization
 * - Tensor conversion for ONNX input
 *
 * @author AILive Team
 * @since Phase 8.0
 */
object VisionPreprocessor {

    private const val TAG = "VisionPreprocessor"

    // Qwen2-VL vision encoder expects 224x224 images
    private const val TARGET_SIZE = 224

    // ImageNet normalization parameters (standard for vision models)
    private val MEAN = floatArrayOf(0.485f, 0.485f, 0.456f)  // R, G, B
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)   // R, G, B

    /**
     * Preprocess Bitmap for Qwen2-VL vision encoder
     *
     * Steps:
     * 1. Resize to 224x224
     * 2. Normalize RGB values using ImageNet stats
     * 3. Convert to NCHW format (batch, channels, height, width)
     *
     * @param image Input bitmap (any size)
     * @return Float buffer in NCHW format, ready for ONNX input
     */
    fun preprocessImage(image: Bitmap): FloatBuffer {
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "🖼️  Preprocessing image: ${image.width}x${image.height}")

        // Step 1: Resize to 224x224
        val resized = resizeImage(image, TARGET_SIZE, TARGET_SIZE)
        Log.d(TAG, "   ✓ Resized to ${resized.width}x${resized.height}")

        // Step 2: Extract RGB pixels
        val pixels = IntArray(TARGET_SIZE * TARGET_SIZE)
        resized.getPixels(pixels, 0, TARGET_SIZE, 0, 0, TARGET_SIZE, TARGET_SIZE)

        // Step 3: Normalize and convert to NCHW format
        val bufferSize = 3 * TARGET_SIZE * TARGET_SIZE  // 3 channels (RGB)
        val buffer = FloatBuffer.allocate(bufferSize)

        // NCHW format: [batch=1, channels=3, height=224, width=224]
        // Layout: all R values, then all G values, then all B values
        for (c in 0 until 3) {  // Channels: R=0, G=1, B=2
            for (y in 0 until TARGET_SIZE) {
                for (x in 0 until TARGET_SIZE) {
                    val pixel = pixels[y * TARGET_SIZE + x]

                    // Extract RGB components (Android uses ARGB format)
                    val value = when (c) {
                        0 -> ((pixel shr 16) and 0xFF)  // Red
                        1 -> ((pixel shr 8) and 0xFF)   // Green
                        2 -> (pixel and 0xFF)           // Blue
                        else -> 0
                    }

                    // Normalize: (value / 255.0 - mean) / std
                    val normalized = ((value / 255.0f) - MEAN[c]) / STD[c]
                    buffer.put(normalized)
                }
            }
        }

        buffer.rewind()

        val preprocessTime = (System.currentTimeMillis() - startTime) / 1000.0
        Log.i(TAG, "✅ Image preprocessed in ${preprocessTime}s")
        Log.d(TAG, "   Output shape: [1, 3, $TARGET_SIZE, $TARGET_SIZE]")
        Log.d(TAG, "   Buffer size: $bufferSize floats")

        return buffer
    }

    /**
     * Resize bitmap to target dimensions
     *
     * @param image Source bitmap
     * @param targetWidth Target width
     * @param targetHeight Target height
     * @return Resized bitmap
     */
    private fun resizeImage(image: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return if (image.width == targetWidth && image.height == targetHeight) {
            // Already correct size
            image
        } else {
            // Resize using bilinear filtering (good quality, fast)
            Bitmap.createScaledBitmap(image, targetWidth, targetHeight, true)
        }
    }

    /**
     * Get expected input shape for vision encoder
     * Format: [batch_size, channels, height, width]
     */
    fun getInputShape(): LongArray {
        return longArrayOf(1, 3, TARGET_SIZE.toLong(), TARGET_SIZE.toLong())
    }

    /**
     * Validate that an image is suitable for preprocessing
     *
     * @param image Bitmap to validate
     * @return true if valid, false otherwise
     */
    fun validateImage(image: Bitmap): Boolean {
        if (image.width <= 0 || image.height <= 0) {
            Log.e(TAG, "❌ Invalid image dimensions: ${image.width}x${image.height}")
            return false
        }

        if (image.config != Bitmap.Config.ARGB_8888 && image.config != Bitmap.Config.RGB_565) {
            Log.w(TAG, "⚠️ Unusual bitmap config: ${image.config}. May cause issues.")
        }

        Log.d(TAG, "✓ Image validation passed: ${image.width}x${image.height}, ${image.config}")
        return true
    }
}
