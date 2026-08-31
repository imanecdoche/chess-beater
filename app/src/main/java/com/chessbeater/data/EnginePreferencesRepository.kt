package com.chessbeater.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.chessbeater.engine.models.EngineConfig
import com.chessbeater.engine.models.EngineType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chess_beater_preferences")

data class AppUserPreferences(
    val engineType: EngineType = EngineType.STOCKFISH,
    val powerPercentage: Int = 100,
    val showCanvasArrow: Boolean = true,
    val showFloatingHud: Boolean = true,
    val isStealthToastMode: Boolean = false,
    val isHapticAlertEnabled: Boolean = true,
    val targetApp: com.chessbeater.vision.models.ChessAppTarget = com.chessbeater.vision.models.ChessAppTarget.CHESS_COM,
    val selectedAppPackage: String = "",
    val selectedAppName: String = "",
    val autoLaunchTargetApp: Boolean = true,
    val showInteractiveMiniBoard: Boolean = false,
    val miniBoardSizeDp: Int = 220,
    val miniBoardOpacity: Float = 0.94f,
    val miniBoardPosX: Int = 40,
    val miniBoardPosY: Int = 180,
    val isGhostMode: Boolean = false,
    val isTouchForwardingEnabled: Boolean = true,
    val isPiecesHiddenInGhostMode: Boolean = true
) {
    fun toEngineConfig(): EngineConfig = EngineConfig(engineType, powerPercentage)
}

/**
 * DataStore Repository managing persistent user configuration
 */
class EnginePreferencesRepository(private val context: Context) {

    private val dataStore = context.dataStore

    companion object {
        val KEY_ENGINE_TYPE = stringPreferencesKey("key_engine_type")
        val KEY_POWER_PERCENTAGE = intPreferencesKey("key_power_percentage")
        val KEY_SHOW_CANVAS_ARROW = booleanPreferencesKey("key_show_canvas_arrow")
        val KEY_SHOW_FLOATING_HUD = booleanPreferencesKey("key_show_floating_hud")
        val KEY_STEALTH_TOAST_MODE = booleanPreferencesKey("key_stealth_toast_mode")
        val KEY_HAPTIC_ENABLED = booleanPreferencesKey("key_haptic_enabled")
        val KEY_TARGET_APP = stringPreferencesKey("key_target_app")
        val KEY_SELECTED_APP_PACKAGE = stringPreferencesKey("key_selected_app_package")
        val KEY_SELECTED_APP_NAME = stringPreferencesKey("key_selected_app_name")
        val KEY_AUTO_LAUNCH_TARGET_APP = booleanPreferencesKey("key_auto_launch_target_app")
        val KEY_SHOW_MINI_BOARD = booleanPreferencesKey("key_show_mini_board")
        val KEY_MINI_BOARD_SIZE_DP = intPreferencesKey("key_mini_board_size_dp")
        val KEY_MINI_BOARD_OPACITY = floatPreferencesKey("key_mini_board_opacity")
        val KEY_MINI_BOARD_POS_X = intPreferencesKey("key_mini_board_pos_x")
        val KEY_MINI_BOARD_POS_Y = intPreferencesKey("key_mini_board_pos_y")
        val KEY_IS_GHOST_MODE = booleanPreferencesKey("key_is_ghost_mode")
        val KEY_IS_TOUCH_FORWARDING = booleanPreferencesKey("key_is_touch_forwarding")
        val KEY_IS_PIECES_HIDDEN_GHOST = booleanPreferencesKey("key_is_pieces_hidden_ghost")
    }

    val userPreferencesFlow: Flow<AppUserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val engineName = preferences[KEY_ENGINE_TYPE] ?: EngineType.STOCKFISH.name
            val engineType = try {
                EngineType.valueOf(engineName)
            } catch (e: Exception) {
                EngineType.STOCKFISH
            }

            val appName = preferences[KEY_TARGET_APP] ?: com.chessbeater.vision.models.ChessAppTarget.CHESS_COM.name
            val targetApp = try {
                com.chessbeater.vision.models.ChessAppTarget.valueOf(appName)
            } catch (e: Exception) {
                com.chessbeater.vision.models.ChessAppTarget.CHESS_COM
            }

