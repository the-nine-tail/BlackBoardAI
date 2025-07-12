package com.example.blackboardai.domain.usecase

import android.util.Log
import com.example.blackboardai.domain.entity.ChatMessage
import com.example.blackboardai.domain.repository.AIRepository
import com.example.blackboardai.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val aiRepository: AIRepository
) {
    companion object {
        private const val TAG = "[BlackBoardAI Log]"
    }
    
    // Background scope for async database operations
    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    suspend operator fun invoke(userMessage: String): Flow<ChatMessage> = flow {
        val messageStartTime = System.currentTimeMillis()
        Log.d(TAG, "📝 Processing message: '${userMessage.take(50)}${if (userMessage.length > 50) "..." else ""}'")
        
        // Step 1: Create and emit user message immediately (don't wait for DB save)
        val userChatMessage = ChatMessage(
            content = userMessage,
            isFromUser = true
        )
        
        Log.d(TAG, "👤 Emitting user message immediately")
        emit(userChatMessage)
        
        // Step 2: Save user message to DB asynchronously (non-blocking)
        dbScope.launch {
            val dbStartTime = System.currentTimeMillis()
            try {
                val userMessageId = chatRepository.saveMessage(userChatMessage)
                val dbTime = System.currentTimeMillis() - dbStartTime
                Log.d(TAG, "💾 User message saved to DB in ${dbTime}ms (ID: $userMessageId)")
            } catch (e: Exception) {
                val dbTime = System.currentTimeMillis() - dbStartTime
                Log.e(TAG, "💥 Failed to save user message after ${dbTime}ms: ${e.message}")
            }
        }
        
        // Step 3: Generate AI response
        val aiStartTime = System.currentTimeMillis()
        Log.d(TAG, "🤖 Starting AI response generation...")
        
        aiRepository.generateResponse(userMessage).collect { aiResponse ->
            val aiResponseTime = System.currentTimeMillis() - aiStartTime
            Log.d(TAG, "🤖 AI response received in ${aiResponseTime}ms")
            
            val aiChatMessage = ChatMessage(
                content = aiResponse,
                isFromUser = false
            )
            
            // Step 4: Emit AI response immediately (don't wait for DB save)
            Log.d(TAG, "🤖 Emitting AI response immediately")
            emit(aiChatMessage)
            
            // Step 5: Save AI response to DB asynchronously (non-blocking)
            dbScope.launch {
                val dbStartTime = System.currentTimeMillis()
                try {
                    val aiMessageId = chatRepository.saveMessage(aiChatMessage)
                    val dbTime = System.currentTimeMillis() - dbStartTime
                    Log.d(TAG, "💾 AI message saved to DB in ${dbTime}ms (ID: $aiMessageId)")
                } catch (e: Exception) {
                    val dbTime = System.currentTimeMillis() - dbStartTime
                    Log.e(TAG, "💥 Failed to save AI message after ${dbTime}ms: ${e.message}")
                }
            }
            
            val totalTime = System.currentTimeMillis() - messageStartTime
            Log.d(TAG, "🎉 Message flow complete in ${totalTime}ms")
        }
    }
} 