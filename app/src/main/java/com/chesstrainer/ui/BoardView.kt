package com.chesstrainer.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chesstrainer.Arrow
import kotlin.math.*

private val lightSq = Color(0xFFF0D9B5)
private val darkSq = Color(0xFFB58863)

// Unicode piece fallback (used when PNG not available)
private val pieceSymbols = mapOf(
    "K" to "\u2654", "Q" to "\u2655", "R" to "\u2656",
    "B" to "\u2657", "N" to "\u2658", "P" to "\u2659",
    "k" to "\u265A", "q" to "\u265B", "r" to "\u265C",
    "b" to "\u265D", "n" to "\u265E", "p" to "\u265F",
)

// Mapping: piece char → PNG filename in assets/pieces/
private val pngFilenames = mapOf(
    'K' to "Chess_klt60.png", 'k' to "Chess_kdt60.png",
    'Q' to "Chess_qlt60.png", 'q' to "Chess_qdt60.png",
    'R' to "Chess_rlt60.png", 'r' to "Chess_rdt60.png",
    'B' to "Chess_blt60.png", 'b' to "Chess_bdt60.png",
    'N' to "Chess_nlt60.png", 'n' to "Chess_ndt60.png",
    'P' to "Chess_plt60.png", 'p' to "Chess_pdt60.png",
)

@Composable
private fun loadPieceBitmaps(
    context: Context,
): Map<Char, ImageBitmap> {
    val bitmaps = remember { mutableStateOf<Map<Char, ImageBitmap>>(emptyMap()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val map = mutableMapOf<Char, ImageBitmap>()
            for ((piece, filename) in pngFilenames) {
                try {
                    context.assets.open("pieces/$filename").use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) {
                            map[piece] = bitmap.asImageBitmap()
                        }
                    }
                } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) { bitmaps.value = map }
        }
    }
    return bitmaps.value
}

@Composable
fun BoardView(
    fen: String,
    flipped: Boolean,
    lastMoveFrom: Int?,
    lastMoveTo: Int?,
    selectedSquare: Int?,
    legalMoveSquares: Set<Int>,
    arrows: List<Arrow>,
    onSquareClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pieceBitmaps = loadPieceBitmaps(context)

    Box(modifier = modifier.fillMaxWidth().aspectRatio(1f)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(4.dp)
                .pointerInput(fen) {
                    detectTapGestures { offset ->
                        val size = this.size
                        val sq = size.width / 8f
                        val col = (offset.x / sq).toInt().coerceIn(0, 7)
                        val row = (offset.y / sq).toInt().coerceIn(0, 7)
                        val chessRank = if (!flipped) 7 - row else row
                        val square = chessRank * 8 + col
                        onSquareClick(square)
                    }
                }
        ) {
            val sq = size.width / 8f

            // Draw board
            for (r in 0..7) {
                for (c in 0..7) {
                    val color = if ((r + c) % 2 == 0) lightSq else darkSq
                    val vr = if (!flipped) 7 - r else r
                    drawRect(
                        color = color,
                        topLeft = Offset(c * sq, vr * sq),
                        size = Size(sq, sq),
                    )
                }
            }

            // Draw last move highlight
            if (lastMoveFrom != null) {
                drawSquareHighlight(lastMoveFrom, sq, HighlightLastMove, flipped)
            }
            if (lastMoveTo != null) {
                drawSquareHighlight(lastMoveTo, sq, HighlightLastMove, flipped)
            }

            // Draw selected square
            if (selectedSquare != null) {
                drawSquareHighlight(selectedSquare, sq, HighlightSelected, flipped)
            }

            // Draw legal move dots
            for (square in legalMoveSquares) {
                val r = square / 8
                val c = square % 8
                val vr = if (!flipped) 7 - r else r
                val cx = (c + 0.5f) * sq
                val cy = (vr + 0.5f) * sq
                drawCircle(
                    color = HighlightLegal,
                    radius = sq * 0.15f,
                    center = Offset(cx, cy),
                )
            }

            // Draw pieces
            drawPiecesFromFen(fen, sq, flipped, pieceBitmaps)

            // Draw analysis arrows
            for (arrow in arrows) {
                drawAnalysisArrow(arrow, sq, flipped)
            }
        }
    }
}

