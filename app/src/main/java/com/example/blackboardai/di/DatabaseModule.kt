package com.example.blackboardai.di

import android.content.Context
import androidx.room.Room
import com.example.blackboardai.data.local.dao.ChatMessageDao
import com.example.blackboardai.data.local.database.BlackBoardAIDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideBlackBoardAIDatabase(
        @ApplicationContext context: Context
    ): BlackBoardAIDatabase {
        return Room.databaseBuilder(
            context,
            BlackBoardAIDatabase::class.java,
            BlackBoardAIDatabase.DATABASE_NAME
        ).build()
    }
    
    @Provides
    fun provideChatMessageDao(database: BlackBoardAIDatabase): ChatMessageDao {
        return database.chatMessageDao()
    }
} 