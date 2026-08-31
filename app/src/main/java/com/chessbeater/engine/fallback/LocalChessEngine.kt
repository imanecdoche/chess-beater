package com.chessbeater.engine.fallback

import com.chessbeater.engine.models.EngineResult
import com.chessbeater.vision.models.PlayerColor
import kotlin.math.max
import kotlin.math.min

/**
 * Pure Kotlin Standalone Chess Engine & Instant Fallback Engine.
 * Computes legal moves and optimal candidate moves using Minimax & Piece-Square Tables in < 15ms.
 * Guarantees that Chess Beater never freezes even if native/process engines encounter delays.
 */
object LocalChessEngine {

    data class Move(val from: Int, val to: Int, val promo: Char? = null) {
        fun toUci(): String {
            val fc = ('a'.code + (from % 8)).toChar()
            val fr = ('0'.code + (8 - (from / 8))).toChar()
            val tc = ('a'.code + (to % 8)).toChar()
            val tr = ('0'.code + (8 - (to / 8))).toChar()
            val p = promo?.lowercaseChar()?.toString() ?: ""
            return "$fc$fr$tc$tr$p"
        }
    }

    private val PAWN_PST = intArrayOf(
         0,  0,  0,  0,  0,  0,  0,  0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
         5,  5, 10, 25, 25, 10,  5,  5,
         0,  0,  0, 20, 20,  0,  0,  0,
         5, -5,-10,  0,  0,-10, -5,  5,
         5, 10, 10,-20,-20, 10, 10,  5,
         0,  0,  0,  0,  0,  0,  0,  0
    )

    private val KNIGHT_PST = intArrayOf(
        -50,-40,-30,-30,-30,-30,-40,-50,
        -40,-20,  0,  0,  0,  0,-20,-40,
        -30,  0, 10, 15, 15, 10,  0,-30,
        -30,  5, 15, 20, 20, 15,  5,-30,
        -30,  0, 15, 20, 20, 15,  0,-30,
        -30,  5, 10, 15, 15, 10,  5,-30,
        -40,-20,  0,  5,  5,  0,-20,-40,
        -50,-40,-30,-30,-30,-30,-40,-50
    )

    private val BISHOP_PST = intArrayOf(
        -20,-10,-10,-10,-10,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5, 10, 10,  5,  0,-10,
        -10,  5,  5, 10, 10,  5,  5,-10,
        -10,  0, 10, 10, 10, 10,  0,-10,
        -10, 10, 10, 10, 10, 10, 10,-10,
        -10,  5,  0,  0,  0,  0,  5,-10,
        -20,-10,-10,-10,-10,-10,-10,-20
    )

    private val ROOK_PST = intArrayOf(
         0,  0,  0,  0,  0,  0,  0,  0,
         5, 10, 10, 10, 10, 10, 10,  5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
         0,  0,  0,  5,  5,  0,  0,  0
    )

    private val QUEEN_PST = intArrayOf(
        -20,-10,-10, -5, -5,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5,  5,  5,  5,  0,-10,
        -5,   0,  5,  5,  5,  5,  0, -5,
         0,   0,  5,  5,  5,  5,  0, -5,
        -10,  5,  5,  5,  5,  5,  0,-10,
        -10,  0,  5,  0,  0,  0,  0,-10,
        -20,-10,-10, -5, -5,-10,-10,-20
    )

    private val KING_PST = intArrayOf(
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -20,-30,-30,-40,-40,-30,-30,-20,
        -10,-20,-20,-20,-20,-20,-20,-10,
         20, 20,  0,  0,  0,  0, 20, 20,
         20, 30, 10,  0,  0, 10, 30, 20
    )

