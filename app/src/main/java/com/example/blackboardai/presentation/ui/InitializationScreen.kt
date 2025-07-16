package com.example.blackboardai.presentation.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blackboardai.data.ai.ModelStatus
import com.example.blackboardai.presentation.viewmodel.AppInitializationViewModel

@Composable
fun InitializationScreen(
    onInitializationComplete: () -> Unit,
    viewModel: AppInitializationViewModel = hiltViewModel()
) {
    val state by viewModel.initializationState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    // Permission state
    var hasStoragePermission by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var permissionDeniedPermanently by remember { mutableStateOf(false) }
    
    // Check initial permission state
    LaunchedEffect(Unit) {
        hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        
        // If we have permission, start initialization
        if (hasStoragePermission) {
            viewModel.startInitializationWithPermission()
        }
    }
    
    // Permission launcher for Android 10 and below
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val writeGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
        val readGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        
        hasStoragePermission = writeGranted && readGranted
        
        if (hasStoragePermission) {
            showPermissionRationale = false
            viewModel.startInitializationWithPermission()
        } else {
            // Check if user denied permanently
            permissionDeniedPermanently = !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                context as androidx.activity.ComponentActivity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            showPermissionRationale = true
        }
    }
    
    // Permission launcher for Android 11+ (MANAGE_EXTERNAL_STORAGE)
    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasStoragePermission = android.os.Environment.isExternalStorageManager()
            if (hasStoragePermission) {
                showPermissionRationale = false
                viewModel.startInitializationWithPermission()
            } else {
                showPermissionRationale = true
                permissionDeniedPermanently = true
            }
        }
    }
    
    // Function to request storage permissions
    fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - Request MANAGE_EXTERNAL_STORAGE
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                // Fallback to general manage external storage settings
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(intent)
            }
        } else {
            // Android 10 and below - Request legacy storage permissions
            legacyPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
    
    // Navigate to main app when initialization is complete and user is ready
    LaunchedEffect(state.canProceed) {
        if (state.canProceed) {
            onInitializationComplete()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Logo/Icon
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = "BlackBoard AI",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // App Title
            Text(
                text = "BlackBoard AI",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "AI-Powered Problem Solving",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Show permission request if not granted
            if (!hasStoragePermission) {
                PermissionRequestCard(
                    showRationale = showPermissionRationale,
                    deniedPermanently = permissionDeniedPermanently,
                    onRequestPermission = { requestStoragePermissions() },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                )
            } else {
                // Initialization Status Card (only show when we have permission)
                InitializationStatusCard(
                    state = state,
                    onRetry = viewModel::retryInitialization,
                    onProceed = viewModel::proceedToApp
                )
            }
        }
    }
}

@Composable
private fun PermissionRequestCard(
    showRationale: Boolean,
    deniedPermanently: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = "Storage Permission",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Storage Permission Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = if (showRationale && deniedPermanently) {
                    "BlackBoard AI needs storage permission to download and extract the AI model files. Please enable \"Files and media\" permission in app settings."
                } else if (showRationale) {
                    "BlackBoard AI needs storage permission to download the AI model. This allows the app to access and extract model files needed for AI functionality."
                } else {
                    "BlackBoard AI needs to access your device storage to download and set up the AI model for problem solving."
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (deniedPermanently) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open App Settings")
                }
            } else {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Grant Permission")
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "📱 Android 11+ Instructions:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1. Tap 'Grant Permission' above\n2. Find 'BlackBoard AI' in the list\n3. Toggle 'Allow access to manage all files'",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InitializationStatusCard(
    state: com.example.blackboardai.presentation.viewmodel.AppInitializationState,
    onRetry: () -> Unit,
    onProceed: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Icon and Message
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusIcon(status = state.progress.status)
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = getStatusTitle(state.progress.status),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = state.progress.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    // Show current path if available and different from message
                    state.progress.currentPath?.let { path ->
                        if (!state.progress.message.contains(path)) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Path: ${formatPathForDisplay(path)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Progress Indicator
            when {
                state.hasError -> {
                    // Error State
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Main error message
                        Text(
                            text = "❌ Setup Failed",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Detailed error in a scrollable card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            LazyColumn(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                item {
                                    Text(
                                        text = state.progress.error ?: "Unknown error occurred",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Smart retry button that continues from failed step
                            val failedStep = state.progress.failedStep
                            val retryText = when (failedStep) {
                                ModelStatus.EXTRACTING_MODEL -> "Retry Extraction"
                                ModelStatus.DOWNLOADING_MODEL -> "Retry Download"
                                ModelStatus.CHECKING_MODEL -> "Retry Setup"
                                ModelStatus.INITIALIZING_INFERENCE -> "Retry AI Engine"
                                ModelStatus.CREATING_SESSION -> "Retry AI Session"
                                ModelStatus.WARMING_UP -> "Retry Warmup"
                                else -> "Retry Setup"
                            }
                            
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(retryText)
                            }
                        }
                    }
                }
                
                state.isInitialized -> {
                    // Success State
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "✅ Ready to use BlackBoard AI!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = onProceed,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = "Get Started",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                
                else -> {
                    // Loading State
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = state.progress.progress,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "${(state.progress.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
    
    Spacer(modifier = Modifier.height(32.dp))
    
    // Additional Info
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "First time setup",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "We're setting up the AI model for problem solving. This only happens once and may take a few minutes depending on your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun StatusIcon(status: ModelStatus) {
    val (icon, color) = when (status) {
        ModelStatus.NOT_INITIALIZED -> Icons.Default.HourglassEmpty to MaterialTheme.colorScheme.outline
        ModelStatus.CHECKING_MODEL -> Icons.Default.Search to MaterialTheme.colorScheme.primary
        ModelStatus.DOWNLOADING_MODEL -> Icons.Default.CloudDownload to MaterialTheme.colorScheme.primary
        ModelStatus.EXTRACTING_MODEL -> Icons.Default.Archive to MaterialTheme.colorScheme.primary
        ModelStatus.INITIALIZING_INFERENCE -> Icons.Default.Memory to MaterialTheme.colorScheme.primary
        ModelStatus.CREATING_SESSION -> Icons.Default.Link to MaterialTheme.colorScheme.primary
        ModelStatus.WARMING_UP -> Icons.Default.Tune to MaterialTheme.colorScheme.primary
        ModelStatus.READY -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        ModelStatus.ERROR -> Icons.Default.Error to MaterialTheme.colorScheme.error
    }
    
    if (status == ModelStatus.READY) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = color
        )
    } else if (status == ModelStatus.ERROR) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = color
        )
    } else {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = color
            )
        }
    }
}

private fun getStatusTitle(status: ModelStatus): String {
    return when (status) {
        ModelStatus.NOT_INITIALIZED -> "Starting up..."
        ModelStatus.CHECKING_MODEL -> "Checking for AI Model"
        ModelStatus.DOWNLOADING_MODEL -> "Downloading AI Model"
        ModelStatus.EXTRACTING_MODEL -> "Extracting AI Model"
        ModelStatus.INITIALIZING_INFERENCE -> "Loading AI Engine"
        ModelStatus.CREATING_SESSION -> "Preparing AI Session"
        ModelStatus.WARMING_UP -> "Optimizing AI Performance"
        ModelStatus.READY -> "BlackBoard AI Ready!"
        ModelStatus.ERROR -> "Setup Failed"
    }
}

/**
 * Format file paths for better display on mobile screens
 */
private fun formatPathForDisplay(path: String): String {
    // Replace user directory with ~ for privacy and brevity
    val homeDir = System.getProperty("user.home") ?: ""
    val displayPath = if (homeDir.isNotEmpty() && path.startsWith(homeDir)) {
        path.replace(homeDir, "~")
    } else {
        path
    }
    
    // If path is too long, show just the last few directories
    val maxLength = 60
    return if (displayPath.length > maxLength) {
        val parts = displayPath.split("/")
        if (parts.size > 3) {
            ".../" + parts.takeLast(3).joinToString("/")
        } else {
            displayPath.takeLast(maxLength)
        }
    } else {
        displayPath
    }
} 