package com.example.blackboardai.domain.usecase

import com.example.blackboardai.domain.entity.ChatMessage
import com.example.blackboardai.domain.repository.AIRepository
import com.example.blackboardai.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val aiRepository: AIRepository
) {
    suspend operator fun invoke(userMessage: String): Flow<ChatMessage> = flow {
        // Save user message
        val userChatMessage = ChatMessage(
            content = userMessage,
            isFromUser = true
        )
        val userMessageId = chatRepository.saveMessage(userChatMessage)
        emit(userChatMessage.copy(id = userMessageId))
        
        // Generate AI response
        aiRepository.generateResponse(userMessage).collect { aiResponse ->
            val aiChatMessage = ChatMessage(
                content = aiResponse,
                isFromUser = false
            )
            val aiMessageId = chatRepository.saveMessage(aiChatMessage)
            emit(aiChatMessage.copy(id = aiMessageId))
        }
    }
} 