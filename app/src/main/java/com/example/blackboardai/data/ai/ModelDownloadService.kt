package com.example.blackboardai.data.ai

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
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
        private const val TEMP_DOWNLOAD_FILENAME = "model_download.zip"
        private const val MODEL_URL = "https://www.kaggle.com/api/v1/models/google/gemma-3n/tfLite/gemma-3n-e4b-it-int4/1/download"
        
        // Common model file extensions to look for in extracted files
        private val MODEL_EXTENSIONS = listOf(".task", ".tflite", ".bin")
        
        // Kaggle API credentials - these should be set as environment variables or build config
        private const val KAGGLE_USERNAME = "sahdevsurolia" // Replace with your Kaggle username
        private const val KAGGLE_KEY = "b0250ef25037e6f3059bb97fa937430a" // Replace with your Kaggle API key
    }
    
    sealed class DownloadResult {
        data class Success(val file: File) : DownloadResult()
        data class Progress(val percentage: Int, val currentPath: String? = null) : DownloadResult()
        data class Error(val message: String, val fileMetadata: String? = null) : DownloadResult()
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
    
    /**
     * Get the target model file path using app-specific external Downloads folder
     */
    private fun getModelFilePath(): File {
        val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(null) // Fallback to app external storage root
            ?: context.filesDir // Final fallback to internal storage
            
        // Ensure the directory exists
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        
        return File(downloadsDir, MODEL_FILENAME)
    }

    suspend fun downloadModel(): Flow<DownloadResult> = flow {
        Log.d(TAG, "Downloading model...")
        
        // Get the target model file path using app-specific external Downloads folder
        val modelFile = getModelFilePath()
        Log.d(TAG, "📁 Model target path: ${modelFile.absolutePath}")
        
        // Emit initial progress with target path
        emit(DownloadResult.Progress(0, "Target: ${modelFile.absolutePath}"))
        
        // Check storage permissions first
        val hasStoragePermission = hasExternalStoragePermission()
        Log.d(TAG, "External storage permission granted: $hasStoragePermission")
        
        if (!hasStoragePermission) {
            Log.e(TAG, "❌ Storage permission not granted")
            val metadata = buildFileMetadata(modelFile)
            emit(DownloadResult.Error(
                "Storage permission required: BlackBoard AI needs access to device storage to download and extract the AI model. Please grant storage permission in app settings.", 
                metadata
            ))
            return@flow
        }
        
        // Check for manually placed model in accessible locations
        // Try multiple possible locations that the app can access
        val possibleLocations = buildList {
            // App-specific external storage (no permissions needed, accessible to user via file manager)
            context.getExternalFilesDir(null)?.let { 
                add(File(it, MODEL_FILENAME))
            }
            
            // App-specific Downloads folder
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)?.let {
                add(File(it, MODEL_FILENAME))
            }
            
            // Additional robust directory detection for different Android devices
            val externalStorageDirs = context.getExternalFilesDirs(null)
            externalStorageDirs.forEach { dir ->
                dir?.let {
                    add(File(it, MODEL_FILENAME))
                    // Also check Downloads subfolder in each external storage
                    add(File(it, "Downloads/$MODEL_FILENAME"))
                }
            }
            
            // Check common Android storage patterns (if we have permission)
            if (hasStoragePermission) {
                // External storage root
                Environment.getExternalStorageDirectory()?.let { extStorage ->
                    add(File(extStorage, "Download/$MODEL_FILENAME"))
                    add(File(extStorage, "Downloads/$MODEL_FILENAME"))
                    add(File(extStorage, "Documents/$MODEL_FILENAME"))
                    
                    // Samsung devices often use different patterns
                    add(File(extStorage, "storage/emulated/0/Download/$MODEL_FILENAME"))
                    add(File(extStorage, "storage/emulated/0/Downloads/$MODEL_FILENAME"))
                }
                
                // Check for secondary storage (SD cards)
                val secondaryStorages = context.getExternalFilesDirs(null).drop(1)
                secondaryStorages.forEach { storage ->
                    storage?.let {
                        val parentDir = it.parentFile?.parentFile?.parentFile?.parentFile
                        parentDir?.let { root ->
                            add(File(root, "Download/$MODEL_FILENAME"))
                            add(File(root, "Downloads/$MODEL_FILENAME"))
                        }
                    }
                }
            }
        }.distinctBy { it.absolutePath } // Remove duplicates
        
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
                val totalSize = externalModelFile.length()
                var copiedBytes = 0L
                var lastProgress = 0
                
                emit(DownloadResult.Progress(0, "Copying from: ${externalModelFile.absolutePath}"))
                
                externalModelFile.inputStream().use { inputStream ->
                    modelFile.outputStream().use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            copiedBytes += bytesRead
                            
                            // Emit progress every few percent to show activity
                            val progress = if (totalSize > 0) {
                                ((copiedBytes * 100) / totalSize).toInt()
                            } else {
                                50 // If size unknown, show 50% progress
                            }
                            
                            if (progress != lastProgress && progress % 5 == 0) {
                                emit(DownloadResult.Progress(progress, "Copying from: ${externalModelFile.absolutePath}"))
                                lastProgress = progress
                                // Small delay to make progress visible for fast local copies
                                kotlinx.coroutines.delay(50)
                            }
                        }
                    }
                }
                
                emit(DownloadResult.Progress(100, "Copied to: ${modelFile.absolutePath}"))
                Log.d(TAG, "Model copied successfully from: ${externalModelFile.absolutePath}")
                emit(DownloadResult.Success(modelFile))
                return@flow
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy model from ${externalModelFile.absolutePath}: ${e.message}")
                
                // Log detailed info about the failed copy operation
                val sourceMetadata = buildFileMetadata(externalModelFile, "Copy Source")
                val targetMetadata = buildFileMetadata(modelFile, "Copy Target")
                Log.e(TAG, "Copy failed - Source: $sourceMetadata")
                Log.e(TAG, "Copy failed - Target: $targetMetadata")
                
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
            emit(DownloadResult.Progress(0, "Downloading from: $MODEL_URL"))
            
            // Create request with Kaggle API authentication
            val credentials = Credentials.basic(KAGGLE_USERNAME, KAGGLE_KEY)
            val request = Request.Builder()
                .url(MODEL_URL)
                .header("Authorization", credentials)
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val metadata = buildFileMetadata(modelFile)
                emit(DownloadResult.Error(
                    "Kaggle download failed: ${response.code} - Check your Kaggle credentials", 
                    metadata
                ))
                return@flow
            }
            
            val contentLength = response.body?.contentLength() ?: -1
            val source = response.body?.source()
            
            if (source == null) {
                val metadata = buildFileMetadata(modelFile)
                emit(DownloadResult.Error("Empty response body", metadata))
                return@flow
            }
            
            // Download to temporary file first  
            val tempDownloadFile = File(modelFile.parentFile, TEMP_DOWNLOAD_FILENAME)
            val sink = tempDownloadFile.sink().buffer()
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
                        if (progress != lastProgress && progress < 90) { // Reserve 90-100% for extraction
                            emit(DownloadResult.Progress(progress, "Downloading to: ${tempDownloadFile.absolutePath}"))
                            lastProgress = progress
                        }
                    }
                }
            }
            
            Log.d(TAG, "Kaggle model download completed successfully")
            
            // Check if the downloaded file is compressed and needs extraction
            val downloadedFile = tempDownloadFile
            
            if (isCompressedFile(downloadedFile)) {
                val compressionType = getCompressionType(downloadedFile)
                Log.d(TAG, "Downloaded file is compressed ($compressionType), extracting...")
                val extractMessage = when (compressionType) {
                    "ZIP" -> "Extracting ZIP archive from: ${downloadedFile.absolutePath}"
                    "GZIP" -> "Extracting GZIP file from: ${downloadedFile.absolutePath}"
                    "TAR_GZ" -> "Extracting TAR.GZ archive from: ${downloadedFile.absolutePath}"
                    else -> "Extracting $compressionType file from: ${downloadedFile.absolutePath}"
                }
                emit(DownloadResult.Progress(90, extractMessage))
                
                val extractedFile = when (compressionType) {
                    "ZIP" -> extractFromZip(downloadedFile, modelFile)
                    "GZIP" -> extractFromGzip(downloadedFile, modelFile)
                    "TAR_GZ" -> extractFromTarGz(downloadedFile, modelFile)
                    else -> null
                }
                emit(DownloadResult.Progress(95, "Finalizing extraction..."))
                downloadedFile.delete() // Clean up temporary file
                
                if (extractedFile != null) {
                    emit(DownloadResult.Progress(100, "Extracted to: ${extractedFile.absolutePath}"))
                    emit(DownloadResult.Success(extractedFile))
                } else {
                    val fileMetadata = buildFileMetadata(downloadedFile, compressionType)
                    emit(DownloadResult.Error(
                        "Failed to extract model file from $compressionType archive", 
                        fileMetadata
                    ))
                }
            } else {
                // File is not compressed, rename to final name
                if (downloadedFile.renameTo(modelFile)) {
                    emit(DownloadResult.Progress(100, "Ready: ${modelFile.absolutePath}"))
                    emit(DownloadResult.Success(modelFile))
                } else {
                    val metadata = buildFileMetadata(downloadedFile)
                    emit(DownloadResult.Error("Failed to rename downloaded file", metadata))
                }
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "Download failed", e)
            val metadata = buildFileMetadata(modelFile)
            emit(DownloadResult.Error("Network error: ${e.message}", metadata))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during download", e)
            val metadata = buildFileMetadata(modelFile)
            emit(DownloadResult.Error("Unexpected error: ${e.message}", metadata))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Check if a file is compressed (zip or gzip format)
     */
    private fun isCompressedFile(file: File): Boolean {
        return try {
            val header = ByteArray(4)
            FileInputStream(file).use { input ->
                input.read(header)
            }
            
            // Check for ZIP file signature (PK header)
            val isZip = header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
            
            // Check for GZIP file signature (1f 8b)
            val isGzip = header[0] == 0x1f.toByte() && header[1] == 0x8b.toByte()
            
            isZip || isGzip
        } catch (e: Exception) {
            Log.w(TAG, "Could not check file compression: ${e.message}")
            false
        }
    }
    
    /**
     * Get compression type of a file
     */
    private fun getCompressionType(file: File): String {
        return try {
            val header = ByteArray(10)
            FileInputStream(file).use { input ->
                input.read(header)
            }
            
            when {
                header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() -> "ZIP"
                header[0] == 0x1f.toByte() && header[1] == 0x8b.toByte() -> {
                    // Check if it's a tar.gz by trying to detect TAR signature after GZIP decompression
                    if (isTarGzFile(file)) "TAR_GZ" else "GZIP"
                }
                else -> "UNKNOWN"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine compression type: ${e.message}")
            "UNKNOWN"
        }
    }
    
    /**
     * Check if a GZIP file contains a TAR archive (tar.gz)
     */
    private fun isTarGzFile(file: File): Boolean {
        return try {
            GZIPInputStream(FileInputStream(file)).use { gzipStream ->
                val tarHeader = ByteArray(262) // TAR header is 512 bytes, but we only need first part
                val bytesRead = gzipStream.read(tarHeader)
                
                if (bytesRead >= 262) {
                    // Check for TAR magic number at position 257: "ustar\0"
                    val magic = String(tarHeader, 257, 5, Charsets.US_ASCII)
                    magic == "ustar"
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not check if file is tar.gz: ${e.message}")
            false
        }
    }
    
    /**
     * Extract model file from a ZIP archive and rename to MODEL_FILENAME
     */
    private suspend fun extractFromZip(zipFile: File, targetFile: File): File? {
        return try {
            ZipInputStream(FileInputStream(zipFile)).use { zipInputStream ->
                var entry: ZipEntry? = zipInputStream.nextEntry
                
                while (entry != null) {
                    val entryName = entry.name
                    Log.d(TAG, "Found ZIP entry: $entryName")
                    
                    // Look for files with model extensions
                    if (!entry.isDirectory && MODEL_EXTENSIONS.any { ext -> 
                        entryName.endsWith(ext, ignoreCase = true) 
                    }) {
                        Log.d(TAG, "Extracting model file: $entryName")
                        
                        // Extract this file as our model file
                        FileOutputStream(targetFile).use { output ->
                            val buffer = ByteArray(8192)
                            var length: Int
                            
                            while (zipInputStream.read(buffer).also { length = it } > 0) {
                                output.write(buffer, 0, length)
                            }
                        }
                        
                        Log.d(TAG, "Successfully extracted ZIP and renamed to: ${targetFile.absolutePath}")
                        return@use targetFile
                    }
                    
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
                
                Log.w(TAG, "No model file found in ZIP with extensions: $MODEL_EXTENSIONS")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract from ZIP: ${e.message}")
            null
        }
    }
    
    /**
     * Extract model file from a GZIP archive (simple .gz files, not tar.gz)
     */
    private suspend fun extractFromGzip(gzipFile: File, targetFile: File): File? {
        return try {
            GZIPInputStream(FileInputStream(gzipFile)).use { gzipInputStream ->
                // For .gz files, extract directly to target
                Log.d(TAG, "Extracting GZIP file: ${gzipFile.name}")
                
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var length: Int
                    
                    while (gzipInputStream.read(buffer).also { length = it } > 0) {
                        output.write(buffer, 0, length)
                    }
                }
                
                // Check if the extracted file has the right extension
                if (MODEL_EXTENSIONS.any { ext -> 
                    gzipFile.name.contains(ext.substring(1), ignoreCase = true) || 
                    targetFile.length() > 1024 * 1024 // Assume files > 1MB might be model files
                }) {
                    Log.d(TAG, "Successfully extracted GZIP and renamed to: ${targetFile.absolutePath}")
                    targetFile
                } else {
                    Log.w(TAG, "Extracted GZIP file doesn't appear to be a model file")
                    targetFile.delete()
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract from GZIP: ${e.message}")
            null
        }
    }
    
    /**
     * Build detailed file metadata for error reporting
     */
    private fun buildFileMetadata(file: File, compressionType: String? = null): String {
        return buildString {
            appendLine("File Information:")
            appendLine("• Name: ${file.name}")
            appendLine("• Path: ${file.absolutePath}")
            appendLine("• Size: ${formatFileSize(file.length())}")
            appendLine("• Exists: ${file.exists()}")
            appendLine("• Readable: ${file.canRead()}")
            appendLine("• Last Modified: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(file.lastModified()))}")
            
            if (compressionType != null) {
                appendLine("• Compression: $compressionType")
            }
            
            if (file.exists()) {
                try {
                    val header = ByteArray(10)
                    java.io.FileInputStream(file).use { input ->
                        val bytesRead = input.read(header)
                        if (bytesRead > 0) {
                            val hexHeader = header.take(bytesRead).joinToString(" ") { 
                                String.format("%02X", it)
                            }
                            appendLine("• File Header: $hexHeader")
                        }
                    }
                } catch (e: Exception) {
                    appendLine("• File Header: Could not read (${e.message})")
                }
            }
        }
    }
    
    /**
     * Format file size in human readable format
     */
    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.1f GB", gb)
    }
    
    /**
     * Extract model file from a TAR.GZ archive
     */
    private suspend fun extractFromTarGz(tarGzFile: File, targetFile: File): File? {
        return try {
            Log.d(TAG, "Extracting TAR.GZ file: ${tarGzFile.name}")
            
            GZIPInputStream(FileInputStream(tarGzFile)).use { gzipInputStream ->
                TarArchiveInputStream(gzipInputStream).use { tarInputStream ->
                    var entry: TarArchiveEntry? = tarInputStream.nextTarEntry
                    val allEntries = mutableListOf<String>()
                    
                    while (entry != null) {
                        val entryName = entry.name
                        val entrySize = entry.size
                        allEntries.add("$entryName (${entrySize} bytes)")
                        Log.d(TAG, "Found TAR entry: $entryName (size: $entrySize bytes, isDirectory: ${entry.isDirectory})")
                        
                        // Look for files with model extensions or potential model files
                        val hasModelExtension = MODEL_EXTENSIONS.any { ext -> 
                            entryName.endsWith(ext, ignoreCase = true) 
                        }
                        val isPotentialModelFile = !entry.isDirectory && (
                            hasModelExtension ||
                            entryName.contains("gemma", ignoreCase = true) ||
                            entryName.contains("model", ignoreCase = true) ||
                            (entrySize > 10 * 1024 * 1024 && entryName.contains("it", ignoreCase = true)) // Large files with "it" (instruction tuned)
                        )
                        
                        if (isPotentialModelFile) {
                            Log.d(TAG, "✅ Extracting model file from TAR: $entryName")
                            
                            // Extract this file as our model file
                            FileOutputStream(targetFile).use { output ->
                                val buffer = ByteArray(8192)
                                var length: Int
                                var totalWritten = 0L
                                
                                while (tarInputStream.read(buffer).also { length = it } > 0) {
                                    output.write(buffer, 0, length)
                                    totalWritten += length
                                }
                                
                                Log.d(TAG, "Extracted $totalWritten bytes from $entryName")
                            }
                            
                            Log.d(TAG, "✅ Successfully extracted TAR.GZ and renamed to: ${targetFile.absolutePath}")
                            return@use targetFile
                        }
                        
                        entry = tarInputStream.nextTarEntry
                    }
                    
                    Log.w(TAG, "❌ No model file found in TAR.GZ with extensions: $MODEL_EXTENSIONS")
                    Log.w(TAG, "Available entries in archive: ${allEntries.joinToString(", ")}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract from TAR.GZ: ${e.message}")
            Log.e(TAG, "Error details: ${e.stackTraceToString()}")
            
            // Log detailed file information for debugging
            val fileMetadata = buildFileMetadata(tarGzFile, "TAR.GZ")
            Log.e(TAG, "TAR.GZ Extraction Failed - $fileMetadata")
            
            null
        }
    }
} 