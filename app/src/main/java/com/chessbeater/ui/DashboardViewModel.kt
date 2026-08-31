package com.chessbeater.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chessbeater.data.AppUserPreferences
import com.chessbeater.data.EnginePreferencesRepository
import com.chessbeater.engine.models.EngineConfig
import com.chessbeater.engine.models.EngineType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val selectedEngine: EngineType = EngineType.STOCKFISH,
    val targetApp: com.chessbeater.vision.models.ChessAppTarget = com.chessbeater.vision.models.ChessAppTarget.CHESS_COM,
    val selectedAppPackage: String = "",
    val selectedAppName: String = "",
    val autoLaunchTargetApp: Boolean = true,
    val showInteractiveMiniBoard: Boolean = false,
    val miniBoardSizeDp: Int = 220,
    val miniBoardOpacity: Float = 0.94f,
    val isGhostMode: Boolean = false,
    val isTouchForwardingEnabled: Boolean = true,
    val powerPercentage: Int = 100,
    val showCanvasArrow: Boolean = true,
    val showFloatingHud: Boolean = true,
    val isStealthToastMode: Boolean = false,
    val isHapticAlertEnabled: Boolean = true,
    val isQuickAlignmentEnabled: Boolean = true,
    val isSaveSessionLogsEnabled: Boolean = false,
    val savedLogsList: List<com.chessbeater.logging.SessionLogInfo> = emptyList(),
    // Dual service status
    val isVisionServiceRunning: Boolean = false,
    val isMiniBoardServiceRunning: Boolean = false,
    val currentFen: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    val bestMove: String? = null,
    val evalScore: String = "+0.00",
    val searchDepth: Int = 20,
    val moveTimeMs: Int = 500,
    val latencyMs: Long = 0L,
    val fps: Float = 0.0f
) {
    val isServiceRunning: Boolean get() = isVisionServiceRunning || isMiniBoardServiceRunning

    val estimatedElo: Int get() = 800 + ((powerPercentage / 100.0) * 2700).toInt()

    val eloRankTitle: String get() = when {
        estimatedElo >= 3200 -> "Superhuman GM"
        estimatedElo >= 2800 -> "World Champion"
        estimatedElo >= 2500 -> "Grandmaster (GM)"
        estimatedElo >= 2300 -> "International Master"
        estimatedElo >= 2000 -> "Candidate Master (CM)"
        estimatedElo >= 1600 -> "Intermediate Club Player"
        estimatedElo >= 1200 -> "Casual Player"
        else -> "Beginner (800 Elo)"
    }
}

