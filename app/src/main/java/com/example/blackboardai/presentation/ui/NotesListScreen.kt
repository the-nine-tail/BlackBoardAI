package com.example.blackboardai.presentation.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blackboardai.data.ai.GoogleAIService
import com.example.blackboardai.data.ai.ModelStatus
import com.example.blackboardai.domain.entity.Note
import com.example.blackboardai.presentation.overlay.OverlayManager
import com.example.blackboardai.presentation.viewmodel.NotesViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    overlayManager: OverlayManager,
    onCreateNote: () -> Unit,
    onNoteClick: (Long) -> Unit,
    viewModel: NotesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    // Show diagnostic results as dialog
    uiState.error?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { 
                Text(
                    text = if (errorMessage.startsWith("✅")) "Diagnostic Result" else "Error",
                    color = if (errorMessage.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            },
            text = { 
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Notes",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Model Diagnostics Button
                    var showDiagnosticsDialog by remember { mutableStateOf(false) }
                    
                    IconButton(
                        onClick = { showDiagnosticsDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Model Diagnostics",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    if (showDiagnosticsDialog) {
                        ModelDiagnosticsDialog(
                            onDismiss = { showDiagnosticsDialog = false },
                            onCheckIntegrity = {
                                viewModel.checkModelIntegrity()
                                showDiagnosticsDialog = false
                            },
                            onForceReset = {
                                viewModel.forceCompleteReset()
                                showDiagnosticsDialog = false
                            }
                        )
                    }
                    
                    // Smart Overlay Button (Toggle Start/Stop)
                    IconButton(
                        onClick = {
                            if (uiState.isModelReady) {
                                if (overlayManager.isOverlayActive()) {
                                    overlayManager.stopSmartOverlay()
                                    Toast.makeText(context, "Smart overlay stopped", Toast.LENGTH_SHORT).show()
                                } else {
                                    overlayManager.startSmartOverlay()
                                }
                            } else {
                                Toast.makeText(context, "AI model is still loading. Please wait...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = uiState.isModelReady
                    ) {
                        Icon(
                            imageVector = if (overlayManager.isOverlayActive()) Icons.Default.Stop else Icons.Default.ScreenShare,
                            contentDescription = if (overlayManager.isOverlayActive()) "Stop Overlay" else "Smart Overlay",
                            tint = if (uiState.isModelReady) {
                                if (overlayManager.isOverlayActive()) 
                                    MaterialTheme.colorScheme.error
                                else 
                                    MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (uiState.isModelReady) {
                        onCreateNote()
                    }
                },
                containerColor = if (uiState.isModelReady) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.outline,
                content = {
                    if (uiState.isModelReady) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Note",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.notes.isEmpty() -> {
                    EmptyNotesState(
                        onCreateNote = onCreateNote,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Show model status banner if not ready
                        if (!uiState.isModelReady) {
                            item {
                                ModelStatusBanner(
                                    modelStatus = uiState.modelStatus,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        
                        items(
                            items = uiState.notes,
                            key = { note -> note.id }
                        ) { note ->
                            NoteCard(
                                note = note,
                                onClick = { 
                                    if (uiState.isModelReady) {
                                        onNoteClick(note.id) 
                                    }
                                },
                                onDelete = { viewModel.deleteNote(note.id) },
                                isEnabled = uiState.isModelReady
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelStatusBanner(
    modelStatus: ModelStatus,
    modifier: Modifier = Modifier
) {
    val (message, icon, color) = when (modelStatus) {
        ModelStatus.NOT_INITIALIZED, ModelStatus.CHECKING_MODEL -> 
            Triple("AI is starting up...", Icons.Default.Psychology, MaterialTheme.colorScheme.primary)
        ModelStatus.DOWNLOADING_MODEL -> 
            Triple("Downloading AI model...", Icons.Default.Psychology, MaterialTheme.colorScheme.primary)
        ModelStatus.EXTRACTING_MODEL -> 
            Triple("Extracting AI model...", Icons.Default.Psychology, MaterialTheme.colorScheme.primary)
        ModelStatus.INITIALIZING_INFERENCE, ModelStatus.CREATING_SESSION, ModelStatus.WARMING_UP -> 
            Triple("Setting up AI engine...", Icons.Default.Psychology, MaterialTheme.colorScheme.primary)
        ModelStatus.ERROR -> 
            Triple("AI setup failed. Some features may not work.", Icons.Default.Psychology, MaterialTheme.colorScheme.error)
        ModelStatus.READY -> 
            Triple("", Icons.Default.Psychology, MaterialTheme.colorScheme.primary) // Should not show this banner when ready
    }
    
    if (modelStatus != ModelStatus.READY) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = color.copy(alpha = 0.1f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, 
                color.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (modelStatus == ModelStatus.ERROR) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = color
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Setup",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = color
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun EmptyNotesState(
    onCreateNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No notes yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create your first note to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onCreateNote,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Note")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    isEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Note") },
            text = { Text("Are you sure you want to delete \"${note.title}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    Card(
        onClick = if (isEnabled) onClick else {{}},
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) 
                MaterialTheme.colorScheme.surface 
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (note.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = note.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                IconButton(
                    onClick = { showDeleteDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Note",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDate(note.updatedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = formatSize(note.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDate(instant: kotlinx.datetime.Instant): String {
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDateTime.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${localDateTime.dayOfMonth}, ${localDateTime.year}"
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}

@Composable
private fun ModelDiagnosticsDialog(
    onDismiss: () -> Unit,
    onCheckIntegrity: () -> Unit,
    onForceReset: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Model Diagnostics")
            }
        },
        text = { 
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Use these tools to diagnose and fix AI model issues:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Text(
                    text = "• Check Integrity: Verify model file is not corrupted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "• Force Reset: Delete all model files and force re-download",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "⚠️ Use Force Reset if model was working before but suddenly stopped",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCheckIntegrity
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Check Integrity")
                }
                
                Button(
                    onClick = onForceReset,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Force Reset")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
} 