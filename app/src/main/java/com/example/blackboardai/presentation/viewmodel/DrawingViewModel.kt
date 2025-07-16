package com.example.blackboardai.presentation.viewmodel

import android.content.Context
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max
import android.util.Log
import java.io.File
import java.io.FileOutputStream

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
    private val googleAIService: GoogleAIService,
    @ApplicationContext private val context: Context
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
    
    fun erasePath(eraserPath: DrawingPath) {
        val currentPaths = _uiState.value.drawingPaths.toMutableList()
        val currentShapes = _uiState.value.shapes.toMutableList()
        
        // Remove paths that intersect with the eraser path
        val remainingPaths = currentPaths.filter { drawingPath ->
            !pathsIntersect(drawingPath, eraserPath)
        }
        
        // Remove shapes that intersect with the eraser path
        val remainingShapes = currentShapes.filter { shape ->
            !shapeIntersectsWithPath(shape, eraserPath)
        }
        
        _uiState.value = _uiState.value.copy(
            drawingPaths = remainingPaths,
            shapes = remainingShapes
        )
    }
    
    private fun pathsIntersect(path1: DrawingPath, eraserPath: DrawingPath): Boolean {
        if (path1.points.isEmpty() || eraserPath.points.isEmpty()) return false
        
        val eraserRadius = eraserPath.strokeWidth / 2
        
        // Check if any point in path1 is within eraser radius of any eraser point
        for (point1 in path1.points) {
            for (eraserPoint in eraserPath.points) {
                val distance = sqrt(
                    (point1.x - eraserPoint.x).toDouble().pow(2.0) +
                    (point1.y - eraserPoint.y).toDouble().pow(2.0)
                ).toFloat()
                
                if (distance <= eraserRadius + path1.strokeWidth / 2) {
                    return true
                }
            }
        }
        return false
    }
    
    private fun shapeIntersectsWithPath(shape: DrawingShape, eraserPath: DrawingPath): Boolean {
        if (eraserPath.points.isEmpty()) return false
        
        val eraserRadius = eraserPath.strokeWidth / 2
        
        // Check if any eraser point intersects with the shape
        for (eraserPoint in eraserPath.points) {
            when (shape.type) {
                ShapeType.LINE -> {
                    // Check distance from point to line segment
                    val distance = distanceFromPointToLineSegment(
                        eraserPoint.x, eraserPoint.y,
                        shape.startX, shape.startY,
                        shape.endX, shape.endY
                    )
                    if (distance <= eraserRadius + shape.strokeWidth / 2) {
                        return true
                    }
                }
                ShapeType.RECTANGLE -> {
                    // Check if point is inside or near rectangle
                    val left = min(shape.startX, shape.endX) - shape.strokeWidth / 2
                    val right = max(shape.startX, shape.endX) + shape.strokeWidth / 2
                    val top = min(shape.startY, shape.endY) - shape.strokeWidth / 2
                    val bottom = max(shape.startY, shape.endY) + shape.strokeWidth / 2
                    
                    if (eraserPoint.x >= left - eraserRadius && eraserPoint.x <= right + eraserRadius &&
                        eraserPoint.y >= top - eraserRadius && eraserPoint.y <= bottom + eraserRadius) {
                        return true
                    }
                }
                ShapeType.CIRCLE -> {
                    // Check distance from eraser point to circle center
                    val centerX = (shape.startX + shape.endX) / 2
                    val centerY = (shape.startY + shape.endY) / 2
                    val circleRadius = sqrt(
                        (shape.endX - shape.startX).toDouble().pow(2.0) +
                        (shape.endY - shape.startY).toDouble().pow(2.0)
                    ).toFloat() / 2
                    
                    val distance = sqrt(
                        (eraserPoint.x - centerX).toDouble().pow(2.0) +
                        (eraserPoint.y - centerY).toDouble().pow(2.0)
                    ).toFloat()
                    
                    if (distance <= eraserRadius + circleRadius + shape.strokeWidth / 2) {
                        return true
                    }
                }
            }
        }
        return false
    }
    
    private fun distanceFromPointToLineSegment(
        px: Float, py: Float,
        x1: Float, y1: Float, 
        x2: Float, y2: Float
    ): Float {
        val A = px - x1
        val B = py - y1
        val C = x2 - x1
        val D = y2 - y1
        
        val dot = A * C + B * D
        val lenSq = C * C + D * D
        
        var param = -1f
        if (lenSq != 0f) {
            param = dot / lenSq
        }
        
        val xx: Float
        val yy: Float
        
        when {
            param < 0 -> {
                xx = x1
                yy = y1
            }
            param > 1 -> {
                xx = x2
                yy = y2
            }
            else -> {
                xx = x1 + param * C
                yy = y1 + param * D
            }
        }
        
        val dx = px - xx
        val dy = py - yy
        return sqrt(dx * dx + dy * dy)
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
                
                // Check if AI model is ready
                if (googleAIService.getModelStatus() != com.example.blackboardai.data.ai.ModelStatus.READY) {
                    val statusMessage = when (googleAIService.getModelStatus()) {
                        com.example.blackboardai.data.ai.ModelStatus.NOT_INITIALIZED,
                        com.example.blackboardai.data.ai.ModelStatus.CHECKING_MODEL -> 
                            "🔄 AI is still starting up...\n\nPlease wait a moment while we initialize the AI model for the first time."
                        com.example.blackboardai.data.ai.ModelStatus.DOWNLOADING_MODEL -> 
                            "📥 Downloading AI model...\n\nThis only happens once. Please wait while we download the AI model to your device."
                        com.example.blackboardai.data.ai.ModelStatus.INITIALIZING_INFERENCE,
                        com.example.blackboardai.data.ai.ModelStatus.CREATING_SESSION,
                        com.example.blackboardai.data.ai.ModelStatus.WARMING_UP -> 
                            "⚙️ Setting up AI engine...\n\nAlmost ready! Just finishing the AI setup process."
                        com.example.blackboardai.data.ai.ModelStatus.ERROR -> 
                            "❌ AI setup failed\n\nThere was an error setting up the AI model. Please restart the app or check your internet connection."
                        else -> "⏳ AI not ready yet...\n\nPlease wait for the AI model to finish initializing."
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        isSolving = false,
                        aiSolution = statusMessage,
                        showSolution = true
                    )
                    return@launch
                }
                
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
                
                // Export drawing as bitmap for AI analysis - use smaller size to reduce token usage
                Log.d("DrawingViewModel", "🎨 Exporting drawing as bitmap for AI analysis...")
                val drawingBitmap = exportDrawingAsBitmap(512, 512) // Reduced from 1024x1024 to save tokens
                
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
                
                // Estimate token usage for debugging
                val promptTokens = estimateTokenCount(multimodalPrompt)
                val imageTokens = estimateImageTokenCount(drawingBitmap.width, drawingBitmap.height)
                val totalEstimatedTokens = promptTokens + imageTokens
                
                Log.d("DrawingViewModel", "📝 Created multimodal prompt for Gemma")
                Log.d("DrawingViewModel", "🔢 Token estimation:")
                Log.d("DrawingViewModel", "   - Prompt tokens: ~$promptTokens")
                Log.d("DrawingViewModel", "   - Image tokens: ~$imageTokens (${drawingBitmap.width}x${drawingBitmap.height})")
                Log.d("DrawingViewModel", "   - Total estimated: ~$totalEstimatedTokens / 4096")
                Log.d("DrawingViewModel", "   - Remaining for response: ~${4096 - totalEstimatedTokens}")
                
                if (totalEstimatedTokens > 3800) {
                    Log.w("DrawingViewModel", "⚠️ High token usage! May exceed limit during generation.")
                }
                

                
                // Use the new streaming multimodal method with simplified prompt to prevent loops
                var responseLength = 0
                val maxResponseLength = 8000 // Increased limit to match 4096 token capacity
                var isStreamingComplete = false
                
                try {
                    // Add timeout to prevent hanging
                    val streamingResult = withTimeoutOrNull(1800000) { // 3 minute timeout
                        googleAIService.generateMultimodalResponse(multimodalPrompt, drawingBitmap).collect { streamingResponse ->
                            responseLength = streamingResponse.length
                            
                            // Stop if response gets too long (prevents infinite loops)
                            if (responseLength > maxResponseLength) {
                                Log.w("DrawingViewModel", "⚠️ Response too long (${responseLength} chars), stopping stream")
                                _uiState.value = _uiState.value.copy(
                                    isSolving = false,
                                    aiSolution = streamingResponse.take(maxResponseLength) + "\n\n*[Response truncated to prevent excessive length]*",
                                    showSolution = true
                                )
                                isStreamingComplete = true
                                return@collect
                            }
                            
                            // Update UI with each streaming chunk - keep isSolving true while streaming
                            _uiState.value = _uiState.value.copy(
                                isSolving = true, // Keep true while streaming
                                aiSolution = streamingResponse,
                                showSolution = true
                            )
                            Log.d("DrawingViewModel", "📤 Streaming AI response: ${streamingResponse.length} chars")
                        }
                        
                        // Mark as complete when flow finishes naturally
                        isStreamingComplete = true
                        Log.d("DrawingViewModel", "✅ Streaming complete, final response length: $responseLength chars")
                    }
                    
                    // Handle timeout or completion
                    if (streamingResult == null) {
                        Log.w("DrawingViewModel", "⚠️ AI response timed out after 3 minutes")
                        _uiState.value = _uiState.value.copy(
                            isSolving = false,
                            aiSolution = "## Timeout\nThe AI response took too long and was stopped. Please try again with a simpler problem.",
                            showSolution = true
                        )
                    } else {
                        // Streaming completed successfully - set isSolving to false
                        Log.d("DrawingViewModel", "🎉 AI response streaming completed successfully")
                        _uiState.value = _uiState.value.copy(
                            isSolving = false
                        )
                    }
                } catch (e: Exception) {
                    Log.e("DrawingViewModel", "💥 Error in streaming flow: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        isSolving = false,
                        aiSolution = "Error during streaming: ${e.message}",
                        showSolution = true
                    )
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
                        colorHex = if (drawingPath.isEraser) "#TRANSPARENT" else colorToHex(drawingPath.color),
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
                            color = if (serializablePath.isEraser) Color.Transparent else hexToColor(serializablePath.colorHex),
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
            if (hex == "#TRANSPARENT") {
                Color.Transparent
            } else {
                Color(android.graphics.Color.parseColor(hex))
            }
        } catch (e: Exception) {
            Color.Black
        }
    }
    
    // Helper functions for future Gemma AI integration
    
    /**
     * Export drawing as bitmap for AI processing with comprehensive debugging
     */
    fun exportDrawingAsBitmap(width: Int = 1024, height: Int = 1024): android.graphics.Bitmap? {
        return try {
            val startTime = System.currentTimeMillis()
            Log.d("DrawingViewModel", "🎨 === BITMAP EXPORT START ===")
            
            // Log drawing content analysis
            Log.d("DrawingViewModel", "📊 Drawing content analysis:")
            Log.d("DrawingViewModel", "   - Paths: ${_uiState.value.drawingPaths.size}")
            Log.d("DrawingViewModel", "   - Shapes: ${_uiState.value.shapes.size}")
            Log.d("DrawingViewModel", "   - Text elements: ${_uiState.value.textElements.size}")
            
            // Validate drawing content
            if (_uiState.value.drawingPaths.isEmpty() && 
                _uiState.value.shapes.isEmpty() && 
                _uiState.value.textElements.isEmpty()) {
                Log.w("DrawingViewModel", "⚠️ No drawing content found! Bitmap will be blank.")
                return null
            }
            
            // Create bitmap with proper configuration
            val bitmap = android.graphics.Bitmap.createBitmap(
                width, 
                height, 
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            
            // Fill with white background
            canvas.drawColor(android.graphics.Color.WHITE)
            Log.d("DrawingViewModel", "✅ Canvas created: ${width}x${height}, white background applied")
            
            // Calculate coordinate bounds for scaling
            val bounds = calculateDrawingBounds()
            Log.d("DrawingViewModel", "📏 Drawing bounds: ${bounds}")
            
                         // Calculate scaling factors
             val scaleX = if (bounds.width() > 0) (width * 0.9f) / bounds.width() else 1f
             val scaleY = if (bounds.height() > 0) (height * 0.9f) / bounds.height() else 1f
             val scale = minOf(scaleX, scaleY)
             
             // Calculate offset to center the drawing
             val offsetX = (width - bounds.width() * scale) / 2 - bounds.left * scale
             val offsetY = (height - bounds.height() * scale) / 2 - bounds.top * scale
            
            Log.d("DrawingViewModel", "🔧 Scaling: scale=$scale, offset=(${offsetX}, ${offsetY})")
            
            // Apply transformation
            canvas.save()
            canvas.translate(offsetX, offsetY)
            canvas.scale(scale, scale)
            
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
            
            var elementsDrawn = 0
            
            // Draw all paths with detailed logging
            _uiState.value.drawingPaths.forEachIndexed { index, drawingPath ->
                if (drawingPath.points.isNotEmpty()) {
                    try {
                        // Convert Compose Color to Android Color properly
                        val androidColor = convertComposeColorToAndroid(drawingPath.color)
                        paint.color = androidColor
                        paint.strokeWidth = drawingPath.strokeWidth
                        
                        // Handle eraser mode
                        if (drawingPath.isEraser) {
                            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                        } else {
                            paint.xfermode = null
                        }
                        
                        val path = android.graphics.Path()
                        path.moveTo(drawingPath.points.first().x, drawingPath.points.first().y)
                        drawingPath.points.drop(1).forEach { point ->
                            path.lineTo(point.x, point.y)
                        }
                        canvas.drawPath(path, paint)
                        elementsDrawn++
                        
                        Log.d("DrawingViewModel", "✏️ Path $index: ${drawingPath.points.size} points, " +
                                "color=#${Integer.toHexString(androidColor)}, " +
                                "strokeWidth=${drawingPath.strokeWidth}, " +
                                "isEraser=${drawingPath.isEraser}")
                    } catch (e: Exception) {
                        Log.e("DrawingViewModel", "💥 Error drawing path $index: ${e.message}")
                    }
                }
            }
            
            // Reset xfermode for shapes and text
            paint.xfermode = null
            
            // Draw all shapes with detailed logging
            _uiState.value.shapes.forEachIndexed { index, shape ->
                try {
                    val androidColor = convertComposeColorToAndroid(shape.color)
                    paint.color = androidColor
                    paint.strokeWidth = shape.strokeWidth
                    paint.style = if (shape.filled) android.graphics.Paint.Style.FILL else android.graphics.Paint.Style.STROKE
                    
                    when (shape.type) {
                        ShapeType.RECTANGLE -> {
                            canvas.drawRect(
                                minOf(shape.startX, shape.endX),
                                minOf(shape.startY, shape.endY),
                                maxOf(shape.startX, shape.endX),
                                maxOf(shape.startY, shape.endY),
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
                    elementsDrawn++
                    
                    Log.d("DrawingViewModel", "🔸 Shape $index: ${shape.type}, " +
                            "bounds=(${shape.startX},${shape.startY})-(${shape.endX},${shape.endY}), " +
                            "color=#${Integer.toHexString(androidColor)}, " +
                            "strokeWidth=${shape.strokeWidth}, filled=${shape.filled}")
                } catch (e: Exception) {
                    Log.e("DrawingViewModel", "💥 Error drawing shape $index: ${e.message}")
                }
            }
            
            // Draw all text elements with detailed logging
            _uiState.value.textElements.forEachIndexed { index, textElement ->
                try {
                    val androidColor = convertComposeColorToAndroid(textElement.color)
                    paint.color = androidColor
                    paint.style = android.graphics.Paint.Style.FILL
                    paint.textSize = textElement.fontSize
                    paint.strokeWidth = 0f
                    
                    canvas.drawText(textElement.text, textElement.x, textElement.y, paint)
                    elementsDrawn++
                    
                    Log.d("DrawingViewModel", "📝 Text $index: '${textElement.text}' " +
                            "at (${textElement.x},${textElement.y}), " +
                            "color=#${Integer.toHexString(androidColor)}, " +
                            "fontSize=${textElement.fontSize}")
                } catch (e: Exception) {
                    Log.e("DrawingViewModel", "💥 Error drawing text $index: ${e.message}")
                }
            }
            
            canvas.restore()
            
            val exportTime = System.currentTimeMillis() - startTime
            Log.d("DrawingViewModel", "✅ Bitmap export completed in ${exportTime}ms")
            Log.d("DrawingViewModel", "📊 Total elements drawn: $elementsDrawn")
            
            // Analyze bitmap for blank detection
            val pixelAnalysis = analyzeBitmapPixels(bitmap)
            Log.d("DrawingViewModel", "🔍 Pixel analysis: $pixelAnalysis")
            

            
            return bitmap
            
        } catch (e: Exception) {
            Log.e("DrawingViewModel", "💥 Error exporting bitmap: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Calculate the bounds of all drawing elements
     */
    private fun calculateDrawingBounds(): android.graphics.RectF {
        val bounds = android.graphics.RectF()
        var hasContent = false
        
        // Include all path points
        _uiState.value.drawingPaths.forEach { path ->
            path.points.forEach { point ->
                if (!hasContent) {
                    bounds.set(point.x, point.y, point.x, point.y)
                    hasContent = true
                } else {
                    bounds.union(point.x, point.y)
                }
            }
        }
        
        // Include all shapes
        _uiState.value.shapes.forEach { shape ->
            val left = minOf(shape.startX, shape.endX)
            val top = minOf(shape.startY, shape.endY)
            val right = maxOf(shape.startX, shape.endX)
            val bottom = maxOf(shape.startY, shape.endY)
            
            if (!hasContent) {
                bounds.set(left, top, right, bottom)
                hasContent = true
            } else {
                bounds.union(left, top, right, bottom)
            }
        }
        
        // Include all text elements
        _uiState.value.textElements.forEach { text ->
            if (!hasContent) {
                bounds.set(text.x, text.y, text.x, text.y)
                hasContent = true
            } else {
                bounds.union(text.x, text.y)
            }
        }
        
        // Add padding
        if (hasContent) {
            bounds.inset(-50f, -50f)
        }
        
        return bounds
    }
    
         /**
      * Convert Compose Color to Android Color properly
      */
     private fun convertComposeColorToAndroid(composeColor: Color): Int {
         val colorValue = composeColor.value
         return when {
             // Handle standard colors
             (colorValue.toLong() and 0xFF000000L) == 0xFF000000L -> {
                 // Color has alpha channel, use as-is
                 colorValue.toLong().toInt()
             }
             else -> {
                 // Add full alpha if missing
                 (0xFF000000L or (colorValue.toLong() and 0xFFFFFFL)).toInt()
             }
         }
     }
    
    /**
     * Analyze bitmap pixels to detect if it's blank
     */
    private fun analyzeBitmapPixels(bitmap: android.graphics.Bitmap): String {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        
        var whitePixels = 0
        var nonWhitePixels = 0
        var transparentPixels = 0
        
        // Sample pixels (check every 10th pixel to avoid performance issues)
        for (y in 0 until height step 10) {
            for (x in 0 until width step 10) {
                val pixel = bitmap.getPixel(x, y)
                when {
                    pixel == android.graphics.Color.WHITE -> whitePixels++
                    pixel == android.graphics.Color.TRANSPARENT -> transparentPixels++
                    else -> nonWhitePixels++
                }
            }
        }
        
        val sampledPixels = whitePixels + nonWhitePixels + transparentPixels
        
        return "sampled:$sampledPixels, white:$whitePixels, nonWhite:$nonWhitePixels, transparent:$transparentPixels"
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

    /**
     * Estimate token count for text (rough approximation)
     */
    private fun estimateTokenCount(text: String): Int {
        // Rough estimation: ~4 characters per token on average
        return (text.length / 4).coerceAtLeast(1)
    }
    
    /**
     * Estimate token count for images based on patch-based processing
     */
    private fun estimateImageTokenCount(width: Int, height: Int, patchSize: Int = 16): Int {
        // Vision transformers typically use 16x16 patches
        val patchesX = (width + patchSize - 1) / patchSize
        val patchesY = (height + patchSize - 1) / patchSize
        val totalPatches = patchesX * patchesY
        
        // Add some overhead for special tokens (CLS, SEP, etc.)
        return totalPatches + 10
    }

    private fun createImageBasedSolvingPrompt(): String {
        return """
            ###  SYSTEM  ###
           You are “Sketch-Solve”, an offline tutor that receives a image of a hand-drawn
           math or physics problem and must reply in following format:

           ###  OUTPUT FORMAT  ###
           ## Explanation
           <lay-person steps, crystal-clear, with one relatable example>
           ## Answer
           <concise, to the point, direct answer only>

           ###  STRICT RULES  ###
           1. Take your time and meticulously read and understand the problem. Work the problem step-by-step in your head. 
           2. Do NOT show that internal reasoning.
           3. Write the solution in a beautiful way that is easy to understand and follow with clear lay-person steps.

           ###  FEW-SHOT EXAMPLES  ###
           **Example 1 - Simple geometry**

           Hand-drawn diagram: (triangle with legs “3 cm” and “4 cm” labeled) 
           <assistant does hidden work> 
           OUTPUT:
           Explanation: This is a right-angled triangle. The area of any triangle is ½ x base x height.
                Here the two legs are the base (3 cm) and the height (4 cm): ½ x 3 x 4 = 6.  Picture folding a rectangle of toast (3 cm x 4 cm) along its diagonal - you keep exactly half of it.
            Answer: 6 cm²

           **Example 2 - Newton's 2nd law**

           Hand-drawn diagram: (block labeled “m = 2 kg”, arrow labeled “a = 3 m/s²” to the right) 
           <assistant does hidden work> 
           OUTPUT:
           
           Explanation: Newton's second law says force = mass x acceleration.  So 2 kg x 3 m/s² = 6 N.  It's like pushing a shopping cart: doubling the load doubles the push you feel.
           Answer: 6 N
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