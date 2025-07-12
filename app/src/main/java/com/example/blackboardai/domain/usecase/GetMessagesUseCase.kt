package com.example.blackboardai.domain.usecase

import com.example.blackboardai.domain.entity.ChatMessage
import com.example.blackboardai.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMessagesUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(): Flow<List<ChatMessage>> {
        return chatRepository.getAllMessages()
    }
} 