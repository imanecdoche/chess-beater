package com.chessbeater.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.min
import com.chessbeater.data.BoardPreferencesRepository
import com.chessbeater.data.BoardVisualPreferences
import com.chessbeater.data.CalibrationPreferencesRepository
import com.chessbeater.data.CalibrationPreset
import com.chessbeater.data.EnginePreferencesRepository
import com.chessbeater.data.PresetRepository
import com.chessbeater.engine.ChessEngineService
import com.chessbeater.engine.models.EngineConfig
import com.chessbeater.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Standalone Foreground Service untuk Interactive Mini Chessboard Overlay (Sprint 21, 28, 30, 31, 32, 33, 35, & 36).
 * - Mendukung Multi-Preset Kalibrasi Papan Catur dengan Penautan Paket Game & Auto-Switching.
 * - GUI Sliders untuk Transparansi per Elemen & Auto-Hide Gerakan.
 * - Auto-Save & Auto-Restore posisi/ukuran papan terakhir dengan debouncing 500ms.
 * - Shake-to-Show Sensor Detector untuk memunculkan kembali overlay setelah disembunyikan.
 * - Kustomisasi ukuran tombol mata (56dp/72dp/88dp).
 */
class MiniBoardOverlayService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var overlayManager: OverlayManager? = null
    private val chessEngineService: ChessEngineService = ChessEngineService(dispatcher = Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    // Shake Detector & Haptic
    private var sensorManager: SensorManager? = null
    private var shakeDetector: ShakeDetector? = null
    private var vibrator: Vibrator? = null

    private var savePositionJob: Job? = null
    private var stockfishJob: Job? = null
    private var autoShowJob: Job? = null
    private var autoHideJob: Job? = null
    private var autoDetectJob: Job? = null
    private var currentHideReason = OverlayHideReason.NONE
    private val highlightDetector = com.chessbeater.vision.BoardHighlightDetector()
    private val pixelSampler = com.chessbeater.vision.BoardPixelSampler()
    // Independent Board coordinates
    private var savedBoardX: Int = 40
    private var savedBoardY: Int = 180
    private var savedBoardSizePx: Int = 600

    // Independent Floating Eye coordinates
    private var eyePosX: Int = 40
    private var eyePosY: Int = 180

    // Visual preferences state
    private var currentVisualPrefs = BoardVisualPreferences()
    private var isBoardShowing = false
    private var isEyeShowing = false

    companion object {
        private const val TAG = "MiniBoardService"
        const val NOTIFICATION_CHANNEL_ID = "chess_beater_miniboard_channel"
        const val NOTIFICATION_ID = 2002
        const val ACTION_START = "com.chessbeater.action.START_MINI_BOARD"
        const val ACTION_STOP = "com.chessbeater.action.STOP_MINI_BOARD"
        const val ACTION_START_CALIBRATION = "com.chessbeater.action.START_CALIBRATION"

        var instance: MiniBoardOverlayService? = null
            private set

        var isRunning = false
            private set
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChessBeater::MiniBoardWakeLock")
        wakeLock?.acquire(30 * 60 * 1000L)

        // Setup Vibrator & ShakeDetector
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        shakeDetector = ShakeDetector {
            onShakeDetected()
        }
        shakeDetector?.start(sensorManager)

        createNotificationChannel()
        startForegroundCompat()

        // 1. Inisialisasi engine di background
        serviceScope.launch {
            Log.d(TAG, "Memulai inisialisasi ChessEngineService di background...")
            chessEngineService.initializeEngine()
        }

        // 2. Tampilkan UI Mini Board dengan posisi tersimpan
        initUi()
    }

    private fun startForegroundCompat() {
        val stopIntent = Intent(this, MiniBoardOverlayService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Chess Beater ♟")
            .setContentText("Ghost / Mini Board Relay aktif — Stockfish siap")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Hentikan", stopPi)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initUi() {
        serviceScope.launch {
            val prefRepo = EnginePreferencesRepository(this@MiniBoardOverlayService)
            val boardRepo = BoardPreferencesRepository(this@MiniBoardOverlayService)
            val calibRepo = CalibrationPreferencesRepository(this@MiniBoardOverlayService)
            val presetRepo = PresetRepository(this@MiniBoardOverlayService)

            val prefs = prefRepo.userPreferencesFlow.first()
            val lastPos = boardRepo.getLastBoardPosition().first()
            val calib = calibRepo.getCalibrationFlow(prefs.selectedAppPackage).first()
            val savedArrowDuration = boardRepo.getArrowDurationMs().first()
            val visualPrefs = boardRepo.getVisualPreferences().first()
            currentVisualPrefs = visualPrefs

            val config = EngineConfig(prefs.engineType, prefs.powerPercentage)
            chessEngineService.setStrength(config)

            // Tentukan posisi & ukuran awal: Last Saved > Calibration > Default Prefs
            val density = resources.displayMetrics.density
            val defaultSizePx = (prefs.miniBoardSizeDp * density).toInt().coerceIn(280, 900)

            val initX = lastPos?.x ?: calib?.x ?: prefs.miniBoardPosX
            val initY = lastPos?.y ?: calib?.y ?: prefs.miniBoardPosY
            val initSizePx = lastPos?.sizePx ?: calib?.size ?: defaultSizePx

            savedBoardX = initX
            savedBoardY = initY
            savedBoardSizePx = initSizePx

            // Default eye position mirrors initial board position
            eyePosX = initX
            eyePosY = initY

            withContext(Dispatchers.Main) {
                showMiniBoard(prefs, initX, initY, initSizePx, calib?.toRect(), savedArrowDuration, visualPrefs)
            }

            // Observe calibration presets and sync to UI
            combine(presetRepo.getAllPresets(), presetRepo.getActivePresetId()) { presets, activeId ->
                Pair(presets, activeId)
            }.collect { (presets, activeId) ->
                withContext(Dispatchers.Main) {
                    overlayManager?.updateInteractiveBoardPresets(presets, activeId)
                }
            }
        }
    }

    private fun showMiniBoard(
        prefs: com.chessbeater.data.AppUserPreferences,
        initialX: Int,
        initialY: Int,
        initialSizePx: Int,
        calibratedRect: Rect?,
        savedArrowDurationMs: Long = 1000L,
        visualPrefs: BoardVisualPreferences = BoardVisualPreferences()
    ) {
        overlayManager = OverlayManager(this)

        overlayManager?.onMiniBoardEvaluationRequested = { fen ->
            stockfishJob?.cancel()
            stockfishJob = serviceScope.launch(Dispatchers.IO) {
                Log.d("StockfishSync", "Mengevaluasi FEN baru: $fen")
                val bestMove = try {
                    com.chessbeater.engine.StockfishBridge.getInstance(this@MiniBoardOverlayService).getBestMove(fen, 400L)
                } catch (e: Exception) {
                    Log.e(TAG, "Error eval", e)
                    null
                }
                withContext(Dispatchers.Main) {
                    val result = if (bestMove != null) com.chessbeater.engine.models.EngineResult(bestMove = bestMove) else null
                    overlayManager?.updateInteractiveBoardEngineResult(result)
                }
            }
        }

        overlayManager?.onMiniBoardPositionChanged = { x, y ->
            savedBoardX = x
            savedBoardY = y
            debounceSaveBoardPosition()
        }

        overlayManager?.onFloatingEyePositionChanged = { x, y ->
            eyePosX = x
            eyePosY = y
            Log.d(TAG, "Floating eye moved to: ($eyePosX, $eyePosY) - Saved board position remains intact at: ($savedBoardX, $savedBoardY)")
        }

        overlayManager?.onMiniBoardSizeChanged = { newSizePx ->
            savedBoardSizePx = newSizePx
            debounceSaveBoardPosition()
        }

        overlayManager?.onMiniBoardThinkingTimeChanged = { timeMs ->
            Log.d(TAG, "Mini Board thinking time updated: ${timeMs}ms")
            chessEngineService.maxThinkingTimeMs = timeMs
        }

        overlayManager?.onMiniBoardArrowDurationChanged = { durMs ->
            Log.d(TAG, "Mini Board arrow duration updated: ${durMs}ms")
            serviceScope.launch {
                BoardPreferencesRepository(this@MiniBoardOverlayService).saveArrowDurationMs(durMs)
            }
        }

        overlayManager?.onMiniBoardVisualPreferencesChanged = { updatedPrefs ->
            currentVisualPrefs = updatedPrefs
            serviceScope.launch {
                val repo = BoardPreferencesRepository(this@MiniBoardOverlayService)
                repo.saveVisualPreferences(updatedPrefs)
                Log.d(TAG, "Saved updated visual preferences: $updatedPrefs")
            }
        }

        overlayManager?.onMiniBoardClickThroughModeToggled = { enabled ->
            currentVisualPrefs = currentVisualPrefs.copy(isClickThroughMode = enabled)
            serviceScope.launch {
                BoardPreferencesRepository(this@MiniBoardOverlayService).saveClickThroughMode(enabled)
            }
            val toastMsg = if (enabled) "👆 Mode Tembus Game Asli (0ms Lag) Aktif" else "🎮 Mode Interaktif Papan Aktif"
            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
        }

        overlayManager?.onFloatingEyeLongPressed = {
            // Invisible Ghost Mode
            currentHideReason = OverlayHideReason.MANUAL
            autoShowJob?.cancel()
            isEyeShowing = false
            isBoardShowing = false
            overlayManager?.hideFloatingEye()
            Toast.makeText(this, "👻 Mode Tak Terlihat Aktif (Guncang HP untuk memunculkan)", Toast.LENGTH_LONG).show()
        }

        overlayManager?.onMiniBoardToggleVisibilityRequested = { hide, reason ->
            if (hide) {
                currentHideReason = reason
                Log.d(TAG, "Mini board hidden to floating eye (reason=$reason) at ($eyePosX, $eyePosY). Preserving board at ($savedBoardX, $savedBoardY)")
                overlayManager?.hideAllDialogs()
                isBoardShowing = false
                overlayManager?.hideInteractiveBoard(detachOnly = true)
                showFloatingEyeFromSaved()

                autoShowJob?.cancel()
                val livePrefs = getSharedPreferences("chessbeater_visual_prefs", Context.MODE_PRIVATE)
                val isAutoShowOn = livePrefs.getSafeBoolean("auto_show_enabled", livePrefs.getSafeBoolean("is_auto_show_enabled", currentVisualPrefs.isAutoShowEnabled))
                val autoShowDelay = livePrefs.getSafeFloat("auto_show_delay_sec", currentVisualPrefs.autoShowDelaySec.toFloat())

                if (reason == OverlayHideReason.AUTO_HIDE && isAutoShowOn && autoShowDelay > 0) {
                    autoShowJob = serviceScope.launch(Dispatchers.Main) {
                        delay((autoShowDelay * 1000L).toLong())
                        if (isEyeShowing && !isBoardShowing && currentHideReason == OverlayHideReason.AUTO_HIDE) {
                            Log.d(TAG, "Auto-show timer triggered: Restoring mini board automatically after ${autoShowDelay}s")
                            restoreBoardFromEye()
                        }
                    }
                } else {
                    Log.d(TAG, "Manual hide or auto-show disabled: Auto-show timer NOT scheduled (reason=$reason)")
                }
            } else {
                currentHideReason = OverlayHideReason.NONE
                autoShowJob?.cancel()
            }
        }

        overlayManager?.onMiniBoardSnapToCalibrationRequested = {
            serviceScope.launch(Dispatchers.IO) {
                val prefRepo = EnginePreferencesRepository(this@MiniBoardOverlayService)
                val calibRepo = CalibrationPreferencesRepository(this@MiniBoardOverlayService)
                val currentPrefs = prefRepo.userPreferencesFlow.first()
                val calib = calibRepo.getCalibrationFlow(currentPrefs.selectedAppPackage).first()
                val targetRect = calib?.toRect()

                withContext(Dispatchers.Main) {
                    if (targetRect != null && targetRect.width() > 0) {
                        overlayManager?.snapInteractiveBoard(targetRect)
                        savedBoardX = targetRect.left
                        savedBoardY = targetRect.top
                        savedBoardSizePx = targetRect.width()
                        debounceSaveBoardPosition()
                        Toast.makeText(this@MiniBoardOverlayService, "🎯 Berhasil pas kalibrasi papan!", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "Berhasil snap ke kalibrasi: $targetRect")
                    } else {
                        Toast.makeText(this@MiniBoardOverlayService, "Belum ada data kalibrasi papan!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        overlayManager?.onMiniBoardSavePositionToPresetRequested = {
            val currentX = savedBoardX
            val currentY = savedBoardY
            val currentSize = savedBoardSizePx

            serviceScope.launch(Dispatchers.IO) {
                val density = resources.displayMetrics.density
                val headerHeightPx = (48f * density).toInt()
                val pureBoardX = currentX.toFloat()
                val pureBoardY = (currentY + headerHeightPx).toFloat()
                val boardSize = currentSize.toFloat()

                // 1. Simpan ke preferensi posisi terakhir
                val boardRepo = BoardPreferencesRepository(this@MiniBoardOverlayService)
                boardRepo.saveLastBoardPosition(currentX, currentY, currentSize)

                // 2. Perbarui koordinat preset aktif
                val presetRepo = PresetRepository(this@MiniBoardOverlayService)
                presetRepo.updateActivePresetCoordinates(pureBoardX, pureBoardY, boardSize)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "✅ Posisi tersimpan! (X: $currentX, Y: $currentY, Size: ${currentSize}px)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        overlayManager?.onStartCalibrationRequested = {
            startCalibrationFlow()
        }

        overlayManager?.onPresetSelected = { preset ->
            applyPreset(preset)
        }

        overlayManager?.onAutoDetectionToggled = { enabled ->
            currentVisualPrefs = currentVisualPrefs.copy(isAutoDetectionEnabled = enabled)
            serviceScope.launch(Dispatchers.IO) {
                BoardPreferencesRepository(this@MiniBoardOverlayService).saveAutoDetectionEnabled(enabled)
            }
            startAutoDetectionLoop()
        }

        overlayManager?.onEloRatingChanged = { newElo ->
            currentVisualPrefs = currentVisualPrefs.copy(eloRating = newElo)
            chessEngineService.setEloRating(newElo)
            serviceScope.launch(Dispatchers.IO) {
                BoardPreferencesRepository(this@MiniBoardOverlayService).saveEloRating(newElo)
            }
        }

        overlayManager?.onMiniBoardClosed = {
            try {
                val appIntent = Intent(this@MiniBoardOverlayService, com.chessbeater.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(appIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch MainActivity", e)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }

        // Auto-launch target chess app jika diaktifkan
        if (prefs.autoLaunchTargetApp && prefs.selectedAppPackage.isNotBlank()) {
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(prefs.selectedAppPackage)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                launchIntent?.let { startActivity(it) }
                Log.i(TAG, "Auto-launched chess app: ${prefs.selectedAppPackage}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to auto-launch chess app", e)
            }
        }

        val initialRect = Rect(initialX, initialY, initialX + initialSizePx, initialY + initialSizePx)

        chessEngineService.setEloRating(visualPrefs.eloRating)

        overlayManager?.showInteractiveBoard(
            boardSizeDp = (initialSizePx / resources.displayMetrics.density).toInt(),
            opacity = prefs.miniBoardOpacity,
            initialX = initialX,
            initialY = initialY,
            calibratedRect = initialRect,
            isGhostMode = prefs.isGhostMode,
            isTouchForwarding = prefs.isTouchForwardingEnabled,
            isPiecesHiddenInGhostMode = prefs.isPiecesHiddenInGhostMode,
            arrowDurationMs = savedArrowDurationMs,
            gridAlpha = visualPrefs.gridAlpha,
            pieceAlpha = visualPrefs.pieceAlpha,
            highlightAlpha = visualPrefs.highlightAlpha,
            arrowAlpha = visualPrefs.arrowAlpha,
            floatingEyeAlpha = visualPrefs.floatingEyeAlpha,
            autoHideDelaySec = visualPrefs.autoHideDelaySec,
            eyeSizeDp = visualPrefs.eyeSizeDp,
            isClickThroughMode = visualPrefs.isClickThroughMode,
            isAutoShowEnabled = visualPrefs.isAutoShowEnabled,
            autoShowDelaySec = visualPrefs.autoShowDelaySec,
            isAutoDetectionEnabled = visualPrefs.isAutoDetectionEnabled,
            eloRating = visualPrefs.eloRating,
            startHidden = true
        )
        overlayManager?.onOpenSettingsOverlayRequested = {
            openMainControlMenu()
        }

        // Initial Startup State: Papan sembunyi secara default, Floating Eye siap di tepi layar
        val isQuickAlignEnabled = getSharedPreferences("chessbeater_visual_prefs", Context.MODE_PRIVATE)
            .getSafeBoolean("quick_alignment_enabled", true)

        if (isQuickAlignEnabled) {
            isBoardShowing = false
            currentHideReason = OverlayHideReason.MANUAL
            showFloatingEyeFromSaved()
            Log.i(TAG, "MiniBoardOverlayService started — Floating Eye ready at ($eyePosX, $eyePosY) with Quick Alignment HUD")

            // Tampilkan Quick Alignment Grid HUD neon transparan untuk verifikasi posisi instan
            val initialBoundsF = RectF(
                savedBoardX.toFloat(),
                savedBoardY.toFloat(),
                (savedBoardX + savedBoardSizePx).toFloat(),
                (savedBoardY + savedBoardSizePx).toFloat()
            )
            overlayManager?.showQuickAlignmentOverlay(initialBoundsF) { newBounds ->
                savedBoardX = newBounds.left.toInt()
                savedBoardY = newBounds.top.toInt()
                savedBoardSizePx = newBounds.width().toInt().coerceIn(280, 1400)
                debounceSaveBoardPosition()

                try {
                    getSharedPreferences("chessbeater_visual_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putFloat("board_left", newBounds.left)
                        .putFloat("board_top", newBounds.top)
                        .putFloat("board_right", newBounds.right)
                        .putFloat("board_bottom", newBounds.bottom)
                        .apply()
                } catch (ignored: Exception) {}

                overlayManager?.snapInteractiveBoard(
                    Rect(savedBoardX, savedBoardY, savedBoardX + savedBoardSizePx, savedBoardY + savedBoardSizePx)
                )
                Toast.makeText(this@MiniBoardOverlayService, "🔒 Posisi papan berhasil diselaraskan!", Toast.LENGTH_SHORT).show()

                // Munculkan QuickSideSelector dialog di tengah area papan
                overlayManager?.showQuickSideSelector(
                    boardBounds = newBounds,
                    onSideSelected = { isOpponentWhite ->
                        switchSideAndResetGame(isOpponentWhite)
                    },
                    onDismiss = {}
                )
            }
        } else {
            // Lewati kalibrasi cepat, langsung siapkan floating eye
            isBoardShowing = false
            currentHideReason = OverlayHideReason.MANUAL
            overlayManager?.hideInteractiveBoard(detachOnly = true)
            showFloatingEyeFromSaved()
            Log.d("ServiceStartup", "⚡ Kalibrasi cepat dinonaktifkan: Langsung siap dalam mode tersembunyi.")
        }

        startAutoDetectionLoop()
    }

    fun switchSideAndResetGame(opponentIsWhite: Boolean) {
        try {
            getSharedPreferences("chessbeater_visual_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("is_board_flipped", opponentIsWhite)
                .putString("engine_side", if (opponentIsWhite) "BLACK" else "WHITE")
                .apply()
        } catch (ignored: Exception) {}

        val interactiveView = overlayManager?.getInteractiveBoardView()
        interactiveView?.switchSideAndResetGame(opponentIsWhite)
        val modeText = if (opponentIsWhite) "Lawan Putih (Mesin Hitam Bawah)" else "Lawan Hitam (Mesin Putih Bawah)"
        Toast.makeText(this@MiniBoardOverlayService, "⚔️ Mode diatur: $modeText", Toast.LENGTH_SHORT).show()
    }

    fun applyPreset(preset: CalibrationPreset) {
        savedBoardX = preset.x.toInt()
        savedBoardY = preset.y.toInt()
        savedBoardSizePx = preset.width.toInt()

        serviceScope.launch {
            val repo = PresetRepository(this@MiniBoardOverlayService)
            repo.setActivePresetId(preset.id)
            val boardRepo = BoardPreferencesRepository(this@MiniBoardOverlayService)
            boardRepo.saveLastBoardPosition(savedBoardX, savedBoardY, savedBoardSizePx)
        }

        serviceScope.launch(Dispatchers.Main) {
            if (isBoardShowing) {
                val targetRect = Rect(savedBoardX, savedBoardY, savedBoardX + savedBoardSizePx, savedBoardY + savedBoardSizePx)
                overlayManager?.snapInteractiveBoard(targetRect)
            }
            Toast.makeText(this@MiniBoardOverlayService, "⚡ Preset aktif: ${preset.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCalibrationFlow() {
        val currentRect = Rect(savedBoardX, savedBoardY, savedBoardX + savedBoardSizePx, savedBoardY + savedBoardSizePx)
        val wasBoardShowing = isBoardShowing
        if (wasBoardShowing) {
            overlayManager?.hideInteractiveBoard(detachOnly = true)
        }
        overlayManager?.showCalibrationOverlay(
            initialRect = currentRect,
            onSave = { savedRect ->
                savedBoardX = savedRect.left
                savedBoardY = savedRect.top
                savedBoardSizePx = savedRect.width()
                debounceSaveBoardPosition()
                if (wasBoardShowing) {
                    overlayManager?.restoreInteractiveBoard(savedBoardX, savedBoardY, savedBoardSizePx)
                    overlayManager?.snapInteractiveBoard(savedRect)
                }
                Toast.makeText(this@MiniBoardOverlayService, "✅ Kalibrasi disimpan! (X: ${savedRect.left}, Y: ${savedRect.top}, Size: ${savedRect.width()}px)", Toast.LENGTH_SHORT).show()
            },
            onSavePreset = { preset ->
                serviceScope.launch {
                    val repo = PresetRepository(this@MiniBoardOverlayService)
                    repo.savePreset(preset)
                    applyPreset(preset)
                }
            },
            onCancel = {
                if (wasBoardShowing) {
                    overlayManager?.restoreInteractiveBoard(savedBoardX, savedBoardY, savedBoardSizePx)
                }
            }
        )
    }

    fun openMainControlMenu() {
        val boardRect = RectF(
            savedBoardX.toFloat(),
            savedBoardY.toFloat(),
            (savedBoardX + savedBoardSizePx).toFloat(),
            (savedBoardY + savedBoardSizePx).toFloat()
        )
        overlayManager?.showMainControlMenu(
            boardBounds = boardRect,
            onSelectOpponentWhite = {
                switchSideAndResetGame(true)
            },
            onSelectOpponentBlack = {
                switchSideAndResetGame(false)
            },
            onToggleAutoDetect = {
                val current = currentVisualPrefs.isAutoDetectionEnabled
                currentVisualPrefs = currentVisualPrefs.copy(isAutoDetectionEnabled = !current)
                overlayManager?.getInteractiveBoardView()?.isAutoDetectionEnabled = !current
                debounceSaveBoardPosition()
                Toast.makeText(this@MiniBoardOverlayService, if (!current) "🤖 Deteksi otomatis lawan: AKTIF" else "🤖 Deteksi otomatis lawan: NONAKTIF", Toast.LENGTH_SHORT).show()
            },
            onUndoMove = {
                overlayManager?.getInteractiveBoardView()?.undoLastMove()
            },
            onCorrectionMode = {
                overlayManager?.getInteractiveBoardView()?.toggleCorrectionMode()
            },
            onSavePreset = {
                serviceScope.launch(Dispatchers.IO) {
                    val repo = PresetRepository(this@MiniBoardOverlayService)
                    val preset = com.chessbeater.data.CalibrationPreset(
                        id = System.currentTimeMillis().toString(),
                        name = "Preset ${System.currentTimeMillis() % 1000}",
                        x = savedBoardX.toFloat(),
                        y = savedBoardY.toFloat(),
                        width = savedBoardSizePx.toFloat(),
                        height = savedBoardSizePx.toFloat()
                    )
                    repo.savePreset(preset)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MiniBoardOverlayService, "💾 Posisi papan disimpan ke preset!", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onNewCalibration = {
                startCalibrationFlow()
            },
            onFlipBoard = {
                overlayManager?.getInteractiveBoardView()?.flipBoard()
            },
            onResetGame = {
                overlayManager?.getInteractiveBoardView()?.resetBoard()
                Toast.makeText(this@MiniBoardOverlayService, "🗑️ Papan catur berhasil di-reset ke awal", Toast.LENGTH_SHORT).show()
            },
            onOpenAdvancedSettings = {
                openSettingsOverlay()
            },
            onHideOverlay = {
                hideOverlayManually()
            },
            onExitService = {
                stopSelf()
            },
            onClose = {}
        )
    }

    fun closeMainControlMenu() {
        overlayManager?.hideMainControlMenu()
    }

    fun openSettingsOverlay() {
        val boardRect = RectF(
            savedBoardX.toFloat(),
            savedBoardY.toFloat(),
            (savedBoardX + savedBoardSizePx).toFloat(),
            (savedBoardY + savedBoardSizePx).toFloat()
        )
        overlayManager?.showSettingsOverlay(
            boardBounds = boardRect,
            onBackToMainMenu = {
                openMainControlMenu()
            },
            onClose = {
                overlayManager?.getInteractiveBoardView()?.reloadVisualSettingsOnly()
            }
        )
    }

    fun closeSettingsOverlay() {
        overlayManager?.hideSettingsOverlay()
        overlayManager?.getInteractiveBoardView()?.reloadVisualSettingsOnly()
    }

    fun hideOverlayManually() {
        overlayManager?.hideAllDialogs()
        currentHideReason = OverlayHideReason.MANUAL
        isBoardShowing = false
        overlayManager?.hideInteractiveBoard(detachOnly = true)
        showFloatingEyeFromSaved()
        Log.d(TAG, "🔒 Papan & seluruh menu dialog berhasil di-hide serempak.")
    }

    private fun showFloatingEyeFromSaved() {
        isEyeShowing = true
        overlayManager?.showFloatingEye(
            initialX = eyePosX,
            initialY = eyePosY,
            eyeSizeDp = currentVisualPrefs.eyeSizeDp,
            floatingEyeAlpha = currentVisualPrefs.floatingEyeAlpha,
            onLongPressed = {
                isEyeShowing = false
                isBoardShowing = false
                overlayManager?.hideFloatingEye()
                Toast.makeText(this, "👻 Mode Tak Terlihat Aktif (Guncang HP untuk memunculkan)", Toast.LENGTH_LONG).show()
            }
        ) {
            restoreBoardFromEye()
        }
    }

    private fun restoreBoardFromEye() {
        currentHideReason = OverlayHideReason.NONE
        autoShowJob?.cancel()
        Log.d(TAG, "Restoring mini board from floating eye to original position ($savedBoardX, $savedBoardY, $savedBoardSizePx)")
        isEyeShowing = false
        isBoardShowing = true
        overlayManager?.hideFloatingEye()
        overlayManager?.restoreInteractiveBoard(savedBoardX, savedBoardY, savedBoardSizePx)
    }

    private fun onShakeDetected() {
        Log.i(TAG, "Shake detected! Current state: isBoardShowing=$isBoardShowing, isEyeShowing=$isEyeShowing")
        // Trigger 50ms haptic feedback
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }

        serviceScope.launch(Dispatchers.Main) {
            if (!isBoardShowing && !isEyeShowing) {
                // Invisible Mode -> Reveal Floating Eye
                showFloatingEyeFromSaved()
                Toast.makeText(this@MiniBoardOverlayService, "👁️ Tombol mata dimunculkan kembali!", Toast.LENGTH_SHORT).show()
            } else if (isEyeShowing) {
                // Eye is showing -> directly restore board
                restoreBoardFromEye()
                Toast.makeText(this@MiniBoardOverlayService, "♟ Papan catur dipulihkan!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startAutoDetectionLoop() {
        autoDetectJob?.cancel()
        if (!currentVisualPrefs.isAutoDetectionEnabled) {
            Log.d(TAG, "Auto-detection disabled: Scanning loop inactive (0% CPU)")
            return
        }

        Log.i(TAG, "Starting opponent move auto-detection background polling loop (100ms / 10 FPS)")
        autoDetectJob = serviceScope.launch(Dispatchers.Default) {
            var lastDetectedMovePair: Pair<Int, Int>? = null
            var consecutiveDetectCount = 0

            while (isActive && currentVisualPrefs.isAutoDetectionEnabled) {
                delay(100L)

                if (!isBoardShowing || overlayManager?.isInteractiveBoardOpponentTurn() != true) {
                    lastDetectedMovePair = null
                    consecutiveDetectCount = 0
                    continue
                }

                val frameBitmap = com.chessbeater.capture.ScreenCaptureService.latestFrame
                if (frameBitmap == null || frameBitmap.isRecycled) continue

                val state = overlayManager?.getInteractiveBoardState() ?: continue
                val (boardArr, activeTurn, isFlipped) = state
                val castling = overlayManager?.getInteractiveBoardCastlingRights() ?: "KQkq"

                // 1. Crop calibrated region of game board from frameBitmap
                val density = resources.displayMetrics.density
                val headerHeightPx = (48f * density).toInt()
                val cropLeft = savedBoardX.coerceIn(0, frameBitmap.width - 1)
                val cropTop = (savedBoardY + headerHeightPx).coerceIn(0, frameBitmap.height - 1)
                val cropSize = savedBoardSizePx.coerceAtMost(min(frameBitmap.width - cropLeft, frameBitmap.height - cropTop))

                if (cropSize < 50) continue

                val croppedBoard = try {
                    Bitmap.createBitmap(frameBitmap, cropLeft, cropTop, cropSize, cropSize)
                } catch (e: Exception) {
                    null
                } ?: continue

                // 2. Detect moved squares using 4-corner pixel sampler
                val detectedMove = pixelSampler.detectMoveFromFrame(croppedBoard, boardArr, activeTurn, castling, isFlipped)
                croppedBoard.recycle()

                if (detectedMove != null) {
                    if (detectedMove == lastDetectedMovePair) {
                        consecutiveDetectCount++
                    } else {
                        lastDetectedMovePair = detectedMove
                        consecutiveDetectCount = 1
                    }

                    if (consecutiveDetectCount >= 2) {
                        Log.i("BoardPixelSampler", "🤖 Auto-detected opponent move: ${detectedMove.first} -> ${detectedMove.second}")
                        lastDetectedMovePair = null
                        consecutiveDetectCount = 0

                        withContext(Dispatchers.Main) {
                            // 1. Haptic feedback 30ms
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator?.vibrate(VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator?.vibrate(30L)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Haptic feedback failed", e)
                            }

                            // 2. Execute move on interactive board
                            overlayManager?.executeInteractiveBoardOpponentMove(detectedMove.first, detectedMove.second)
                        }

                        // Extra delay after move execution to avoid sampling move in-flight animations
                        delay(600L)
                    }
                } else {
                    lastDetectedMovePair = null
                    consecutiveDetectCount = 0
                }
            }
        }
    }

    private fun debounceSaveBoardPosition() {
        savePositionJob?.cancel()
        savePositionJob = serviceScope.launch {
            delay(500L)
            val boardRepo = BoardPreferencesRepository(this@MiniBoardOverlayService)
            boardRepo.saveLastBoardPosition(savedBoardX, savedBoardY, savedBoardSizePx)
            EnginePreferencesRepository(this@MiniBoardOverlayService).updateMiniBoardPosition(savedBoardX, savedBoardY)
            Log.d(TAG, "Auto-saved last board position: x=$savedBoardX, y=$savedBoardY, sizePx=$savedBoardSizePx")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Mini Board Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Chess Beater Mini Board Relay Mode" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_START_CALIBRATION -> {
                startCalibrationFlow()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        isRunning = false
        stockfishJob?.cancel()
        autoShowJob?.cancel()
        autoDetectJob?.cancel()
        shakeDetector?.stop(sensorManager)
        overlayManager?.hideInteractiveBoard()
        overlayManager?.hideFloatingEye()
        overlayManager?.hideQuickAlignmentOverlay()
        overlayManager?.hideQuickSideSelector()
        overlayManager?.hideSettingsOverlay()
        chessEngineService.release()
        serviceScope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        Log.i(TAG, "MiniBoardOverlayService destroyed")
    }
}
