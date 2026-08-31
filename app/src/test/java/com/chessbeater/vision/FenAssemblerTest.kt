package com.chessbeater.vision

import com.chessbeater.vision.models.PieceClass
import com.chessbeater.vision.models.PlayerColor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FenAssemblerTest {

    private lateinit var fenAssembler: FenAssembler

    @Before
    fun setUp() {
        fenAssembler = FenAssembler()
    }

    @Test
    fun testStartingPositionFenGeneration() {
        val matrix = Array(8) { Array(8) { PieceClass.EMPTY } }

        // Rank 8 (row 0)
        matrix[0] = arrayOf(
            PieceClass.BLACK_ROOK, PieceClass.BLACK_KNIGHT, PieceClass.BLACK_BISHOP, PieceClass.BLACK_QUEEN,
            PieceClass.BLACK_KING, PieceClass.BLACK_BISHOP, PieceClass.BLACK_KNIGHT, PieceClass.BLACK_ROOK
        )
        // Rank 7 (row 1)
        matrix[1] = Array(8) { PieceClass.BLACK_PAWN }

        // Rank 2 (row 6)
        matrix[6] = Array(8) { PieceClass.WHITE_PAWN }

        // Rank 1 (row 7)
        matrix[7] = arrayOf(
            PieceClass.WHITE_ROOK, PieceClass.WHITE_KNIGHT, PieceClass.WHITE_BISHOP, PieceClass.WHITE_QUEEN,
            PieceClass.WHITE_KING, PieceClass.WHITE_BISHOP, PieceClass.WHITE_KNIGHT, PieceClass.WHITE_ROOK
        )

        val fen = fenAssembler.assembleFen(matrix, PlayerColor.WHITE)
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", fen)
    }

    @Test
    fun testMidgamePositionFenGeneration() {
        // Italian Game position: "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4"
        val matrix = Array(8) { Array(8) { PieceClass.EMPTY } }

        // Rank 8: r . b q k . n r
        matrix[0][0] = PieceClass.BLACK_ROOK
        matrix[0][2] = PieceClass.BLACK_BISHOP
        matrix[0][3] = PieceClass.BLACK_QUEEN
        matrix[0][4] = PieceClass.BLACK_KING
        matrix[0][6] = PieceClass.BLACK_KNIGHT
        matrix[0][7] = PieceClass.BLACK_ROOK

        // Rank 7: p p p p . p p p
        matrix[1] = arrayOf(
            PieceClass.BLACK_PAWN, PieceClass.BLACK_PAWN, PieceClass.BLACK_PAWN, PieceClass.BLACK_PAWN,
            PieceClass.EMPTY, PieceClass.BLACK_PAWN, PieceClass.BLACK_PAWN, PieceClass.BLACK_PAWN
        )

        // Rank 6: . . n . . . . . (Nc6)
        matrix[2][2] = PieceClass.BLACK_KNIGHT

        // Rank 5: . . b . p . . . (Bc5, e5)
        matrix[3][2] = PieceClass.BLACK_BISHOP
        matrix[3][4] = PieceClass.BLACK_PAWN

        // Rank 4: . . B . P . . . (Bc4, e4)
        matrix[4][2] = PieceClass.WHITE_BISHOP
        matrix[4][4] = PieceClass.WHITE_PAWN

        // Rank 3: . . . . . N . . (Nf3)
        matrix[5][5] = PieceClass.WHITE_KNIGHT

        // Rank 2: P P P P . P P P
        matrix[6] = arrayOf(
            PieceClass.WHITE_PAWN, PieceClass.WHITE_PAWN, PieceClass.WHITE_PAWN, PieceClass.WHITE_PAWN,
            PieceClass.EMPTY, PieceClass.WHITE_PAWN, PieceClass.WHITE_PAWN, PieceClass.WHITE_PAWN
        )

        // Rank 1: R N B Q K . . R
        matrix[7][0] = PieceClass.WHITE_ROOK
        matrix[7][1] = PieceClass.WHITE_KNIGHT
        matrix[7][2] = PieceClass.WHITE_BISHOP
        matrix[7][3] = PieceClass.WHITE_QUEEN
        matrix[7][4] = PieceClass.WHITE_KING
        matrix[7][7] = PieceClass.WHITE_ROOK

        val fen = fenAssembler.assembleFen(matrix, PlayerColor.WHITE)
        assertEquals("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 1", fen)
    }

    @Test
    fun testBlackOrientationFlipping() {
        // Construct matrix from Black's perspective (Rank 1 at top, Rank 8 at bottom)
        val blackViewMatrix = Array(8) { Array(8) { PieceClass.EMPTY } }

        // Top row from Black's POV is White's back rank
        blackViewMatrix[0] = arrayOf(
            PieceClass.WHITE_ROOK, PieceClass.WHITE_KNIGHT, PieceClass.WHITE_BISHOP, PieceClass.WHITE_KING,
            PieceClass.WHITE_QUEEN, PieceClass.WHITE_BISHOP, PieceClass.WHITE_KNIGHT, PieceClass.WHITE_ROOK
        )
        blackViewMatrix[1] = Array(8) { PieceClass.WHITE_PAWN }
        blackViewMatrix[6] = Array(8) { PieceClass.BLACK_PAWN }
        blackViewMatrix[7] = arrayOf(
            PieceClass.BLACK_ROOK, PieceClass.BLACK_KNIGHT, PieceClass.BLACK_BISHOP, PieceClass.BLACK_KING,
            PieceClass.BLACK_QUEEN, PieceClass.BLACK_BISHOP, PieceClass.BLACK_KNIGHT, PieceClass.BLACK_ROOK
        )

        val fen = fenAssembler.assembleFen(blackViewMatrix, PlayerColor.BLACK)
        // Correctly flipped back to standard White Rank 8 to 1 order
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", fen)
    }

    @Test
    fun testSquareNotationMapping() {
        val extractor = SquareExtractor()

        // White at bottom
        assertEquals("a8", extractor.indexToSquareNotation(0, PlayerColor.WHITE))
        assertEquals("h8", extractor.indexToSquareNotation(7, PlayerColor.WHITE))
        assertEquals("e4", extractor.indexToSquareNotation(36, PlayerColor.WHITE))
        assertEquals("h1", extractor.indexToSquareNotation(63, PlayerColor.WHITE))

        // Black at bottom
        assertEquals("h1", extractor.indexToSquareNotation(0, PlayerColor.BLACK))
        assertEquals("a1", extractor.indexToSquareNotation(7, PlayerColor.BLACK))
        assertEquals("a8", extractor.indexToSquareNotation(63, PlayerColor.BLACK))
    }
}
