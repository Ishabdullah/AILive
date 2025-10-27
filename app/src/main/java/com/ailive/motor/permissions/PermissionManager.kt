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
 */
class PermissionManager(private val activity: FragmentActivity) {
    private val TAG = "PermissionManager"
    
    private val _permissionStates = MutableStateFlow<Map<String, PermissionState>>(emptyMap())
    val permissionStates: StateFlow<Map<String, PermissionState>> = _permissionStates.asStateFlow()
    
    // Single permission launcher
    private val singlePermissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            handlePermissionResult(pendingPermission, granted)
        }
    
    // Multiple permissions launcher
    private val multiplePermissionsLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            results.forEach { (permission, granted) ->
                handlePermissionResult(permission, granted)
            }
        }
    
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
                Log.i(TAG, "Showing rationale for: $permission")
                updatePermissionState(permission, PermissionState.RationaleRequired(rationale ?: ""))
                // In production, show a dialog with rationale before requesting
                requestPermissionInternal(permission, onResult)
            }
            else -> {
                Log.d(TAG, "Requesting permission: $permission")
                requestPermissionInternal(permission, onResult)
            }
        }
    }
    
    /**
     * Request multiple permissions at once.
     */
    fun requestPermissions(
        context: Context,
        permissions: List<String>,
        onResult: (Map<String, Boolean>) -> Unit
    ) {
        val deniedPermissions = permissions.filter { !isPermissionGranted(context, it) }
        
        if (deniedPermissions.isEmpty()) {
            Log.d(TAG, "All permissions already granted")
            onResult(permissions.associateWith { true })
            return
        }
        
        Log.d(TAG, "Requesting ${deniedPermissions.size} permissions")
        
        // Store callback for multiple permissions
        val results = mutableMapOf<String, Boolean>()
        onPermissionResultCallback = { permission, granted ->
            results[permission] = granted
            if (results.size == deniedPermissions.size) {
                onResult(results)
            }
        }
        
        multiplePermissionsLauncher.launch(deniedPermissions.toTypedArray())
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
    
    private fun requestPermissionInternal(permission: String, onResult: (Boolean) -> Unit) {
        pendingPermission = permission
        onPermissionResultCallback = { _, granted -> onResult(granted) }
        singlePermissionLauncher.launch(permission)
    }
    
    private fun handlePermissionResult(permission: String?, granted: Boolean) {
        permission ?: return
        
        Log.i(TAG, "Permission result: $permission = $granted")
        updatePermissionState(
            permission,
            if (granted) PermissionState.Granted else PermissionState.Denied
        )
        
        onPermissionResultCallback?.invoke(permission, granted)
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
