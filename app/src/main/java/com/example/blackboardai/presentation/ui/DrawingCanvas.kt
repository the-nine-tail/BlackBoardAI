package com.example.blackboardai.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlin.math.sqrt
import com.example.blackboardai.domain.entity.DrawingPath
import com.example.blackboardai.domain.entity.DrawingShape
import com.example.blackboardai.domain.entity.ShapeType
import com.example.blackboardai.domain.entity.TextElement
import com.example.blackboardai.presentation.viewmodel.DrawingMode

@Composable
fun DrawingCanvas(
    paths: List<DrawingPath>,
    shapes: List<DrawingShape>,
    textElements: List<TextElement>,
    currentColor: Color,
    strokeWidth: Float,
    drawingMode: DrawingMode,
    selectedShape: ShapeType?,
    onAddPath: (DrawingPath) -> Unit,
    onAddShape: (DrawingShape) -> Unit,
    onAddText: (TextElement) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPath by remember { mutableStateOf(Path()) }
    var currentDrawingPath by remember { mutableStateOf<DrawingPath?>(null) }
    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var endPoint by remember { mutableStateOf<Offset?>(null) }
    var isDrawing by remember { mutableStateOf(false) }
    
    // Force recomposition trigger for real-time drawing
    var pathUpdateTrigger by remember { mutableStateOf(0) }
    
    val density = LocalDensity.current
    
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .clipToBounds()
            .pointerInput(drawingMode, selectedShape, currentColor, strokeWidth) {
                detectDragGestures(
                    onDragStart = { offset ->
                        when (drawingMode) {
                            DrawingMode.DRAW, DrawingMode.ERASE -> {
                                currentPath = Path()
                                currentPath.moveTo(offset.x, offset.y)
                                currentDrawingPath = DrawingPath(
                                    path = currentPath,
                                    color = if (drawingMode == DrawingMode.ERASE) Color.White else currentColor,
                                    strokeWidth = strokeWidth,
                                    isEraser = drawingMode == DrawingMode.ERASE
                                )
                                isDrawing = true
                                // Trigger initial recomposition
                                pathUpdateTrigger++
                            }
                            DrawingMode.SHAPE -> {
                                startPoint = offset
                                endPoint = offset
                                isDrawing = true
                                // Trigger initial recomposition for shape preview
                                pathUpdateTrigger++
                            }
                            DrawingMode.TEXT -> {
                                // Text input handling would be done separately
                                // For now, we'll add a placeholder text
                                onAddText(
                                    TextElement(
                                        text = "Sample Text",
                                        x = offset.x,
                                        y = offset.y,
                                        color = currentColor
                                    )
                                )
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        when (drawingMode) {
                            DrawingMode.DRAW, DrawingMode.ERASE -> {
                                if (isDrawing) {
                                    // Create a new path to ensure recomposition
                                    val newPath = Path().apply {
                                        addPath(currentPath)
                                        lineTo(change.position.x, change.position.y)
                                    }
                                    currentPath = newPath
                                    currentDrawingPath = DrawingPath(
                                        path = newPath,
                                        color = if (drawingMode == DrawingMode.ERASE) Color.White else currentColor,
                                        strokeWidth = strokeWidth,
                                        isEraser = drawingMode == DrawingMode.ERASE
                                    )
                                    // Trigger recomposition for real-time drawing
                                    pathUpdateTrigger++
                                }
                            }
                            DrawingMode.SHAPE -> {
                                if (isDrawing) {
                                    endPoint = change.position
                                    // Trigger recomposition for real-time shape preview
                                    pathUpdateTrigger++
                                }
                            }
                            else -> {}
                        }
                    },
                    onDragEnd = {
                        when (drawingMode) {
                            DrawingMode.DRAW, DrawingMode.ERASE -> {
                                currentDrawingPath?.let { drawingPath ->
                                    onAddPath(drawingPath)
                                }
                                currentDrawingPath = null
                                isDrawing = false
                                // Reset the path for next drawing
                                currentPath = Path()
                            }
                            DrawingMode.SHAPE -> {
                                val start = startPoint
                                val end = endPoint
                                if (start != null && end != null && selectedShape != null) {
                                    onAddShape(
                                        DrawingShape(
                                            type = selectedShape,
                                            startX = start.x,
                                            startY = start.y,
                                            endX = end.x,
                                            endY = end.y,
                                            color = currentColor,
                                            strokeWidth = strokeWidth
                                        )
                                    )
                                }
                                startPoint = null
                                endPoint = null
                                isDrawing = false
                            }
                            else -> {}
                        }
                    }
                )
            }
    ) {
        // Use pathUpdateTrigger to force recomposition for real-time drawing
        pathUpdateTrigger
        
        // Draw all completed paths
        paths.forEach { drawingPath ->
            drawPath(
                path = drawingPath.path,
                color = drawingPath.color,
                style = Stroke(
                    width = drawingPath.strokeWidth,
                    cap = drawingPath.strokeCap,
                    join = drawingPath.strokeJoin
                )
            )
        }
        
        // Draw current path being drawn
        currentDrawingPath?.let { drawingPath ->
            drawPath(
                path = drawingPath.path,
                color = drawingPath.color,
                style = Stroke(
                    width = drawingPath.strokeWidth,
                    cap = drawingPath.strokeCap,
                    join = drawingPath.strokeJoin
                )
            )
        }
        
        // Draw all shapes
        shapes.forEach { shape ->
            drawShape(shape)
        }
        
        // Draw preview shape while dragging
        if (isDrawing && drawingMode == DrawingMode.SHAPE && selectedShape != null) {
            val start = startPoint
            val end = endPoint
            if (start != null && end != null) {
                val previewShape = DrawingShape(
                    type = selectedShape,
                    startX = start.x,
                    startY = start.y,
                    endX = end.x,
                    endY = end.y,
                    color = currentColor.copy(alpha = 0.7f),
                    strokeWidth = strokeWidth
                )
                drawShape(previewShape)
            }
        }
        
        // Draw all text elements
        textElements.forEach { textElement ->
            drawContext.canvas.nativeCanvas.drawText(
                textElement.text,
                textElement.x,
                textElement.y,
                android.graphics.Paint().apply {
                    color = textElement.color.toArgb()
                    textSize = with(density) { textElement.fontSize.dp.toPx() }
                    isAntiAlias = true
                }
            )
        }
    }
}

private fun DrawScope.drawShape(shape: DrawingShape) {
    val paint = Stroke(width = shape.strokeWidth)
    
    when (shape.type) {
        ShapeType.RECTANGLE -> {
            drawRect(
                color = shape.color,
                topLeft = Offset(
                    kotlin.math.min(shape.startX, shape.endX),
                    kotlin.math.min(shape.startY, shape.endY)
                ),
                size = androidx.compose.ui.geometry.Size(
                    kotlin.math.abs(shape.endX - shape.startX),
                    kotlin.math.abs(shape.endY - shape.startY)
                ),
                style = if (shape.filled) androidx.compose.ui.graphics.drawscope.Fill else paint
            )
        }
        ShapeType.CIRCLE -> {
            val center = Offset(
                (shape.startX + shape.endX) / 2,
                (shape.startY + shape.endY) / 2
            )
            val radius = sqrt(
                (shape.endX - shape.startX).toDouble().pow(2.0) +
                (shape.endY - shape.startY).toDouble().pow(2.0)
            ).toFloat() / 2
            
            drawCircle(
                color = shape.color,
                radius = radius,
                center = center,
                style = if (shape.filled) androidx.compose.ui.graphics.drawscope.Fill else paint
            )
        }
        ShapeType.LINE -> {
            drawLine(
                color = shape.color,
                start = Offset(shape.startX, shape.startY),
                end = Offset(shape.endX, shape.endY),
                strokeWidth = shape.strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
} 