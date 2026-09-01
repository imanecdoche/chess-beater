package com.chessbeater.engine

import com.chessbeater.vision.models.Side

object ChessMoveValidator {
    fun isLegal(fen: String, fromUci: String, toUci: String, turnStr: String = "WHITE"): Boolean {
        val board = ChessFenUtils.fenToBoardArray(fen)
        val fromIdx = ChessFenUtils.uciSquareToIndex(fromUci)
        val toIdx = ChessFenUtils.uciSquareToIndex(toUci)
        if (fromIdx == -1 || toIdx == -1) return false

        val side = if (turnStr.equals("WHITE", ignoreCase = true) || fen.contains(" w ")) Side.WHITE else Side.BLACK
        val parts = fen.trim().split(Regex("\\s+"))
        val castling = if (parts.size >= 3) parts[2] else "KQkq"

        return ChessLogic.isMoveLegal(fromIdx, toIdx, board, side, castling)
    }
}
