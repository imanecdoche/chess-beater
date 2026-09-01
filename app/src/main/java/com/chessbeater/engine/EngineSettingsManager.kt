package com.chessbeater.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.chessbeater.utils.PrefKeys

/**
 * Centralized Engine Settings and Target ELO Power Management.
 */
object EngineSettingsManager {
    const val PREFS_NAME = PrefKeys.PREF_NAME
    const val PREFS_NAME_ALT = "chessbeater_visual_prefs"

    const val KEY_ENGINE_TARGET_ELO = PrefKeys.KEY_ENGINE_ELO
    const val KEY_ENGINE_BULLET_MODE = PrefKeys.KEY_BULLET_MODE
    const val KEY_AUTO_HIDE_ENABLED = PrefKeys.KEY_AUTO_HIDE_ENABLED
    const val KEY_AUTO_HIDE_DELAY_SEC = PrefKeys.KEY_AUTO_HIDE_SEC
    const val KEY_AUTO_SHOW_ENABLED = PrefKeys.KEY_AUTO_SHOW_ENABLED
    const val KEY_AUTO_SHOW_DELAY_SEC = PrefKeys.KEY_AUTO_SHOW_SEC

    // Legacy aliases
    const val KEY_TARGET_ELO = "target_elo"
    const val KEY_MAX_ELO_RATING = "max_elo_rating"
    const val KEY_ELO_RATING = "elo_rating"

    const val DEFAULT_TARGET_ELO = 2800
    const val DEFAULT_BULLET_MODE = false
    const val DEFAULT_AUTO_HIDE_ENABLED = false
    const val DEFAULT_AUTO_HIDE_DELAY_SEC = 3.0f
    const val DEFAULT_AUTO_SHOW_ENABLED = false
    const val DEFAULT_AUTO_SHOW_DELAY_SEC = 1.5f

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getTargetElo(context: Context): Int {
        return try {
            val prefs = getPrefs(context)
            val elo = if (prefs.contains(KEY_ENGINE_TARGET_ELO)) {
                prefs.getInt(KEY_ENGINE_TARGET_ELO, DEFAULT_TARGET_ELO)
            } else if (prefs.contains(KEY_TARGET_ELO)) {
                prefs.getInt(KEY_TARGET_ELO, DEFAULT_TARGET_ELO)
            } else if (prefs.contains(KEY_MAX_ELO_RATING)) {
                prefs.getInt(KEY_MAX_ELO_RATING, DEFAULT_TARGET_ELO)
            } else if (prefs.contains(KEY_ELO_RATING)) {
                prefs.getInt(KEY_ELO_RATING, DEFAULT_TARGET_ELO)
            } else {
                DEFAULT_TARGET_ELO
            }
            elo.coerceIn(800, 3500)
        } catch (e: Exception) {
            Log.w("EngineSettingsManager", "Error reading target_elo, fallback to $DEFAULT_TARGET_ELO", e)
            DEFAULT_TARGET_ELO
        }
    }

    fun isBulletMode(context: Context): Boolean {
        return try {
            getPrefs(context).getBoolean(KEY_ENGINE_BULLET_MODE, DEFAULT_BULLET_MODE)
        } catch (e: Exception) {
            DEFAULT_BULLET_MODE
        }
    }

    fun isAutoHideEnabled(context: Context): Boolean {
        return try {
            getPrefs(context).getBoolean(KEY_AUTO_HIDE_ENABLED, DEFAULT_AUTO_HIDE_ENABLED)
        } catch (e: Exception) {
            DEFAULT_AUTO_HIDE_ENABLED
        }
    }

    fun getAutoHideDelaySec(context: Context): Float {
        return try {
            val p = getPrefs(context)
            if (p.contains(KEY_AUTO_HIDE_DELAY_SEC)) {
                p.getFloat(KEY_AUTO_HIDE_DELAY_SEC, DEFAULT_AUTO_HIDE_DELAY_SEC).coerceIn(0.5f, 30.0f)
            } else {
                DEFAULT_AUTO_HIDE_DELAY_SEC
            }
        } catch (e: Exception) {
            DEFAULT_AUTO_HIDE_DELAY_SEC
        }
    }

    fun isAutoShowEnabled(context: Context): Boolean {
        return try {
            getPrefs(context).getBoolean(KEY_AUTO_SHOW_ENABLED, DEFAULT_AUTO_SHOW_ENABLED)
        } catch (e: Exception) {
            DEFAULT_AUTO_SHOW_ENABLED
        }
    }

