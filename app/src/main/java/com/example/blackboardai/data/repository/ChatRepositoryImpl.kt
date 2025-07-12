package com.example.blackboardai.data.repository

import com.example.blackboardai.data.local.dao.ChatMessageDao
import com.example.blackboardai.data.local.entity.toDomain
import com.example.blackboardai.data.local.entity.toEntity
import com.example.blackboardai.domain.entity.ChatMessage
import com.example.blackboardai.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatMessageDao: ChatMessageDao
) : ChatRepository {
    
    override suspend fun saveMessage(message: ChatMessage): Long {
        return chatMessageDao.insertMessage(message.toEntity())
    }
    
    override suspend fun getAllMessages(): Flow<List<ChatMessage>> {
        return chatMessageDao.getAllMessages().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun deleteMessage(messageId: Long) {
        chatMessageDao.deleteMessage(messageId)
    }
    
    override suspend fun clearAllMessages() {
        chatMessageDao.clearAllMessages()
    }
} 