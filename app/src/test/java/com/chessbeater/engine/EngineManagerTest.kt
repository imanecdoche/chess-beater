package com.chessbeater.engine

import com.chessbeater.engine.models.EngineConfig
import com.chessbeater.engine.models.EngineType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EngineManagerTest {

    @Test
    fun testDynamicEngineSwitching() = kotlinx.coroutines.runBlocking {
        val manager = EngineManager()

        assertEquals(EngineType.STOCKFISH, manager.activeEngineType)

        // Switch to Leela Chess Zero
        manager.switchEngine(EngineType.LC0_ALPHAZERO)
        assertEquals(EngineType.LC0_ALPHAZERO, manager.activeEngineType)

        // Switch to Deep Blue Retro
        manager.switchEngine(EngineType.DEEP_BLUE_CLASSIC)
        assertEquals(EngineType.DEEP_BLUE_CLASSIC, manager.activeEngineType)

        // Switch back to Stockfish
        manager.switchEngine(EngineType.STOCKFISH)
        assertEquals(EngineType.STOCKFISH, manager.activeEngineType)
        manager.release()
    }

    @Test
    fun testSetStrengthUpdatesConfig() = kotlinx.coroutines.runBlocking {
        val manager = EngineManager()

        val config = EngineConfig(EngineType.LC0_ALPHAZERO, powerPercentage = 75)
        manager.setStrength(config)

        assertEquals(EngineType.LC0_ALPHAZERO, manager.activeEngineType)
        manager.release()
    }
}



