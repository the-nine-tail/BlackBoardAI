package com.example.blackboardai.domain.repository

import com.example.blackboardai.domain.entity.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    suspend fun getAllNotes(): Flow<List<Note>>
    suspend fun getNoteById(id: Long): Note?
    suspend fun insertNote(note: Note): Long
    suspend fun updateNote(note: Note)
    suspend fun deleteNote(id: Long)
    suspend fun deleteAllNotes()
    suspend fun searchNotes(query: String): Flow<List<Note>>
} 