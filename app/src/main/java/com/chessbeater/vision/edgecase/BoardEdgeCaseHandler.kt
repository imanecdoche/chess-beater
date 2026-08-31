package com.chessbeater.vision.edgecase

import android.graphics.Bitmap
import android.graphics.Color
import com.chessbeater.vision.models.PieceClass
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Vision Edge-Case & Artifact Filtering Handler.
 * Addresses:
 * 1. Platform Highlight & Arrow Filtering (Yellow last-move squares, green dots, red in-check glows).
 * 2. Adaptive Contrast & Lighting Normalization across custom board themes (Wood, Emerald, Bubblegum, Dark Mode).
 * 3. Piece Occlusion Guard (Handling finger dragging/hovering during active human moves).
 */
class BoardEdgeCaseHandler {

    // Thresholds for artificial highlight detection in HSV color space
    companion object {
        private const val YELLOW_HUE_MIN = 45f
        private const val YELLOW_HUE_MAX = 68f
        private const val GREEN_HUE_MIN = 90f
        private const val GREEN_HUE_MAX = 150f
        private const val RED_HUE_MIN = 345f
        private const val RED_HUE_MAX = 15f
        private const val SATURATION_HIGHLIGHT_THRESHOLD = 0.40f
    }

    /**
     * Filters out artificial platform highlight colors (e.g. Chess.com/Lichess move indicators)
     * from a 32x32 sub-square bitmap before feeding into TFLite classifier.
     */
    fun filterPlatformHighlights(squareBitmap: Bitmap): Bitmap {
        val width = squareBitmap.width
        val height = squareBitmap.height
        val outputBitmap = squareBitmap.copy(squareBitmap.config ?: Bitmap.Config.ARGB_8888, true)

        val pixels = IntArray(width * height)
        squareBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val hsv = FloatArray(3)
        var hasHighlight = false

        for (i in pixels.indices) {
            val color = pixels[i]
            Color.colorToHSV(color, hsv)

            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]

            // Check if pixel belongs to saturated yellow, green, or red platform overlay
            val isYellowHighlight = (hue in YELLOW_HUE_MIN..YELLOW_HUE_MAX) && sat >= SATURATION_HIGHLIGHT_THRESHOLD
            val isGreenDot = (hue in GREEN_HUE_MIN..GREEN_HUE_MAX) && sat >= SATURATION_HIGHLIGHT_THRESHOLD
            val isRedCheck = ((hue >= RED_HUE_MIN || hue <= RED_HUE_MAX) && sat >= SATURATION_HIGHLIGHT_THRESHOLD)

            if (isYellowHighlight || isGreenDot || isRedCheck) {
                hasHighlight = true
                // Neutralize highlight saturation while preserving luminance/edges
                hsv[1] = (sat * 0.15f) // Desaturate
                pixels[i] = Color.HSVToColor(Color.alpha(color), hsv)
            }
        }

        if (hasHighlight) {
            outputBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        }
        return outputBitmap
    }

    /**
     * Normalizes contrast and luminance across various board themes (Wood, Glass, Emerald, Dark)
     * using an adaptive histogram normalization heuristic.
     */
    fun normalizeContrastAdaptive(squareBitmap: Bitmap): Bitmap {
        val width = squareBitmap.width
        val height = squareBitmap.height
        val outputBitmap = squareBitmap.copy(squareBitmap.config ?: Bitmap.Config.ARGB_8888, true)

        val pixels = IntArray(width * height)
        squareBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var minLum = 255
        var maxLum = 0

        // Find luminance range
        for (color in pixels) {
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            if (lum < minLum) minLum = lum
            if (lum > maxLum) maxLum = lum
        }

        val range = max(1, maxLum - minLum)

        // Stretch histogram if dynamic range is compressed (e.g. low contrast board theme)
        if (range < 180) {
            for (i in pixels.indices) {
                val color = pixels[i]
                val a = Color.alpha(color)
                val r = ((Color.red(color) - minLum) * 255 / range).coerceIn(0, 255)
                val g = ((Color.green(color) - minLum) * 255 / range).coerceIn(0, 255)
                val b = ((Color.blue(color) - minLum) * 255 / range).coerceIn(0, 255)
                pixels[i] = Color.argb(a, r, g, b)
            }
            outputBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        }

        return outputBitmap
    }

    /**
     * Piece Occlusion Guard: Validates whether a detected piece change is stable
     * or if a square is occluded by player's finger during drag/drop gesture.
     */
    fun resolveOcclusion(
        predictedPiece: PieceClass,
        confidence: Float,
        lastConfirmedPiece: PieceClass
    ): PieceClass {
        // If classification confidence is low (< 0.65) during an active move,
        // retain previous confirmed state to prevent false transient FEN generation.
        return if (confidence < 0.65f) {
            lastConfirmedPiece
        } else {
            predictedPiece
        }
    }
}
