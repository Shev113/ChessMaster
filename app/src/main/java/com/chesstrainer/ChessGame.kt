package com.chesstrainer

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ChessGame {
    private val board = Board()
    private var playerSide = Side.WHITE
    private var playerColorBoolean = true
    private val _moveHistory = mutableListOf<String>()
    val moveHistory: List<String> get() = _moveHistory.toList()

    @Synchronized
    fun start(playerColor: Boolean) {
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        playerColorBoolean = playerColor
        playerSide = if (playerColor) Side.WHITE else Side.BLACK
        _moveHistory.clear()
    }

    @Synchronized
    fun makeMove(uci: String): MoveResult {
        val move = Move(uci, board.sideToMove)
        if (!board.isMoveLegal(move, true)) {
            return MoveResult(ok = false, error = "Illegal move")
        }
        board.doMove(move, true)
        _moveHistory.add(uci)
        return buildResult()
    }

    @Synchronized
    fun aiMove(uci: String): MoveResult {
        val move = Move(uci, board.sideToMove)
        board.doMove(move, false)
        _moveHistory.add(uci)
        return buildResult()
    }

    @Synchronized
    fun undo(): MoveResult {
        if (_moveHistory.isEmpty()) {
            return MoveResult(ok = false, error = "No moves")
        }
        board.undoMove()
        _moveHistory.removeLast()
        return MoveResult(
            ok = true,
            fen = board.fen,
            turn = board.sideToMove == Side.WHITE,
            inCheck = board.isKingAttacked,
            moveHistory = _moveHistory.toList(),
        )
    }

    @Synchronized
    fun getLegalMovesFrom(square: Int): List<String> {
        return board.legalMoves()
            .filter { it.from.ordinal == square }
            .map { it.toString() }
    }

    @Synchronized
    fun getLegalMoveTargets(square: Int): Set<Int> {
        return board.legalMoves()
            .filter { it.from.ordinal == square }
            .map { it.to.ordinal }
            .toSet()
    }

    @Synchronized
    fun getPieceAt(square: Int): Char? {
        val piece = board.getPiece(Square.squareAt(square))
        if (piece == Piece.NONE) return null
        return piece.fenSymbol.first()
    }

    @Synchronized
    fun getFen(): String = board.fen

    @Synchronized
    fun getFenAtPly(plyIndex: Int): String {
        val b = Board()
        b.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        for (i in 0..plyIndex) {
            if (i >= _moveHistory.size) break
            b.doMove(Move(_moveHistory[i], b.sideToMove))
        }
        return b.fen
    }

    @Synchronized
    fun getSideToMove(): Side = board.sideToMove

    @Synchronized
    fun isPlayerTurn(): Boolean = board.sideToMove == playerSide

    @Synchronized
    fun isGameOver(): Boolean = board.isMated || board.isStaleMate || board.isDraw

    @Synchronized
    fun result(): String? = when {
        board.isMated -> if (board.sideToMove == Side.WHITE) "0-1" else "1-0"
        board.isStaleMate || board.isDraw -> "1/2-1/2"
        else -> null
    }

    @Synchronized
    fun isInCheck(): Boolean = board.isKingAttacked

    @Synchronized
    fun getPlayerColorBoolean(): Boolean = playerColorBoolean

    @Synchronized
    fun getPlayerSide(): Side = playerSide

    private fun buildResult(): MoveResult {
        return MoveResult(
            ok = true,
            fen = board.fen,
            turn = board.sideToMove == Side.WHITE,
            gameOver = isGameOver(),
            result = result(),
            inCheck = isInCheck(),
            isPlayerTurn = isPlayerTurn(),
            moveHistory = _moveHistory.toList(),
        )
    }
}

suspend fun ChessGame.startAsync(playerColor: Boolean) = withContext(Dispatchers.IO) { start(playerColor) }
suspend fun ChessGame.makeMoveAsync(uci: String) = withContext(Dispatchers.IO) { makeMove(uci) }
suspend fun ChessGame.aiMoveAsync(uci: String) = withContext(Dispatchers.IO) { aiMove(uci) }
suspend fun ChessGame.undoAsync() = withContext(Dispatchers.IO) { undo() }
suspend fun ChessGame.getLegalMovesFromAsync(square: Int) = withContext(Dispatchers.IO) { getLegalMovesFrom(square) }
suspend fun ChessGame.getLegalMoveTargetsAsync(square: Int) = withContext(Dispatchers.IO) { getLegalMoveTargets(square) }
suspend fun ChessGame.getFenAtPlyAsync(plyIndex: Int) = withContext(Dispatchers.IO) { getFenAtPly(plyIndex) }
