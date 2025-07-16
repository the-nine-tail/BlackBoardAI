package com.example.blackboardai.presentation.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.blackboardai.R
import com.example.blackboardai.data.ai.GoogleAIService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SmartOverlayService : Service() {
    
    companion object {
        private const val TAG = "[BlackBoardAI Overlay]"
        private const val NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "smart_overlay_channel"
        
        const val ACTION_START_OVERLAY = "START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "STOP_OVERLAY"
        const val EXTRA_MEDIA_PROJECTION_DATA = "MEDIA_PROJECTION_DATA"
        const val EXTRA_MEDIA_PROJECTION_RESULT_CODE = "MEDIA_PROJECTION_RESULT_CODE"
    }
    
    @Inject
    lateinit var googleAIService: GoogleAIService
    
    private var windowManager: WindowManager? = null
    private var floatingButton: FloatingButtonView? = null
    private var overlayView: OverlaySelectionView? = null
    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    
    private var isOverlayVisible = false
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SmartOverlayService created")
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_OVERLAY -> {
                val resultCode = intent.getIntExtra(EXTRA_MEDIA_PROJECTION_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(EXTRA_MEDIA_PROJECTION_DATA)
                
                if (resultCode == Activity.RESULT_OK && data != null) {
                    try {
                        Log.d(TAG, "🚀 Starting overlay service with media projection...")
                        
                        // CRITICAL: Start foreground service BEFORE creating MediaProjection
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            // Android 14+ requires specific service type
                            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                        } else {
                            startForeground(NOTIFICATION_ID, createNotification())
                        }
                        
                        Log.d(TAG, "✅ Foreground service started successfully")
                        
                        // Now safe to create MediaProjection
                        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
                        Log.d(TAG, "✅ MediaProjection created successfully")
                        
                        // Show floating button
                        Log.d(TAG, "🔲 Attempting to show floating button...")
                        showFloatingButton()
                        Log.d(TAG, "✅ showFloatingButton() call completed")
                        
                    } catch (e: SecurityException) {
                        Log.e(TAG, "❌ SecurityException starting overlay service: ${e.message}")
                        Toast.makeText(this, "Failed to start overlay: ${e.message}", Toast.LENGTH_LONG).show()
                        stopSelf()
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Exception starting overlay service: ${e.message}")
                        Toast.makeText(this, "Failed to start overlay service", Toast.LENGTH_SHORT).show()
                        stopSelf()
                    }
                } else {
                    Log.e(TAG, "❌ Invalid media projection data: resultCode=$resultCode, data=$data")
                    stopSelf()
                }
            }
            ACTION_STOP_OVERLAY -> {
                stopOverlay()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SmartOverlayService destroyed")
        hideFloatingButton()
        hideOverlay()
        mediaProjection?.stop()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Smart Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "BlackBoard AI Smart Overlay Service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val stopIntent = Intent(this, SmartOverlayService::class.java).apply {
            action = ACTION_STOP_OVERLAY
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BlackBoard AI Smart Overlay")
            .setContentText("Tap the floating button to capture and analyze content")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)
            .build()
    }
    
    private fun showFloatingButton() {
        if (floatingButton != null) {
            Log.d(TAG, "⚠️ Floating button already exists, skipping")
            return
        }
        
        try {
            Log.d(TAG, "🎯 Creating FloatingButtonView...")
            floatingButton = FloatingButtonView(this) { onFloatingButtonClicked() }
            Log.d(TAG, "✅ FloatingButtonView created successfully")
            
            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            
            val params = WindowManager.LayoutParams(
                200, // Fixed width
                200, // Fixed height  
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 200
            }
            
            Log.d(TAG, "🎯 Adding floating button to WindowManager...")
            windowManager?.addView(floatingButton, params)
            floatingButton?.setWindowLayoutParams(params)
            Log.d(TAG, "✅ Floating button added to WindowManager successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show floating button: ${e.message}", e)
            Toast.makeText(this, "Failed to show overlay button: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun hideFloatingButton() {
        floatingButton?.let { button ->
            try {
                windowManager?.removeView(button)
                floatingButton = null
                Log.d(TAG, "Floating button hidden")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide floating button", e)
            }
        }
    }
    
    private fun onFloatingButtonClicked() {
        Log.d(TAG, "Floating button clicked")
        if (isOverlayVisible) {
            hideOverlay()
        } else {
            showOverlay()
        }
    }
    
    private fun showOverlay() {
        if (overlayView != null) return
        
        try {
            overlayView = OverlaySelectionView(
                context = this,
                mediaProjection = mediaProjection,
                googleAIService = googleAIService,
                onCloseListener = { hideOverlay() }
            )
            
            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            
            windowManager?.addView(overlayView, params)
            isOverlayVisible = true
            
            // Hide floating button when overlay is shown
            floatingButton?.visibility = View.GONE
            
            Log.d(TAG, "Overlay shown")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
            Toast.makeText(this, "Failed to show selection overlay", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun hideOverlay() {
        overlayView?.let { overlay ->
            try {
                windowManager?.removeView(overlay)
                overlayView = null
                isOverlayVisible = false
                
                // Show floating button when overlay is hidden
                floatingButton?.visibility = View.VISIBLE
                
                Log.d(TAG, "Overlay hidden")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide overlay", e)
            }
        }
    }
    
    private fun stopOverlay() {
        hideFloatingButton()
        hideOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
} 