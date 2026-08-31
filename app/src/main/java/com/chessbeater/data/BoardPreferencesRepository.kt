package com.chessbeater.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Geometric board overlay coordinates and dimension.
 */
data class BoardPosition(
    val x: Int,
    val y: Int,
    val sizePx: Int
)

/**
 * Granular Visual & Transparency configurations per element (Sprint 35).
 */
data class BoardVisualPreferences(
    val gridAlpha: Float = 0.15f,            // 0.0f .. 1.0f (Default: 15% Ghost Grid)
    val pieceAlpha: Float = 1.0f,            // 0.0f .. 1.0f (Default: 100% Solid)
    val highlightAlpha: Float = 0.65f,       // 0.0f .. 1.0f (Default: 65%)
    val arrowAlpha: Float = 0.90f,           // 0.0f .. 1.0f (Default: 90%)
    val floatingEyeAlpha: Float = 0.85f,     // 0.0f .. 1.0f (Default: 85%)
    val autoHideDelaySec: Int = -1,          // -1 = Off, 0 = Instant 0s, 1..10 = Delay Sec
    val eyeSizeDp: Int = 72,                 // Default: 72dp (Large)
    val isClickThroughMode: Boolean = false,
    val isAutoShowEnabled: Boolean = false,
    val autoShowDelaySec: Int = 2,           // 1..10 Sec (Default: 2s)
    val isAutoDetectionEnabled: Boolean = false, // Default: Off (Manual by default)
    val eloRating: Int = 2200                // Range: 1350 .. 2850 (Default: 2200 Master)
) {
    val isAutoHideEnabled: Boolean get() = autoHideDelaySec >= 0
}

/**
 * DataStore repository for persistent board overlay positions, visual styles, and durations.
 */
class BoardPreferencesRepository(context: Context) {

    private val dataStore = context.dataStore

    companion object {
        val KEY_LAST_POS_X = intPreferencesKey("key_last_board_pos_x")
        val KEY_LAST_POS_Y = intPreferencesKey("key_last_board_pos_y")
        val KEY_LAST_SIZE_PX = intPreferencesKey("key_last_board_size_px")
        val KEY_ARROW_DURATION_MS = longPreferencesKey("key_arrow_duration_ms")
        val KEY_GRID_ALPHA_FLOAT = androidx.datastore.preferences.core.floatPreferencesKey("key_grid_alpha_float")
        val KEY_PIECE_ALPHA_FLOAT = androidx.datastore.preferences.core.floatPreferencesKey("key_piece_alpha_float")
        val KEY_HIGHLIGHT_ALPHA_FLOAT = androidx.datastore.preferences.core.floatPreferencesKey("key_highlight_alpha_float")
        val KEY_ARROW_ALPHA_FLOAT = androidx.datastore.preferences.core.floatPreferencesKey("key_arrow_alpha_float")
        val KEY_FLOATING_EYE_ALPHA_FLOAT = androidx.datastore.preferences.core.floatPreferencesKey("key_floating_eye_alpha_float")
        val KEY_AUTO_HIDE_DELAY_SEC = intPreferencesKey("key_auto_hide_delay_sec")
        val KEY_EYE_SIZE_DP = intPreferencesKey("key_eye_size_dp")
        val KEY_IS_CLICK_THROUGH_MODE = booleanPreferencesKey("key_is_click_through_mode")
        val KEY_IS_AUTO_SHOW_ENABLED = booleanPreferencesKey("key_is_auto_show_enabled")
        val KEY_AUTO_SHOW_DELAY_SEC = intPreferencesKey("key_auto_show_delay_sec")
        val KEY_IS_AUTO_DETECTION_ENABLED = booleanPreferencesKey("key_is_auto_detection_enabled")
        val KEY_ELO_RATING = intPreferencesKey("key_engine_elo_rating")
    }

