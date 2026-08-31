package com.chessbeater.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log

/**
 * Sprint 46: High-efficiency pixel sampling detector for last-move highlighted squares.
 */
class BoardHighlightDetector {

    companion object {
        private const val TAG = "BoardHighlightDetector"
    }

    /**
     * Deteksi 2 petak yang disorot/highlight oleh game catur (Chess.com kuning/hijau, Lichess, dll)
     */
    fun detectMovedSquares(
        boardBitmap: Bitmap,
        isFlipped: Boolean,
        legalMovesFromBoard: List<Pair<Int, Int>>
    ): Pair<Int, Int>? {
        if (boardBitmap.width <= 0 || boardBitmap.height <= 0) return null

        val sqW = boardBitmap.width / 8f
        val sqH = boardBitmap.height / 8f
        val highlightedSquares = mutableListOf<Int>()

        for (row in 0..7) {
            for (col in 0..7) {
                // Sample 5 points per square (center + 4 offsets) for robust noise/piece rejection
                val sampleOffsets = listOf(
                    0.5f to 0.5f,
                    0.25f to 0.25f,
                    0.75f to 0.25f,
                    0.25f to 0.75f,
                    0.75f to 0.75f
                )

                var highlightVotes = 0
                for ((ox, oy) in sampleOffsets) {
                    val px = ((col + ox) * sqW).toInt().coerceIn(0, boardBitmap.width - 1)
                    val py = ((row + oy) * sqH).toInt().coerceIn(0, boardBitmap.height - 1)
                    val pixelColor = boardBitmap.getPixel(px, py)

                    val hsv = FloatArray(3)
                    Color.colorToHSV(pixelColor, hsv)
                    val hue = hsv[0]        // Hue 35..95 = Kuning/Hijau khas Chess.com / Lichess highlight
                    val saturation = hsv[1] // Saturasi > 0.20
                    val value = hsv[2]      // Brightness > 0.35

                    if ((hue in 35f..95f && saturation > 0.20f && value > 0.35f) || isCustomHighlight(pixelColor)) {
                        highlightVotes++
                    }
                }

                if (highlightVotes >= 2) {
                    val sqIndex = if (isFlipped) (7 - row) * 8 + (7 - col) else row * 8 + col
                    highlightedSquares.add(sqIndex)
                }
            }
        }

        // Validasi: Jika ditemukan tepat 2 petak highlight (asal & tujuan)
        if (highlightedSquares.size == 2) {
            val sq1 = highlightedSquares[0]
            val sq2 = highlightedSquares[1]
            // Cocokkan dengan daftar langkah legal yang valid
            if (legalMovesFromBoard.any { it.first == sq1 && it.second == sq2 }) {
                Log.d(TAG, "Highlight move detected: $sq1 -> $sq2")
                return Pair(sq1, sq2)
            }
            if (legalMovesFromBoard.any { it.first == sq2 && it.second == sq1 }) {
                Log.d(TAG, "Highlight move detected: $sq2 -> $sq1")
                return Pair(sq2, sq1)
            }
        } else if (highlightedSquares.isNotEmpty()) {
            // Cari pasangan dari kandidat highlight yang cocok dengan daftar langkah legal
            for (i in highlightedSquares.indices) {
                for (j in i + 1 until highlightedSquares.size) {
                    val s1 = highlightedSquares[i]
                    val s2 = highlightedSquares[j]
                    if (legalMovesFromBoard.any { it.first == s1 && it.second == s2 }) {
                        Log.d(TAG, "Highlight move matched from multi-candidates: $s1 -> $s2")
                        return Pair(s1, s2)
                    }
                    if (legalMovesFromBoard.any { it.first == s2 && it.second == s1 }) {
                        Log.d(TAG, "Highlight move matched from multi-candidates: $s2 -> $s1")
                        return Pair(s2, s1)
                    }
                }
            }
        }

        return null
    }

    private fun isCustomHighlight(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val isYellowish = r > 150 && g > 150 && b < 140
        val isGreenishTint = g > 140 && g > r + 20 && g > b + 20
        return isYellowish || isGreenishTint
    }
}
