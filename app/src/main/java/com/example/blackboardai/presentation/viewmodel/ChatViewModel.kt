package com.example.blackboardai.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blackboardai.data.ai.GoogleAIService
import com.example.blackboardai.domain.entity.ChatMessage
import com.example.blackboardai.domain.usecase.GetMessagesUseCase
import com.example.blackboardai.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sendMessageUseCase: SendMessageUseCase,
    private val getMessagesUseCase: GetMessagesUseCase,
    private val googleAIService: GoogleAIService
) : ViewModel() {
    
    companion object {
        private const val TAG = "[BlackBoardAI Log]"
    }
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _currentMessage = MutableStateFlow("")
    val currentMessage: StateFlow<String> = _currentMessage.asStateFlow()
    
    private val _isModelInitialized = MutableStateFlow(false)
    val isModelInitialized: StateFlow<Boolean> = _isModelInitialized.asStateFlow()
    
    init {
        Log.d(TAG, "🏗️ ChatViewModel initialized")
        loadMessages()
        checkModelStatus()
    }
    
    private fun loadMessages() {
        Log.d(TAG, "📚 Loading chat messages from database...")
        viewModelScope.launch {
            try {
                getMessagesUseCase().collect { messageList ->
                    _messages.value = messageList
                    Log.d(TAG, "📚 Loaded ${messageList.size} messages from database")
                }
            } catch (e: Exception) {
                Log.e(TAG, "💥 Failed to load messages: ${e.message}")
            }
        }
    }
    
    /**
     * Check model status - NO initialization here, just status check
     */
    private fun checkModelStatus() {
        viewModelScope.launch {
            // Simple status check - model should already be initialized by Application
            val isReady = googleAIService.isModelReady()
            _isModelInitialized.value = isReady
            
            if (isReady) {
                Log.d(TAG, "✅ Model is ready for inference")
            } else {
                Log.d(TAG, "⏳ Model not ready yet, waiting...")
                
                // Poll for model readiness (it should be initializing in background)
                monitorModelStatus()
            }
        }
    }
    
    /**
     * Monitor model status until ready
     */
    private fun monitorModelStatus() {
        viewModelScope.launch {
            var attempts = 0
            val maxAttempts = 30 // 30 seconds max wait
            
            while (!googleAIService.isModelReady() && attempts < maxAttempts) {
                kotlinx.coroutines.delay(1000) // Check every second
                attempts++
                Log.d(TAG, "⏳ Waiting for model... (${attempts}s)")
            }
            
            val isReady = googleAIService.isModelReady()
            _isModelInitialized.value = isReady
            
            if (isReady) {
                Log.d(TAG, "✅ Model became ready after ${attempts}s")
            } else {
                Log.e(TAG, "❌ Model failed to initialize after ${attempts}s")
            }
        }
    }
    
    fun updateCurrentMessage(message: String) {
        _currentMessage.value = message
    }
    
    fun sendMessage() {
        val message = _currentMessage.value.trim()
        if (message.isEmpty()) {
            Log.w(TAG, "⚠️ Empty message, ignoring")
            return
        }
        
        if (!_isModelInitialized.value) {
            Log.w(TAG, "⚠️ Model not ready, ignoring message")
            return
        }
        
        _isLoading.value = true
        _currentMessage.value = ""
        
        val sendStartTime = System.currentTimeMillis()
        Log.d(TAG, "🚀 === SENDING MESSAGE START ===")
        Log.d(TAG, "📤 Message: '${message.take(100)}${if (message.length > 100) "..." else ""}'")
        
        viewModelScope.launch {
            try {
                sendMessageUseCase(message).collect { chatMessage ->
                    val elapsedTime = System.currentTimeMillis() - sendStartTime
                    val messageType = if (chatMessage.isFromUser) "👤 User" else "🤖 AI"
                    
                    Log.d(TAG, "$messageType message processed in ${elapsedTime}ms")
                    
                    // Messages are automatically updated through loadMessages() flow
                    
                    if (!chatMessage.isFromUser) {
                        // AI response received - end of flow
                        val totalTime = System.currentTimeMillis() - sendStartTime
                        Log.d(TAG, "🎉 === MESSAGE FLOW COMPLETE: ${totalTime}ms ===")
                    }
                }
            } catch (e: Exception) {
                val elapsedTime = System.currentTimeMillis() - sendStartTime
                Log.e(TAG, "💥 Message sending failed after ${elapsedTime}ms: ${e.message}")
            } finally {
                _isLoading.value = false
                val totalTime = System.currentTimeMillis() - sendStartTime
                Log.d(TAG, "🔚 Send message operation completed in ${totalTime}ms")
            }
        }
    }
} 