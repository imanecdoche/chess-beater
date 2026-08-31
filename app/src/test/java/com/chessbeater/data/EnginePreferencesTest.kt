package com.chessbeater.data

import com.chessbeater.engine.models.EngineType
import org.junit.Assert.*
import org.junit.Test

class EnginePreferencesTest {

    @Test
    fun testAppUserPreferencesDefaultValues() {
        val prefs = AppUserPreferences()
        assertEquals(EngineType.STOCKFISH, prefs.engineType)
        assertEquals(100, prefs.powerPercentage)
        assertTrue(prefs.showCanvasArrow)
        assertTrue(prefs.showFloatingHud)
        assertFalse(prefs.isStealthToastMode)
        assertTrue(prefs.isHapticAlertEnabled)

        val config = prefs.toEngineConfig()
        assertEquals(EngineType.STOCKFISH, config.engineType)
        assertEquals(100, config.powerPercentage)
    }

    @Test
    fun testCustomAppUserPreferences() {
        val prefs = AppUserPreferences(
            engineType = EngineType.LC0_ALPHAZERO,
            powerPercentage = 65,
            showCanvasArrow = false,
            showFloatingHud = true,
            isStealthToastMode = true,
            isHapticAlertEnabled = false
        )

        assertEquals(EngineType.LC0_ALPHAZERO, prefs.engineType)
        assertEquals(65, prefs.powerPercentage)
        assertFalse(prefs.showCanvasArrow)
        assertTrue(prefs.showFloatingHud)
        assertTrue(prefs.isStealthToastMode)
        assertFalse(prefs.isHapticAlertEnabled)

        val config = prefs.toEngineConfig()
        assertEquals(EngineType.LC0_ALPHAZERO, config.engineType)
        assertEquals(65, config.powerPercentage)
    }

    @Test
    fun testAppScannerPackageMapping() {
        val chessComTarget = InstalledAppScanner.mapPackageToTarget("com.chess.android")
        assertEquals(com.chessbeater.vision.models.ChessAppTarget.CHESS_COM, chessComTarget)

        val lichessTarget = InstalledAppScanner.mapPackageToTarget("org.lichess.mobileapp")
        assertEquals(com.chessbeater.vision.models.ChessAppTarget.LICHESS, lichessTarget)

        val otherTarget = InstalledAppScanner.mapPackageToTarget("com.other.chessgame")
        assertEquals(com.chessbeater.vision.models.ChessAppTarget.CHESS_COM, otherTarget)
    }
}

