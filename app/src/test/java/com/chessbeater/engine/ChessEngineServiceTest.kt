package com.chessbeater.engine

import com.chessbeater.engine.models.EngineConfig
import com.chessbeater.engine.models.EngineType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChessEngineServiceTest {

    @Test
    fun testStockfishPowerCalibrationAtZeroPercent() {
        val config = EngineConfig(EngineType.STOCKFISH, powerPercentage = 0)
        val commands = config.toUciCommands()

        assertTrue(commands.contains("setoption name UCI_LimitStrength value true"))
        assertTrue(commands.contains("setoption name UCI_Elo value 800"))
        assertTrue(commands.contains("setoption name Skill Level value 0"))
        // Power < 30% should inject error for human-like beginner play
        assertTrue(commands.contains("setoption name Skill Level Maximum Error value 200"))
        assertEquals("go depth 1 movetime 50", config.toGoCommand())
    }

    @Test
    fun testStockfishPowerCalibrationAtFullStrength() {
        val config = EngineConfig(EngineType.STOCKFISH, powerPercentage = 100)
        val commands = config.toUciCommands()

        assertTrue(commands.contains("setoption name UCI_LimitStrength value true"))
        assertTrue(commands.contains("setoption name UCI_Elo value 3500"))
        assertTrue(commands.contains("setoption name Skill Level value 20"))
        assertTrue(commands.contains("setoption name Threads value 2"))
        assertTrue(commands.contains("setoption name Hash value 32"))
        assertFalse(commands.any { it.contains("Skill Level Maximum Error") })
        assertEquals("go depth 25 movetime 2000", config.toGoCommand())
    }

    @Test
    fun testLc0PowerCalibration() {
        val config = EngineConfig(EngineType.LC0_ALPHAZERO, powerPercentage = 50)
        val commands = config.toUciCommands()

        assertTrue(commands.contains("setoption name Nodes value 760"))
        assertTrue(commands.contains("setoption name MiniBatchSize value 16"))
        assertEquals("go nodes 760", config.toGoCommand())
    }

    @Test
    fun testRetroDeepBlueCalibration() {
        val config = EngineConfig(EngineType.DEEP_BLUE_CLASSIC, powerPercentage = 100)
        val commands = config.toUciCommands()

        assertTrue(commands.contains("setoption name ClassicDepth value 13"))
        assertEquals("go depth 13", config.toGoCommand())
    }

    @Test
    fun testParseUciOutputStandardCentipawns() {
        val outputLines = listOf(
            "info depth 1 seldepth 1 multipv 1 score cp 20 nodes 20 pv e2e4",
            "info depth 12 seldepth 16 multipv 1 score cp 65 nodes 45000 nps 900000 time 50 pv e2e4 e7e5 g1f3",
            "bestmove e2e4 ponder e7e5"
        )

        val result = ChessEngineService.parseUciOutput(outputLines, calculationTimeMs = 120L)
        assertNotNull(result)
        assertEquals("e2e4", result?.bestMove)
        assertEquals("e7e5", result?.ponderMove)
        assertEquals(65, result?.evaluationCentipawns)
        assertNull(result?.mateInMoves)
        assertEquals(12, result?.depth)
        assertEquals(120L, result?.calculationTimeMs)
    }

    @Test
    fun testParseUciOutputWinningMate() {
        val outputLines = listOf(
            "info depth 8 multipv 1 score mate 3 nodes 12000 pv d1h5 g7g6 h5e5",
            "bestmove d1h5 ponder g7g6"
        )

        val result = ChessEngineService.parseUciOutput(outputLines, calculationTimeMs = 85L)
        assertNotNull(result)
        assertEquals("d1h5", result?.bestMove)
        assertEquals("g7g6", result?.ponderMove)
        assertEquals(3, result?.mateInMoves)
        assertNull(result?.evaluationCentipawns)
        assertEquals(8, result?.depth)
    }

    @Test
    fun testParseUciOutputLosingMate() {
        val outputLines = listOf(
            "info depth 6 multipv 1 score mate -1 nodes 4000 pv f7f8",
            "bestmove f7f8"
        )

        val result = ChessEngineService.parseUciOutput(outputLines, calculationTimeMs = 40L)
        assertNotNull(result)
        assertEquals("f7f8", result?.bestMove)
        assertNull(result?.ponderMove)
        assertEquals(-1, result?.mateInMoves)
    }

    @Test
    fun testEngineServiceEvaluatePositionFlow() = kotlinx.coroutines.runBlocking {
        val service = ChessEngineService()
        val initSuccess = service.initializeEngine()
        assertTrue(initSuccess)

        val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        val result = service.evaluatePosition(startFen)

        assertNotNull(result)
        assertTrue(result.bestMove.length in 4..5)
        assertTrue(result.depth > 0)

        service.release()
    }
}

