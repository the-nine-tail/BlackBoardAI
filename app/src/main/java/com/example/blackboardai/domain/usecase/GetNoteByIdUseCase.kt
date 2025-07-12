package com.example.blackboardai.domain.usecase

import com.example.blackboardai.domain.entity.Note
import com.example.blackboardai.domain.repository.NoteRepository
import javax.inject.Inject

class GetNoteByIdUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(id: Long): Note? {
        return repository.getNoteById(id)
    }
} 