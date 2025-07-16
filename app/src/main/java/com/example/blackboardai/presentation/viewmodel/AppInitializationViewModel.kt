package com.example.blackboardai.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blackboardai.data.ai.GoogleAIService
import com.example.blackboardai.data.ai.ModelStatus
import com.example.blackboardai.data.ai.ModelInitializationProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppInitializationViewModel @Inject constructor(
    private val googleAIService: GoogleAIService
) : ViewModel() {
    
    private val _initializationState = MutableStateFlow(AppInitializationState())
    val initializationState: StateFlow<AppInitializationState> = _initializationState.asStateFlow()
    
    init {
        monitorInitializationProgress()
        checkAndStartInitialization()
    }
    
    private fun monitorInitializationProgress() {
        viewModelScope.launch {
            googleAIService.initializationProgress.collectLatest { progress ->
                _initializationState.value = _initializationState.value.copy(
                    progress = progress,
                    isInitialized = progress.status == ModelStatus.READY,
                    hasError = progress.status == ModelStatus.ERROR
                )
            }
        }
    }
    
    private fun checkAndStartInitialization() {
        viewModelScope.launch {
            // Check if initialization is truly needed
            val isNeeded = googleAIService.isInitializationNeeded()
            val currentStatus = googleAIService.getModelStatus()
            
            Log.d("AppInitViewModel", "📊 Status: $currentStatus, initialization needed: $isNeeded")
            
            if (isNeeded) {
                Log.d("AppInitViewModel", "🚀 ViewModel starting model setup...")
                googleAIService.initializeModelOnce()
            } else {
                Log.d("AppInitViewModel", "⏭️ Model setup not needed - already handled or in progress")
            }
        }
    }
    
    fun retryInitialization() {
        viewModelScope.launch {
            _initializationState.value = _initializationState.value.copy(
                hasError = false
            )
            
            // Use smart retry that continues from the failed step
            val currentProgress = _initializationState.value.progress
            if (currentProgress.failedStep != null) {
                Log.d("AppInitViewModel", "🔄 Using smart retry from step: ${currentProgress.failedStep}")
                googleAIService.retryFromFailedStep()
            } else {
                Log.d("AppInitViewModel", "🔄 No failed step info, doing full retry")
                // Fallback to full reset if no failed step info
                googleAIService.resetInitializationState()
                googleAIService.initializeModelOnce()
            }
        }
    }
    
    fun startInitializationWithPermission() {
        viewModelScope.launch {
            Log.d("AppInitViewModel", "🔐 Storage permission granted, starting initialization")
            googleAIService.initializeModelOnce()
        }
    }
    
    fun proceedToApp() {
        _initializationState.value = _initializationState.value.copy(
            canProceed = true
        )
    }
}

data class AppInitializationState(
    val progress: ModelInitializationProgress = ModelInitializationProgress(),
    val isInitialized: Boolean = false,
    val hasError: Boolean = false,
    val canProceed: Boolean = false
) 