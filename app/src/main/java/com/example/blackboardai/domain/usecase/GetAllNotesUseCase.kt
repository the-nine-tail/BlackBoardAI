package com.example.blackboardai.domain.usecase

import com.example.blackboardai.domain.entity.Note
import com.example.blackboardai.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllNotesUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(): Flow<List<Note>> {
        return repository.getAllNotes()
    }
} 