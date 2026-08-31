package com.chessbeater.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Manages tactile haptic feedback / discrete Morse vibrations for Chess Beater
 * Aligned with PRD Section 5.3:
 * - 1 Short vibration: Standard new move recommendation.
 * - 2 Vibrations: Tactical swing, check, or mate threat.
 * - Morse Warning Pattern: Blunder risk or critical danger.
 */
class HapticFeedbackManager(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var isHapticsEnabled: Boolean = true

    enum class HapticPattern {
        STANDARD_MOVE,   // 1 discrete vibration (40ms)
        TACTICAL_MOVE,   // 2 sharp vibrations (40ms on, 50ms off, 40ms on)
        BLUNDER_WARNING, // Morse code warning pattern (80ms on, 50ms off, 80ms on, 50ms off, 120ms on)
        MATE_IN_SIGHT    // Triple sharp pulse
    }

    companion object {
        private const val TAG = "HapticFeedbackManager"
        private val STANDARD_PATTERN = longArrayOf(0, 45)
        private val TACTICAL_PATTERN = longArrayOf(0, 40, 50, 40)
        private val BLUNDER_PATTERN = longArrayOf(0, 80, 50, 80, 50, 120)
        private val MATE_PATTERN = longArrayOf(0, 50, 40, 50, 40, 80)
    }

    /**
     * Triggers haptic vibration based on tactical situation
     */
    fun triggerHaptic(pattern: HapticPattern) {
        if (!isHapticsEnabled || vibrator == null || !vibrator.hasVibrator()) {
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = when (pattern) {
                    HapticPattern.STANDARD_MOVE -> STANDARD_PATTERN
                    HapticPattern.TACTICAL_MOVE -> TACTICAL_PATTERN
                    HapticPattern.BLUNDER_WARNING -> BLUNDER_PATTERN
                    HapticPattern.MATE_IN_SIGHT -> MATE_PATTERN
                }
                val amplitudes = IntArray(timings.size) { index ->
                    if (index % 2 == 0) 0 else VibrationEffect.DEFAULT_AMPLITUDE
                }
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                when (pattern) {
                    HapticPattern.STANDARD_MOVE -> vibrator.vibrate(45L)
                    HapticPattern.TACTICAL_MOVE -> vibrator.vibrate(TACTICAL_PATTERN, -1)
                    HapticPattern.BLUNDER_WARNING -> vibrator.vibrate(BLUNDER_PATTERN, -1)
                    HapticPattern.MATE_IN_SIGHT -> vibrator.vibrate(MATE_PATTERN, -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing haptic vibration", e)
        }
    }

    /**
     * Automatically determines and plays appropriate haptic feedback based on evaluation result
     */
    fun onEngineResultReceived(
        bestMove: String,
        evalCentipawns: Int?,
        mateInMoves: Int?,
        isBlunderRisk: Boolean = false
    ) {
        when {
            mateInMoves != null && mateInMoves > 0 -> {
                triggerHaptic(HapticPattern.MATE_IN_SIGHT)
            }
            isBlunderRisk || (mateInMoves != null && mateInMoves < 0) || (evalCentipawns != null && evalCentipawns < -250) -> {
                triggerHaptic(HapticPattern.BLUNDER_WARNING)
            }
            evalCentipawns != null && Math.abs(evalCentipawns) >= 150 -> {
                triggerHaptic(HapticPattern.TACTICAL_MOVE)
            }
            else -> {
                triggerHaptic(HapticPattern.STANDARD_MOVE)
            }
        }
    }

    fun cancel() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling vibration", e)
        }
    }
}
