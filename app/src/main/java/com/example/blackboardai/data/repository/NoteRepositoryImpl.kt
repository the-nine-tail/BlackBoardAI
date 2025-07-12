package com.example.blackboardai.data.repository

import com.example.blackboardai.data.local.dao.NoteDao
import com.example.blackboardai.data.local.entity.NoteEntity
import com.example.blackboardai.domain.entity.Note
import com.example.blackboardai.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao
) : NoteRepository {
    
    override suspend fun getAllNotes(): Flow<List<Note>> {
        return noteDao.getAllNotes().map { entities ->
            entities.map { it.toDomainNote() }
        }
    }
    
    override suspend fun getNoteById(id: Long): Note? {
        return noteDao.getNoteById(id)?.toDomainNote()
    }
    
    override suspend fun insertNote(note: Note): Long {
        return noteDao.insertNote(note.toEntity())
    }
    
    override suspend fun updateNote(note: Note) {
        noteDao.updateNote(note.toEntity())
    }
    
    override suspend fun deleteNote(id: Long) {
        noteDao.deleteNote(id)
    }
    
    override suspend fun deleteAllNotes() {
        noteDao.deleteAllNotes()
    }
    
    override suspend fun searchNotes(query: String): Flow<List<Note>> {
        return noteDao.searchNotes(query).map { entities ->
            entities.map { it.toDomainNote() }
        }
    }
    
    private fun NoteEntity.toDomainNote(): Note {
        return Note(
            id = id,
            title = title,
            content = content,
            drawingData = drawingData,
            thumbnail = thumbnail,
            createdAt = createdAt,
            updatedAt = updatedAt,
            size = size,
            backgroundColor = backgroundColor,
            tags = tags
        )
    }
    
    private fun Note.toEntity(): NoteEntity {
        return NoteEntity(
            id = id,
            title = title,
            content = content,
            drawingData = drawingData,
            thumbnail = thumbnail,
            createdAt = createdAt,
            updatedAt = updatedAt,
            size = size,
            backgroundColor = backgroundColor,
            tags = tags
        )
    }
} 