class DashboardViewModel(
    private val app: Application,
    private val preferencesRepository: EnginePreferencesRepository
) : AndroidViewModel(app) {

    constructor(application: Application) : this(application, EnginePreferencesRepository(application))

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        val loggingEnabled = com.chessbeater.logging.SessionLogger.isLoggingEnabled(app)
        val logs = com.chessbeater.logging.SessionLogger.getAllLogs(app)
        val quickAlignEnabled = app.getSharedPreferences("chessbeater_visual_prefs", android.content.Context.MODE_PRIVATE)
            .getBoolean("quick_alignment_enabled", true)
        _uiState.update { it.copy(isSaveSessionLogsEnabled = loggingEnabled, savedLogsList = logs, isQuickAlignmentEnabled = quickAlignEnabled) }

        viewModelScope.launch {
            preferencesRepository.userPreferencesFlow.collect { prefs ->
                _uiState.update { current ->
                    current.copy(
                        selectedEngine = prefs.engineType,
                        targetApp = prefs.targetApp,
                        selectedAppPackage = prefs.selectedAppPackage,
                        selectedAppName = prefs.selectedAppName,
                        autoLaunchTargetApp = prefs.autoLaunchTargetApp,
                        showInteractiveMiniBoard = prefs.showInteractiveMiniBoard,
                        miniBoardSizeDp = prefs.miniBoardSizeDp,
                        miniBoardOpacity = prefs.miniBoardOpacity,
                        isGhostMode = prefs.isGhostMode,
                        isTouchForwardingEnabled = prefs.isTouchForwardingEnabled,
                        powerPercentage = prefs.powerPercentage,
                        showCanvasArrow = prefs.showCanvasArrow,
                        showFloatingHud = prefs.showFloatingHud,
                        isStealthToastMode = prefs.isStealthToastMode,
                        isHapticAlertEnabled = prefs.isHapticAlertEnabled,
                        searchDepth = calculateDepth(prefs.engineType, prefs.powerPercentage),
                        moveTimeMs = calculateMoveTime(prefs.engineType, prefs.powerPercentage)
                    )
                }
            }
        }
    }

    fun selectEngine(engineType: EngineType) = viewModelScope.launch { preferencesRepository.updateEngineType(engineType) }
    fun selectTargetApp(targetApp: com.chessbeater.vision.models.ChessAppTarget) = viewModelScope.launch { preferencesRepository.updateTargetApp(targetApp) }
    fun selectInstalledApp(appInfo: com.chessbeater.data.InstalledAppInfo) = viewModelScope.launch {
        val inferredTarget = com.chessbeater.data.InstalledAppScanner.mapPackageToTarget(appInfo.packageName)
        preferencesRepository.updateTargetApp(inferredTarget)
        preferencesRepository.updateSelectedApp(appInfo.packageName, appInfo.appName)
    }
    fun toggleAutoLaunch(autoLaunch: Boolean) = viewModelScope.launch { preferencesRepository.updateAutoLaunch(autoLaunch) }
    fun toggleInteractiveMiniBoard(show: Boolean) = viewModelScope.launch { preferencesRepository.updateMiniBoardVisibility(show) }
    fun toggleGhostMode(enabled: Boolean) = viewModelScope.launch { preferencesRepository.updateGhostMode(enabled) }
    fun toggleTouchForwarding(enabled: Boolean) = viewModelScope.launch { preferencesRepository.updateTouchForwarding(enabled) }
    fun setPowerPercentage(power: Int) = viewModelScope.launch { preferencesRepository.updatePowerPercentage(power) }
    fun toggleCanvasArrow(show: Boolean) = viewModelScope.launch { preferencesRepository.updateCanvasArrow(show) }
    fun toggleFloatingHud(show: Boolean) = viewModelScope.launch { preferencesRepository.updateFloatingHud(show) }
    fun toggleStealthToastMode(stealth: Boolean) = viewModelScope.launch { preferencesRepository.updateStealthToast(stealth) }
    fun toggleHapticAlert(enabled: Boolean) = viewModelScope.launch { preferencesRepository.updateHapticAlert(enabled) }

    fun toggleQuickAlignment(enabled: Boolean) {
        app.getSharedPreferences("chessbeater_visual_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean("quick_alignment_enabled", enabled)
            .apply()
        _uiState.update { it.copy(isQuickAlignmentEnabled = enabled) }
    }

    fun toggleSaveSessionLogs(enabled: Boolean) {
        com.chessbeater.logging.SessionLogger.setLoggingEnabled(app, enabled)
        refreshSavedLogs()
    }

    fun refreshSavedLogs() {
        val loggingEnabled = com.chessbeater.logging.SessionLogger.isLoggingEnabled(app)
        val logs = com.chessbeater.logging.SessionLogger.getAllLogs(app)
        _uiState.update { it.copy(isSaveSessionLogsEnabled = loggingEnabled, savedLogsList = logs) }
    }

    fun deleteLog(file: java.io.File) {
        com.chessbeater.logging.SessionLogger.deleteLog(file)
        refreshSavedLogs()
    }

    fun clearAllLogs() {
        com.chessbeater.logging.SessionLogger.clearAllLogs(app)
        refreshSavedLogs()
    }

    fun updateVisionServiceStatus(isRunning: Boolean) {
        _uiState.update { it.copy(isVisionServiceRunning = isRunning) }
    }

    fun updateMiniBoardServiceStatus(isRunning: Boolean) {
        _uiState.update { it.copy(isMiniBoardServiceRunning = isRunning) }
    }

    fun updateServiceStatus(isRunning: Boolean) = updateVisionServiceStatus(isRunning)

    private fun calculateDepth(engineType: EngineType, powerPercentage: Int): Int = when (engineType) {
        EngineType.STOCKFISH -> 10 + ((powerPercentage / 100.0) * 15).toInt()
        EngineType.LC0_ALPHAZERO -> 50 + ((powerPercentage / 100.0) * 750).toInt()
        EngineType.DEEP_BLUE_CLASSIC -> 2 + ((powerPercentage / 100.0) * 8).toInt()
    }

    private fun calculateMoveTime(engineType: EngineType, powerPercentage: Int): Int =
        100 + ((powerPercentage / 100.0) * 900).toInt()
}