            val power = preferences[KEY_POWER_PERCENTAGE] ?: 100
            val showArrow = preferences[KEY_SHOW_CANVAS_ARROW] ?: true
            val showHud = preferences[KEY_SHOW_FLOATING_HUD] ?: true
            val stealthToast = preferences[KEY_STEALTH_TOAST_MODE] ?: false
            val haptic = preferences[KEY_HAPTIC_ENABLED] ?: true
            val selectedPackage = preferences[KEY_SELECTED_APP_PACKAGE] ?: ""
            val selectedName = preferences[KEY_SELECTED_APP_NAME] ?: ""
            val autoLaunch = preferences[KEY_AUTO_LAUNCH_TARGET_APP] ?: true
            val showMiniBoard = preferences[KEY_SHOW_MINI_BOARD] ?: false
            val miniSize = preferences[KEY_MINI_BOARD_SIZE_DP] ?: 220
            val miniOpacity = preferences[KEY_MINI_BOARD_OPACITY] ?: 0.94f
            val miniX = preferences[KEY_MINI_BOARD_POS_X] ?: 40
            val miniY = preferences[KEY_MINI_BOARD_POS_Y] ?: 180
            val isGhost = preferences[KEY_IS_GHOST_MODE] ?: false
            val isTouchForward = preferences[KEY_IS_TOUCH_FORWARDING] ?: true
            val isPiecesHiddenGhost = preferences[KEY_IS_PIECES_HIDDEN_GHOST] ?: true

            AppUserPreferences(
                engineType = engineType,
                powerPercentage = power,
                showCanvasArrow = showArrow,
                showFloatingHud = showHud,
                isStealthToastMode = stealthToast,
                isHapticAlertEnabled = haptic,
                targetApp = targetApp,
                selectedAppPackage = selectedPackage,
                selectedAppName = selectedName,
                autoLaunchTargetApp = autoLaunch,
                showInteractiveMiniBoard = showMiniBoard,
                miniBoardSizeDp = miniSize,
                miniBoardOpacity = miniOpacity,
                miniBoardPosX = miniX,
                miniBoardPosY = miniY,
                isGhostMode = isGhost,
                isTouchForwardingEnabled = isTouchForward,
                isPiecesHiddenInGhostMode = isPiecesHiddenGhost
            )
        }

    suspend fun updateEngineType(type: EngineType) {
        dataStore.edit { preferences ->
            preferences[KEY_ENGINE_TYPE] = type.name
        }
    }

    suspend fun updatePowerPercentage(power: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_POWER_PERCENTAGE] = power.coerceIn(10, 100)
        }
    }

    suspend fun updateCanvasArrow(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_SHOW_CANVAS_ARROW] = enabled
        }
    }

    suspend fun updateFloatingHud(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_SHOW_FLOATING_HUD] = enabled
        }
    }

    suspend fun updateStealthToast(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_STEALTH_TOAST_MODE] = enabled
        }
    }

    suspend fun updateHapticAlert(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_HAPTIC_ENABLED] = enabled
        }
    }

    suspend fun updateTargetApp(target: com.chessbeater.vision.models.ChessAppTarget) {
        dataStore.edit { preferences ->
            preferences[KEY_TARGET_APP] = target.name
        }
    }

    suspend fun updateSelectedApp(packageName: String, appName: String) {
        dataStore.edit { preferences ->
            preferences[KEY_SELECTED_APP_PACKAGE] = packageName
            preferences[KEY_SELECTED_APP_NAME] = appName
        }
    }

    suspend fun updateAutoLaunch(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_AUTO_LAUNCH_TARGET_APP] = enabled
        }
    }

    suspend fun updateMiniBoardVisibility(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_SHOW_MINI_BOARD] = show
        }
    }

    suspend fun updateMiniBoardSize(sizeDp: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_MINI_BOARD_SIZE_DP] = sizeDp
        }
    }

    suspend fun updateMiniBoardOpacity(opacity: Float) {
        dataStore.edit { preferences ->
            preferences[KEY_MINI_BOARD_OPACITY] = opacity
        }
    }

    suspend fun updateMiniBoardPosition(x: Int, y: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_MINI_BOARD_POS_X] = x
            preferences[KEY_MINI_BOARD_POS_Y] = y
        }
    }

    suspend fun updateGhostMode(isGhost: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_IS_GHOST_MODE] = isGhost
        }
    }

    suspend fun updateTouchForwarding(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_IS_TOUCH_FORWARDING] = enabled
        }
    }

    suspend fun updatePiecesHiddenInGhostMode(hidden: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_IS_PIECES_HIDDEN_GHOST] = hidden
        }
    }
}
