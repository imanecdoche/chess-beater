package com.chessbeater.vision.models

import android.graphics.Rect

/**
 * Supported target chess applications and vision layouts.
 */
enum class ChessAppTarget(val displayName: String, val description: String) {
    CHESS_COM(
        displayName = "Chess.com",
        description = "Optimized for Chess.com mobile layout (center 1:1 square, green/wood themes)"
    ),
    LICHESS(
        displayName = "Lichess",
        description = "Optimized for Lichess mobile app (center-weighted, brown/blue themes)"
    ),
    UNIVERSAL_AUTO(
        displayName = "Universal (Auto-Detect)",
        description = "Dynamic OpenCV contour edge detection with automatic fallback"
    )
}

/**
 * Geometric profile and cropping presets for target chess applications.
 */
data class ChessAppProfile(
    val target: ChessAppTarget = ChessAppTarget.CHESS_COM,
    val centerCropRatio: Float = 1.0f,
    val verticalOffsetRatio: Float = 0.0f, // 0.0 means centered vertically on screen
    val customCalibratedRect: Rect? = null,
    val screenWidth: Int = 1080,
    val screenHeight: Int = 2400
) {
    /**
     * Calculates the estimated board bounding rectangle for a given screen dimension
     * as a reliable zero-latency fallback when dynamic contour search is ambiguous,
     * or returns the exact manual calibration coordinates if set by the user.
     */
    fun calculateFallbackBoardRect(targetScreenWidth: Int = screenWidth, targetScreenHeight: Int = screenHeight): Rect {
        if (customCalibratedRect != null && customCalibratedRect.width() > 0 && customCalibratedRect.height() > 0) {
            return customCalibratedRect
        }
        val boardDimension = minOf(targetScreenWidth, targetScreenHeight)
        val left = (targetScreenWidth - boardDimension) / 2
        val centerY = targetScreenHeight / 2
        val top = (centerY - (boardDimension / 2) + (verticalOffsetRatio * targetScreenHeight)).toInt()
            .coerceIn(0, (targetScreenHeight - boardDimension).coerceAtLeast(0))
        return Rect(left, top, left + boardDimension, top + boardDimension)
    }

    companion object {
        fun forTarget(target: ChessAppTarget, screenW: Int = 1080, screenH: Int = 2400): ChessAppProfile = when (target) {
            ChessAppTarget.CHESS_COM -> ChessAppProfile(
                target = ChessAppTarget.CHESS_COM,
                centerCropRatio = 1.0f,
                verticalOffsetRatio = 0.0f,
                screenWidth = screenW,
                screenHeight = screenH
            )
            ChessAppTarget.LICHESS -> ChessAppProfile(
                target = ChessAppTarget.LICHESS,
                centerCropRatio = 1.0f,
                verticalOffsetRatio = 0.0f,
                screenWidth = screenW,
                screenHeight = screenH
            )
            ChessAppTarget.UNIVERSAL_AUTO -> ChessAppProfile(
                target = ChessAppTarget.UNIVERSAL_AUTO,
                centerCropRatio = 1.0f,
                verticalOffsetRatio = 0.0f,
                screenWidth = screenW,
                screenHeight = screenH
            )
        }
    }
}
