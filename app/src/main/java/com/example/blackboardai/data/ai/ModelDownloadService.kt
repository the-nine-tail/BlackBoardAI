package com.example.blackboardai.data.ai

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for downloading Gemma 3n model from Kaggle API.
 * 
 * To use with your own Kaggle account:
 * 1. Go to https://www.kaggle.com/account
 * 2. Create an API token
 * 3. Update KAGGLE_USERNAME and KAGGLE_KEY constants
 * 
 * For testing without network or permission issues:
 * 1. Download model manually using curl command to ~/Downloads/
 * 2. Push the file to device using one of these methods:
 *    - App-specific storage (recommended): adb push ~/Downloads/gemma-3n-e4b-it.task /sdcard/Android/data/com.example.blackboardai/files/
 *    - Public Documents: adb push ~/Downloads/gemma-3n-e4b-it.task /sdcard/Documents/
 *    - Public Downloads: adb push ~/Downloads/gemma-3n-e4b-it.task /sdcard/Download/
 * 3. The service will automatically detect and use the pre-downloaded model
 * 
 * Note: On Android 11+, access to public directories requires MANAGE_EXTERNAL_STORAGE permission
 * which must be granted manually in Settings > Apps > BlackBoardAI > Permissions > Files and media
 * 
 * The download URL corresponds to the curl command:
 * curl -L -u username:api_key -o model.task 
 * https://www.kaggle.com/api/v1/models/google/gemma-3n/tfLite/gemma-3n-e4b-it-int4/1/download
 */
@Singleton
class ModelDownloadService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    
    companion object {
        private const val TAG = "[BlackBoardAI Log]"
        private const val MODEL_FILENAME = "gemma-3n-e4b-it.task"
        private const val MODEL_URL = "https://www.kaggle.com/api/v1/models/google/gemma-3n/tfLite/gemma-3n-e4b-it-int4/1/download"
        
        // Kaggle API credentials - these should be set as environment variables or build config
        private const val KAGGLE_USERNAME = "sahdevsurolia" // Replace with your Kaggle username
        private const val KAGGLE_KEY = "b0250ef25037e6f3059bb97fa937430a" // Replace with your Kaggle API key
    }
    
    sealed class DownloadResult {
        data class Success(val file: File) : DownloadResult()
        data class Progress(val percentage: Int) : DownloadResult()
        data class Error(val message: String) : DownloadResult()
    }
    
    /**
     * Check if app has permission to access external storage
     */
    private fun hasExternalStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // For Android 10 and below, check traditional storage permissions
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun downloadModel(): Flow<DownloadResult> = flow {
        Log.d(TAG, "Downloading model...")
        val modelFile = File(context.filesDir, MODEL_FILENAME)
        
        // Check for manually placed model in accessible locations
        // Try multiple possible locations that the app can access
        val hasStoragePermission = hasExternalStoragePermission()
        Log.d(TAG, "External storage permission granted: $hasStoragePermission")
        
        val possibleLocations = listOf(
            // App-specific external storage (no permissions needed, accessible to user via file manager)
            File(context.getExternalFilesDir(null), MODEL_FILENAME),
            // App-specific Downloads folder
            File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), MODEL_FILENAME)
        )
        
        var externalModelFile: File? = null
        for (location in possibleLocations) {
            val isAppSpecific = location.absolutePath.contains("/Android/data/com.example.blackboardai/")
            
            if (location.exists() && location.canRead()) {
                externalModelFile = location
                Log.d(TAG, "Found pre-downloaded model at: ${location.absolutePath}")
                break
            } else if (location.exists()) {
                Log.w(TAG, "Model found but not readable at: ${location.absolutePath}")
                if (!isAppSpecific && !hasStoragePermission) {
                    Log.w(TAG, "  → This requires MANAGE_EXTERNAL_STORAGE permission")
                    Log.w(TAG, "  → Grant permission in Settings > Apps > BlackBoardAI > Permissions > Files and media")
                    Log.w(TAG, "  → OR move file to app-specific storage: ${context.getExternalFilesDir(null)?.absolutePath}")
                }
            }
        }
        
        if (externalModelFile != null) {
            Log.d(TAG, "Found pre-downloaded model, copying to app files...")
            try {
                externalModelFile.inputStream().use { inputStream ->
                    modelFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.d(TAG, "Model copied successfully from: ${externalModelFile.absolutePath}")
                emit(DownloadResult.Success(modelFile))
                return@flow
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy model from ${externalModelFile.absolutePath}: ${e.message}")
                // Don't return here, continue with network download
            }
        } else {
            Log.d(TAG, "No pre-downloaded model found in accessible locations")
            Log.d(TAG, "Checked locations:")
            possibleLocations.forEach { location ->
                Log.d(TAG, "  ${location.absolutePath} - exists: ${location.exists()}, readable: ${location.canRead()}")
            }
            Log.d(TAG, "Proceeding with network download")
        }
        
        try {
            Log.d(TAG, "Starting model download from Kaggle...")
            
            // Create request with Kaggle API authentication
            val credentials = Credentials.basic(KAGGLE_USERNAME, KAGGLE_KEY)
            val request = Request.Builder()
                .url(MODEL_URL)
                .header("Authorization", credentials)
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                emit(DownloadResult.Error("Kaggle download failed: ${response.code} - Check your Kaggle credentials"))
                return@flow
            }
            
            val contentLength = response.body?.contentLength() ?: -1
            val source = response.body?.source()
            
            if (source == null) {
                emit(DownloadResult.Error("Empty response body"))
                return@flow
            }
            
            val sink = modelFile.sink().buffer()
            var totalBytesRead = 0L
            var lastProgress = 0
            
            sink.use { bufferedSink ->
                while (true) {
                    val bytesRead = source.read(bufferedSink.buffer, 8192)
                    if (bytesRead == -1L) break
                    
                    bufferedSink.emitCompleteSegments()
                    totalBytesRead += bytesRead
                    
                    if (contentLength > 0) {
                        val progress = ((totalBytesRead * 100) / contentLength).toInt()
                        if (progress != lastProgress) {
                            emit(DownloadResult.Progress(progress))
                            lastProgress = progress
                        }
                    }
                }
            }
            
            Log.d(TAG, "Kaggle model download completed successfully")
            emit(DownloadResult.Success(modelFile))
            
        } catch (e: IOException) {
            Log.e(TAG, "Download failed", e)
            emit(DownloadResult.Error("Network error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during download", e)
            emit(DownloadResult.Error("Unexpected error: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
} 