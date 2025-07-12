package com.example.blackboardai.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.example.blackboardai.data.local.dao.ChatMessageDao
import com.example.blackboardai.data.local.dao.NoteDao
import com.example.blackboardai.data.local.entity.ChatMessageEntity
import com.example.blackboardai.data.local.entity.NoteEntity

@Database(
    entities = [ChatMessageEntity::class, NoteEntity::class],
    version = 2,
    exportSchema = false
)
abstract class BlackBoardAIDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun noteDao(): NoteDao
    
    companion object {
        const val DATABASE_NAME = "blackboard_ai_database"
    }
} 