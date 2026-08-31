package com.chessbeater.engine

import android.content.Context
import android.util.Log
import kotlin.random.Random

/**
 * Sprint 64: Anti-Cheat Humanization Engine with MultiPV Candidate Selection & Blunder Guard.
 */
object HumanizationEngine {

    private const val TAG = "Humanize"
    private const val PREFS_NAME = "chessbeater_humanize_prefs"

    // Settings Keys
    private const val KEY_HUMANIZE_ENABLED = "humanize_enabled"
    private const val KEY_HUMANIZE_LEVEL = "humanize_level"
    private const val KEY_BLUNDER_GUARD = "blunder_guard_enabled"
    private const val KEY_NATURAL_DELAY = "natural_delay_enabled"

    // Runtime state (with defaults)
    var isHumanizeEnabled: Boolean = true
    var humanizeLevel: Int = 6
    var isBlunderGuardEnabled: Boolean = true
    var isNaturalDelayEnabled: Boolean = true

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isHumanizeEnabled = prefs.getBoolean(KEY_HUMANIZE_ENABLED, true)
        humanizeLevel = prefs.getInt(KEY_HUMANIZE_LEVEL, 6)
        isBlunderGuardEnabled = prefs.getBoolean(KEY_BLUNDER_GUARD, true)
        isNaturalDelayEnabled = prefs.getBoolean(KEY_NATURAL_DELAY, true)
        Log.d(TAG, "Initialized: enabled=$isHumanizeEnabled, level=$humanizeLevel, blunderGuard=$isBlunderGuardEnabled, naturalDelay=$isNaturalDelayEnabled")
    }

    fun saveSettings(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_HUMANIZE_ENABLED, isHumanizeEnabled)
            .putInt(KEY_HUMANIZE_LEVEL, humanizeLevel)
            .putBoolean(KEY_BLUNDER_GUARD, isBlunderGuardEnabled)
            .putBoolean(KEY_NATURAL_DELAY, isNaturalDelayEnabled)
            .apply()
    }

    fun getLevelDescription(level: Int): String {
        return when (level) {
            0 -> "Level 0: Nonaktif (100% Engine Bestmove, Akurasi ~99%)"
            1, 2 -> "Level $level: Master Konsisten (Akurasi ~95%, 90% T1)"
            3, 4, 5 -> "Level $level: Sangat Kuat (Akurasi ~88%, 80% T1)"
            6 -> "Level 6: Rekomendasi (Akurasi ~82%, Aman dari Anti-Cheat)"
            7, 8 -> "Level $level: Natural Club Player (Akurasi ~75%, 65% T1)"
            9, 10 -> "Level $level: Sangat Humanis (Akurasi ~65%, 50% T1)"
            else -> "Level $level: Kustom"
        }
    }

    data class MoveCandidate(
        val uciMove: String,
        val scoreCp: Int = 0,
        val pvIndex: Int = 1
    )

    /**
     * Memilih langkah dari daftar kandidat MultiPV berdasarkan tingkat humanisasi & Blunder Guard.
     */
    fun selectHumanizedMove(candidates: List<MoveCandidate>): String? {
        if (candidates.isEmpty()) return null
        if (!isHumanizeEnabled || humanizeLevel <= 0 || candidates.size == 1) {
            return candidates[0].uciMove
        }

        val top1 = candidates[0]
        val top2 = candidates.getOrNull(1)
        val top3 = candidates.getOrNull(2)

        // Filter dengan Blunder Guard (Maksimal penurunan cp: 45 cp)
        val validT2 = if (top2 != null) {
            if (!isBlunderGuardEnabled || (top1.scoreCp - top2.scoreCp <= 45)) top2 else null
        } else null

        val validT3 = if (top3 != null) {
            if (!isBlunderGuardEnabled || (top1.scoreCp - top3.scoreCp <= 45)) top3 else null
        } else null

        // Tentukan probabilitas berdasarkan level (0..10)
        // Level 6 (Default): 75% T1, 20% T2, 5% T3
        // Level 10: 50% T1, 35% T2, 15% T3
        val roll = Random.nextInt(100) // 0..99
        val (pT1, pT2) = when {
            humanizeLevel >= 9 -> Pair(50, 35) // 50% T1, 35% T2, 15% T3
            humanizeLevel >= 6 -> Pair(75, 20) // 75% T1, 20% T2, 5% T3
            humanizeLevel >= 3 -> Pair(85, 12) // 85% T1, 12% T2, 3% T3
            else -> Pair(92, 7)                // 92% T1, 7% T2, 1% T3
        }

        val chosenMove = when {
            roll < pT1 -> {
                Log.d(TAG, "🎯 Memilih Top 1 Bestmove (Roll $roll < $pT1): ${top1.uciMove}")
                top1.uciMove
            }
            roll < (pT1 + pT2) && validT2 != null -> {
                Log.d(TAG, "🎭 Memilih Humanized Top 2 Move (Roll $roll, delta: ${top1.scoreCp - validT2.scoreCp}cp): ${validT2.uciMove}")
                validT2.uciMove
            }
            validT3 != null -> {
                Log.d(TAG, "🎭 Memilih Humanized Top 3 Move (Roll $roll, delta: ${top1.scoreCp - validT3.scoreCp}cp): ${validT3.uciMove}")
                validT3.uciMove
            }
            validT2 != null -> {
                Log.d(TAG, "🎭 Fallback ke Top 2 Move: ${validT2.uciMove}")
                validT2.uciMove
            }
            else -> {
                Log.d(TAG, "🎯 Blunder Guard mengembalikan ke Top 1: ${top1.uciMove}")
                top1.uciMove
            }
        }

        return chosenMove
    }
}
