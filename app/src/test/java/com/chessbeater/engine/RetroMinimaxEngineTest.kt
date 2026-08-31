package com.chessbeater.engine

import com.chessbeater.engine.models.EngineConfig
import com.chessbeater.engine.models.EngineType
import com.chessbeater.engine.retro.RetroMinimaxEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetroMinimaxEngineTest {

    @Test
    fun testParseFenToBoard() {
        val engine = RetroMinimaxEngine()
        val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        val board = engine.parseFenToBoard(startFen)

        assertEquals(64, board.size)
        // Rank 8: r n b q k b n r
        assertEquals('r', board[0])
        assertEquals('n', board[1])
        assertEquals('b', board[2])
        assertEquals('q', board[3])
        assertEquals('k', board[4])
        assertEquals('b', board[5])
        assertEquals('n', board[6])
        assertEquals('r', board[7])

        // Rank 1: R N B Q K B N R
        assertEquals('R', board[56])
        assertEquals('N', board[57])
        assertEquals('B', board[58])
        assertEquals('Q', board[59])
        assertEquals('K', board[60])
        assertEquals('B', board[61])
        assertEquals('N', board[62])
        assertEquals('R', board[63])

        // Empty Squares
        assertEquals('.', board[20])
    }

    @Test
    fun testStartingPositionBoardScoreIsBalanced() {
        val engine = RetroMinimaxEngine()
        val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        val board = engine.parseFenToBoard(startFen)
        val score = engine.evaluateBoardScore(board)

        // Starting position symmetry should evaluate to 0
        assertEquals(0, score)
    }

    @Test
    fun testMaterialAdvantagePositionalScoring() {
        val engine = RetroMinimaxEngine()
        // White has an extra Queen on d5 while keeping original Queen on d1
        val fenWithWhiteQueen = "rnbqkbnr/pppppppp/8/3Q4/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        val board = engine.parseFenToBoard(fenWithWhiteQueen)
        val score = engine.evaluateBoardScore(board)

        assertTrue("White should have significant positive score with Queen advantage", score >= 800)

    }

    @Test
    fun testMinimaxEvaluatePosition() = kotlinx.coroutines.runBlocking {
        val engine = RetroMinimaxEngine()
        engine.initializeEngine()

        val config = EngineConfig(EngineType.DEEP_BLUE_CLASSIC, powerPercentage = 10)
        engine.setStrength(config)

        val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        val result = engine.evaluatePosition(startFen)

        assertNotNull(result)
        assertNotNull(result.bestMove)
        assertTrue(result.bestMove.length >= 4)
        assertEquals(2, result.depth) // 1 + (0.1 * 12) = 2 ply
        engine.release()
    }
}


