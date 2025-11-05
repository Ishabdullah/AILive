package com.ailive.motor.permissions

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Android runtime permissions for AILive.
 * Thread-safe, user-consent focused.
 *
 * NOTE: This version does NOT use ActivityResultLauncher to avoid
 * "attempting to register while current state is RESUMED" lifecycle errors.
 * For runtime permission requests, use MainActivity's permissionLauncher instead.
 */
class PermissionManager(private val activity: FragmentActivity) {
    private val TAG = "PermissionManager"

    private val _permissionStates = MutableStateFlow<Map<String, PermissionState>>(emptyMap())
    val permissionStates: StateFlow<Map<String, PermissionState>> = _permissionStates.asStateFlow()

    // Removed ActivityResultLauncher registrations - they cause lifecycle errors
    // when PermissionManager is created after onCreate
    private var pendingPermission: String? = null
    private var onPermissionResultCallback: ((String, Boolean) -> Unit)? = null
    
    /**
     * Check if a single permission is granted.
     */
    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if all permissions in a list are granted.
     */
    fun arePermissionsGranted(context: Context, permissions: List<String>): Boolean {
        return permissions.all { isPermissionGranted(context, it) }
    }
    
    /**
     * Request a single permission with callback.
     * NOTE: This method only checks if permission is already granted.
     * To request new permissions, use MainActivity's permissionLauncher.
     */
    fun requestPermission(
        context: Context,
        permission: String,
        rationale: String? = null,
        onResult: (granted: Boolean) -> Unit
    ) {
        when {
            isPermissionGranted(context, permission) -> {
                Log.d(TAG, "Permission already granted: $permission")
                updatePermissionState(permission, PermissionState.Granted)
                onResult(true)
            }
            activity.shouldShowRequestPermissionRationale(permission) -> {
                Log.i(TAG, "Permission requires rationale: $permission")
                updatePermissionState(permission, PermissionState.RationaleRequired(rationale ?: ""))
                // Cannot launch permission request - would cause lifecycle error
                Log.w(TAG, "⚠️ Permission not granted. Use MainActivity's permissionLauncher to request.")
                onResult(false)
            }
            else -> {
                Log.d(TAG, "Permission not granted: $permission")
                Log.w(TAG, "⚠️ Cannot request permission from PermissionManager. Use MainActivity's permissionLauncher.")
                onResult(false)
            }
        }
    }

    /**
     * Request multiple permissions at once.
     * NOTE: This method only checks if permissions are already granted.
     * To request new permissions, use MainActivity's permissionLauncher.
     */
    fun requestPermissions(
        context: Context,
        permissions: List<String>,
        onResult: (Map<String, Boolean>) -> Unit
    ) {
        val results = permissions.associateWith { isPermissionGranted(context, it) }
        val allGranted = results.values.all { it }

        if (allGranted) {
            Log.d(TAG, "All permissions already granted")
        } else {
            Log.w(TAG, "⚠️ Some permissions not granted. Use MainActivity's permissionLauncher to request.")
        }

        onResult(results)
    }
    
    /**
     * Get current state of a permission.
     */
    fun getPermissionState(context: Context, permission: String): PermissionState {
        return when {
            isPermissionGranted(context, permission) -> PermissionState.Granted
            activity.shouldShowRequestPermissionRationale(permission) -> 
                PermissionState.RationaleRequired("Please grant $permission permission")
            else -> PermissionState.NotRequested
        }
    }
    
    private fun updatePermissionState(permission: String, state: PermissionState) {
        val current = _permissionStates.value.toMutableMap()
        current[permission] = state
        _permissionStates.value = current
    }
    
    /**
     * Reset permission tracking (for testing).
     */
    fun reset() {
        _permissionStates.value = emptyMap()
        pendingPermission = null
        onPermissionResultCallback = null
    }
}

sealed class PermissionState {
    object NotRequested : PermissionState()
    object Granted : PermissionState()
    object Denied : PermissionState()
    data class RationaleRequired(val rationale: String) : PermissionState()
}
