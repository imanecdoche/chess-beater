package com.chessbeater.overlay

import java.io.Serializable

/**
 * Configuration and Styling for Interactive Floating Mini Chessboard
 */
data class OverlayStyleConfig(
    val miniBoardSizeDp: Int = 220, // 140dp - 280dp
    val boardOpacity: Float = 0.94f, // 0.6f - 1.0f
    val lastPosX: Int = 40,
    val lastPosY: Int = 180,
    val isMiniBoardVisible: Boolean = false
) : Serializable
