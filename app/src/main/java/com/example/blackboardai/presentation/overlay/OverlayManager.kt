package com.example.blackboardai.presentation.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class OverlayManager(
    private val activity: ComponentActivity,
    private val onOverlayStarted: () -> Unit = {},
    private val onPermissionDenied: (String) -> Unit = {}
) {
    
    companion object {
        private const val TAG = "[OverlayManager]"
    }
    
    private val mediaProjectionLauncher: ActivityResultLauncher<Intent>
    private val overlayPermissionLauncher: ActivityResultLauncher<Intent>
    private val mediaProjectionManager: MediaProjectionManager
    
    // Track overlay state
    private var isOverlayActive = false
    
    init {
        // Register launchers immediately since we're now created during onCreate
        mediaProjectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        mediaProjectionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            handleMediaProjectionResult(result.resultCode, result.data)
        }
        
        overlayPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            handleOverlayPermissionResult()
        }
    }
    
    private fun handleMediaProjectionResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            Log.d(TAG, "✅ MediaProjection permission granted")
            isOverlayActive = true
            startOverlayService(resultCode, data)
        } else {
            Log.w(TAG, "❌ MediaProjection permission denied")
            onPermissionDenied("Screenshot permission is required for the smart overlay feature")
        }
    }

    private fun handleOverlayPermissionResult() {
        // Check if permission was granted
        if (canDrawOverlays()) {
            Log.d(TAG, "✅ Overlay permission granted")
            requestMediaProjectionPermission()
        } else {
            Log.w(TAG, "❌ Overlay permission denied")
            onPermissionDenied("Overlay permission is required to show the smart assistant over other apps")
        }
    }
    
    /**
     * Start the smart overlay feature
     */
    fun startSmartOverlay() {
        Log.d(TAG, "🚀 Starting smart overlay...")
        
        when {
            !canDrawOverlays() -> {
                Log.d(TAG, "📱 Requesting overlay permission")
                requestOverlayPermission()
            }
            else -> {
                Log.d(TAG, "📷 Requesting MediaProjection permission")
                requestMediaProjectionPermission()
            }
        }
    }
    
    /**
     * Stop the smart overlay service
     */
    fun stopSmartOverlay() {
        Log.d(TAG, "🛑 Stopping smart overlay")
        isOverlayActive = false
        val intent = Intent(activity, SmartOverlayService::class.java).apply {
            action = SmartOverlayService.ACTION_STOP_OVERLAY
        }
        activity.stopService(intent)
    }
    
    /**
     * Check if the app can draw overlays
     */
    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(activity)
        } else {
            true // No permission needed for older versions
        }
    }
    
    /**
     * Request overlay permission
     */
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            // No permission needed for older versions
            requestMediaProjectionPermission()
        }
    }
    
    /**
     * Request MediaProjection permission for screenshot capture
     */
    private fun requestMediaProjectionPermission() {
        mediaProjectionManager.let { manager ->
            val intent = manager.createScreenCaptureIntent()
            mediaProjectionLauncher.launch(intent)
        } ?: run {
            Log.e(TAG, "MediaProjectionManager not available")
            onPermissionDenied("Screenshot capability not available on this device")
        }
    }
    
    /**
     * Start the overlay service with MediaProjection permission
     */
    private fun startOverlayService(resultCode: Int, data: Intent) {
        val intent = Intent(activity, SmartOverlayService::class.java).apply {
            action = SmartOverlayService.ACTION_START_OVERLAY
            putExtra(SmartOverlayService.EXTRA_MEDIA_PROJECTION_RESULT_CODE, resultCode)
            putExtra(SmartOverlayService.EXTRA_MEDIA_PROJECTION_DATA, data)
        }
        
        ContextCompat.startForegroundService(activity, intent)
        onOverlayStarted()
        
        Log.d(TAG, "🎯 Smart overlay service started")
    }
    
    /**
     * Check if smart overlay is currently running
     */
    fun isOverlayRunning(): Boolean {
        // This is a simple check - in a production app you might want to 
        // maintain a more sophisticated state tracking system
        return try {
            val intent = Intent(activity, SmartOverlayService::class.java)
            activity.startService(intent) == null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if overlay is currently active
     */
    fun isOverlayActive(): Boolean = isOverlayActive
} 