package com.example.blackboardai.presentation.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

// Serializable data classes for drawing data
data class SerializableDrawingData(
    val paths: List<SerializablePath> = emptyList(),
    val shapes: List<SerializableShape> = emptyList(),
    val textElements: List<SerializableText> = emptyList()
)

data class SerializablePath(
    val points: List<SerializablePoint> = emptyList(),
    val colorHex: String = "#000000",
    val strokeWidth: Float = 5f,
    val isEraser: Boolean = false
)

data class SerializableShape(
    val type: String, // "CIRCLE", "RECTANGLE", "LINE"
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val colorHex: String = "#000000",
    val strokeWidth: Float = 5f,
    val filled: Boolean = false
)

data class SerializableText(
    val text: String,
    val x: Float,
    val y: Float,
    val colorHex: String = "#000000",
    val fontSize: Float = 16f
)

data class SerializablePoint(
    val x: Float,
    val y: Float
)

@HiltViewModel
class DrawingViewModel @Inject constructor(
    private val saveNoteUseCase: SaveNoteUseCase,
    private val getNoteByIdUseCase: GetNoteByIdUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DrawingUiState())
    val uiState: StateFlow<DrawingUiState> = _uiState.asStateFlow()
    
    private val gson = Gson()
    
    fun loadNote(noteId: Long) {
        if (noteId > 0) {
            viewModelScope.launch {
                try {
                    val note = getNoteByIdUseCase(noteId)
                    note?.let {
                        // Deserialize drawing data
                        val drawingData = deserializeDrawingData(it.drawingData)
                        
                        _uiState.value = _uiState.value.copy(
                            noteId = it.id,
                            title = it.title,
                            content = it.content,
                            drawingPaths = drawingData.paths,
                            shapes = drawingData.shapes,
                            textElements = drawingData.textElements
                        )
                    }
                } catch (e: Exception) {
                    // Handle error loading note
                    println("Error loading note: ${e.message}")
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
        try {
            val serializableData = SerializableDrawingData(
                paths = _uiState.value.drawingPaths.map { drawingPath ->
                    SerializablePath(
                        points = extractPathPoints(drawingPath.path),
                        colorHex = colorToHex(drawingPath.color),
                        strokeWidth = drawingPath.strokeWidth,
                        isEraser = drawingPath.isEraser
                    )
                },
                shapes = _uiState.value.shapes.map { shape ->
                    SerializableShape(
                        type = shape.type.name,
                        startX = shape.startX,
                        startY = shape.startY,
                        endX = shape.endX,
                        endY = shape.endY,
                        colorHex = colorToHex(shape.color),
                        strokeWidth = shape.strokeWidth,
                        filled = shape.filled
                    )
                },
                textElements = _uiState.value.textElements.map { text ->
                    SerializableText(
                        text = text.text,
                        x = text.x,
                        y = text.y,
                        colorHex = colorToHex(text.color),
                        fontSize = text.fontSize
                    )
                }
            )
            return gson.toJson(serializableData)
        } catch (e: Exception) {
            println("Error serializing drawing data: ${e.message}")
            return ""
        }
    }
    
    // Create a data class to hold the converted drawing data
    data class DeserializedDrawingData(
        val paths: List<DrawingPath>,
        val shapes: List<DrawingShape>,
        val textElements: List<TextElement>
    )
    
    private fun deserializeDrawingData(data: String): DeserializedDrawingData {
        return try {
            if (data.isBlank()) {
                DeserializedDrawingData(emptyList(), emptyList(), emptyList())
            } else {
                val drawingData = gson.fromJson(data, SerializableDrawingData::class.java)
                
                // Convert back to UI entities
                DeserializedDrawingData(
                    paths = drawingData.paths.map { serializablePath ->
                        DrawingPath(
                            path = createPathFromPoints(serializablePath.points),
                            color = hexToColor(serializablePath.colorHex),
                            strokeWidth = serializablePath.strokeWidth,
                            isEraser = serializablePath.isEraser
                        )
                    },
                    shapes = drawingData.shapes.map { serializableShape ->
                        DrawingShape(
                            type = ShapeType.valueOf(serializableShape.type),
                            startX = serializableShape.startX,
                            startY = serializableShape.startY,
                            endX = serializableShape.endX,
                            endY = serializableShape.endY,
                            color = hexToColor(serializableShape.colorHex),
                            strokeWidth = serializableShape.strokeWidth,
                            filled = serializableShape.filled
                        )
                    },
                    textElements = drawingData.textElements.map { serializableText ->
                        TextElement(
                            text = serializableText.text,
                            x = serializableText.x,
                            y = serializableText.y,
                            color = hexToColor(serializableText.colorHex),
                            fontSize = serializableText.fontSize
                        )
                    }
                )
            }
        } catch (e: Exception) {
            println("Error deserializing drawing data: ${e.message}")
            DeserializedDrawingData(emptyList(), emptyList(), emptyList())
        }
    }
    
    // Helper functions for color conversion
    private fun colorToHex(color: Color): String {
        val argb = color.value.toULong()
        return "#${argb.toString(16).padStart(8, '0').takeLast(6)}"
    }
    
    private fun hexToColor(hex: String): Color {
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (e: Exception) {
            Color.Black
        }
    }
    
    // Helper functions for path conversion
    private fun extractPathPoints(path: Path): List<SerializablePoint> {
        // For simplicity, we'll store a basic representation
        // In a full implementation, you'd need to iterate through path operations
        return emptyList() // This is a limitation - Path doesn't provide easy point extraction
    }
    
    private fun createPathFromPoints(points: List<SerializablePoint>): Path {
        val path = Path()
        if (points.isNotEmpty()) {
            path.moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { point ->
                path.lineTo(point.x, point.y)
            }
        }
        return path
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