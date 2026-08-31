package com.chessbeater.utils

import android.graphics.PointF
import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test

class DisplayMetricsAdapterTest {

    @Test
    fun testAspectRatioCategorization() {
        // 16:9 Standard phone (1080x1920 -> 1.777)
        val adapter16x9 = DisplayMetricsAdapter(1080, 1920, 420)
        assertEquals(DisplayMetricsAdapter.AspectCategory.CLASSIC_16_9, adapter16x9.aspectCategory)

        // 19.5:9 Modern phone (1080x2340 -> 2.166)
        val adapter19_5x9 = DisplayMetricsAdapter(1080, 2340, 440)
        assertEquals(DisplayMetricsAdapter.AspectCategory.MODERN_19_5_9, adapter19_5x9.aspectCategory)

        // 20:9 Tall phone (1080x2400 -> 2.222)
        val adapter20x9 = DisplayMetricsAdapter(1080, 2400, 450)
        assertEquals(DisplayMetricsAdapter.AspectCategory.ULTRA_TALL_20_9, adapter20x9.aspectCategory)

        // 4:3 Tablet (1536x2048 -> 1.333)
        val adapterTablet = DisplayMetricsAdapter(1536, 2048, 320)
        assertEquals(DisplayMetricsAdapter.AspectCategory.TABLET_4_3, adapterTablet.aspectCategory)
    }

    @Test
    fun testMapCaptureRectToScreen() {
        // Device is 1080x1920 (1.5x scale from 720x1280)
        val adapter = DisplayMetricsAdapter(1080, 1920, 420)

        val captureRect = Rect(100, 200, 500, 600)
        val screenRect = adapter.mapCaptureRectToScreen(captureRect, captureWidth = 720, captureHeight = 1280)

        assertEquals(150, screenRect.left)
        assertEquals(300, screenRect.top)
        assertEquals(750, screenRect.right)
        assertEquals(900, screenRect.bottom)
    }

    @Test
    fun testMapCapturePointToScreen() {
        val adapter = DisplayMetricsAdapter(1080, 2160, 420) // 1.5x width scale, 2.0x height scale from 720x1080
        val point = PointF(100f, 200f)
        val screenPoint = adapter.mapCapturePointToScreen(point, captureWidth = 720, captureHeight = 1080)

        assertEquals(150f, screenPoint.x, 0.01f)
        assertEquals(400f, screenPoint.y, 0.01f)
    }

    @Test
    fun testCalibrateSquareBounds() {
        val adapter = DisplayMetricsAdapter(1080, 2400, 450, statusBarInsetTopPx = 80, navBarInsetBottomPx = 100)
        val skewedRect = Rect(50, 400, 1030, 1400) // 980 width x 1000 height

        val calibrated = adapter.calibrateSquareBounds(skewedRect)

        // Board must be 1:1 orthogonal square
        assertEquals(calibrated.width(), calibrated.height())
        assertTrue(calibrated.top >= adapter.statusBarInsetTopPx)
        assertTrue(calibrated.bottom <= adapter.screenHeightPx - adapter.navBarInsetBottomPx)
    }
}
