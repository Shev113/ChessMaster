package com.chesstrainer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.util.Log
import com.chesstrainer.*
import com.github.bhlangonijr.chesslib.Side
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    onStartAnalysis: (List<String>, Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var gameState by remember { mutableStateOf<GameState?>(null) }
    var aiThinking by remember { mutableStateOf(false) }
    var flipped by remember { mutableStateOf(false) }
    var selectedSquare by remember { mutableStateOf<Int?>(null) }
    var legalMoveSq by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var lastMove by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var moveHistoryDisplay by remember { mutableStateOf<List<String>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showNewGameDialog by remember { mutableStateOf(true) }
    var playerColor by remember { mutableStateOf(true) }
    val context = LocalContext.current

    if (showNewGameDialog) {
        NewGameDialog(
            onStart = { color, _ ->
                playerColor = color
                showNewGameDialog = false
                errorMsg = null
                selectedSquare = null
                legalMoveSq = emptySet()
                lastMove = null
                moveHistoryDisplay = emptyList()
                flipped = !color

                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            StockfishEngine.start(context)
                            ChessGame.start(color)
                        }
                        gameState = GameState(
                            playerColor = color,
                            fen = ChessGame.getFen(),
                            turn = ChessGame.getSideToMove() == Side.WHITE,
                            isPlayerTurn = ChessGame.isPlayerTurn(),
                        )

                        if (!color) {
                            aiThinking = true
                            val best = StockfishEngine.bestMove(gameState!!.fen, 1000)
                            if (best.bestMove.isNotEmpty()) {
                                val result = ChessGame.aiMoveAsync(best.bestMove)
                                gameState = gameState?.copy(
                                    fen = result.fen, turn = result.turn,
                                    isPlayerTurn = result.isPlayerTurn, gameOver = result.gameOver,
                                    result = result.result, inCheck = result.inCheck,
                                )
                                moveHistoryDisplay = result.moveHistory
                            }
                            aiThinking = false
                        }
                    } catch (e: Exception) {
                        errorMsg = "Ошибка: ${e.message}"
                        aiThinking = false
                    }
                }
            }
        )
        return
    }

    val gs = gameState ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Шахматный тренажер") },
                actions = {
                    TextButton(
                        onClick = { onStartAnalysis(moveHistoryDisplay, playerColor) },
                        enabled = moveHistoryDisplay.isNotEmpty(),
                    ) { Text("Анализ", color = MaterialTheme.colorScheme.primary) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = when {
                    gs.gameOver -> "Игра окончена: ${gs.result ?: "?"}"
                    aiThinking -> "AI думает..."
                    gs.inCheck -> "ШАХ!"
                    gs.isPlayerTurn -> "Ваш ход"
                    else -> "Ход соперника"
                },
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    gs.gameOver -> MaterialTheme.colorScheme.primary
                    gs.inCheck -> Color.Red
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(8.dp),
            )

            BoardView(
                fen = gs.fen,
                flipped = flipped,
                lastMoveFrom = lastMove?.first,
                lastMoveTo = lastMove?.second,
                selectedSquare = selectedSquare,
                legalMoveSquares = legalMoveSq,
                arrows = emptyList(),
                onSquareClick = { sq ->
                    if (!gs.isPlayerTurn || aiThinking || gs.gameOver) return@BoardView

                    if (selectedSquare == null) {
                        val piece = pieceAtFen(gs.fen, sq)
                        if (piece != null && piece.isUpperCase() == gs.playerColor) {
                            selectedSquare = sq
                            scope.launch {
                                legalMoveSq = ChessGame.getLegalMoveTargetsAsync(sq)
                            }
                        }
                    } else {
                        val from = selectedSquare!!
                        val targetSq = sq
                        if (targetSq in legalMoveSq) {
                            val uci = "${squareToUci(from)}${squareToUci(targetSq)}"
                            selectedSquare = null
                            legalMoveSq = emptySet()
                            scope.launch {
                                val result = ChessGame.makeMoveAsync(uci)
                                if (result.ok) {
                                    lastMove = Pair(from, targetSq)
                                    gameState = gs.copy(
                                        fen = result.fen, turn = result.turn,
                                        isPlayerTurn = result.isPlayerTurn, gameOver = result.gameOver,
                                        result = result.result, inCheck = result.inCheck,
                                    )
                                    moveHistoryDisplay = result.moveHistory

                    if (!result.isPlayerTurn && !result.gameOver) {
                        aiThinking = true
                        try {
                            Log.d("ChessTrainer", "AI thinking for ${result.fen}")
                            val best = StockfishEngine.bestMove(result.fen, 1000)
                            Log.d("ChessTrainer", "AI result: move=${best.bestMove}, avail=${StockfishEngine.available}")
                            if (best.bestMove.isNotEmpty()) {
                                val aiResult = ChessGame.aiMoveAsync(best.bestMove)
                                val aiFrom = uciSourceSquare(best.bestMove)
                                val aiTo = uciTargetSquare(best.bestMove)
                                lastMove = Pair(aiFrom, aiTo)
                                gameState = gameState?.copy(
                                    fen = aiResult.fen, turn = aiResult.turn,
                                    isPlayerTurn = aiResult.isPlayerTurn, gameOver = aiResult.gameOver,
                                    result = aiResult.result, inCheck = aiResult.inCheck,
                                )
                                moveHistoryDisplay = aiResult.moveHistory
                            } else {
                                Log.w("ChessTrainer", "AI returned no move (available=${StockfishEngine.available})")
                            }
                        } catch (e: Exception) {
                            Log.e("ChessTrainer", "AI error: ${e.message}", e)
                            errorMsg = "Ошибка AI: ${e.message}"
                        }
                        aiThinking = false
                    }
                                }
                            }
                        } else {
                            selectedSquare = null
                            legalMoveSq = emptySet()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                val result = ChessGame.undoAsync()
                                if (result.ok) {
                                    gameState = gameState?.copy(
                                        fen = result.fen, turn = result.turn, inCheck = result.inCheck,
                                    )
                                    moveHistoryDisplay = result.moveHistory
                                    lastMove = null; selectedSquare = null; legalMoveSq = emptySet()
                                }
                            }
                        },
                        enabled = !aiThinking && moveHistoryDisplay.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = null)
                        Spacer(Modifier.width(4.dp)); Text("Назад")
                    }

                    FilledTonalButton(onClick = { flipped = !flipped }) {
                        Icon(Icons.Default.FlipToFront, contentDescription = null)
                        Spacer(Modifier.width(4.dp)); Text("Флип")
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    FilledTonalButton(onClick = { showNewGameDialog = true }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(4.dp)); Text("Новая")
                    }

                    FilledTonalButton(
                        onClick = {
                            if (!gs.gameOver) {
                                val result = if (gs.playerColor) "0-1" else "1-0"
                                gameState = gs.copy(gameOver = true, result = result)
                            }
                        },
                        enabled = !aiThinking && !gs.gameOver,
                    ) {
                        Text("Сдаться")
                    }
                }
            }

            if (moveHistoryDisplay.isNotEmpty()) {
                Text(
                    "История ходов",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                LazyColumn(
                    modifier = Modifier.height(150.dp).fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    itemsIndexed(moveHistoryDisplay.chunked(2)) { i, pair ->
                        val num = i + 1
                        val w = pair.getOrElse(0) { "" }
                        val b = pair.getOrElse(1) { "" }
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("$num.", modifier = Modifier.width(32.dp))
                            Text(w, modifier = Modifier.width(80.dp))
                            Text(b, modifier = Modifier.width(80.dp))
                        }
                    }
                }
            }

            if (errorMsg != null) {
                Text(errorMsg!!, color = Color.Red, modifier = Modifier.padding(8.dp), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun NewGameDialog(
    onStart: (Boolean, Int) -> Unit,
) {
    var selectedColor by remember { mutableStateOf(true) }
    var skillLevel by remember { mutableIntStateOf(10) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Новая игра") },
        text = {
            Column {
                Text("Выберите цвет:", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedColor, onClick = { selectedColor = true })
                    Text("Белые")
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = !selectedColor, onClick = { selectedColor = false })
                    Text("Чёрные")
                }
                Spacer(Modifier.height(16.dp))
                Text("Уровень AI: $skillLevel", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = skillLevel.toFloat(),
                    onValueChange = { skillLevel = it.toInt() },
                    valueRange = 1f..20f,
                    steps = 18,
                )
            }
        },
        confirmButton = { Button(onClick = { onStart(selectedColor, skillLevel) }) { Text("Играть") } },
    )
}

private fun pieceAtFen(fen: String, square: Int): Char? {
    val boardPart = fen.split(" ")[0]
    var sq = 0
    for (ch in boardPart) {
        when {
            ch == '/' -> continue
            ch.isDigit() -> sq += ch - '0'
            else -> {
                // Convert FEN-ordered pos (sq) to chesslib square
                val chessSq = (7 - sq / 8) * 8 + sq % 8
                if (chessSq == square) return ch
                sq++
            }
        }
    }
    return null
}

private fun squareToUci(square: Int): String {
    val file = 'a' + (square % 8)
    val rank = '1' + (square / 8)
    return "$file$rank"
}

private fun uciTargetSquare(uci: String): Int {
    val file = uci[2] - 'a'
    val rank = uci[3] - '1'
    return rank * 8 + file
}

private fun uciSourceSquare(uci: String): Int {
    val file = uci[0] - 'a'
    val rank = uci[1] - '1'
    return rank * 8 + file
}
