package com.chesstrainer

import androidx.compose.ui.graphics.Color

data class GameState(
    val fen: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    val playerColor: Boolean = true,
    val turn: Boolean = true,
    val isPlayerTurn: Boolean = true,
    val gameOver: Boolean = false,
    val result: String? = null,
    val inCheck: Boolean = false,
    val moveHistory: List<String> = emptyList(),
)

data class MoveResult(
    val ok: Boolean,
    val san: String = "",
    val fen: String = "",
    val gameOver: Boolean = false,
    val result: String? = null,
    val turn: Boolean = true,
    val inCheck: Boolean = false,
    val isPlayerTurn: Boolean = true,
    val error: String = "",
    val moveHistory: List<String> = emptyList(),
)

data class Arrow(
    val fromSquare: Int,
    val toSquare: Int,
    val color: Color,
)
