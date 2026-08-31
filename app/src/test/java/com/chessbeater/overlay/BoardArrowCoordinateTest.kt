package com.chessbeater.overlay

import android.graphics.PointF
import android.graphics.Rect
import com.chessbeater.vision.models.PlayerColor
import org.junit.Assert.*
import org.junit.Test

class BoardArrowCoordinateTest {

    private val boardRect = Rect(100, 200, 900, 1000) // 800x800 board, 100px per square

    @Test
    fun testWhiteOrientationCoordinates() {
        // e4 on White orientation:
        // file 'e' is index 4 (columns: a=0, b=1, c=2, d=3, e=4, f=5, g=6, h=7) -> left + 4.5 * 100 = 100 + 450 = 550
        // rank '4' is index 3 (rows from top: 8=0, 7=1, 6=2, 5=3, 4=4, 3=5, 2=6, 1=7) -> top + 4.5 * 100 = 200 + 450 = 650
        val pt = BoardArrowOverlayView.squareToCoordinates("e4", boardRect, PlayerColor.WHITE)
        assertNotNull(pt)
        assertEquals(550.0f, pt!!.x, 0.01f)
        assertEquals(650.0f, pt.y, 0.01f)
    }

    @Test
    fun testWhiteOrientationA1AndH8() {
        // a1 on White orientation: col 0, row 7 -> x = 100 + 50 = 150, y = 200 + 750 = 950
        val a1 = BoardArrowOverlayView.squareToCoordinates("a1", boardRect, PlayerColor.WHITE)
        assertNotNull(a1)
        assertEquals(150.0f, a1!!.x, 0.01f)
        assertEquals(950.0f, a1.y, 0.01f)

        // h8 on White orientation: col 7, row 0 -> x = 100 + 750 = 850, y = 200 + 50 = 250
        val h8 = BoardArrowOverlayView.squareToCoordinates("h8", boardRect, PlayerColor.WHITE)
        assertNotNull(h8)
        assertEquals(850.0f, h8!!.x, 0.01f)
        assertEquals(250.0f, h8.y, 0.01f)
    }

    @Test
    fun testBlackOrientationCoordinates() {
        // e4 on Black orientation:
        // file 'e' is index 4 -> col = 7 - 4 = 3 -> left + 3.5 * 100 = 100 + 350 = 450
        // rank '4' is index 3 -> row = 3 (from top 1=0, 2=1, 3=2, 4=3) -> top + 3.5 * 100 = 200 + 350 = 550
        val pt = BoardArrowOverlayView.squareToCoordinates("e4", boardRect, PlayerColor.BLACK)
        assertNotNull(pt)
        assertEquals(450.0f, pt!!.x, 0.01f)
        assertEquals(550.0f, pt.y, 0.01f)
    }

    @Test
    fun testInvalidSquareInputs() {
        assertNull(BoardArrowOverlayView.squareToCoordinates("z9", boardRect, PlayerColor.WHITE))
        assertNull(BoardArrowOverlayView.squareToCoordinates("", boardRect, PlayerColor.WHITE))
        assertNull(BoardArrowOverlayView.squareToCoordinates("e", boardRect, PlayerColor.WHITE))
        assertNull(BoardArrowOverlayView.squareToCoordinates("i4", boardRect, PlayerColor.WHITE))
        assertNull(BoardArrowOverlayView.squareToCoordinates("e0", boardRect, PlayerColor.WHITE))
    }

    @Test
    fun testArrowColorThemeMapping() {
        // Best Move >= +1.00 (+100 cp) -> Green #00E676
        assertEquals(ArrowColorTheme.COLOR_BEST_MOVE, ArrowColorTheme.getColorForEvaluation(120, null))
        assertEquals(ArrowColorTheme.COLOR_BEST_MOVE, ArrowColorTheme.getColorForEvaluation(null, 3)) // Mate in 3

        // Solid / Standard Equal (-50 to +99 cp) -> Blue #2979FF
        assertEquals(ArrowColorTheme.COLOR_SOLID_MOVE, ArrowColorTheme.getColorForEvaluation(0, null))
        assertEquals(ArrowColorTheme.COLOR_SOLID_MOVE, ArrowColorTheme.getColorForEvaluation(50, null))
        assertEquals(ArrowColorTheme.COLOR_SOLID_MOVE, ArrowColorTheme.getColorForEvaluation(-30, null))

        // Tactical Alternative (-200 to -51 cp or explicit flag) -> Yellow #FFD600
        assertEquals(ArrowColorTheme.COLOR_TACTICAL_ALT, ArrowColorTheme.getColorForEvaluation(-100, null))
        assertEquals(ArrowColorTheme.COLOR_TACTICAL_ALT, ArrowColorTheme.getColorForEvaluation(20, null, isAlternative = true))

        // Blunder Alert (< -200 cp or negative mate) -> Red #FF1744
        assertEquals(ArrowColorTheme.COLOR_BLUNDER_ALERT, ArrowColorTheme.getColorForEvaluation(-350, null))
        assertEquals(ArrowColorTheme.COLOR_BLUNDER_ALERT, ArrowColorTheme.getColorForEvaluation(null, -2))
    }
}
