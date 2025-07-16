package com.example.blackboardai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.blackboardai.presentation.navigation.BlackBoardNavigation
import com.example.blackboardai.presentation.overlay.OverlayManager
import com.example.blackboardai.ui.theme.BlackBoardAITheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private lateinit var overlayManager: OverlayManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create OverlayManager early in lifecycle to register ActivityResultLaunchers
        overlayManager = OverlayManager(
            activity = this,
            onOverlayStarted = {
                // Toast feedback moved here from NotesListScreen
            },
            onPermissionDenied = { message ->
                // Toast feedback moved here from NotesListScreen  
            }
        )
        
        setContent {
            BlackBoardAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BlackBoardNavigation(overlayManager = overlayManager)
                }
            }
        }
    }
}