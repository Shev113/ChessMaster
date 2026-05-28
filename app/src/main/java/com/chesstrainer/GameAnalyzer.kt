package com.chesstrainer

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GameAnalyzer {

    suspend fun analyzeGame(
        moveHistory: List<String>,
        playerColor: Boolean,
    ): AnalysisData = withContext(Dispatchers.IO) {
        val results = mutableListOf<AnalysisResult>()
        val board = Board()

        for ((plyIndex, uci) in moveHistory.withIndex()) {
            val isPlayerMove = (playerColor && plyIndex % 2 == 0) || (!playerColor && plyIndex % 2 == 1)
            if (!isPlayerMove) {
                board.doMove(Move(uci, board.sideToMove))
                continue
            }

            val fenBefore = board.fen
            val uciMove = Move(uci, board.sideToMove)
            val san = uci
            board.doMove(uciMove)

            val evalBefore = StockfishEngine.evalPosition(fenBefore, 500)
            val bestMoveUci = evalBefore.bestMove
            val bestScore = evalBefore.score ?: 0

            val evalAfter = StockfishEngine.evalPosition(board.fen, 300)
            val afterScore = evalAfter.score ?: 0
            val playerScore = -afterScore

            val diff = playerScore - bestScore
            val classification = classifyDiff(diff)

            val bestSan = bestMoveUci

            results.add(AnalysisResult(
                moveNumber = plyIndex / 2 + 1,
                plyIndex = plyIndex,
                playerMove = uci,
                san = san,
                playerEval = playerScore.toDouble() / 100.0,
                bestMove = bestMoveUci.ifEmpty { null },
                bestSan = bestSan,
                bestEval = bestScore.toDouble() / 100.0,
                evalDiff = diff.toDouble() / 100.0,
                classification = classification,
                wasBest = bestMoveUci == uci || diff > -30,
                missed = classification in listOf(ANALYSIS_MISTAKE, ANALYSIS_BLUNDER) && diff < -50,
            ))
        }

        val stats = buildStats(results)
        AnalysisData(results = results, stats = stats)
    }

    private fun classifyDiff(diffCp: Int): String {
        return when {
            diffCp > -30 -> ANALYSIS_BEST
            diffCp > -70 -> ANALYSIS_EXCELLENT
            diffCp > -100 -> ANALYSIS_GOOD
            diffCp > -150 -> ANALYSIS_INACCURACY
            diffCp > -300 -> ANALYSIS_MISTAKE
            else -> ANALYSIS_BLUNDER
        }
    }

    private fun buildStats(results: List<AnalysisResult>): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()
        var totalPlayer = 0
        var missedOps = 0
        for (r in results) {
            if (r.classification.isNotEmpty()) {
                stats[r.classification] = (stats[r.classification] ?: 0) + 1
                totalPlayer++
            }
            if (r.missed) missedOps++
        }
        stats["total_player_moves"] = totalPlayer
        stats["missed_opportunities"] = missedOps
        return stats
    }
}

data class AnalysisResult(
    val moveNumber: Int,
    val plyIndex: Int,
    val playerMove: String,
    val san: String,
    val playerEval: Double,
    val bestMove: String?,
    val bestSan: String,
    val bestEval: Double,
    val evalDiff: Double,
    val classification: String,
    val wasBest: Boolean,
    val missed: Boolean,
)

data class AnalysisData(
    val results: List<AnalysisResult>,
    val stats: Map<String, Int>,
)

const val ANALYSIS_BEST = "Лучший"
const val ANALYSIS_EXCELLENT = "Отлично"
const val ANALYSIS_GOOD = "Хорошо"
const val ANALYSIS_INACCURACY = "Неточность"
const val ANALYSIS_MISTAKE = "Ошибка"
const val ANALYSIS_BLUNDER = "Грубая ошибка"
