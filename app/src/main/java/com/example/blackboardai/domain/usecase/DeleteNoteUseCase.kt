package com.example.blackboardai.domain.usecase

import com.example.blackboardai.domain.repository.NoteRepository
import javax.inject.Inject

class DeleteNoteUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(id: Long) {
        repository.deleteNote(id)
    }
} 