package com.chessbeater.vision

import android.graphics.Bitmap
import android.graphics.Rect
import com.chessbeater.vision.models.BoardLocalizationResult
import com.chessbeater.vision.models.PlayerColor

/**
 * OpenCV-based Chess Board Localization & Perspective Correction Engine
 */
class OpenCvBoardDetector(
    private val targetBoardSize: Int = 512
) {
    companion object {
        private const val TAG = "OpenCvBoardDetector"
        var isOpenCvLoaded = false

        init {
            try {
                System.loadLibrary("opencv_java4")
                isOpenCvLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                isOpenCvLoaded = false
            }
        }
    }

    /**
     * Detects board boundary, applies perspective warp, slices grid, and identifies orientation.
     */
    fun locateAndExtractBoard(
        frameBitmap: Bitmap,
        appProfile: com.chessbeater.vision.models.ChessAppProfile = com.chessbeater.vision.models.ChessAppProfile()
    ): BoardLocalizationResult? {
        val frameWidth = frameBitmap.width
        val frameHeight = frameBitmap.height
        if (frameWidth <= 0 || frameHeight <= 0) return null

        val screenW = appProfile.screenWidth.coerceAtLeast(frameWidth)
        val screenH = appProfile.screenHeight.coerceAtLeast(frameHeight)

        val scaleX = frameWidth.toFloat() / screenW
        val scaleY = frameHeight.toFloat() / screenH

        // 1. Get Screen-space board bounding rectangle
        val screenBoardRect = if (appProfile.customCalibratedRect != null &&
            appProfile.customCalibratedRect.width() > 50 &&
            appProfile.customCalibratedRect.height() > 50
        ) {
            appProfile.customCalibratedRect
        } else {
            appProfile.calculateFallbackBoardRect(screenW, screenH)
        }

        // 2. Map Screen-space rectangle to Frame Bitmap coordinates
        val cropLeft = (screenBoardRect.left * scaleX).toInt().coerceIn(0, frameWidth - 1)
        val cropTop = (screenBoardRect.top * scaleY).toInt().coerceIn(0, frameHeight - 1)
        val cropWidth = (screenBoardRect.width() * scaleX).toInt().coerceAtMost(frameWidth - cropLeft).coerceAtLeast(1)
        val cropHeight = (screenBoardRect.height() * scaleY).toInt().coerceAtMost(frameHeight - cropTop).coerceAtLeast(1)

        // 3. Extract sub-bitmap for board
        val croppedBoard = try {
            Bitmap.createBitmap(frameBitmap, cropLeft, cropTop, cropWidth, cropHeight)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error cropping board bitmap: [${cropLeft}, ${cropTop}, ${cropWidth}x${cropHeight}]", e)
            frameBitmap
        }

        // 4. Scale to standard target board dimension (512x512) for neural inference & slicing
        val scaledBoard = if (croppedBoard.width != targetBoardSize || croppedBoard.height != targetBoardSize) {
            Bitmap.createScaledBitmap(croppedBoard, targetBoardSize, targetBoardSize, true)
        } else {
            croppedBoard
        }

        // 5. Generate 8x8 square coordinate matrix on the 512x512 scaled board
        val squareGrid = generateSquareGrid(scaledBoard.width, scaledBoard.height)

        // 6. Detect board orientation (White vs Black on bottom)
        val orientation = detectOrientation(scaledBoard)

        return BoardLocalizationResult(
            warpedBoardBitmap = scaledBoard,
            boardBoundingRect = screenBoardRect, // Return true SCREEN coordinates for accurate overlay drawing
            squareGrid = squareGrid,
            playerOrientation = orientation,
            isDetectedByContour = false
        )
    }

    /**
     * Slices 8x8 square grid boundaries
     */
    private fun generateSquareGrid(boardWidth: Int, boardHeight: Int): Array<Array<Rect>> {
        val squareWidth = boardWidth / 8
        val squareHeight = boardHeight / 8

        return Array(8) { row ->
            Array(8) { col ->
                Rect(
                    col * squareWidth,
                    row * squareHeight,
                    (col + 1) * squareWidth,
                    (row + 1) * squareHeight
                )
            }
        }
    }

    /**
     * Detects board orientation by analyzing luminance/color distribution
     * of player's bottom ranks (rows 6 and 7) vs opponent's top ranks (rows 0 and 1).
     */
    fun detectOrientation(boardBitmap: Bitmap): PlayerColor {
        val height = boardBitmap.height
        val width = boardBitmap.width
        val squareH = height / 8
        val squareW = width / 8

        var bottomLuminanceSum = 0L
        var bottomSampleCount = 0
        var topLuminanceSum = 0L
        var topSampleCount = 0

        // Sample rows 6 and 7 (Player's side at bottom of screen)
        for (r in 6..7) {
            for (c in 0 until 8) {
                val cx = c * squareW + squareW / 2
                val cy = r * squareH + squareH / 2
                val pixel = boardBitmap.getPixel(cx, cy)
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                bottomLuminanceSum += (0.299 * red + 0.587 * green + 0.114 * blue).toLong()
                bottomSampleCount++
            }
        }

        // Sample rows 0 and 1 (Opponent's side at top of screen)
        for (r in 0..1) {
            for (c in 0 until 8) {
                val cx = c * squareW + squareW / 2
                val cy = r * squareH + squareH / 2
                val pixel = boardBitmap.getPixel(cx, cy)
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                topLuminanceSum += (0.299 * red + 0.587 * green + 0.114 * blue).toLong()
                topSampleCount++
            }
        }

        val avgBot = if (bottomSampleCount > 0) bottomLuminanceSum / bottomSampleCount else 128
        val avgTop = if (topSampleCount > 0) topLuminanceSum / topSampleCount else 128

        // If bottom rank pieces are lighter than top rank pieces -> Player is White (not flipped)
        // If bottom rank pieces are darker than top rank pieces -> Player is Black (flipped)
        return if (avgBot >= avgTop) {
            PlayerColor.WHITE
        } else {
            PlayerColor.BLACK
        }
    }
}

