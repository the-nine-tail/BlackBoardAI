package com.example.blackboardai.data.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class GoogleAIService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloadService: ModelDownloadService
) {
    companion object {
        private const val TAG = "[BlackBoardAI Log]"
        private const val MODEL_FILENAME = "gemma-3n-e4b-it.task"
        private const val GPU_TIMEOUT_MS = 60_000L // 60 seconds timeout for GPU
        private const val TOTAL_TIMEOUT_MS = 90_000L // 90 seconds total timeout
    }
    
    // Singleton pattern - these should NEVER be reset after initialization
    private var llmInference: LlmInference? = null
    private var llmSession: LlmInferenceSession? = null
    private var isModelInitialized = false
    private var cachedModelFilePath: String? = null
    private var isUsingGPU = false
    
    /**
     * Initialize model ONCE during app startup - called from Application class
     */
    suspend fun initializeModelOnce(): Boolean = withContext(Dispatchers.IO) {
        if (isModelInitialized && llmSession != null) {
            Log.d(TAG, "⚡ Model already initialized, skipping...")
            return@withContext true
        }
        
        Log.d(TAG, "🚀 Starting model initialization with performance profiling...")
        val startTime = System.currentTimeMillis()
        
        try {
            // Add total timeout to prevent hanging
            withTimeout(TOTAL_TIMEOUT_MS) {
                
                // Step 1: Get model file with timing
                val fileStartTime = System.currentTimeMillis()
                val modelFile = getModelFileOnce()
                val fileTime = System.currentTimeMillis() - fileStartTime
                
                if (modelFile == null) {
                    Log.e(TAG, "❌ Model file not found after ${fileTime}ms")
                    return@withTimeout false
                }
                
                // Log model file details
                val modelSizeMB = modelFile.length() / (1024 * 1024)
                Log.d(TAG, "📁 Model file ready in ${fileTime}ms: ${modelFile.absolutePath}")
                Log.d(TAG, "📏 Model size: ${modelSizeMB}MB (${modelFile.length()} bytes)")
                
                // Log memory status
                logMemoryStatus("Before model loading")
                
                cachedModelFilePath = modelFile.absolutePath
                
                // Step 2: Initialize LLM Inference with GPU acceleration and timeout
                val inferenceStartTime = System.currentTimeMillis()
                Log.d(TAG, "🔧 Creating LLM Inference instance with GPU acceleration (${GPU_TIMEOUT_MS/1000}s timeout)...")
                
                val (llmInferenceInstance, usingGPU) = createLlmInferenceWithTimeout(modelFile.absolutePath)
                llmInference = llmInferenceInstance
                isUsingGPU = usingGPU
                
                val inferenceTime = System.currentTimeMillis() - inferenceStartTime
                Log.d(TAG, "✅ LLM Inference created in ${inferenceTime}ms (GPU: $isUsingGPU)")
                
                // Log memory status after model loading
                logMemoryStatus("After model loading")
                
                // Step 3: Create session with timing
                val sessionStartTime = System.currentTimeMillis()
                Log.d(TAG, "🔧 Creating LLM Session...")
                
                val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTemperature(0.8f)
                    .build()
                
                llmSession = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
                val sessionTime = System.currentTimeMillis() - sessionStartTime
                Log.d(TAG, "✅ LLM Session created in ${sessionTime}ms")
                
                isModelInitialized = true
                
                // Step 4: Skip warmup to save time - we'll warm up on first use
                Log.d(TAG, "⏭️ Skipping warmup to reduce initialization time")
                
                val totalTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "🎉 Model fully initialized in ${totalTime}ms (${if (isUsingGPU) "GPU" else "CPU"} accelerated)")
                
                // Final memory status
                logMemoryStatus("After initialization")
                
                return@withTimeout true
            }
            
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            val totalTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "⏰ Model initialization TIMED OUT after ${totalTime}ms (limit: ${TOTAL_TIMEOUT_MS}ms)")
            Log.e(TAG, "🔄 Falling back to CPU-only initialization...")
            
            // Emergency fallback to CPU only
            return@withContext initializeCPUOnly()
            
        } catch (e: Exception) {
            val totalTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "💥 Model initialization failed after ${totalTime}ms: ${e.message}")
            return@withContext false
        }
    }
    
    /**
     * Emergency CPU-only initialization
     */
    private suspend fun initializeCPUOnly(): Boolean {
        try {
            Log.d(TAG, "🆘 Emergency CPU-only initialization...")
            val startTime = System.currentTimeMillis()
            
            val modelFile = File(context.filesDir, MODEL_FILENAME)
            if (!modelFile.exists()) {
                Log.e(TAG, "❌ Model file not found for emergency initialization")
                return false
            }
            
            val cpuOptions = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024) // Reduced tokens for faster initialization
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()
            
            llmInference = LlmInference.createFromOptions(context, cpuOptions)
            isUsingGPU = false
            
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTemperature(0.8f)
                .build()
            
            llmSession = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
            isModelInitialized = true
            
            val totalTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "🆘 ✅ Emergency CPU initialization completed in ${totalTime}ms")
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Emergency CPU initialization also failed: ${e.message}")
            return false
        }
    }
    
    /**
     * Create LLM Inference with GPU acceleration and timeout, fallback to CPU if GPU times out
     */
    private suspend fun createLlmInferenceWithTimeout(modelPath: String): Pair<LlmInference, Boolean> {
        // Try GPU acceleration with timeout
        var gpuInference: LlmInference? = null
        var gpuSuccess = false
        
        try {
            Log.d(TAG, "🎮 Attempting GPU acceleration with ${GPU_TIMEOUT_MS/1000}s timeout...")
            val gpuStartTime = System.currentTimeMillis()
            
            gpuInference = withTimeout(GPU_TIMEOUT_MS) {
                val gpuOptions = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(2048)
                    .setPreferredBackend(LlmInference.Backend.GPU)
                    .build()
                
                Log.d(TAG, "🎮 Creating GPU inference instance...")
                LlmInference.createFromOptions(context, gpuOptions)
            }
            
            val gpuTime = System.currentTimeMillis() - gpuStartTime
            Log.d(TAG, "🎮 ✅ GPU acceleration enabled in ${gpuTime}ms! Expected 5-25x speed boost")
            gpuSuccess = true
            
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "🎮 ⏰ GPU acceleration TIMED OUT after ${GPU_TIMEOUT_MS}ms, falling back to CPU")
            Log.w(TAG, "🎮 💡 GPU timeout suggests model compilation or memory issues")
        } catch (e: Exception) {
            Log.w(TAG, "🎮 ⚠️ GPU acceleration failed: ${e.message}")
            Log.w(TAG, "🎮 📱 Device GPU might not be compatible with MediaPipe LLM")
        }
        
        if (gpuSuccess && gpuInference != null) {
            return Pair(gpuInference, true)
        }
        
        // Fallback to CPU
        try {
            Log.d(TAG, "🔧 Using CPU acceleration as fallback...")
            val cpuStartTime = System.currentTimeMillis()
            
            val cpuOptions = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(2048)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()
            
            val cpuInference = LlmInference.createFromOptions(context, cpuOptions)
            val cpuTime = System.currentTimeMillis() - cpuStartTime
            Log.d(TAG, "🔧 ✅ CPU acceleration enabled in ${cpuTime}ms")
            return Pair(cpuInference, false)
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Both GPU and CPU initialization failed: ${e.message}")
            throw e
        }
    }
    
    /**
     * Log memory status for debugging
     */
    private fun logMemoryStatus(phase: String) {
        try {
            val runtime = Runtime.getRuntime()
            val usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            val totalMemoryMB = runtime.totalMemory() / (1024 * 1024)
            val maxMemoryMB = runtime.maxMemory() / (1024 * 1024)
            
            Log.d(TAG, "🧠 Memory $phase: ${usedMemoryMB}MB used / ${totalMemoryMB}MB total / ${maxMemoryMB}MB max")
            
            if (usedMemoryMB > maxMemoryMB * 0.8) {
                Log.w(TAG, "⚠️ High memory usage detected: ${usedMemoryMB}MB (${(usedMemoryMB * 100 / maxMemoryMB)}%)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log memory status: ${e.message}")
        }
    }
    
    /**
     * Get model file ONCE - no repeated file checks
     */
    private suspend fun getModelFileOnce(): File? {
        // First check app internal storage
        val internalModelFile = File(context.filesDir, MODEL_FILENAME)
        if (internalModelFile.exists() && internalModelFile.length() > 0) {
            Log.d(TAG, "📱 Using internal model file: ${internalModelFile.absolutePath}")
            return internalModelFile
        }
        
        Log.d(TAG, "🔄 No internal model found, checking external sources...")
        
        // Download/copy model from external sources (only if needed)
        var modelFile: File? = null
        modelDownloadService.downloadModel().collect { result ->
            when (result) {
                is ModelDownloadService.DownloadResult.Success -> {
                    modelFile = result.file
                    Log.d(TAG, "✅ Model ready: ${result.file.absolutePath}")
                }
                is ModelDownloadService.DownloadResult.Error -> {
                    Log.e(TAG, "❌ Model download/copy failed: ${result.message}")
                    return@collect
                }
                is ModelDownloadService.DownloadResult.Progress -> {
                    Log.d(TAG, "📥 Download progress: ${result.percentage}%")
                }
            }
        }
        
        return modelFile
    }
    
    /**
     * Warmup model with a simple prompt (now called on first use)
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
     * Get acceleration status for debugging
     */
    fun getAccelerationInfo(): String {
        return if (isModelInitialized) {
            if (isUsingGPU) "GPU Accelerated 🎮" else "CPU Accelerated 🔧"
        } else {
            "Not Initialized"
        }
    }
    
    /**
     * Generate response - optimized with detailed logging and GPU acceleration
     */
    suspend fun generateResponse(prompt: String): Flow<String> = flow {
        val session = llmSession
        if (!isModelInitialized || session == null) {
            Log.e(TAG, "❌ Model not ready for inference")
            emit("Model not initialized. Please wait...")
            return@flow
        }
        
        val messageStartTime = System.currentTimeMillis()
        Log.d(TAG, "🤖 Starting ${if (isUsingGPU) "GPU" else "CPU"} accelerated response generation for ${prompt.length} chars")
        
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
                        Log.d(TAG, "⚡ First token received in ${timeToFirstToken}ms (${if (isUsingGPU) "GPU" else "CPU"} accelerated)")
                        firstTokenReceived = true
                    }
                    
                    responseBuilder.append(partialResult)
                    
                    if (done) {
                        val fullResponse = responseBuilder.toString()
                        val totalTime = System.currentTimeMillis() - messageStartTime
                        val generationTime = System.currentTimeMillis() - firstTokenTime
                        val tokensPerSecond = if (generationTime > 0) (fullResponse.length * 1000.0 / generationTime).toInt() else 0
                        
                        Log.d(TAG, "✅ ${if (isUsingGPU) "GPU" else "CPU"} response complete: ${totalTime}ms total (${generationTime}ms generation), ${fullResponse.length} chars, ~${tokensPerSecond} tokens/sec")
                        continuation.resume(fullResponse)
                    }
                }
            }
            
            emit(response)
            
        } catch (e: Exception) {
            val totalTime = System.currentTimeMillis() - messageStartTime
            Log.e(TAG, "💥 ${if (isUsingGPU) "GPU" else "CPU"} response generation failed after ${totalTime}ms: ${e.message}")
            emit("Error generating response: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)
    
    fun cleanup() {
        try {
            llmSession?.close()
            llmInference?.close()
            isModelInitialized = false
            Log.d(TAG, "🧹 Cleanup completed (was using ${if (isUsingGPU) "GPU" else "CPU"})")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Cleanup failed: ${e.message}")
        }
    }
} 