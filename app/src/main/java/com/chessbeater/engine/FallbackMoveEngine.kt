package com.chessbeater.engine

import com.chessbeater.vision.models.PlayerColor
import com.chessbeater.vision.models.Side
import kotlin.math.max
import kotlin.math.min

/**
 * Sprint 52: Embedded Alpha-Beta Fallback Engine.
 * 3-ply Minimax Search with Material & Piece-Square Evaluation.
 * Guarantees instantaneous (< 15ms) legal moves if native Stockfish encounters delay or OS restrictions.
 */
object FallbackMoveEngine {

    private val PAWN_TABLE = intArrayOf(
         0,  0,  0,  0,  0,  0,  0,  0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
         5,  5, 10, 25, 25, 10,  5,  5,
         0,  0,  0, 20, 20,  0,  0,  0,
         5, -5,-10,  0,  0,-10, -5,  5,
         5, 10, 10,-20,-20, 10, 10,  5,
         0,  0,  0,  0,  0,  0,  0,  0
    )

    private val KNIGHT_TABLE = intArrayOf(
        -50,-40,-30,-30,-30,-30,-40,-50,
        -40,-20,  0,  0,  0,  0,-20,-40,
        -30,  0, 10, 15, 15, 10,  0,-30,
        -30,  5, 15, 20, 20, 15,  5,-30,
        -30,  0, 15, 20, 20, 15,  0,-30,
        -30,  5, 10, 15, 15, 10,  5,-30,
        -40,-20,  0,  5,  5,  0,-20,-40,
        -50,-40,-30,-30,-30,-30,-40,-50
    )

    private val BISHOP_TABLE = intArrayOf(
        -20,-10,-10,-10,-10,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5, 10, 10,  5,  0,-10,
        -10,  5,  5, 10, 10,  5,  5,-10,
        -10,  0, 10, 10, 10, 10,  0,-10,
        -10, 10, 10, 10, 10, 10, 10,-10,
        -10,  5,  0,  0,  0,  0,  5,-10,
        -20,-10,-10,-10,-10,-10,-10,-20
    )

    private fun getPieceVal(c: Char): Int = when (c) {
        'P', 'p' -> 100
        'N', 'n' -> 320
        'B', 'b' -> 330
        'R', 'r' -> 500
        'Q', 'q' -> 900
        'K', 'k' -> 20000
        else -> 0
    }

    private fun evaluateBoard(board: CharArray): Int {
        var score = 0
        for (i in 0 until 64) {
            val p = board[i]
            if (p == '.' || p == ' ') continue
            val isWhite = p.isUpperCase()
            val baseVal = getPieceVal(p)
            val pstVal = when (p.uppercaseChar()) {
                'P' -> if (isWhite) PAWN_TABLE[i] else PAWN_TABLE[63 - i]
                'N' -> if (isWhite) KNIGHT_TABLE[i] else KNIGHT_TABLE[63 - i]
                'B' -> if (isWhite) BISHOP_TABLE[i] else BISHOP_TABLE[63 - i]
                else -> 0
            }
            val total = baseVal + pstVal
            if (isWhite) score += total else score -= total
        }
        return score
    }

    fun getBestMove(board: CharArray, turn: PlayerColor): String? {
        val activeSide = if (turn == PlayerColor.WHITE) Side.WHITE else Side.BLACK
        val legalMoves = ChessLogic.getAllLegalMoves(board, activeSide)
        if (legalMoves.isEmpty()) return null

        var bestMoveStr: String? = null
        var bestScore = if (turn == PlayerColor.WHITE) -1000000 else 1000000

        for (pair in legalMoves) {
            val from = pair.first
            val to = pair.second
            val tempBoard = board.copyOf()
            ChessLogic.applyMoveToBoardArray(from, to, tempBoard)

            val score = alphaBeta(
                board = tempBoard,
                depth = 2,
                alpha = -1000000,
                beta = 1000000,
                isMaximizing = (turn == PlayerColor.BLACK)
            )

            if (turn == PlayerColor.WHITE) {
                if (score > bestScore || bestMoveStr == null) {
                    bestScore = score
                    bestMoveStr = indexPairToUci(from, to, board[from])
                }
            } else {
                if (score < bestScore || bestMoveStr == null) {
                    bestScore = score
                    bestMoveStr = indexPairToUci(from, to, board[from])
                }
            }
        }

        return bestMoveStr ?: legalMoves.firstOrNull()?.let { indexPairToUci(it.first, it.second, board[it.first]) }
    }

    private fun alphaBeta(
        board: CharArray,
        depth: Int,
        alpha: Int,
        beta: Int,
        isMaximizing: Boolean
    ): Int {
        if (depth == 0) {
            return evaluateBoard(board)
        }

        val side = if (isMaximizing) Side.WHITE else Side.BLACK
        val moves = ChessLogic.getAllLegalMoves(board, side)
        if (moves.isEmpty()) {
            return if (ChessLogic.isKingInCheck(board, side)) {
                if (isMaximizing) -90000 + (3 - depth) else 90000 - (3 - depth)
            } else {
                0 // Stalemate
            }
        }

        var curAlpha = alpha
        var curBeta = beta

        if (isMaximizing) {
            var maxEval = -1000000
            for (pair in moves) {
                val from = pair.first
                val to = pair.second
                val temp = board.copyOf()
                ChessLogic.applyMoveToBoardArray(from, to, temp)
                val eval = alphaBeta(temp, depth - 1, curAlpha, curBeta, false)
                maxEval = max(maxEval, eval)
                curAlpha = max(curAlpha, eval)
                if (curBeta <= curAlpha) break
            }
            return maxEval
        } else {
            var minEval = 1000000
            for (pair in moves) {
                val from = pair.first
                val to = pair.second
                val temp = board.copyOf()
                ChessLogic.applyMoveToBoardArray(from, to, temp)
                val eval = alphaBeta(temp, depth - 1, curAlpha, curBeta, true)
                minEval = min(minEval, eval)
                curBeta = min(curBeta, eval)
                if (curBeta <= curAlpha) break
            }
            return minEval
        }
    }

    private fun indexPairToUci(from: Int, to: Int, piece: Char): String {
        val fc = ('a'.code + (from % 8)).toChar()
        val fr = ('0'.code + (8 - (from / 8))).toChar()
        val tc = ('a'.code + (to % 8)).toChar()
        val tr = ('0'.code + (8 - (to / 8))).toChar()
        val promo = if (piece.uppercaseChar() == 'P' && (to / 8 == 0 || to / 8 == 7)) "q" else ""
        return "$fc$fr$tc$tr$promo"
    }
}
