package com.chessbeater.overlay

import android.graphics.Color

/**
 * Color scheme mapping for real-time board recommendation arrows
 * Strictly aligned with PRD Section 5.3
 */
object ArrowColorTheme {
    // Hijau Terang: Best Move (+1.00 or higher advantage / winning mate)
    const val COLOR_BEST_MOVE = 0xFF00E676.toInt()

    // Biru Elektrik: Solid / Standard Equal Move (-0.50 to +0.99)
    const val COLOR_SOLID_MOVE = 0xFF2979FF.toInt()

    // Kuning: Alternative Taktis / Sharp evaluation
    const val COLOR_TACTICAL_ALT = 0xFFFFD600.toInt()

    // Crimson / Merah: Critical danger / alert
    const val COLOR_BLUNDER_ALERT = 0xFFFF1744.toInt()

    /**
     * Dynamically determines arrow color based on centipawn score and mate state
     */
    fun getColorForEvaluation(evalCentipawns: Int?, mateInMoves: Int?, isAlternative: Boolean = false): Int {
        if (isAlternative) return COLOR_TACTICAL_ALT

        if (mateInMoves != null) {
            return if (mateInMoves > 0) COLOR_BEST_MOVE else COLOR_BLUNDER_ALERT
        }

        val cp = evalCentipawns ?: 0
        return when {
            cp >= 100 -> COLOR_BEST_MOVE
            cp >= -50 -> COLOR_SOLID_MOVE
            cp >= -200 -> COLOR_TACTICAL_ALT
            else -> COLOR_BLUNDER_ALERT
        }
    }
}
