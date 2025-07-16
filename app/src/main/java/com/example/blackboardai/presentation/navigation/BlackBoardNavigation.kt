package com.example.blackboardai.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.blackboardai.presentation.ui.DrawingScreen
import com.example.blackboardai.presentation.ui.InitializationScreen
import com.example.blackboardai.presentation.ui.NotesListScreen

@Composable
fun BlackBoardNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Initialization.route
    ) {
        composable(Screen.Initialization.route) {
            InitializationScreen(
                onInitializationComplete = {
                    navController.navigate(Screen.NotesList.route) {
                        // Clear the initialization screen from the back stack
                        popUpTo(Screen.Initialization.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.NotesList.route) {
            NotesListScreen(
                onCreateNote = {
                    navController.navigate(Screen.Drawing.createRoute())
                },
                onNoteClick = { noteId ->
                    navController.navigate(Screen.Drawing.createRoute(noteId))
                }
            )
        }
        
        composable(
            route = Screen.Drawing.route,
            arguments = Screen.Drawing.arguments
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getLong("noteId") ?: 0L
            DrawingScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                noteId = noteId
            )
        }
    }
}

sealed class Screen(val route: String) {
    object Initialization : Screen("initialization")
    object NotesList : Screen("notes_list")
    
    object Drawing : Screen("drawing/{noteId}") {
        fun createRoute(noteId: Long = 0L) = "drawing/$noteId"
        
        val arguments = listOf(
            navArgument("noteId") {
                type = NavType.LongType
                defaultValue = 0L
            }
        )
    }
} 