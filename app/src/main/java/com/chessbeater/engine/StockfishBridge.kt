package com.chessbeater.engine

import android.content.Context
import android.os.Build
import android.util.Log
import com.chessbeater.ChessBeaterApp
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * Sprint 64: StockfishBridge with MultiPV Humanization & Natural Move Delay.
 */
class StockfishBridge(private val context: Context) {

    companion object {
        private const val TAG = "StockfishBridge"

        @Volatile
        private var instance: StockfishBridge? = null

        fun getInstance(context: Context = ChessBeaterApp.instance): StockfishBridge {
            return instance ?: synchronized(this) {
                instance ?: StockfishBridge(context.applicationContext).also { instance = it }
            }
        }
    }

    private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null

    private val bridgeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var readerJob: Job? = null
    private var isJniMode = false
    private var isEngineReady = false
    var hasEngineCrashed: Boolean = false
        private set

    // Promise penampung hasil bestmove & handshake aktif
    private val pendingBestMoveDeferred = AtomicReference<CompletableDeferred<String>?>(null)
    private val readyOkDeferred = AtomicReference<CompletableDeferred<Boolean>?>(null)
    private val currentMultiPvCandidates = ConcurrentHashMap<Int, HumanizationEngine.MoveCandidate>()

    init {
        HumanizationEngine.init(context)
    }

    fun isEngineHealthy(): Boolean {
        return isEngineReady && (isJniMode || (process != null && isProcessAlive(process)))
    }

    fun isNativeEngineAlive(): Boolean = isEngineHealthy()

