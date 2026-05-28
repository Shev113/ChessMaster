package com.chesstrainer

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chesstrainer.ui.AnalysisScreen
import com.chesstrainer.ui.ChessTrainerTheme
import com.chesstrainer.ui.GameScreen

@Composable
fun ChessApp() {
    ChessTrainerTheme {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = "game",
        ) {
            composable("game") {
                GameScreen(
                    onStartAnalysis = { moveHistory, playerColor ->
                        navController.currentBackStackEntry?.savedStateHandle?.set("moves", moveHistory)
                        navController.currentBackStackEntry?.savedStateHandle?.set("playerColor", playerColor)
                        navController.navigate("analysis")
                    }
                )
            }

            composable("analysis") {
                val moves = navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.get<List<String>>("moves") ?: emptyList()
                val color = navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.get<Boolean>("playerColor") ?: true

                AnalysisScreen(
                    moveHistory = moves,
                    playerColor = color,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
