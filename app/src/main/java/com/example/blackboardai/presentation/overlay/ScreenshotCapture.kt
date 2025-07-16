package com.example.blackboardai.presentation.overlay

import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ScreenshotCapture {
    
    private const val TAG = "[ScreenshotCapture]"
    
    /**
     * Captures a screenshot of the specified region using MediaProjection
     */
    suspend fun captureScreen(
        mediaProjection: MediaProjection,
        selectionBounds: Rect,
        displayMetrics: DisplayMetrics
    ): Bitmap? = suspendCancellableCoroutine { continuation ->
        
        var imageReader: ImageReader? = null
        var virtualDisplay: VirtualDisplay? = null
        var hasResumed = false // Flag to prevent multiple resumes
        var isCleanedUp = false // Flag to prevent double cleanup
        
        fun cleanup() {
            if (!isCleanedUp) {
                isCleanedUp = true
                try {
                    virtualDisplay?.release()
                    virtualDisplay = null
                    imageReader?.close()
                    imageReader = null
                    Log.d(TAG, "✅ Screenshot capture cleanup completed")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error during cleanup: ${e.message}")
                }
            }
        }
        
        try {
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            val density = displayMetrics.densityDpi
            
            Log.d(TAG, "Capturing screen: ${width}x$height @ ${density}dpi")
            
            // Create ImageReader
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
            
            // Store local reference to avoid smart cast issues
            val localImageReader = imageReader!!
            
            // Set up the capture listener
            localImageReader.setOnImageAvailableListener({ reader ->
                try {
                    // Check if already resumed to prevent race condition
                    if (hasResumed) {
                        Log.d(TAG, "Skipping duplicate image callback - already resumed")
                        return@setOnImageAvailableListener
                    }
                    
                    val image = reader.acquireLatestImage()
                    image?.use { img ->
                        val bitmap = convertImageToBitmap(img, width, height)
                        val croppedBitmap = cropBitmap(bitmap, selectionBounds)
                        val resizedBitmap = resizeBitmapTo512x512(croppedBitmap)
                        
                        Log.d(TAG, "Screenshot captured and processed: ${resizedBitmap.width}x${resizedBitmap.height}")
                        hasResumed = true
                        cleanup() // Clean up immediately after successful capture
                        continuation.resume(resizedBitmap)
                    } ?: run {
                        Log.e(TAG, "Failed to acquire image")
                        if (!hasResumed) {
                            hasResumed = true
                            cleanup()
                            continuation.resume(null)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing captured image", e)
                    if (!hasResumed) {
                        hasResumed = true
                        cleanup()
                        continuation.resumeWithException(e)
                    }
                }
            }, null)
            
            // Create virtual display
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "BlackBoardAI-Screenshot",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                localImageReader.surface,
                null, null
            )
            
            // Clean up when coroutine is cancelled
            continuation.invokeOnCancellation {
                Log.d(TAG, "Screenshot capture cancelled, cleaning up...")
                cleanup()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up screenshot capture", e)
            cleanup()
            if (!hasResumed) {
                hasResumed = true
                continuation.resumeWithException(e)
            }
        }
    }
    
    /**
     * Converts an Image to a Bitmap
     */
    private fun convertImageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width
        
        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        
        // Crop to remove any padding
        return if (rowPadding == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, width, height)
        }
    }
    
    /**
     * Crops the bitmap to the selection bounds
     */
    private fun cropBitmap(bitmap: Bitmap, bounds: Rect): Bitmap {
        // Ensure bounds are within bitmap dimensions
        val left = maxOf(0, bounds.left)
        val top = maxOf(0, bounds.top)
        val right = minOf(bitmap.width, bounds.right)
        val bottom = minOf(bitmap.height, bounds.bottom)
        
        val width = right - left
        val height = bottom - top
        
        return if (width > 0 && height > 0) {
            Bitmap.createBitmap(bitmap, left, top, width, height)
        } else {
            bitmap
        }
    }
    
    /**
     * Resizes the bitmap to 512x512 for AI processing
     */
    private fun resizeBitmapTo512x512(bitmap: Bitmap): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, 512, 512, true)
    }
} 