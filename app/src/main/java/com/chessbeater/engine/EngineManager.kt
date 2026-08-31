package com.chessbeater.engine

import com.chessbeater.engine.lc0.Lc0EngineBridge
import com.chessbeater.engine.models.ChessEngineBridge
import com.chessbeater.engine.models.EngineConfig
import com.chessbeater.engine.models.EngineResult
import com.chessbeater.engine.models.EngineType
import com.chessbeater.engine.retro.RetroMinimaxEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Unified Engine Factory and Multi-Engine Dynamic Switcher (Hot-Switch).
 * Manages Stockfish 16.1 NNUE, Leela Chess Zero (Lc0), and Retro Minimax engines seamlessly.
 */
class EngineManager(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    val stockfishEngine: ChessEngineService = ChessEngineService(dispatcher = dispatcher),
    val lc0Engine: Lc0EngineBridge = Lc0EngineBridge(dispatcher = dispatcher),
    val retroEngine: RetroMinimaxEngine = RetroMinimaxEngine(dispatcher = dispatcher)
) : ChessEngineBridge {

    private val switchMutex = Mutex()
    private var currentConfig = EngineConfig(EngineType.STOCKFISH, 100)

    var activeEngineType: EngineType = EngineType.STOCKFISH
        private set

    private fun getEngineBridge(type: EngineType): ChessEngineBridge {
        return when (type) {
            EngineType.STOCKFISH -> stockfishEngine
            EngineType.LC0_ALPHAZERO -> lc0Engine
            EngineType.DEEP_BLUE_CLASSIC -> retroEngine
        }
    }

    private val currentBridge: ChessEngineBridge
        get() = getEngineBridge(activeEngineType)

    override suspend fun initializeEngine(): Boolean = withContext(dispatcher) {
        switchMutex.withLock {
            stockfishEngine.initializeEngine()
            lc0Engine.initializeEngine()
            retroEngine.initializeEngine()
            true
        }
    }

    /**
     * Dynamically switches the active engine at runtime (hot-switch)
     */
    suspend fun switchEngine(newType: EngineType) = withContext(dispatcher) {
        switchMutex.withLock {
            if (activeEngineType == newType) return@withContext

            currentBridge.stopEvaluation()
            activeEngineType = newType
            currentConfig = currentConfig.copy(engineType = newType)

            val newBridge = getEngineBridge(newType)
            newBridge.initializeEngine()
            newBridge.setStrength(currentConfig)
        }
    }

    override suspend fun setStrength(config: EngineConfig): Unit = withContext(dispatcher) {
        switchMutex.withLock {
            if (config.engineType != activeEngineType) {
                activeEngineType = config.engineType
            }
            currentConfig = config
            currentBridge.setStrength(config)
            Unit
        }
    }


    override suspend fun evaluatePosition(fen: String): EngineResult = withContext(dispatcher) {
        switchMutex.withLock {
            currentBridge.evaluatePosition(fen)
        }
    }

    override suspend fun stopEvaluation() {
        currentBridge.stopEvaluation()
    }

    override fun release() {
        stockfishEngine.release()
        lc0Engine.release()
        retroEngine.release()
    }
}