    fun getAutoShowDelaySec(context: Context): Float {
        return try {
            val p = getPrefs(context)
            if (p.contains(KEY_AUTO_SHOW_DELAY_SEC)) {
                p.getFloat(KEY_AUTO_SHOW_DELAY_SEC, DEFAULT_AUTO_SHOW_DELAY_SEC).coerceIn(0.5f, 30.0f)
            } else {
                DEFAULT_AUTO_SHOW_DELAY_SEC
            }
        } catch (e: Exception) {
            DEFAULT_AUTO_SHOW_DELAY_SEC
        }
    }

    fun saveTargetElo(context: Context, elo: Int) {
        val clamped = elo.coerceIn(800, 3500)
        try {
            getPrefs(context).edit()
                .putInt(KEY_ENGINE_TARGET_ELO, clamped)
                .putInt(KEY_TARGET_ELO, clamped)
                .putInt(KEY_MAX_ELO_RATING, clamped)
                .putInt(KEY_ELO_RATING, clamped)
                .apply()
            context.getSharedPreferences(PREFS_NAME_ALT, Context.MODE_PRIVATE).edit()
                .putInt(KEY_ENGINE_TARGET_ELO, clamped)
                .putInt(KEY_TARGET_ELO, clamped)
                .apply()
            Log.d("EngineSettingsManager", "💾 Saved target_elo: $clamped")
            com.chessbeater.utils.AppLogger.log("CONFIG", "🎯 Target ELO diubah: $clamped")
        } catch (e: Exception) {
            Log.e("EngineSettingsManager", "Error saving target_elo: $clamped", e)
        }
    }

    fun saveBulletMode(context: Context, enabled: Boolean) {
        try {
            getPrefs(context).edit().putBoolean(KEY_ENGINE_BULLET_MODE, enabled).apply()
            context.getSharedPreferences(PREFS_NAME_ALT, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENGINE_BULLET_MODE, enabled).apply()
            if (enabled) {
                saveAutoHideDelaySec(context, 1.0f)
                saveAutoShowDelaySec(context, 1.0f)
            }
            Log.d("EngineSettingsManager", "💾 Saved bullet_mode: $enabled")
            com.chessbeater.utils.AppLogger.log("CONFIG", "⚡ Bullet Mode: $enabled")
        } catch (e: Exception) {
            Log.e("EngineSettingsManager", "Error saving bullet_mode", e)
        }
    }

    fun saveAutoHideEnabled(context: Context, enabled: Boolean) {
        try {
            getPrefs(context).edit().putBoolean(KEY_AUTO_HIDE_ENABLED, enabled).apply()
            context.getSharedPreferences(PREFS_NAME_ALT, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_HIDE_ENABLED, enabled).apply()
            com.chessbeater.utils.AppLogger.log("CONFIG", "⏳ Auto-Hide Enabled: $enabled")
        } catch (e: Exception) {}
    }

    fun saveAutoHideDelaySec(context: Context, delaySec: Float) {
        val clamped = (Math.round(delaySec * 2f) / 2f).coerceIn(0.5f, 30.0f)
        try {
            getPrefs(context).edit().putFloat(KEY_AUTO_HIDE_DELAY_SEC, clamped).apply()
            context.getSharedPreferences(PREFS_NAME_ALT, Context.MODE_PRIVATE).edit().putFloat(KEY_AUTO_HIDE_DELAY_SEC, clamped).apply()
            com.chessbeater.utils.AppLogger.log("CONFIG", "⏱️ Auto-Hide Delay diubah: ${clamped}s")
        } catch (e: Exception) {}
    }

    fun saveAutoShowEnabled(context: Context, enabled: Boolean) {
        try {
            getPrefs(context).edit().putBoolean(KEY_AUTO_SHOW_ENABLED, enabled).apply()
            context.getSharedPreferences(PREFS_NAME_ALT, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_SHOW_ENABLED, enabled).apply()
            com.chessbeater.utils.AppLogger.log("CONFIG", "✨ Auto-Show Enabled: $enabled")
        } catch (e: Exception) {}
    }

    fun saveAutoShowDelaySec(context: Context, delaySec: Float) {
        val clamped = (Math.round(delaySec * 2f) / 2f).coerceIn(0.5f, 30.0f)
        try {
            getPrefs(context).edit().putFloat(KEY_AUTO_SHOW_DELAY_SEC, clamped).apply()
            context.getSharedPreferences(PREFS_NAME_ALT, Context.MODE_PRIVATE).edit().putFloat(KEY_AUTO_SHOW_DELAY_SEC, clamped).apply()
            com.chessbeater.utils.AppLogger.log("CONFIG", "⏱️ Auto-Show Delay diubah: ${clamped}s")
        } catch (e: Exception) {}
    }
}
