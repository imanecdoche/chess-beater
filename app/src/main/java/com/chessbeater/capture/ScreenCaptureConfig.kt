package com.chessbeater.capture

import android.content.Context
import java.io.Serializable

/**
 * Configuration for MediaProjection Screen Ingestion
 * Aligned with PRD Section 2 & 7.1: Downscaled resolution (e.g. 720p) & 15-30 FPS for memory/battery efficiency.
 */
data class ScreenCaptureConfig(
    val targetWidth: Int = 720,
    val targetHeight: Int = 1280,
    val densityDpi: Int = 320,
    val targetFps: Int = 20, // 15 - 30 FPS
    val maxBufferImages: Int = 2,
    val screenWidth: Int = 1080,
    val screenHeight: Int = 2400
) : Serializable {
    /**
     * Time interval between frames in milliseconds to enforce target FPS throttling.
     */
    val frameIntervalMs: Long
        get() = (1000L / targetFps.coerceIn(5, 60))

    companion object {
        fun createForDevice(context: Context, targetFps: Int = 20): ScreenCaptureConfig {
            val metrics = context.resources.displayMetrics
            val screenW = metrics.widthPixels
            val screenH = metrics.heightPixels
            val dpi = metrics.densityDpi

            // Scale width to 720 while strictly preserving physical aspect ratio
            val targetW = 720.coerceAtMost(screenW)
            val targetH = (screenH * (targetW.toFloat() / screenW)).toInt()

            return ScreenCaptureConfig(
                targetWidth = targetW,
                targetHeight = targetH,
                densityDpi = dpi,
                targetFps = targetFps,
                maxBufferImages = 2,
                screenWidth = screenW,
                screenHeight = screenH
            )
        }
    }
}