    fun getLastBoardPosition(): Flow<BoardPosition?> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val x = preferences[KEY_LAST_POS_X] ?: return@map null
                val y = preferences[KEY_LAST_POS_Y] ?: return@map null
                val size = preferences[KEY_LAST_SIZE_PX] ?: return@map null
                if (size > 0) BoardPosition(x, y, size) else null
            }

    suspend fun saveLastBoardPosition(x: Int, y: Int, sizePx: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_LAST_POS_X] = x
            preferences[KEY_LAST_POS_Y] = y
            preferences[KEY_LAST_SIZE_PX] = sizePx
        }
    }

    fun getArrowDurationMs(): Flow<Long> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_ARROW_DURATION_MS] ?: 1000L
            }

    suspend fun saveArrowDurationMs(durationMs: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_ARROW_DURATION_MS] = durationMs
        }
    }

    fun getVisualPreferences(): Flow<BoardVisualPreferences> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                BoardVisualPreferences(
                    gridAlpha = preferences[KEY_GRID_ALPHA_FLOAT] ?: 0.15f,
                    pieceAlpha = preferences[KEY_PIECE_ALPHA_FLOAT] ?: 1.0f,
                    highlightAlpha = preferences[KEY_HIGHLIGHT_ALPHA_FLOAT] ?: 0.65f,
                    arrowAlpha = preferences[KEY_ARROW_ALPHA_FLOAT] ?: 0.90f,
                    floatingEyeAlpha = preferences[KEY_FLOATING_EYE_ALPHA_FLOAT] ?: 0.85f,
                    autoHideDelaySec = preferences[KEY_AUTO_HIDE_DELAY_SEC] ?: -1,
                    eyeSizeDp = preferences[KEY_EYE_SIZE_DP] ?: 72,
                    isClickThroughMode = preferences[KEY_IS_CLICK_THROUGH_MODE] ?: false,
                    isAutoShowEnabled = preferences[KEY_IS_AUTO_SHOW_ENABLED] ?: false,
                    autoShowDelaySec = preferences[KEY_AUTO_SHOW_DELAY_SEC] ?: 2,
                    isAutoDetectionEnabled = preferences[KEY_IS_AUTO_DETECTION_ENABLED] ?: false,
                    eloRating = preferences[KEY_ELO_RATING] ?: 2200
                )
            }

    suspend fun saveVisualPreferences(prefs: BoardVisualPreferences) {
        dataStore.edit { preferences ->
            preferences[KEY_GRID_ALPHA_FLOAT] = prefs.gridAlpha.coerceIn(0.0f, 1.0f)
            preferences[KEY_PIECE_ALPHA_FLOAT] = prefs.pieceAlpha.coerceIn(0.0f, 1.0f)
            preferences[KEY_HIGHLIGHT_ALPHA_FLOAT] = prefs.highlightAlpha.coerceIn(0.0f, 1.0f)
            preferences[KEY_ARROW_ALPHA_FLOAT] = prefs.arrowAlpha.coerceIn(0.0f, 1.0f)
            preferences[KEY_FLOATING_EYE_ALPHA_FLOAT] = prefs.floatingEyeAlpha.coerceIn(0.0f, 1.0f)
            preferences[KEY_AUTO_HIDE_DELAY_SEC] = prefs.autoHideDelaySec.coerceIn(-1, 10)
            preferences[KEY_EYE_SIZE_DP] = prefs.eyeSizeDp
            preferences[KEY_IS_CLICK_THROUGH_MODE] = prefs.isClickThroughMode
            preferences[KEY_IS_AUTO_SHOW_ENABLED] = prefs.isAutoShowEnabled
            preferences[KEY_AUTO_SHOW_DELAY_SEC] = prefs.autoShowDelaySec.coerceIn(1, 10)
            preferences[KEY_IS_AUTO_DETECTION_ENABLED] = prefs.isAutoDetectionEnabled
            preferences[KEY_ELO_RATING] = prefs.eloRating.coerceIn(1320, 3190)
        }
    }

    fun getEloRating(): Flow<Int> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_ELO_RATING] ?: 2200
            }

    suspend fun saveEloRating(elo: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_ELO_RATING] = elo.coerceIn(1320, 3190)
        }
    }

    fun getAutoDetectionEnabled(): Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_IS_AUTO_DETECTION_ENABLED] ?: false
            }

    suspend fun saveAutoDetectionEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_IS_AUTO_DETECTION_ENABLED] = enabled
        }
    }

    suspend fun saveGridAlpha(alpha: Float) {
        dataStore.edit { preferences ->
            preferences[KEY_GRID_ALPHA_FLOAT] = alpha.coerceIn(0.0f, 1.0f)
        }
    }

    suspend fun savePieceAlpha(alpha: Float) {
        dataStore.edit { preferences ->
            preferences[KEY_PIECE_ALPHA_FLOAT] = alpha.coerceIn(0.0f, 1.0f)
        }
    }

    suspend fun saveHighlightAlpha(alpha: Float) {
        dataStore.edit { preferences ->
            preferences[KEY_HIGHLIGHT_ALPHA_FLOAT] = alpha.coerceIn(0.0f, 1.0f)
        }
    }

    suspend fun saveArrowAlpha(alpha: Float) {
        dataStore.edit { preferences ->
            preferences[KEY_ARROW_ALPHA_FLOAT] = alpha.coerceIn(0.0f, 1.0f)
        }
    }

    suspend fun saveFloatingEyeAlpha(alpha: Float) {
        dataStore.edit { preferences ->
            preferences[KEY_FLOATING_EYE_ALPHA_FLOAT] = alpha.coerceIn(0.0f, 1.0f)
        }
    }

    suspend fun saveAutoHideDelaySec(delaySec: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_AUTO_HIDE_DELAY_SEC] = delaySec.coerceIn(-1, 10)
        }
    }

    suspend fun saveEyeSizeDp(sizeDp: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_EYE_SIZE_DP] = sizeDp
        }
    }

    suspend fun saveClickThroughMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_IS_CLICK_THROUGH_MODE] = enabled
        }
    }
}
