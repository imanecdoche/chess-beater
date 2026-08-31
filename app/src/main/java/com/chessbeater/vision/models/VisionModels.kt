package com.chessbeater.vision.models

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Player/piece perspective colors
 */
enum class PlayerColor {
    WHITE,
    BLACK
}

typealias Side = PlayerColor

/**
 * Chess Piece Classifications
 */
enum class PieceClass(val symbol: String) {
    WHITE_KING("K"),
    WHITE_QUEEN("Q"),
    WHITE_ROOK("R"),
    WHITE_BISHOP("B"),
    WHITE_KNIGHT("N"),
    WHITE_PAWN("P"),
    BLACK_KING("k"),
    BLACK_QUEEN("q"),
    BLACK_ROOK("r"),
    BLACK_BISHOP("b"),
    BLACK_KNIGHT("n"),
    BLACK_PAWN("p"),
    EMPTY("");

    val isWhite: Boolean get() = symbol.isNotEmpty() && symbol[0].isUpperCase()
    val isBlack: Boolean get() = symbol.isNotEmpty() && symbol[0].isLowerCase()

    companion object {
        val ALL_CLASSES = arrayOf(
            WHITE_KING, WHITE_QUEEN, WHITE_ROOK, WHITE_BISHOP, WHITE_KNIGHT, WHITE_PAWN,
            BLACK_KING, BLACK_QUEEN, BLACK_ROOK, BLACK_BISHOP, BLACK_KNIGHT, BLACK_PAWN,
            EMPTY
        )

        fun fromIndex(index: Int): PieceClass {
            return if (index in ALL_CLASSES.indices) ALL_CLASSES[index] else EMPTY
        }

        fun fromSymbol(char: Char): PieceClass {
            return when (char) {
                'K' -> WHITE_KING
                'Q' -> WHITE_QUEEN
                'R' -> WHITE_ROOK
                'B' -> WHITE_BISHOP
                'N' -> WHITE_KNIGHT
                'P' -> WHITE_PAWN
                'k' -> BLACK_KING
                'q' -> BLACK_QUEEN
                'r' -> BLACK_ROOK
                'b' -> BLACK_BISHOP
                'n' -> BLACK_KNIGHT
                'p' -> BLACK_PAWN
                else -> EMPTY
            }
        }
    }
}

data class BoardLocalizationResult(
    val warpedBoardBitmap: Bitmap,
    val boardBoundingRect: Rect,
    val squareGrid: Array<Array<Rect>>, // 8x8 square bounding coordinates
    val playerOrientation: PlayerColor = PlayerColor.WHITE,
    val isDetectedByContour: Boolean = false
)

/**
 * Result of full board vision analysis
 */
data class VisionResult(
    val fen: String,
    val boardBoundingRect: Rect?,
    val playerOrientation: PlayerColor,
    val changedSquares: List<String>, // e.g. ["e2", "e4"]
    val pieceMatrix: Array<Array<PieceClass>>, // 8x8 matrix (rank 8 down to 1, files a to h)
    val latencyMs: Long,
    val isPositionChanged: Boolean,
    val isBoardDetected: Boolean = true
)

