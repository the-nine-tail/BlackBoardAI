package com.example.blackboardai.domain.repository

import kotlinx.coroutines.flow.Flow

interface AIRepository {
    suspend fun generateResponse(prompt: String): Flow<String>
    suspend fun isModelReady(): Boolean
} 