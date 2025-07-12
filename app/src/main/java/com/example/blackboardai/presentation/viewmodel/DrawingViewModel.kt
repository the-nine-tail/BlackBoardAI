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
import com.example.blackboardai.data.ai.GoogleAIService
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
import android.util.Log

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
    private val getNoteByIdUseCase: GetNoteByIdUseCase,
    private val googleAIService: GoogleAIService
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
    
    fun dismissSolution() {
        _uiState.value = _uiState.value.copy(showSolution = false, aiSolution = "")
    }
    
    fun solveWithAI() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSolving = true)
                
                // Check if there's anything to solve
                if (_uiState.value.drawingPaths.isEmpty() && 
                    _uiState.value.shapes.isEmpty() && 
                    _uiState.value.textElements.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isSolving = false,
                        aiSolution = "Please draw a problem first! I can help solve math and physics problems from your drawings.",
                        showSolution = true
                    )
                    return@launch
                }
                
                // Export drawing as bitmap for AI analysis
                Log.d("DrawingViewModel", "🎨 Exporting drawing as bitmap for AI analysis...")
                val drawingBitmap = exportDrawingAsBitmap(1024, 1024)
                
                if (drawingBitmap == null) {
                    _uiState.value = _uiState.value.copy(
                        isSolving = false,
                        aiSolution = "Sorry, I couldn't process your drawing. Please try drawing again.",
                        showSolution = true
                    )
                    return@launch
                }
                
                Log.d("DrawingViewModel", "✅ Bitmap exported: ${drawingBitmap.width}x${drawingBitmap.height}")
                
                // Create an optimized prompt for image-based problem solving
                val multimodalPrompt = createImageBasedSolvingPrompt()
                Log.d("DrawingViewModel", "📝 Created multimodal prompt for Gemma")
                
                // Use the new multimodal method with both image and text
                googleAIService.generateMultimodalResponse(multimodalPrompt, drawingBitmap).collect { response ->
                    _uiState.value = _uiState.value.copy(
                        isSolving = false,
                        aiSolution = response,
                        showSolution = true
                    )
                    Log.d("DrawingViewModel", "🎉 Received multimodal AI response: ${response.length} chars")
                }
                
            } catch (e: Exception) {
                Log.e("DrawingViewModel", "💥 Error in multimodal AI solving: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isSolving = false,
                    aiSolution = "Sorry, I couldn't solve this problem right now. Please check your internet connection and try again.\n\nError: ${e.message}",
                    showSolution = true
                )
            }
        }
    }
    
    private fun generateDrawingDescription(): String {
        val description = StringBuilder()
        
        description.append("I have a drawing that contains:\n\n")
        
        // Describe paths (freehand drawing)
        if (_uiState.value.drawingPaths.isNotEmpty()) {
            description.append("${_uiState.value.drawingPaths.size} hand-drawn strokes/lines")
            val colors = _uiState.value.drawingPaths.map { colorToHex(it.color) }.distinct()
            if (colors.size > 1) {
                description.append(" in ${colors.size} different colors")
            }
            description.append("\n")
        }
        
        // Describe shapes
        if (_uiState.value.shapes.isNotEmpty()) {
            val shapeTypes = _uiState.value.shapes.groupBy { it.type }
            shapeTypes.forEach { (type, shapes) ->
                description.append("${shapes.size} ${type.name.lowercase()}(s)\n")
            }
        }
        
        // Describe text elements
        if (_uiState.value.textElements.isNotEmpty()) {
            description.append("Text elements: ")
            _uiState.value.textElements.forEach { text ->
                description.append("\"${text.text}\" ")
            }
            description.append("\n")
        }
        
        // Add note content if available
        if (_uiState.value.content.isNotBlank()) {
            description.append("\nAdditional context: ${_uiState.value.content}\n")
        }
        
        return description.toString()
    }
    
    private fun createSolvingPrompt(drawingDescription: String): String {
        return """
            You are an expert AI tutor specializing in mathematics and physics. I have drawn a problem and need your help solving it.
            
            Drawing Description:
            $drawingDescription
            
            Please analyze this drawing and:
            
            1. **IDENTIFY THE PROBLEM**: Determine what mathematical or physics problem is being presented in the drawing
            
            2. **PROVIDE COMPLETE SOLUTION**: Give a detailed, step-by-step solution with:
               - Clear explanation of each step
               - All calculations shown
               - Reasoning behind each step
               - Final answer clearly stated
            
            3. **EXPLAIN CONCEPTS**: Explain any key concepts, formulas, or principles used
            
            4. **VISUAL GUIDANCE**: If helpful, describe how to visualize or diagram the solution
            
            Common problem types I can help with:
            - Algebra equations and inequalities
            - Geometry (area, perimeter, angles, triangles, circles)
            - Trigonometry problems
            - Calculus (derivatives, integrals, limits)
            - Physics (motion, forces, energy, waves, electricity)
            - Word problems and applications
            - Graph analysis and function problems
            
            If the drawing is unclear or doesn't represent a clear mathematical/physics problem, please ask for clarification or suggest how to better present the problem.
            
            Please provide a comprehensive, educational response that helps me understand both the solution and the underlying concepts.
        """.trimIndent()
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

    private fun createImageBasedSolvingPrompt(): String {
        return """
            You are an expert AI tutor specializing in mathematics and physics with advanced visual analysis capabilities.
            
            I have drawn a mathematical or physics problem on a digital canvas. Please analyze the image I'm sharing and provide a comprehensive solution.
            
            **ANALYZE THE IMAGE AND:**
            
            1. **🔍 VISUAL ANALYSIS**: 
               - Identify all mathematical symbols, numbers, equations, and diagrams in the image
               - Recognize geometric shapes, graphs, charts, or physics diagrams
               - Note any handwritten text, labels, or annotations
               - Describe the spatial relationships between elements
            
            2. **🧮 PROBLEM IDENTIFICATION**: 
               - Determine the specific type of mathematical or physics problem
               - Identify what is being asked or what needs to be solved
               - Recognize the domain (algebra, geometry, calculus, physics, etc.)
            
            3. **📖 STEP-BY-STEP SOLUTION**: 
               - Provide a complete, detailed solution with clear steps
               - Show all calculations and mathematical work
               - Explain the reasoning behind each step
               - Use proper mathematical notation in your response
               - Highlight the final answer clearly
            
            4. **🎓 EDUCATIONAL EXPLANATION**: 
               - Explain key concepts and formulas used
               - Provide context for why certain methods were chosen
               - Suggest alternative approaches if applicable
               - Include memory aids or tips for similar problems
            
            5. **✅ VERIFICATION**: 
               - Check your work and verify the solution makes sense
               - Mention any assumptions made
               - Suggest ways to double-check the answer
            
            **PROBLEM TYPES I CAN SOLVE:**
            - Algebra: equations, inequalities, systems, polynomials
            - Geometry: area, perimeter, angles, triangles, circles, 3D shapes
            - Trigonometry: sin/cos/tan, identities, wave functions
            - Calculus: derivatives, integrals, limits, optimization
            - Physics: mechanics, thermodynamics, waves, electricity, magnetism
            - Statistics: probability, distributions, data analysis
            - Pre-calculus: functions, logarithms, exponentials
            - Graph analysis: interpreting charts, function behavior
            
            **SPECIAL INSTRUCTIONS:**
            - If the image shows a graph, analyze coordinates, slopes, and key points
            - If it's a geometric figure, identify measurements and relationships
            - If it's a physics setup, recognize forces, motion, or energy concepts
            - If text is unclear, make reasonable interpretations and state your assumptions
            
            Please provide a thorough, educational response that helps me not just get the answer, but truly understand the problem and solution method.
        """.trimIndent()
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
    val isSaving: Boolean = false,
    val isSolving: Boolean = false,
    val aiSolution: String = "",
    val showSolution: Boolean = false
)

enum class DrawingMode {
    DRAW,
    ERASE,
    SHAPE,
    TEXT
} 