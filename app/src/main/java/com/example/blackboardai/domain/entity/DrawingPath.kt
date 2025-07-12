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
    // Generate Path from points for rendering
    fun toPath(): Path {
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