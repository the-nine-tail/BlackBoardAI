package com.example.blackboardai.data.ai

import android.content.Context
import android.graphics.Bitmap
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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
    }
    
    // Singleton pattern - these should NEVER be reset after initialization
    private var llmInference: LlmInference? = null
    private var llmSession: LlmInferenceSession? = null
    private var isModelInitialized = false
    private var cachedModelFilePath: String? = null
    
    /**
     * Initialize model ONCE during app startup - called from Application class
     */
    suspend fun initializeModelOnce(): Boolean = withContext(Dispatchers.IO) {
        if (isModelInitialized && llmSession != null) {
            Log.d(TAG, "⚡ Model already initialized, skipping...")
            return@withContext true
        }
        
        Log.d(TAG, "🚀 Starting model initialization...")
        val startTime = System.currentTimeMillis()
        
        try {
            // Step 1: Get model file (only once)
            val modelFile = getModelFileOnce()
            if (modelFile == null) {
                Log.e(TAG, "❌ Model file not found")
                return@withContext false
            }
            
            Log.d(TAG, "📁 Model file ready: ${modelFile.absolutePath}")
            cachedModelFilePath = modelFile.absolutePath
            
            // Step 2: Initialize LLM Inference
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
            val warmupStartTime = System.currentTimeMillis()
            Log.d(TAG, "🔥 Warming up model...")
            
            try {
                warmupModel()
                val warmupTime = System.currentTimeMillis() - warmupStartTime
                Log.d(TAG, "✅ Model warmed up in ${warmupTime}ms")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Warmup failed but continuing: ${e.message}")
            }
            
            val totalTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "🎉 Model fully initialized and ready in ${totalTime}ms")
            
            return@withContext true
            
        } catch (e: Exception) {
            val totalTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "💥 Model initialization failed after ${totalTime}ms: ${e.message}")
            return@withContext false
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