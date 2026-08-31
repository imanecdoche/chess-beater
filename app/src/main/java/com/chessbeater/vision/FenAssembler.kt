package com.chessbeater.vision

import com.chessbeater.vision.models.PieceClass
import com.chessbeater.vision.models.PlayerColor

/**
 * Standard Chess FEN (Forsyth–Edwards Notation) Assembler & State Validator
 */
class FenAssembler {

    private var activeTurn: PlayerColor = PlayerColor.WHITE
    private var whiteKingsideCastling = true
    private var whiteQueensideCastling = true
    private var blackKingsideCastling = true
    private var blackQueensideCastling = true
    private var enPassantTarget: String = "-"
    private var halfmoveClock: Int = 0
    private var fullmoveNumber: Int = 1

    /**
     * Converts an 8x8 grid of PieceClass into a valid FEN string.
     * Note: In standard FEN, rows are ordered from Rank 8 (top) to Rank 1 (bottom),
     * and files from 'a' (left) to 'h' (right).
     */
    fun assembleFen(
        pieceMatrix: Array<Array<PieceClass>>,
        playerOrientation: PlayerColor = PlayerColor.WHITE,
        overrideTurn: PlayerColor? = null
    ): String {
        val normalizedMatrix = if (playerOrientation == PlayerColor.BLACK) {
            // Flip board 180 degrees if Black is at the bottom
            flipMatrix180(pieceMatrix)
        } else {
            pieceMatrix
        }

        val fenRows = StringBuilder()

        for (row in 0 until 8) {
            var emptyCount = 0
            for (col in 0 until 8) {
                val piece = normalizedMatrix[row][col]
                if (piece == PieceClass.EMPTY) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        fenRows.append(emptyCount)
                        emptyCount = 0
                    }
                    fenRows.append(piece.symbol)
                }
            }
            if (emptyCount > 0) {
                fenRows.append(emptyCount)
            }
            if (row < 7) {
                fenRows.append("/")
            }
        }

        // Update castling rights according to king & rook presence
        updateCastlingRights(normalizedMatrix)

        val turnStr = (overrideTurn ?: activeTurn).let {
            if (it == PlayerColor.WHITE) "w" else "b"
        }

        val castlingStr = buildCastlingString()

        return "$fenRows $turnStr $castlingStr $enPassantTarget $halfmoveClock $fullmoveNumber"
    }

    /**
     * Toggles the active turn (White -> Black -> White)
     */
    fun toggleTurn() {
        if (activeTurn == PlayerColor.BLACK) {
            fullmoveNumber++
            activeTurn = PlayerColor.WHITE
        } else {
            activeTurn = PlayerColor.BLACK
        }
    }

    fun setActiveTurn(turn: PlayerColor) {
        activeTurn = turn
    }

    fun setEnPassantTarget(target: String) {
        enPassantTarget = if (target.isNotBlank()) target else "-"
    }

    fun resetState() {
        activeTurn = PlayerColor.WHITE
        whiteKingsideCastling = true
        whiteQueensideCastling = true
        blackKingsideCastling = true
        blackQueensideCastling = true
        enPassantTarget = "-"
        halfmoveClock = 0
        fullmoveNumber = 1
    }

    private fun updateCastlingRights(matrix: Array<Array<PieceClass>>) {
        // If White king is not on e1 (row 7, col 4), revoke all white castling
        if (matrix[7][4] != PieceClass.WHITE_KING) {
            whiteKingsideCastling = false
            whiteQueensideCastling = false
        }
        // If White rooks are not on h1 / a1
        if (matrix[7][7] != PieceClass.WHITE_ROOK) whiteKingsideCastling = false
        if (matrix[7][0] != PieceClass.WHITE_ROOK) whiteQueensideCastling = false

        // If Black king is not on e8 (row 0, col 4), revoke all black castling
        if (matrix[0][4] != PieceClass.BLACK_KING) {
            blackKingsideCastling = false
            blackQueensideCastling = false
        }
        // If Black rooks are not on h8 / a8
        if (matrix[0][7] != PieceClass.BLACK_ROOK) blackKingsideCastling = false
        if (matrix[0][0] != PieceClass.BLACK_ROOK) blackQueensideCastling = false
    }

    private fun buildCastlingString(): String {
        val sb = StringBuilder()
        if (whiteKingsideCastling) sb.append("K")
        if (whiteQueensideCastling) sb.append("Q")
        if (blackKingsideCastling) sb.append("k")
        if (blackQueensideCastling) sb.append("q")
        return if (sb.isEmpty()) "-" else sb.toString()
    }

    /**
     * Flips 8x8 matrix 180 degrees (reverses rows and columns)
     */
    private fun flipMatrix180(matrix: Array<Array<PieceClass>>): Array<Array<PieceClass>> {
        val flipped = Array(8) { Array(8) { PieceClass.EMPTY } }
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                flipped[7 - r][7 - c] = matrix[r][c]
            }
        }
        return flipped
    }
}
