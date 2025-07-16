package com.example.blackboardai.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blackboardai.data.ai.GoogleAIService
import com.example.blackboardai.data.ai.ModelStatus
import com.example.blackboardai.domain.entity.Note
import com.example.blackboardai.domain.usecase.DeleteNoteUseCase
import com.example.blackboardai.domain.usecase.GetAllNotesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val getAllNotesUseCase: GetAllNotesUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    private val googleAIService: GoogleAIService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()
    
    init {
        loadNotes()
        monitorModelStatus()
    }
    
    private fun monitorModelStatus() {
        viewModelScope.launch {
            googleAIService.initializationProgress.collectLatest { progress ->
                _uiState.value = _uiState.value.copy(
                    modelStatus = progress.status,
                    isModelReady = progress.status == ModelStatus.READY
                )
            }
        }
    }
    
    private fun loadNotes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                getAllNotesUseCase().collect { notes ->
                    _uiState.value = _uiState.value.copy(
                        notes = notes,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            try {
                deleteNoteUseCase(noteId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Check model file integrity and display results
     */
    fun checkModelIntegrity() {
        viewModelScope.launch {
            try {
                val result = googleAIService.checkModelIntegrity()
                val message = if (result.isValid) {
                    "✅ Model file is valid\n\n${result.details}"
                } else {
                    "❌ ${result.message}\n\n${result.details}\n\n${if (result.shouldReDownload) "Recommendation: Force reset to re-download" else "Check file permissions"}"
                }
                
                _uiState.value = _uiState.value.copy(
                    error = message
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to check model integrity: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Force complete reset - clears all cached states and forces model re-download
     */
    fun forceCompleteReset() {
        viewModelScope.launch {
            try {
                googleAIService.forceCompleteReset()
                _uiState.value = _uiState.value.copy(
                    error = "✅ Force reset complete!\n\nAll model files and caches cleared. Restart the app to trigger fresh model download."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to force reset: ${e.message}"
                )
            }
        }
    }
}

data class NotesUiState(
    val notes: List<Note> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val modelStatus: ModelStatus = ModelStatus.NOT_INITIALIZED,
    val isModelReady: Boolean = false
) 