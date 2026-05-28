package com.chesstrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chesstrainer.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    moveHistory: List<String>,
    playerColor: Boolean,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var analysisData by remember { mutableStateOf<AnalysisData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedResult by remember { mutableStateOf<AnalysisResult?>(null) }
    var reviewFen by remember { mutableStateOf<String?>(null) }
    var arrows by remember { mutableStateOf<List<Arrow>>(emptyList()) }

    LaunchedEffect(moveHistory) {
        isLoading = true
        try {
            analysisData = GameAnalyzer.analyzeGame(moveHistory, playerColor)
        } catch (_: Exception) {}
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Анализ партии") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Анализ партии...")
                }
            }
        } else {
            val data = analysisData
            if (data == null) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Ошибка анализа")
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                if (reviewFen != null && selectedResult != null) {
                    val r = selectedResult!!
                    BoardView(
                        fen = reviewFen!!,
                        flipped = !playerColor,
                        lastMoveFrom = uciSquare(r.playerMove, 0),
                        lastMoveTo = uciSquare(r.playerMove, 2),
                        selectedSquare = null,
                        legalMoveSquares = emptySet(),
                        arrows = arrows,
                        onSquareClick = {},
                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                    )

                    Text(
                        "Ход ${r.moveNumber}.${r.san} — ${r.classification}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        textAlign = TextAlign.Center,
                    )
                    HorizontalDivider()
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Статистика:", fontWeight = FontWeight.Bold)
                        Text("Всего ходов: ${data.stats["total_player_moves"] ?: 0}")
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Грубых ошибок: ${data.stats[ANALYSIS_BLUNDER] ?: 0}", color = Color(0xFFDC1E1E))
                            Text("Ошибок: ${data.stats[ANALYSIS_MISTAKE] ?: 0}", color = Color(0xFFF08C1E))
                            Text("Неточности: ${data.stats[ANALYSIS_INACCURACY] ?: 0}", color = Color(0xFFF0D232))
                        }
                        Text("Упущено возможностей: ${data.stats["missed_opportunities"] ?: 0}")
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                ) {
                    itemsIndexed(data.results) { idx, r ->
                        val bgColor = when {
                            selectedResult == r -> MaterialTheme.colorScheme.primaryContainer
                            idx % 2 == 0 -> MaterialTheme.colorScheme.surface
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bgColor)
                                .clickable {
                                    selectedResult = r
                                    scope.launch {
                                        reviewFen = ChessGame.getFenAtPlyAsync(r.plyIndex)
                                    }
                                    val arr = mutableListOf<Arrow>()
                                    val errColor = AnalysisColors[r.classification] ?: Color.Gray
                                    arr.add(Arrow(
                                        uciSquare(r.playerMove, 0),
                                        uciSquare(r.playerMove, 2),
                                        errColor,
                                    ))
                                    if (!r.wasBest && r.bestMove != null) {
                                        arr.add(Arrow(
                                            uciSquare(r.bestMove!!, 0),
                                            uciSquare(r.bestMove!!, 2),
                                            Color(0xFF64C864).copy(alpha = 0.7f),
                                        ))
                                    }
                                    arrows = arr
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(r.moveNumber.toString(), modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
                            Text(r.san, modifier = Modifier.width(60.dp))
                            Text(
                                r.classification,
                                modifier = Modifier.width(120.dp),
                                color = AnalysisColors[r.classification] ?: Color.Gray,
                                fontWeight = if (r.classification != ANALYSIS_BEST) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(if (r.wasBest) "—" else r.bestSan, modifier = Modifier.width(60.dp))
                            Text(
                                if (r.wasBest) "—" else String.format("%.2f", r.evalDiff),
                                modifier = Modifier.width(50.dp),
                                textAlign = TextAlign.End,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private fun uciSquare(uci: String, offset: Int): Int {
    val file = uci[offset] - 'a'
    val rank = uci[offset + 1] - '1'
    return rank * 8 + file
}
