package com.chessbeater.orchestrator

import android.graphics.Bitmap
import android.util.Log
import com.chessbeater.capture.ScreenCaptureService
import com.chessbeater.engine.EngineManager
import com.chessbeater.engine.models.ChessEngineBridge
import com.chessbeater.engine.models.EngineConfig
import com.chessbeater.governor.BatteryGovernor
import com.chessbeater.haptics.HapticFeedbackManager
import com.chessbeater.overlay.OverlayManager
import com.chessbeater.vision.BoardVisionPipeline
import com.chessbeater.vision.models.PlayerColor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sprint 25: Integration Orchestrator (Glue Layer)
 * - Player Color Locking: Locked after 3 initial frames or manually toggled via HUD [⚪/⚫]
 * - Turn Filtering: Shows recommendation arrows and evaluation only on player's turn; clears arrow and displays "Menunggu giliran lawan..." on opponent turn
 * - FEN Debouncer: 2-frame stability check before triggering engine evaluation
 */
class GameOrchestrator(
    private val visionPipeline: BoardVisionPipeline = BoardVisionPipeline(),
    private val engineService: ChessEngineBridge = EngineManager(dispatcher = Dispatchers.Default),
    private val overlayManager: OverlayManager? = null,
    private val hapticManager: HapticFeedbackManager? = null,
    private val batteryGovernor: BatteryGovernor = BatteryGovernor(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    private val orchestratorScope = CoroutineScope(dispatcher + SupervisorJob())
    private val isProcessingFrame = AtomicBoolean(false)
    val engineWatchdog: com.chessbeater.engine.watchdog.EngineWatchdog = if (engineService is EngineManager) {
        com.chessbeater.engine.watchdog.EngineWatchdog(engineService, dispatcher)
    } else {
        com.chessbeater.engine.watchdog.EngineWatchdog(EngineManager(dispatcher), dispatcher)
    }

    private val _stateFlow = MutableStateFlow(OrchestratorState())
    val stateFlow: StateFlow<OrchestratorState> = _stateFlow.asStateFlow()

    private var frameCollectJob: Job? = null
    private var lastProcessedFen: String = ""
    private var candidateFen: String = ""
    private var candidateFenCount: Int = 0

    // Player Color Locking
    private var playerColor: PlayerColor? = null
    private var colorCandidate: PlayerColor? = null
    private var colorCandidateStreak: Int = 0
    private var isManualColorLocked: Boolean = false

    private var frameCount: Int = 0
    private var lastFpsTimestamp: Long = System.currentTimeMillis()

    companion object {
        private const val TAG = "GameOrchestrator"
    }

    /**
     * Initializes engine and starts listening for screen capture frames
     */
    suspend fun start(): Boolean = withContext(dispatcher) {
        if (_stateFlow.value.isRunning) return@withContext true

        Log.i(TAG, "Starting GameOrchestrator...")
        val engineInitialized = engineService.initializeEngine()
        if (!engineInitialized) {
            Log.e(TAG, "Failed to initialize native chess engine")
            return@withContext false
        }

        overlayManager?.showOverlays()
        playerColor?.let { overlayManager?.setPlayerColor(it) }
        engineWatchdog.startMonitoring()

        _stateFlow.value = _stateFlow.value.copy(isRunning = true)

        startFrameCollector()
        Log.i(TAG, "GameOrchestrator started successfully")
        true
    }

    /**
     * Stops the pipeline, overlays, and releases resources
     */
    fun stop() {
        if (!_stateFlow.value.isRunning) return

        engineWatchdog.stopMonitoring()
        frameCollectJob?.cancel()
        frameCollectJob = null

        overlayManager?.clearOverlays()
        overlayManager?.hideOverlays()
        hapticManager?.cancel()

        _stateFlow.value = _stateFlow.value.copy(isRunning = false)
        Log.i(TAG, "GameOrchestrator stopped")
    }

    /**
     * Subscribes to the real-time frame stream from ScreenCaptureService
     */
    private fun startFrameCollector() {
        frameCollectJob?.cancel()
        frameCollectJob = orchestratorScope.launch {
            ScreenCaptureService.frameFlow.collect { bitmap ->
                if (_stateFlow.value.isRunning) {
                    processIncomingFrame(bitmap)
                }
            }
        }
    }

    private var appProfile: com.chessbeater.vision.models.ChessAppProfile = com.chessbeater.vision.models.ChessAppProfile()

    fun updateAppProfile(profile: com.chessbeater.vision.models.ChessAppProfile) {
        this.appProfile = profile
    }

    fun toggleManualPlayerColor() {
        val current = playerColor ?: PlayerColor.WHITE
        val newColor = if (current == PlayerColor.WHITE) PlayerColor.BLACK else PlayerColor.WHITE
        setManualPlayerColor(newColor)
    }

    fun setManualPlayerColor(color: PlayerColor) {
        playerColor = color
        isManualColorLocked = true
        overlayManager?.setPlayerColor(color)
        lastProcessedFen = ""
        candidateFen = ""
        candidateFenCount = 0
        Log.i(TAG, "Manual player color locked to: $color")
    }

    /**
     * Processes a single frame through the complete pipeline with strict throttling.
     */
    suspend fun processIncomingFrame(frameBitmap: Bitmap) {
        if (!batteryGovernor.shouldProcessFrame()) {
            return
        }

        if (!isProcessingFrame.compareAndSet(false, true)) {
            return
        }

        try {
            val frameStartTime = System.currentTimeMillis()

            // 1. Vision Analysis (Board detection, Warping, Piece classification, FEN assembly)
            val visionResult = visionPipeline.processFrame(frameBitmap, appProfile, playerColor)
            val visionLatency = visionResult.latencyMs


            batteryGovernor.onFrameAnalysisCompleted(
                isPositionChanged = visionResult.isPositionChanged,
                changedSquareCount = visionResult.changedSquares.size
            )

            // Update diagnostic HUD status
            val currentCalculatedFps = calculateFps()
            overlayManager?.updateDiagnostic(
                fps = currentCalculatedFps,
                isBoardDetected = visionResult.isBoardDetected,
                isEngineCalculating = false
            )

            // 2. Player Color Locking (First 3 frames)
            if (!isManualColorLocked && playerColor == null && visionResult.isBoardDetected) {
                val detectedOrientation = visionResult.playerOrientation
                if (detectedOrientation == colorCandidate) {
                    colorCandidateStreak++
                    if (colorCandidateStreak >= 3) {
                        playerColor = detectedOrientation
                        overlayManager?.setPlayerColor(detectedOrientation)
                        Log.i("BoardOrientationDetector", "Player color successfully locked to: $playerColor")
                    }
                } else {
                    colorCandidate = detectedOrientation
                    colorCandidateStreak = 1
                }
            }

            val activePlayerColor = playerColor ?: visionResult.playerOrientation

            // 3. FEN Debouncer & Turn Filtering
            val currentFen = visionResult.fen
            if (currentFen.isNotBlank()) {
                if (currentFen == candidateFen) {
                    candidateFenCount++
                } else {
                    candidateFen = currentFen
                    candidateFenCount = 1
                }
            }

            val isPositionStable = (candidateFenCount >= 2) || (lastProcessedFen.isEmpty() && currentFen.isNotBlank())
            val hasPositionChanged = isPositionStable && currentFen.isNotBlank() && currentFen != lastProcessedFen

            // Parse active turn from FEN ('w' or 'b')
            val fenParts = currentFen.trim().split(" ")
            val activeTurn = if (fenParts.getOrNull(1) == "b") PlayerColor.BLACK else PlayerColor.WHITE
            val isOurTurn = (activeTurn == activePlayerColor)

            if (hasPositionChanged) {
                lastProcessedFen = currentFen

                if (isOurTurn) {
                    // --- PLAYER'S TURN ---
                    overlayManager?.updateDiagnostic(
                        fps = currentCalculatedFps,
                        isBoardDetected = visionResult.isBoardDetected,
                        isEngineCalculating = true
                    )

                    val engineStartTime = System.currentTimeMillis()
                    val engineResult = engineWatchdog.safeEvaluate(currentFen)
                    val engineLatency = System.currentTimeMillis() - engineStartTime
                    val totalLatency = System.currentTimeMillis() - frameStartTime

                    Log.i(TAG, "Player's turn ($activePlayerColor). BestMove: ${engineResult.bestMove}")

                    // Update Recommendation Arrow
                    overlayManager?.updateBoardArrow(
                        bestMove = engineResult.bestMove,
                        evalCentipawns = engineResult.evaluationCentipawns,
                        mateInMoves = engineResult.mateInMoves,
                        boardRect = visionResult.boardBoundingRect,
                        orientation = activePlayerColor
                    )

                    // Update Floating HUD
                    val turnLabel = if (activePlayerColor == PlayerColor.WHITE) "Putih" else "Hitam"
                    overlayManager?.updateHud(
                        bestMove = "👉 Giliran Anda ($turnLabel): ${engineResult.bestMove ?: "..."}",
                        evalCentipawns = engineResult.evaluationCentipawns,
                        mateInMoves = engineResult.mateInMoves,
                        depth = engineResult.depth,
                        latencyMs = totalLatency
                    )


                    // Trigger Tactile Haptics
                    hapticManager?.onEngineResultReceived(
                        bestMove = engineResult.bestMove,
                        evalCentipawns = engineResult.evaluationCentipawns,
                        mateInMoves = engineResult.mateInMoves
                    )

                    updateMetrics(
                        fen = currentFen,
                        bestMove = engineResult.bestMove,
                        evalCp = engineResult.evaluationCentipawns,
                        mate = engineResult.mateInMoves,
                        depth = engineResult.depth,
                        totalLatency = totalLatency,
                        visionLat = visionLatency,
                        engineLat = engineLatency,
                        orientation = activePlayerColor,
                        boardRect = visionResult.boardBoundingRect,
                        calculatedFps = currentCalculatedFps
                    )
                } else {
                    // --- OPPONENT'S TURN ---
                    val totalLatency = System.currentTimeMillis() - frameStartTime
                    Log.i(TAG, "Opponent's turn. Clearing recommendation arrow.")

                    // Clear recommendation arrow from screen
                    overlayManager?.clearBoardArrow()

                    // Update Floating HUD
                    overlayManager?.updateHud(
                        bestMove = "⏳ Menunggu giliran lawan...",
                        evalCentipawns = _stateFlow.value.evalCentipawns,
                        mateInMoves = _stateFlow.value.mateInMoves,
                        depth = _stateFlow.value.searchDepth,
                        latencyMs = totalLatency
                    )

                    updateMetrics(
                        fen = currentFen,
                        bestMove = "⏳ Menunggu giliran lawan...",
                        evalCp = _stateFlow.value.evalCentipawns,
                        mate = _stateFlow.value.mateInMoves,
                        depth = _stateFlow.value.searchDepth,
                        totalLatency = totalLatency,
                        visionLat = visionLatency,
                        engineLat = 0L,
                        orientation = activePlayerColor,
                        boardRect = visionResult.boardBoundingRect,
                        calculatedFps = currentCalculatedFps
                    )
                }
            } else {
                // If board bounding rect shifted, keep arrow aligned only on our turn
                if (isOurTurn && visionResult.boardBoundingRect != null && _stateFlow.value.bestMove != null && !_stateFlow.value.bestMove!!.startsWith("⏳")) {
                    overlayManager?.updateBoardArrow(
                        bestMove = _stateFlow.value.bestMove,
                        evalCentipawns = _stateFlow.value.evalCentipawns,
                        mateInMoves = _stateFlow.value.mateInMoves,
                        boardRect = visionResult.boardBoundingRect,
                        orientation = activePlayerColor
                    )
                } else if (!isOurTurn) {
                    overlayManager?.clearBoardArrow()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in GameOrchestrator pipeline loop: ${e.message}", e)
            overlayManager?.updateDiagnostic(
                fps = 0f,
                isBoardDetected = false,
                isEngineCalculating = false
            )
        } finally {
            isProcessingFrame.set(false)
        }
    }

    private fun calculateFps(): Float {
        frameCount++
        val now = System.currentTimeMillis()
        val elapsedSec = (now - lastFpsTimestamp) / 1000.0f
        return if (elapsedSec >= 1.0f) {
            val fps = frameCount / elapsedSec
            frameCount = 0
            lastFpsTimestamp = now
            fps
        } else {
            _stateFlow.value.fps
        }
    }

    suspend fun updateEngineConfig(config: EngineConfig) = withContext(dispatcher) {
        engineService.setStrength(config)
        _stateFlow.value = _stateFlow.value.copy(engineConfig = config)
    }

    suspend fun evaluateCustomFen(fen: String): com.chessbeater.engine.models.EngineResult = withContext(dispatcher) {
        engineWatchdog.safeEvaluate(fen)
    }

    fun resetGame() {
        visionPipeline.reset()
        lastProcessedFen = ""
        candidateFen = ""
        candidateFenCount = 0
        overlayManager?.clearOverlays()
        _stateFlow.value = _stateFlow.value.copy(
            currentFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            bestMove = null,
            evalCentipawns = null,
            mateInMoves = null
        )
    }

    private fun updateMetrics(
        fen: String,
        bestMove: String,
        evalCp: Int?,
        mate: Int?,
        depth: Int,
        totalLatency: Long,
        visionLat: Long,
        engineLat: Long,
        orientation: PlayerColor,
        boardRect: android.graphics.Rect?,
        calculatedFps: Float = _stateFlow.value.fps
    ) {
        _stateFlow.value = _stateFlow.value.copy(
            currentFen = fen,
            bestMove = bestMove,
            evalCentipawns = evalCp,
            mateInMoves = mate,
            searchDepth = depth,
            lastEndToEndLatencyMs = totalLatency,
            visionLatencyMs = visionLat,
            engineLatencyMs = engineLat,
            fps = calculatedFps,
            playerOrientation = orientation,
            boardBoundingRect = boardRect
        )
    }

    fun release() {
        stop()
        engineWatchdog.release()
        engineService.release()
        orchestratorScope.cancel()
    }
}
