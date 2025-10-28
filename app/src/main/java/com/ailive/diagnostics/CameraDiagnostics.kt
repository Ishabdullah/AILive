package com.ailive.diagnostics

import android.content.Context
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * CameraDiagnostics - S24 Ultra Camera Capability Scanner
 * Generates camera_support.json for adaptive AI behavior
 */
class CameraDiagnostics(private val context: Context) {
    private val TAG = "CameraDiagnostics"
    
    suspend fun runDiagnostics(): JSONObject = withContext(Dispatchers.Default) {
        val report = JSONObject()
        
        try {
            Log.i(TAG, "=== Starting Camera Diagnostics ===")
            
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            
            // Device info
            report.put("device", JSONObject().apply {
                put("manufacturer", android.os.Build.MANUFACTURER)
                put("model", android.os.Build.MODEL)
                put("android_version", android.os.Build.VERSION.SDK_INT)
            })
            
            // Check available cameras
            val hasBackCamera = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            val hasFrontCamera = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            
            report.put("cameras", JSONObject().apply {
                put("back_camera", hasBackCamera)
                put("front_camera", hasFrontCamera)
            })
            
            // Get back camera info
            if (hasBackCamera) {
                val camera = cameraProvider.bindToLifecycle(
                    context as androidx.lifecycle.LifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA
                )
                
                val cameraInfo = camera.cameraInfo
                
                report.put("camera_capabilities", JSONObject().apply {
                    put("has_flash", cameraInfo.hasFlashUnit())
                    put("zoom_supported", cameraInfo.zoomState.value != null)
                    put("torch_supported", cameraInfo.torchState.value != null)
                })
                
                cameraProvider.unbindAll()
            }
            
            // Hardware acceleration
            report.put("ai_acceleration", JSONObject().apply {
                put("nnapi_available", checkNNAPI())
                put("gpu_available", true) // S24 Ultra always has Adreno GPU
                put("npu_available", true) // S24 Ultra has NPU
            })
            
            Log.i(TAG, "✓ Diagnostics complete")
            Log.i(TAG, report.toString(2))
            
            // Save to file
            saveDiagnostics(report)
            
        } catch (e: Exception) {
            Log.e(TAG, "Diagnostics failed", e)
            report.put("error", e.message)
        }
        
        report
    }
    
    private fun checkNNAPI(): Boolean {
        return try {
            android.os.Build.VERSION.SDK_INT >= 27 // NNAPI available from API 27+
        } catch (e: Exception) {
            false
        }
    }
    
    private fun saveDiagnostics(report: JSONObject) {
        try {
            val dir = File(context.filesDir, "AILive/system/reports")
            dir.mkdirs()
            
            val file = File(dir, "camera_support.json")
            file.writeText(report.toString(2))
            
            Log.i(TAG, "✓ Report saved: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save report", e)
        }
    }
}
