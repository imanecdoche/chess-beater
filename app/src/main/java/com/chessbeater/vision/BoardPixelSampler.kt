package com.chessbeater.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.chessbeater.engine.ChessLogic
import com.chessbeater.vision.models.PlayerColor
import com.chessbeater.vision.models.Side

/**
 * Sprint 63: High-Speed 4-Corner Pixel Sampler for Chess.com and Lichess Highlights.
 */
class BoardPixelSampler {

    companion object {
        private const val TAG = "BoardPixelSampler"

        // Sample points per square (4 corners at 15% and 85% to strictly avoid piece body occlusion)
        private val CORNER_OFFSETS = listOf(
            0.15f to 0.15f, // Top-Left
            0.85f to 0.15f, // Top-Right
            0.15f to 0.85f, // Bottom-Left
            0.85f to 0.85f  // Bottom-Right
        )

        private fun idx2notation(idx: Int): String {
            if (idx !in 0..63) return "??"
            val file = 'a' + (idx % 8)
            val rank = 8 - (idx / 8)
            return "$file$rank"
        }
    }

    /**
     * Deteksi langkah yang dilakukan lawan berdasarkan 2 petak yang disorot/highlight pada frame bitmap.
     */
    fun detectMoveFromFrame(
        boardBitmap: Bitmap,
        board: CharArray,
        currentTurn: PlayerColor,
        castlingRights: String = "KQkq",
        isFlipped: Boolean = false
    ): Pair<Int, Int>? {
        if (boardBitmap.width <= 0 || boardBitmap.height <= 0) return null

        val sqW = boardBitmap.width / 8f
        val sqH = boardBitmap.height / 8f
        val highlightedSquares = mutableListOf<Int>()

        for (row in 0..7) {
            for (col in 0..7) {
                var highlightVotes = 0

                for ((ox, oy) in CORNER_OFFSETS) {
                    val px = ((col + ox) * sqW).toInt().coerceIn(0, boardBitmap.width - 1)
                    val py = ((row + oy) * sqH).toInt().coerceIn(0, boardBitmap.height - 1)
                    val pixel = boardBitmap.getPixel(px, py)

                    if (isHighlightColor(pixel)) {
                        highlightVotes++
                    }
                }

                // Jika minimal 2 dari 4 sudut terdeteksi warna highlight
                if (highlightVotes >= 2) {
                    val sqIndex = if (isFlipped) (7 - row) * 8 + (7 - col) else row * 8 + col
                    highlightedSquares.add(sqIndex)
                }
            }
        }

        if (highlightedSquares.isEmpty()) return null

        Log.d(TAG, "Kandidat petak highlight terdeteksi (${highlightedSquares.size}): $highlightedSquares")

        val side = if (currentTurn == PlayerColor.WHITE) Side.WHITE else Side.BLACK
        val legalMoves = ChessLogic.getAllLegalMoves(board, side, castlingRights)
        if (legalMoves.isEmpty()) return null

        // 1. Tepat 2 petak highlight (Kasus paling umum: petak asal & petak tujuan)
        if (highlightedSquares.size == 2) {
            val s1 = highlightedSquares[0]
            val s2 = highlightedSquares[1]

            val move1 = legalMoves.find { it.first == s1 && it.second == s2 }
            if (move1 != null) {
                Log.d(TAG, "✅ Langkah terdeteksi: ${idx2notation(s1)} ➔ ${idx2notation(s2)}")
                return move1
            }
            val move2 = legalMoves.find { it.first == s2 && it.second == s1 }
            if (move2 != null) {
                Log.d(TAG, "✅ Langkah terdeteksi: ${idx2notation(s2)} ➔ ${idx2notation(s1)}")
                return move2
            }
        }

        // 2. Jika lebih dari 2 petak (misal rokade atau capture highlight), cari kombinasi legal
        for (i in highlightedSquares.indices) {
            for (j in highlightedSquares.indices) {
                if (i == j) continue
                val from = highlightedSquares[i]
                val to = highlightedSquares[j]
                val matched = legalMoves.find { it.first == from && it.second == to }
                if (matched != null) {
                    Log.d(TAG, "✅ Langkah multi-kandidat terdeteksi: ${idx2notation(from)} ➔ ${idx2notation(to)}")
                    return matched
                }
            }
        }

        return null
    }

    /**
     * Memeriksa apakah warna pixel cocok dengan profil highlight Chess.com atau Lichess.
     */
    private fun isHighlightColor(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        // 1. Profil HSV Kuning-Hijau Chess.com & Lichess
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = hsv[0]        // 30..105 (Kuning ke Hijau)
        val sat = hsv[1]        // > 0.18
        val value = hsv[2]      // > 0.35

        if (hue in 30f..105f && sat > 0.18f && value > 0.35f) {
            return true
        }

        // 2. Profil Kuning Chess.com Classic / Neon: (R tinggi, G tinggi, B lebih rendah)
        if (r > 180 && g > 180 && (r - b > 40 || g - b > 40)) {
            return true
        }

        // 3. Profil Hijau Chess.com / Lichess: (G > R dan G > B)
        if (g > 140 && g > r + 15 && g > b + 15) {
            return true
        }

        // 4. Profil Oranye/Coklat Kayu Wood theme highlight
        if (r > 170 && g in 100..200 && b < 100 && (r - b > 80)) {
            return true
        }

        return false
    }
}
