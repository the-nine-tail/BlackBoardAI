package com.example.blackboardai

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import dagger.hilt.android.EntryPointAccessors
import com.example.blackboardai.data.ai.GoogleAIService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AIServiceEntryPoint {
    fun getGoogleAIService(): GoogleAIService
}

@HiltAndroidApp
class BlackBoardAIApplication : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        Log.d("[BlackBoardAI Log]", "Application onCreate - Starting model initialization...")
        
        // Initialize AI model ONCE during app startup using Hilt EntryPoint
        applicationScope.launch {
            val startTime = System.currentTimeMillis()
            Log.d("[BlackBoardAI Log]", "=== MODEL INITIALIZATION START ===")
            
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    applicationContext,
                    AIServiceEntryPoint::class.java
                )
                val googleAIService = entryPoint.getGoogleAIService()
                
                val success = googleAIService.initializeModelOnce()
                val initTime = System.currentTimeMillis() - startTime
                
                if (success) {
                    Log.d("[BlackBoardAI Log]", "✅ Model initialized successfully in ${initTime}ms")
                } else {
                    Log.e("[BlackBoardAI Log]", "❌ Model initialization failed after ${initTime}ms")
                }
            } catch (e: Exception) {
                val initTime = System.currentTimeMillis() - startTime
                Log.e("[BlackBoardAI Log]", "💥 Model initialization exception after ${initTime}ms: ${e.message}")
            }
            
            Log.d("[BlackBoardAI Log]", "=== MODEL INITIALIZATION END ===")
        }
    }
} 