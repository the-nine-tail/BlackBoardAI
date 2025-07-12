package com.example.blackboardai.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.blackboardai.domain.entity.ShapeType
import com.example.blackboardai.presentation.viewmodel.DrawingMode

@Composable
fun DrawingToolbar(
    currentColor: Color,
    strokeWidth: Float,
    drawingMode: DrawingMode,
    selectedShape: ShapeType?,
    title: String,
    isSaving: Boolean,
    isSolving: Boolean = false,
    onColorSelected: (Color) -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    onDrawingModeChanged: (DrawingMode) -> Unit,
    onShapeSelected: (ShapeType?) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onNavigateBack: () -> Unit,
    onSave: () -> Unit,
    onSolve: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOptions by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column {
            // Main toolbar row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Navigation and title section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Back button
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    // Title
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(120.dp)
                    )
                    
                    // Save button
                    IconButton(
                        onClick = onSave,
                        enabled = !isSaving,
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save Note",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    // Solve button (AI-powered problem solving)
                    IconButton(
                        onClick = onSolve,
                        enabled = !isSolving,
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (isSolving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Psychology, // Brain icon for AI solving
                                contentDescription = "Solve with AI",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    
                    // Separator
                    Divider(
                        modifier = Modifier
                            .height(32.dp)
                            .width(1.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }
                
                // Drawing mode buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactModeButton(
                        icon = Icons.Default.Edit,
                        isSelected = drawingMode == DrawingMode.DRAW,
                        onClick = {
                            onDrawingModeChanged(DrawingMode.DRAW)
                            onShapeSelected(null)
                        }
                    )
                    
                    CompactModeButton(
                        icon = Icons.Default.Clear,
                        isSelected = drawingMode == DrawingMode.ERASE,
                        onClick = {
                            onDrawingModeChanged(DrawingMode.ERASE)
                            onShapeSelected(null)
                        }
                    )
                    
                    CompactModeButton(
                        icon = Icons.Default.Interests,
                        isSelected = drawingMode == DrawingMode.SHAPE,
                        onClick = {
                            onDrawingModeChanged(DrawingMode.SHAPE)
                            if (selectedShape == null) {
                                onShapeSelected(ShapeType.CIRCLE)
                            }
                        }
                    )
                    
                    CompactModeButton(
                        icon = Icons.Default.TextFields,
                        isSelected = drawingMode == DrawingMode.TEXT,
                        onClick = {
                            onDrawingModeChanged(DrawingMode.TEXT)
                            onShapeSelected(null)
                        }
                    )
                    
                    // Separator
                    Spacer(modifier = Modifier.width(8.dp))
                    Divider(
                        modifier = Modifier
                            .height(32.dp)
                            .width(1.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Shape selection (only visible in shape mode)
                    if (drawingMode == DrawingMode.SHAPE) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CompactShapeButton(
                                label = "○",
                                isSelected = selectedShape == ShapeType.CIRCLE,
                                onClick = { onShapeSelected(ShapeType.CIRCLE) }
                            )
                            CompactShapeButton(
                                label = "□",
                                isSelected = selectedShape == ShapeType.RECTANGLE,
                                onClick = { onShapeSelected(ShapeType.RECTANGLE) }
                            )
                            CompactShapeButton(
                                label = "—",
                                isSelected = selectedShape == ShapeType.LINE,
                                onClick = { onShapeSelected(ShapeType.LINE) }
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Divider(
                            modifier = Modifier
                                .height(32.dp)
                                .width(1.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    // Color selector
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { showOptions = !showOptions }
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Stroke width display
                    Surface(
                        onClick = { showOptions = !showOptions },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${strokeWidth.toInt()}px",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                // Push action buttons to the right
                Spacer(modifier = Modifier.weight(1f))
                
                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onUndo,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = "Undo",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear All",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    IconButton(
                        onClick = { showOptions = !showOptions },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (showOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "More Options",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            // Expandable options panel
            if (showOptions) {
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Color palette
                    Text(
                        text = "Colors",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ColorPalette(
                        selectedColor = currentColor,
                        onColorSelected = { color ->
                            onColorSelected(color)
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Stroke width slider
                    Text(
                        text = "Stroke Width: ${strokeWidth.toInt()}px",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Slider(
                        value = strokeWidth,
                        onValueChange = onStrokeWidthChanged,
                        valueRange = 1f..50f,
                        steps = 49,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactModeButton(
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Surface(
        onClick = onClick,
        modifier = modifier.size(36.dp),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = contentColor
            )
        }
    }
}

@Composable
private fun CompactShapeButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Surface(
        onClick = onClick,
        modifier = modifier.size(32.dp),
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ColorPalette(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = listOf(
        Color.Black,
        Color.Red,
        Color.Blue,
        Color.Green,
        Color.Yellow,
        Color.Magenta,
        Color.Cyan,
        Color(0xFF800080), // Purple
        Color(0xFFFFA500), // Orange
        Color(0xFF8B4513), // Brown
        Color.Gray,
        Color.White
    )
    
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(colors) { color ->
            val isSelected = color == selectedColor
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(color) }
            )
        }
    }
} 