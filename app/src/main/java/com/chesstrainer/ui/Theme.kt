package com.chesstrainer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.chesstrainer.ANALYSIS_BEST
import com.chesstrainer.ANALYSIS_BLUNDER
import com.chesstrainer.ANALYSIS_EXCELLENT
import com.chesstrainer.ANALYSIS_GOOD
import com.chesstrainer.ANALYSIS_INACCURACY
import com.chesstrainer.ANALYSIS_MISTAKE

private val LightColors = lightColorScheme(
    primary = Color(0xFF4A6741),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCE8C3),
    secondary = Color(0xFF8B6F47),
    surface = Color(0xFFF8F5F0),
    onSurface = Color(0xFF1C1B1A),
    background = Color(0xFFF8F5F0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8D89A),
    onPrimary = Color(0xFF1B3A14),
    primaryContainer = Color(0xFF32522A),
    secondary = Color(0xFFD4B88F),
    surface = Color(0xFF1C1B1A),
    onSurface = Color(0xFFE4E1DB),
    background = Color(0xFF1C1B1A),
)

@Composable
fun ChessTrainerTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

// ── Chess board colors ──

val LightSquare = Color(0xFFF0D9B5)
val DarkSquare = Color(0xFFB58863)
val HighlightLastMove = Color(0xFFCDD26A).copy(alpha = 0.7f)
val HighlightSelected = Color(0xFFFFC800).copy(alpha = 0.7f)
val HighlightLegal = Color(0xFFFFC800).copy(alpha = 0.3f)

val AnalysisColors: Map<String, Color> = mapOf(
    ANALYSIS_BEST to Color(0xFF64C864).copy(alpha = 0.7f),
    ANALYSIS_EXCELLENT to Color(0xFF89C864).copy(alpha = 0.7f),
    ANALYSIS_GOOD to Color(0xFFC8C864).copy(alpha = 0.7f),
    ANALYSIS_INACCURACY to Color(0xFFF0D232).copy(alpha = 0.7f),
    ANALYSIS_MISTAKE to Color(0xFFF08C1E).copy(alpha = 0.7f),
    ANALYSIS_BLUNDER to Color(0xFFDC1E1E).copy(alpha = 0.7f),
)
