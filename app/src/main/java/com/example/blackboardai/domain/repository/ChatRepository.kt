package com.example.blackboardai.domain.repository

import com.example.blackboardai.domain.entity.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    suspend fun saveMessage(message: ChatMessage): Long
    suspend fun getAllMessages(): Flow<List<ChatMessage>>
    suspend fun deleteMessage(messageId: Long)
    suspend fun clearAllMessages()
} 