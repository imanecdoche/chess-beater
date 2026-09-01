package com.chessbeater.engine

import android.util.Log
import com.chessbeater.engine.models.ChessEngineBridge
import com.chessbeater.engine.models.EngineConfig
import com.chessbeater.engine.models.EngineResult
import com.chessbeater.engine.models.EngineType
import com.chessbeater.engine.process.StockfishProcessManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.regex.Pattern

/**
 * Service orchestrating the Standalone Process Chess Engine (DroidFish Standard Architecture),
 * with strict Max Thinking Time limits and Instant Local Fallback.
 */
class ChessEngineService(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val processManager: StockfishProcessManager = StockfishProcessManager()
) : ChessEngineBridge {

    private val serviceScope = CoroutineScope(dispatcher + SupervisorJob())
    private val executionMutex = Mutex()

    private var currentConfig: EngineConfig = EngineConfig(EngineType.STOCKFISH, 100)
    private var isInitialized = false

    // Configurable Max Thinking Time (500ms, 1000ms, 1500ms, 2000ms, 3000ms)
    var maxThinkingTimeMs: Long = 1000L

    val engineOutputFlow: SharedFlow<String> = processManager.engineOutputFlow

    companion object {
        private const val TAG = "ChessEngineService"
        private val BESTMOVE_PATTERN: Pattern = Pattern.compile("^bestmove\\s+([a-h][1-8][a-h][1-8][qrbn]?)(?:\\s+ponder\\s+([a-h][1-8][a-h][1-8][qrbn]?))?")
        private val SCORE_CP_PATTERN: Pattern = Pattern.compile("score\\s+cp\\s+(-?\\d+)")
        private val SCORE_MATE_PATTERN: Pattern = Pattern.compile("score\\s+mate\\s+(-?\\d+)")
        private val DEPTH_PATTERN: Pattern = Pattern.compile("depth\\s+(\\d+)")

        fun parseBestMove(rawOutput: String): String? {
            val match = Regex("""bestmove\s+([a-h][1-8][a-h][1-8][qrbn]?)""").find(rawOutput)
            return match?.groupValues?.get(1)
        }

        fun parseUciOutput(lines: List<String>, calculationTimeMs: Long): EngineResult? {
            var bestMove: String? = null
            var ponderMove: String? = null
            var scoreCp: Int? = null
            var scoreMate: Int? = null
            var lastDepth = 0

            for (line in lines) {
                val depthMatcher = DEPTH_PATTERN.matcher(line)
                if (depthMatcher.find()) {
                    lastDepth = depthMatcher.group(1)?.toIntOrNull() ?: lastDepth
                }

                val cpMatcher = SCORE_CP_PATTERN.matcher(line)
                if (cpMatcher.find()) {
                    scoreCp = cpMatcher.group(1)?.toIntOrNull()
                    scoreMate = null
                }

                val mateMatcher = SCORE_MATE_PATTERN.matcher(line)
                if (mateMatcher.find()) {
                    scoreMate = mateMatcher.group(1)?.toIntOrNull()
                    scoreCp = null
                }

                if (line.startsWith("bestmove")) {
                    bestMove = parseBestMove(line)
                    val bmMatcher = BESTMOVE_PATTERN.matcher(line)
                    if (bmMatcher.find()) {
                        ponderMove = bmMatcher.group(2)
                    }
                }
            }

            return bestMove?.let {
                EngineResult(
                    bestMove = it,
                    ponderMove = ponderMove,
                    evaluationCentipawns = scoreCp,
                    mateInMoves = scoreMate,
                    depth = lastDepth,
                    calculationTimeMs = calculationTimeMs
                )
            }
        }
    }

    var currentEloRating: Int = 2200

    fun applyFullStrengthMode() {
        processManager.sendCommand("setoption name UCI_LimitStrength value false")
        processManager.sendCommand("setoption name Skill Level value 20")
        processManager.sendCommand("setoption name Threads value 2")
        processManager.sendCommand("setoption name Hash value 32")
        processManager.sendCommand("isready")
        Log.d("StockfishNative", "🚀 Mode UNLIMITED MAX POWER Aktif (Skill Level 20, No Limit)")
    }

    fun setEloRating(targetElo: Int) {
        currentEloRating = targetElo
        val clampedElo = targetElo.coerceIn(800, 3500)
        Log.d("StockfishNative", "🎯 Mengonfigurasi Engine Power: $clampedElo ELO")

        if (clampedElo >= 2800) {
            applyFullStrengthMode()
            return
        }

        val skillLevel = ((clampedElo - 800) * 19 / (2800 - 800)).coerceIn(0, 19)
        val uciElo = clampedElo.coerceIn(1320, 3190)

        processManager.sendCommand("setoption name UCI_LimitStrength value true")
        processManager.sendCommand("setoption name UCI_Elo value $uciElo")
        processManager.sendCommand("setoption name Skill Level value $skillLevel")
        processManager.sendCommand("setoption name Threads value 2")
        processManager.sendCommand("setoption name Hash value 32")
        processManager.sendCommand("isready")

        Log.d("StockfishNative", "⚖️ Mode Terkalibrasi Aktif: ELO=$uciElo, SkillLevel=$skillLevel")
    }

    override suspend fun initializeEngine(): Boolean = withContext(dispatcher) {
        executionMutex.withLock {
            if (isInitialized) return@withContext true

            val started = processManager.startProcess()
            if (!started) {
                val errMsg = "❌ Gagal memuat binary Stockfish asli!"
                Log.e(TAG, errMsg)
                throw IllegalStateException(errMsg)
            }

            val uciOkDeferred = CompletableDeferred<Boolean>()
            val readyOkDeferred = CompletableDeferred<Boolean>()

            val handshakeJob = serviceScope.launch {
                engineOutputFlow.collect { line ->
                    Log.d("StockfishRaw", "Stdout: $line")
                    if (line.contains("uciok")) uciOkDeferred.complete(true)
                    if (line.contains("readyok")) readyOkDeferred.complete(true)
                }
            }

            try {
                processManager.sendCommand("uci")
                withTimeoutOrNull(1000L) { uciOkDeferred.await() }

                applyConfigInternal(currentConfig)
                setEloRating(currentEloRating)

                processManager.sendCommand("isready")
                withTimeoutOrNull(1000L) { readyOkDeferred.await() }

                isInitialized = true
                Log.i(TAG, "Stockfish Process UCI Handshake completed successfully.")
            } catch (e: Exception) {
                Log.w(TAG, "UCI Handshake note: ${e.message}")
                isInitialized = true
            } finally {
                handshakeJob.cancel()
            }

            true
        }
    }

    override suspend fun setStrength(config: EngineConfig): Unit = withContext(dispatcher) {
        executionMutex.withLock {
            applyConfigInternal(config)
            Unit
        }
    }

    private fun applyConfigInternal(config: EngineConfig) {
        currentConfig = config
        val uciOptions = config.toUciCommands()
        for (cmd in uciOptions) {
            processManager.sendCommand(cmd)
        }
        processManager.sendCommand("isready")
    }

    override suspend fun evaluatePosition(fen: String): EngineResult = withContext(dispatcher) {
        executionMutex.withLock {
            if (!isInitialized) {
                initializeEngine()
            }

            val startTime = System.currentTimeMillis()
            val limitMs = maxThinkingTimeMs
            val collectedLines = mutableListOf<String>()
            val resultDeferred = CompletableDeferred<EngineResult>()

            val listenerJob = serviceScope.launch {
                engineOutputFlow.collect { line ->
                    collectedLines.add(line)
                    Log.d("StockfishRaw", "Stdout: $line")
                    if (line.startsWith("bestmove")) {
                        val duration = System.currentTimeMillis() - startTime
                        val bm = parseBestMove(line) ?: "0000"
                        val result = parseUciOutput(collectedLines, duration)
                            ?: EngineResult(
                                bestMove = bm,
                                calculationTimeMs = duration
                            )
                        if (!resultDeferred.isCompleted) {
                            resultDeferred.complete(result)
                        }
                    }
                }
            }

            try {
                Log.d("StockfishDebug", "Evaluating FEN: $fen")
                processManager.sendCommand("stop")
                processManager.sendCommand("position fen $fen")

                // Search depth budget: ensure balanced command based on thinking time and ELO
                val goCommand = if (limitMs < 400L) {
                    "go depth 12"
                } else {
                    "go movetime $limitMs"
                }
                processManager.sendCommand(goCommand)
                Log.d("StockfishAudit", "FEN Dikirim ke Engine: $fen")

                val result = withTimeoutOrNull(limitMs + 3000L) {
                    resultDeferred.await()
                }

                val rawEngineResponse = collectedLines.joinToString("\n")
                val bestMove = result?.bestMove

                Log.d("StockfishBridge", "=== REQUEST EVALUATION ===")
                Log.d("StockfishBridge", "INPUT FEN: $fen")
                Log.d("StockfishBridge", "RAW ENGINE OUTPUT:\n$rawEngineResponse")
                Log.d("StockfishBridge", "PARSED BESTMOVE: $bestMove")
                Log.d("StockfishBridge", "==========================")

                if (result != null && result.bestMove != "0000") {
                    return@withContext result
                } else {
                    val duration = System.currentTimeMillis() - startTime
                    Log.w(TAG, "Stockfish returned no move or timed out (${duration}ms)")
                    stopEvaluation()
                    return@withContext EngineResult(bestMove = "0000", calculationTimeMs = duration)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Engine eval exception: ${e.message}", e)
                val duration = System.currentTimeMillis() - startTime
                return@withContext EngineResult(bestMove = "0000", calculationTimeMs = duration)
            } finally {
                listenerJob.cancel()
            }
        }
    }

    override suspend fun stopEvaluation() {
        processManager.stopEvaluation()
    }

    override fun release() {
        processManager.destroy()
        serviceScope.cancel()
        isInitialized = false
    }
}
