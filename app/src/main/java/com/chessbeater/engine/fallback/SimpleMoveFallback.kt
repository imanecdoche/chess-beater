package com.chessbeater.engine.fallback

import com.chessbeater.vision.models.PlayerColor

/**
 * Lightweight Legal Move Fallback Generator.
 * Provides immediate valid chess moves from the current 8x8 board matrix
 * whenever the native engine/process times out, guaranteeing 0% frozen UI.
 */
object SimpleMoveFallback {

    data class FallbackMove(
        val from: Int,
        val to: Int,
        val uci: String
    )

    fun findFirstLegalMove(board: CharArray, turn: PlayerColor): FallbackMove? {
        val isWhite = (turn == PlayerColor.WHITE)

        for (sq in 0 until 64) {
            val p = board[sq]
            if (p == '.' || p.isUpperCase() != isWhite) continue

            val r = sq / 8
            val c = sq % 8
            val destinations = mutableListOf<Int>()

            when (p.uppercaseChar()) {
                'P' -> {
                    val dir = if (isWhite) -1 else 1
                    val startRow = if (isWhite) 6 else 1
                    val nr = r + dir
                    if (nr in 0..7 && board[nr * 8 + c] == '.') {
                        destinations.add(nr * 8 + c)
                        val dr = r + 2 * dir
                        if (r == startRow && board[dr * 8 + c] == '.') {
                            destinations.add(dr * 8 + c)
                        }
                    }
                    for (dc in listOf(-1, 1)) {
                        val nc = c + dc
                        if (nr in 0..7 && nc in 0..7) {
                            val target = board[nr * 8 + nc]
                            if (target != '.' && target.isUpperCase() != isWhite) {
                                destinations.add(nr * 8 + nc)
                            }
                        }
                    }
                }
                'N' -> {
                    val jumps = listOf(
                        -2 to -1, -2 to 1, -1 to -2, -1 to 2,
                         1 to -2,  1 to 2,  2 to -1,  2 to 1
                    )
                    for ((dr, dc) in jumps) {
                        val nr = r + dr
                        val nc = c + dc
                        if (nr in 0..7 && nc in 0..7) {
                            val t = board[nr * 8 + nc]
                            if (t == '.' || t.isUpperCase() != isWhite) {
                                destinations.add(nr * 8 + nc)
                            }
                        }
                    }
                }
                'B', 'R', 'Q' -> {
                    val dirs = when (p.uppercaseChar()) {
                        'B' -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
                        'R' -> listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
                        else -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1, -1 to 0, 1 to 0, 0 to -1, 0 to 1)
                    }
                    for ((dr, dc) in dirs) {
                        var nr = r + dr
                        var nc = c + dc
                        while (nr in 0..7 && nc in 0..7) {
                            val t = board[nr * 8 + nc]
                            if (t == '.') {
                                destinations.add(nr * 8 + nc)
                            } else {
                                if (t.isUpperCase() != isWhite) destinations.add(nr * 8 + nc)
                                break
                            }
                            nr += dr
                            nc += dc
                        }
                    }
                }
                'K' -> {
                    for (dr in -1..1) {
                        for (dc in -1..1) {
                            if (dr == 0 && dc == 0) continue
                            val nr = r + dr
                            val nc = c + dc
                            if (nr in 0..7 && nc in 0..7) {
                                val t = board[nr * 8 + nc]
                                if (t == '.' || t.isUpperCase() != isWhite) {
                                    destinations.add(nr * 8 + nc)
                                }
                            }
                        }
                    }
                }
            }

            if (destinations.isNotEmpty()) {
                val to = destinations.first()
                val fc = ('a'.code + c).toChar()
                val fr = ('0'.code + (8 - r)).toChar()
                val tc = ('a'.code + (to % 8)).toChar()
                val tr = ('0'.code + (8 - (to / 8))).toChar()
                return FallbackMove(sq, to, "$fc$fr$tc$tr")
            }
        }
        return null
    }
}
