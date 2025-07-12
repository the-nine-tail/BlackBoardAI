package com.example.blackboardai.di

import com.example.blackboardai.data.repository.AIRepositoryImpl
import com.example.blackboardai.data.repository.ChatRepositoryImpl
import com.example.blackboardai.data.repository.NoteRepositoryImpl
import com.example.blackboardai.domain.repository.AIRepository
import com.example.blackboardai.domain.repository.ChatRepository
import com.example.blackboardai.domain.repository.NoteRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository
    
    @Binds
    @Singleton
    abstract fun bindAIRepository(
        aiRepositoryImpl: AIRepositoryImpl
    ): AIRepository
    
    @Binds
    @Singleton
    abstract fun bindNoteRepository(
        noteRepositoryImpl: NoteRepositoryImpl
    ): NoteRepository
} 