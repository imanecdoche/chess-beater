package com.chessbeater.governor

import android.os.SystemClock

/**
 * Smart Battery & Frame Governor.
 * Dynamically throttles screen capture FPS to minimize CPU & battery consumption:
 * - 5–10 FPS when board is static / opponent is thinking (PRD Section 2 & 7.1)
 * - 30 FPS burst mode when piece movement or board change is detected.
 */
class BatteryGovernor(
    val idleFps: Int = 8,        // 125ms interval
    val normalFps: Int = 18,     // 55ms interval
    val burstFps: Int = 30       // 33ms interval
) {

    enum class PowerState {
        BURST_ACTIVE,        // Fast 30 FPS for instant move detection
        NORMAL_TRACKING,     // Standard 18 FPS
        IDLE_CONSERVATIVE    // Ultra-low 8 FPS during opponent thought pauses
    }

    var currentState: PowerState = PowerState.NORMAL_TRACKING
        private set

    private var staticFrameCount: Int = 0
    private var lastStateChangeTimestamp: Long = SystemClock.uptimeMillis()
    private var lastFrameProcessedTimestamp: Long = 0L

    companion object {
        private const val STATIC_THRESHOLD_TO_IDLE = 4 // Consecutive unchanged frames to enter idle mode
        private const val BURST_HOLD_DURATION_MS = 600L // Keep burst mode for at least 600ms after move
    }

    /**
     * Determines whether the current frame should be processed or skipped based on dynamic interval throttling.
     */
    fun shouldProcessFrame(nowMs: Long = SystemClock.uptimeMillis()): Boolean {
        val targetInterval = getTargetFrameIntervalMs()
        if (nowMs - lastFrameProcessedTimestamp >= targetInterval) {
            lastFrameProcessedTimestamp = nowMs
            return true
        }
        return false
    }

    /**
     * Updates governor state based on vision pipeline change detection.
     */
    fun onFrameAnalysisCompleted(isPositionChanged: Boolean, changedSquareCount: Int) {
        val now = SystemClock.uptimeMillis()

        if (isPositionChanged || changedSquareCount >= 2) {
            // Immediate transition to Burst Active mode (30 FPS)
            currentState = PowerState.BURST_ACTIVE
            staticFrameCount = 0
            lastStateChangeTimestamp = now
        } else {
            staticFrameCount++
            if (currentState == PowerState.BURST_ACTIVE) {
                if (now - lastStateChangeTimestamp > BURST_HOLD_DURATION_MS) {
                    currentState = PowerState.NORMAL_TRACKING
                    lastStateChangeTimestamp = now
                }
            } else if (staticFrameCount >= STATIC_THRESHOLD_TO_IDLE) {
                // Drop to idle conservative mode to save battery
                currentState = PowerState.IDLE_CONSERVATIVE
            }
        }
    }

    fun getTargetFps(): Int {
        return when (currentState) {
            PowerState.BURST_ACTIVE -> burstFps
            PowerState.NORMAL_TRACKING -> normalFps
            PowerState.IDLE_CONSERVATIVE -> idleFps
        }
    }

    fun getTargetFrameIntervalMs(): Long {
        val fps = getTargetFps().coerceIn(5, 60)
        return (1000L / fps)
    }

    fun reset() {
        currentState = PowerState.NORMAL_TRACKING
        staticFrameCount = 0
        lastFrameProcessedTimestamp = 0L
    }
}
