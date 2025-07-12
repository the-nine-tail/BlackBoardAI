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
import androidx.compose.ui.unit.dp
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
        AISolutionDialog(
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
private fun AISolutionDialog(
    solution: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "AI Solution",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    SelectionContainer {
                        Text(
                            text = solution,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close")
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
} 