package com.chessbeater.utils

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.abs

/**
 * Multi-Resolution and Aspect Ratio Calibration Adapter.
 * Calibrates and transforms coordinates between the downscaled capture buffer
 * (e.g. 720x1280) and native physical display (16:9, 19.5:9, 20:9, cutouts/notch, and tablets).
 * Aligned with PRD Section 7 & Phase 4.
 */
class DisplayMetricsAdapter(
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val densityDpi: Int,
    val statusBarInsetTopPx: Int = 0,
    val navBarInsetBottomPx: Int = 0
) {

    val aspectRatio: Float
        get() = if (screenWidthPx > 0) screenHeightPx.toFloat() / screenWidthPx.toFloat() else 1.777f

    enum class AspectCategory {
        CLASSIC_16_9,   // ~1.777
        TALL_18_9,      // ~2.000
        MODERN_19_5_9,  // ~2.166 (Standard modern smartphone)
        ULTRA_TALL_20_9,// ~2.222
        TABLET_4_3,     // ~1.333
        TABLET_16_10    // ~1.600
    }

    val aspectCategory: AspectCategory
        get() = when {
            aspectRatio < 1.45f -> AspectCategory.TABLET_4_3
            aspectRatio < 1.70f -> AspectCategory.TABLET_16_10
            aspectRatio < 1.88f -> AspectCategory.CLASSIC_16_9
            aspectRatio < 2.08f -> AspectCategory.TALL_18_9
            aspectRatio < 2.20f -> AspectCategory.MODERN_19_5_9
            else -> AspectCategory.ULTRA_TALL_20_9
        }

    /**
     * Maps a bounding rectangle detected in capture space (e.g., 720p) to the device's native screen pixel space.
     */
    fun mapCaptureRectToScreen(
        captureRect: Rect,
        captureWidth: Int,
        captureHeight: Int
    ): Rect {
        if (captureWidth <= 0 || captureHeight <= 0) return captureRect

        val scaleX = screenWidthPx.toFloat() / captureWidth.toFloat()
        val scaleY = screenHeightPx.toFloat() / captureHeight.toFloat()

        val left = (captureRect.left * scaleX).toInt()
        val top = (captureRect.top * scaleY).toInt()
        val right = (captureRect.right * scaleX).toInt()
        val bottom = (captureRect.bottom * scaleY).toInt()

        return Rect(left, top, right, bottom)
    }

    /**
     * Maps a PointF detected in capture space to physical screen pixel coordinates.
     */
    fun mapCapturePointToScreen(
        capturePoint: PointF,
        captureWidth: Int,
        captureHeight: Int
    ): PointF {
        if (captureWidth <= 0 || captureHeight <= 0) return capturePoint

        val scaleX = screenWidthPx.toFloat() / captureWidth.toFloat()
        val scaleY = screenHeightPx.toFloat() / captureHeight.toFloat()

        return PointF(capturePoint.x * scaleX, capturePoint.y * scaleY)
    }

    /**
     * Calibrates board bounds to ensure standard square aspect ratio (1:1 orthogonal grid)
     * even if distorted or perspective-skewed by camera notches/system bars.
     */
    fun calibrateSquareBounds(detectedRect: Rect): Rect {
        val width = detectedRect.width()
        val height = detectedRect.height()
        val side = (width + height) / 2

        val centerX = detectedRect.centerX()
        val centerY = detectedRect.centerY()

        val left = (centerX - side / 2).coerceAtLeast(0)
        val top = (centerY - side / 2).coerceAtLeast(statusBarInsetTopPx)
        val right = (left + side).coerceAtMost(screenWidthPx)
        val bottom = (top + side).coerceAtMost(screenHeightPx - navBarInsetBottomPx)

        return Rect(left, top, right, bottom)
    }

    companion object {
        fun fromContext(context: Context): DisplayMetricsAdapter {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayMetrics = DisplayMetrics()

            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)

            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            val density = displayMetrics.densityDpi

            // Estimate system insets
            val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBarHeight = if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0

            return DisplayMetricsAdapter(
                screenWidthPx = width,
                screenHeightPx = height,
                densityDpi = density,
                statusBarInsetTopPx = statusBarHeight
            )
        }
    }
}
