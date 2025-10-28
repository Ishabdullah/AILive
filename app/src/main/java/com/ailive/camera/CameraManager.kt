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
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val modelManager: ModelManager
) {
    private val TAG = "CameraManager"
    
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val analysisScope = CoroutineScope(Dispatchers.Default + Job())
    
    private var frameCount = 0
    var onClassificationResult: ((String, Float, Long) -> Unit)? = null
    
    fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCamera(previewView)
                Log.i(TAG, "✓ Camera started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Camera start failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    private fun bindCamera(previewView: PreviewView) {
        val cameraProvider = cameraProvider ?: return
        
        // Preview use case
        val preview = Preview.Builder()
            .build()
            .apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
        
        // Image analysis use case
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
        
        // Set analyzer with explicit logging
        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            frameCount++
            Log.d(TAG, ">>> ANALYZER CALLED: Frame #$frameCount")
            
            // Process immediately - no skipping frames yet
            processFrame(imageProxy)
        }
        
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            Log.i(TAG, "✓ Camera bound with preview + analysis")
        } catch (e: Exception) {
            Log.e(TAG, "Binding failed", e)
        }
    }
    
    @OptIn(ExperimentalGetImage::class)
    private fun processFrame(imageProxy: ImageProxy) {
        // Only process every 30th frame to avoid overload
        if (frameCount % 30 != 0) {
            imageProxy.close()
            return
        }
        
        analysisScope.launch {
            try {
                Log.i(TAG, "Processing frame #$frameCount")
                
                val bitmap = yuv420ToBitmap(imageProxy)
                
                if (bitmap != null) {
                    Log.d(TAG, "Bitmap: ${bitmap.width}x${bitmap.height}")
                    
                    val result = modelManager.classifyImage(bitmap)
                    
                    if (result != null) {
                        Log.i(TAG, "✓ Result: ${result.topLabel} (${(result.confidence * 100).toInt()}%)")
                        
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
                    Log.e(TAG, "Bitmap conversion failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Frame processing error", e)
            } finally {
                imageProxy.close()
            }
        }
    }
    
    @OptIn(ExperimentalGetImage::class)
    private fun yuv420ToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null
        
        try {
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
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 80, out)
            val imageBytes = out.toByteArray()
            
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "YUV conversion failed", e)
            return null
        }
    }
    
    fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        analysisScope.cancel()
        Log.i(TAG, "Camera stopped")
    }
}
