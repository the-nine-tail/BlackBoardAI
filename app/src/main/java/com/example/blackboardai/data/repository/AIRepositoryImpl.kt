package com.example.blackboardai.data.repository

import com.example.blackboardai.data.ai.GoogleAIService
import com.example.blackboardai.domain.repository.AIRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIRepositoryImpl @Inject constructor(
    private val aiService: GoogleAIService
) : AIRepository {
    
    override suspend fun generateResponse(prompt: String): Flow<String> {
        return aiService.generateResponse(prompt)
    }
    
    override suspend fun isModelReady(): Boolean {
        return aiService.isModelReady()
    }
} 