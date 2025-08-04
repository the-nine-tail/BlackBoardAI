package com.example.blackboardai.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun CustomSolutionDialog(
    solution: String,
    isStreaming: Boolean = false,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    
    val listState = rememberLazyListState()
    LaunchedEffect(solution, isStreaming) {
        if (isStreaming || solution.isNotEmpty()) {
            listState.animateScrollToItem(1)
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false // This allows custom width
        )
    ) {
        Surface(
            modifier = Modifier
                .width(screenWidth * 0.8f) // Use 80% of screen width
                .heightIn(max = screenHeight * 0.8f) // Maximum 80% of screen height
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header with title and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "AI Solution",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        // Show streaming indicator only when actually streaming
                        if (isStreaming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Content area with scrollable solution
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        SelectionContainer {
                            SimpleMarkdownText(
                                markdown = solution.ifEmpty { 
                                    "🧠 Analyzing your drawing...\n\nPlease wait while I process the image and generate a solution." 
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Close button at bottom
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Close",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
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
        // Process markdown line by line
        val lines = markdown.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            
            when {
                // Headers
                line.startsWith("## ") && line.length > 3 -> {
                    Text(
                        text = line.substring(3),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                line.startsWith("# ") && line.length > 2 -> {
                    Text(
                        text = line.substring(2),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                
                // Horizontal rule
                line == "---" -> {
                    Divider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline
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