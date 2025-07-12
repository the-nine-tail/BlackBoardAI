package com.example.blackboardai.presentation.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blackboardai.domain.entity.DrawingPath
import com.example.blackboardai.domain.entity.DrawingShape
import com.example.blackboardai.domain.entity.Note
import com.example.blackboardai.domain.entity.ShapeType
import com.example.blackboardai.domain.entity.TextElement
import com.example.blackboardai.domain.usecase.GetNoteByIdUseCase
import com.example.blackboardai.domain.usecase.SaveNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import javax.inject.Inject

@HiltViewModel
class DrawingViewModel @Inject constructor(
    private val saveNoteUseCase: SaveNoteUseCase,
    private val getNoteByIdUseCase: GetNoteByIdUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DrawingUiState())
    val uiState: StateFlow<DrawingUiState> = _uiState.asStateFlow()
    
    fun loadNote(noteId: Long) {
        if (noteId > 0) {
            viewModelScope.launch {
                try {
                    val note = getNoteByIdUseCase(noteId)
                    note?.let {
                        _uiState.value = _uiState.value.copy(
                            noteId = it.id,
                            title = it.title,
                            content = it.content
                            // TODO: Deserialize drawing data when implementing proper serialization
                        )
                    }
                } catch (e: Exception) {
                    // Handle error loading note
                }
            }
        }
    }
    
    fun addPath(path: DrawingPath) {
        val currentPaths = _uiState.value.drawingPaths.toMutableList()
        currentPaths.add(path)
        _uiState.value = _uiState.value.copy(drawingPaths = currentPaths)
    }
    
    fun addShape(shape: DrawingShape) {
        val currentShapes = _uiState.value.shapes.toMutableList()
        currentShapes.add(shape)
        _uiState.value = _uiState.value.copy(shapes = currentShapes)
    }
    
    fun addText(text: TextElement) {
        val currentTexts = _uiState.value.textElements.toMutableList()
        currentTexts.add(text)
        _uiState.value = _uiState.value.copy(textElements = currentTexts)
    }
    
    fun clearCanvas() {
        _uiState.value = _uiState.value.copy(
            drawingPaths = emptyList(),
            shapes = emptyList(),
            textElements = emptyList()
        )
    }
    
    fun undo() {
        val currentPaths = _uiState.value.drawingPaths.toMutableList()
        if (currentPaths.isNotEmpty()) {
            currentPaths.removeLastOrNull()
            _uiState.value = _uiState.value.copy(drawingPaths = currentPaths)
        }
    }
    
    fun updateCurrentColor(color: Color) {
        _uiState.value = _uiState.value.copy(currentColor = color)
    }
    
    fun updateStrokeWidth(width: Float) {
        _uiState.value = _uiState.value.copy(strokeWidth = width)
    }
    
    fun updateDrawingMode(mode: DrawingMode) {
        _uiState.value = _uiState.value.copy(drawingMode = mode)
    }
    
    fun updateSelectedShape(shapeType: ShapeType?) {
        _uiState.value = _uiState.value.copy(selectedShape = shapeType)
    }
    
    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(title = title)
    }
    
    fun updateContent(content: String) {
        _uiState.value = _uiState.value.copy(content = content)
    }
    
    fun saveNote(onSuccess: (Long) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSaving = true)
                
                val note = Note(
                    id = _uiState.value.noteId,
                    title = _uiState.value.title.ifBlank { "Untitled Note" },
                    content = _uiState.value.content,
                    drawingData = serializeDrawingData(),
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                    backgroundColor = "#FFFFFF"
                )
                
                val noteId = saveNoteUseCase(note)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    noteId = noteId
                )
                onSuccess(noteId)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false)
                onError(e.message ?: "Failed to save note")
            }
        }
    }
    
    private fun serializeDrawingData(): String {
        // Simple serialization - in production, you might want to use a more robust format
        return "${_uiState.value.drawingPaths.size},${_uiState.value.shapes.size},${_uiState.value.textElements.size}"
    }
}

data class DrawingUiState(
    val drawingPaths: List<DrawingPath> = emptyList(),
    val shapes: List<DrawingShape> = emptyList(),
    val textElements: List<TextElement> = emptyList(),
    val currentColor: Color = Color.Black,
    val strokeWidth: Float = 5f,
    val drawingMode: DrawingMode = DrawingMode.DRAW,
    val selectedShape: ShapeType? = null,
    val title: String = "",
    val content: String = "",
    val noteId: Long = 0L,
    val isSaving: Boolean = false
)

enum class DrawingMode {
    DRAW,
    ERASE,
    SHAPE,
    TEXT
} 