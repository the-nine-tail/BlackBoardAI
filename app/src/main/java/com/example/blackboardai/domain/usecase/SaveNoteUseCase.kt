package com.example.blackboardai.domain.usecase

import com.example.blackboardai.domain.entity.Note
import com.example.blackboardai.domain.repository.NoteRepository
import kotlinx.datetime.Clock
import javax.inject.Inject

class SaveNoteUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(note: Note): Long {
        val now = Clock.System.now()
        val noteToSave = if (note.id == 0L) {
            // New note
            note.copy(
                createdAt = now,
                updatedAt = now
            )
        } else {
            // Update existing note
            note.copy(updatedAt = now)
        }
        
        return if (note.id == 0L) {
            repository.insertNote(noteToSave)
        } else {
            repository.updateNote(noteToSave)
            note.id
        }
    }
} 