package com.chessbeater.engine

object ChessFenUtils {
    const val INITIAL_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    fun uciSquareToIndex(sq: String): Int {
        if (sq.length < 2) return -1
        val file = sq[0] - 'a'
        val rank = 8 - (sq[1] - '0')
        return if (file in 0..7 && rank in 0..7) rank * 8 + file else -1
    }

    fun indexToUciSquare(idx: Int): String {
        if (idx !in 0..63) return ""
        val file = ('a'.code + (idx % 8)).toChar()
        val rank = 8 - (idx / 8)
        return "$file$rank"
    }

    fun fenToBoardArray(fen: String): CharArray {
        val board = CharArray(64) { '.' }
        val fenBoard = fen.trim().split(Regex("\\s+")).firstOrNull() ?: return board
        var row = 0
        var col = 0
        for (ch in fenBoard) {
            if (ch == '/') {
                row++
                col = 0
                if (row > 7) break
            } else if (ch.isDigit()) {
                col += ch - '0'
            } else if (ch.isLetter()) {
                if (row in 0..7 && col in 0..7) {
                    board[row * 8 + col] = ch
                }
                col++
            }
        }
        return board
    }

    fun boardArrayToFen(board: CharArray, turn: String = "WHITE", castling: String = "KQkq"): String {
        val sb = StringBuilder()
        for (r in 0..7) {
            var emptyCount = 0
            for (c in 0..7) {
                val piece = board[r * 8 + c]
                if (piece == '.' || piece == ' ') {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount)
                        emptyCount = 0
                    }
                    sb.append(piece)
                }
            }
            if (emptyCount > 0) {
                sb.append(emptyCount)
            }
            if (r < 7) {
                sb.append('/')
            }
        }
        val turnChar = if (turn.equals("WHITE", ignoreCase = true) || turn.equals("w", ignoreCase = true)) "w" else "b"
        val activeCastling = if (castling.isBlank()) "-" else castling
        return "$sb $turnChar $activeCastling - 0 1"
    }

    fun updateFenAfterMove(currentFen: String, fromUci: String, toUci: String, promotion: String? = null): String {
        val board = fenToBoardArray(currentFen)
        val fromIdx = uciSquareToIndex(fromUci)
        val toIdx = uciSquareToIndex(toUci)
        if (fromIdx == -1 || toIdx == -1) return currentFen

        val parts = currentFen.trim().split(Regex("\\s+"))
        val isWhite = if (parts.size >= 2) parts[1] == "w" else true
        val castling = if (parts.size >= 3) parts[2] else "KQkq"

        ChessLogic.applyMoveToBoardArray(fromIdx, toIdx, board)

        if (!promotion.isNullOrBlank()) {
            val promoChar = promotion[0]
            board[toIdx] = if (isWhite) promoChar.uppercaseChar() else promoChar.lowercaseChar()
        }

        val nextTurn = if (isWhite) "BLACK" else "WHITE"
        return boardArrayToFen(board, nextTurn, castling)
    }
}
