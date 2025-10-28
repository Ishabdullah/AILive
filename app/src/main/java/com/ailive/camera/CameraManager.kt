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
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraManager - Captures frames and feeds them to ModelManager
 * Phase 2.2: Real-time object recognition
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val modelManager: ModelManager
) {
    private val TAG = "CameraManager"
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val analysisScope = CoroutineScope(Dispatchers.Default + Job())
    
    private var frameCount = 0
    
    var onClassificationResult: ((String, Float, Long) -> Unit)? = null
    
    /**
     * Start camera and begin classification
     */
    fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(previewView)
                Log.i(TAG, "✓ Camera started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    /**
     * Bind camera preview and image analysis
     */
    private fun bindCameraUseCases(previewView: PreviewView) {
        val cameraProvider = cameraProvider ?: return
        
        // Preview
        preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
        
        // Image analysis for classification
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImage(imageProxy)
                }
            }
        
        // Select back camera
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        try {
            // Unbind all use cases before rebinding
            cameraProvider.unbindAll()
            
            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            
            Log.i(TAG, "✓ Camera use cases bound")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
        }
    }
    
    /**
     * Process camera frame through ModelManager
     */
    @OptIn(ExperimentalGetImage::class)
    private fun processImage(imageProxy: ImageProxy) {
        frameCount++
        
        // Only process every 10th frame to avoid overload
        if (frameCount % 10 != 0) {
            imageProxy.close()
            return
        }
        
        analysisScope.launch {
            try {
                Log.d(TAG, "Processing frame #$frameCount")
                
                // Convert ImageProxy to Bitmap
                val bitmap = imageProxyToBitmap(imageProxy)
                
                if (bitmap != null) {
                    Log.d(TAG, "Bitmap created: ${bitmap.width}x${bitmap.height}")
                    
                    // Classify image
                    val result = modelManager.classifyImage(bitmap)
                    
                    if (result != null) {
                        Log.i(TAG, "Classification: ${result.topLabel} (${result.confidence})")
                        
                        // Callback to UI
                        withContext(Dispatchers.Main) {
                            onClassificationResult?.invoke(
                                result.topLabel,
                                result.confidence,
                                result.inferenceTimeMs
                            )
                        }
                    } else {
                        Log.w(TAG, "Classification returned null")
                    }
                    
                    bitmap.recycle()
                } else {
                    Log.e(TAG, "Failed to convert ImageProxy to Bitmap")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image", e)
            } finally {
                imageProxy.close()
            }
        }
    }
    
    /**
     * Convert ImageProxy to Bitmap (proper YUV conversion)
     */
    @OptIn(ExperimentalGetImage::class)
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null
        
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 50, out)
        val imageBytes = out.toByteArray()
        
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
    
    /**
     * Stop camera
     */
    fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        analysisScope.cancel()
        Log.i(TAG, "Camera stopped")
    }
}
