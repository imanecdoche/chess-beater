package com.chessbeater.vision

import android.graphics.Bitmap
import android.graphics.Rect
import com.chessbeater.vision.models.PlayerColor

/**
 * Square Slicing & Frame Difference Change Detector
 */
class SquareExtractor(
    private val pieceInputSize: Int = 32,
    private val pixelDiffThreshold: Double = 18.0
) {
    // Previous frame square luminance signatures (64 squares)
    private val previousSquareSignatures = DoubleArray(64) { 0.0 }
    private var hasPreviousFrame = false

    /**
     * Slices the warped orthogonal board into 64 sub-bitmaps of size 32x32.
     * Returns List of 64 Bitmaps ordered row-by-row (top-to-bottom, left-to-right).
     */
    fun slice64Squares(
        warpedBoardBitmap: Bitmap,
        squareGrid: Array<Array<Rect>>
    ): List<Bitmap> {
        val squareBitmaps = ArrayList<Bitmap>(64)

        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val rect = squareGrid[row][col]
                val subBitmap = Bitmap.createBitmap(
                    warpedBoardBitmap,
                    rect.left,
                    rect.top,
                    rect.width(),
                    rect.height()
                )

                val scaledSquare = if (subBitmap.width != pieceInputSize || subBitmap.height != pieceInputSize) {
                    val scaled = Bitmap.createScaledBitmap(subBitmap, pieceInputSize, pieceInputSize, true)
                    subBitmap.recycle()
                    scaled
                } else {
                    subBitmap
                }
                squareBitmaps.add(scaledSquare)
            }
        }
        return squareBitmaps
    }


    /**
     * Frame Difference Change Detector:
     * Computes mean luminance difference for all 64 squares.
     * Returns list of changed square notations (e.g., ["e2", "e4"]).
     */
    fun detectChangedSquares(
        squareBitmaps: List<Bitmap>,
        playerOrientation: PlayerColor
    ): List<String> {
        val changedSquares = mutableListOf<String>()
        val currentSignatures = DoubleArray(64)

        for (i in 0 until 64) {
            val bitmap = squareBitmaps[i]
            val signature = computeSquareSignature(bitmap)
            currentSignatures[i] = signature

            if (hasPreviousFrame) {
                val diff = Math.abs(signature - previousSquareSignatures[i])
                if (diff > pixelDiffThreshold) {
                    val squareNotation = indexToSquareNotation(i, playerOrientation)
                    changedSquares.add(squareNotation)
                }
            }
        }

        // Update previous frame cache
        System.arraycopy(currentSignatures, 0, previousSquareSignatures, 0, 64)
        hasPreviousFrame = true

        return changedSquares
    }

    /**
     * Resets the frame difference state cache
     */
    fun resetCache() {
        hasPreviousFrame = false
        previousSquareSignatures.fill(0.0)
    }

    private fun computeSquareSignature(bitmap: Bitmap): Double {
        var totalLuminance = 0.0
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                totalLuminance += (0.299 * r + 0.587 * g + 0.114 * b)
            }
        }
        return if (totalPixels > 0) totalLuminance / totalPixels else 0.0
    }

    /**
     * Maps flat index (0..63) to chess square notation (e.g. 0 -> "a8", 63 -> "h1" when White is bottom)
     */
    fun indexToSquareNotation(index: Int, playerOrientation: PlayerColor): String {
        val row = index / 8
        val col = index % 8

        val fileChar = if (playerOrientation == PlayerColor.WHITE) {
            ('a'.code + col).toChar()
        } else {
            ('h'.code - col).toChar()
        }

        val rankNum = if (playerOrientation == PlayerColor.WHITE) {
            8 - row
        } else {
            1 + row
        }

        return "$fileChar$rankNum"
    }
}
