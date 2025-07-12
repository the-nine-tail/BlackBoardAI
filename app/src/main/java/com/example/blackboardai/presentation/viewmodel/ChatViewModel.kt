package com.example.blackboardai.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val getMessagesUseCase: GetMessagesUseCase
) : ViewModel() {
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _currentMessage = MutableStateFlow("")
    val currentMessage: StateFlow<String> = _currentMessage.asStateFlow()
    
    init {
        loadMessages()
    }
    
    private fun loadMessages() {
        viewModelScope.launch {
            getMessagesUseCase().collect { messageList ->
                _messages.value = messageList
            }
        }
    }
    
    fun updateCurrentMessage(message: String) {
        _currentMessage.value = message
    }
    
    fun sendMessage() {
        val message = _currentMessage.value.trim()
        if (message.isNotEmpty()) {
            _isLoading.value = true
            _currentMessage.value = ""
            
            viewModelScope.launch {
                try {
                    sendMessageUseCase(message).collect { chatMessage ->
                        // Messages will be automatically updated through the flow in loadMessages()
                    }
                } catch (e: Exception) {
                    // Handle error - could emit error state
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }
} 