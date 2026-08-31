package com.chessbeater.orchestrator

import android.graphics.Rect
import com.chessbeater.engine.models.EngineConfig
import com.chessbeater.engine.models.EngineType
import com.chessbeater.vision.models.PlayerColor

/**
 * State representing the real-time status of the Chess Beater orchestration pipeline
 */
data class OrchestratorState(
    val isRunning: Boolean = false,
    val currentFen: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    val bestMove: String? = null,
    val evalCentipawns: Int? = null,
    val mateInMoves: Int? = null,
    val searchDepth: Int = 0,
    val boardBoundingRect: Rect? = null,
    val playerOrientation: PlayerColor = PlayerColor.WHITE,
    val lastEndToEndLatencyMs: Long = 0L,
    val visionLatencyMs: Long = 0L,
    val engineLatencyMs: Long = 0L,
    val fps: Float = 0.0f,
    val engineConfig: EngineConfig = EngineConfig(EngineType.STOCKFISH, 100)
)
