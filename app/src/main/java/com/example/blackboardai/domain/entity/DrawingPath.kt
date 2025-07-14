package com.example.blackboardai.domain.entity

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin

data class DrawingPath(
    val points: List<DrawingPoint> = emptyList(), // Store actual points for perfect preservation
    val color: Color = Color.Black,
    val strokeWidth: Float = 5f,
    val strokeCap: StrokeCap = StrokeCap.Round,
    val strokeJoin: StrokeJoin = StrokeJoin.Round,
    val isEraser: Boolean = false
) {
    // Generate Path from points for rendering with optimization
    fun toPath(): Path {
        val path = Path()
        if (points.isNotEmpty()) {
            val firstPoint = points.first()
            path.moveTo(firstPoint.x, firstPoint.y)
            
            // Use quadratic curves for smoother lines when we have enough points
            if (points.size > 2) {
                var i = 1
                while (i < points.size - 1) {
                    val currentPoint = points[i]
                    val nextPoint = points[i + 1]
                    
                    // Create control point for smooth curve
                    val controlX = (currentPoint.x + nextPoint.x) / 2
                    val controlY = (currentPoint.y + nextPoint.y) / 2
                    
                    path.quadraticBezierTo(
                        currentPoint.x, currentPoint.y,
                        controlX, controlY
                    )
                    i++
                }
                // Connect to the last point
                if (points.size > 1) {
                    val lastPoint = points.last()
                    path.lineTo(lastPoint.x, lastPoint.y)
                }
            } else if (points.size == 2) {
                // Simple line for two points
                path.lineTo(points[1].x, points[1].y)
            }
            // Single point is just moveTo, no additional drawing needed
        }
        return path
    }
}

data class DrawingPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f, // For future pressure sensitivity
    val timestamp: Long = System.currentTimeMillis() // For stroke speed analysis
)

data class DrawingShape(
    val type: ShapeType,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val color: Color = Color.Black,
    val strokeWidth: Float = 5f,
    val filled: Boolean = false
)

enum class ShapeType {
    CIRCLE,
    RECTANGLE,
    LINE
}

data class TextElement(
    val text: String,
    val x: Float,
    val y: Float,
    val color: Color = Color.Black,
    val fontSize: Float = 16f
) 