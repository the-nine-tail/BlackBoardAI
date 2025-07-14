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
import com.example.blackboardai.domain.entity.DrawingPoint
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
    onErasePath: (DrawingPath) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPoints by remember { mutableStateOf<List<DrawingPoint>>(emptyList()) }
    var currentDrawingPath by remember { mutableStateOf<DrawingPath?>(null) }
    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var endPoint by remember { mutableStateOf<Offset?>(null) }
    var isDrawing by remember { mutableStateOf(false) }
    
    // More efficient recomposition trigger
    var drawingRevision by remember { mutableStateOf(0) }
    
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
                                // Start new path with initial point
                                val initialPoint = DrawingPoint(
                                    x = offset.x,
                                    y = offset.y,
                                    pressure = 1f,
                                    timestamp = System.currentTimeMillis()
                                )
                                currentPoints = listOf(initialPoint)
                                currentDrawingPath = DrawingPath(
                                    points = currentPoints,
                                    color = if (drawingMode == DrawingMode.ERASE) Color.Transparent else currentColor,
                                    strokeWidth = strokeWidth,
                                    isEraser = drawingMode == DrawingMode.ERASE
                                )
                                isDrawing = true
                                drawingRevision++
                            }
                            DrawingMode.SHAPE -> {
                                startPoint = offset
                                endPoint = offset
                                isDrawing = true
                                drawingRevision++
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
                    onDrag = { change, dragAmount ->
                        when (drawingMode) {
                            DrawingMode.DRAW, DrawingMode.ERASE -> {
                                if (isDrawing) {
                                    // Add new point with better distance filtering
                                    val newPoint = DrawingPoint(
                                        x = change.position.x,
                                        y = change.position.y,
                                        pressure = 1f,
                                        timestamp = System.currentTimeMillis()
                                    )
                                    
                                    // Only add point if it's far enough from the last point for smoother lines
                                    val lastPoint = currentPoints.lastOrNull()
                                    val shouldAddPoint = lastPoint == null || run {
                                        val distance = kotlin.math.sqrt(
                                            (newPoint.x - lastPoint.x) * (newPoint.x - lastPoint.x) +
                                            (newPoint.y - lastPoint.y) * (newPoint.y - lastPoint.y)
                                        )
                                        distance >= 1f // Minimum distance threshold for smoother lines (reduced for better sensitivity)
                                    }
                                    
                                    if (shouldAddPoint) {
                                        currentPoints = currentPoints + newPoint
                                        currentDrawingPath = DrawingPath(
                                            points = currentPoints,
                                            color = if (drawingMode == DrawingMode.ERASE) Color.Transparent else currentColor,
                                            strokeWidth = strokeWidth,
                                            isEraser = drawingMode == DrawingMode.ERASE
                                        )
                                        drawingRevision++
                                    }
                                }
                            }
                            DrawingMode.SHAPE -> {
                                if (isDrawing) {
                                    endPoint = change.position
                                    drawingRevision++
                                }
                            }
                            else -> {}
                        }
                    },
                    onDragEnd = {
                        when (drawingMode) {
                            DrawingMode.DRAW -> {
                                currentDrawingPath?.let { drawingPath ->
                                    onAddPath(drawingPath)
                                }
                                currentDrawingPath = null
                                isDrawing = false
                                // Reset the points for next drawing
                                currentPoints = emptyList()
                            }
                            DrawingMode.ERASE -> {
                                currentDrawingPath?.let { drawingPath ->
                                    onErasePath(drawingPath)
                                }
                                currentDrawingPath = null
                                isDrawing = false
                                // Reset the points for next drawing
                                currentPoints = emptyList()
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
        // Use drawingRevision to trigger recomposition efficiently
        drawingRevision
        
        // Draw all completed paths (skip eraser paths - they're for removal only)
        paths.forEach { drawingPath ->
            if (drawingPath.points.isNotEmpty() && !drawingPath.isEraser) {
                val path = drawingPath.toPath()
                drawPath(
                    path = path,
                    color = drawingPath.color,
                    style = Stroke(
                        width = drawingPath.strokeWidth,
                        cap = drawingPath.strokeCap,
                        join = drawingPath.strokeJoin
                    )
                )
            }
        }
        
        // Draw current path being drawn (show eraser as semi-transparent circle)
        currentDrawingPath?.let { drawingPath ->
            if (drawingPath.points.isNotEmpty()) {
                if (drawingPath.isEraser) {
                    // Show eraser indicator as semi-transparent circles
                    drawingPath.points.forEach { point ->
                        drawCircle(
                            color = Color.Red.copy(alpha = 0.3f),
                            radius = drawingPath.strokeWidth / 2,
                            center = Offset(point.x, point.y)
                        )
                    }
                } else {
                    // Draw normal path
                    drawPath(
                        path = drawingPath.toPath(),
                        color = drawingPath.color,
                        style = Stroke(
                            width = drawingPath.strokeWidth,
                            cap = drawingPath.strokeCap,
                            join = drawingPath.strokeJoin
                        )
                    )
                }
            }
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