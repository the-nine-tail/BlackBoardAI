package com.example.blackboardai.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Job

enum class ModelStatus {
    NOT_INITIALIZED,
    CHECKING_MODEL,
    DOWNLOADING_MODEL,
    EXTRACTING_MODEL,
    INITIALIZING_INFERENCE,
    CREATING_SESSION,
    WARMING_UP,
    READY,
    ERROR
}

data class ModelInitializationProgress(
    val status: ModelStatus = ModelStatus.NOT_INITIALIZED,
    val message: String = "",
    val progress: Float = 0f, // 0.0 to 1.0
    val error: String? = null,
    val currentPath: String? = null,
    val failedStep: ModelStatus? = null // Track which step failed for smart retry
)

data class ModelIntegrityResult(
    val isValid: Boolean,
    val message: String,
    val details: String,
    val shouldReDownload: Boolean = false
)

@Singleton
class GoogleAIService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloadService: ModelDownloadService
) {
    companion object {
        private const val TAG = "[BlackBoardAI Log]"
        private const val MODEL_FILENAME = "gemma-3n-e4b-it.task"
    }
    
    // Singleton pattern - these should NEVER be reset after initialization
    private var llmInference: LlmInference? = null
    private var llmSession: LlmInferenceSession? = null
    private var isModelInitialized = false
    private var cachedModelFilePath: String? = null
    
    // Thread-safe initialization mutex to prevent multiple concurrent initializations
    private val initializationMutex = Mutex()
    
    // Request mutex to prevent multiple concurrent AI requests
    private val requestMutex = Mutex()
    private var currentRequestJob: Job? = null
    
    // Central initialization coordinator - prevents multiple external calls
    private var initializationInProgress = false
    private var initializationCompleted = false
    
    // Model initialization progress tracking
    private val _initializationProgress = MutableStateFlow(ModelInitializationProgress())
    val initializationProgress: StateFlow<ModelInitializationProgress> = _initializationProgress.asStateFlow()
    
    /**
     * Get current model status
     */
    fun getModelStatus(): ModelStatus = _initializationProgress.value.status
    
    /**
     * Check if model initialization is needed (not started and not in progress)
     */
    fun isInitializationNeeded(): Boolean {
        val status = getModelStatus()
        return status == ModelStatus.NOT_INITIALIZED && !initializationInProgress && !initializationCompleted
    }
    
    /**
     * Reset initialization state for retry scenarios
     */
    suspend fun resetInitializationState() {
        initializationMutex.withLock {
            // CENTRAL COORDINATOR: Reset all flags
            initializationInProgress = false
            initializationCompleted = false
            
            _initializationProgress.value = ModelInitializationProgress(
                status = ModelStatus.NOT_INITIALIZED,
                message = "",
                progress = 0f
            )
            isModelInitialized = false
            llmInference?.close()
            llmSession = null
            llmInference = null
        }
    }
    
    /**
     * Force complete reset - clears all cached states and forces model re-download
     * Use this when model corruption is suspected
     */
    suspend fun forceCompleteReset() = withContext(Dispatchers.IO) {
        initializationMutex.withLock {
            Log.e(TAG, "🔄 FORCE RESET: Clearing all cached states and model files...")
            
            // CENTRAL COORDINATOR: Reset all flags
            initializationInProgress = false
            initializationCompleted = false
            
            // Close existing instances
            llmSession = null
            llmInference?.close()
            llmInference = null
            isModelInitialized = false
            cachedModelFilePath = null
        
        // Clear model files from all possible locations
        val modelLocations = listOf(
            getModelFilePath(), // Current Downloads location
            File(context.filesDir, MODEL_FILENAME), // Legacy internal storage
            // Additional app-specific storage locations
            context.getExternalFilesDir(null)?.let { File(it, MODEL_FILENAME) },
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)?.let { File(it, MODEL_FILENAME) }
        ).filterNotNull()
        
        modelLocations.forEach { file ->
            try {
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.e(TAG, "🗑️ Deleted model file: ${file.absolutePath} - Success: $deleted")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete model file: ${file.absolutePath} - ${e.message}")
            }
        }
        
        // Clear any temporary download files
        val tempFiles = listOf(
            File(getModelFilePath().parentFile, "model_download.zip"),
            File(getModelFilePath().parentFile, "model_download.tmp")
        )
        tempFiles.forEach { file ->
            try {
                if (file.exists()) {
                    file.delete()
                    Log.e(TAG, "🗑️ Deleted temp file: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete temp file: ${file.absolutePath}")
            }
        }
        
        // Force garbage collection to clear GPU memory caches
        performAggressiveMemoryCleanup()
        
        // Reset initialization state
        _initializationProgress.value = ModelInitializationProgress(
            status = ModelStatus.NOT_INITIALIZED,
            message = "Reset complete - ready for fresh initialization",
            progress = 0f
        )
        
        Log.e(TAG, "✅ FORCE RESET: Complete - all cached states cleared")
        } // End of initializationMutex.withLock
    }
    
    /**
     * Check model file integrity
     */
    suspend fun checkModelIntegrity(): ModelIntegrityResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔍 Checking model file integrity...")
        
        val modelFile = getModelFilePath()
        
        if (!modelFile.exists()) {
            return@withContext ModelIntegrityResult(
                isValid = false,
                message = "Model file not found",
                details = "File does not exist: ${modelFile.absolutePath}",
                shouldReDownload = true
            )
        }
        
        val fileSize = modelFile.length()
        if (fileSize == 0L) {
            return@withContext ModelIntegrityResult(
                isValid = false,
                message = "Model file is empty",
                details = "File size: 0 bytes at ${modelFile.absolutePath}",
                shouldReDownload = true
            )
        }
        
        // Check if file size is reasonable (Gemma 3n model should be > 100MB)
        val minExpectedSize = 100 * 1024 * 1024L // 100MB
        if (fileSize < minExpectedSize) {
            return@withContext ModelIntegrityResult(
                isValid = false,
                message = "Model file too small",
                details = "File size: ${formatFileSize(fileSize)}, expected > ${formatFileSize(minExpectedSize)}",
                shouldReDownload = true
            )
        }
        
        // Check file header to ensure it's not corrupted
        try {
            val header = ByteArray(16)
            modelFile.inputStream().use { input ->
                val bytesRead = input.read(header)
                if (bytesRead < 8) {
                    return@withContext ModelIntegrityResult(
                        isValid = false,
                        message = "Model file header corrupted",
                        details = "Could not read file header, bytes read: $bytesRead",
                        shouldReDownload = true
                    )
                }
                
                // Check if it looks like a valid TensorFlow Lite model
                val headerHex = header.take(8).joinToString(" ") { String.format("%02X", it) }
                Log.d(TAG, "Model file header: $headerHex")
                
                // TFLite files typically start with specific patterns
                val hasValidHeader = header[0] != 0.toByte() || header[1] != 0.toByte()
                
                if (!hasValidHeader) {
                    return@withContext ModelIntegrityResult(
                        isValid = false,
                        message = "Model file appears corrupted",
                        details = "Invalid file header: $headerHex",
                        shouldReDownload = true
                    )
                }
            }
        } catch (e: Exception) {
            return@withContext ModelIntegrityResult(
                isValid = false,
                message = "Cannot read model file",
                details = "Read error: ${e.message}",
                shouldReDownload = true
            )
        }
        
        // Test basic file operations
        try {
            val canRead = modelFile.canRead()
            val lastModified = modelFile.lastModified()
            
            if (!canRead) {
                return@withContext ModelIntegrityResult(
                    isValid = false,
                    message = "Model file not readable",
                    details = "File permissions issue at ${modelFile.absolutePath}",
                    shouldReDownload = false
                )
            }
            
            return@withContext ModelIntegrityResult(
                isValid = true,
                message = "Model file appears valid",
                details = "Size: ${formatFileSize(fileSize)}, Last modified: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(lastModified))}"
            )
            
        } catch (e: Exception) {
            return@withContext ModelIntegrityResult(
                isValid = false,
                message = "Model file access error",
                details = "Error: ${e.message}",
                shouldReDownload = true
            )
        }
    }
    
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
     * Smart retry that continues from the failed step instead of starting over
     */
    suspend fun retryFromFailedStep(): Boolean = withContext(Dispatchers.IO) {
        val currentProgress = _initializationProgress.value
        val failedStep = currentProgress.failedStep ?: ModelStatus.NOT_INITIALIZED
        
        Log.d(TAG, "🔄 Smart retry from failed step: $failedStep")
        
        try {
            when (failedStep) {
                ModelStatus.CHECKING_MODEL, ModelStatus.DOWNLOADING_MODEL, ModelStatus.EXTRACTING_MODEL -> {
                    // If download/extraction failed, try to get model file again
                    Log.d(TAG, "🔄 Retrying model acquisition from step: $failedStep")
                    
                    _initializationProgress.value = ModelInitializationProgress(
                        status = ModelStatus.CHECKING_MODEL,
                        message = "Retrying model setup...",
                        progress = 0.1f
                    )
                    
                    val modelFile = getModelFileOnce()
                    if (modelFile == null) {
                        Log.e(TAG, "❌ Model file still not available after retry")
                        _initializationProgress.value = ModelInitializationProgress(
                            status = ModelStatus.ERROR,
                            message = "Model setup failed after retry",
                            progress = 0f,
                            error = "Could not acquire model file after retry",
                            failedStep = ModelStatus.CHECKING_MODEL
                        )
                        return@withContext false
                    }
                    
                    cachedModelFilePath = modelFile.absolutePath
                    Log.d(TAG, "✅ Model file acquired successfully: ${modelFile.absolutePath}")
                    
                    // Continue with inference initialization
                    return@withContext initializeInferenceAndSession(modelFile)
                }
                
                ModelStatus.INITIALIZING_INFERENCE, ModelStatus.CREATING_SESSION -> {
                    // If inference/session creation failed, retry from there
                    Log.d(TAG, "🔄 Retrying inference initialization from step: $failedStep")
                    
                    val modelPath = cachedModelFilePath
                    if (modelPath == null) {
                        Log.e(TAG, "❌ No cached model path for inference retry")
                        return@withContext retryFromFailedStep() // Fall back to full retry
                    }
                    
                    val modelFile = File(modelPath)
                    if (!modelFile.exists()) {
                        Log.e(TAG, "❌ Cached model file no longer exists: $modelPath")
                        return@withContext retryFromFailedStep() // Fall back to full retry
                    }
                    
                    return@withContext initializeInferenceAndSession(modelFile)
                }
                
                ModelStatus.WARMING_UP -> {
                    // If warmup failed, just retry warmup
                    Log.d(TAG, "🔄 Retrying model warmup")
                    
                    _initializationProgress.value = ModelInitializationProgress(
                        status = ModelStatus.WARMING_UP,
                        message = "Retrying model warmup...",
                        progress = 0.9f
                    )
                    
                    try {
                        warmupModel()
                        _initializationProgress.value = ModelInitializationProgress(
                            status = ModelStatus.READY,
                            message = "AI model ready!",
                            progress = 1.0f
                        )
                        return@withContext true
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Warmup retry failed: ${e.message}")
                        _initializationProgress.value = ModelInitializationProgress(
                            status = ModelStatus.ERROR,
                            message = "Model warmup failed after retry",
                            progress = 0f,
                            error = e.message,
                            failedStep = ModelStatus.WARMING_UP
                        )
                        return@withContext false
                    }
                }
                
                else -> {
                    // For other cases, do full initialization
                    Log.d(TAG, "🔄 Doing full initialization retry")
                    return@withContext initializeModelOnce()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Smart retry failed: ${e.message}")
            _initializationProgress.value = ModelInitializationProgress(
                status = ModelStatus.ERROR,
                message = "Retry failed",
                progress = 0f,
                error = e.message,
                failedStep = failedStep
            )
            return@withContext false
        }
    }
    
    /**
     * Initialize inference and session (extracted for reuse in retry logic)
     */
    private suspend fun initializeInferenceAndSession(modelFile: File): Boolean {
        try {
            // Step 2: Initialize LLM Inference
            _initializationProgress.value = ModelInitializationProgress(
                status = ModelStatus.INITIALIZING_INFERENCE,
                message = "Initializing AI inference engine...",
                progress = 0.5f
            )
            
            val inferenceStartTime = System.currentTimeMillis()
            Log.d(TAG, "🔧 Creating LLM Inference instance...")
            
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(4096)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .setMaxNumImages(1)
                .build()
            
            llmInference = LlmInference.createFromOptions(context, options)
            val inferenceTime = System.currentTimeMillis() - inferenceStartTime
            Log.d(TAG, "✅ LLM Inference created in ${inferenceTime}ms")
            
            // Step 3: Create session
            _initializationProgress.value = ModelInitializationProgress(
                status = ModelStatus.CREATING_SESSION,
                message = "Creating AI session...",
                progress = 0.7f
            )
            
            val sessionStartTime = System.currentTimeMillis()
            Log.d(TAG, "🔧 Creating LLM Session...")
            
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTemperature(0.2f)
                .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
                .build()
            
            llmSession = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
            val sessionTime = System.currentTimeMillis() - sessionStartTime
            Log.d(TAG, "✅ LLM Session created in ${sessionTime}ms")
            
            isModelInitialized = true
            
            // Step 4: Warmup model
            _initializationProgress.value = ModelInitializationProgress(
                status = ModelStatus.WARMING_UP,
                message = "Warming up AI model...",
                progress = 0.9f
            )
            
            val warmupStartTime = System.currentTimeMillis()
            Log.d(TAG, "🔥 Warming up model...")
            
            try {
                warmupModel()
                val warmupTime = System.currentTimeMillis() - warmupStartTime
                Log.d(TAG, "✅ Model warmed up in ${warmupTime}ms")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Warmup failed but continuing: ${e.message}")
            }
            
            // Step 5: Ready!
            _initializationProgress.value = ModelInitializationProgress(
                status = ModelStatus.READY,
                message = "AI model ready!",
                progress = 1.0f
            )
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Inference/Session initialization failed: ${e.message}")
            val failedStep = if (llmInference == null) {
                ModelStatus.INITIALIZING_INFERENCE
            } else {
                ModelStatus.CREATING_SESSION
            }
            
            _initializationProgress.value = ModelInitializationProgress(
                status = ModelStatus.ERROR,
                message = "AI engine initialization failed",
                progress = 0f,
                error = e.message,
                failedStep = failedStep
            )
            return false
        }
    }
    
    /**
     * Initialize model ONCE during app startup - called from Application class
     */
    suspend fun initializeModelOnce(): Boolean = withContext(Dispatchers.IO) {
        // Use mutex to ensure only one initialization happens at a time
        initializationMutex.withLock {
            // CENTRAL COORDINATOR: Check if initialization is already completed
            if (initializationCompleted && isModelInitialized && llmSession != null) {
                Log.d(TAG, "⚡ Model already initialized, skipping...")
                _initializationProgress.value = ModelInitializationProgress(
                    status = ModelStatus.READY,
                    message = "Model ready",
                    progress = 1.0f
                )
                return@withContext true
            }
            
            // CENTRAL COORDINATOR: Check if initialization is currently in progress
            if (initializationInProgress) {
                Log.d(TAG, "⏳ Model initialization already in progress, waiting...")
                // Return current status instead of starting another initialization
                val currentStatus = _initializationProgress.value.status
                return@withContext (currentStatus == ModelStatus.READY)
            }
            
            // CENTRAL COORDINATOR: Mark initialization as in progress
            initializationInProgress = true
            Log.d(TAG, "🚀 Starting model initialization (coordinator)...")
            val startTime = System.currentTimeMillis()
        
        try {
            // Step 1: Check for model file
            val targetModelFile = getModelFilePath()
            _initializationProgress.value = ModelInitializationProgress(
                status = ModelStatus.CHECKING_MODEL,
                message = "Checking for AI model...\nLooking in: ${targetModelFile.absolutePath}",
                progress = 0.1f,
                currentPath = targetModelFile.absolutePath
            )
            
            val modelFile = getModelFileOnce()
            if (modelFile == null) {
                Log.e(TAG, "❌ Model file not found")
                _initializationProgress.value = ModelInitializationProgress(
                    status = ModelStatus.ERROR,
                    message = "Model file not found",
                    progress = 0f,
                    error = "Unable to locate or download the AI model"
                )
                return@withContext false
            }
            
            Log.d(TAG, "📁 Model file ready: ${modelFile.absolutePath}")
            cachedModelFilePath = modelFile.absolutePath
            
            // Step 2: Initialize LLM Inference with GPU Memory Management
            _initializationProgress.value = ModelInitializationProgress(
                status = ModelStatus.INITIALIZING_INFERENCE,
                message = "Optimizing GPU memory and initializing AI engine...",
                progress = 0.5f
            )
            
            // Aggressive memory cleanup before model loading
            performAggressiveMemoryCleanup()
            
            // Check thermal state and wait if device is overheating
            checkAndHandleThermalState()
            
            val inferenceStartTime = System.currentTimeMillis()
            Log.d(TAG, "🔧 Creating LLM Inference with GPU optimization...")
            
            // Try progressive loading strategies for GPU memory optimization
            llmInference = createInferenceWithGPUOptimization(modelFile.absolutePath)
            val inferenceTime = System.currentTimeMillis() - inferenceStartTime
            Log.d(TAG, "✅ LLM Inference created in ${inferenceTime}ms")
            
            // Step 3: Create session with timeout and optimization
            _initializationProgress.value = ModelInitializationProgress(
                status = ModelStatus.CREATING_SESSION,
                message = "Creating optimized AI session...",
                progress = 0.7f
            )
            
            val sessionStartTime = System.currentTimeMillis()
            Log.d(TAG, "🔧 Creating LLM Session with optimization...")
            
            llmSession = try {
                withTimeout(30000L) { // 30 second timeout for session creation
                    val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTopK(40)
                        .setTemperature(0.2f)
                        .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
                        .build()
                    
                    LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "❌ Session creation timed out - likely GPU memory issue")
                throw RuntimeException("Session creation timed out. GPU may be under memory pressure.", e)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Session creation failed: ${e.message}")
                performAggressiveMemoryCleanup()
                throw e
            }
            
            val sessionTime = System.currentTimeMillis() - sessionStartTime
            Log.d(TAG, "✅ LLM Session created in ${sessionTime}ms")
            
            isModelInitialized = true
            
            // Step 4: Warmup model
            _initializationProgress.value = ModelInitializationProgress(
                status = ModelStatus.WARMING_UP,
                message = "Warming up AI model...",
                progress = 0.9f
            )
            
            val warmupStartTime = System.currentTimeMillis()
            Log.d(TAG, "🔥 Warming up model...")
            
            try {
                warmupModel()
                val warmupTime = System.currentTimeMillis() - warmupStartTime
                Log.d(TAG, "✅ Model warmed up in ${warmupTime}ms")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Warmup failed but continuing: ${e.message}")
            }
            
            // Step 5: Ready!
            _initializationProgress.value = ModelInitializationProgress(
                status = ModelStatus.READY,
                message = "AI model ready!",
                progress = 1.0f
            )
            
            // CENTRAL COORDINATOR: Mark initialization as completed
            initializationCompleted = true
            initializationInProgress = false
            
            val totalTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "🎉 Model fully initialized and ready in ${totalTime}ms")
            
            return@withContext true
            
        } catch (e: Exception) {
            val totalTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "💥 Model initialization failed after ${totalTime}ms: ${e.message}")
            
            // CENTRAL COORDINATOR: Reset flags on error
            initializationInProgress = false
            initializationCompleted = false
            
            _initializationProgress.value = ModelInitializationProgress(
                status = ModelStatus.ERROR,
                message = "Model initialization failed",
                progress = 0f,
                error = e.message
            )
            return@withContext false
        }
        } // End of initializationMutex.withLock
    }
    
    /**
     * Get the target model file path using app-specific external Downloads folder
     */
    private fun getModelFilePath(): File {
        val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(null) // Fallback to app external storage root
            ?: context.filesDir // Final fallback to internal storage
            
        return File(downloadsDir, MODEL_FILENAME)
    }
    
    /**
     * Get model file ONCE - no repeated file checks
     */
    private suspend fun getModelFileOnce(): File? {
        // First check app-specific Downloads folder (new location)
        val downloadsModelFile = getModelFilePath()
        if (downloadsModelFile.exists() && downloadsModelFile.length() > 0) {
            Log.d(TAG, "📁 Using Downloads model file: ${downloadsModelFile.absolutePath}")
            return downloadsModelFile
        }
        
        // Check legacy internal storage location for backwards compatibility
        val internalModelFile = File(context.filesDir, MODEL_FILENAME)
        if (internalModelFile.exists() && internalModelFile.length() > 0) {
            Log.d(TAG, "📱 Using legacy internal model file: ${internalModelFile.absolutePath}")
            return internalModelFile
        }
        
        Log.d(TAG, "🔄 No model found, checking external sources...")
        
        // Update progress to downloading
        val targetModelFile = getModelFilePath()
        _initializationProgress.value = ModelInitializationProgress(
            status = ModelStatus.DOWNLOADING_MODEL,
            message = "Setting up AI model...\nTarget: ${targetModelFile.absolutePath}",
            progress = 0.2f,
            currentPath = targetModelFile.absolutePath
        )
        
        // Download/copy model from external sources (only if needed)
        var modelFile: File? = null
        var lastProgress = 0
        modelDownloadService.downloadModel().collect { result ->
            when (result) {
                is ModelDownloadService.DownloadResult.Success -> {
                    modelFile = result.file
                    Log.d(TAG, "✅ Model ready: ${result.file.absolutePath}")
                }
                is ModelDownloadService.DownloadResult.Error -> {
                    Log.e(TAG, "❌ Model download/copy failed: ${result.message}")
                    
                    // Determine which step failed based on the error context
                    val failedStep = when {
                        result.message.contains("extract", ignoreCase = true) -> ModelStatus.EXTRACTING_MODEL
                        result.message.contains("download", ignoreCase = true) -> ModelStatus.DOWNLOADING_MODEL
                        else -> ModelStatus.CHECKING_MODEL
                    }
                    
                    val errorMessage = buildString {
                        append(result.message)
                        appendLine()
                        appendLine()
                        append("Target location: ${getModelFilePath().absolutePath}")
                        
                        // Include file metadata if available
                        result.fileMetadata?.let { metadata ->
                            appendLine()
                            appendLine()
                            append(metadata)
                        }
                    }
                    
                    _initializationProgress.value = ModelInitializationProgress(
                        status = ModelStatus.ERROR,
                        message = "Model setup failed",
                        progress = 0f,
                        error = errorMessage,
                        failedStep = failedStep
                    )
                    return@collect
                }
                is ModelDownloadService.DownloadResult.Progress -> {
                    Log.d(TAG, "📥 Download progress: ${result.percentage}%")
                    if (result.percentage != lastProgress) {
                        val pathMessage = result.currentPath?.let { path ->
                            "\n$path"
                        } ?: ""
                        
                        // Handle extraction progress separately from download progress  
                        val isExtracting = result.currentPath?.contains("Extracting") == true ||
                                          result.currentPath?.contains("ZIP") == true ||
                                          result.currentPath?.contains("GZIP") == true ||
                                          result.currentPath?.contains("TAR_GZ") == true
                        
                        val statusMessage = if (isExtracting) {
                            "Extracting AI model... ${result.percentage}%$pathMessage"
                        } else {
                            "Downloading AI model... ${result.percentage}%$pathMessage"
                        }
                        
                        val status = if (isExtracting) {
                            ModelStatus.EXTRACTING_MODEL
                        } else {
                            ModelStatus.DOWNLOADING_MODEL
                        }
                        
                        val progressValue = if (status == ModelStatus.EXTRACTING_MODEL) {
                            0.35f + (result.percentage / 100f * 0.1f) // 35% to 45% for extraction
                        } else {
                            0.2f + (result.percentage / 100f * 0.15f) // 20% to 35% for download
                        }
                        
                        _initializationProgress.value = ModelInitializationProgress(
                            status = status,
                            message = statusMessage,
                            progress = progressValue,
                            currentPath = result.currentPath
                        )
                        lastProgress = result.percentage
                    }
                }
            }
        }
        
        return modelFile
    }
    
    /**
     * Warmup model with a simple prompt
     */
    private suspend fun warmupModel() {
        val session = llmSession ?: return
        
        suspendCancellableCoroutine { continuation ->
            val responseBuilder = StringBuilder()
            
            session.addQueryChunk("Hi")
            session.generateResponseAsync { partialResult, done ->
                responseBuilder.append(partialResult)
                if (done) {
                    Log.d(TAG, "🔥 Warmup response: ${responseBuilder.toString().take(50)}")
                    continuation.resume(Unit)
                }
            }
        }
    }
    
    /**
     * Check if model is ready - simple boolean check, no initialization
     */
    fun isModelReady(): Boolean = isModelInitialized && llmSession != null
    
    /**
     * Generate streaming response with both text and image (multimodal) - Perfect for problem solving!
     */
    suspend fun generateMultimodalResponse(prompt: String, imageBitmap: Bitmap): Flow<String> = callbackFlow {
        val session = llmSession
        if (!isModelInitialized || session == null) {
            Log.e(TAG, "❌ Model not ready for multimodal inference")
            trySend("Model not initialized. Please wait...")
            close()
            return@callbackFlow
        }
        
        val messageStartTime = System.currentTimeMillis()
        Log.d(TAG, "🎨 Starting multimodal AI streaming response generation")
        Log.d(TAG, "📝 Prompt: ${prompt.take(100)}${if (prompt.length > 100) "..." else ""}")
        Log.d(TAG, "🖼️ Image: ${imageBitmap.width}x${imageBitmap.height} (${imageBitmap.config})")
        
        try {
            // Convert Bitmap to MPImage for MediaPipe
            val mpImage: MPImage = BitmapImageBuilder(imageBitmap).build()
            Log.d(TAG, "✅ Image converted to MPImage successfully")
            Log.d(TAG, "📊 MPImage details: width=${mpImage.width}, height=${mpImage.height}")
            

            
            // Analyze bitmap pixels to detect blank issues
            val pixelAnalysis = analyzeBitmapPixels(imageBitmap)
            Log.d(TAG, "🔍 Original bitmap analysis: $pixelAnalysis")

            // Add text prompt
            session.addQueryChunk(prompt)
            Log.d(TAG, "📝 Text prompt added to session")
            
            // Add image (recommended by MediaPipe docs)
            session.addImage(mpImage)
            Log.d(TAG, "🖼️ Image added to session")
            
            var firstTokenTime = 0L
            var firstTokenReceived = false
            var currentResponse = ""
            
            // Generate response with streaming chunks
            session.generateResponseAsync { partialResult, done ->
                try {
                    if (!firstTokenReceived) {
                        firstTokenTime = System.currentTimeMillis()
                        val timeToFirstToken = firstTokenTime - messageStartTime
                        Log.d(TAG, "⚡ First multimodal token received in ${timeToFirstToken}ms")
                        firstTokenReceived = true
                    }
                    
                    // Emit each chunk as it arrives
                    if (partialResult.isNotEmpty()) {
                        currentResponse += partialResult
                        // Emit the current accumulated response for streaming effect
                        val sendResult = trySend(currentResponse)
                        if (sendResult.isSuccess) {
                            Log.d(TAG, "📤 Streamed chunk: +${partialResult.length} chars (total: ${currentResponse.length})")
                        } else {
                            Log.w(TAG, "⚠️ Failed to send chunk: ${sendResult.exceptionOrNull()?.message}")
                        }
                    }
                    
                    if (done) {
                        val totalTime = System.currentTimeMillis() - messageStartTime
                        val generationTime = if (firstTokenReceived) {
                            System.currentTimeMillis() - firstTokenTime
                        } else {
                            0
                        }
                        
                        Log.d(TAG, "🎉 Multimodal streaming response complete!")
                        Log.d(TAG, "📊 Stats: ${totalTime}ms total (${generationTime}ms generation)")
                        Log.d(TAG, "📏 Final response: ${currentResponse.length} chars")
                        Log.d(TAG, "🧠 Preview: ${currentResponse.take(150)}${if (currentResponse.length > 150) "..." else ""}")
                        
                        // Ensure final response is sent before closing
                        if (currentResponse.isNotEmpty()) {
                            trySend(currentResponse)
                        }
                        
                        // Close the flow when complete
                        Log.d(TAG, "🔚 Closing multimodal streaming flow")
                        close()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "💥 Error in multimodal streaming callback: ${e.message}")
                    trySend("Error processing multimodal response: ${e.message}")
                    close(e)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error setting up multimodal streaming: ${e.message}")
            trySend("Error setting up multimodal analysis: ${e.message}")
            close(e)
        }
        
        // Keep the flow open until the callback completes
        awaitClose {
            Log.d(TAG, "🔚 Multimodal streaming flow closed")
        }
        
    }.flowOn(Dispatchers.IO)
    
    /**
     * Generate response - optimized with detailed logging
     */
    suspend fun generateResponse(prompt: String): Flow<String> = flow {
        val session = llmSession
        if (!isModelInitialized || session == null) {
            Log.e(TAG, "❌ Model not ready for inference")
            emit("Model not initialized. Please wait...")
            return@flow
        }
        
        val messageStartTime = System.currentTimeMillis()
        Log.d(TAG, "🤖 Starting AI response generation for ${prompt.length} chars")
        
        try {
            val response = suspendCancellableCoroutine { continuation ->
                val responseBuilder = StringBuilder()
                var firstTokenTime = 0L
                var firstTokenReceived = false
                
                session.addQueryChunk(prompt)
                session.generateResponseAsync { partialResult, done ->
                    if (!firstTokenReceived) {
                        firstTokenTime = System.currentTimeMillis()
                        val timeToFirstToken = firstTokenTime - messageStartTime
                        Log.d(TAG, "⚡ First token received in ${timeToFirstToken}ms")
                        firstTokenReceived = true
                    }
                    
                    responseBuilder.append(partialResult)
                    
                    if (done) {
                        val fullResponse = responseBuilder.toString()
                        val totalTime = System.currentTimeMillis() - messageStartTime
                        val generationTime = System.currentTimeMillis() - firstTokenTime
                        
                        Log.d(TAG, "✅ Response complete: ${totalTime}ms total (${generationTime}ms generation), ${fullResponse.length} chars")
                        continuation.resume(fullResponse)
                    }
                }
            }
            
            emit(response)
            
        } catch (e: Exception) {
            val totalTime = System.currentTimeMillis() - messageStartTime
            Log.e(TAG, "💥 Response generation failed after ${totalTime}ms: ${e.message}")
            emit("Error generating response: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Analyze a bitmap image for the overlay feature
     */
    suspend fun analyzeImage(bitmap: android.graphics.Bitmap): String = requestMutex.withLock {
        if (!isModelInitialized || llmSession == null) {
            return@withLock "Model not initialized. Please wait..."
        }
        
        // Cancel any previous request
        currentRequestJob?.cancel()
        Log.d(TAG, "🔄 Cancelled previous AI request")
        
        // Create a new async job for this request
        val requestJob = CoroutineScope(Dispatchers.IO).async {
            try {
                Log.d(TAG, "🖼️ Analyzing overlay image: ${bitmap.width}x${bitmap.height}")
                
                // Use multimodal analysis with a specific prompt for educational content
                val prompt = "Analyze this image and provide a clear, educational explanation. If it contains text, summarize it. If it shows a diagram, explain what it represents. If it's a math problem, solve it step by step. If it's a concept, explain it in simple terms that a student would understand."
                
                // Reset the session to clear any previous state
                try {
                    llmSession?.close()
                    val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTopK(40)
                        .setTemperature(0.2f)
                        .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
                        .build()
                    llmSession = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
                    Log.d(TAG, "🔄 Reset LLM session for new request")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Could not reset session: ${e.message}")
                }
                
                // Use non-streaming approach to prevent repetition
                val response = suspendCancellableCoroutine { continuation ->
                val responseBuilder = StringBuilder()
                var isCompleted = false
                
                try {
                    // Convert Bitmap to MPImage for MediaPipe
                    val mpImage: MPImage = BitmapImageBuilder(bitmap).build()
                    Log.d(TAG, "✅ Image converted to MPImage successfully")
                    
                    // Add text prompt
                    llmSession?.addQueryChunk(prompt)
                    Log.d(TAG, "📝 Text prompt added to session")
                    
                    // Add image
                    llmSession?.addImage(mpImage)
                    Log.d(TAG, "🖼️ Image added to session")
                    
                    // Generate response with streaming chunks
                    llmSession?.generateResponseAsync { partialResult, done ->
                        try {
                            if (!isCompleted) {
                                responseBuilder.append(partialResult)
                                
                                if (done) {
                                    isCompleted = true
                                    val fullResponse = responseBuilder.toString()
                                    Log.d(TAG, "🔍 Overlay analysis response: $fullResponse")
                                    continuation.resume(fullResponse)
                                }
                            }
                        } catch (e: Exception) {
                            if (!isCompleted) {
                                isCompleted = true
                                Log.e(TAG, "💥 Error in overlay analysis callback: ${e.message}")
                                continuation.resumeWithException(e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!isCompleted) {
                        isCompleted = true
                        Log.e(TAG, "💥 Error setting up overlay analysis: ${e.message}")
                        continuation.resumeWithException(e)
                    }
                }
                }
                
                val result = if (response.isNotBlank()) {
                    response
                } else {
                    "I couldn't analyze this image. Please try selecting a clearer area with text or diagrams."
                }
                
                Log.d(TAG, "✅ Overlay analysis complete: ${result.length} chars")
                result
                
            } catch (e: Exception) {
                Log.e(TAG, "💥 Error analyzing overlay image: ${e.message}")
                "Error analyzing image: ${e.message}"
            }
        }
        
        // Store the current request job
        currentRequestJob = requestJob
        
        // Wait for the request to complete and return the result
        val result = requestJob.await()
        
        // Clear the current request job after completion
        if (currentRequestJob == requestJob) {
            currentRequestJob = null
        }
        
        return@withLock result
    }
    
    /**
     * Analyze a bitmap image for the overlay feature with streaming response
     */
    suspend fun analyzeImageStreaming(bitmap: android.graphics.Bitmap): Flow<String> = requestMutex.withLock {
        if (!isModelInitialized || llmSession == null) {
            return@withLock flow { emit("Model not initialized. Please wait...") }
        }
        
        // Cancel any previous request
        currentRequestJob?.cancel()
        Log.d(TAG, "🔄 Cancelled previous AI request")
        
        try {
            Log.d(TAG, "🖼️ Analyzing overlay image with streaming: ${bitmap.width}x${bitmap.height}")
            
            // Use multimodal analysis with a specific prompt for educational content
            val prompt = """
           You are an “Physics and Math Expert tutor”, that receives a image which can contain:
           1. A math or physics problem
           2. A concept or idea 
           3. A diagram (hand-drawn or not)
           4. A text
           5. A combination of the above
           
           *Your ultimate goal is to provide a clear, easy to understand and concise explanation of the image in layman terms.*
           
           ###  STEPS TO FOLLOW  ###
           1. Analyze the given image carefully and understand the content thoroughly.
           2. Understand each part of image very carefully such as text, diagram, concept, etc.
           3. Formulate a clear, concise and easy to understand explanation of the image in layman terms.
           4. You must read your answer internally and determine if it is simple and easy enough for kid below age of 16?.
           5. If not, then again follow step 1-4 and try to make answer more simple, add real world examples in each iteration.
           6. Finally, provide the answer in a clear, concise and easy to understand format.

           ###  OUTPUT FORMAT  ###
           1. Use proper formatting such as heading, list, bold text, etc.
           2. Provide clear, concise and easy to understand explanation of the image in layman terms.
           3. Use proper markdown formatting and spacing.
           4. Never output your though process, prompt used, or any other information that is not part of the answer.
           5. Do not output any image/video or their path in answer. If question contains image/video, 
           then you can refer to it in your answer but do not output their path/url/source.
        """.trimIndent()
            
            // Reset the session to clear any previous state
            try {
                llmSession?.close()
                val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTemperature(0.2f)
                    .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
                    .build()
                llmSession = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
                Log.d(TAG, "🔄 Reset LLM session for new streaming request")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Could not reset session: ${e.message}")
            }
            
            // Use streaming approach for real-time response
            generateMultimodalResponse(prompt, bitmap)
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error analyzing overlay image with streaming: ${e.message}")
            flow { emit("Error analyzing image: ${e.message}") }
        } finally {
            // Clear the current request job after completion
            currentRequestJob = null
        }
    }
    
    /**
     * Convert Android Bitmap to MediaPipe MPImage
     */
    private fun convertBitmapToMPImage(bitmap: android.graphics.Bitmap): MPImage {
        return BitmapImageBuilder(bitmap).build()
    }
    
    /**
     * Perform aggressive memory cleanup before model loading
     */
    private fun performAggressiveMemoryCleanup() {
        Log.d(TAG, "🧹 Performing aggressive memory cleanup...")
        
        // Force garbage collection multiple times
        repeat(3) {
            System.gc()
            System.runFinalization()
            Thread.sleep(100) // Give GC time to work
        }
        
        // Log memory status
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val availableMemory = maxMemory - usedMemory
        
        Log.d(TAG, "📊 Memory status after cleanup:")
        Log.d(TAG, "   Used: ${usedMemory / 1024 / 1024}MB")
        Log.d(TAG, "   Available: ${availableMemory / 1024 / 1024}MB")
        Log.d(TAG, "   Max: ${maxMemory / 1024 / 1024}MB")
        
        // Clean up any previous model instances
        try {
            llmSession?.close()
            llmInference?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Warning during cleanup: ${e.message}")
        }
        
        llmSession = null
        llmInference = null
        
        // Final GC after cleanup
        System.gc()
        Thread.sleep(200)
        
        Log.d(TAG, "✅ Memory cleanup completed")
    }
    
    /**
     * Check device thermal state and handle overheating
     */
    private suspend fun checkAndHandleThermalState() {
        try {
            // Get thermal status if available (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val thermalManager = powerManager as? PowerManager
                val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    powerManager.currentThermalStatus
                } else {
                    PowerManager.THERMAL_STATUS_NONE
                }
                
                Log.d(TAG, "🌡️ Thermal status: $thermalStatus")
                
                when (thermalStatus) {
                    PowerManager.THERMAL_STATUS_SEVERE,
                    PowerManager.THERMAL_STATUS_CRITICAL,
                    PowerManager.THERMAL_STATUS_EMERGENCY,
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> {
                        Log.w(TAG, "⚠️ Device overheating! Waiting 10 seconds to cool down...")
                        _initializationProgress.value = ModelInitializationProgress(
                            status = ModelStatus.INITIALIZING_INFERENCE,
                            message = "Device overheating. Waiting to cool down...",
                            progress = 0.45f
                        )
                        delay(10000) // Wait 10 seconds
                    }
                    PowerManager.THERMAL_STATUS_MODERATE -> {
                        Log.w(TAG, "🔥 Device warm. Waiting 5 seconds...")
                        delay(5000) // Wait 5 seconds
                    }
                    else -> {
                        Log.d(TAG, "❄️ Thermal state OK")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not check thermal state: ${e.message}")
        }
    }
    
    /**
     * Create LLM Inference with progressive GPU optimization strategies
     */
    private suspend fun createInferenceWithGPUOptimization(modelPath: String): LlmInference {
        // Check available memory before attempting
        val runtime = Runtime.getRuntime()
        val availableMemory = (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())) / 1024 / 1024
        Log.d(TAG, "💾 Available memory: ${availableMemory}MB")
        
        val strategies = listOf(
            // Strategy 1: Higher capacity (only for high-memory devices)
            InferenceStrategy(
                name = "Optimized GPU",
                maxTokens = 3800,
                backend = LlmInference.Backend.GPU,
                maxImages = 1,
                timeout = 60000L
            ),
            // Strategy 2: Moderate GPU settings (only if enough memory)
            InferenceStrategy(
                name = "Moderate GPU", 
                maxTokens = 2048,
                backend = LlmInference.Backend.GPU,
                maxImages = 1,
                timeout = 45000L
            ),
            // Strategy 3: Conservative GPU settings (most likely to work)
            InferenceStrategy(
                name = "Conservative GPU",
                maxTokens = 1024,
                backend = LlmInference.Backend.GPU,
                maxImages = 1,
                timeout = 30000L
            )
        )
        
        for ((index, strategy) in strategies.withIndex()) {
            try {
                Log.d(TAG, "🎯 Trying strategy ${index + 1}: ${strategy.name}")
                Log.d(TAG, "   Max tokens: ${strategy.maxTokens}")
                Log.d(TAG, "   Backend: ${strategy.backend}")
                Log.d(TAG, "   Timeout: ${strategy.timeout}ms")
                
                                 _initializationProgress.value = ModelInitializationProgress(
                     status = ModelStatus.INITIALIZING_INFERENCE,
                     message = "Trying ${strategy.name} (${strategy.maxTokens} tokens)...",
                     progress = 0.5f + (index * 0.05f)
                 )
                 
                 // Monitor memory pressure during inference creation
                 val memoryMonitorJob = CoroutineScope(Dispatchers.IO).launch {
                     repeat(strategy.timeout.toInt() / 1000) {
                         delay(1000)
                         val currentRuntime = Runtime.getRuntime()
                         val currentAvailable = (currentRuntime.maxMemory() - (currentRuntime.totalMemory() - currentRuntime.freeMemory())) / 1024 / 1024
                         if (currentAvailable < 200) { // Less than 200MB available
                             Log.w(TAG, "⚠️ Low memory detected: ${currentAvailable}MB available")
                             performAggressiveMemoryCleanup()
                         }
                     }
                 }
                 
                 val inference = try {
                     withTimeout(strategy.timeout) {
                         Log.d(TAG, "🔧 Creating inference with ${strategy.maxTokens} max tokens...")
                         val options = LlmInference.LlmInferenceOptions.builder()
                             .setModelPath(modelPath)
                             .setMaxTokens(strategy.maxTokens)
                             .setPreferredBackend(strategy.backend)
                             .setMaxNumImages(strategy.maxImages)
                             .build()
                         
                         LlmInference.createFromOptions(context, options)
                     }
                 } finally {
                     memoryMonitorJob.cancel()
                 }
                
                Log.d(TAG, "✅ Successfully created inference with ${strategy.name}")
                return inference
                
                         } catch (e: TimeoutCancellationException) {
                 Log.w(TAG, "⏰ Strategy ${strategy.name} timed out after ${strategy.timeout}ms")
                 Log.w(TAG, "   This usually indicates GPU memory pressure or thermal throttling")
                 performAggressiveMemoryCleanup()
             } catch (e: Exception) {
                 Log.w(TAG, "❌ Strategy ${strategy.name} failed: ${e.message}")
                 Log.w(TAG, "   Error type: ${e.javaClass.simpleName}")
                 
                 // Provide specific error guidance
                 when {
                     e.message?.contains("GPU", ignoreCase = true) == true -> {
                         Log.w(TAG, "   💡 GPU-related error - likely insufficient GPU memory")
                     }
                     e.message?.contains("memory", ignoreCase = true) == true -> {
                         Log.w(TAG, "   💡 Memory-related error - performing extra cleanup")
                         repeat(2) { performAggressiveMemoryCleanup() }
                     }
                     e.message?.contains("timeout", ignoreCase = true) == true -> {
                         Log.w(TAG, "   💡 Timeout error - device may be thermal throttling")
                     }
                 }
                 
                 performAggressiveMemoryCleanup()
                 
                 if (index == strategies.size - 1) {
                     // Last strategy failed, provide comprehensive error info
                     val errorInfo = buildString {
                         appendLine("All GPU optimization strategies failed!")
                         appendLine("Device Info:")
                         appendLine("  Available RAM: ${availableMemory}MB")
                         appendLine("  Android Version: ${android.os.Build.VERSION.SDK_INT}")
                         appendLine("  Device Model: ${android.os.Build.MODEL}")
                         appendLine("Last Error: ${e.message}")
                         appendLine("Suggestions:")
                         appendLine("  - Device may have insufficient GPU memory")
                         appendLine("  - Try closing other apps to free memory")
                         appendLine("  - Restart device if thermal throttling occurred")
                     }
                     Log.e(TAG, errorInfo)
                     throw RuntimeException(errorInfo, e)
                 }
             }
            
            // Wait between attempts to let GPU memory settle and check thermal state
            Log.d(TAG, "⏳ Waiting 3 seconds before next strategy...")
            delay(3000)
            checkAndHandleThermalState()
        }
        
        throw RuntimeException("All GPU optimization strategies failed")
    }
    
    /**
     * Conservative warmup to reduce GPU memory pressure
     */
    private suspend fun warmupModelConservatively() {
        Log.d(TAG, "🔥 Starting conservative model warmup...")
        try {
            // Use a very simple prompt to warm up the model with minimal GPU load
            val simplePrompt = "Hi"
            llmSession?.addQueryChunk(simplePrompt)
            
            // Generate a small response to warm up the GPU pipeline
            llmSession?.generateResponseAsync { result, done ->
                if (done) {
                    Log.d(TAG, "✅ Conservative warmup completed")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Conservative warmup encountered issue: ${e.message}")
            throw e
        }
    }
    
    /**
     * Data class for inference strategies
     */
    private data class InferenceStrategy(
        val name: String,
        val maxTokens: Int,
        val backend: LlmInference.Backend,
        val maxImages: Int,
        val timeout: Long
    )
    
    fun cleanup() {
        try {
            llmSession?.close()
            llmInference?.close()
            isModelInitialized = false
            Log.d(TAG, "🧹 Cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Cleanup failed: ${e.message}")
        }
    }
    

    
    /**
     * Analyze bitmap pixels to detect if it's blank
     */
    private fun analyzeBitmapPixels(bitmap: android.graphics.Bitmap): String {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        
        var whitePixels = 0
        var nonWhitePixels = 0
        var transparentPixels = 0
        
        // Sample pixels (check every 10th pixel to avoid performance issues)
        for (y in 0 until height step 10) {
            for (x in 0 until width step 10) {
                val pixel = bitmap.getPixel(x, y)
                when {
                    pixel == android.graphics.Color.WHITE -> whitePixels++
                    pixel == android.graphics.Color.TRANSPARENT -> transparentPixels++
                    else -> nonWhitePixels++
                }
            }
        }
        
        val sampledPixels = whitePixels + nonWhitePixels + transparentPixels
        
        return "sampled:$sampledPixels, white:$whitePixels, nonWhite:$nonWhitePixels, transparent:$transparentPixels"
    }
} 