package com.example.blackboardai.presentation.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.lifecycle.viewModelScope
import com.example.blackboardai.domain.entity.DrawingPath
import com.example.blackboardai.domain.entity.DrawingPoint
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
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

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
    val y: Float,
    val pressure: Float = 1f,
    val timestamp: Long = System.currentTimeMillis()
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
                
                val drawingDataJson = serializeDrawingData()
                val note = Note(
                    id = _uiState.value.noteId,
                    title = _uiState.value.title.ifBlank { "Untitled Note" },
                    content = _uiState.value.content,
                    drawingData = drawingDataJson,
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                    size = calculateNoteSize(drawingDataJson),
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
                        points = drawingPath.points.map { point ->
                            SerializablePoint(
                                x = point.x,
                                y = point.y,
                                pressure = point.pressure,
                                timestamp = point.timestamp
                            )
                        },
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
                            points = serializablePath.points.map { point ->
                                DrawingPoint(
                                    x = point.x,
                                    y = point.y,
                                    pressure = point.pressure,
                                    timestamp = point.timestamp
                                )
                            },
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
    
    // Helper functions for future Gemma AI integration
    
    /**
     * Export drawing as bitmap for AI processing
     * This will be useful when integrating with Gemma model
     */
    fun exportDrawingAsBitmap(width: Int = 1024, height: Int = 1024): android.graphics.Bitmap? {
        try {
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            
            // Fill with white background
            canvas.drawColor(android.graphics.Color.WHITE)
            
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
            
            // Draw all paths
            _uiState.value.drawingPaths.forEach { drawingPath ->
                if (drawingPath.points.isNotEmpty()) {
                    paint.color = drawingPath.color.value.toInt()
                    paint.strokeWidth = drawingPath.strokeWidth
                    
                    val path = android.graphics.Path()
                    path.moveTo(drawingPath.points.first().x, drawingPath.points.first().y)
                    drawingPath.points.drop(1).forEach { point ->
                        path.lineTo(point.x, point.y)
                    }
                    canvas.drawPath(path, paint)
                }
            }
            
            // Draw all shapes
            _uiState.value.shapes.forEach { shape ->
                paint.color = shape.color.value.toInt()
                paint.strokeWidth = shape.strokeWidth
                
                when (shape.type) {
                    ShapeType.RECTANGLE -> {
                        canvas.drawRect(
                            min(shape.startX, shape.endX),
                            min(shape.startY, shape.endY),
                            max(shape.startX, shape.endX),
                            max(shape.startY, shape.endY),
                            paint
                        )
                    }
                    ShapeType.CIRCLE -> {
                        val centerX = (shape.startX + shape.endX) / 2
                        val centerY = (shape.startY + shape.endY) / 2
                        val radius = sqrt(
                            (shape.endX - shape.startX).toDouble().pow(2.0) +
                            (shape.endY - shape.startY).toDouble().pow(2.0)
                        ).toFloat() / 2
                        canvas.drawCircle(centerX, centerY, radius, paint)
                    }
                    ShapeType.LINE -> {
                        canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, paint)
                    }
                }
            }
            
            return bitmap
        } catch (e: Exception) {
            println("Error exporting bitmap: ${e.message}")
            return null
        }
    }
    
    /**
     * Export drawing as SVG for AI processing and web compatibility
     * SVG format is both human-readable and AI-processable
     */
    fun exportDrawingAsSVG(width: Int = 1024, height: Int = 1024): String {
        val svg = StringBuilder()
        svg.append("<svg width=\"$width\" height=\"$height\" xmlns=\"http://www.w3.org/2000/svg\">\n")
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n")
        
        // Add paths
        _uiState.value.drawingPaths.forEach { drawingPath ->
            if (drawingPath.points.isNotEmpty()) {
                val pathData = StringBuilder("M ${drawingPath.points.first().x},${drawingPath.points.first().y}")
                drawingPath.points.drop(1).forEach { point ->
                    pathData.append(" L ${point.x},${point.y}")
                }
                
                svg.append("<path d=\"$pathData\" ")
                svg.append("stroke=\"${colorToHex(drawingPath.color)}\" ")
                svg.append("stroke-width=\"${drawingPath.strokeWidth}\" ")
                svg.append("fill=\"none\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n")
            }
        }
        
        // Add shapes
        _uiState.value.shapes.forEach { shape ->
            when (shape.type) {
                ShapeType.RECTANGLE -> {
                    svg.append("<rect x=\"${min(shape.startX, shape.endX)}\" ")
                    svg.append("y=\"${min(shape.startY, shape.endY)}\" ")
                    svg.append("width=\"${abs(shape.endX - shape.startX)}\" ")
                    svg.append("height=\"${abs(shape.endY - shape.startY)}\" ")
                    svg.append("stroke=\"${colorToHex(shape.color)}\" ")
                    svg.append("stroke-width=\"${shape.strokeWidth}\" fill=\"none\"/>\n")
                }
                ShapeType.CIRCLE -> {
                    val centerX = (shape.startX + shape.endX) / 2
                    val centerY = (shape.startY + shape.endY) / 2
                    val radius = sqrt(
                        (shape.endX - shape.startX).toDouble().pow(2.0) +
                        (shape.endY - shape.startY).toDouble().pow(2.0)
                    ).toFloat() / 2
                    svg.append("<circle cx=\"$centerX\" cy=\"$centerY\" r=\"$radius\" ")
                    svg.append("stroke=\"${colorToHex(shape.color)}\" ")
                    svg.append("stroke-width=\"${shape.strokeWidth}\" fill=\"none\"/>\n")
                }
                ShapeType.LINE -> {
                    svg.append("<line x1=\"${shape.startX}\" y1=\"${shape.startY}\" ")
                    svg.append("x2=\"${shape.endX}\" y2=\"${shape.endY}\" ")
                    svg.append("stroke=\"${colorToHex(shape.color)}\" ")
                    svg.append("stroke-width=\"${shape.strokeWidth}\"/>\n")
                }
            }
        }
        
        svg.append("</svg>")
        return svg.toString()
    }
    
    /**
     * Calculate the size of the note in bytes for metadata
     */
    private fun calculateNoteSize(drawingData: String): Long {
        val titleSize = _uiState.value.title.toByteArray().size
        val contentSize = _uiState.value.content.toByteArray().size
        val drawingSize = drawingData.toByteArray().size
        return (titleSize + contentSize + drawingSize).toLong()
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