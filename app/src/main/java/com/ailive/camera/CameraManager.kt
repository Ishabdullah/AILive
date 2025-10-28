package com.ailive.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.media.Image
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

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val modelManager: ModelManager
) {
    private val TAG = "CameraManager"
    
    private var analysisUseCase: ImageAnalysis? = null
    private var frameCount = 0
    
    var onClassificationResult: ((String, Float, Long) -> Unit)? = null
    
    @SuppressLint("RestrictedApi")
    fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            // Preview
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            
            // Image Analysis - SIMPLE setup
            analysisUseCase = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            
            // THE KEY: Set analyzer on MAIN thread
            analysisUseCase!!.setAnalyzer(
                ContextCompat.getMainExecutor(context)
            ) { image ->
                frameCount++
                
                // Process every frame to verify it works
                if (frameCount % 30 == 0) {
                    Log.i(TAG, "========================================")
                    Log.i(TAG, "FRAME #$frameCount RECEIVED!")
                    Log.i(TAG, "========================================")
                    processImageProxy(image)
                } else {
                    image.close()
                }
            }
            
            // Bind to lifecycle
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    analysisUseCase
                )
                
                Log.i(TAG, "========================================")
                Log.i(TAG, "CAMERA BOUND! Waiting for frames...")
                Log.i(TAG, "========================================")
                
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed!", e)
            }
            
        }, ContextCompat.getMainExecutor(context))
    }
    
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Log.i(TAG, "Converting to bitmap...")
                val bitmap = imageProxyToBitmap(imageProxy)
                
                if (bitmap != null) {
                    Log.i(TAG, "Bitmap: ${bitmap.width}x${bitmap.height}")
                    Log.i(TAG, "Calling ModelManager...")
                    
                    val result = modelManager.classifyImage(bitmap)
                    
                    if (result != null) {
                        Log.i(TAG, "========================================")
                        Log.i(TAG, "SUCCESS! ${result.topLabel} (${(result.confidence*100).toInt()}%)")
                        Log.i(TAG, "========================================")
                        
                        withContext(Dispatchers.Main) {
                            onClassificationResult?.invoke(
                                result.topLabel,
                                result.confidence,
                                result.inferenceTimeMs
                            )
                        }
                    } else {
                        Log.w(TAG, "Result was null")
                    }
                    
                    bitmap.recycle()
                } else {
                    Log.e(TAG, "Bitmap conversion failed")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}", e)
            } finally {
                imageProxy.close()
            }
        }
    }
    
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    
    fun stopCamera() {
        Log.i(TAG, "Total frames received: $frameCount")
    }
}
