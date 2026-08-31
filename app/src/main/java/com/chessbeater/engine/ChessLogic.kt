package com.chessbeater.engine

import com.chessbeater.vision.models.PlayerColor
import com.chessbeater.vision.models.Side

/**
 * Chess board validation utilities including square attack detection and in-check validation.
 */
object ChessLogic {

    /**
     * Checks if a square [sqIndex] is under attack by pieces of side [bySide].
     */
    fun isSquareAttacked(sqIndex: Int, board: CharArray, bySide: Side): Boolean {
        if (sqIndex !in 0..63) return false
        val targetRow = sqIndex / 8
        val targetCol = sqIndex % 8

        // 1. Cek serangan Pion lawan
        val pawnChar = if (bySide == Side.WHITE) 'P' else 'p'
        val pawnAttackRow = if (bySide == Side.WHITE) targetRow + 1 else targetRow - 1
        if (pawnAttackRow in 0..7) {
            if (targetCol - 1 >= 0 && board[pawnAttackRow * 8 + (targetCol - 1)] == pawnChar) return true
            if (targetCol + 1 <= 7 && board[pawnAttackRow * 8 + (targetCol + 1)] == pawnChar) return true
        }

        // 2. Cek serangan Kuda (Knight)
        val knightChar = if (bySide == Side.WHITE) 'N' else 'n'
        val knightOffsets = arrayOf(
            Pair(-2, -1), Pair(-2, 1), Pair(-1, -2), Pair(-1, 2),
            Pair(1, -2), Pair(1, 2), Pair(2, -1), Pair(2, 1)
        )
        for ((dr, dc) in knightOffsets) {
            val r = targetRow + dr
            val c = targetCol + dc
            if (r in 0..7 && c in 0..7 && board[r * 8 + c] == knightChar) return true
        }

        // 3. Cek serangan Garis Lurus (Benteng & Ratu)
        val straightDirs = arrayOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1))
        val rookChar = if (bySide == Side.WHITE) 'R' else 'r'
        val queenChar = if (bySide == Side.WHITE) 'Q' else 'q'
        for ((dr, dc) in straightDirs) {
            var r = targetRow + dr
            var c = targetCol + dc
            while (r in 0..7 && c in 0..7) {
                val p = board[r * 8 + c]
                if (p != '.' && p != ' ') {
                    if (p == rookChar || p == queenChar) return true
                    break
                }
                r += dr
                c += dc
            }
        }

        // 4. Cek serangan Diagonal (Gajah & Ratu)
        val diagDirs = arrayOf(Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1))
        val bishopChar = if (bySide == Side.WHITE) 'B' else 'b'
        for ((dr, dc) in diagDirs) {
            var r = targetRow + dr
            var c = targetCol + dc
            while (r in 0..7 && c in 0..7) {
                val p = board[r * 8 + c]
                if (p != '.' && p != ' ') {
                    if (p == bishopChar || p == queenChar) return true
                    break
                }
                r += dr
                c += dc
            }
        }

        // 5. Cek ancaman Raja Lawan di sekitar (1 petak)
        val oppKingChar = if (bySide == Side.WHITE) 'K' else 'k'
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val r = targetRow + dr
                val c = targetCol + dc
                if (r in 0..7 && c in 0..7 && board[r * 8 + c] == oppKingChar) return true
            }
        }

        return false
    }

    /**
     * Checks if the King of [side] is in check on [board].
     */
    fun isKingInCheck(board: CharArray, side: Side): Boolean {
        val kingChar = if (side == Side.WHITE) 'K' else 'k'
        val oppSide = if (side == Side.WHITE) Side.BLACK else Side.WHITE
        val kingPos = board.indexOf(kingChar)
        if (kingPos == -1) return false
        return isSquareAttacked(kingPos, board, oppSide)
    }

    /**
     * Generates all pseudo-legal and castling destinations for a specific piece square.
     */
    fun getLegalDestinationsForSquare(sqIdx: Int, board: CharArray, castlingRights: String): List<Int> {
        val destinations = mutableListOf<Int>()
        val p = board.getOrNull(sqIdx) ?: return destinations
        if (p == '.' || p == ' ') return destinations

        val isW = p.isUpperCase()
        val row = sqIdx / 8
        val col = sqIdx % 8

        when (p.uppercaseChar()) {
            'P' -> {
                val dir = if (isW) -1 else 1
                val startRow = if (isW) 6 else 1
                val nr = row + dir
                if (nr in 0..7 && board[nr * 8 + col] == '.') {
                    destinations.add(nr * 8 + col)
                    val dr = row + 2 * dir
                    if (row == startRow && board[dr * 8 + col] == '.') destinations.add(dr * 8 + col)
                }
                for (dc in listOf(-1, 1)) {
                    val nc = col + dc
                    if (nr in 0..7 && nc in 0..7) {
                        val t = board[nr * 8 + nc]
                        if (t != '.' && t != ' ' && t.isUpperCase() != isW) destinations.add(nr * 8 + nc)
                    }
                }
            }
            'N' -> {
                val knightOffsets = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
                for ((dr, dc) in knightOffsets) {
                    val nr = row + dr; val nc = col + dc
                    if (nr in 0..7 && nc in 0..7) {
                        val t = board[nr * 8 + nc]
                        if (t == '.' || t == ' ' || t.isUpperCase() != isW) destinations.add(nr * 8 + nc)
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
                    var nr = row + dr; var nc = col + dc
                    while (nr in 0..7 && nc in 0..7) {
                        val t = board[nr * 8 + nc]
                        if (t == '.' || t == ' ') {
                            destinations.add(nr * 8 + nc)
                        } else {
                            if (t.isUpperCase() != isW) destinations.add(nr * 8 + nc)
                            break
                        }
                        nr += dr; nc += dc
                    }
                }
            }
            'K' -> {
                val enemyKingChar = if (isW) 'k' else 'K'
                val enemyKingIdx = board.indexOf(enemyKingChar)
                val enemyKingRow = if (enemyKingIdx >= 0) enemyKingIdx / 8 else -99
                val enemyKingCol = if (enemyKingIdx >= 0) enemyKingIdx % 8 else -99

                for (dr in -1..1) {
                    for (dc in -1..1) {
                        if (dr == 0 && dc == 0) continue
                        val nr = row + dr; val nc = col + dc
                        if (nr in 0..7 && nc in 0..7) {
                            if (kotlin.math.abs(nr - enemyKingRow) <= 1 && kotlin.math.abs(nc - enemyKingCol) <= 1) continue
                            val t = board[nr * 8 + nc]
                            if (t == '.' || t == ' ' || t.isUpperCase() != isW) destinations.add(nr * 8 + nc)
                        }
                    }
                }

                val side = if (isW) PlayerColor.WHITE else PlayerColor.BLACK
                val enemySide = if (isW) PlayerColor.BLACK else PlayerColor.WHITE
                val inCheck = isKingInCheck(board, side)

                if (!inCheck) {
                    if (isW && sqIdx == 60) {
                        if (castlingRights.contains('K') && board[61] == '.' && board[62] == '.' && board[63] == 'R' &&
                            !isSquareAttacked(61, board, enemySide) && !isSquareAttacked(62, board, enemySide)) {
                            destinations.add(62)
                        }
                        if (castlingRights.contains('Q') && board[59] == '.' && board[58] == '.' && board[57] == '.' && board[56] == 'R' &&
                            !isSquareAttacked(59, board, enemySide) && !isSquareAttacked(58, board, enemySide)) {
                            destinations.add(58)
                        }
                    } else if (!isW && sqIdx == 4) {
                        if (castlingRights.contains('k') && board[5] == '.' && board[6] == '.' && board[7] == 'r' &&
                            !isSquareAttacked(5, board, enemySide) && !isSquareAttacked(6, board, enemySide)) {
                            destinations.add(6)
                        }
                        if (castlingRights.contains('q') && board[3] == '.' && board[2] == '.' && board[1] == '.' && board[0] == 'r' &&
                            !isSquareAttacked(3, board, enemySide) && !isSquareAttacked(2, board, enemySide)) {
                            destinations.add(2)
                        }
                    }
                }
            }
        }
        return destinations
    }

    /**
     * Applies a move on a 64-square board array cleanly, removing the source piece
     * and handling castling rook displacement.
     */
    fun applyMoveToBoardArray(fromIndex: Int, toIndex: Int, board: CharArray) {
        if (fromIndex !in 0..63 || toIndex !in 0..63) return
        val piece = board[fromIndex]
        if (piece == '.' || piece == ' ') return

        // 1. Wajib hapus bidak asal secara absolut
        board[fromIndex] = '.'

        // 2. Tempatkan di petak tujuan (menimpa bidak lawan jika capture)
        board[toIndex] = piece

        // 3. Tangani perpindahan benteng saat rokade
        if (piece == 'K') {
            if (fromIndex == 60 && toIndex == 62) {
                board[63] = '.'
                board[61] = 'R'
            } else if (fromIndex == 60 && toIndex == 58) {
                board[56] = '.'
                board[59] = 'R'
            }
        } else if (piece == 'k') {
            if (fromIndex == 4 && toIndex == 6) {
                board[7] = '.'
                board[5] = 'r'
            } else if (fromIndex == 4 && toIndex == 2) {
                board[0] = '.'
                board[3] = 'r'
            }
        }
    }

    /**
     * Validates if a move is fully legal and does not leave the King in check.
     */
    fun isMoveLegal(fromIndex: Int, toIndex: Int, board: CharArray, activeSide: Side, castlingRights: String = "KQkq"): Boolean {
        if (fromIndex !in 0..63 || toIndex !in 0..63) return false
        val piece = board[fromIndex]
        if (piece == '.' || piece == ' ') return false
        val isWhite = piece.isUpperCase()
        if (isWhite != (activeSide == Side.WHITE)) return false

        val legalDests = getLegalDestinationsForSquare(fromIndex, board, castlingRights)
        if (toIndex !in legalDests) return false

        val tempBoard = board.copyOf()
        applyMoveToBoardArray(fromIndex, toIndex, tempBoard)
        return !isKingInCheck(tempBoard, activeSide)
    }

    /**
     * Generates all strictly legal moves (fromSquare to toSquare) for the given active side.
     * Moves that leave the king in check are strictly filtered out.
     */
    fun getAllLegalMoves(board: CharArray, activeSide: Side, castlingRights: String = "KQkq"): List<Pair<Int, Int>> {
        val moves = mutableListOf<Pair<Int, Int>>()
        val isW = activeSide == Side.WHITE
        for (sq in 0..63) {
            val piece = board[sq]
            if (piece != '.' && piece != ' ' && piece.isUpperCase() == isW) {
                val dests = getLegalDestinationsForSquare(sq, board, castlingRights)
                for (dest in dests) {
                    val tempBoard = board.copyOf()
                    applyMoveToBoardArray(sq, dest, tempBoard)
                    if (!isKingInCheck(tempBoard, activeSide)) {
                        moves.add(Pair(sq, dest))
                    }
                }
            }
        }
        return moves
    }
}