private fun DrawScope.drawSquareHighlight(square: Int, sq: Float, color: Color, flipped: Boolean) {
    val r = square / 8
    val c = square % 8
    val vr = if (!flipped) 7 - r else r
    drawRect(
        color = color,
        topLeft = Offset(c * sq, vr * sq),
        size = Size(sq, sq),
    )
}

private fun DrawScope.drawPiecesFromFen(
    fen: String,
    sq: Float,
    flipped: Boolean,
    bitmaps: Map<Char, ImageBitmap>,
) {
    val boardPart = fen.split(" ")[0]
    var rank = 7
    var file = 0

    for (ch in boardPart) {
        when {
            ch == '/' -> {
                rank--
                file = 0
            }
            ch.isDigit() -> file += ch - '0'
            else -> {
                val vr = if (!flipped) 7 - rank else rank
                val cx = (file + 0.5f) * sq
                val cy = (vr + 0.5f) * sq

                val bitmap = bitmaps[ch]
                if (bitmap != null) {
                    // Draw PNG piece, scaled to fit square
                    val scale = (sq * 0.88f) / min(bitmap.width.toFloat(), bitmap.height.toFloat())
                    val w = bitmap.width * scale
                    val h = bitmap.height * scale
                    drawImage(
                        image = bitmap,
                        dstOffset = IntOffset((cx - w / 2f).toInt(), (cy - h / 2f).toInt()),
                        dstSize = IntSize(w.toInt(), h.toInt()),
                    )
                } else {
                    // Fallback: colored circle + unicode symbol
                    val isWhite = ch.isUpperCase()
                    val bgColor = if (isWhite) Color.White else Color(0xFF2A2A2A)
                    val fgColor = if (isWhite) Color.Black else Color.White

                    drawCircle(
                        color = bgColor,
                        radius = sq * 0.42f,
                        center = Offset(cx, cy),
                    )

                    val symbol = pieceSymbols[ch.toString()] ?: ch.toString()
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            this.color = fgColor.hashCode()
                            textSize = sq * 0.65f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        drawText(symbol, cx, cy + sq * 0.23f, paint)
                    }
                }
                file++
            }
        }
    }
}

private fun DrawScope.drawAnalysisArrow(arrow: Arrow, sq: Float, flipped: Boolean) {
    val fromR = arrow.fromSquare / 8
    val fromC = arrow.fromSquare % 8
    val toR = arrow.toSquare / 8
    val toC = arrow.toSquare % 8

    val fromVR = if (!flipped) 7 - fromR else fromR
    val toVR = if (!flipped) 7 - toR else toR

    val fx = (fromC + 0.5f) * sq
    val fy = (fromVR + 0.5f) * sq
    val tx = (toC + 0.5f) * sq
    val ty = (toVR + 0.5f) * sq

    val dx = tx - fx
    val dy = ty - fy
    val len = sqrt(dx * dx + dy * dy)
    if (len < 1f) return

    val nx = dx / len
    val ny = dy / len

    val inset = sq * 0.12f
    val sx = fx + nx * inset
    val sy = fy + ny * inset
    val ex = tx - nx * sq * 0.28f
    val ey = ty - ny * sq * 0.28f

    // Line
    drawLine(
        color = arrow.color,
        start = Offset(sx, sy),
        end = Offset(ex, ey),
        strokeWidth = sq * 0.06f,
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
    )

    // Arrowhead
    val headLen = sq * 0.22f
    val angle = atan2(dy, dx)
    val path = Path().apply {
        moveTo(ex, ey)
        lineTo(ex - headLen * cos(angle - 0.5f), ey - headLen * sin(angle - 0.5f))
        lineTo(ex - headLen * cos(angle + 0.5f), ey - headLen * sin(angle + 0.5f))
        close()
    }
    drawPath(path, color = arrow.color)
}