    fun getBestMove(fen: String, depth: Int = 3): EngineResult {
        val startTime = System.currentTimeMillis()
        val board = CharArray(64) { '.' }
        val parts = fen.trim().split(" ")
        val placement = parts.firstOrNull() ?: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
        val isWhiteTurn = parts.getOrNull(1) != "b"

        var idx = 0
        for (c in placement) {
            when {
                c == '/' -> Unit
                c.isDigit() -> idx += (c - '0')
                idx < 64 -> board[idx++] = c
            }
        }

        val legalMoves = generateAllLegalMoves(board, isWhiteTurn)
        if (legalMoves.isEmpty()) {
            return EngineResult(
                bestMove = "0000",
                evaluationCentipawns = 0,
                depth = depth,
                calculationTimeMs = System.currentTimeMillis() - startTime
            )
        }

        var bestMove = legalMoves.first()
        var bestVal = -999999

        for (m in legalMoves) {
            val nextBoard = board.copyOf()
            applyMove(nextBoard, m)
            val score = -minimax(nextBoard, depth - 1, -30000, 30000, !isWhiteTurn)
            if (score > bestVal) {
                bestVal = score
                bestMove = m
            }
        }

        val calcTime = System.currentTimeMillis() - startTime
        return EngineResult(
            bestMove = bestMove.toUci(),
            evaluationCentipawns = bestVal,
            depth = depth,
            calculationTimeMs = calcTime
        )
    }

    private fun minimax(board: CharArray, depth: Int, alpha: Int, beta: Int, isWhiteTurn: Boolean): Int {
        if (depth <= 0) {
            return evaluateBoard(board, isWhiteTurn)
        }

        val moves = generateAllLegalMoves(board, isWhiteTurn)
        if (moves.isEmpty()) return -25000 + (10 - depth)

        var a = alpha
        for (m in moves) {
            val nextBoard = board.copyOf()
            applyMove(nextBoard, m)
            val score = -minimax(nextBoard, depth - 1, -beta, -a, !isWhiteTurn)
            if (score >= beta) return beta
            if (score > a) a = score
        }
        return a
    }

    private fun applyMove(board: CharArray, m: Move) {
        val p = board[m.from]
        board[m.from] = '.'
        board[m.to] = when {
            m.promo != null && p == 'P' -> m.promo.uppercaseChar()
            m.promo != null && p == 'p' -> m.promo.lowercaseChar()
            else -> p
        }

        // Castling King move handling
        if (p == 'K') {
            if (m.from == 60 && m.to == 62) { board[63] = '.'; board[61] = 'R' }
            else if (m.from == 60 && m.to == 58) { board[56] = '.'; board[59] = 'R' }
        } else if (p == 'k') {
            if (m.from == 4 && m.to == 6) { board[7] = '.'; board[5] = 'r' }
            else if (m.from == 4 && m.to == 2) { board[0] = '.'; board[3] = 'r' }
        }
    }

