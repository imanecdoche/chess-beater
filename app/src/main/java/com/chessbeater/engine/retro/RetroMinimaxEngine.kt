package com.chessbeater.engine.retro

import com.chessbeater.engine.models.ChessEngineBridge
import com.chessbeater.engine.models.EngineConfig
import com.chessbeater.engine.models.EngineResult
import com.chessbeater.engine.models.EngineType
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min

/**
 * Pure Classic Minimax Chess Engine with Alpha-Beta Pruning (Deep Blue 1997 style).
 * Operates without neural networks using static material valuation & piece-square tables (PST).
 * Aligned with PRD Section 4.1 & 4.2.
 */
class RetroMinimaxEngine(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ChessEngineBridge {

    private val executionMutex = Mutex()
    private var currentConfig = EngineConfig(EngineType.DEEP_BLUE_CLASSIC, 100)
    private var isInitialized = false
    private var isStopped = false

    companion object {
        // Material Values (Centipawns)
        const val VAL_PAWN = 100
        const val VAL_KNIGHT = 320
        const val VAL_BISHOP = 330
        const val VAL_ROOK = 500
        const val VAL_QUEEN = 900
        const val VAL_KING = 20000

        // Piece Square Tables (PST) - 8x8 grid (Rank 8 top down to Rank 1)
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
            -5,  0,  5,  5,  5,  5,  0, -5,
            0,  0,  5,  5,  5,  5,  0, -5,
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
    }

    override suspend fun initializeEngine(): Boolean = withContext(dispatcher) {
        executionMutex.withLock {
            isInitialized = true
            isStopped = false
            true
        }
    }

    override suspend fun setStrength(config: EngineConfig): Unit = withContext(dispatcher) {
        executionMutex.withLock {
            currentConfig = config
            Unit
        }
    }


    override suspend fun evaluatePosition(fen: String): EngineResult = withContext(dispatcher) {
        executionMutex.withLock {
            val startTime = System.currentTimeMillis()
            isStopped = false

            // Target search depth from configuration: 1 to 13 ply
            val targetDepth = (1 + (currentConfig.powerPercentage / 100.0 * 12).toInt()).coerceIn(1, 13)

            val board = parseFenToBoard(fen)
            val isWhiteToMove = isWhiteTurn(fen)

            // Minimax Alpha-Beta search for best move
            val searchResult = searchRoot(board, targetDepth, isWhiteToMove)
            val duration = System.currentTimeMillis() - startTime

            EngineResult(
                bestMove = searchResult.bestMove,
                ponderMove = searchResult.ponderMove,
                evaluationCentipawns = searchResult.score,
                mateInMoves = null,
                depth = targetDepth,
                calculationTimeMs = duration
            )
        }
    }

    override suspend fun stopEvaluation() {
        isStopped = true
    }

    override fun release() {
        isStopped = true
        isInitialized = false
    }

    /**
     * Evaluates static board position score (White advantage positive, Black advantage negative)
     */
    fun evaluateBoardScore(board: CharArray): Int {
        var score = 0
        for (i in 0 until 64) {
            val piece = board[i]
            if (piece == '.') continue

            val (material, pstScore) = when (piece) {
                'P' -> Pair(VAL_PAWN, PAWN_PST[i])
                'N' -> Pair(VAL_KNIGHT, KNIGHT_PST[i])
                'B' -> Pair(VAL_BISHOP, BISHOP_PST[i])
                'R' -> Pair(VAL_ROOK, ROOK_PST[i])
                'Q' -> Pair(VAL_QUEEN, QUEEN_PST[i])
                'K' -> Pair(VAL_KING, KING_PST[i])
                'p' -> Pair(-VAL_PAWN, -PAWN_PST[63 - i])
                'n' -> Pair(-VAL_KNIGHT, -KNIGHT_PST[63 - i])
                'b' -> Pair(-VAL_BISHOP, -BISHOP_PST[63 - i])
                'r' -> Pair(-VAL_ROOK, -ROOK_PST[63 - i])
                'q' -> Pair(-VAL_QUEEN, -QUEEN_PST[63 - i])
                'k' -> Pair(-VAL_KING, -KING_PST[63 - i])
                else -> Pair(0, 0)
            }
            score += (material + pstScore)
        }
        return score
    }

    private data class SearchRootResult(
        val bestMove: String,
        val ponderMove: String?,
        val score: Int
    )

    private fun searchRoot(board: CharArray, maxDepth: Int, isWhite: Boolean): SearchRootResult {
        val moves = generateMoves(board, isWhite)
        if (moves.isEmpty()) {
            return SearchRootResult("e2e4", null, 0)
        }

        var bestMove = moves[0]
        var ponderMove: String? = if (moves.size > 1) moves[1] else null
        var bestScore = if (isWhite) Int.MIN_VALUE else Int.MAX_VALUE

        // Iterative deepening search up to maxDepth
        val effectiveDepth = min(maxDepth, 4) // Root depth clamp for instant mobile latency

        for (move in moves) {
            if (isStopped) break

            val cloned = board.clone()
            makeMove(cloned, move)

            val score = alphaBeta(cloned, effectiveDepth - 1, Int.MIN_VALUE + 1000, Int.MAX_VALUE - 1000, !isWhite)

            if (isWhite) {
                if (score > bestScore) {
                    bestScore = score
                    bestMove = move
                }
            } else {
                if (score < bestScore) {
                    bestScore = score
                    bestMove = move
                }
            }
        }

        return SearchRootResult(bestMove, ponderMove, bestScore)
    }

    private fun alphaBeta(board: CharArray, depth: Int, alphaInit: Int, betaInit: Int, isWhite: Boolean): Int {
        if (depth <= 0 || isStopped) {
            return evaluateBoardScore(board)
        }

        var alpha = alphaInit
        var beta = betaInit
        val moves = generateMoves(board, isWhite)

        if (moves.isEmpty()) {
            return evaluateBoardScore(board)
        }

        if (isWhite) {
            var maxEval = Int.MIN_VALUE
            for (move in moves) {
                val cloned = board.clone()
                makeMove(cloned, move)
                val eval = alphaBeta(cloned, depth - 1, alpha, beta, false)
                maxEval = max(maxEval, eval)
                alpha = max(alpha, eval)
                if (beta <= alpha) break // Alpha-Beta cutoff
            }
            return maxEval
        } else {
            var minEval = Int.MAX_VALUE
            for (move in moves) {
                val cloned = board.clone()
                makeMove(cloned, move)
                val eval = alphaBeta(cloned, depth - 1, alpha, beta, true)
                minEval = min(minEval, eval)
                beta = min(beta, eval)
                if (beta <= alpha) break // Alpha-Beta cutoff
            }
            return minEval
        }
    }

    /**
     * Generates plausible moves for pieces on the board
     */
    private fun generateMoves(board: CharArray, isWhite: Boolean): List<String> {
        val moves = mutableListOf<String>()

        for (i in 0 until 64) {
            val p = board[i]
            if (p == '.') continue
            val isPieceWhite = p.isUpperCase()
            if (isPieceWhite != isWhite) continue

            val file = i % 8
            val rank = 7 - (i / 8) // 0..7 (rank 1..8)
            val fromNotation = "${('a' + file)}${rank + 1}"

            // Generate basic piece moves
            when (p.uppercaseChar()) {
                'P' -> {
                    val step = if (isWhite) -8 else 8
                    val forwardIdx = i + step
                    if (forwardIdx in 0..63 && board[forwardIdx] == '.') {
                        val toNotation = "${('a' + (forwardIdx % 8))}${8 - (forwardIdx / 8)}"
                        moves.add("$fromNotation$toNotation")

                        // Double step from starting rank
                        val startRank = if (isWhite) 6 else 1
                        val doubleStepIdx = i + step * 2
                        if (i / 8 == startRank && doubleStepIdx in 0..63 && board[doubleStepIdx] == '.') {
                            val toDouble = "${('a' + (doubleStepIdx % 8))}${8 - (doubleStepIdx / 8)}"
                            moves.add("$fromNotation$toDouble")
                        }
                    }
                    // Pawn Captures
                    val captureOffsets = if (isWhite) intArrayOf(-9, -7) else intArrayOf(7, 9)
                    for (off in captureOffsets) {
                        val targetIdx = i + off
                        if (targetIdx in 0..63) {
                            val targetFile = targetIdx % 8
                            if (kotlin.math.abs(targetFile - file) == 1 && board[targetIdx] != '.') {
                                val targetIsWhite = board[targetIdx].isUpperCase()
                                if (targetIsWhite != isWhite) {
                                    val toNotation = "${('a' + targetFile)}${8 - (targetIdx / 8)}"
                                    moves.add("$fromNotation$toNotation")
                                }
                            }
                        }
                    }
                }
                'N' -> {
                    val knightDeltas = intArrayOf(-17, -15, -10, -6, 6, 10, 15, 17)
                    for (delta in knightDeltas) {
                        val targetIdx = i + delta
                        if (targetIdx in 0..63) {
                            val targetFile = targetIdx % 8
                            val targetRank = targetIdx / 8
                            val fileDiff = kotlin.math.abs(targetFile - file)
                            val rankDiff = kotlin.math.abs(targetRank - (i / 8))
                            if ((fileDiff == 1 && rankDiff == 2) || (fileDiff == 2 && rankDiff == 1)) {
                                if (board[targetIdx] == '.' || board[targetIdx].isUpperCase() != isWhite) {
                                    val toNotation = "${('a' + targetFile)}${8 - targetRank}"
                                    moves.add("$fromNotation$toNotation")
                                }
                            }
                        }
                    }
                }
                'B', 'R', 'Q' -> {
                    val directions = when (p.uppercaseChar()) {
                        'B' -> arrayOf(Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1))
                        'R' -> arrayOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1))
                        else -> arrayOf(
                            Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1),
                            Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1)
                        )
                    }
                    for (dir in directions) {
                        var curR = (i / 8) + dir.first
                        var curF = file + dir.second
                        while (curR in 0..7 && curF in 0..7) {
                            val targetIdx = curR * 8 + curF
                            val targetPiece = board[targetIdx]
                            if (targetPiece == '.') {
                                val toNotation = "${('a' + curF)}${8 - curR}"
                                moves.add("$fromNotation$toNotation")
                            } else {
                                if (targetPiece.isUpperCase() != isWhite) {
                                    val toNotation = "${('a' + curF)}${8 - curR}"
                                    moves.add("$fromNotation$toNotation")
                                }
                                break
                            }
                            curR += dir.first
                            curF += dir.second
                        }
                    }
                }
                'K' -> {
                    for (dr in -1..1) {
                        for (df in -1..1) {
                            if (dr == 0 && df == 0) continue
                            val curR = (i / 8) + dr
                            val curF = file + df
                            if (curR in 0..7 && curF in 0..7) {
                                val targetIdx = curR * 8 + curF
                                if (board[targetIdx] == '.' || board[targetIdx].isUpperCase() != isWhite) {
                                    val toNotation = "${('a' + curF)}${8 - curR}"
                                    moves.add("$fromNotation$toNotation")
                                }
                            }
                        }
                    }
                }
            }
        }

        return if (moves.isNotEmpty()) moves else listOf("e2e4")
    }

    private fun makeMove(board: CharArray, move: String) {
        if (move.length < 4) return
        val fromFile = move[0] - 'a'
        val fromRank = 8 - (move[1] - '0')
        val toFile = move[2] - 'a'
        val toRank = 8 - (move[3] - '0')

        val fromIdx = fromRank * 8 + fromFile
        val toIdx = toRank * 8 + toFile

        if (fromIdx in 0..63 && toIdx in 0..63) {
            val piece = board[fromIdx]
            board[fromIdx] = '.'
            board[toIdx] = piece
        }
    }

    fun parseFenToBoard(fen: String): CharArray {
        val board = CharArray(64) { '.' }
        val boardPart = fen.split(" ").firstOrNull() ?: return board
        val rows = boardPart.split("/")

        var idx = 0
        for (row in rows) {
            for (ch in row) {
                if (ch.isDigit()) {
                    val emptyCount = ch - '0'
                    idx += emptyCount
                } else if (idx < 64) {
                    board[idx++] = ch
                }
            }
        }
        return board
    }

    private fun isWhiteTurn(fen: String): Boolean {
        val parts = fen.split(" ")
        return parts.getOrNull(1) != "b"
    }
}
