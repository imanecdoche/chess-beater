package com.chessbeater.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.chessbeater.engine.models.EngineResult
import com.chessbeater.vision.models.PlayerColor
import kotlin.math.roundToInt

/**
 * Thread-safe Lifecycle Manager for all floating overlay windows.
 * Encapsulates safe WindowManager add/update/remove with isAttachedToWindow guards.
 */
class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var boardArrowView: BoardArrowOverlayView? = null
    private var arrowParams: WindowManager.LayoutParams? = null

    private var floatingHudView: FloatingHudView? = null
    private var hudParams: WindowManager.LayoutParams? = null

    private var calibrationOverlayView: BoardCalibrationOverlayView? = null
    private var calibParams: WindowManager.LayoutParams? = null

    private var interactiveBoardView: InteractiveBoardOverlayView? = null
    private var interactiveBoardParams: WindowManager.LayoutParams? = null

    private var floatingEyeView: FloatingEyeToggleView? = null
    private var floatingEyeParams: WindowManager.LayoutParams? = null

    private var isOverlaysShowing = false
    private var isCalibrating = false
    private var isInteractiveBoardShowing = false
    private var isFloatingEyeShowing = false

    var onCalibrationRequested: (() -> Unit)? = null
    var onPlayerColorToggleRequested: (() -> Unit)? = null
    var onMiniBoardEvaluationRequested: ((fen: String) -> Unit)? = null
    var onMiniBoardThinkingTimeChanged: ((Long) -> Unit)? = null
    var onMiniBoardArrowDurationChanged: ((Long) -> Unit)? = null
    var onMiniBoardToggleVisibilityRequested: ((hide: Boolean, reason: OverlayHideReason) -> Unit)? = null
    var onMiniBoardPositionChanged: ((x: Int, y: Int) -> Unit)? = null
    var onMiniBoardSizeChanged: ((sizePx: Int) -> Unit)? = null
    var onMiniBoardSnapToCalibrationRequested: (() -> Unit)? = null
    var onStartCalibrationRequested: (() -> Unit)? = null
    var onPresetSelected: ((com.chessbeater.data.CalibrationPreset) -> Unit)? = null
    var onMiniBoardSavePositionToPresetRequested: (() -> Unit)? = null
    var onMiniBoardClosed: (() -> Unit)? = null
    var onFloatingEyePositionChanged: ((x: Int, y: Int) -> Unit)? = null
    var onFloatingEyeClicked: (() -> Unit)? = null
    var onFloatingEyeLongPressed: (() -> Unit)? = null
    var onMiniBoardVisualPreferencesChanged: ((com.chessbeater.data.BoardVisualPreferences) -> Unit)? = null
    var onMiniBoardClickThroughModeToggled: ((Boolean) -> Unit)? = null
    var onAutoDetectionToggled: ((Boolean) -> Unit)? = null
    var onEloRatingChanged: ((Int) -> Unit)? = null

    companion object {
        private const val TAG = "OverlayManager"

        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
    }

    /**
     * Attaches and displays both overlay windows (guaranteed to run on Main UI Thread)
     */
    fun showOverlays() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showOverlays() }
            return
        }

        if (isOverlaysShowing || windowManager == null || !canDrawOverlays(context)) {
            Log.w(TAG, "Cannot show overlays: permission denied or already active")
            return
        }

        try {
            // 1. Setup Fullscreen Transparent Arrow Overlay Window
            val arrowLayoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            arrowParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                arrowLayoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }

            boardArrowView = BoardArrowOverlayView(context)
            windowManager.addView(boardArrowView, arrowParams)

            // 2. Setup Draggable Floating HUD Window
            val hudLayoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            hudParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                hudLayoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 60
                y = 200
            }

            floatingHudView = FloatingHudView(
                context = context,
                onDragListener = { dx, dy ->
                    hudParams?.let { params ->
                        params.x += dx
                        params.y += dy
                        floatingHudView?.let { hud ->
                            if (hud.isAttachedToWindow) {
                                try {
                                    windowManager.updateViewLayout(hud, params)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Error updating HUD layout", e)
                                }
                            }
                        }
                    }
                },
                onCloseListener = {
                    hideOverlays()
                },
                onCalibrateListener = {
                    onCalibrationRequested?.invoke()
                },
                onMiniBoardToggleListener = {
                    if (isInteractiveBoardShowing) hideInteractiveBoard() else showInteractiveBoard()
                },
                onColorToggleListener = {
                    onPlayerColorToggleRequested?.invoke()
                }
            )

            windowManager.addView(floatingHudView, hudParams)
            isOverlaysShowing = true
            Log.i(TAG, "Overlays successfully attached to WindowManager on Main Thread")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach overlay windows", e)
            hideOverlays()
        }
    }

    /**
     * Displays the Interactive Floating Mini-Chessboard overlay
     */
    fun showInteractiveBoard(
        boardSizeDp: Int = 220,
        opacity: Float = 0.94f,
        initialX: Int = 40,
        initialY: Int = 180,
        calibratedRect: Rect? = null,
        isGhostMode: Boolean = false,
        isTouchForwarding: Boolean = true,
        isPiecesHiddenInGhostMode: Boolean = true,
        arrowDurationMs: Long = 1000L,
        gridAlpha: Float = 0.15f,
        pieceAlpha: Float = 1.0f,
        highlightAlpha: Float = 0.65f,
        arrowAlpha: Float = 0.90f,
        floatingEyeAlpha: Float = 0.85f,
        autoHideDelaySec: Int = -1,
        eyeSizeDp: Int = 72,
        isClickThroughMode: Boolean = false,
        isAutoShowEnabled: Boolean = false,
        autoShowDelaySec: Int = 2,
        isAutoDetectionEnabled: Boolean = false,
        eloRating: Int = 2200,
        startHidden: Boolean = false
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                showInteractiveBoard(
                    boardSizeDp,
                    opacity,
                    initialX,
                    initialY,
                    calibratedRect,
                    isGhostMode,
                    isTouchForwarding,
                    isPiecesHiddenInGhostMode,
                    arrowDurationMs,
                    gridAlpha,
                    pieceAlpha,
                    highlightAlpha,
                    arrowAlpha,
                    floatingEyeAlpha,
                    autoHideDelaySec,
                    eyeSizeDp,
                    isClickThroughMode,
                    isAutoShowEnabled,
                    autoShowDelaySec,
                    isAutoDetectionEnabled,
                    eloRating,
                    startHidden
                )
            }
            return
        }

        if (isInteractiveBoardShowing || windowManager == null || !canDrawOverlays(context)) return

        try {
            val density = context.resources.displayMetrics.density
            val headerHeightPx = (48f * density).roundToInt()
            val boardSizePx = calibratedRect?.width() ?: (boardSizeDp * density).toInt().coerceIn(280, 900)
            val posX = calibratedRect?.left ?: initialX
            val posY = if (calibratedRect != null) (calibratedRect.top - headerHeightPx) else initialY

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val touchableFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    (if (isClickThroughMode) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0)

            interactiveBoardParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                touchableFlags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            interactiveBoardView = InteractiveBoardOverlayView(
                context = context,
                onDragListener = { dx, dy ->
                    // Fullscreen canvas moves boardRect internally
                    onMiniBoardPositionChanged?.invoke(dx, dy)
                },
                onScaleListener = { scaleFactor ->
                    interactiveBoardView?.let { v ->
                        val curSz = v.getBoardSize()
                        val newSz = (curSz * scaleFactor).toInt().coerceIn(280, 1400)
                        v.updateBoardSize(newSz)
                        onMiniBoardSizeChanged?.invoke(newSz)
                    }
                },
                onCloseListener = { hideInteractiveBoard(); onMiniBoardClosed?.invoke() },
                onEvaluateRequested = { fen -> onMiniBoardEvaluationRequested?.invoke(fen) },
                onThinkingTimeChanged = { timeMs -> onMiniBoardThinkingTimeChanged?.invoke(timeMs) },
                onSnapToCalibrationRequested = { onMiniBoardSnapToCalibrationRequested?.invoke() },
                onStartCalibrationRequested = { onStartCalibrationRequested?.invoke() },
                onPresetSelected = { preset -> onPresetSelected?.invoke(preset) },
                onSaveCurrentPositionToPresetRequested = { onMiniBoardSavePositionToPresetRequested?.invoke() },
                onArrowDurationChanged = { dur -> onMiniBoardArrowDurationChanged?.invoke(dur) },
                onToggleVisibilityRequested = { hide, reason -> onMiniBoardToggleVisibilityRequested?.invoke(hide, reason) },
                onVisualPreferencesChanged = { prefs -> onMiniBoardVisualPreferencesChanged?.invoke(prefs) },
                onClickThroughModeToggled = { enabled ->
                    setInteractiveBoardClickThrough(enabled)
                    onMiniBoardClickThroughModeToggled?.invoke(enabled)
                },
                onAutoDetectionToggled = { enabled ->
                    onAutoDetectionToggled?.invoke(enabled)
                },
                onEloRatingChanged = { elo ->
                    onEloRatingChanged?.invoke(elo)
                },
                onOpenSettingsRequested = {
                    onOpenSettingsOverlayRequested?.invoke()
                },
                boardSizePx = boardSizePx,
                isGhostMode = isGhostMode,
                isTouchForwarding = isTouchForwarding,
                isPiecesHiddenInGhostMode = isPiecesHiddenInGhostMode,
                arrowDurationMs = arrowDurationMs,
                gridAlpha = gridAlpha,
                pieceAlpha = pieceAlpha,
                highlightAlpha = highlightAlpha,
                arrowAlpha = arrowAlpha,
                floatingEyeAlpha = floatingEyeAlpha,
                autoHideDelaySec = autoHideDelaySec,
                eyeSizeDp = eyeSizeDp,
                isClickThroughMode = isClickThroughMode,
                isAutoShowEnabled = isAutoShowEnabled,
                autoShowDelaySec = autoShowDelaySec,
                isAutoDetectionEnabled = isAutoDetectionEnabled,
                eloRating = eloRating
            )

            if (startHidden) {
                isInteractiveBoardShowing = false
                Log.i(TAG, "Interactive Mini-Chessboard initialized in HIDDEN mode (ready for Floating Eye restore)")
            } else {
                windowManager.addView(interactiveBoardView, interactiveBoardParams)
                isInteractiveBoardShowing = true
                Log.i(TAG, "Interactive Mini-Chessboard displayed in Fullscreen Overlay (calibrated=${calibratedRect != null}, ghost=$isGhostMode, arrowDur=${arrowDurationMs}ms, autoHide=${autoHideDelaySec}s, clickThrough=$isClickThroughMode)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to display Interactive Mini-Chessboard", e)
            hideInteractiveBoard()
        }
    }

    fun setInteractiveBoardClickThrough(enabled: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setInteractiveBoardClickThrough(enabled) }
            return
        }
        val view = interactiveBoardView ?: return
        val params = interactiveBoardParams ?: return
        if (enabled) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        try {
            if (view.isAttachedToWindow) {
                windowManager?.updateViewLayout(view, params)
                Log.i(TAG, "Interactive board FLAG_NOT_TOUCHABLE set to: $enabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update layout flags", e)
        }
    }

    fun getInteractiveBoardView(): InteractiveBoardOverlayView? = interactiveBoardView

    /**
     * Restores the existing Interactive Floating Mini-Chessboard overlay keeping state intact
     */
    fun restoreInteractiveBoard(posX: Int, posY: Int, sizePx: Int) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreInteractiveBoard(posX, posY, sizePx) }
            return
        }
        hideFloatingEye()
        val view = interactiveBoardView
        if (view != null && !isInteractiveBoardShowing && windowManager != null) {
            try {
                view.reloadBoardBounds()
                val targetRect = android.graphics.RectF(posX.toFloat(), posY.toFloat(), (posX + sizePx).toFloat(), (posY + sizePx).toFloat())
                view.updateBoardRect(targetRect)
                val params = interactiveBoardParams ?: run {
                    val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    }
                    WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        layoutType,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                        x = 0
                        y = 0
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        }
                    }
                }
                interactiveBoardParams = params
                windowManager.addView(view, params)
                isInteractiveBoardShowing = true
                Log.i(TAG, "Interactive Mini-Chessboard restored in Fullscreen Overlay")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore Interactive Mini-Chessboard", e)
            }
        }
    }

    /**
     * Displays the Floating Eye Toggle overlay
     */
    fun showFloatingEye(
        initialX: Int = 40,
        initialY: Int = 180,
        eyeSizeDp: Int = 72,
        floatingEyeAlpha: Float = 0.85f,
        onLongPressed: (() -> Unit)? = null,
        onClick: () -> Unit
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showFloatingEye(initialX, initialY, eyeSizeDp, floatingEyeAlpha, onLongPressed, onClick) }
            return
        }
        if (isFloatingEyeShowing || windowManager == null || !canDrawOverlays(context)) return

        try {
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            floatingEyeParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = initialX
                y = initialY
            }

            floatingEyeView = FloatingEyeToggleView(
                context = context,
                onDragListener = { dx, dy ->
                    floatingEyeParams?.let { params ->
                        params.x += dx
                        params.y += dy
                        floatingEyeView?.let { v ->
                            if (v.isAttachedToWindow) {
                                try {
                                    windowManager.updateViewLayout(v, params)
                                    onFloatingEyePositionChanged?.invoke(params.x, params.y)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Error updating floating eye layout", e)
                                }
                            }
                        }
                    }
                },
                onClickListener = {
                    onClick()
                    onFloatingEyeClicked?.invoke()
                },
                onEyeLongPressed = {
                    onLongPressed?.invoke()
                    onFloatingEyeLongPressed?.invoke()
                },
                eyeSizeDp = eyeSizeDp,
                floatingEyeAlpha = floatingEyeAlpha
            )

            windowManager.addView(floatingEyeView, floatingEyeParams)
            isFloatingEyeShowing = true
            Log.i(TAG, "FloatingEyeToggleView attached at ($initialX, $initialY) size=${eyeSizeDp}dp alpha=$floatingEyeAlpha")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show FloatingEyeToggleView", e)
            hideFloatingEye()
        }
    }

    /**
     * Hides the Floating Eye Toggle overlay
     */
    fun hideFloatingEye() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideFloatingEye() }
            return
        }
        if (!isFloatingEyeShowing) return
        try {
            floatingEyeView?.let {
                if (it.isAttachedToWindow) {
                    windowManager?.removeView(it)
                }
                floatingEyeView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing floating eye view", e)
        } finally {
            isFloatingEyeShowing = false
            floatingEyeParams = null
        }
    }

    /**
     * Snaps Interactive Mini-Chessboard layout & position to match calibrated game board
     */
    fun snapInteractiveBoard(calibratedRect: Rect) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { snapInteractiveBoard(calibratedRect) }
            return
        }
        val rectF = android.graphics.RectF(
            calibratedRect.left.toFloat(),
            calibratedRect.top.toFloat(),
            calibratedRect.right.toFloat(),
            calibratedRect.bottom.toFloat()
        )
        interactiveBoardView?.updateBoardRect(rectF)
        Log.i(TAG, "Successfully snapped mini-chessboard to calibration: $calibratedRect")
    }

    /**
     * Hides the Interactive Floating Mini-Chessboard overlay
     */
    fun hideInteractiveBoard(detachOnly: Boolean = false) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideInteractiveBoard(detachOnly) }
            return
        }
        if (!isInteractiveBoardShowing) return
        hideAllDialogs()
        try {
            interactiveBoardView?.let {
                if (it.isAttachedToWindow) {
                    windowManager?.removeView(it)
                }
                if (!detachOnly) {
                    interactiveBoardView = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing interactive mini-chessboard", e)
        } finally {
            isInteractiveBoardShowing = false
            if (!detachOnly) {
                interactiveBoardParams = null
            }
        }
    }

    fun updateInteractiveBoardEngineResult(bestMove: String?, evalCp: Int?, mate: Int?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateInteractiveBoardEngineResult(bestMove, evalCp, mate) }
            return
        }
        val result = EngineResult(
            bestMove = bestMove ?: "",
            evaluationCentipawns = evalCp,
            mateInMoves = mate
        )
        interactiveBoardView?.onEngineResult(result)
    }

    fun updateInteractiveBoardEngineResult(result: EngineResult?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateInteractiveBoardEngineResult(result) }
            return
        }
        interactiveBoardView?.onEngineResult(result)
    }

    fun updateInteractiveBoardPresets(presets: List<com.chessbeater.data.CalibrationPreset>, activeId: String?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateInteractiveBoardPresets(presets, activeId) }
            return
        }
        interactiveBoardView?.updatePresets(presets, activeId)
    }

    fun isInteractiveBoardOpponentTurn(): Boolean = interactiveBoardView?.isOpponentTurn() ?: false

    fun executeInteractiveBoardOpponentMove(from: Int, to: Int) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { executeInteractiveBoardOpponentMove(from, to) }
            return
        }
        interactiveBoardView?.executeExternalMove(from, to, isStockfish = false)
    }

    fun getInteractiveBoardState(): Triple<CharArray, com.chessbeater.vision.models.PlayerColor, Boolean>? {
        val v = interactiveBoardView ?: return null
        return Triple(v.getBoardArray(), v.getCurrentTurn(), v.getIsBoardFlipped())
    }

    fun getInteractiveBoardCastlingRights(): String = interactiveBoardView?.getCastlingRights() ?: "KQkq"

    /**
     * Displays fullscreen calibration overlay with draggable crop handles
     */
    fun showCalibrationOverlay(
        initialRect: Rect? = null,
        onSave: (Rect) -> Unit,
        onSavePreset: ((com.chessbeater.data.CalibrationPreset) -> Unit)? = null,
        onCancel: () -> Unit = {}
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showCalibrationOverlay(initialRect, onSave, onSavePreset, onCancel) }
            return
        }

        if (isCalibrating || windowManager == null || !canDrawOverlays(context)) return

        try {
            val calibLayoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            calibParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                calibLayoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            calibrationOverlayView = BoardCalibrationOverlayView(
                context = context,
                initialRect = initialRect,
                onSaveListener = { savedRect ->
                    hideCalibrationOverlay()
                    onSave(savedRect)
                },
                onSavePresetListener = { preset ->
                    onSavePreset?.invoke(preset)
                },
                onCancelListener = {
                    hideCalibrationOverlay()
                    onCancel()
                }
            )

            windowManager.addView(calibrationOverlayView, calibParams)
            isCalibrating = true
            Log.i(TAG, "Board calibration overlay displayed on Main Thread")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to display calibration overlay", e)
            hideCalibrationOverlay()
        }
    }

    /**
     * Removes and dismisses the calibration overlay view.
     */
    fun hideCalibrationOverlay() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideCalibrationOverlay() }
            return
        }
        if (!isCalibrating) return
        try {
            calibrationOverlayView?.let {
                if (it.isAttachedToWindow) {
                    windowManager?.removeView(it)
                }
                calibrationOverlayView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing calibration overlay", e)
        } finally {
            isCalibrating = false
            calibParams = null
        }
    }

    private var quickAlignmentView: com.chessbeater.ui.QuickAlignmentOverlayView? = null

    /**
     * Displays fullscreen lightweight Quick Alignment HUD overlay
     */
    fun showQuickAlignmentOverlay(
        initialBounds: android.graphics.RectF,
        onLocked: (android.graphics.RectF) -> Unit
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showQuickAlignmentOverlay(initialBounds, onLocked) }
            return
        }

        if (windowManager == null || !canDrawOverlays(context)) return

        try {
            hideQuickAlignmentOverlay()
            val calibLayoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                calibLayoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            quickAlignmentView = com.chessbeater.ui.QuickAlignmentOverlayView(
                context = context,
                initialBounds = initialBounds,
                onLockedCallback = { finalBounds ->
                    hideQuickAlignmentOverlay()
                    onLocked(finalBounds)
                }
            )

            windowManager.addView(quickAlignmentView, params)
            Log.i(TAG, "Quick alignment HUD overlay displayed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to display quick alignment overlay", e)
            hideQuickAlignmentOverlay()
        }
    }

    fun hideQuickAlignmentOverlay() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideQuickAlignmentOverlay() }
            return
        }
        try {
            val v = quickAlignmentView
            if (v != null && windowManager != null) {
                if (v.isAttachedToWindow) {
                    windowManager.removeViewImmediate(v)
                } else {
                    windowManager.removeView(v)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menghapus QuickAlignmentView: ${e.message}")
        } finally {
            quickAlignmentView = null
        }
    }

    private var quickSideSelectorView: com.chessbeater.ui.QuickSideSelectorView? = null

    /**
     * Displays compact floating quick side selector dialog
     */
    fun showQuickSideSelector(
        boardBounds: android.graphics.RectF,
        onSideSelected: (isOpponentWhite: Boolean) -> Unit,
        onDismiss: () -> Unit
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showQuickSideSelector(boardBounds, onSideSelected, onDismiss) }
            return
        }

        if (windowManager == null || !canDrawOverlays(context)) return

        try {
            hideQuickSideSelector()
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val density = context.resources.displayMetrics.density
            val dialogWidth = (boardBounds.width() * 0.90f).toInt().coerceAtLeast((280 * density).toInt())
            val params = WindowManager.LayoutParams(
                dialogWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (boardBounds.centerX() - dialogWidth / 2f).toInt()
                y = (boardBounds.centerY() - (110 * density)).toInt().coerceAtLeast((40 * density).toInt())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            quickSideSelectorView = com.chessbeater.ui.QuickSideSelectorView(
                context = context,
                onSideSelected = { isOpponentWhite ->
                    hideQuickSideSelector()
                    onSideSelected(isOpponentWhite)
                },
                onDismissKeepLast = {
                    hideQuickSideSelector()
                    onDismiss()
                }
            )

            windowManager.addView(quickSideSelectorView, params)
            Log.i(TAG, "Quick side selector dialog displayed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show quick side selector dialog", e)
            hideQuickSideSelector()
        }
    }

    fun hideQuickSideSelector() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideQuickSideSelector() }
            return
        }
        try {
            val v = quickSideSelectorView
            if (v != null && windowManager != null) {
                if (v.isAttachedToWindow) {
                    windowManager.removeViewImmediate(v)
                } else {
                    windowManager.removeView(v)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove quick side selector view: ${e.message}")
        } finally {
            quickSideSelectorView = null
        }
    }

    private var mainControlMenuView: com.chessbeater.ui.MainControlMenuView? = null
    var isMainControlMenuShowing: Boolean = false
        private set

    /**
     * Displays Level 1 Main Control Menu View in WindowManager
     */
    fun showMainControlMenu(
        boardBounds: android.graphics.RectF,
        onSelectOpponentWhite: () -> Unit,
        onSelectOpponentBlack: () -> Unit,
        onToggleAutoDetect: () -> Unit,
        onUndoMove: () -> Unit,
        onCorrectionMode: () -> Unit,
        onSavePreset: () -> Unit,
        onNewCalibration: () -> Unit,
        onFlipBoard: () -> Unit,
        onResetGame: () -> Unit,
        onOpenAdvancedSettings: () -> Unit,
        onHideOverlay: () -> Unit,
        onExitService: () -> Unit,
        onClose: () -> Unit
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                showMainControlMenu(
                    boardBounds,
                    onSelectOpponentWhite,
                    onSelectOpponentBlack,
                    onToggleAutoDetect,
                    onUndoMove,
                    onCorrectionMode,
                    onSavePreset,
                    onNewCalibration,
                    onFlipBoard,
                    onResetGame,
                    onOpenAdvancedSettings,
                    onHideOverlay,
                    onExitService,
                    onClose
                )
            }
            return
        }

        if (windowManager == null || !canDrawOverlays(context)) return

        try {
            hideAllDialogs()
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val density = context.resources.displayMetrics.density
            val widthPx = boardBounds.width().toInt().coerceAtLeast((300 * density).toInt())
            val heightPx = (boardBounds.height() * 0.95f).toInt().coerceAtLeast((340 * density).toInt())

            val params = WindowManager.LayoutParams(
                widthPx,
                heightPx,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = boardBounds.left.toInt()
                y = boardBounds.top.toInt()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            val isAutoDetect = interactiveBoardView?.isAutoDetectionEnabled ?: false
            val isFlipped = interactiveBoardView?.getIsBoardFlipped() ?: false

            mainControlMenuView = com.chessbeater.ui.MainControlMenuView(
                context = context,
                isAutoDetectActive = isAutoDetect,
                isFlipped = isFlipped,
                onSelectOpponentWhite = onSelectOpponentWhite,
                onSelectOpponentBlack = onSelectOpponentBlack,
                onToggleAutoDetect = onToggleAutoDetect,
                onUndoMove = onUndoMove,
                onCorrectionMode = onCorrectionMode,
                onSavePreset = onSavePreset,
                onNewCalibration = onNewCalibration,
                onFlipBoard = onFlipBoard,
                onResetGame = onResetGame,
                onOpenAdvancedSettings = {
                    hideMainControlMenu()
                    onOpenAdvancedSettings()
                },
                onHideOverlay = {
                    hideAllDialogs()
                    onHideOverlay()
                },
                onExitService = {
                    hideAllDialogs()
                    onExitService()
                },
                onClose = {
                    hideMainControlMenu()
                    onClose()
                }
            )

            windowManager.addView(mainControlMenuView, params)
            isMainControlMenuShowing = true
            Log.i(TAG, "Main control menu displayed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show main control menu", e)
            hideMainControlMenu()
        }
    }

    fun hideMainControlMenu() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideMainControlMenu() }
            return
        }
        try {
            val v = mainControlMenuView
            if (v != null && windowManager != null) {
                if (v.isAttachedToWindow) {
                    windowManager.removeViewImmediate(v)
                } else {
                    windowManager.removeView(v)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove main control menu view: ${e.message}")
        } finally {
            mainControlMenuView = null
            isMainControlMenuShowing = false
        }
    }

    private var settingsOverlayView: com.chessbeater.ui.SettingsOverlayView? = null
    var isSettingsOverlayShowing: Boolean = false
        private set
    var onOpenSettingsOverlayRequested: (() -> Unit)? = null

    /**
     * Displays standalone SettingsOverlayView window in WindowManager
     */
    fun showSettingsOverlay(
        boardBounds: android.graphics.RectF,
        onBackToMainMenu: (() -> Unit)? = null,
        onClose: () -> Unit
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showSettingsOverlay(boardBounds, onBackToMainMenu, onClose) }
            return
        }

        if (windowManager == null || !canDrawOverlays(context)) return

        try {
            hideAllDialogs()
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val density = context.resources.displayMetrics.density
            val widthPx = boardBounds.width().toInt().coerceAtLeast((300 * density).toInt())
            val heightPx = (boardBounds.height() * 0.95f).toInt().coerceAtLeast((340 * density).toInt())

            val params = WindowManager.LayoutParams(
                widthPx,
                heightPx,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = boardBounds.left.toInt()
                y = boardBounds.top.toInt()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            settingsOverlayView = com.chessbeater.ui.SettingsOverlayView(
                context = context,
                onBackToMainMenu = {
                    hideSettingsOverlay()
                    interactiveBoardView?.reloadVisualSettingsOnly()
                    onBackToMainMenu?.invoke()
                },
                onVisualSettingsChanged = {
                    interactiveBoardView?.reloadVisualSettingsOnly()
                },
                onClose = {
                    hideSettingsOverlay()
                    interactiveBoardView?.reloadVisualSettingsOnly()
                    onClose()
                }
            )

            windowManager.addView(settingsOverlayView, params)
            isSettingsOverlayShowing = true
            Log.i(TAG, "Settings overlay window displayed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show settings overlay window", e)
            hideSettingsOverlay()
        }
    }

    fun hideSettingsOverlay() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideSettingsOverlay() }
            return
        }
        try {
            val v = settingsOverlayView
            if (v != null && windowManager != null) {
                if (v.isAttachedToWindow) {
                    windowManager.removeViewImmediate(v)
                } else {
                    windowManager.removeView(v)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove settings overlay view: ${e.message}")
        } finally {
            settingsOverlayView = null
            isSettingsOverlayShowing = false
        }
    }

    fun hideAllDialogs() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideAllDialogs() }
            return
        }
        hideMainControlMenu()
        hideSettingsOverlay()
        hideQuickSideSelector()
        hideQuickAlignmentOverlay()
        hideCalibrationOverlay()
    }

    /**
     * Updates board recommendation arrow
     */
    fun updateBoardArrow(
        bestMove: String?,
        evalCentipawns: Int?,
        mateInMoves: Int?,
        boardRect: Rect?,
        orientation: PlayerColor = PlayerColor.WHITE
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateBoardArrow(bestMove, evalCentipawns, mateInMoves, boardRect, orientation) }
            return
        }
        boardArrowView?.updateArrow(bestMove, evalCentipawns, mateInMoves, boardRect, orientation)
    }

    /**
     * Updates Floating HUD values
     */
    fun updateHud(
        bestMove: String,
        evalCentipawns: Int?,
        mateInMoves: Int?,
        depth: Int,
        latencyMs: Long
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateHud(bestMove, evalCentipawns, mateInMoves, depth, latencyMs) }
            return
        }
        floatingHudView?.updateData(bestMove, evalCentipawns, mateInMoves, depth, latencyMs)
    }

    /**
     * Updates Floating HUD diagnostic telemetry
     */
    fun updateDiagnostic(
        fps: Float,
        isBoardDetected: Boolean,
        isEngineCalculating: Boolean
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateDiagnostic(fps, isBoardDetected, isEngineCalculating) }
            return
        }
        floatingHudView?.updateDiagnostic(fps, isBoardDetected, isEngineCalculating)
    }

    /**
     * Clears board arrow canvas
     */
    fun clearBoardArrow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { clearBoardArrow() }
            return
        }
        boardArrowView?.clearArrow()
    }

    /**
     * Updates player color indicator on HUD
     */
    fun setPlayerColor(color: PlayerColor) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setPlayerColor(color) }
            return
        }
        floatingHudView?.setPlayerColor(color)
    }

    /**
     * Clears all rendered arrows and HUD values
     */
    fun clearOverlays() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { clearOverlays() }
            return
        }
        boardArrowView?.clearArrow()
    }

    /**
     * Removes and cleans up all overlay views from WindowManager
     */
    fun hideOverlays() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideOverlays() }
            return
        }
        hideCalibrationOverlay()
        hideInteractiveBoard()
        hideFloatingEye()
        if (!isOverlaysShowing) return

        try {
            boardArrowView?.let {
                if (it.isAttachedToWindow) {
                    windowManager?.removeView(it)
                }
                boardArrowView = null
            }
            floatingHudView?.let {
                if (it.isAttachedToWindow) {
                    windowManager?.removeView(it)
                }
                floatingHudView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay views", e)
        } finally {
            isOverlaysShowing = false
            arrowParams = null
            hudParams = null
        }
    }

    fun isShowing(): Boolean = isOverlaysShowing
    fun isCalibrating(): Boolean = isCalibrating
    fun isInteractiveBoardShowing(): Boolean = isInteractiveBoardShowing
    fun isFloatingEyeShowing(): Boolean = isFloatingEyeShowing
}
