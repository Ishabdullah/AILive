package com.ailive.motor.actuators

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import com.ailive.motor.ActionResult
import com.ailive.motor.permissions.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-level camera control for AILive.
 * Wraps Camera2 API with safety checks.
 */
class CameraController(
    private val context: Context,
    private val permissionManager: PermissionManager
) {
    private val TAG = "CameraController"
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    
    /**
     * Get list of available cameras.
     */
    suspend fun getAvailableCameras(): ActionResult<List<CameraInfo>> = withContext(Dispatchers.IO) {
        try {
            val cameraList = mutableListOf<CameraInfo>()
            
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                val level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                
                cameraList.add(
                    CameraInfo(
                        id = cameraId,
                        facing = when (facing) {
                            CameraCharacteristics.LENS_FACING_FRONT -> CameraFacing.FRONT
                            CameraCharacteristics.LENS_FACING_BACK -> CameraFacing.BACK
                            else -> CameraFacing.EXTERNAL
                        },
                        hardwareLevel = level ?: 0
                    )
                )
            }
            
            Log.d(TAG, "Found ${cameraList.size} cameras")
            ActionResult.Success(cameraList)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera access error", e)
            ActionResult.Failure(
                error = com.ailive.motor.ActionError.HardwareUnavailable("Camera"),
                recoverable = true
            )
        } catch (e: Exception) {
            ActionResult.Failure(
                error = com.ailive.motor.ActionError.Unknown(e),
                recoverable = false
            )
        }
    }
    
    /**
     * Check if camera permission is granted.
     */
    fun hasCameraPermission(): Boolean {
        return permissionManager.isPermissionGranted(context, Manifest.permission.CAMERA)
    }
    
    /**
     * Request camera permission.
     */
    fun requestCameraPermission(onResult: (Boolean) -> Unit) {
        permissionManager.requestPermission(
            context,
            Manifest.permission.CAMERA,
            rationale = "AILive needs camera access for visual perception",
            onResult = onResult
        )
    }
    
    /**
     * Placeholder for actual capture logic (Camera2 implementation would go here).
     */
    suspend fun captureImage(cameraId: String): ActionResult<String> = withContext(Dispatchers.IO) {
        if (!hasCameraPermission()) {
            return@withContext ActionResult.SafetyBlocked(
                violation = "Camera permission not granted",
                rule = "PERMISSION_REQUIRED",
                requiredPermission = Manifest.permission.CAMERA
            )
        }
        
        // In production: implement full Camera2 capture session
        Log.d(TAG, "Capture image requested for camera: $cameraId")
        
        // Simulated result
        ActionResult.Success(
            data = "/storage/emulated/0/AILive/capture_${System.currentTimeMillis()}.jpg",
            metadata = mapOf("camera_id" to cameraId)
        )
    }
}

data class CameraInfo(
    val id: String,
    val facing: CameraFacing,
    val hardwareLevel: Int
)

enum class CameraFacing {
    FRONT, BACK, EXTERNAL
}
