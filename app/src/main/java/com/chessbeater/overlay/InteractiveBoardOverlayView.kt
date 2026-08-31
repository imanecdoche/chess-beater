package com.chessbeater.overlay

import android.annotation.SuppressLint
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import com.chessbeater.data.CalibrationPreset
import com.chessbeater.service.ChessAccessibilityService
import com.chessbeater.engine.ChessLogic
import com.chessbeater.engine.models.EngineResult
import com.chessbeater.vision.models.PlayerColor
import com.chessbeater.util.*
import kotlinx.coroutines.*
import kotlin.math.*
import com.chessbeater.R
/**
 * Sprint 35, 36, 37, & 38: InteractiveBoardOverlayView
 * - Undo Move Feature with Snapshot State
 * - Board Position Correction Mode (Interactive Board Editor)
 * - Dual-Pass Solid Filled White Pieces with Sharp Contrast Outlines
 * - Full Castling Support (Kingside O-O & Queenside O-O-O)
 * - Multi-Preset Calibration Menu & Package Binding Auto-Switching
 * - Interactive GUI Sliders for Transparencies & Auto-Hide
 */
@SuppressLint("ViewConstructor")
class InteractiveBoardOverlayView(
    context: Context,
    private val onDragListener: (dx: Int, dy: Int) -> Unit,
    private val onScaleListener: (scaleFactor: Float) -> Unit = {},
    private val onCloseListener: () -> Unit,
    private val onEvaluateRequested: (fen: String) -> Unit,
    private val onThinkingTimeChanged: (Long) -> Unit = {},
    var onSnapToCalibrationRequested: (() -> Unit)? = null,
    var onStartCalibrationRequested: (() -> Unit)? = null,
    var onPresetSelected: ((CalibrationPreset) -> Unit)? = null,
    var onSaveCurrentPositionToPresetRequested: (() -> Unit)? = null,
    var onArrowDurationChanged: ((Long) -> Unit)? = null,
    var onToggleVisibilityRequested: ((hide: Boolean, reason: OverlayHideReason) -> Unit)? = null,
    var onVisualPreferencesChanged: ((com.chessbeater.data.BoardVisualPreferences) -> Unit)? = null,
    var onClickThroughModeToggled: ((Boolean) -> Unit)? = null,
    var onAutoDetectionToggled: ((Boolean) -> Unit)? = null,
    var onEloRatingChanged: ((Int) -> Unit)? = null,
    var onOpenSettingsRequested: (() -> Unit)? = null,
    private var boardSizePx: Int = 600,
    var isGhostMode: Boolean = false,
    var isTouchForwarding: Boolean = true,
    var isPiecesHiddenInGhostMode: Boolean = true,
    var arrowDurationMs: Long = 1000L,
    var gridAlpha: Float = 0.15f,
    var pieceAlpha: Float = 1.0f,
    var highlightAlpha: Float = 0.65f,
    var arrowAlpha: Float = 0.90f,
    var floatingEyeAlpha: Float = 0.85f,
    var autoHideDelaySec: Int = -1,
    var eyeSizeDp: Int = 72,
    var isClickThroughMode: Boolean = false,
    var isAutoShowEnabled: Boolean = false,
    var autoShowDelaySec: Int = 2,
    var isAutoDetectionEnabled: Boolean = false,
    var eloRating: Int = 2200
) : View(context) {

    private val viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var evalTimeoutJob: Job? = null
    private var arrowDismissJob: Job? = null
    private var autoHideJob: Job? = null
    private var isArrowVisible: Boolean = true

    data class PieceAnimation(
        val pieceChar: Char,
        val fromSq: Int,
        val toSq: Int,
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        var currentX: Float = startX,
        var currentY: Float = startY
    )
    private var activePieceAnim: PieceAnimation? = null
    private var pieceMoveAnimator: ValueAnimator? = null

    // Arrow Duration choices: 1000ms, 2000ms, 3000ms, -1L (Selamanya)
    private val arrowDurations = listOf(1000L, 2000L, 3000L, -1L)

    val HEADER_HEIGHT_DP = 48f
    val headerHeightPx: Float
        get() = HEADER_HEIGHT_DP * resources.displayMetrics.density

    // --- Board State ---
    private val board = CharArray(64) { '.' }
    private var castlingRights: String = "KQkq"
    private var halfMoveClock: Int = 0
    private var fullMoveNumber: Int = 1

    private var opponentColor: PlayerColor = PlayerColor.BLACK
    private var stockfishColor: PlayerColor = PlayerColor.WHITE
    private var currentTurn: PlayerColor = PlayerColor.WHITE
    private var isBoardFlipped: Boolean = false

    private var selectedSquare: Int? = null
    private val legalDestinations = mutableListOf<Int>()
    private var lastMoveFrom: Int? = null
    private var lastMoveTo: Int? = null

    private var engineBestMove: String? = null
    private var evalText: String = ""
    private var isEngineCalculating: Boolean = false

    // Thinking Time Modes: 0.5s, 1.0s, 2.0s, 3.0s
    private val thinkingTimes = listOf(500L, 1000L, 2000L, 3000L)
    private var currentThinkingTimeIndex = 1 // Default: 1.0s

    // Move History Log & Snapshot History for Undo
    private val moveHistory = mutableListOf<String>()

    data class BoardSnapshot(
        val boardState: CharArray,
        val currentTurn: PlayerColor,
        val castlingRights: String,
        val halfMoveClock: Int = 0,
        val fullMoveNumber: Int = 1,
        val lastMoveFrom: Int?,
        val lastMoveTo: Int?,
        val lastEngineBestMove: String?,
        val lastEvalText: String?,
        val historyLog: List<String>
    )

    private val snapshotHistory = ArrayDeque<BoardSnapshot>()

    // Board Editor / Correction Mode State
    var isCorrectionMode: Boolean = false
        private set
    private var selectedEditorPiece: Char? = null // 'P','N','B','R','Q','K','p','n','b','r','q','k','X'(Delete), or null
    private val editorPaletteRects = mutableListOf<Pair<RectF, Char>>()
    private val editorActionBtnRects = mutableListOf<Pair<RectF, String>>() // "TURN", "CLEAR", "DONE"

    // Modal Menu Navigation
    enum class MenuPage {
        MAIN,
        APPEARANCE,
        PRESETS
    }
    private var currentMenuPage = MenuPage.MAIN

    enum class SettingsTab {
        DISPLAY_ENGINE,
        ANTI_CHEAT
    }
    private var currentSettingsTab = SettingsTab.DISPLAY_ENGINE
    private val settingsTab1Rect = RectF()
    private val settingsTab2Rect = RectF()
    private var settingsScrollY = 0f
    private var maxSettingsScrollY = 0f

    // Presets Cache
    private val presetsList = mutableListOf<CalibrationPreset>()
    private var activePresetId: String? = null
    private val presetItemRects = mutableListOf<RectF>()
    private val presetBottomBtnRects = mutableListOf<RectF>()

    // Modal Menu State
    var isMenuOpen: Boolean = false
        private set

    // Touch Coordinates & Bounds
    private val btnMenuBounds = RectF()
    private val btnEyeBounds = RectF()
    private val headerBounds = RectF()
    private val boardRect = RectF()
    private val modalCardRect = RectF()
    private val modalCloseBtnRect = RectF()
    private val modalItemRects = mutableListOf<RectF>()

    // Slider Specs for Appearance Page
    enum class SliderType {
        ELO_RATING,
        MAX_ELO_RATING,
        OVERLAY_TRANSPARENCY,
        HUMANIZE_LEVEL,
        GRID_ALPHA,
        PIECE_ALPHA,
        HIGHLIGHT_ALPHA,
        ARROW_ALPHA,
        MOVE_GUIDE_ALPHA,
        FLOATING_EYE_ALPHA,
        AUTO_HIDE_DELAY,
        AUTO_SHOW_DELAY
    }

    data class SliderSpec(
        val type: SliderType,
        val label: String,
        val valueText: String,
        val progress: Float,
        val bounds: RectF = RectF(),
        val trackRect: RectF = RectF(),
        val thumbHitRect: RectF = RectF()
    )

    private val activeSliders = mutableListOf<SliderSpec>()
    private var activeDraggingSlider: SliderType? = null
    private val appearanceBottomBtnRects = mutableListOf<RectF>()

    var moveGuideAlpha: Float = 0.80f
    var maxEloRating: Int = 3500

    // 2-Column Settings Interactive Buttons & Toggles
    private val statusBounds = RectF()
    private val editorBounds = RectF()

    val boardBounds: RectF
        get() = boardRect

    val finishButtonBounds = RectF()
    val clearBoardButtonBounds = RectF()
    val turnToggleButtonBounds = RectF()
    val deleteToolButtonBounds = RectF()
    val correctionPanelBounds = RectF()
    val palettePieceBounds = mutableMapOf<Char, RectF>()
    var selectedPalettePiece: Char?
        get() = selectedEditorPiece
        set(value) { selectedEditorPiece = value }

    private val lockBoardToggleRect = RectF()
    private val ghostControlsToggleRect = RectF()
    private val humanizeToggleRect = RectF()
    private val blunderGuardToggleRect = RectF()
    private val naturalDelayToggleRect = RectF()
    private val flipBoardBtnRect = RectF()
    private val resetBoardBtnRect = RectF()
    private val highlightStyleBtnRect = RectF()

    var isBoardLocked: Boolean = true
    var isGhostControlsEnabled: Boolean = false

    private var isHighlightFilled: Boolean = true
    var fromSquareColor: Int = Color.parseColor("#00E5FF")
    var toSquareColor: Int = Color.parseColor("#10B981")
    var sizeHeaderEyeDp: Int = 34
    var sizeHeaderMenuDp: Int = 34
    private val customHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Slider Debounce Jobs
    private var eloDebounceJob: Job? = null
    private var maxEloDebounceJob: Job? = null
    private var humanizeSaveDebounceJob: Job? = null

    private var rawDownX = 0f
    private var rawDownY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private val touchSlop = 14f
    private var activePointerId = -1

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!isMenuOpen && !isCorrectionMode) {
                    onScaleListener(detector.scaleFactor)
                }
                return true
            }
        })

    // Paints
    private val lightSqClassicPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(238, 238, 210) }
    private val darkSqClassicPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(118, 150, 86) }
    private val lightSqGhostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(18, 255, 255, 255) }
    private val darkSqGhostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(32, 0, 0, 0) }

    // Neo piece bitmap caching
    private val pieceDrawableMap: Map<Char, Int> = mapOf(
        'P' to R.drawable.piece_neo_wp,
        'N' to R.drawable.piece_neo_wn,
        'B' to R.drawable.piece_neo_wb,
        'R' to R.drawable.piece_neo_wr,
        'Q' to R.drawable.piece_neo_wq,
        'K' to R.drawable.piece_neo_wk,
        'p' to R.drawable.piece_neo_bp,
        'n' to R.drawable.piece_neo_bn,
        'b' to R.drawable.piece_neo_bb,
        'r' to R.drawable.piece_neo_br,
        'q' to R.drawable.piece_neo_bq,
        'k' to R.drawable.piece_neo_bk
    )
    private val pieceBitmapCache = mutableMapOf<Char, Bitmap>()
    private val pieceBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        clearShadowLayer()
        isDither = true
        isFilterBitmap = true
    }

    private fun refreshPieceBitmaps(boardPx: Int) {
        val sqSize = boardPx / 8f
        val targetSize = (sqSize * 0.82f).roundToInt()
        pieceDrawableMap.forEach { (char, resId) ->
            val src = BitmapFactory.decodeResource(resources, resId)
            if (src != null) {
                val scaled = Bitmap.createScaledBitmap(src, targetSize, targetSize, true)
                pieceBitmapCache[char] = scaled
            }
        }
    }

    private val selFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(160, 255, 215, 0) }
    private val selStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.rgb(255, 235, 59) }
    private val lastMoveFromPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(120, 205, 220, 57) }
    private val lastMoveToPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(150, 0, 230, 118) }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(220, 0, 230, 118) }
    private val dotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4.5f; color = Color.rgb(0, 255, 128) }

    // Dual-Pass Solid Filled Piece Paints
    private val whitePieceFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val whitePieceStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        color = Color.rgb(15, 23, 42) // Dark Navy Slate #0F172A
        strokeJoin = Paint.Join.ROUND
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val blackPieceFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(17, 24, 39) // Deep Black #111827
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val blackPieceStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
        color = Color.argb(180, 255, 255, 255) // Thin white halo
        strokeJoin = Paint.Join.ROUND
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val statusBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 15, 23, 42); style = Paint.Style.FILL }
    private val statusBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.argb(160, 51, 65, 85) }
    private val statusTxtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 230, 118); textAlign = Paint.Align.LEFT }
    private val calcTxtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 171, 0); textAlign = Paint.Align.LEFT }
    private val histTxtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(148, 163, 184); textAlign = Paint.Align.LEFT }

    // Header & Menu Button Paints
    private val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 15, 23, 42); style = Paint.Style.FILL }
    private val headerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.argb(160, 51, 65, 85) }
    private val menuBtnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(220, 30, 41, 59) }
    private val menuBtnActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(0, 230, 118) }
    private val menuBtnTxtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.WHITE }
    private val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.rgb(226, 232, 240) }

    // Modal Dialog Paints (#1A1D24 solid slate card & #2E3440 border)
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(220, 10, 13, 20) }
    private val modalBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(26, 29, 36) } // #1A1D24
    private val modalBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f; color = Color.rgb(46, 52, 64) } // #2E3440
    private val modalTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.rgb(56, 189, 248) } // #38BDF8
    private val modalItemBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(33, 38, 48) }
    private val modalItemActiveBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(16, 185, 129) }
    private val modalItemTxtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.rgb(226, 232, 240) }
    private val modalCloseTxtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.rgb(148, 163, 184) }

    // Slider Paints (#38BDF8 accents & #E2E8F0 labels)
    private val sliderTrackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(51, 65, 85) } // #334155
    private val sliderTrackFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(56, 189, 248) } // #38BDF8
    private val sliderThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
    private val sliderThumbBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.rgb(56, 189, 248) }
    private val sliderLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.rgb(226, 232, 240) } // #E2E8F0
    private val sliderValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.rgb(56, 189, 248) } // #38BDF8
    private val navBottomBtnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(51, 65, 85) } // #334155
    private val navBottomBtnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.rgb(71, 85, 105) } // #475569

    // Editor Palette Paints (100% Solid & Independent)
    private val editorBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(15, 23, 42); style = Paint.Style.FILL }
    private val editorBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.argb(160, 51, 65, 85) }
    private val editorSelectedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 230, 118); style = Paint.Style.FILL }
    private val editorSelectedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 230, 118); style = Paint.Style.STROKE; strokeWidth = 3f }
    private val editorItemBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 30, 41, 59); style = Paint.Style.FILL }
    private val editorActionBtnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 30, 41, 59); style = Paint.Style.FILL }
    private val editorActionDoneBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 230, 118); style = Paint.Style.FILL }
    private val editorActionTxtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.WHITE }
    private val palettePiecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 255 }
    private val palettePieceWhiteStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.rgb(15, 23, 42)
        alpha = 255
    }
    private val palettePieceWhiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        alpha = 255
    }
    private val palettePieceBlackStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
        alpha = 255
    }
    private val palettePieceBlackFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(15, 23, 42)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        alpha = 255
    }

    // Trajectory Arrow Paints
    private val arrowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.argb(230, 0, 230, 118) }
    private val arrowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(230, 0, 230, 118) }
    private val arrowPath = Path()
    private var overlayAlpha: Float = 0.95f

    // Board Correction Mode Transparency Backups
    private var backupPieceAlpha = 1.0f
    private var backupGridAlpha = 0.85f
    private var backupHighlightAlpha = 0.50f
    private var backupArrowAlpha = 0.95f
    private var backupMoveGuideAlpha = 0.80f

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setOpponentColor(PlayerColor.BLACK)
        try {
            val dm = context.resources.displayMetrics
            val defSize = (dm.widthPixels * 0.94f).coerceIn(280f, dm.widthPixels.toFloat())
            val defL = (dm.widthPixels - defSize) / 2f
            val defT = (dm.heightPixels - defSize) / 2f

            val prefs = context.getSharedPreferences("chessbeater_visual_prefs", Context.MODE_PRIVATE)
            val savedAlpha = prefs.getSafeFloat("overlay_alpha", 0.95f)
            overlayAlpha = savedAlpha
            this.alpha = savedAlpha
            isHighlightFilled = prefs.getSafeBoolean("highlight_is_filled", true)
            fromSquareColor = prefs.getSafeInt("color_highlight_from", Color.parseColor("#00E5FF"))
            toSquareColor = prefs.getSafeInt("color_highlight_to", Color.parseColor("#10B981"))
            pieceAlpha = prefs.getSafeFloat("piece_alpha", 1.0f)
            gridAlpha = prefs.getSafeFloat("grid_alpha", 0.85f)
            arrowAlpha = prefs.getSafeFloat("arrow_alpha", 0.95f)
            highlightAlpha = prefs.getSafeFloat("highlight_alpha", 0.50f)
            floatingEyeAlpha = prefs.getSafeFloat("floating_eye_alpha", 0.85f)
            moveGuideAlpha = prefs.getSafeFloat("move_guide_alpha", 0.80f)
            maxEloRating = prefs.getSafeInt("max_elo_rating", 3500)
            sizeHeaderEyeDp = prefs.getSafeInt("size_header_eye_dp", 34)
            sizeHeaderMenuDp = prefs.getSafeInt("size_header_menu_dp", 34)
            isBoardLocked = prefs.getSafeBoolean("board_is_locked", true)
            isGhostControlsEnabled = prefs.getSafeBoolean("ghost_controls_enabled", false)

            val bL = prefs.getSafeFloat("board_left", defL)
            val bT = prefs.getSafeFloat("board_top", defT)
            val bR = prefs.getSafeFloat("board_right", bL + defSize)
            val bB = prefs.getSafeFloat("board_bottom", bT + defSize)
            boardRect.set(bL, bT, bR, bB)
            boardSizePx = boardRect.width().toInt().coerceIn(280, 1400)
        } catch (ignored: Exception) {}
    }

    fun reloadBoardBounds() {
        try {
            val prefs = context.getSharedPreferences("chessbeater_visual_prefs", Context.MODE_PRIVATE)
            val left = prefs.getSafeFloat("board_left", 0f)
            val top = prefs.getSafeFloat("board_top", 0f)
            val right = prefs.getSafeFloat("board_right", 0f)
            val bottom = prefs.getSafeFloat("board_bottom", 0f)

            if (right > left && bottom > top) {
                boardRect.set(left, top, right, bottom)
                boardSizePx = boardRect.width().toInt().coerceIn(280, 1400)
                postInvalidate()
                Log.d("InteractiveBoard", "✅ Bounds berhasil dimuat ulang: $boardRect")
            }
        } catch (e: Exception) {
            Log.w("InteractiveBoard", "Gagal reloadBoardBounds", e)
        }
    }

    fun reloadVisualSettings() {
        loadAlphaPreferences()
    }

    fun reloadVisualSettingsOnly() {
        loadAlphaPreferences()
    }

    fun updateVisualPaintsOnly() {
        loadAlphaPreferences()
    }

    fun loadAlphaPreferences() {
        try {
            val prefs = context.getSharedPreferences("chessbeater_visual_prefs", Context.MODE_PRIVATE)
            val savedAlpha = prefs.getSafeFloat("overlay_alpha", 0.95f)
            overlayAlpha = savedAlpha
            this.alpha = savedAlpha
            isHighlightFilled = prefs.getSafeBoolean("highlight_is_filled", true)
            fromSquareColor = prefs.getSafeInt("color_highlight_from", Color.parseColor("#00E5FF"))
            toSquareColor = prefs.getSafeInt("color_highlight_to", Color.parseColor("#10B981"))
            pieceAlpha = prefs.getSafeFloat("alpha_pieces", prefs.getSafeFloat("piece_alpha", prefs.getSafeFloat("pieces_alpha", 1.0f))).coerceIn(0.05f, 1.0f)
            val boardAlpha = prefs.getSafeFloat("board_alpha", prefs.getSafeFloat("grid_alpha", 0.85f)).coerceIn(0.0f, 1.0f)
            gridAlpha = boardAlpha
            arrowAlpha = prefs.getSafeFloat("alpha_arrows", prefs.getSafeFloat("arrow_alpha", 0.95f)).coerceIn(0.05f, 1.0f)
            highlightAlpha = prefs.getSafeFloat("alpha_highlights", prefs.getSafeFloat("highlight_alpha", 0.50f)).coerceIn(0.05f, 1.0f)
            floatingEyeAlpha = prefs.getSafeFloat("alpha_floating_eye", prefs.getSafeFloat("floating_eye_alpha", 0.85f)).coerceIn(0.05f, 1.0f)
            moveGuideAlpha = prefs.getSafeFloat("alpha_dots", prefs.getSafeFloat("move_guide_alpha", prefs.getSafeFloat("guide_dots_alpha", 0.80f))).coerceIn(0.05f, 1.0f)
            maxEloRating = prefs.getSafeInt("max_elo_rating", 3500)
            sizeHeaderEyeDp = prefs.getSafeInt("size_header_eye_dp", 34)
            sizeHeaderMenuDp = prefs.getSafeInt("size_header_menu_dp", 34)
            isBoardLocked = prefs.getSafeBoolean("board_is_locked", true)
            isGhostControlsEnabled = prefs.getSafeBoolean("ghost_controls_enabled", false)

            val isAutoHideOn = prefs.getSafeBoolean("auto_hide_enabled", false)
            val hideDelay = prefs.getSafeFloat("auto_hide_delay_sec", 5.0f)
            autoHideDelaySec = if (isAutoHideOn) hideDelay.toInt().coerceAtLeast(1) else -1

            val gAlphaInt = (gridAlpha * 255).roundToInt().coerceIn(0, 255)
            lightSqClassicPaint.alpha = gAlphaInt
            darkSqClassicPaint.alpha = gAlphaInt
            lightSqGhostPaint.alpha = gAlphaInt
            darkSqGhostPaint.alpha = gAlphaInt

            val pAlphaInt = (pieceAlpha * 255).roundToInt().coerceIn(0, 255)
            pieceBitmapPaint.alpha = pAlphaInt
            whitePieceFillPaint.alpha = pAlphaInt
            whitePieceStrokePaint.alpha = pAlphaInt
            blackPieceFillPaint.alpha = pAlphaInt
            blackPieceStrokePaint.alpha = pAlphaInt

            val aAlphaInt = (arrowAlpha * 255).roundToInt().coerceIn(0, 255)
            arrowFillPaint.alpha = aAlphaInt
            arrowStrokePaint.alpha = aAlphaInt

            reloadBoardBounds()
            postInvalidate()
            Log.d("InteractiveBoard", "✅ Visual paints only & alpha preferences berhasil dimuat ulang!")
        } catch (e: Exception) {
            Log.w("InteractiveBoard", "Gagal loadAlphaPreferences", e)
        }
    }

    private fun drawSquareHighlight(canvas: Canvas, rect: RectF, color: Int) {
        if (isHighlightFilled) {
            customHighlightPaint.style = Paint.Style.FILL
            customHighlightPaint.color = ColorUtils.setAlphaComponent(color, (110 * highlightAlpha).roundToInt().coerceIn(0, 255))
            canvas.drawRect(rect, customHighlightPaint)
        } else {
            customHighlightPaint.style = Paint.Style.STROKE
            val strokeW = 4.5f * resources.displayMetrics.density
            customHighlightPaint.strokeWidth = strokeW
            customHighlightPaint.color = ColorUtils.setAlphaComponent(color, (240 * highlightAlpha).roundToInt().coerceIn(0, 255))
            val strokeOffset = strokeW / 2f
            val insetRect = RectF(
                rect.left + strokeOffset,
                rect.top + strokeOffset,
                rect.right - strokeOffset,
                rect.bottom - strokeOffset
            )
            canvas.drawRoundRect(insetRect, 6f, 6f, customHighlightPaint)
        }
    }

    fun updatePresets(presets: List<CalibrationPreset>, activeId: String?) {
        presetsList.clear()
        presetsList.addAll(presets)
        activePresetId = activeId
        postInvalidate()
    }

    private fun notifyVisualPrefsChanged() {
        try {
            context.getSharedPreferences("chessbeater_visual_prefs", Context.MODE_PRIVATE).edit().apply {
                putFloat("piece_alpha", pieceAlpha)
                putFloat("grid_alpha", gridAlpha)
                putFloat("arrow_alpha", arrowAlpha)
                putFloat("highlight_alpha", highlightAlpha)
                putFloat("floating_eye_alpha", floatingEyeAlpha)
                putFloat("overlay_alpha", overlayAlpha)
                putFloat("move_guide_alpha", moveGuideAlpha)
                putInt("max_elo_rating", maxEloRating)
                putBoolean("highlight_is_filled", isHighlightFilled)
                putInt("color_highlight_from", fromSquareColor)
                putInt("color_highlight_to", toSquareColor)
                putBoolean("board_is_locked", isBoardLocked)
                putBoolean("ghost_controls_enabled", isGhostControlsEnabled)
                putFloat("board_left", boardRect.left)
                putFloat("board_top", boardRect.top)
                putFloat("board_right", boardRect.right)
                putFloat("board_bottom", boardRect.bottom)
                apply()
            }
        } catch (ignored: Exception) {}

        onVisualPreferencesChanged?.invoke(
            com.chessbeater.data.BoardVisualPreferences(
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
        )
        postInvalidate()
    }

    // ====== PUBLIC API ======

    val isCorrectionModeActive: Boolean
        get() = isCorrectionMode

    fun setupCorrectionLayoutMetrics(customTop: Float? = null) {
        val density = resources.displayMetrics.density
        val pad = 6f * density
        val rowH = 38f * density
        val panelHeight = rowH * 3 + pad * 4

        val panelTop = if (customTop != null) {
            customTop
        } else {
            val statusH = (boardBounds.width() * 0.12f).coerceIn(36f, 56f)
            val headerH = headerHeightPx
            val statusBarH = 24f * density
            val headerTop = if (boardBounds.top - headerH - (8f * density) >= statusBarH) {
                boardBounds.top - headerH - (8f * density)
            } else {
                boardBounds.bottom + (8f * density)
            }
            val statusTop = if (headerTop > boardBounds.bottom) headerTop + headerH + (4f * density) else boardBounds.bottom + (4f * density)
            val defaultTop = statusTop + statusH + (4f * density)
            val maxTop = if (height > 0) (height.toFloat() - panelHeight - 10f).coerceAtLeast(boardBounds.bottom + 4f * density) else defaultTop
            defaultTop.coerceAtMost(maxTop)
        }

        correctionPanelBounds.set(
            boardBounds.left,
            panelTop,
            boardBounds.right,
            panelTop + panelHeight
        )
        editorBounds.set(correctionPanelBounds)

        val editorW = correctionPanelBounds.width()
        val editorL = correctionPanelBounds.left

        // 1. Palet Bidak (P, N, B, R, Q, K - Putih & Hitam)
        palettePieceBounds.clear()
        editorPaletteRects.clear()

        // Row 1: White Pieces
        val whitePieces = listOf('P', 'N', 'B', 'R', 'Q', 'K')
        val itemW = (editorW - pad * 7) / 6f
        for (i in whitePieces.indices) {
            val p = whitePieces[i]
            val l = editorL + pad + i * (itemW + pad)
            val t = panelTop + pad
            val rect = RectF(l, t, l + itemW, t + rowH)
            palettePieceBounds[p] = rect
            editorPaletteRects.add(Pair(rect, p))
        }

        // Row 2: Black Pieces
        val blackPieces = listOf('p', 'n', 'b', 'r', 'q', 'k')
        for (i in blackPieces.indices) {
            val p = blackPieces[i]
            val l = editorL + pad + i * (itemW + pad)
            val t = panelTop + pad * 2 + rowH
            val rect = RectF(l, t, l + itemW, t + rowH)
            palettePieceBounds[p] = rect
            editorPaletteRects.add(Pair(rect, p))
        }

        // Row 3: Action Buttons [ 🗑️ Hapus ] [ ⚪/⚫ Giliran ] [ 🔄 Kosongkan ] [ ✅ Selesai ]
        val row3T = panelTop + pad * 3 + rowH * 2
        val btnW = (editorW - pad * 5) / 4f

        deleteToolButtonBounds.set(editorL + pad, row3T, editorL + pad + btnW, row3T + rowH)
        palettePieceBounds['X'] = deleteToolButtonBounds
        editorPaletteRects.add(Pair(deleteToolButtonBounds, 'X'))

        turnToggleButtonBounds.set(editorL + pad * 2 + btnW, row3T, editorL + pad * 2 + btnW * 2, row3T + rowH)
        clearBoardButtonBounds.set(editorL + pad * 3 + btnW * 2, row3T, editorL + pad * 3 + btnW * 3, row3T + rowH)
        finishButtonBounds.set(editorL + pad * 4 + btnW * 3, row3T, editorL + editorW - pad, row3T + rowH)

        editorActionBtnRects.clear()
        editorActionBtnRects.add(Pair(turnToggleButtonBounds, "TURN"))
        editorActionBtnRects.add(Pair(clearBoardButtonBounds, "CLEAR"))
        editorActionBtnRects.add(Pair(finishButtonBounds, "DONE"))
    }

    fun clearAllPiecesForCorrection() {
        board.fill('.')
        selectedSquare = null
        selectedPalettePiece = null
    }

    fun getSquareNameFromCoordinates(x: Float, y: Float): String {
        val sqW = boardBounds.width() / 8f
        val sqH = boardBounds.height() / 8f
        val col = ((x - boardBounds.left) / sqW).toInt().coerceIn(0, 7)
        val row = ((y - boardBounds.top) / sqH).toInt().coerceIn(0, 7)
        val bRow = if (isBoardFlipped) 7 - row else row
        val bCol = if (isBoardFlipped) 7 - col else col
        val sqIdx = bRow * 8 + bCol
        return idx2notation(sqIdx)
    }

    fun setPieceAtSquare(square: String, piece: Char) {
        val sqIdx = notation2idx(square)
        if (sqIdx in 0..63) {
            board[sqIdx] = piece
        }
    }

    fun removePieceAtSquare(square: String) {
        val sqIdx = notation2idx(square)
        if (sqIdx in 0..63) {
            board[sqIdx] = '.'
        }
    }

    fun notation2idx(sqName: String): Int {
        if (sqName.length != 2) return -1
        val file = sqName[0] - 'a'
        val rank = 8 - (sqName[1] - '0')
        if (file !in 0..7 || rank !in 0..7) return -1
        return rank * 8 + file
    }

    fun generateFenFromCurrentBoard(): String {
        return generateFen()
    }

    fun enterCorrectionMode() {
        enterBoardCorrectionMode()
    }

    fun exitCorrectionMode() {
        exitBoardCorrectionMode()
    }

    fun enterBoardCorrectionMode() {
        isCorrectionMode = true
        pieceAlpha = 0.50f       // 50% agar bidak asli game di bawah terlihat
        val alpha50 = 128
        pieceBitmapPaint.alpha = alpha50
        whitePieceFillPaint.alpha = alpha50
        whitePieceStrokePaint.alpha = alpha50
        blackPieceFillPaint.alpha = alpha50
        blackPieceStrokePaint.alpha = alpha50

        selectedPalettePiece = null
        selectedSquare = null
        setupCorrectionLayoutMetrics()
        requestLayout()
        postInvalidate()
        Log.d("BoardCorrection", "🛠️ Masuk mode koreksi papan: Alpha bidak diatur ke 50%.")
    }

    fun exitBoardCorrectionMode() {
        isCorrectionMode = false
        selectedPalettePiece = null
        selectedSquare = null
        loadAlphaPreferences() // Kembalikan transparansi bidak normal dari SharedPreferences

        // Buat FEN baru dari posisi papan hasil editan
        val newFen = generateFenFromCurrentBoard()
        try {
            loadFen(newFen)
            snapshotHistory.clear()
            moveHistory.clear()
            lastMoveFrom = null
            lastMoveTo = null
            engineBestMove = null
            evalText = ""
            isEngineCalculating = false
        } catch (e: Exception) {
            Log.w("BoardCorrection", "Error reloading FEN on exit correction", e)
        }

        requestLayout()
        postInvalidate()
        Toast.makeText(context, "✅ Posisi papan berhasil diperbarui!", Toast.LENGTH_SHORT).show()
        checkAndTriggerStockfishTurn()
        Log.d("BoardCorrection", "✅ Keluar dari mode koreksi. FEN baru: $newFen")
    }

    fun toggleCorrectionMode() {
        if (isCorrectionMode) {
            exitBoardCorrectionMode()
        } else {
            enterBoardCorrectionMode()
        }
    }

    fun getCurrentTurn(): PlayerColor = currentTurn
    fun getOpponentColor(): PlayerColor = opponentColor
    fun getBoardArray(): CharArray = board.clone()
    fun getIsBoardFlipped(): Boolean = isBoardFlipped
    fun reloadBoardOrientation() {
        try {
            val prefs = context.getSharedPreferences("chessbeater_visual_prefs", Context.MODE_PRIVATE)
            val flipped = prefs.getBoolean("is_board_flipped", isBoardFlipped)
            isBoardFlipped = flipped
            val engineSideStr = prefs.getString("engine_side", null)
            if (engineSideStr != null) {
                val engineColor = if (engineSideStr.equals("BLACK", ignoreCase = true)) PlayerColor.BLACK else PlayerColor.WHITE
                val oppColor = if (engineColor == PlayerColor.BLACK) PlayerColor.WHITE else PlayerColor.BLACK
                opponentColor = oppColor
                stockfishColor = engineColor
            }
            postInvalidate()
        } catch (ignored: Exception) {}
    }
    fun getCastlingRights(): String = castlingRights
    fun isOpponentTurn(): Boolean = (currentTurn == opponentColor && !isCorrectionMode)

    fun executeExternalMove(from: Int, to: Int, isStockfish: Boolean = false) {
        executeMove(from, to, isStockfish)
    }

    fun resetBoard() {
        if (isCorrectionMode) {
            exitBoardCorrectionMode()
        }
        evalTimeoutJob?.cancel()
        arrowDismissJob?.cancel()
        autoHideJob?.cancel()
        isArrowVisible = false
        castlingRights = "KQkq"
        halfMoveClock = 0
        fullMoveNumber = 1
        snapshotHistory.clear()
        loadFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        selectedSquare = null
        legalDestinations.clear()
        lastMoveFrom = null
        lastMoveTo = null
        engineBestMove = null
        evalText = ""
        moveHistory.clear()
        isEngineCalculating = false
        currentTurn = PlayerColor.WHITE
        postInvalidate()
        checkAndTriggerStockfishTurn()
    }

    private fun createCurrentSnapshot(): BoardSnapshot = BoardSnapshot(
        boardState = board.copyOf(),
        currentTurn = currentTurn,
        castlingRights = castlingRights,
        halfMoveClock = halfMoveClock,
        fullMoveNumber = fullMoveNumber,
        lastMoveFrom = lastMoveFrom,
        lastMoveTo = lastMoveTo,
        lastEngineBestMove = engineBestMove,
        lastEvalText = evalText,
        historyLog = moveHistory.toList()
    )

    fun undoLastMove() {
        if (snapshotHistory.isEmpty()) {
            Toast.makeText(context, "Belum ada langkah untuk di-undo", Toast.LENGTH_SHORT).show()
            return
        }

        evalTimeoutJob?.cancel()
        arrowDismissJob?.cancel()
        autoHideJob?.cancel()
        isEngineCalculating = false

        // Pop last snapshot. If the last move was from engine, pop one more to revert to player's turn
        var targetSnapshot = snapshotHistory.removeLast()
        if (targetSnapshot.currentTurn != opponentColor && snapshotHistory.isNotEmpty()) {
            targetSnapshot = snapshotHistory.removeLast()
        }

        targetSnapshot.boardState.copyInto(board)
        currentTurn = targetSnapshot.currentTurn
        castlingRights = targetSnapshot.castlingRights
        halfMoveClock = targetSnapshot.halfMoveClock
        fullMoveNumber = targetSnapshot.fullMoveNumber
        lastMoveFrom = targetSnapshot.lastMoveFrom
        lastMoveTo = targetSnapshot.lastMoveTo
        engineBestMove = targetSnapshot.lastEngineBestMove
        evalText = targetSnapshot.lastEvalText ?: ""
        moveHistory.clear()
        moveHistory.addAll(targetSnapshot.historyLog)

        selectedSquare = null
        legalDestinations.clear()
        startArrowDismissTimer()
        postInvalidate()
        Toast.makeText(context, "↺ Langkah berhasil di-undo!", Toast.LENGTH_SHORT).show()
    }

    fun switchSideAndResetGame(opponentIsWhite: Boolean) {
        evalTimeoutJob?.cancel()
        arrowDismissJob?.cancel()
        autoHideJob?.cancel()

        if (opponentIsWhite) {
            // Lawan Putih: User Putih (Atas), Engine Hitam (Bawah) -> Board Flipped
            opponentColor = PlayerColor.WHITE
            stockfishColor = PlayerColor.BLACK
            isBoardFlipped = true
        } else {
            // Lawan Hitam: User Hitam (Atas), Engine Putih (Bawah) -> Board Normal
            opponentColor = PlayerColor.BLACK
            stockfishColor = PlayerColor.WHITE
            isBoardFlipped = false
        }

        try {
            context.getSharedPreferences("chessbeater_visual_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("is_board_flipped", isBoardFlipped)
                .putString("engine_side", if (opponentIsWhite) "BLACK" else "WHITE")
                .apply()
        } catch (ignored: Exception) {}

        // Total reset bersih ke FEN awal
        isArrowVisible = false
        castlingRights = "KQkq"
        halfMoveClock = 0
        fullMoveNumber = 1
        snapshotHistory.clear()
        loadFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        selectedSquare = null
        legalDestinations.clear()
        lastMoveFrom = null
        lastMoveTo = null
        engineBestMove = null
        evalText = ""
        moveHistory.clear()
        isEngineCalculating = false
        currentTurn = PlayerColor.WHITE
        postInvalidate()

        if (stockfishColor == PlayerColor.WHITE) {
            checkAndTriggerStockfishTurn() // Mesin Putih langsung ambil langkah pertama otomatis
        }
        Log.d("ChessGame", "🔄 Ganti sisi terdeteksi: Papan otomatis di-reset bersih ke FEN awal.")
    }

    fun setOpponentColor(color: PlayerColor) {
        switchSideAndResetGame(color == PlayerColor.WHITE)
    }

    fun flipBoard() {
        isBoardFlipped = !isBoardFlipped
        postInvalidate()
    }

    private fun checkAndTriggerStockfishTurn() {
        if (currentTurn == stockfishColor && !isCorrectionMode) {
            isEngineCalculating = true
            triggerEval()
        }
    }

    fun toggleThinkingTime() {
        currentThinkingTimeIndex = (currentThinkingTimeIndex + 1) % thinkingTimes.size
        val newTime = thinkingTimes[currentThinkingTimeIndex]
        onThinkingTimeChanged(newTime)
        postInvalidate()
    }

    fun toggleArrowDuration() {
        val currentIndex = arrowDurations.indexOf(arrowDurationMs).takeIf { it >= 0 } ?: 0
        val nextIndex = (currentIndex + 1) % arrowDurations.size
        arrowDurationMs = arrowDurations[nextIndex]
        onArrowDurationChanged?.invoke(arrowDurationMs)
        startArrowDismissTimer()
        postInvalidate()
    }

    private fun startArrowDismissTimer() {
        arrowDismissJob?.cancel()
        isArrowVisible = true
        if (arrowDurationMs > 0) {
            arrowDismissJob = viewScope.launch(Dispatchers.Main) {
                delay(arrowDurationMs)
                isArrowVisible = false
                postInvalidate()
            }
        } else {
            isArrowVisible = true
        }
    }

    private fun triggerAutoHideIfNeeded() {
        if (autoHideDelaySec >= 0 && !isEngineCalculating && !isCorrectionMode) {
            autoHideJob?.cancel()
            if (autoHideDelaySec == 0) {
                onToggleVisibilityRequested?.invoke(true, OverlayHideReason.AUTO_HIDE)
            } else {
                autoHideJob = viewScope.launch(Dispatchers.Main) {
                    delay(autoHideDelaySec * 1000L)
                    if (!isEngineCalculating && !isCorrectionMode) {
                        Log.d("AutoHide", "⏱️ Auto-Hide triggered after $autoHideDelaySec s")
                        onToggleVisibilityRequested?.invoke(true, OverlayHideReason.AUTO_HIDE)
                    }
                }
            }
        }
    }

    fun getBoardSize(): Int = boardRect.width().toInt()

    fun updateBoardRect(rect: RectF) {
        boardRect.set(rect)
        boardSizePx = rect.width().toInt().coerceIn(280, 1400)
        refreshPieceBitmaps(boardSizePx)
        try {
            context.getSharedPreferences("chessbeater_visual_prefs", Context.MODE_PRIVATE)
                .edit()
                .putFloat("board_left", rect.left)
                .putFloat("board_top", rect.top)
                .putFloat("board_right", rect.right)
                .putFloat("board_bottom", rect.bottom)
                .apply()
        } catch (ignored: Exception) {}
        postInvalidate()
    }

    fun updateBoardSize(newSizePx: Int) {
        boardSizePx = newSizePx.coerceIn(280, 1400)
        boardRect.right = boardRect.left + boardSizePx
        boardRect.bottom = boardRect.top + boardSizePx
        refreshPieceBitmaps(boardSizePx)
        try {
            context.getSharedPreferences("chessbeater_visual_prefs", Context.MODE_PRIVATE)
                .edit()
                .putFloat("board_right", boardRect.right)
                .putFloat("board_bottom", boardRect.bottom)
                .apply()
        } catch (ignored: Exception) {}
        postInvalidate()
    }

    fun onEngineResult(result: EngineResult?) {
        evalTimeoutJob?.cancel()
        isEngineCalculating = false

        if (isCorrectionMode) return

        val bestMove = result?.bestMove?.takeIf { it.length >= 4 && it != "0000" } ?: run {
            Toast.makeText(context, "⚠️ Engine tidak merespons, input langkah manual", Toast.LENGTH_SHORT).show()
            postInvalidate()
            return
        }

        engineBestMove = bestMove
        isArrowVisible = true
        Log.d("StockfishSuccess", "🏹 Menggambar panah untuk langkah: $bestMove")
        evalText = when {
            result?.mateInMoves != null -> if (result.mateInMoves > 0) "#${result.mateInMoves}" else "-#${abs(result.mateInMoves)}"
            result?.evaluationCentipawns != null -> {
                val s = result.evaluationCentipawns / 100.0
                if (s >= 0) "+%.2f".format(s) else "%.2f".format(s)
            }
            else -> ""
        }

        Log.d("StockfishNative", "Engine BestMove: $bestMove | CurrentTurn: $currentTurn")
        startArrowDismissTimer()
        applyEngineMove(bestMove)

        // Trigger Auto-Hide hanya setelah Stockfish selesai evaluasi dan panah terlihat
        triggerAutoHideIfNeeded()
    }

    fun applyEngineMove(uciMove: String) {
        isEngineCalculating = false

        if (uciMove.length < 4 || uciMove == "0000") {
            postInvalidate()
            return
        }

        val fromCol = uciMove[0].lowercaseChar() - 'a'
        val fromRank = uciMove[1] - '1'
        val toCol = uciMove[2].lowercaseChar() - 'a'
        val toRank = uciMove[3] - '1'

        if (fromCol !in 0..7 || toCol !in 0..7 || fromRank !in 0..7 || toRank !in 0..7) {
            postInvalidate()
            return
        }

        val fromRow = 7 - fromRank
        val toRow = 7 - toRank
        val fromIdx = fromRow * 8 + fromCol
        val toIdx = toRow * 8 + toCol
        val promotionChar = if (uciMove.length >= 5) uciMove[4] else null

        val p = board[fromIdx]
        val isExpectedWhite = (currentTurn == PlayerColor.WHITE)
        Log.d("StockfishMove", "Raw UCI: $uciMove | From: ${uciMove.take(2)} (col=$fromCol, rank=$fromRank, idx=$fromIdx) -> To: ${uciMove.substring(2, 4)} (col=$toCol, rank=$toRank, idx=$toIdx) | Bidak Asal: $p")

        // Validate strictly using ChessLogic.isMoveLegal
        val isLegal = ChessLogic.isMoveLegal(fromIdx, toIdx, board, currentTurn, castlingRights)
        if (!isLegal || p == '.' || p.isUpperCase() != isExpectedWhite) {
            Log.e("StockfishError", "Abaikan langkah tidak valid untuk turn saat ini: $uciMove | fromIdx=$fromIdx, toIdx=$toIdx, piece=$p, turn=$currentTurn")
            postInvalidate()
            return
        }

        executeMove(fromIdx, toIdx, isStockfish = true, promotionChar = promotionChar)
    }

    // ====== DRAWING ======

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val headerH = HEADER_HEIGHT_DP * density
        val statusBarH = 24f * density
        val headerTop = if (boardRect.top - headerH - (8f * density) >= statusBarH) {
            boardRect.top - headerH - (8f * density)
        } else {
            boardRect.bottom + (8f * density)
        }
        headerBounds.set(boardRect.left, headerTop, boardRect.right, headerTop + headerH)

        // 1. Header Bar with Title & Action Buttons
        if (!isGhostControlsEnabled || isCorrectionMode) {
            canvas.drawRoundRect(headerBounds, 8f * density, 8f * density, headerBgPaint)
            canvas.drawRoundRect(headerBounds, 8f * density, 8f * density, headerBorderPaint)

            val titleTxtSz = (headerH * 0.42f).coerceIn(11f, 16f)
            headerTitlePaint.textSize = titleTxtSz
            val titleFm = headerTitlePaint.fontMetrics
            val autoBadge = if (isAutoDetectionEnabled) " [AUTO]" else ""
            val modeStr = when {
                isCorrectionMode -> "🛠️ KOREKSI"
                isGhostMode -> "👻 GHOST"
                else -> "♟ KLASIK"
            }
            val bridge = com.chessbeater.engine.StockfishBridge.getInstance(context)
            val isHealthy = bridge.isEngineHealthy()
            val errorBadge = if (!isHealthy) " [ ⚠️ Engine Init... ]" else ""
            val headerText = "Chess Beater • $modeStr$autoBadge$errorBadge"
            headerTitlePaint.color = if (!isHealthy) Color.parseColor("#FFB74D") else Color.WHITE
            canvas.drawText(headerText, headerBounds.left + 12f, headerBounds.centerY() - (titleFm.ascent + titleFm.descent) / 2f, headerTitlePaint)
        }

        // Three Dots Button [⋮] at Top-Right
        val menuBtnSize = (sizeHeaderMenuDp * density).coerceIn(24f * density, 52f * density)
        val eyeBtnSize = (sizeHeaderEyeDp * density).coerceIn(24f * density, 52f * density)

        btnMenuBounds.set(
            headerBounds.right - menuBtnSize - 4f,
            headerBounds.centerY() - menuBtnSize / 2f,
            headerBounds.right - 4f,
            headerBounds.centerY() + menuBtnSize / 2f
        )
        if (!isGhostControlsEnabled || isCorrectionMode) {
            canvas.drawRoundRect(btnMenuBounds, menuBtnSize / 2f, menuBtnSize / 2f, if (isMenuOpen) menuBtnActivePaint else menuBtnBgPaint)
        } else {
            val ghostBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 255, 255, 255); style = Paint.Style.FILL }
            canvas.drawRoundRect(btnMenuBounds, menuBtnSize / 2f, menuBtnSize / 2f, ghostBgPaint)
        }

        menuBtnTxtPaint.textSize = (menuBtnSize * 0.58f).coerceIn(14f * density, 28f * density)
        val btnFm = menuBtnTxtPaint.fontMetrics
        menuBtnTxtPaint.color = if (isGhostControlsEnabled && !isCorrectionMode) Color.argb(100, 255, 255, 255) else if (isMenuOpen) Color.BLACK else Color.WHITE
        canvas.drawText("⋮", btnMenuBounds.centerX(), btnMenuBounds.centerY() - (btnFm.ascent + btnFm.descent) / 2f, menuBtnTxtPaint)

        // Quick Eye Toggle Button [👁] Next to Three Dots Button
        btnEyeBounds.set(
            btnMenuBounds.left - eyeBtnSize - 6f,
            headerBounds.centerY() - eyeBtnSize / 2f,
            btnMenuBounds.left - 6f,
            headerBounds.centerY() + eyeBtnSize / 2f
        )
        if (!isGhostControlsEnabled || isCorrectionMode) {
            canvas.drawRoundRect(btnEyeBounds, eyeBtnSize / 2f, eyeBtnSize / 2f, menuBtnBgPaint)
        } else {
            val ghostBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 255, 255, 255); style = Paint.Style.FILL }
            canvas.drawRoundRect(btnEyeBounds, eyeBtnSize / 2f, eyeBtnSize / 2f, ghostBgPaint)
        }
        menuBtnTxtPaint.color = if (isGhostControlsEnabled && !isCorrectionMode) Color.argb(100, 255, 255, 255) else Color.WHITE
        menuBtnTxtPaint.textSize = (eyeBtnSize * 0.52f).coerceIn(12f * density, 26f * density)
        val eyeFm = menuBtnTxtPaint.fontMetrics
        canvas.drawText("👁", btnEyeBounds.centerX(), btnEyeBounds.centerY() - (eyeFm.ascent + eyeFm.descent) / 2f, menuBtnTxtPaint)

        // 2. 8x8 Chessboard (Rendered EXACTLY on boardRect)
        val sqW = boardRect.width() / 8f
        val sqH = boardRect.height() / 8f
        val sqSz = sqW

        val gAlphaInt = (gridAlpha * 255).roundToInt().coerceIn(0, 255)
        val lightPaint = (if (isGhostMode) lightSqGhostPaint else lightSqClassicPaint).apply { alpha = gAlphaInt }
        val darkPaint = (if (isGhostMode) darkSqGhostPaint else darkSqClassicPaint).apply { alpha = gAlphaInt }

        val hAlphaInt = (highlightAlpha * 255).roundToInt().coerceIn(0, 255)
        val pAlphaInt = (pieceAlpha * 255).roundToInt().coerceIn(0, 255)
        val mgAlphaInt = (moveGuideAlpha * 220).roundToInt().coerceIn(0, 255)
        val mgRingAlphaInt = (moveGuideAlpha * 255).roundToInt().coerceIn(0, 255)
        dotPaint.alpha = mgAlphaInt
        dotRingPaint.alpha = mgRingAlphaInt

        for (r in 0 until 8) {
            for (c in 0 until 8) {
                val bRow = if (isBoardFlipped) 7 - r else r
                val bCol = if (isBoardFlipped) 7 - c else c
                val sq = bRow * 8 + bCol
                val sqL = boardRect.left + c * sqW
                val sqT = boardRect.top + r * sqH

                canvas.drawRect(sqL, sqT, sqL + sqW, sqT + sqH, if ((r + c) % 2 == 0) lightPaint else darkPaint)

                // High-visibility last move highlights
                if (!isCorrectionMode) {
                    val sqRect = RectF(sqL, sqT, sqL + sqW, sqT + sqH)
                    if (sq == lastMoveFrom) {
                        drawSquareHighlight(canvas, sqRect, fromSquareColor)
                    }
                    if (sq == lastMoveTo) {
                        drawSquareHighlight(canvas, sqRect, toSquareColor)
                    }
                }

                // Selected square
                if (sq == selectedSquare) {
                    val sqRect = RectF(sqL, sqT, sqL + sqW, sqT + sqH)
                    drawSquareHighlight(canvas, sqRect, Color.rgb(255, 215, 0))
                }

                // Legal destination markers (only if not correction mode)
                if (!isCorrectionMode && sq in legalDestinations) {
                    val destPiece = board[sq]
                    if (destPiece != '.') {
                        canvas.drawCircle(sqL + sqW / 2f, sqT + sqH / 2f, sqW * 0.40f, dotRingPaint)
                    } else {
                        canvas.drawCircle(sqL + sqW / 2f, sqT + sqH / 2f, sqW * 0.18f, dotPaint)
                    }
                }

                // Draw pieces using cached Neo bitmap images
                val p = board[sq]
                val isMoving = (activePieceAnim != null && (sq == activePieceAnim?.fromSq || sq == activePieceAnim?.toSq))
                if (p != '.' && !isMoving && (!isGhostMode || !isPiecesHiddenInGhostMode)) {
                    val bitmap = pieceBitmapCache[p]
                    if (bitmap != null) {
                        pieceBitmapPaint.alpha = pAlphaInt
                        // Center bitmap within square
                        val left = sqL + (sqW - bitmap.width) / 2f
                        val top = sqT + (sqH - bitmap.height) / 2f
                        canvas.drawBitmap(bitmap, left, top, pieceBitmapPaint)
                    } else {
                        // Fallback to text rendering if bitmap missing
                        val isW = p.isUpperCase()
                        val glyph = piece2unicode(p)
                        val pieceSz = sqW * 0.82f
                        val textX = sqL + sqW / 2f
                        if (isW) {
                            whitePieceStrokePaint.textSize = pieceSz
                            whitePieceStrokePaint.alpha = pAlphaInt
                            whitePieceFillPaint.textSize = pieceSz
                            whitePieceFillPaint.alpha = pAlphaInt
                            val fm = whitePieceFillPaint.fontMetrics
                            val textY = sqT + sqH / 2f - (fm.ascent + fm.descent) / 2f
                            canvas.drawText(glyph, textX, textY, whitePieceStrokePaint)
                            canvas.drawText(glyph, textX, textY, whitePieceFillPaint)
                        } else {
                            blackPieceStrokePaint.textSize = pieceSz
                            blackPieceStrokePaint.alpha = pAlphaInt
                            blackPieceFillPaint.textSize = pieceSz
                            blackPieceFillPaint.alpha = pAlphaInt
                            val fm = blackPieceFillPaint.fontMetrics
                            val textY = sqT + sqH / 2f - (fm.ascent + fm.descent) / 2f
                            canvas.drawText(glyph, textX, textY, blackPieceStrokePaint)
                            canvas.drawText(glyph, textX, textY, blackPieceFillPaint)
                        }
                    }
                }
            }
        }

        // Draw active sliding piece animation smoothly over board
        activePieceAnim?.let { anim ->
            val p = anim.pieceChar
            val bitmap = pieceBitmapCache[p]
            if (bitmap != null) {
                pieceBitmapPaint.alpha = pAlphaInt
                val left = anim.currentX + (sqW - bitmap.width) / 2f
                val top = anim.currentY + (sqH - bitmap.height) / 2f
                canvas.drawBitmap(bitmap, left, top, pieceBitmapPaint)
            } else {
                val isW = p.isUpperCase()
                val glyph = piece2unicode(p)
                val pieceSz = sqW * 0.82f
                val textX = anim.currentX + sqW / 2f
                val useFill = if (isW) whitePieceFillPaint else blackPieceFillPaint
                val useStroke = if (isW) whitePieceStrokePaint else blackPieceStrokePaint
                useFill.textSize = pieceSz
                useFill.alpha = pAlphaInt
                useStroke.textSize = pieceSz
                useStroke.alpha = pAlphaInt
                val fm = useFill.fontMetrics
                val textY = anim.currentY + sqH / 2f - (fm.ascent + fm.descent) / 2f
                canvas.drawText(glyph, textX, textY, useStroke)
                canvas.drawText(glyph, textX, textY, useFill)
            }
        }

        // Draw trajectory arrow
        if (!isCorrectionMode) {
            drawArrow(canvas, boardRect, sqW)
        }

        // 3. Status bar & Move History
        val statusTop = if (headerTop > boardRect.bottom) headerTop + headerH + (4f * density) else boardRect.bottom + (4f * density)
        val statusH = (boardRect.width() * 0.12f).coerceIn(36f, 56f)
        statusBounds.set(boardRect.left, statusTop, boardRect.right, statusTop + statusH)

        if (!isGhostControlsEnabled || isCorrectionMode) {
            canvas.drawRoundRect(statusBounds, 8f * density, 8f * density, statusBgPaint)
            canvas.drawRoundRect(statusBounds, 8f * density, 8f * density, statusBorderPaint)

            val line1Y = statusTop + statusH * 0.38f
            val line2Y = statusTop + statusH * 0.80f

            val txtSz = (statusH * 0.32f).coerceIn(10f, 15f)
            val usePaint = if (isEngineCalculating) calcTxtPaint.also { it.textSize = txtSz } else statusTxtPaint.also { it.textSize = txtSz }
            histTxtPaint.textSize = (statusH * 0.28f).coerceIn(9f, 13f)

            val statusMsg = if (isCorrectionMode) "🛠️ Mode Koreksi Aktif: Sentuh bidak atau palet untuk mengedit" else buildStatus()
            val historyMsg = if (isCorrectionMode) "Giliran Aktif: ${if (currentTurn == PlayerColor.WHITE) "⚪ Putih" else "⚫ Hitam"}" else buildHistorySummary()

            canvas.drawText(statusMsg, statusBounds.left + 8f, line1Y, usePaint)
            canvas.drawText(historyMsg, statusBounds.left + 8f, line2Y, histTxtPaint)
        }

        // 4. Draw Editor Palette Bar if in Correction Mode
        if (isCorrectionMode) {
            drawEditorPalette(canvas, statusTop + statusH + (4f * density))
        }

        // 5. Draw Modal Action Sheet if Menu is Open
        if (isMenuOpen) {
            drawMenuModal(canvas)
        }
    }

    private fun drawEditorPalette(canvas: Canvas, editorTop: Float) {
        setupCorrectionLayoutMetrics(editorTop)

        val density = resources.displayMetrics.density
        val rowH = 38f * density

        canvas.drawRoundRect(correctionPanelBounds, 8f * density, 8f * density, editorBgPaint)
        canvas.drawRoundRect(correctionPanelBounds, 8f * density, 8f * density, editorBorderPaint)

        // Row 1: White Pieces
        val whitePieces = listOf('P', 'N', 'B', 'R', 'Q', 'K')
        for (p in whitePieces) {
            val rect = palettePieceBounds[p] ?: continue
            val isSel = (selectedPalettePiece == p || selectedEditorPiece == p)
            canvas.drawRoundRect(rect, 6f * density, 6f * density, if (isSel) editorSelectedBgPaint else editorItemBgPaint)
            if (isSel) canvas.drawRoundRect(rect, 6f * density, 6f * density, editorSelectedStrokePaint)

            val bitmap = pieceBitmapCache[p]
            if (bitmap != null) {
                val left = rect.left + (rect.width() - bitmap.width) / 2f
                val top = rect.top + (rowH - bitmap.height) / 2f
                canvas.drawBitmap(bitmap, left, top, palettePiecePaint)
            } else {
                val glyph = piece2unicode(p)
                val pieceSz = rowH * 0.72f
                val fm = palettePieceWhiteFill.fontMetrics
                val txtX = rect.centerX()
                val txtY = rect.centerY() - (fm.ascent + fm.descent) / 2f
                palettePieceWhiteStroke.textSize = pieceSz
                palettePieceWhiteFill.textSize = pieceSz
                canvas.drawText(glyph, txtX, txtY, palettePieceWhiteStroke)
                canvas.drawText(glyph, txtX, txtY, palettePieceWhiteFill)
            }
        }

        // Row 2: Black Pieces
        val blackPieces = listOf('p', 'n', 'b', 'r', 'q', 'k')
        for (p in blackPieces) {
            val rect = palettePieceBounds[p] ?: continue
            val isSel = (selectedPalettePiece == p || selectedEditorPiece == p)
            canvas.drawRoundRect(rect, 6f * density, 6f * density, if (isSel) editorSelectedBgPaint else editorItemBgPaint)
            if (isSel) canvas.drawRoundRect(rect, 6f * density, 6f * density, editorSelectedStrokePaint)

            val bitmap = pieceBitmapCache[p]
            if (bitmap != null) {
                val left = rect.left + (rect.width() - bitmap.width) / 2f
                val top = rect.top + (rowH - bitmap.height) / 2f
                canvas.drawBitmap(bitmap, left, top, palettePiecePaint)
            } else {
                val glyph = piece2unicode(p)
                val pieceSz = rowH * 0.72f
                val fm = palettePieceBlackFill.fontMetrics
                val txtX = rect.centerX()
                val txtY = rect.centerY() - (fm.ascent + fm.descent) / 2f
                palettePieceBlackStroke.textSize = pieceSz
                palettePieceBlackFill.textSize = pieceSz
                canvas.drawText(glyph, txtX, txtY, palettePieceBlackStroke)
                canvas.drawText(glyph, txtX, txtY, palettePieceBlackFill)
            }
        }

        // Row 3: Action Buttons [ 🗑️ Hapus ] [ ⚪/⚫ Giliran ] [ 🔄 Kosongkan ] [ ✅ Selesai ]
        // 1. Delete Tool (char 'X')
        val isDelSel = (selectedPalettePiece == 'X' || selectedEditorPiece == 'X')
        canvas.drawRoundRect(deleteToolButtonBounds, 6f * density, 6f * density, if (isDelSel) editorSelectedBgPaint else editorItemBgPaint)
        editorActionTxtPaint.color = if (isDelSel) Color.BLACK else Color.rgb(248, 113, 113)
        editorActionTxtPaint.textSize = (rowH * 0.38f).coerceIn(10f, 13f)
        canvas.drawText("🗑️ Hapus", deleteToolButtonBounds.centerX(), deleteToolButtonBounds.centerY() + 5f, editorActionTxtPaint)

        // 2. Turn Toggle Button
        canvas.drawRoundRect(turnToggleButtonBounds, 6f * density, 6f * density, editorActionBtnBgPaint)
        editorActionTxtPaint.color = Color.WHITE
        val turnStr = if (currentTurn == PlayerColor.WHITE) "⚪ Putih" else "⚫ Hitam"
        canvas.drawText(turnStr, turnToggleButtonBounds.centerX(), turnToggleButtonBounds.centerY() + 5f, editorActionTxtPaint)

        // 3. Clear Board Button
        canvas.drawRoundRect(clearBoardButtonBounds, 6f * density, 6f * density, editorActionBtnBgPaint)
        editorActionTxtPaint.color = Color.rgb(251, 191, 36)
        canvas.drawText("🔄 Reset", clearBoardButtonBounds.centerX(), clearBoardButtonBounds.centerY() + 5f, editorActionTxtPaint)

        // 4. Finish / Done Button
        canvas.drawRoundRect(finishButtonBounds, 6f * density, 6f * density, editorActionDoneBgPaint)
        editorActionTxtPaint.color = Color.BLACK
        canvas.drawText("✅ Selesai", finishButtonBounds.centerX(), finishButtonBounds.centerY() + 5f, editorActionTxtPaint)
    }

    private fun drawMenuModal(canvas: Canvas) {
        // Fullscreen Dim Scrim
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        val density = resources.displayMetrics.density
        val modalW = boardRect.width()
        val modalH = boardRect.height()
        val modalL = boardRect.left
        val modalT = boardRect.top

        modalCardRect.set(modalL, modalT, modalL + modalW, modalT + modalH)
        canvas.drawRoundRect(modalCardRect, 12f * density, 12f * density, modalBgPaint)
        canvas.drawRoundRect(modalCardRect, 12f * density, 12f * density, modalBorderPaint)

        // Header Bar (Tinggi ~28dp):
        val titlePad = 10f * density
        modalTitlePaint.textSize = 11.5f * density
        modalTitlePaint.color = Color.rgb(226, 232, 240)
        val modalTitle = when (currentMenuPage) {
            MenuPage.MAIN -> "⚙ KONTROL & PENGATURAN"
            MenuPage.APPEARANCE -> "⚙️ PENGATURAN & ANTI-CHEAT"
            MenuPage.PRESETS -> "📁 PRESET KALIBRASI TERSIMPAN"
        }
        val titleY = modalT + (19f * density)
        canvas.drawText(modalTitle, modalL + titlePad, titleY, modalTitlePaint)

        // Close Button (22x22dp, font 11sp)
        val closeBtnSz = 22f * density
        modalCloseBtnRect.set(modalL + modalW - closeBtnSz - (8f * density), modalT + (5f * density), modalL + modalW - (8f * density), modalT + closeBtnSz + (5f * density))
        modalCloseTxtPaint.textSize = 11f * density
        val fmClose = modalCloseTxtPaint.fontMetrics
        val closeY = modalCloseBtnRect.centerY() - (fmClose.ascent + fmClose.descent) / 2f
        canvas.drawText("✕", modalCloseBtnRect.centerX(), closeY, modalCloseTxtPaint)

        when (currentMenuPage) {
            MenuPage.MAIN -> drawMainMenu(canvas, modalL, modalT, modalW, modalH)
            MenuPage.APPEARANCE -> drawAppearanceSliders(canvas, modalL, modalT, modalW, modalH)
            MenuPage.PRESETS -> drawPresetsMenu(canvas, modalL, modalT, modalW, modalH)
        }
    }

    private fun drawMainMenu(canvas: Canvas, modalL: Float, modalT: Float, modalW: Float, modalH: Float) {
        val arrowLabel = when (arrowDurationMs) {
            1000L -> "1.0s"
            2000L -> "2.0s"
            3000L -> "3.0s"
            -1L -> "Selamanya"
            else -> "${arrowDurationMs / 1000.0}s"
        }
        val menuItems = listOf(
            Pair("⚪ Lawan: Putih (Komputer jalan)", opponentColor == PlayerColor.WHITE),
            Pair("⚫ Lawan: Hitam (Pemain jalan)", opponentColor == PlayerColor.BLACK),
            Pair("🤖 Deteksi Otomatis Lawan: ${if (isAutoDetectionEnabled) "ON" else "OFF"}", isAutoDetectionEnabled),
            Pair("↺ Undo Langkah Terakhir", false),
            Pair("🛠️ Koreksi Posisi Papan (Editor)", isCorrectionMode),
            Pair("💾 Simpan Posisi ke Preset", false),
            Pair("🎯 Lakukan Kalibrasi Baru", false),
            Pair("📁 Preset Tersimpan (${presetsList.size}) ➔", false),
            Pair("🔄 Putar Papan (Flip)", isBoardFlipped),
            Pair("↺ Reset Game", false),
            Pair("⚡ Waktu Berpikir: ${thinkingTimes[currentThinkingTimeIndex] / 1000.0}s", false),
            Pair("🏹 Durasi Panah: $arrowLabel", false),
            Pair("⚙️ Pengaturan & Anti-Cheat ➔", false),
            Pair("👁 Sembunyikan Papan (Floating Eye)", false),
            Pair("🛑 Matikan Service", false)
        )

        modalItemRects.clear()
        val density = resources.displayMetrics.density
        val startY = modalT + (32f * density)
        val totalListH = modalH - (40f * density)
        val itemH = (totalListH / menuItems.size) - (2.5f * density)
        modalItemTxtPaint.textSize = (10.5f * density).coerceIn(9.5f, 12f)

        for (i in menuItems.indices) {
            val (label, isActive) = menuItems[i]
            val itemT = startY + i * (itemH + (2.5f * density))
            val itemRect = RectF(modalL + (8f * density), itemT, modalL + modalW - (8f * density), itemT + itemH)
            modalItemRects.add(itemRect)

            val paint = if (isActive) modalItemActiveBgPaint else modalItemBgPaint
            canvas.drawRoundRect(itemRect, 6f * density, 6f * density, paint)

            modalItemTxtPaint.color = if (isActive) Color.WHITE else Color.rgb(226, 232, 240)
            val fm = modalItemTxtPaint.fontMetrics
            val txtY = itemRect.centerY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText(label, itemRect.left + (10f * density), txtY, modalItemTxtPaint)
        }
    }

    private fun drawPresetsMenu(canvas: Canvas, modalL: Float, modalT: Float, modalW: Float, modalH: Float) {
        presetItemRects.clear()
        presetBottomBtnRects.clear()
        val density = resources.displayMetrics.density

        val startY = modalT + (32f * density)
        val bottomAreaH = 36f * density
        val totalListH = modalH - (40f * density) - bottomAreaH

        if (presetsList.isEmpty()) {
            val emptyTxtY = modalT + modalH * 0.45f
            histTxtPaint.textSize = 11f * density
            canvas.drawText("Belum ada preset kalibrasi tersimpan.", modalL + (16f * density), emptyTxtY, histTxtPaint)
        } else {
            val maxVisible = min(presetsList.size, 6)
            val itemH = (totalListH / maxVisible) - (3.5f * density)
            val itemTxtSz = (itemH * 0.38f).coerceIn(9.5f * density, 12f * density)
            modalItemTxtPaint.textSize = itemTxtSz

            for (i in 0 until maxVisible) {
                val preset = presetsList[i]
                val isActive = (preset.id == activePresetId)
                val itemT = startY + i * (itemH + (3.5f * density))
                val itemRect = RectF(modalL + (8f * density), itemT, modalL + modalW - (8f * density), itemT + itemH)
                presetItemRects.add(itemRect)

                val paint = if (isActive) modalItemActiveBgPaint else modalItemBgPaint
                canvas.drawRoundRect(itemRect, 6f * density, 6f * density, paint)

                modalItemTxtPaint.color = if (isActive) Color.WHITE else Color.rgb(226, 232, 240)
                val fm = modalItemTxtPaint.fontMetrics
                val txtY = itemRect.centerY() - (fm.ascent + fm.descent) / 2f

                val prefix = if (isActive) "✔ " else "♟ "
                val pkgInfo = if (!preset.packageName.isNullOrBlank()) " (${preset.packageName})" else ""
                val dimInfo = " • ${preset.width.toInt()}px"
                val label = "$prefix${preset.name}$dimInfo$pkgInfo"
                canvas.drawText(label, itemRect.left + (10f * density), txtY, modalItemTxtPaint)
            }
        }

        // Bottom Row: [ ➕ Kalibrasi Baru ] & [ ↩ Kembali ]
        val bottomY = modalT + modalH - bottomAreaH - (4f * density)
        val halfW = (modalW - (24f * density)) / 2f
        val newCalibBtnRect = RectF(modalL + (8f * density), bottomY, modalL + (8f * density) + halfW, bottomY + (30f * density))
        val returnBtnRect = RectF(modalL + (16f * density) + halfW, bottomY, modalL + modalW - (8f * density), bottomY + (30f * density))
        presetBottomBtnRects.add(newCalibBtnRect)
        presetBottomBtnRects.add(returnBtnRect)

        canvas.drawRoundRect(newCalibBtnRect, 6f * density, 6f * density, modalItemActiveBgPaint)
        headerTitlePaint.color = Color.WHITE
        headerTitlePaint.textSize = 10.5f * density
        canvas.drawText("➕ Kalibrasi Baru", newCalibBtnRect.centerX() - (32f * density), newCalibBtnRect.centerY() + 4f, headerTitlePaint)

        canvas.drawRoundRect(returnBtnRect, 6f * density, 6f * density, modalItemBgPaint)
        canvas.drawRoundRect(returnBtnRect, 6f * density, 6f * density, modalBorderPaint)
        headerTitlePaint.color = Color.WHITE
        canvas.drawText("↩ Kembali", returnBtnRect.centerX() - (20f * density), returnBtnRect.centerY() + 4f, headerTitlePaint)
    }

    private fun drawAppearanceSliders(canvas: Canvas, modalL: Float, modalT: Float, modalW: Float, modalH: Float) {
        val density = resources.displayMetrics.density
        activeSliders.clear()
        appearanceBottomBtnRects.clear()

        // 1. Sticky Tab Switcher Header (Pinned di bawah Header Judul, Height 28dp)
        val startY = modalT + (28f * density)
        val tabH = 28f * density
        val tab1W = (modalW - (20f * density)) / 2f
        settingsTab1Rect.set(modalL + (8f * density), startY, modalL + (8f * density) + tab1W, startY + tabH)
        settingsTab2Rect.set(modalL + (12f * density) + tab1W, startY, modalL + modalW - (8f * density), startY + tabH)

        val isTab1 = (currentSettingsTab == SettingsTab.DISPLAY_ENGINE)
        canvas.drawRoundRect(settingsTab1Rect, 6f * density, 6f * density, if (isTab1) modalItemActiveBgPaint else modalItemBgPaint)
        if (!isTab1) canvas.drawRoundRect(settingsTab1Rect, 6f * density, 6f * density, modalBorderPaint)

        val isTab2 = (currentSettingsTab == SettingsTab.ANTI_CHEAT)
        canvas.drawRoundRect(settingsTab2Rect, 6f * density, 6f * density, if (isTab2) modalItemActiveBgPaint else modalItemBgPaint)
        if (!isTab2) canvas.drawRoundRect(settingsTab2Rect, 6f * density, 6f * density, modalBorderPaint)

        modalItemTxtPaint.textSize = 10f * density
        val fmTab = modalItemTxtPaint.fontMetrics

        modalItemTxtPaint.color = if (isTab1) Color.WHITE else Color.rgb(148, 163, 184)
        val tab1Y = settingsTab1Rect.centerY() - (fmTab.ascent + fmTab.descent) / 2f
        canvas.drawText("🎮 Tampilan & Engine", settingsTab1Rect.centerX() - (52f * density), tab1Y, modalItemTxtPaint)

        modalItemTxtPaint.color = if (isTab2) Color.WHITE else Color.rgb(148, 163, 184)
        val tab2Y = settingsTab2Rect.centerY() - (fmTab.ascent + fmTab.descent) / 2f
        canvas.drawText("🛡️ Anti-Cheat & Humanize", settingsTab2Rect.centerX() - (58f * density), tab2Y, modalItemTxtPaint)

        // 2. Bottom Action Button: [ ⬅️ Kembali ke Menu Papan ] (Pinned di bawah, Height: 30dp)
        val bottomH = 30f * density
        val bottomY = modalT + modalH - bottomH - (6f * density)
        val returnBtnRect = RectF(modalL + (8f * density), bottomY, modalL + modalW - (8f * density), bottomY + bottomH)
        appearanceBottomBtnRects.add(returnBtnRect)
        canvas.drawRoundRect(returnBtnRect, 6f * density, 6f * density, navBottomBtnBgPaint)
        canvas.drawRoundRect(returnBtnRect, 6f * density, 6f * density, navBottomBtnBorderPaint)
        headerTitlePaint.color = Color.WHITE
        headerTitlePaint.textSize = 10.5f * density
        val fmB = headerTitlePaint.fontMetrics
        val retY = returnBtnRect.centerY() - (fmB.ascent + fmB.descent) / 2f
        canvas.drawText("⬅️ Kembali ke Menu Papan", returnBtnRect.centerX() - (58f * density), retY, headerTitlePaint)

        // 3. Scrollable Viewport (Lebar Penuh, Padding 8dp, Jarak Antar Row 4dp)
        val viewportTop = startY + tabH + (6f * density)
        val viewportBottom = bottomY - (6f * density)
        val viewportH = viewportBottom - viewportTop

        val fullW = modalW - (16f * density)
        val fullL = modalL + (8f * density)
        val sliderH = 32f * density
        val toggleH = 26f * density
        val gap = 4f * density

        canvas.save()
        canvas.clipRect(modalL, viewportTop, modalL + modalW, viewportBottom)

        if (currentSettingsTab == SettingsTab.DISPLAY_ENGINE) {
            val totalContentH = (10 * (sliderH + gap)) + (3 * (toggleH + gap)) + (16f * density)
            maxSettingsScrollY = max(0f, totalContentH - viewportH)
            settingsScrollY = settingsScrollY.coerceIn(0f, maxSettingsScrollY)

            var curY = viewportTop - settingsScrollY

            // 1. Lock Board Toggle (Height: 26dp)
            lockBoardToggleRect.set(fullL, curY, fullL + fullW, curY + toggleH)
            canvas.drawRoundRect(lockBoardToggleRect, 6f * density, 6f * density, if (isBoardLocked) modalItemActiveBgPaint else modalItemBgPaint)
            if (!isBoardLocked) canvas.drawRoundRect(lockBoardToggleRect, 6f * density, 6f * density, modalBorderPaint)
            modalItemTxtPaint.color = Color.WHITE
            modalItemTxtPaint.textSize = 9.5f * density
            val fmL = modalItemTxtPaint.fontMetrics
            val lockTxtY = lockBoardToggleRect.centerY() - (fmL.ascent + fmL.descent) / 2f
            canvas.drawText("🔒 Kunci Posisi Papan (Cegah Geser): ${if (isBoardLocked) "ON" else "OFF"}", lockBoardToggleRect.centerX() - (88f * density), lockTxtY, modalItemTxtPaint)
            curY += toggleH + gap

            // 2. Ghost Controls Toggle (Height: 26dp)
            ghostControlsToggleRect.set(fullL, curY, fullL + fullW, curY + toggleH)
            canvas.drawRoundRect(ghostControlsToggleRect, 6f * density, 6f * density, if (isGhostControlsEnabled) modalItemActiveBgPaint else modalItemBgPaint)
            if (!isGhostControlsEnabled) canvas.drawRoundRect(ghostControlsToggleRect, 6f * density, 6f * density, modalBorderPaint)
            val ghostTxtY = ghostControlsToggleRect.centerY() - (fmL.ascent + fmL.descent) / 2f
            canvas.drawText("👻 Sembunyikan Header & Footer (100%): ${if (isGhostControlsEnabled) "ON" else "OFF"}", ghostControlsToggleRect.centerX() - (96f * density), ghostTxtY, modalItemTxtPaint)
            curY += toggleH + gap

            // 3. Style Highlight Segmented Button (26dp)
            highlightStyleBtnRect.set(fullL, curY, fullL + fullW, curY + toggleH)
            canvas.drawRoundRect(highlightStyleBtnRect, 6f * density, 6f * density, modalItemBgPaint)
            canvas.drawRoundRect(highlightStyleBtnRect, 6f * density, 6f * density, modalBorderPaint)
            modalItemTxtPaint.color = if (isHighlightFilled) Color.rgb(34, 197, 94) else Color.rgb(56, 189, 248)
            val fmH = modalItemTxtPaint.fontMetrics
            val styleTxtY = highlightStyleBtnRect.centerY() - (fmH.ascent + fmH.descent) / 2f
            canvas.drawText("🎨 Gaya Highlight: ${if (isHighlightFilled) "🟩 Filled (Penuh)" else "🔲 Outlined (Garis)"}", highlightStyleBtnRect.centerX() - (75f * density), styleTxtY, modalItemTxtPaint)
            curY += toggleH + gap

            // 4. Target ELO (800 - 3500)
            val eloProgress = ((eloRating - 800f) / (3500f - 800f)).coerceIn(0f, 1f)
            val eloRect = RectF(fullL, curY, fullL + fullW, curY + sliderH)
            drawSingleSlider(canvas, SliderSpec(SliderType.ELO_RATING, "⚡ Target ELO", "$eloRating ELO", eloProgress), eloRect)
            curY += sliderH + gap

            // 5. MAX ELO Power (800 - 3500)
            val maxEloProgress = ((maxEloRating - 800f) / (3500f - 800f)).coerceIn(0f, 1f)
            val maxEloRect = RectF(fullL, curY, fullL + fullW, curY + sliderH)
            drawSingleSlider(canvas, SliderSpec(SliderType.MAX_ELO_RATING, "🔥 MAX ELO Power", "$maxEloRating ELO", maxEloProgress), maxEloRect)
            curY += sliderH + gap

            // 6. ⚪ Transparansi Move Guide Dots: 0% - 100%
            val mgRect = RectF(fullL, curY, fullL + fullW, curY + sliderH)
            drawSingleSlider(canvas, SliderSpec(SliderType.MOVE_GUIDE_ALPHA, "⚪ Transparansi Move Guide Dots", "${(moveGuideAlpha * 100).roundToInt()}%", moveGuideAlpha), mgRect)
            curY += sliderH + gap

            // 7. ♟️ Transparansi Bidak: 0% - 100%
            val pieceRect = RectF(fullL, curY, fullL + fullW, curY + sliderH)
            drawSingleSlider(canvas, SliderSpec(SliderType.PIECE_ALPHA, "♟️ Transparansi Bidak", "${(pieceAlpha * 100).roundToInt()}%", pieceAlpha), pieceRect)
            curY += sliderH + gap

            // 8. 🏁 Transparansi Grid Papan: 0% - 100%
            val gridRect = RectF(fullL, curY, fullL + fullW, curY + sliderH)
            drawSingleSlider(canvas, SliderSpec(SliderType.GRID_ALPHA, "🏁 Transparansi Grid Papan", "${(gridAlpha * 100).roundToInt()}%", gridAlpha), gridRect)
            curY += sliderH + gap

            // 9. 🏹 Transparansi Panah BestMove: 20% - 100%
            val arrowProgress = ((arrowAlpha - 0.20f) / 0.80f).coerceIn(0f, 1f)
            val arrowRect = RectF(fullL, curY, fullL + fullW, curY + sliderH)
            drawSingleSlider(canvas, SliderSpec(SliderType.ARROW_ALPHA, "🏹 Transparansi Panah BestMove", "${(arrowAlpha * 100).roundToInt()}%", arrowProgress), arrowRect)
            curY += sliderH + gap

            // 10. 🟩 Transparansi Highlight: 10% - 100%
            val highProgress = ((highlightAlpha - 0.10f) / 0.90f).coerceIn(0f, 1f)
            val highRect = RectF(fullL, curY, fullL + fullW, curY + sliderH)
            drawSingleSlider(canvas, SliderSpec(SliderType.HIGHLIGHT_ALPHA, "🟩 Transparansi Highlight", "${(highlightAlpha * 100).roundToInt()}%", highProgress), highRect)
            curY += sliderH + gap

            // 11. 👁️ Transparansi Floating Eye: 20% - 100%
            val eyeProgress = ((floatingEyeAlpha - 0.20f) / 0.80f).coerceIn(0f, 1f)
            val eyeRect = RectF(fullL, curY, fullL + fullW, curY + sliderH)
            drawSingleSlider(canvas, SliderSpec(SliderType.FLOATING_EYE_ALPHA, "👁️ Transparansi Floating Eye", "${(floatingEyeAlpha * 100).roundToInt()}%", eyeProgress), eyeRect)
            curY += sliderH + gap

            // 12. ⏱️ Auto-Hide Timer: 0s - 10s
            val isAutoHideActive = autoHideDelaySec >= 0
            val autoHideStr = if (!isAutoHideActive) "Off" else if (autoHideDelaySec == 0) "0s" else "${autoHideDelaySec}s"
            val autoHideProgress = ((autoHideDelaySec + 1) / 11f).coerceIn(0f, 1f)
            val hideRect = RectF(fullL, curY, fullL + fullW, curY + sliderH)
            drawSingleSlider(canvas, SliderSpec(SliderType.AUTO_HIDE_DELAY, "⏱️ Auto-Hide Timer", autoHideStr, autoHideProgress), hideRect)
            curY += sliderH + gap

            // 11. ⏱️ Auto-Show Interval: 0s - 10s
            val autoShowStr = if (!isAutoHideActive) "Off" else if (!isAutoShowEnabled || autoShowDelaySec <= 0) "Off" else "${autoShowDelaySec}s"
            val autoShowProgress = if (!isAutoHideActive || !isAutoShowEnabled) 0f else (autoShowDelaySec / 10f).coerceIn(0f, 1f)
            val showRect = RectF(fullL, curY, fullL + fullW, curY + sliderH)
            drawSingleSlider(canvas, SliderSpec(SliderType.AUTO_SHOW_DELAY, "⏱️ Auto-Show Interval", autoShowStr, autoShowProgress), showRect)
        } else {
            val totalContentH = (3 * (toggleH + gap)) + (sliderH + gap) + (24f * density)
            maxSettingsScrollY = max(0f, totalContentH - viewportH)
            settingsScrollY = settingsScrollY.coerceIn(0f, maxSettingsScrollY)

            var curY = viewportTop - settingsScrollY

            // 1. Humanize Move Switch Card (Height: 26dp)
            humanizeToggleRect.set(fullL, curY, fullL + fullW, curY + toggleH)
            canvas.drawRoundRect(humanizeToggleRect, 6f * density, 6f * density, if (com.chessbeater.engine.HumanizationEngine.isHumanizeEnabled) modalItemActiveBgPaint else modalItemBgPaint)
            modalItemTxtPaint.color = Color.WHITE
            modalItemTxtPaint.textSize = 9.5f * density
            val fmH2 = modalItemTxtPaint.fontMetrics
            val hY = humanizeToggleRect.centerY() - (fmH2.ascent + fmH2.descent) / 2f
            canvas.drawText("🎭 Humanize Move (Anti-Cheat): ${if (com.chessbeater.engine.HumanizationEngine.isHumanizeEnabled) "ON" else "OFF"}", humanizeToggleRect.centerX() - (85f * density), hY, modalItemTxtPaint)
            curY += toggleH + gap

            // 2. Tingkat Humanis Slider (Height: 32dp)
            val humanizeProgress = (com.chessbeater.engine.HumanizationEngine.humanizeLevel / 10f).coerceIn(0f, 1f)
            val humLvlRect = RectF(fullL, curY, fullL + fullW, curY + sliderH)
            drawSingleSlider(canvas, SliderSpec(SliderType.HUMANIZE_LEVEL, "🎯 Tingkat Humanis", "Level ${com.chessbeater.engine.HumanizationEngine.humanizeLevel}", humanizeProgress), humLvlRect)
            curY += sliderH + (2f * density)

            // 3. Dynamic Description Text (8.5sp, #94A3B8)
            val descTxt = com.chessbeater.engine.HumanizationEngine.getLevelDescription(com.chessbeater.engine.HumanizationEngine.humanizeLevel)
            histTxtPaint.textSize = 8.5f * density
            histTxtPaint.color = Color.rgb(148, 163, 184)
            canvas.drawText(descTxt, fullL + (4f * density), curY + (10f * density), histTxtPaint)
            curY += (14f * density) + gap

            // 4. Blunder Guard Switch Card (Height: 26dp)
            blunderGuardToggleRect.set(fullL, curY, fullL + fullW, curY + toggleH)
            canvas.drawRoundRect(blunderGuardToggleRect, 6f * density, 6f * density, if (com.chessbeater.engine.HumanizationEngine.isBlunderGuardEnabled) modalItemActiveBgPaint else modalItemBgPaint)
            modalItemTxtPaint.color = Color.WHITE
            modalItemTxtPaint.textSize = 9.5f * density
            val bY = blunderGuardToggleRect.centerY() - (fmH2.ascent + fmH2.descent) / 2f
            canvas.drawText("🛡️ Blunder Guard (Safety Net): ${if (com.chessbeater.engine.HumanizationEngine.isBlunderGuardEnabled) "ON" else "OFF"}", blunderGuardToggleRect.centerX() - (80f * density), bY, modalItemTxtPaint)
            curY += toggleH + gap

            // 5. Natural Move Delay Switch Card (Height: 26dp)
            naturalDelayToggleRect.set(fullL, curY, fullL + fullW, curY + toggleH)
            canvas.drawRoundRect(naturalDelayToggleRect, 6f * density, 6f * density, if (com.chessbeater.engine.HumanizationEngine.isNaturalDelayEnabled) modalItemActiveBgPaint else modalItemBgPaint)
            modalItemTxtPaint.color = Color.WHITE
            modalItemTxtPaint.textSize = 9.5f * density
            val nY = naturalDelayToggleRect.centerY() - (fmH2.ascent + fmH2.descent) / 2f
            canvas.drawText("⏳ Natural Move Delay (Adaptive Jitter): ${if (com.chessbeater.engine.HumanizationEngine.isNaturalDelayEnabled) "ON" else "OFF"}", naturalDelayToggleRect.centerX() - (95f * density), nY, modalItemTxtPaint)
        }

        canvas.restore()
    }

    private fun drawSingleSlider(canvas: Canvas, spec: SliderSpec, itemRect: RectF) {
        val density = resources.displayMetrics.density
        val labelTxtSz = 9.5f * density
        sliderLabelPaint.textSize = labelTxtSz
        sliderLabelPaint.color = Color.rgb(203, 213, 225) // #CBD5E1
        sliderValPaint.textSize = labelTxtSz
        sliderValPaint.color = Color.rgb(56, 189, 248) // #38BDF8
        val txtY = itemRect.top + (10f * density)
        canvas.drawText(spec.label, itemRect.left + (2f * density), txtY, sliderLabelPaint)
        canvas.drawText(spec.valueText, itemRect.right - (2f * density), txtY, sliderValPaint)

        val trackH = 3.5f * density
        val trackT = itemRect.top + (18f * density)
        val trackRect = RectF(itemRect.left + (2f * density), trackT, itemRect.right - (2f * density), trackT + trackH)

        canvas.drawRoundRect(trackRect, trackH / 2f, trackH / 2f, sliderTrackBgPaint)

        val activeWidth = trackRect.width() * spec.progress
        if (activeWidth > 0) {
            val filledRect = RectF(trackRect.left, trackRect.top, trackRect.left + activeWidth, trackRect.bottom)
            canvas.drawRoundRect(filledRect, trackH / 2f, trackH / 2f, sliderTrackFillPaint)
        }

        val thumbRadius = 7f * density
        val thumbX = (trackRect.left + activeWidth).coerceIn(trackRect.left, trackRect.right)
        val thumbY = trackRect.centerY()
        canvas.drawCircle(thumbX, thumbY, thumbRadius, sliderThumbPaint)
        canvas.drawCircle(thumbX, thumbY, thumbRadius, sliderThumbBorderPaint)

        val thumbHitSlop = 18f * density
        val thumbHitRect = RectF(thumbX - thumbHitSlop, trackRect.top - (10f * density), thumbX + thumbHitSlop, trackRect.bottom + (10f * density))

        activeSliders.add(spec.copy(bounds = itemRect, trackRect = trackRect, thumbHitRect = thumbHitRect))
    }

    private fun updateSliderValue(type: SliderType, touchX: Float, trackRect: RectF) {
        val progress = ((touchX - trackRect.left) / trackRect.width()).coerceIn(0.0f, 1.0f)
        when (type) {
            SliderType.ELO_RATING -> {
                val elo = (800 + progress * (3500 - 800)).roundToInt()
                eloRating = elo // Update UI real-time
                eloDebounceJob?.cancel()
                eloDebounceJob = viewScope.launch {
                    delay(250L)
                    onEloRatingChanged?.invoke(elo)
                    Log.d("SettingsDebounce", "⚡ ELO resmi disetel ke Engine: $elo")
                }
            }
            SliderType.MAX_ELO_RATING -> {
                val elo = (800 + progress * (3500 - 800)).roundToInt()
                maxEloRating = elo
                maxEloDebounceJob?.cancel()
                maxEloDebounceJob = viewScope.launch {
                    delay(250L)
                    onEloRatingChanged?.invoke(elo)
                    notifyVisualPrefsChanged()
                    Log.d("SettingsDebounce", "🔥 MAX ELO resmi disetel: $elo")
                }
            }
            SliderType.OVERLAY_TRANSPARENCY -> {
                val alphaVal = (0.30f + progress * 0.70f).coerceIn(0.30f, 1.0f)
                overlayAlpha = alphaVal
                this.alpha = alphaVal
                notifyVisualPrefsChanged()
                Log.d("SettingsTransparency", "👻 Transparansi diubah ke: ${(alphaVal * 100).roundToInt()}%")
            }
            SliderType.HUMANIZE_LEVEL -> {
                val lvl = (progress * 10).roundToInt().coerceIn(0, 10)
                com.chessbeater.engine.HumanizationEngine.humanizeLevel = lvl // Update UI real-time
                humanizeSaveDebounceJob?.cancel()
                humanizeSaveDebounceJob = viewScope.launch {
                    delay(250L)
                    com.chessbeater.engine.HumanizationEngine.saveSettings(context)
                    Log.d("SettingsDebounce", "🎯 Humanize Level resmi disimpan: $lvl")
                }
            }
            SliderType.PIECE_ALPHA -> {
                pieceAlpha = progress.coerceIn(0f, 1f)
                notifyVisualPrefsChanged()
            }
            SliderType.GRID_ALPHA -> {
                gridAlpha = progress.coerceIn(0f, 1f)
                notifyVisualPrefsChanged()
            }
            SliderType.ARROW_ALPHA -> {
                arrowAlpha = (0.20f + progress * 0.80f).coerceIn(0.20f, 1.0f)
                notifyVisualPrefsChanged()
            }
            SliderType.HIGHLIGHT_ALPHA -> {
                highlightAlpha = (0.10f + progress * 0.90f).coerceIn(0.10f, 1.0f)
                notifyVisualPrefsChanged()
            }
            SliderType.MOVE_GUIDE_ALPHA -> {
                moveGuideAlpha = progress.coerceIn(0f, 1f)
                notifyVisualPrefsChanged()
            }
            SliderType.FLOATING_EYE_ALPHA -> {
                floatingEyeAlpha = (0.20f + progress * 0.80f).coerceIn(0.20f, 1.0f)
                notifyVisualPrefsChanged()
            }
            SliderType.AUTO_HIDE_DELAY -> {
                val step = (progress * 11).roundToInt() - 1 // -1 .. 10
                autoHideDelaySec = step
                if (step < 0) isAutoShowEnabled = false
                notifyVisualPrefsChanged()
            }
            SliderType.AUTO_SHOW_DELAY -> {
                if (autoHideDelaySec >= 0) {
                    val step = (progress * 10).roundToInt() // 0 .. 10
                    if (step == 0) {
                        isAutoShowEnabled = false
                        autoShowDelaySec = 2
                    } else {
                        isAutoShowEnabled = true
                        autoShowDelaySec = step
                    }
                }
                notifyVisualPrefsChanged()
            }
        }
    }

    private fun buildStatus(): String = when {
        isEngineCalculating -> "⏳ Stockfish sedang berpikir..."
        currentTurn == opponentColor -> {
            val evalPart = if (evalText.isNotBlank()) " ($evalText)" else ""
            val lastMoveStr = engineBestMove?.takeIf { it.length >= 4 }?.let {
                " | Mesin: ${it.substring(0, 2)}➔${it.substring(2, 4)}$evalPart"
            } ?: ""
            "👉 Giliran Anda (${if (opponentColor == PlayerColor.WHITE) "Putih" else "Hitam"})$lastMoveStr"
        }
        currentTurn == stockfishColor -> "⏳ Giliran Stockfish..."
        else -> ""
    }

    private fun buildHistorySummary(): String {
        if (moveHistory.isEmpty()) return "Riwayat: Belum ada langkah"
        val lastMoves = moveHistory.takeLast(3).joinToString("  |  ")
        return "Langkah: $lastMoves"
    }

    private fun drawArrow(canvas: Canvas, bRect: RectF, sqSz: Float) {
        if (!isArrowVisible) return

        // Prioritize engine's recommended best move when available, or last executed move
        val activeUci: String? = when {
            engineBestMove?.length?.let { it >= 4 } == true -> engineBestMove
            lastMoveFrom != null && lastMoveTo != null -> "${idx2notation(lastMoveFrom!!)}${idx2notation(lastMoveTo!!)}"
            else -> null
        }

        if (activeUci == null || activeUci.length < 4) return

        val coords = uciToPixel(activeUci, bRect, isBoardFlipped) ?: return
        val fromPt = coords.first
        val toPt = coords.second

        val dx = toPt.x - fromPt.x
        val dy = toPt.y - fromPt.y
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (dist < 1f) return

        val headSz = sqSz * 0.38f
        val sw = sqSz * 0.11f
        val aAlphaInt = (arrowAlpha * 255).roundToInt().coerceIn(0, 255)
        arrowStrokePaint.strokeWidth = sw
        arrowStrokePaint.alpha = aAlphaInt
        arrowFillPaint.alpha = aAlphaInt

        val isKnight = isKnightMove(activeUci)

        if (isKnight) {
            // Draw smooth organic Knight arc path (Quad Bezier with perpendicular offset)
            val midX = (fromPt.x + toPt.x) / 2f
            val midY = (fromPt.y + toPt.y) / 2f
            val ctrlOffsetX = -dy * 0.28f
            val ctrlOffsetY = dx * 0.28f
            val ctrlX = midX + ctrlOffsetX
            val ctrlY = midY + ctrlOffsetY

            val endAngle = atan2((toPt.y - ctrlY).toDouble(), (toPt.x - ctrlX).toDouble()).toFloat()

            arrowPath.reset()
            arrowPath.moveTo(fromPt.x, fromPt.y)
            arrowPath.quadTo(ctrlX, ctrlY, toPt.x - cos(endAngle) * (headSz * 0.65f), toPt.y - sin(endAngle) * (headSz * 0.65f))
            canvas.drawPath(arrowPath, arrowStrokePaint)

            // Arrowhead at destination
            val ha = 0.52f
            val leftX = toPt.x - headSz * cos(endAngle - ha)
            val leftY = toPt.y - headSz * sin(endAngle - ha)
            val rightX = toPt.x - headSz * cos(endAngle + ha)
            val rightY = toPt.y - headSz * sin(endAngle + ha)

            arrowPath.reset()
            arrowPath.moveTo(toPt.x, toPt.y)
            arrowPath.lineTo(leftX, leftY)
            arrowPath.lineTo(toPt.x - headSz * 0.32f * cos(endAngle), toPt.y - headSz * 0.32f * sin(endAngle))
            arrowPath.lineTo(rightX, rightY)
            arrowPath.close()
            canvas.drawPath(arrowPath, arrowFillPaint)
        } else {
            // Straight move arrow
            val ang = atan2(dy.toDouble(), dx.toDouble()).toFloat()
            val shX = toPt.x - cos(ang) * headSz * 0.6f
            val shY = toPt.y - sin(ang) * headSz * 0.6f
            canvas.drawLine(fromPt.x, fromPt.y, shX, shY, arrowStrokePaint)

            val ha = 0.52f
            arrowPath.reset()
            arrowPath.moveTo(toPt.x, toPt.y)
            arrowPath.lineTo(toPt.x - headSz * cos(ang - ha), toPt.y - headSz * sin(ang - ha))
            arrowPath.lineTo(toPt.x - headSz * 0.30f * cos(ang), toPt.y - headSz * 0.30f * sin(ang))
            arrowPath.lineTo(toPt.x - headSz * cos(ang + ha), toPt.y - headSz * sin(ang + ha))
            arrowPath.close()
            canvas.drawPath(arrowPath, arrowFillPaint)
        }
    }

    // ====== TOUCH HANDLING ======

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        // 1. PRIORITAS UTAMA: JIKA DALAM MODE KOREKSI PAPAN
        if (isCorrectionModeActive) {
            return handleCorrectionTouch(event)
        }

        // 2. Dialog Pengaturan & Menu Kontrol (Jika Aktif)
        if (isMenuOpen) {
            autoHideJob?.cancel() // Hentikan timer auto-hide saat menu dibuka

            if (modalCardRect.contains(x, y)) {
                // Sentuhan di dalam kartu dialog pengaturan -> proses dan konsumsi penuh!
                return onTouchEvent(event)
            }

            // Jika menyentuh di luar kartu modal dialog pengaturan:
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                // Tutup menu pengaturan saja (kembali ke papan), JANGAN sembunyikan papan overlay!
                isMenuOpen = false
                currentMenuPage = MenuPage.MAIN
                postInvalidate()
            }
            return true // Cegah tembus ke aksi hide papan saat menu terbuka
        }

        // 3. Tombol Header (Mata & Menu Titik Tiga)
        if (btnEyeBounds.contains(x, y)) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                isMenuOpen = false
                currentMenuPage = MenuPage.MAIN
                postInvalidate()
                onToggleVisibilityRequested?.invoke(true, OverlayHideReason.MANUAL)
            }
            return true
        }

        if (btnMenuBounds.contains(x, y)) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                if (onOpenSettingsRequested != null) {
                    onOpenSettingsRequested?.invoke()
                } else {
                    isMenuOpen = !isMenuOpen
                    if (isMenuOpen) {
                        currentMenuPage = MenuPage.MAIN
                        autoHideJob?.cancel()
                    }
                    postInvalidate()
                }
            }
            return true
        }

        // 4. Area Papan Catur Normal
        if (boardRect.contains(x, y)) {
            return handleChessBoardTouch(event)
        }

        // Header / Status Bounds (jika tidak ghost mode)
        if (!isGhostControlsEnabled) {
            if (headerBounds.contains(x, y) || statusBounds.contains(x, y)) {
                return onTouchEvent(event)
            }
        }

        // 5. Di Luar Semua Elemen -> Tembus ke Game di Bawahnya!
        return false
    }

    private fun handleChessBoardTouch(event: MotionEvent): Boolean {
        val touchX = event.x
        val touchY = event.y
        // Guard: allow touch only when not in engine calculation and it's opponent turn (unless in correction mode)
        if (!isCorrectionMode && (isEngineCalculating || currentTurn != opponentColor)) {
            Log.d("InteractiveBoardOverlayView", "Touch ignored: engine calculating or not opponent turn")
            return false
        }
        val squareW = boardRect.width() / 8f
        val squareH = boardRect.height() / 8f

        val file = ((touchX - boardRect.left) / squareW).toInt().coerceIn(0, 7)
        val rank = ((touchY - boardRect.top) / squareH).toInt().coerceIn(0, 7)

        val col = if (isBoardFlipped) 7 - file else file
        val row = if (isBoardFlipped) 7 - rank else rank
        val clicked = row * 8 + col

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                rawDownX = event.rawX
                rawDownY = event.rawY
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                isDragging = false
                autoHideJob?.cancel()

                // Tangani tap petak catur
                handleBoardTap(clicked)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = kotlin.math.abs(event.rawX - rawDownX)
                val dy = kotlin.math.abs(event.rawY - rawDownY)
                if (!isBoardLocked && (dx > touchSlop || dy > touchSlop)) {
                    isDragging = true
                    val curX = event.rawX
                    val curY = event.rawY
                    val dxx = (curX - lastTouchX).toInt()
                    val dyy = (curY - lastTouchY).toInt()
                    if (dxx != 0 || dyy != 0) {
                        boardRect.offset(dxx.toFloat(), dyy.toFloat())
                        notifyVisualPrefsChanged()
                        lastTouchX = curX
                        lastTouchY = curY
                        postInvalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val distSq = (event.rawX - rawDownX) * (event.rawX - rawDownX) + (event.rawY - rawDownY) * (event.rawY - rawDownY)
                val isTap = !isDragging && distSq < (touchSlop * touchSlop)

                if (!isTap && isDragging) {
                    isDragging = false
                } else if (!isTap && isTouchForwarding) {
                    ChessAccessibilityService.forwardDrag(rawDownX, rawDownY, event.rawX, event.rawY, 150L)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return true
            }
        }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val touchX = event.x
        val touchY = event.y

        val isInsideBoard = boardRect.contains(touchX, touchY)
        val isInsideHeader = (!isGhostControlsEnabled && headerBounds.contains(touchX, touchY)) || btnMenuBounds.contains(touchX, touchY) || btnEyeBounds.contains(touchX, touchY)
        val isInsideStatus = (!isGhostControlsEnabled && statusBounds.contains(touchX, touchY))
        val isInsidePalette = isCorrectionMode && editorBounds.contains(touchX, touchY)
        val isInsideModal = isMenuOpen && modalCardRect.contains(touchX, touchY)

        // 1. Jika sentuhan di luar seluruh elemen overlay: tolak dan tembuskan ke game
        if (!isInsideBoard && !isInsideHeader && !isInsideStatus && !isInsidePalette && !isInsideModal) {
            return false
        }

        if (!isMenuOpen && !isCorrectionMode && !isBoardLocked) {
            scaleDetector.onTouchEvent(event)
            if (scaleDetector.isInProgress) return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                rawDownX = event.rawX
                rawDownY = event.rawY
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                isDragging = false
                autoHideJob?.cancel()

                if (isMenuOpen && currentMenuPage == MenuPage.APPEARANCE) {
                    for (slider in activeSliders) {
                        if (slider.thumbHitRect.contains(touchX, touchY) || slider.trackRect.contains(touchX, touchY)) {
                            activeDraggingSlider = slider.type
                            return true
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isMenuOpen) {
                    if (currentMenuPage == MenuPage.APPEARANCE) {
                        val dx = kotlin.math.abs(event.rawX - rawDownX)
                        val dy = kotlin.math.abs(event.rawY - rawDownY)

                        if (activeDraggingSlider != null) {
                            if (dy > touchSlop && dy > dx) {
                                // User sedang scroll vertikal -> lepaskan slider ke scrollview
                                activeDraggingSlider = null
                            } else if (dx > 4f) {
                                val activeSlider = activeSliders.find { it.type == activeDraggingSlider }
                                if (activeSlider != null) {
                                    updateSliderValue(activeSlider.type, touchX, activeSlider.trackRect)
                                }
                                return true
                            }
                        }

                        if (activeDraggingSlider == null && maxSettingsScrollY > 0f) {
                            val curY = event.rawY
                            val deltaY = lastTouchY - curY
                            if (kotlin.math.abs(deltaY) > 2f) {
                                settingsScrollY = (settingsScrollY + deltaY).coerceIn(0f, maxSettingsScrollY)
                                lastTouchX = event.rawX
                                lastTouchY = curY
                                postInvalidate()
                            }
                            return true
                        }
                    }
                    return true
                }

                // 2. JIKA POSISI PAPAN TERKUNCI (isBoardLocked == true):
                // Cegah pergeseran papan akibat gesture drag yang tidak disengaja
                if (isBoardLocked) {
                    return true // Konsumsi gerakan tanpa memindahkan koordinat papan
                }

                // Jangan izinkan geser papan jika sentuhan awal bukan di dalam boardRect
                if (!boardRect.contains(rawDownX, rawDownY)) {
                    return true
                }

                val idx = event.findPointerIndex(activePointerId)
                if (idx < 0) return true
                val curX = event.rawX
                val curY = event.rawY
                val totalDx = curX - rawDownX
                val totalDy = curY - rawDownY
                val distSq = totalDx * totalDx + totalDy * totalDy

                if (!isDragging && distSq > touchSlop * touchSlop) {
                    isDragging = true
                }

                if (isDragging) {
                    val dxx = (curX - lastTouchX).toInt()
                    val dyy = (curY - lastTouchY).toInt()
                    if (dxx != 0 || dyy != 0) {
                        boardRect.offset(dxx.toFloat(), dyy.toFloat())
                        notifyVisualPrefsChanged()
                        lastTouchX = curX
                        lastTouchY = curY
                        postInvalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (isMenuOpen && currentMenuPage == MenuPage.APPEARANCE) {
                    if (activeDraggingSlider != null) {
                        val activeSlider = activeSliders.find { it.type == activeDraggingSlider }
                        if (activeSlider != null) {
                            // Commit on Release: Update nilai slider saat jari diangkat
                            updateSliderValue(activeSlider.type, touchX, activeSlider.trackRect)
                        }
                        activeDraggingSlider = null
                        postInvalidate()
                        return true
                    }
                }

                val rawUpX = event.rawX
                val rawUpY = event.rawY
                val distSq = (rawUpX - rawDownX) * (rawUpX - rawDownX) + (rawUpY - rawDownY) * (rawUpY - rawDownY)
                val isTap = !isDragging && distSq < (touchSlop * touchSlop)

                if (isTap) {
                    // 1. Three Dots Menu Toggle Button
                    if (btnMenuBounds.contains(x, y)) {
                        isMenuOpen = !isMenuOpen
                        if (isMenuOpen) currentMenuPage = MenuPage.MAIN
                        postInvalidate()
                        return true
                    }

                    // 2. Quick Eye Button (Hide to Floating Eye)
                    if (btnEyeBounds.contains(x, y)) {
                        isMenuOpen = false
                        currentMenuPage = MenuPage.MAIN
                        postInvalidate()
                        onToggleVisibilityRequested?.invoke(true, OverlayHideReason.MANUAL)
                        return true
                    }

                    // 3. If modal menu is open, handle modal tap
                    if (isMenuOpen) {
                        handleModalTap(x, y)
                        return true
                    }

                    // 4. If in Correction Mode, handle editor interaction
                    if (isCorrectionModeActive) {
                        return handleCorrectionTouch(event)
                    }

                    // 5. Board tap interaction when modal is closed & normal mode
                    if (boardRect.contains(x, y)) {
                        val sqW = boardRect.width() / 8f
                        val sqH = boardRect.height() / 8f
                        val col = ((x - boardRect.left) / sqW).toInt().coerceIn(0, 7)
                        val row = ((y - boardRect.top) / sqH).toInt().coerceIn(0, 7)
                        val bRow = if (isBoardFlipped) 7 - row else row
                        val bCol = if (isBoardFlipped) 7 - col else col
                        val sq = bRow * 8 + bCol
                        handleBoardTap(sq)
                    }
                } else {
                    // Manual drag / swipe on board touch forwarding (only when menu is closed & not in editor)
                    if (!isMenuOpen && !isCorrectionMode && isTouchForwarding && boardRect.contains(rawDownX, rawDownY)) {
                        if (distSq < 20f * 20f) {
                            ChessAccessibilityService.forwardClick(rawDownX, rawDownY)
                        } else {
                            ChessAccessibilityService.forwardDrag(rawDownX, rawDownY, rawUpX, rawUpY, 150L)
                        }
                    }
                }
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleCorrectionTouch(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 1. Cek Klik Tombol Selesai
                if (finishButtonBounds.contains(x, y)) {
                    if (!board.contains('K') || !board.contains('k')) {
                        Toast.makeText(context, "⚠️ Kedua Raja (♔ & ♚) harus ada di papan!", Toast.LENGTH_SHORT).show()
                    } else {
                        exitCorrectionMode()
                    }
                    return true
                }

                // 2. Cek Klik Tombol Kosongkan Papan
                if (clearBoardButtonBounds.contains(x, y)) {
                    clearAllPiecesForCorrection()
                    postInvalidate()
                    return true
                }

                // Cek Klik Tombol Ganti Giliran
                if (turnToggleButtonBounds.contains(x, y)) {
                    currentTurn = if (currentTurn == PlayerColor.WHITE) PlayerColor.BLACK else PlayerColor.WHITE
                    postInvalidate()
                    return true
                }

                // 3. Cek Pemilihan Bidak dari Palet
                for ((piece, bounds) in palettePieceBounds) {
                    if (bounds.contains(x, y)) {
                        selectedPalettePiece = if (selectedPalettePiece == piece) null else piece
                        selectedSquare = null
                        postInvalidate()
                        return true
                    }
                }

                // 4. Cek Sentuhan di Petak Papan Catur (Taruh / Hapus Bidak)
                if (boardBounds.contains(x, y)) {
                    val square = getSquareNameFromCoordinates(x, y)
                    val sqIdx = notation2idx(square)
                    if (sqIdx in 0..63) {
                        if (selectedPalettePiece != null) {
                            if (selectedPalettePiece == 'X') {
                                removePieceAtSquare(square)
                            } else {
                                setPieceAtSquare(square, selectedPalettePiece!!)
                            }
                        } else {
                            if (selectedSquare == null) {
                                if (board[sqIdx] != '.') {
                                    selectedSquare = sqIdx
                                }
                            } else {
                                val from = selectedSquare!!
                                if (from == sqIdx) {
                                    removePieceAtSquare(square)
                                    selectedSquare = null
                                } else {
                                    board[sqIdx] = board[from]
                                    board[from] = '.'
                                    selectedSquare = null
                                }
                            }
                        }
                    }
                    postInvalidate()
                    return true
                }
                return true
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                return true
            }
        }
        return true
    }

    private fun handleModalTap(tapX: Float, tapY: Float) {
        if (!modalCardRect.contains(tapX, tapY) || modalCloseBtnRect.contains(tapX, tapY)) {
            isMenuOpen = false
            currentMenuPage = MenuPage.MAIN
            postInvalidate()
            return
        }

        when (currentMenuPage) {
            MenuPage.MAIN -> {
                for (i in modalItemRects.indices) {
                    if (modalItemRects[i].contains(tapX, tapY)) {
                        when (i) {
                            0 -> { setOpponentColor(PlayerColor.WHITE); isMenuOpen = false }
                            1 -> { setOpponentColor(PlayerColor.BLACK); isMenuOpen = false }
                            2 -> {
                                isAutoDetectionEnabled = !isAutoDetectionEnabled
                                onAutoDetectionToggled?.invoke(isAutoDetectionEnabled)
                                notifyVisualPrefsChanged()
                                Toast.makeText(context, if (isAutoDetectionEnabled) "🤖 Deteksi otomatis lawan: AKTIF" else "🤖 Deteksi otomatis lawan: NONAKTIF", Toast.LENGTH_SHORT).show()
                            }
                            3 -> { isMenuOpen = false; undoLastMove() }
                            4 -> {
                                isMenuOpen = false
                                isCorrectionMode = !isCorrectionMode
                                selectedEditorPiece = null
                                selectedSquare = null
                                requestLayout()
                                postInvalidate()
                            }
                            5 -> { isMenuOpen = false; onSaveCurrentPositionToPresetRequested?.invoke() }
                            6 -> { isMenuOpen = false; onStartCalibrationRequested?.invoke() }
                            7 -> { currentMenuPage = MenuPage.PRESETS }
                            8 -> { flipBoard(); isMenuOpen = false }
                            9 -> { resetBoard(); isMenuOpen = false }
                            10 -> { toggleThinkingTime() }
                            11 -> { toggleArrowDuration() }
                            12 -> {
                                isMenuOpen = false
                                onOpenSettingsRequested?.invoke() ?: run { currentMenuPage = MenuPage.APPEARANCE }
                            }
                            13 -> { isMenuOpen = false; onToggleVisibilityRequested?.invoke(true, OverlayHideReason.MANUAL) }
                            14 -> { isMenuOpen = false; onCloseListener() }
                        }
                        postInvalidate()
                        return
                    }
                }
            }
            MenuPage.APPEARANCE -> {
                if (settingsTab1Rect.contains(tapX, tapY)) {
                    currentSettingsTab = SettingsTab.DISPLAY_ENGINE
                    settingsScrollY = 0f
                    postInvalidate()
                    return
                }
                if (settingsTab2Rect.contains(tapX, tapY)) {
                    currentSettingsTab = SettingsTab.ANTI_CHEAT
                    settingsScrollY = 0f
                    postInvalidate()
                    return
                }
                if (currentSettingsTab == SettingsTab.DISPLAY_ENGINE) {
                    if (lockBoardToggleRect.contains(tapX, tapY)) {
                        isBoardLocked = !isBoardLocked
                        notifyVisualPrefsChanged()
                        Toast.makeText(context, if (isBoardLocked) "🔒 Papan TERKUNCI (Cegah geser)" else "🔓 Papan TIDAK TERKUNCI (Bisa digeser)", Toast.LENGTH_SHORT).show()
                        postInvalidate()
                        return
                    }
                    if (ghostControlsToggleRect.contains(tapX, tapY)) {
                        isGhostControlsEnabled = !isGhostControlsEnabled
                        notifyVisualPrefsChanged()
                        Toast.makeText(context, if (isGhostControlsEnabled) "👻 Header & Footer Tersembunyi (100% Transparan)" else "👀 Header & Footer Tampil Normal", Toast.LENGTH_SHORT).show()
                        postInvalidate()
                        return
                    }
                    if (highlightStyleBtnRect.contains(tapX, tapY)) {
                        isHighlightFilled = !isHighlightFilled
                        try {
                            context.getSharedPreferences("chessbeater_visual_prefs", Context.MODE_PRIVATE)
                                .edit().putBoolean("highlight_is_filled", isHighlightFilled).apply()
                        } catch (ignored: Exception) {}
                        Log.d("SettingsStyle", "🎨 Style highlight diubah ke: ${if (isHighlightFilled) "FILLED" else "OUTLINED"}")
                        postInvalidate()
                        return
                    }
                } else if (currentSettingsTab == SettingsTab.ANTI_CHEAT) {
                    if (humanizeToggleRect.contains(tapX, tapY)) {
                        com.chessbeater.engine.HumanizationEngine.isHumanizeEnabled = !com.chessbeater.engine.HumanizationEngine.isHumanizeEnabled
                        com.chessbeater.engine.HumanizationEngine.saveSettings(context)
                        postInvalidate()
                        return
                    }
                    if (blunderGuardToggleRect.contains(tapX, tapY)) {
                        com.chessbeater.engine.HumanizationEngine.isBlunderGuardEnabled = !com.chessbeater.engine.HumanizationEngine.isBlunderGuardEnabled
                        com.chessbeater.engine.HumanizationEngine.saveSettings(context)
                        postInvalidate()
                        return
                    }
                    if (naturalDelayToggleRect.contains(tapX, tapY)) {
                        com.chessbeater.engine.HumanizationEngine.isNaturalDelayEnabled = !com.chessbeater.engine.HumanizationEngine.isNaturalDelayEnabled
                        com.chessbeater.engine.HumanizationEngine.saveSettings(context)
                        postInvalidate()
                        return
                    }
                }
                for (rect in appearanceBottomBtnRects) {
                    if (rect.contains(tapX, tapY)) {
                        currentMenuPage = MenuPage.MAIN
                        postInvalidate()
                        return
                    }
                }
            }
            MenuPage.PRESETS -> {
                for (i in presetItemRects.indices) {
                    if (presetItemRects[i].contains(tapX, tapY)) {
                        val preset = presetsList.getOrNull(i)
                        if (preset != null) {
                            activePresetId = preset.id
                            onPresetSelected?.invoke(preset)
                            isMenuOpen = false
                            postInvalidate()
                        }
                        return
                    }
                }
                for (i in presetBottomBtnRects.indices) {
                    if (presetBottomBtnRects[i].contains(tapX, tapY)) {
                        if (i == 0) {
                            // Kalibrasi Baru
                            isMenuOpen = false
                            onStartCalibrationRequested?.invoke()
                        } else {
                            // Kembali
                            currentMenuPage = MenuPage.MAIN
                            postInvalidate()
                        }
                        return
                    }
                }
            }
        }
    }

    private fun handleBoardTap(clicked: Int) {
        val piece = board[clicked]
        Log.d("InteractiveBoardTouch", "Tap di sq=$clicked, Bidak=$piece, Giliran=$currentTurn, OpponentColor=$opponentColor (flipped=$isBoardFlipped)")

        val isPieceNonEmpty = (piece != '.' && piece != ' ')

        if (selectedSquare == null) {
            if (!isPieceNonEmpty) return

            // 1. Cek Giliran: Hanya respon jika giliran User (opponentColor) saat bukan mode koreksi
            if (!isCorrectionMode && currentTurn != opponentColor) {
                Log.w("BoardTouch", "⛔ Bukan giliran user! (Giliran saat ini: $currentTurn, Warna User: $opponentColor)")
                return
            }

            // 2. KUNCI KEPEMILIKAN: User Putih HANYA bisa sentuh bidak Putih, User Hitam HANYA bisa sentuh bidak Hitam
            if (!isCorrectionMode) {
                val isPieceWhite = piece.isUpperCase()
                val userIsWhite = (opponentColor == PlayerColor.WHITE)
                if (isPieceWhite != userIsWhite) {
                    Log.w("BoardTouch", "⛔ Anda tidak bisa menggerakkan bidak lawan (${if (isPieceWhite) "Putih" else "Hitam"})!")
                    return // Tolak sentuhan seketika, bidak lawan tidak bisa dipilih
                }
            }

            selectedSquare = clicked
            computeLegal(clicked)
            postInvalidate()
        } else {
            val from = selectedSquare!!
            when {
                from == clicked -> {
                    // Tap petak yang sama -> Batalkan pilihan
                    selectedSquare = null
                    legalDestinations.clear()
                    postInvalidate()
                }
                clicked in legalDestinations -> {
                    // Tap petak tujuan legal -> Eksekusi langkah
                    executeMove(from, clicked, isStockfish = false)
                }
                isPieceNonEmpty && (piece.isUpperCase() == board[from].isUpperCase()) -> {
                    // Ganti pilihan ke bidak sendiri yang lain
                    if (!isCorrectionMode) {
                        val isPieceWhite = piece.isUpperCase()
                        val userIsWhite = (opponentColor == PlayerColor.WHITE)
                        if (isPieceWhite != userIsWhite) {
                            Log.w("BoardTouch", "⛔ Anda tidak bisa memilih bidak lawan (${if (isPieceWhite) "Putih" else "Hitam"})!")
                            return
                        }
                    }
                    selectedSquare = clicked
                    computeLegal(clicked)
                    postInvalidate()
                }
                else -> {
                    // Tap petak tidak legal -> Tolak & batalkan pilihan
                    Log.w("BoardTouch", "⛔ Langkah tidak sah dari $from ke $clicked!")
                    selectedSquare = null
                    legalDestinations.clear()
                    postInvalidate()
                }
            }
        }
    }


    private fun executeMove(from: Int, to: Int, isStockfish: Boolean, promotionChar: Char? = null) {
        val movingPiece = board[from]
        if (movingPiece == '.') {
            Log.w("StockfishNative", "executeMove ignored: Source square $from is empty")
            return
        }

        val isWhitePiece = movingPiece.isUpperCase()
        val expectedWhite = (currentTurn == PlayerColor.WHITE)

        // Strict Legal Move Validation: Jangan ubah FEN atau giliran jika langkah tidak sah
        if (!isCorrectionMode && !ChessLogic.isMoveLegal(from, to, board, currentTurn, castlingRights)) {
            Log.e("ChessLogic", "⛔ Abaikan langkah tidak sah (illegal move) dari $from ke $to untuk giliran $currentTurn")
            return
        }


        // Save snapshot for Undo
        if (snapshotHistory.size >= 50) {
            snapshotHistory.removeFirst()
        }
        snapshotHistory.addLast(createCurrentSnapshot())

        // Touch-Forwarding to underlying chess app via Accessibility Service
        if (!isStockfish && isTouchForwarding) {
            val sqW = boardRect.width() / 8f
            val sqH = boardRect.height() / 8f
            val (fromDispCol, fromDispRow) = sq2dispCR(from)
            val (toDispCol, toDispRow) = sq2dispCR(to)

            val fromX = boardRect.left + (fromDispCol + 0.5f) * sqW
            val fromY = boardRect.top + (fromDispRow + 0.5f) * sqH
            val toX = boardRect.left + (toDispCol + 0.5f) * sqW
            val toY = boardRect.top + (toDispRow + 0.5f) * sqH

            ChessAccessibilityService.forwardDrag(fromX, fromY, toX, toY, 150L)
        }

        val targetPiece = board[to]
        val isCapture = targetPiece != '.'

        // 1. Move piece on 64 square board
        board[from] = '.'
        board[to] = movingPiece

        // Start Smooth 140ms Sliding Piece Animation Strictly Relative to boardRect
        val sqW = boardRect.width() / 8f
        val sqH = boardRect.height() / 8f
        val (fromDispCol, fromDispRow) = sq2dispCR(from)
        val (toDispCol, toDispRow) = sq2dispCR(to)

        val startX = boardRect.left + fromDispCol * sqW
        val startY = boardRect.top + fromDispRow * sqH
        val endX = boardRect.left + toDispCol * sqW
        val endY = boardRect.top + toDispRow * sqH

        pieceMoveAnimator?.cancel()
        val anim = PieceAnimation(
            pieceChar = movingPiece,
            fromSq = from,
            toSq = to,
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            currentX = startX,
            currentY = startY
        )
        activePieceAnim = anim

        pieceMoveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 140L
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener { va ->
                val f = va.animatedFraction
                anim.currentX = anim.startX + (anim.endX - anim.startX) * f
                anim.currentY = anim.startY + (anim.endY - anim.startY) * f
                postInvalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    activePieceAnim = null
                    postInvalidate()
                }
            })
            start()
        }

        // Pawn Promotion handling
        if (movingPiece == 'P' && to / 8 == 0) {
            board[to] = promotionChar?.uppercaseChar() ?: 'Q'
        } else if (movingPiece == 'p' && to / 8 == 7) {
            board[to] = promotionChar?.lowercaseChar() ?: 'q'
        }

        // Castling King move handling & Rook displacement
        if (movingPiece == 'K') {
            if (from == 60 && to == 62) {
                // White Kingside O-O
                board[63] = '.'
                board[61] = 'R'
            } else if (from == 60 && to == 58) {
                // White Queenside O-O-O
                board[56] = '.'
                board[59] = 'R'
            }
            // Remove White castling rights
            castlingRights = castlingRights.replace("K", "").replace("Q", "")
        } else if (movingPiece == 'k') {
            if (from == 4 && to == 6) {
                // Black Kingside O-O
                board[7] = '.'
                board[5] = 'r'
            } else if (from == 4 && to == 2) {
                // Black Queenside O-O-O
                board[0] = '.'
                board[3] = 'r'
            }
            // Remove Black castling rights
            castlingRights = castlingRights.replace("k", "").replace("q", "")
        }

        // Rook moves / captures invalidate specific castling rights
        if (from == 63 || to == 63) castlingRights = castlingRights.replace("K", "")
        if (from == 56 || to == 56) castlingRights = castlingRights.replace("Q", "")
        if (from == 7 || to == 7) castlingRights = castlingRights.replace("k", "")
        if (from == 0 || to == 0) castlingRights = castlingRights.replace("q", "")
        if (castlingRights.isEmpty()) castlingRights = "-"

        // Update halfmove clock
        if (movingPiece.lowercaseChar() == 'p' || isCapture) {
            halfMoveClock = 0
        } else {
            halfMoveClock++
        }

        lastMoveFrom = from
        lastMoveTo = to
        selectedSquare = null
        legalDestinations.clear()

        // Clear old arrow immediately upon new move execution
        engineBestMove = null
        isArrowVisible = false

        val fromNotation = idx2notation(from)
        val toNotation = idx2notation(to)
        val moverName = if (isStockfish) "🤖" else "👤"
        val moveEntry = "$moverName $movingPiece:$fromNotation➔$toNotation"
        moveHistory.add(moveEntry)

        // Session Logger Recording
        try {
            val moveNotation = "$fromNotation$toNotation${promotionChar ?: ""}"
            val playerStr = if (isWhitePiece) "Putih" else "Hitam"
            com.chessbeater.logging.SessionLogger.logMove(
                context = context,
                moveNumber = moveHistory.size,
                fromTo = moveNotation,
                player = playerStr,
                fen = generateFen(),
                bestMove = engineBestMove,
                eval = evalText
            )
        } catch (ignored: Exception) {}

        currentTurn = if (currentTurn == PlayerColor.WHITE) PlayerColor.BLACK else PlayerColor.WHITE
        if (currentTurn == PlayerColor.WHITE) {
            fullMoveNumber++
        }

        Log.d("StockfishNative", "Move executed: $moveEntry | Next turn: $currentTurn | Castling: $castlingRights | halfMoveClock=$halfMoveClock | fullMoveNumber=$fullMoveNumber")

        if (currentTurn == stockfishColor) {
            isEngineCalculating = true
            triggerEval()
        } else {
            isEngineCalculating = false
        }

        postInvalidate()
    }

    fun getSquareCenterCoordinates(square: String): PointF {
        if (square.length < 2) return PointF(boardRect.centerX(), boardRect.centerY())
        val col = (square[0].lowercaseChar() - 'a').coerceIn(0, 7)
        val row = (8 - (square[1] - '0')).coerceIn(0, 7)
        val squareW = boardRect.width() / 8f
        val squareH = boardRect.height() / 8f

        val finalCol = if (isBoardFlipped) 7 - col else col
        val finalRow = if (isBoardFlipped) 7 - row else row

        val centerX = boardRect.left + (finalCol * squareW) + (squareW / 2f)
        val centerY = boardRect.top + (finalRow * squareH) + (squareH / 2f)
        return PointF(centerX, centerY)
    }

    fun getSquareTopLeftCoordinates(sq: Int): PointF {
        val row = (sq / 8).coerceIn(0, 7)
        val col = (sq % 8).coerceIn(0, 7)
        val squareW = boardRect.width() / 8f
        val squareH = boardRect.height() / 8f

        val finalCol = if (isBoardFlipped) 7 - col else col
        val finalRow = if (isBoardFlipped) 7 - row else row

        val left = boardRect.left + (finalCol * squareW)
        val top = boardRect.top + (finalRow * squareH)
        return PointF(left, top)
    }

    private fun triggerEval() {
        isEngineCalculating = true
        engineBestMove = null
        isArrowVisible = false
        evalTimeoutJob?.cancel()

        val isStockfishNativeRunning = com.chessbeater.engine.StockfishBridge.getInstance(context).isNativeEngineAlive()
        Log.d("EngineCheck", "Engine Aktif: ${if (isStockfishNativeRunning) "STOCKFISH NATIVE C++ (ORIGINAL)" else "FALLBACK MINI-ENGINE (WEAK)"}")

        val fen = generateFen()
        val inCheck = ChessLogic.isKingInCheck(board, currentTurn)
        Log.d("CheckAudit", "Turn: $currentTurn | InCheck: $inCheck | FEN: $fen")
        Log.d("StockfishDebug", "Evaluating FEN: $fen")
        Log.d("StockfishNative", "Triggering evaluation for FEN: $fen")
        onEvaluateRequested(fen)
        postInvalidate()
    }

    // ====== CHESS LOGIC ======

    private fun loadFen(fen: String) {
        board.fill('.')
        val parts = fen.trim().split("\\s+".toRegex())
        var idx = 0
        for (ch in (parts.firstOrNull() ?: return)) {
            when { ch == '/' -> Unit; ch.isDigit() -> idx += ch - '0'; idx < 64 -> board[idx++] = ch }
        }
        currentTurn = if (parts.getOrNull(1) == "b") PlayerColor.BLACK else PlayerColor.WHITE
        castlingRights = parts.getOrNull(2) ?: "KQkq"
        halfMoveClock = parts.getOrNull(4)?.toIntOrNull() ?: 0
        fullMoveNumber = parts.getOrNull(5)?.toIntOrNull() ?: 1
    }

    private fun generateFen(
        customBoard: CharArray = board,
        activeTurn: PlayerColor = currentTurn,
        cRights: String = castlingRights,
        enPassantSquare: String = "-",
        hMoveClock: Int = halfMoveClock,
        fMoveNumber: Int = fullMoveNumber
    ): String {
        val fenBuilder = StringBuilder()

        for (row in 0..7) {
            var emptyCount = 0
            for (col in 0..7) {
                val piece = customBoard[row * 8 + col]
                if (piece == '.' || piece == ' ' || piece == '0') {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        fenBuilder.append(emptyCount)
                        emptyCount = 0
                    }
                    fenBuilder.append(piece)
                }
            }
            if (emptyCount > 0) fenBuilder.append(emptyCount)
            if (row < 7) fenBuilder.append('/')
        }

        val turnStr = if (activeTurn == PlayerColor.WHITE) "w" else "b"
        val castling = if (cRights.isBlank() || cRights == "-") "-" else cRights
        return "$fenBuilder $turnStr $castling $enPassantSquare $hMoveClock $fMoveNumber"
    }

    private fun idx2notation(idx: Int): String {
        val f = ('a'.code + (idx % 8)).toChar()
        val r = ('0'.code + (8 - (idx / 8))).toChar()
        return "$f$r"
    }

    private fun sq2dispCR(sq: Int): Pair<Int, Int> {
        val r = sq / 8; val c = sq % 8
        return Pair(if (isBoardFlipped) 7 - c else c, if (isBoardFlipped) 7 - r else r)
    }

    private fun computeLegal(sqIdx: Int) {
        legalDestinations.clear()
        val p = board[sqIdx]; if (p == '.') return
        val isW = p.isUpperCase(); val row = sqIdx / 8; val col = sqIdx % 8
        when (p.uppercaseChar()) {
            'P' -> {
                val dir = if (isW) -1 else 1; val startRow = if (isW) 6 else 1; val nr = row + dir
                if (nr in 0..7 && board[nr * 8 + col] == '.') {
                    legalDestinations.add(nr * 8 + col)
                    val dr = row + 2 * dir
                    if (row == startRow && board[dr * 8 + col] == '.') legalDestinations.add(dr * 8 + col)
                }
                for (dc in listOf(-1, 1)) { val nc = col + dc
                    if (nr in 0..7 && nc in 0..7) { val t = board[nr * 8 + nc]; if (t != '.' && t.isUpperCase() != isW) legalDestinations.add(nr * 8 + nc) } }
            }
            'N' -> for ((dr, dc) in listOf(-2 to -1,-2 to 1,-1 to -2,-1 to 2,1 to -2,1 to 2,2 to -1,2 to 1)) {
                val nr = row+dr; val nc = col+dc
                if (nr in 0..7 && nc in 0..7) { val t = board[nr*8+nc]; if (t=='.'||t.isUpperCase()!=isW) legalDestinations.add(nr*8+nc) } }
            'B', 'R', 'Q' -> {
                val dirs = when (p.uppercaseChar()) {
                    'B' -> listOf(-1 to -1,-1 to 1,1 to -1,1 to 1)
                    'R' -> listOf(-1 to 0,1 to 0,0 to -1,0 to 1)
                    else -> listOf(-1 to -1,-1 to 1,1 to -1,1 to 1,-1 to 0,1 to 0,0 to -1,0 to 1)
                }
                for ((dr, dc) in dirs) { var nr = row+dr; var nc = col+dc
                    while (nr in 0..7 && nc in 0..7) { val t = board[nr*8+nc]
                        if (t == '.') legalDestinations.add(nr*8+nc) else { if (t.isUpperCase()!=isW) legalDestinations.add(nr*8+nc); break }
                        nr+=dr; nc+=dc } }
            }
            'K' -> {
                val enemyKingChar = if (isW) 'k' else 'K'
                val enemyKingIdx = board.indexOf(enemyKingChar)
                val enemyKingRow = if (enemyKingIdx >= 0) enemyKingIdx / 8 else -99
                val enemyKingCol = if (enemyKingIdx >= 0) enemyKingIdx % 8 else -99

                for (dr in -1..1) {
                    for (dc in -1..1) {
                        if (dr == 0 && dc == 0) continue
                        val nr = row + dr; val nc = col + dc
                        if (nr in 0..7 && nc in 0..7) {
                            // Rule: King cannot move adjacent to enemy King
                            if (kotlin.math.abs(nr - enemyKingRow) <= 1 && kotlin.math.abs(nc - enemyKingCol) <= 1) continue

                            val t = board[nr * 8 + nc]
                            if (t == '.' || t.isUpperCase() != isW) legalDestinations.add(nr * 8 + nc)
                        }
                    }
                }
                // Castling for King with Strict In-Check and Square Attack Validation
                val side = if (isW) PlayerColor.WHITE else PlayerColor.BLACK
                val enemySide = if (isW) PlayerColor.BLACK else PlayerColor.WHITE
                val inCheck = ChessLogic.isKingInCheck(board, side)

                if (!inCheck) {
                    if (isW && sqIdx == 60) {
                        // White O-O (Kingside: e1 -> g1 / 60 -> 62)
                        if (castlingRights.contains('K') &&
                            board[61] == '.' && board[62] == '.' && board[63] == 'R' &&
                            !ChessLogic.isSquareAttacked(61, board, enemySide) &&
                            !ChessLogic.isSquareAttacked(62, board, enemySide)
                        ) {
                            legalDestinations.add(62)
                        }
                        // White O-O-O (Queenside: e1 -> c1 / 60 -> 58)
                        if (castlingRights.contains('Q') &&
                            board[59] == '.' && board[58] == '.' && board[57] == '.' && board[56] == 'R' &&
                            !ChessLogic.isSquareAttacked(59, board, enemySide) &&
                            !ChessLogic.isSquareAttacked(58, board, enemySide)
                        ) {
                            legalDestinations.add(58)
                        }
                    } else if (!isW && sqIdx == 4) {
                        // Black O-O (Kingside: e8 -> g8 / 4 -> 6)
                        if (castlingRights.contains('k') &&
                            board[5] == '.' && board[6] == '.' && board[7] == 'r' &&
                            !ChessLogic.isSquareAttacked(5, board, enemySide) &&
                            !ChessLogic.isSquareAttacked(6, board, enemySide)
                        ) {
                            legalDestinations.add(6)
                        }
                        // Black O-O-O (Queenside: e8 -> c8 / 4 -> 2)
                        if (castlingRights.contains('q') &&
                            board[3] == '.' && board[2] == '.' && board[1] == '.' && board[0] == 'r' &&
                            !ChessLogic.isSquareAttacked(3, board, enemySide) &&
                            !ChessLogic.isSquareAttacked(2, board, enemySide)
                        ) {
                            legalDestinations.add(2)
                        }
                    }
                }
            }
        }

        // Strict validation: Filter out moves that leave the King in check (e.g. pinned pieces)
        val side = if (isW) PlayerColor.WHITE else PlayerColor.BLACK
        legalDestinations.retainAll { dest ->
            val tempBoard = board.copyOf()
            ChessLogic.applyMoveToBoardArray(sqIdx, dest, tempBoard)
            !ChessLogic.isKingInCheck(tempBoard, side)
        }
    }


    private fun piece2unicode(p: Char): String = when (p) {
        'K' -> "♔"; 'Q' -> "♕"; 'R' -> "♖"; 'B' -> "♗"; 'N' -> "♘"; 'P' -> "♙"
        'k' -> "♚"; 'q' -> "♛"; 'r' -> "♜"; 'b' -> "♝"; 'n' -> "♞"; 'p' -> "♟"
        else -> ""
    }

    companion object {
        fun uciToPixel(uciMove: String, boardRect: RectF, isFlipped: Boolean): Pair<PointF, PointF>? {
            if (uciMove.length < 4) return null

            // 1. Parse File ('a'..'h') -> Kolom X (0..7)
            val fromCol = uciMove[0].lowercaseChar() - 'a'
            val toCol = uciMove[2].lowercaseChar() - 'a'

            // 2. Parse Rank ('1'..'8') -> Baris Y (0..7, 0=Rank 1, 7=Rank 8)
            val fromRank = uciMove[1] - '1'
            val toRank = uciMove[3] - '1'

            if (fromCol !in 0..7 || toCol !in 0..7 || fromRank !in 0..7 || toRank !in 0..7) return null

            val sqW = boardRect.width() / 8f
            val sqH = boardRect.height() / 8f

            // 3. Konversi ke Posisi Tampilan (White di bawah -> Rank 1 = displayRow 7, Rank 8 = displayRow 0)
            val displayFromCol = if (isFlipped) 7 - fromCol else fromCol
            val displayFromRow = if (isFlipped) fromRank else 7 - fromRank

            val displayToCol = if (isFlipped) 7 - toCol else toCol
            val displayToRow = if (isFlipped) toRank else 7 - toRank

            val fromX = boardRect.left + (displayFromCol + 0.5f) * sqW
            val fromY = boardRect.top + (displayFromRow + 0.5f) * sqH

            val toX = boardRect.left + (displayToCol + 0.5f) * sqW
            val toY = boardRect.top + (displayToRow + 0.5f) * sqH

            return Pair(PointF(fromX, fromY), PointF(toX, toY))
        }

        fun isKnightMove(uciMove: String): Boolean {
            if (uciMove.length < 4) return false
            val fileDiff = abs(uciMove[0].lowercaseChar() - uciMove[2].lowercaseChar())
            val rankDiff = abs(uciMove[1] - uciMove[3])
            return (fileDiff == 1 && rankDiff == 2) || (fileDiff == 2 && rankDiff == 1)
        }
    }
}