    fun startEngine() {
        bridgeScope.launch {
            try {
                if (process != null && isProcessAlive(process) && isEngineReady) return@launch
                stopEngineInternal()
                hasEngineCrashed = false

                val nativeLib = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")
                Log.d("StockfishInit", "Path binary: ${nativeLib.absolutePath} | Exists: ${nativeLib.exists()}")

                if (nativeLib.exists()) {
                    try {
                        nativeLib.setExecutable(true, false)
                        nativeLib.setReadable(true, false)
                    } catch (ignored: Exception) {}

                    try {
                        val pb = ProcessBuilder(nativeLib.absolutePath).redirectErrorStream(true)
                        val proc = pb.start()
                        process = proc

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !proc.isAlive) {
                            Log.e("StockfishInit", "❌ Process langsung exit")
                            hasEngineCrashed = true
                        } else {
                            writer = OutputStreamWriter(proc.outputStream, Charsets.UTF_8)
                            reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8), 8192)

                            startSingleReaderLoop()

                            // Kirim inisialisasi awal dengan MultiPV 3
                            val initBatch = "uci\nsetoption name MultiPV value 3\nsetoption name UCI_LimitStrength value true\nsetoption name UCI_Elo value 2200\nsetoption name Skill Level value 11\nsetoption name Threads value 2\nsetoption name Hash value 16\nisready\n"
                            writer?.write(initBatch)
                            writer?.flush()
                            Log.d("StockfishNative", "UCI Init Batch Sent with MultiPV 3")

                            val readyPromise = CompletableDeferred<Boolean>()
                            readyOkDeferred.set(readyPromise)

                            val ready = withTimeoutOrNull(2500L) { readyPromise.await() }
                            if (ready == true) {
                                isEngineReady = true
                                isJniMode = false
                                Log.d("StockfishInit", "✅ Single Stockfish Instance Aktif & Sinkron!")
                                return@launch
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("StockfishInit", "Gagal inisialisasi subprocess", e)
                        hasEngineCrashed = true
                    }
                }

                // JNI C++ In-Process Fallback
                if (StockfishNativeBridge.isNativeLoaded()) {
                    val init = StockfishNativeBridge.nativeInitEngine()
                    if (init) {
                        isJniMode = true
                        isEngineReady = true
                        hasEngineCrashed = false
                        StockfishNativeBridge.nativeSendUciCommand("uci")
                        StockfishNativeBridge.nativeSendUciCommand("setoption name MultiPV value 3")
                        StockfishNativeBridge.nativeSendUciCommand("ucinewgame")
                        StockfishNativeBridge.nativeSendUciCommand("isready")
                        startJniReaderLoop()
                        Log.d("StockfishInit", "✅ High-Speed JNI C++ Stockfish Engine Aktif!")
                        return@launch
                    }
                }

                hasEngineCrashed = true
                Log.e("StockfishInit", "❌ Seluruh engine Stockfish gagal diinisialisasi.")
            } catch (e: Exception) {
                Log.e("StockfishBridge", "Gagal start engine", e)
            }
        }
    }

    private fun startSingleReaderLoop() {
        readerJob?.cancel()
        readerJob = bridgeScope.launch {
            try {
                while (isActive) {
                    val line = reader?.readLine() ?: break
                    Log.d("StockfishOutput", line)

                    if (line.contains("readyok")) {
                        readyOkDeferred.getAndSet(null)?.complete(true)
                    }

                    parseMultiPvLine(line)

                    if (line.startsWith("bestmove")) {
                        handleBestMoveOutput(line)
                    }
                }
            } catch (e: Exception) {
                Log.e("StockfishBridge", "Reader loop terminated", e)
            }
        }
    }

    private fun startJniReaderLoop() {
        readerJob?.cancel()
        readerJob = bridgeScope.launch {
            try {
                while (isActive) {
                    val line = StockfishNativeBridge.nativeReadEngineOutput()
                    if (line != null) {
                        Log.d("StockfishOutput", line)
                        if (line.startsWith("bestmove")) {
                            handleBestMoveOutput(line)
                        } else {
                            parseMultiPvLine(line)
                        }
                    } else {
                        delay(10)
                    }
                }
            } catch (e: Exception) {
                Log.e("StockfishBridge", "JNI reader loop terminated", e)
            }
        }
    }

    private fun parseMultiPvLine(line: String) {
        if (line.contains("multipv") && line.contains("pv ")) {
            try {
                val pvIdx = line.substringAfter("multipv ").substringBefore(" ").toIntOrNull() ?: 1
                val scoreCp = if (line.contains("score cp ")) {
                    line.substringAfter("score cp ").substringBefore(" ").toIntOrNull() ?: 0
                } else 0
                val move = line.substringAfter("pv ").trim().split(Regex("\\s+")).firstOrNull()
                if (move != null && move.length in 4..5) {
                    currentMultiPvCandidates[pvIdx] = HumanizationEngine.MoveCandidate(move, scoreCp, pvIdx)
                }
            } catch (ignored: Exception) {}
        }
    }

    private fun handleBestMoveOutput(line: String) {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size >= 2 && parts[1] != "(none)" && parts[1] != "0000") {
            val originalBest = parts[1]
            if (!currentMultiPvCandidates.containsKey(1)) {
                currentMultiPvCandidates[1] = HumanizationEngine.MoveCandidate(originalBest, 0, 1)
            }
            val candidatesList = currentMultiPvCandidates.values.sortedBy { it.pvIndex }
            val selected = HumanizationEngine.selectHumanizedMove(candidatesList) ?: originalBest

            Log.d("Humanize", "🎯 Bestmove terpilih: $selected (Engine T1: $originalBest, Candidates: ${candidatesList.size})")
            currentMultiPvCandidates.clear()
            pendingBestMoveDeferred.getAndSet(null)?.complete(selected)
        } else {
            pendingBestMoveDeferred.getAndSet(null)?.complete("")
        }
    }

    fun sendCommand(cmd: String) {
        bridgeScope.launch {
            try {
                if (isJniMode) {
                    StockfishNativeBridge.nativeSendUciCommand(cmd)
                } else {
                    writer?.write("$cmd\n")
                    writer?.flush()
                }
                Log.d("StockfishNative", "UCI Command: $cmd")
            } catch (e: Exception) {
                Log.e("StockfishBridge", "Gagal kirim command: $cmd", e)
            }
        }
    }

    fun setElo(elo: Int) {
        val skill = (((elo.coerceIn(1320, 3190) - 1320f) / (3190f - 1320f)) * 20f).toInt().coerceIn(0, 20)
        val batch = "setoption name UCI_LimitStrength value true\nsetoption name UCI_Elo value $elo\nsetoption name Skill Level value $skill\nisready\n"
        bridgeScope.launch {
            try {
                writer?.write(batch)
                writer?.flush()
            } catch (ignored: Exception) {}
        }
    }

    fun setEloRating(elo: Int) = setElo(elo)

    fun setMaxElo(maxElo: Int) {
        if (maxElo >= 3400) {
            applyFullStrengthMode()
        } else {
            setElo(maxElo)
        }
    }

    fun applyFullStrengthMode() {
        val batch = "setoption name UCI_LimitStrength value false\nsetoption name Skill Level value 20\nsetoption name Threads value 2\nsetoption name Hash value 16\nisready\n"
        bridgeScope.launch {
            try {
                writer?.write(batch)
                writer?.flush()
            } catch (ignored: Exception) {}
        }
    }

    suspend fun getBestMove(fen: String, moveTimeMs: Long = 400L): String? = withContext(Dispatchers.IO) {
        if (!isEngineHealthy()) {
            startEngine()
            delay(300)
        }

        currentMultiPvCandidates.clear()
        val movePromise = CompletableDeferred<String>()
        pendingBestMoveDeferred.set(movePromise)

        val effectiveMovetime = moveTimeMs.coerceAtLeast(300L)
        val depthCmd = if (effectiveMovetime < 400L) "go depth 12" else "go movetime $effectiveMovetime"

        // Kirim paket evaluasi
        if (isJniMode) {
            StockfishNativeBridge.nativeSendUciCommand("stop")
            StockfishNativeBridge.nativeSendUciCommand("position fen $fen")
            StockfishNativeBridge.nativeSendUciCommand(depthCmd)
        } else {
            val batchCommand = "stop\nposition fen $fen\n$depthCmd\n"
            try {
                writer?.write(batchCommand)
                writer?.flush()
                Log.d("StockfishNative", "UCI Batch Sent:\n$batchCommand")
            } catch (e: Exception) {
                Log.e("StockfishBridge", "Gagal kirim batch command", e)
                return@withContext null
            }
        }

        return@withContext try {
            withTimeout(effectiveMovetime + 1500L) {
                val move = movePromise.await()
                if (move.isNotBlank()) {
                    hasEngineCrashed = false

                    // Terapkan Natural Move Delay jika diaktifkan (1200ms - 3000ms)
                    if (HumanizationEngine.isNaturalDelayEnabled) {
                        val naturalDelay = Random.nextLong(1200L, 3000L)
                        Log.d("Humanize", "⏳ Menerapkan Natural Move Delay: ${naturalDelay}ms")
                        delay(naturalDelay)
                    }

                    Log.d("StockfishSuccess", "🏹 Menggambar panah untuk langkah: $move")
                    move
                } else null
            }
        } catch (e: TimeoutCancellationException) {
            Log.w("StockfishBridge", "⚠️ Timeout evaluasi FEN: $fen")
            pendingBestMoveDeferred.set(null)
            null
        } catch (e: Exception) {
            Log.e("StockfishBridge", "Error saat await bestmove", e)
            pendingBestMoveDeferred.set(null)
            null
        }
    }

    private fun stopEngineInternal() {
        try {
            readerJob?.cancel()
            writer?.write("quit\n")
            writer?.flush()
            writer?.close()
            reader?.close()
            process?.destroy()
        } catch (ignored: Exception) {}
        process = null
        writer = null
        reader = null
        isEngineReady = false
        currentMultiPvCandidates.clear()
    }

    fun stopEngine() {
        bridgeScope.launch {
            stopEngineInternal()
            if (isJniMode) {
                StockfishNativeBridge.nativeDestroyEngine()
                isJniMode = false
            }
        }
    }

    private fun isProcessAlive(p: Process?): Boolean {
        if (p == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            p.isAlive
        } else {
            try {
                p.exitValue()
                false
            } catch (_: IllegalThreadStateException) {
                true
            }
        }
    }
}