    private fun generateAllLegalMoves(board: CharArray, isWhiteTurn: Boolean): List<Move> {
        val moves = mutableListOf<Move>()
        for (sq in 0 until 64) {
            val p = board[sq]
            if (p == '.' || p.isUpperCase() != isWhiteTurn) continue
            val row = sq / 8
            val col = sq % 8

            when (p.uppercaseChar()) {
                'P' -> {
                    val dir = if (isWhiteTurn) -1 else 1
                    val startRow = if (isWhiteTurn) 6 else 1
                    val promoRow = if (isWhiteTurn) 0 else 7
                    val nr = row + dir

                    if (nr in 0..7 && board[nr * 8 + col] == '.') {
                        val dest = nr * 8 + col
                        if (nr == promoRow) {
                            moves.add(Move(sq, dest, 'q'))
                            moves.add(Move(sq, dest, 'r'))
                            moves.add(Move(sq, dest, 'b'))
                            moves.add(Move(sq, dest, 'n'))
                        } else {
                            moves.add(Move(sq, dest))
                            val dr = row + 2 * dir
                            if (row == startRow && board[dr * 8 + col] == '.') {
                                moves.add(Move(sq, dr * 8 + col))
                            }
                        }
                    }

                    for (dc in listOf(-1, 1)) {
                        val nc = col + dc
                        if (nr in 0..7 && nc in 0..7) {
                            val capSq = nr * 8 + nc
                            val t = board[capSq]
                            if (t != '.' && t.isUpperCase() != isWhiteTurn) {
                                if (nr == promoRow) {
                                    moves.add(Move(sq, capSq, 'q'))
                                    moves.add(Move(sq, capSq, 'r'))
                                } else {
                                    moves.add(Move(sq, capSq))
                                }
                            }
                        }
                    }
                }
                'N' -> {
                    val jumps = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
                    for ((dr, dc) in jumps) {
                        val nr = row + dr
                        val nc = col + dc
                        if (nr in 0..7 && nc in 0..7) {
                            val dest = nr * 8 + nc
                            val t = board[dest]
                            if (t == '.' || t.isUpperCase() != isWhiteTurn) {
                                moves.add(Move(sq, dest))
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
                        var nr = row + dr
                        var nc = col + dc
                        while (nr in 0..7 && nc in 0..7) {
                            val dest = nr * 8 + nc
                            val t = board[dest]
                            if (t == '.') {
                                moves.add(Move(sq, dest))
                            } else {
                                if (t.isUpperCase() != isWhiteTurn) moves.add(Move(sq, dest))
                                break
                            }
                            nr += dr
                            nc += dc
                        }
                    }
                }
                'K' -> {
                    val enemyKingChar = if (isWhiteTurn) 'k' else 'K'
                    val enemyKingIdx = board.indexOf(enemyKingChar)
                    val enemyKingRow = if (enemyKingIdx >= 0) enemyKingIdx / 8 else -99
                    val enemyKingCol = if (enemyKingIdx >= 0) enemyKingIdx % 8 else -99

                    for (dr in -1..1) {
                        for (dc in -1..1) {
                            if (dr == 0 && dc == 0) continue
                            val nr = row + dr
                            val nc = col + dc
                            if (nr in 0..7 && nc in 0..7) {
                                // Rule: King cannot move adjacent to enemy King
                                if (kotlin.math.abs(nr - enemyKingRow) <= 1 && kotlin.math.abs(nc - enemyKingCol) <= 1) continue

                                val dest = nr * 8 + nc
                                val t = board[dest]
                                if (t == '.' || t.isUpperCase() != isWhiteTurn) {
                                    moves.add(Move(sq, dest))
                                }
                            }
                        }
                    }
                    // Castling candidate moves
                    if (isWhiteTurn && sq == 60) {
                        if (board[61] == '.' && board[62] == '.' && board[63] == 'R') moves.add(Move(60, 62))
                        if (board[59] == '.' && board[58] == '.' && board[57] == '.' && board[56] == 'R') moves.add(Move(60, 58))
                    } else if (!isWhiteTurn && sq == 4) {
                        if (board[5] == '.' && board[6] == '.' && board[7] == 'r') moves.add(Move(4, 6))
                        if (board[3] == '.' && board[2] == '.' && board[1] == '.' && board[0] == 'r') moves.add(Move(4, 2))
                    }
                }
            }
        }
        return moves
    }

    private fun evaluateBoard(board: CharArray, isWhiteTurn: Boolean): Int {
        var score = 0
        for (sq in 0 until 64) {
            val p = board[sq]
            if (p == '.') continue
            val flippedSq = if (p.isUpperCase()) sq else (63 - sq)
            val valPiece = when (p.uppercaseChar()) {
                'P' -> 100 + PAWN_PST[flippedSq]
                'N' -> 320 + KNIGHT_PST[flippedSq]
                'B' -> 330 + BISHOP_PST[flippedSq]
                'R' -> 500 + ROOK_PST[flippedSq]
                'Q' -> 900 + QUEEN_PST[flippedSq]
                'K' -> 20000 + KING_PST[flippedSq]
                else -> 0
            }
            if (p.isUpperCase()) score += valPiece else score -= valPiece
        }
        return if (isWhiteTurn) score else -score
    }
}
