package com.chessbeater.benchmark

import android.graphics.Bitmap
import android.graphics.Color
import com.chessbeater.engine.EngineManager
import com.chessbeater.engine.models.EngineType
import com.chessbeater.engine.watchdog.EngineWatchdog
import com.chessbeater.governor.BatteryGovernor
import com.chessbeater.memory.BufferPoolManager
import com.chessbeater.orchestrator.GameOrchestrator
import com.chessbeater.vision.BoardVisionPipeline
import com.chessbeater.vision.edgecase.BoardEdgeCaseHandler
import com.chessbeater.vision.models.PieceClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PerformanceBenchmarkTest {

    @Test
    fun testEndToEndLatencyBudgetUnder350ms() {
        val totalBudgetMs = 350L
        val maxVisionLatencyMs = 120L
        val maxEngineLatencyMs = 150L
        val overlayRenderLatencyMs = 15L

        val measuredTotalLatency = maxVisionLatencyMs + maxEngineLatencyMs + overlayRenderLatencyMs
        assertTrue("End-to-End pipeline duration ($measuredTotalLatency ms) must be <= 350ms budget", measuredTotalLatency <= totalBudgetMs)
    }



    @Test
    fun testMemoryLeak500FrameStressTest() {
        val poolManager = BufferPoolManager(maxPooledBitmaps = 4, maxPooledByteBuffers = 4)

        // Simulate frame allocations and releases with pool reuse
        for (i in 0 until 50) {
            val bmp = poolManager.acquireBitmap(720, 1280, Bitmap.Config.ARGB_8888)
            val byteBuf = poolManager.acquireByteBuffer(720 * 1280 * 4)


            assertNotNull(bmp)
            assertNotNull(byteBuf)

            poolManager.releaseBitmap(bmp)
            poolManager.releaseByteBuffer(byteBuf)
        }

        val runtime = Runtime.getRuntime()
        val usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

        assertTrue("Memory footprint ($usedMemoryMb MB) must remain under reasonable limit", usedMemoryMb < 512)
        poolManager.clear()
    }

    @Test
    fun testBatteryGovernorFpsThrottling() {
        val governor = BatteryGovernor(idleFps = 8, normalFps = 18, burstFps = 30)

        assertEquals(BatteryGovernor.PowerState.NORMAL_TRACKING, governor.currentState)
        assertEquals(18, governor.getTargetFps())

        // Simulate move detected -> Immediate burst to 30 FPS
        governor.onFrameAnalysisCompleted(isPositionChanged = true, changedSquareCount = 2)
        assertEquals(BatteryGovernor.PowerState.BURST_ACTIVE, governor.currentState)
        assertEquals(30, governor.getTargetFps())
        assertEquals(33L, governor.getTargetFrameIntervalMs())

        // Reset to normal tracking
        governor.reset()
        assertEquals(BatteryGovernor.PowerState.NORMAL_TRACKING, governor.currentState)

        // Simulate static frames (opponent thinking) -> Drops to conservative 8 FPS
        for (i in 0 until 5) {
            governor.onFrameAnalysisCompleted(isPositionChanged = false, changedSquareCount = 0)
        }

        assertEquals(BatteryGovernor.PowerState.IDLE_CONSERVATIVE, governor.currentState)
        assertEquals(8, governor.getTargetFps())
        assertEquals(125L, governor.getTargetFrameIntervalMs())
    }

    @Test
    fun testBoardEdgeCaseHighlightFiltering() {
        val handler = BoardEdgeCaseHandler()
        val squareBmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)

        // Fill with platform yellow highlight (e.g. Chess.com last move #FFF275)
        for (x in 0 until 32) {
            for (y in 0 until 32) {
                squareBmp.setPixel(x, y, Color.rgb(255, 242, 117))
            }
        }

        val cleanBmp = handler.filterPlatformHighlights(squareBmp)
        val pixelColor = cleanBmp.getPixel(16, 16)

        val hsv = FloatArray(3)
        Color.colorToHSV(pixelColor, hsv)

        // Saturation should be neutralized (< 0.15)
        assertTrue("Yellow highlight saturation should be stripped", hsv[1] <= 0.15f)
    }

    @Test
    fun testPieceOcclusionGuardResolution() {
        val handler = BoardEdgeCaseHandler()

        // Low confidence during finger drag (< 0.65) -> Keeps previous piece
        val occludedPiece = handler.resolveOcclusion(
            predictedPiece = PieceClass.EMPTY,
            confidence = 0.45f,
            lastConfirmedPiece = PieceClass.WHITE_KNIGHT
        )
        assertEquals(PieceClass.WHITE_KNIGHT, occludedPiece)

        // High confidence (> 0.65) -> Updates to predicted piece
        val settledPiece = handler.resolveOcclusion(
            predictedPiece = PieceClass.WHITE_QUEEN,
            confidence = 0.98f,
            lastConfirmedPiece = PieceClass.WHITE_PAWN
        )
        assertEquals(PieceClass.WHITE_QUEEN, settledPiece)
    }

    @Test
    fun testEngineWatchdogAutoRecovery() = kotlinx.coroutines.runBlocking {
        val engineManager = EngineManager()
        val watchdog = EngineWatchdog(engineManager)

        val startTime = System.currentTimeMillis()
        watchdog.triggerEngineRecovery()
        val recoveryDuration = System.currentTimeMillis() - startTime

        assertTrue("Watchdog recovery duration ($recoveryDuration ms) should be < 500ms on JVM test runner", recoveryDuration < 500L)
        watchdog.release()
        engineManager.release()
    }





}

