package com.ailive.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.ailive.ai.models.ModelManager
import kotlinx.coroutines.*
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
    
    private var lastClassification = ""
    private var lastConfidence = 0f
    private var lastInferenceTime = 0L
    
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
        analysisScope.launch {
            try {
                // Convert ImageProxy to Bitmap
                val bitmap = imageProxy.toBitmap()
                
                if (bitmap != null) {
                    // Classify image
                    val result = modelManager.classifyImage(bitmap)
                    
                    if (result != null) {
                        lastClassification = result.topLabel
                        lastConfidence = result.confidence
                        lastInferenceTime = result.inferenceTimeMs
                        
                        // Callback to UI
                        withContext(Dispatchers.Main) {
                            onClassificationResult?.invoke(
                                lastClassification,
                                lastConfidence,
                                lastInferenceTime
                            )
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image", e)
            } finally {
                imageProxy.close()
            }
        }
    }
    
    /**
     * Convert ImageProxy to Bitmap
     */
    @OptIn(ExperimentalGetImage::class)
    private fun ImageProxy.toBitmap(): Bitmap? {
        val image = this.image ?: return null
        
        // Convert YUV to RGB bitmap
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        
        // Simple conversion (you may need more sophisticated YUV->RGB)
        // For now, we'll use a placeholder
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
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
    
    /**
     * Get last classification result
     */
    fun getLastResult() = Triple(lastClassification, lastConfidence, lastInferenceTime)
}
