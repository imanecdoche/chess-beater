package com.chessbeater.capture

import org.junit.Assert.*
import org.junit.Test

class ScreenCaptureConfigTest {

    @Test
    fun testDefaultConfigValues() {
        val config = ScreenCaptureConfig()
        assertEquals(720, config.targetWidth)
        assertEquals(1280, config.targetHeight)
        assertEquals(20, config.targetFps)
        assertEquals(50L, config.frameIntervalMs) // 1000 / 20 = 50ms
    }

    @Test
    fun testCustomFpsIntervalCalculation() {
        val config30Fps = ScreenCaptureConfig(targetFps = 30)
        assertEquals(33L, config30Fps.frameIntervalMs)

        val config15Fps = ScreenCaptureConfig(targetFps = 15)
        assertEquals(66L, config15Fps.frameIntervalMs)
    }
}
