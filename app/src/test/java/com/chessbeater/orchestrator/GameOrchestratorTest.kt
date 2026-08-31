package com.chessbeater.orchestrator

import com.chessbeater.engine.ChessEngineService
import com.chessbeater.engine.models.EngineConfig
import com.chessbeater.engine.models.EngineType
import com.chessbeater.vision.BoardVisionPipeline
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameOrchestratorTest {

    @Test
    fun testOrchestratorLifecycleAndState() = kotlinx.coroutines.runBlocking {
        val engineService = ChessEngineService()
        val visionPipeline = BoardVisionPipeline()

        val orchestrator = GameOrchestrator(
            visionPipeline = visionPipeline,
            engineService = engineService,
            overlayManager = null,
            hapticManager = null
        )

        assertFalse(orchestrator.stateFlow.value.isRunning)

        val started = orchestrator.start()
        assertTrue(started)
        assertTrue(orchestrator.stateFlow.value.isRunning)

        // Test Engine Config Update
        val newConfig = EngineConfig(EngineType.STOCKFISH, powerPercentage = 60)
        orchestrator.updateEngineConfig(newConfig)

        assertEquals(60, orchestrator.stateFlow.value.engineConfig.powerPercentage)

        // Test Reset Game State
        orchestrator.resetGame()
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", orchestrator.stateFlow.value.currentFen)
        assertNull(orchestrator.stateFlow.value.bestMove)

        orchestrator.stop()
        assertFalse(orchestrator.stateFlow.value.isRunning)

        orchestrator.release()
    }
}


