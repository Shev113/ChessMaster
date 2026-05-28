package com.chesstrainer

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object StockfishEngine {
    private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null
    private var hashMB = 64
    private var engineThreads = 1
    var available by mutableStateOf(false)
    private var ready = CompletableDeferred<Unit>().apply { complete(Unit) }

    fun start(context: Context, hash: Int = 64, threads: Int = 1) {
        if (process != null) stop()
        ready = CompletableDeferred()

        val pageSize = try {
            java.io.File("/proc/self/auxv").let { f ->
                if (!f.exists()) return@let 4096
                val data = f.readBytes()
                var pagesz = 4096
                var i = 0
                while (i + 7 < data.size) {
                    val id = data[i].toInt() and 0xFF or ((data[i + 1].toInt() and 0xFF) shl 8)
                    if (id == 6) {
                        pagesz = (data[i + 4].toInt() and 0xFF) or
                                 ((data[i + 5].toInt() and 0xFF) shl 8) or
                                 ((data[i + 6].toInt() and 0xFF) shl 16) or
                                 ((data[i + 7].toInt() and 0xFF) shl 24)
                        break
                    }
                    i += 8
                }
                pagesz
            }
        } catch (_: Exception) { 4096 }

        if (pageSize > 4096) {
            available = false
            ready.complete(Unit)
            Log.w("ChessTrainer", "16KB page — Stockfish unavailable")
            return
        }

        hashMB = hash.coerceIn(16, 512)
        engineThreads = threads.coerceIn(1, 4)

        try {
            val bin = tryRunNativeLib(context) ?: run {
                val binaryName = findAnyBinary(context)
                if (binaryName == null) { available = false; ready.complete(Unit); Log.w("ChessTrainer", "No binary for arch ${getArch()}"); return }
                val baseDir = try {
                    val dir = java.io.File(context.codeCacheDir, "stockfish")
                    dir.mkdirs(); dir
                } catch (_: Exception) { context.filesDir }
                val bin = java.io.File(baseDir, binaryName)
                val t0 = System.currentTimeMillis()
                context.assets.open(binaryName).use { src ->
                    bin.outputStream().use { dst -> src.copyTo(dst) }
                }
                Log.d("ChessTrainer", "Extracted ${binaryName} (${(System.currentTimeMillis() - t0) / 1000}s)")
                bin.setExecutable(true, false)
                bin
            }

            process = try {
                ProcessBuilder(bin.absolutePath).apply {
                    directory(bin.parentFile)
                    redirectErrorStream(true)
                }.start().also { Log.d("ChessTrainer", "Running directly") }
            } catch (e: Exception) {
                Log.d("ChessTrainer", "Direct exec failed: ${e.message}, trying linker64")
                val linker = if (java.io.File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"
                ProcessBuilder(linker, bin.absolutePath).apply {
                    directory(bin.parentFile)
                    redirectErrorStream(true)
                }.start().also { Log.d("ChessTrainer", "Running via $linker") }
            }
            writer = OutputStreamWriter(process!!.outputStream, "UTF-8")
            reader = BufferedReader(InputStreamReader(process!!.inputStream, "UTF-8"))

            send("uci"); if (!waitFor("uciok", 30000)) { Log.w("ChessTrainer", "uciok timeout"); val exit = process?.exitValue(); if (exit != null) Log.e("ChessTrainer", "process exited: $exit"); stop(); available = false; ready.complete(Unit); return }
            send("setoption name Hash value $hashMB")
            send("setoption name Threads value $engineThreads")
            send("isready"); if (!waitFor("readyok", 10000)) { Log.w("ChessTrainer", "readyok timeout"); stop(); available = false; ready.complete(Unit); return }
            available = true
            ready.complete(Unit)
        } catch (e: Exception) {
            Log.e("ChessTrainer", "Stockfish start failed: ${e.message}", e)
            stop(); available = false; ready.complete(Unit)
        }
    }

    fun stop() {
        try {
            send("quit")
            process?.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Exception) {}
        process?.destroyForcibly()
        process = null; writer = null; reader = null
    }

    suspend fun awaitReady() {
        ready.await()
    }

    suspend fun bestMove(fen: String, timeMs: Int = 2000): BestMoveResult =
        withContext(Dispatchers.IO) {
            ready.await()
            if (!available) return@withContext SimpleAI.bestMove(fen, 3)
            try { search(fen, timeMs, 1).first() }
            catch (_: Exception) { SimpleAI.bestMove(fen, 3) }
        }

    suspend fun evalPosition(fen: String, timeMs: Int = 1000): BestMoveResult =
        withContext(Dispatchers.IO) {
            ready.await()
            if (!available) return@withContext SimpleAI.bestMove(fen, 2)
            try { search(fen, timeMs, 1).first() }
            catch (_: Exception) { SimpleAI.bestMove(fen, 2) }
        }

    private fun search(fen: String, timeMs: Int, multiPv: Int): List<BestMoveResult> {
        send("position fen $fen")
        if (multiPv > 1) send("setoption name MultiPV value $multiPv")
        send("go movetime ${timeMs.coerceAtLeast(100)}")
        val output = mutableListOf<String>()
        while (true) {
            val line = reader?.readLine() ?: break
            output.add(line)
            if (line.startsWith("bestmove")) break
        }
        if (multiPv > 1) send("setoption name MultiPV value 1")
        return if (multiPv > 1) parseMultiPv(output) else listOf(parseSingle(output))
    }

    private fun getArch(): String {
        val osArch = System.getProperty("os.arch") ?: ""
        val abi = try { android.os.Build.SUPPORTED_ABIS.firstOrNull() } catch (_: Exception) { null }
        val primary = when {
            osArch.contains("aarch64", true) || abi?.contains("arm64", true) == true -> "arm64-v8a"
            osArch.contains("armv7", true) || abi?.contains("armeabi", true) == true -> "armeabi-v7a"
            osArch.contains("x86_64", true) || osArch.contains("amd64", true) || abi?.contains("x86_64", true) == true -> "x86_64"
            osArch.contains("x86", true) || abi?.contains("x86", true) == true -> "x86"
            else -> "arm64-v8a"
        }
        android.util.Log.d("ChessTrainer", "Detected arch: $primary (osArch=$osArch, abi=$abi)")
        return primary
    }

    private fun findAnyBinary(context: Context): String? {
        for (arch in listOf(getArch(), "arm64-v8a", "armeabi-v7a")) {
            val name = "stockfish-$arch"
            if (binaryExists(context, name)) return name
        }
        return null
    }

    private fun binaryExists(context: Context, name: String): Boolean {
        return try {
            context.assets.open(name).close()
            true
        } catch (_: Exception) { false }
    }

    private fun tryRunNativeLib(context: Context): java.io.File? {
        val dir = context.applicationInfo.nativeLibraryDir ?: return null
        val lib = java.io.File(dir, "libstockfish.so")
        if (!lib.exists()) { Log.d("ChessTrainer", "No native lib at $dir/libstockfish.so"); return null }
        Log.d("ChessTrainer", "Found native lib at ${lib.absolutePath}")
        lib.setExecutable(true, false)
        return lib
    }

    private fun send(cmd: String) {
        try { writer?.write("$cmd\n"); writer?.flush() } catch (_: Exception) {}
    }

    private fun waitFor(token: String, timeoutMs: Long = 2000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val line = try { reader?.readLine() } catch (_: Exception) { break } ?: break
            if (line.trim() == token) return true
        }
        return false
    }

    private fun parseSingle(output: List<String>): BestMoveResult {
        val bestMove = output.firstOrNull { it.startsWith("bestmove") }?.split(" ")?.get(1) ?: ""
        val infoLine = output.lastOrNull { it.startsWith("info") && it.contains("score cp") }
        var score: Int? = null; var isMate = false
        if (infoLine != null) {
            val parts = infoLine.split(" ")
            for (i in parts.indices) {
                when {
                    parts[i] == "score" && i + 2 < parts.size && parts[i + 1] == "cp" ->
                        score = parts[i + 2].toIntOrNull()
                    parts[i] == "score" && i + 2 < parts.size && parts[i + 1] == "mate" -> {
                        isMate = true; score = parts[i + 2].toIntOrNull()
                    }
                }
            }
        }
        return BestMoveResult(bestMove, score, isMate, output)
    }

    private fun parseMultiPv(output: List<String>): List<BestMoveResult> {
        val results = mutableMapOf<Int, BestMoveResult>()
        for (line in output) {
            if (!line.startsWith("info")) continue
            val parts = line.split(" ")
            var pvIdx = 0; var found = false
            for (i in parts.indices) {
                if (parts[i] == "multipv") { pvIdx = (parts.getOrNull(i + 1)?.toIntOrNull() ?: 1) - 1; found = true; break }
            }
            if (!found || pvIdx >= 10) continue
            val existing = results[pvIdx]
            var score = existing?.score; var isMate = existing?.isMate ?: false; var bestMove = existing?.bestMove ?: ""
            for (i in parts.indices) {
                when {
                    parts[i] == "score" && i + 2 < parts.size && parts[i + 1] == "cp" -> score = parts[i + 2].toIntOrNull() ?: score
                    parts[i] == "score" && i + 2 < parts.size && parts[i + 1] == "mate" -> { isMate = true; score = parts[i + 2].toIntOrNull() ?: score }
                    parts[i] == "pv" && i + 1 < parts.size -> bestMove = parts[i + 1]
                }
            }
            results[pvIdx] = BestMoveResult(bestMove, score, isMate, listOf(line))
        }
        return results.entries.sortedBy { it.key }.map { it.value }
    }
}

data class BestMoveResult(
    val bestMove: String,
    val score: Int?,
    val isMate: Boolean,
    val rawOutput: List<String>,
)
