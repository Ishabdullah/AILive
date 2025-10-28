package com.ailive.ai.models

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Manages TensorFlow Lite models for AILive
 * Handles image classification using MobileNetV2
 */
class ModelManager(private val context: Context) {
    private val TAG = "ModelManager"
    
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    
    companion object {
        private const val MODEL_PATH = "models/mobilenet_v2.tflite"
        private const val LABELS_PATH = "models/labels.txt"
        private const val INPUT_SIZE = 224
        private const val PIXEL_SIZE = 3
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 127.5f
    }
    
    private val labels: List<String> by lazy {
        try {
            context.assets.open(LABELS_PATH).bufferedReader().readLines()
        } catch (e: Exception) {
            Log.w(TAG, "Labels file not found, using defaults")
            listOf("Unknown")
        }
    }
    
    /**
     * Initialize the TensorFlow Lite interpreter
     */
    fun initialize() {
        try {
            val options = Interpreter.Options()
            
            // Try to use GPU acceleration with default settings
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                // Use default GPU delegate (no custom options to avoid API issues)
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
                Log.i(TAG, "✓ GPU acceleration enabled (Adreno)")
            } else {
                options.setNumThreads(4)
                Log.i(TAG, "Using CPU with 4 threads")
            }
            
            val model = loadModelFile()
            interpreter = Interpreter(model, options)
            
            Log.i(TAG, "✓ TensorFlow Lite initialized successfully")
            Log.i(TAG, "  Model: MobileNetV2")
            Log.i(TAG, "  Labels: ${labels.size} classes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TensorFlow Lite", e)
            // Fallback to CPU if GPU fails
            try {
                Log.w(TAG, "Attempting CPU fallback...")
                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                }
                val model = loadModelFile()
                interpreter = Interpreter(model, options)
                Log.i(TAG, "✓ TensorFlow Lite initialized (CPU mode)")
            } catch (fallbackError: Exception) {
                Log.e(TAG, "CPU fallback also failed", fallbackError)
            }
        }
    }
    
    /**
     * Load model file from assets
     */
    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_PATH)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    /**
     * Classify an image
     */
    fun classifyImage(bitmap: Bitmap): ClassificationResult? {
        if (interpreter == null) {
            Log.w(TAG, "Interpreter not initialized")
            return null
        }
        
        try {
            val startTime = System.currentTimeMillis()
            
            val inputBuffer = preprocessImage(bitmap)
            val outputArray = Array(1) { FloatArray(labels.size) }
            
            interpreter?.run(inputBuffer, outputArray)
            
            val results = outputArray[0].mapIndexed { index, confidence ->
                val label = if (index < labels.size) labels[index] else "Unknown"
                Pair(label, confidence)
            }.sortedByDescending { it.second }.take(5)
            
            val inferenceTime = System.currentTimeMillis() - startTime
            
            Log.d(TAG, "Classification complete in ${inferenceTime}ms")
            Log.d(TAG, "Top result: ${results[0].first} (${(results[0].second * 100).toInt()}%)")
            
            return ClassificationResult(
                topLabel = results[0].first,
                confidence = results[0].second,
                allResults = results,
                inferenceTimeMs = inferenceTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "Classification failed", e)
            return null
        }
    }
    
    /**
     * Preprocess image for model input
     */
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val imgData = ByteBuffer.allocateDirect(
            4 * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE
        )
        imgData.order(ByteOrder.nativeOrder())
        
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaledBitmap.getPixels(intValues, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)
        
        var pixel = 0
        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until INPUT_SIZE) {
                val value = intValues[pixel++]
                
                // Normalize pixel values to [-1, 1]
                imgData.putFloat(((value shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                imgData.putFloat(((value shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                imgData.putFloat(((value and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
            }
        }
        
        return imgData
    }
    
    /**
     * Get model info
     */
    fun getModelInfo(): String {
        return """
            Model: MobileNetV2
            Input size: ${INPUT_SIZE}x${INPUT_SIZE}
            Classes: ${labels.size}
            GPU: ${if (gpuDelegate != null) "Enabled" else "Disabled"}
        """.trimIndent()
    }
    
    /**
     * Release resources
     */
    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
        Log.i(TAG, "ModelManager closed")
    }
}

/**
 * Classification result data class
 */
data class ClassificationResult(
    val topLabel: String,
    val confidence: Float,
    val allResults: List<Pair<String, Float>>,
    val inferenceTimeMs: Long
)
