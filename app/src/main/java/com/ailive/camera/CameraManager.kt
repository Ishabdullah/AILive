package com.ailive.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.ailive.ai.models.ModelManager
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor

/**
 * CameraManager - Optimized for Samsung S24 Ultra
 * Phase 2.2: Real-time vision with AI classification
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val modelManager: ModelManager
) {
    private val TAG = "CameraManager"

    private var cameraProvider: ProcessCameraProvider? = null
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    private val analysisScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var frameCount = 0
    var onClassificationResult: ((String, Float, Long) -> Unit)? = null

    // PHASE 5: Frame buffering for VisionAnalysisTool
    @Volatile
    private var latestFrame: Bitmap? = null
    private val frameLock = Any()
    
    /**
     * Start camera with S24 Ultra optimizations
     */
    fun startCamera(previewView: PreviewView) {
        Log.i(TAG, "=== Initializing Camera for S24 Ultra ===")
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                
                if (cameraProvider == null) {
                    Log.e(TAG, "❌ CameraProvider is NULL!")
                    return@addListener
                }
                
                Log.i(TAG, "✓ CameraProvider obtained")
                bindCameraUseCases(previewView)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to get CameraProvider", e)
            }
        }, mainExecutor)
    }
    
    /**
     * Bind camera use cases
     */
    private fun bindCameraUseCases(previewView: PreviewView) {
        val provider = cameraProvider ?: run {
            Log.e(TAG, "❌ Provider null in bindCameraUseCases")
            return
        }
        
        try {
            // Unbind first
            provider.unbindAll()
            Log.i(TAG, "Unbound all previous use cases")
            
            // Create Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            
            Log.i(TAG, "Preview created")
            
            // Create ImageAnalysis
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            
            Log.i(TAG, "ImageAnalysis created")
            
            // CRITICAL: Set analyzer with proper executor
            imageAnalysis.setAnalyzer(mainExecutor) { imageProxy ->
                frameCount++
                
                if (frameCount == 1) {
                    Log.i(TAG, "========================================")
                    Log.i(TAG, "🎉 FIRST FRAME RECEIVED!")
                    Log.i(TAG, "========================================")
                }
                
                if (frameCount % 30 == 0) {
                    Log.i(TAG, "📸 Frame #$frameCount - processing...")
                    processFrame(imageProxy)
                } else {
                    imageProxy.close()
                }
            }
            
            Log.i(TAG, "Analyzer set on mainExecutor")
            
            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            // Bind to lifecycle
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            
            Log.i(TAG, "========================================")
            Log.i(TAG, "✅ CAMERA BOUND SUCCESSFULLY!")
            Log.i(TAG, "Camera ID: ${camera.cameraInfo}")
            Log.i(TAG, "Waiting for analyzer callbacks...")
            Log.i(TAG, "========================================")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ BINDING FAILED!", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Process frame with TensorFlow Lite
     */
    @OptIn(ExperimentalGetImage::class)
    private fun processFrame(imageProxy: ImageProxy) {
        analysisScope.launch {
            try {
                val bitmap = convertToBitmap(imageProxy)

                if (bitmap != null) {
                    Log.d(TAG, "Bitmap: ${bitmap.width}x${bitmap.height}")

                    // PHASE 5: Store latest frame for VisionAnalysisTool
                    // Create a copy since we'll recycle the original
                    val frameCopy = bitmap.copy(bitmap.config, true)
                    synchronized(frameLock) {
                        latestFrame?.recycle()  // Recycle old frame
                        latestFrame = frameCopy
                    }

                    val result = modelManager.classifyImage(bitmap)

                    if (result != null) {
                        Log.i(TAG, "✅ ${result.topLabel} (${(result.confidence*100).toInt()}%)")

                        withContext(Dispatchers.Main) {
                            onClassificationResult?.invoke(
                                result.topLabel,
                                result.confidence,
                                result.inferenceTimeMs
                            )
                        }
                    }

                    bitmap.recycle()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Processing error", e)
            } finally {
                imageProxy.close()
            }
        }
    }
    
    /**
     * Convert ImageProxy to Bitmap (RGBA format for S24 Ultra)
     */
    @OptIn(ExperimentalGetImage::class)
    private fun convertToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            
            Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            ).apply {
                copyPixelsFromBuffer(buffer.rewind())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bitmap conversion failed", e)
            null
        }
    }
    
    /**
     * Stop camera
     */
    fun stopCamera() {
        cameraProvider?.unbindAll()
        analysisScope.cancel()

        // Clean up latest frame
        synchronized(frameLock) {
            latestFrame?.recycle()
            latestFrame = null
        }

        Log.i(TAG, "Camera stopped. Total frames: $frameCount")
    }

    /**
     * PHASE 5: Get latest camera frame for VisionAnalysisTool
     * Returns a copy of the latest frame, or null if no frame available
     */
    fun getLatestFrame(): Bitmap? {
        return synchronized(frameLock) {
            latestFrame?.let {
                // Return a copy to prevent recycling issues
                it.copy(it.config, true)
            }
        }
    }

    /**
     * PHASE 5: Check if camera is initialized and ready
     */
    fun isInitialized(): Boolean {
        return cameraProvider != null
    }
}
