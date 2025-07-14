package com.example.blackboardai.presentation.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blackboardai.presentation.viewmodel.DrawingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(
    onNavigateBack: () -> Unit,
    noteId: Long = 0L,
    viewModel: DrawingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showTitleDialog by remember { mutableStateOf(false) }
    
    // Force landscape orientation and load note if editing
    LaunchedEffect(noteId) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        // Load existing note if noteId is provided
        if (noteId > 0) {
            viewModel.loadNote(noteId)
        }
    }
    
    // Reset orientation when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? Activity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    
    if (showTitleDialog) {
        TitleInputDialog(
            currentTitle = uiState.title,
            onTitleChanged = viewModel::updateTitle,
            onDismiss = { showTitleDialog = false },
            onConfirm = {
                showTitleDialog = false
                viewModel.saveNote(
                    onSuccess = { noteId ->
                        Toast.makeText(context, "Note saved successfully!", Toast.LENGTH_SHORT).show()
                        onNavigateBack()
                    },
                    onError = { error ->
                        Toast.makeText(context, "Error: $error", Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }
    
    // AI Solution Dialog
    if (uiState.showSolution) {
        CustomSolutionDialog(
            solution = uiState.aiSolution,
            onDismiss = viewModel::dismissSolution
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Drawing toolbar with navigation at top
        DrawingToolbar(
            currentColor = uiState.currentColor,
            strokeWidth = uiState.strokeWidth,
            drawingMode = uiState.drawingMode,
            selectedShape = uiState.selectedShape,
            title = if (uiState.title.isBlank()) "New Note" else uiState.title,
            isSaving = uiState.isSaving,
            isSolving = uiState.isSolving,
            onColorSelected = viewModel::updateCurrentColor,
            onStrokeWidthChanged = viewModel::updateStrokeWidth,
            onDrawingModeChanged = viewModel::updateDrawingMode,
            onShapeSelected = viewModel::updateSelectedShape,
            onUndo = viewModel::undo,
            onClear = viewModel::clearCanvas,
            onNavigateBack = onNavigateBack,
            onSave = { showTitleDialog = true },
            onSolve = viewModel::solveWithAI,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Full screen drawing canvas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            DrawingCanvas(
                paths = uiState.drawingPaths,
                shapes = uiState.shapes,
                textElements = uiState.textElements,
                currentColor = uiState.currentColor,
                strokeWidth = uiState.strokeWidth,
                drawingMode = uiState.drawingMode,
                selectedShape = uiState.selectedShape,
                onAddPath = viewModel::addPath,
                onAddShape = viewModel::addShape,
                onAddText = viewModel::addText,
                onErasePath = viewModel::erasePath,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun TitleInputDialog(
    currentTitle: String,
    onTitleChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var title by remember { mutableStateOf(currentTitle) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Save Note",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter a title for your note:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Note Title") },
                    placeholder = { Text("Untitled Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onTitleChanged(title.ifBlank { "Untitled Note" })
                    onConfirm()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}



@Composable
private fun SimpleMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    // Handle empty or whitespace-only content
    if (markdown.isBlank()) {
        Text(
            text = "🧠 Analyzing your drawing...\n\nPlease wait while I process the image and generate a solution.",
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        return
    }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val lines = markdown.split('\n')
        var i = 0
        
        while (i < lines.size) {
            val line = lines[i].trim()
            
            // Skip empty lines that might cause issues
            if (line.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                i++
                continue
            }
            
            when {
                // Headers - with safe substring
                line.startsWith("### ") && line.length > 4 -> {
                    Text(
                        text = line.substring(4),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                line.startsWith("## ") && line.length > 3 -> {
                    Text(
                        text = line.substring(3),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                line.startsWith("# ") && line.length > 2 -> {
                    Text(
                        text = line.substring(2),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Horizontal rule
                line == "---" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(vertical = 8.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    )
                }
                
                // Bullet points - with safe substring
                line.startsWith("- ") && line.length > 2 -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        MarkdownStyledText(
                            text = line.substring(2),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Blockquotes - with safe substring
                line.startsWith("> ") && line.length > 2 -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(20.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = MaterialTheme.shapes.small
                                    )
                            )
                            MarkdownStyledText(
                                text = line.substring(2),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                
                // Code blocks (single line with backticks) - with safe substring
                line.startsWith("`") && line.endsWith("`") && line.length > 2 -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = line.substring(1, line.length - 1),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Regular text with inline formatting
                else -> {
                    MarkdownStyledText(text = line)
                }
            }
            i++
        }
    }
}

@Composable
private fun MarkdownStyledText(
    text: String,
    modifier: Modifier = Modifier
) {
    // Safely handle text with bold formatting - process outside compose
    val processedText = remember(text) {
        try {
            if (text.contains("**") && text.split("**").size > 1) {
                text.split("**")
            } else {
                listOf(text) // Return single item list for plain text
            }
        } catch (e: Exception) {
            listOf(text) // Fallback to plain text
        }
    }
    
    // Render based on processed result
    if (processedText.size > 1) {
        // Has bold formatting - render as styled text
        Row(modifier = modifier) {
            processedText.forEachIndexed { index, part ->
                if (part.isNotEmpty()) { // Only render non-empty parts
                    Text(
                        text = part,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (index % 2 == 1) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    } else {
        // Regular text - render as plain text
        Text(
            text = text,
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
} 