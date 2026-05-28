package com.chesstrainer

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SimpleAI {
    private val typeValues: Map<PieceType, Int> = mapOf(
        PieceType.PAWN to 100,
        PieceType.KNIGHT to 320,
        PieceType.BISHOP to 330,
        PieceType.ROOK to 500,
        PieceType.QUEEN to 900,
        PieceType.KING to 20000,
    )

    suspend fun bestMove(fen: String, depth: Int = 3): BestMoveResult = withContext(Dispatchers.IO) {
        try {
            val board = Board()
            board.loadFromFen(fen)
            val side = board.sideToMove
            val moves = board.legalMoves()
            if (moves.isEmpty()) return@withContext BestMoveResult("", null, false, emptyList())

            var bestScore = Int.MIN_VALUE
            var bestMove = moves.first().toString()

            for (move in moves) {
                board.doMove(move, false)
                val score = -alphaBeta(board, depth - 1, Int.MIN_VALUE + 1, Int.MAX_VALUE - 1, side)
                board.undoMove()
                if (score > bestScore) {
                    bestScore = score
                    bestMove = move.toString()
                }
            }

            BestMoveResult(bestMove, bestScore / 100, false, emptyList())
        } catch (_: Exception) {
            BestMoveResult("", null, false, emptyList())
        }
    }

    private fun alphaBeta(board: Board, depth: Int, alpha: Int, beta: Int, side: Side): Int {
        if (depth == 0) return evaluate(board, side)

        val moves = board.legalMoves()
        if (moves.isEmpty()) {
            return if (board.isKingAttacked) -100000 else 0
        }

        var a = alpha
        for (move in moves) {
            board.doMove(move, false)
            val score = -alphaBeta(board, depth - 1, -beta, -a, flipSide(side))
            board.undoMove()
            if (score >= beta) return beta
            if (score > a) a = score
        }
        return a
    }

    private fun evaluate(board: Board, side: Side): Int {
        var score = 0
        for (piece in Piece.allPieces) {
            if (piece == Piece.NONE) continue
            val value = typeValues[piece.pieceType] ?: continue
            score += board.getPieceLocation(piece).size * if (piece.pieceSide == side) value else -value
        }
        return score
    }

    private fun flipSide(side: Side): Side =
        if (side == Side.WHITE) Side.BLACK else Side.WHITE
}
