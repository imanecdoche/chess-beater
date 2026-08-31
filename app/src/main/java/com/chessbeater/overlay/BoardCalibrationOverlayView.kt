package com.chessbeater.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.chessbeater.data.CalibrationPreset
import com.chessbeater.service.ChessAccessibilityService
import kotlin.math.max
import kotlin.math.min

/**
 * Sprint 36: Interactive Fullscreen Calibration Overlay with Multi-Preset Saving & Package Binding.
 * Allows the user to physically drag and resize an 8x8 bounding box directly over any chess app,
 * and save it as a named preset with automatic app package binding.
 */
@SuppressLint("ViewConstructor")
class BoardCalibrationOverlayView(
    context: Context,
    initialRect: Rect? = null,
    private val onSaveListener: (Rect) -> Unit,
    private val onSavePresetListener: ((CalibrationPreset) -> Unit)? = null,
    private val onCancelListener: () -> Unit
) : View(context) {

    private val neonBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 230, 118) // #00E676 Neon Green
        style = Paint.Style.STROKE
        strokeWidth = 5.0f
    }

    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 0, 230, 118)
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
    }

    private val fillOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(135, 10, 14, 20) // Dim backdrop outside board
        style = Paint.Style.FILL
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 214, 0) // #FFD600 Neon Gold
        style = Paint.Style.FILL
    }

    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3.0f
    }

    private val saveBtnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 230, 118)
        style = Paint.Style.FILL
    }

    private val cancelBtnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 32, 42, 56)
        style = Paint.Style.FILL
    }

    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 30f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val guideTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    // Modal Card Paints
    private val dialogBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(25, 32, 44) }
    private val dialogBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.rgb(0, 230, 118) }
    private val dialogTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.rgb(0, 230, 118) }
    private val dialogTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.WHITE }
    private val dialogSubtextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); color = Color.rgb(148, 163, 184) }
    private val checkboxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(0, 230, 118) }
    private val checkboxBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f; color = Color.WHITE }

    // Geometry
    private val boardBox = RectF()
    private val resizeHandleCenter = PointF()
    private val handleRadius = 42f

    private val saveBtnBounds = RectF()
    private val cancelBtnBounds = RectF()

    // Dialog state & geometry
    private var isSaveDialogOpen = false
    private val dialogBounds = RectF()
    private val dialogConfirmBtnBounds = RectF()
    private val dialogCancelBtnBounds = RectF()
    private val dialogCheckboxBounds = RectF()
    private var isPackageBindingChecked = true

    private var detectedPackage: String? = null
    private var presetDefaultName: String = "Preset Papan"

    // Touch Dragging State
    private enum class DragMode { NONE, MOVE_BOARD, RESIZE_CORNER }
    private var currentDragMode = DragMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    init {
        setBackgroundColor(Color.TRANSPARENT)
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels.toFloat()
        val screenH = dm.heightPixels.toFloat()

        if (initialRect != null && initialRect.width() > 100) {
            boardBox.set(initialRect)
        } else {
            val defaultSize = min(screenW, screenH) * 0.90f
            val left = (screenW - defaultSize) / 2f
            val top = (screenH - defaultSize) / 2f
            boardBox.set(left, top, left + defaultSize, top + defaultSize)
        }

        detectedPackage = ChessAccessibilityService.currentForegroundPackage
        presetDefaultName = when {
            detectedPackage?.contains("chess", ignoreCase = true) == true -> "Chess.com Portrait"
            detectedPackage?.contains("lichess", ignoreCase = true) == true -> "Lichess App"
            detectedPackage != null -> "Preset ${detectedPackage!!.substringAfterLast('.')}"
            else -> "Preset Papan Catur"
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ensureBoardWithinScreen(w.toFloat(), h.toFloat())
    }

    private fun ensureBoardWithinScreen(screenW: Float, screenH: Float) {
        val size = boardBox.width()
        val clampedSize = size.coerceIn(200f, min(screenW, screenH))
        val left = boardBox.left.coerceIn(0f, screenW - clampedSize)
        val top = boardBox.top.coerceIn(0f, screenH - clampedSize)
        boardBox.set(left, top, left + clampedSize, top + clampedSize)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Draw dimming shade outside of board box
        canvas.save()
        canvas.clipRect(boardBox, Region.Op.DIFFERENCE)
        canvas.drawRect(0f, 0f, w, h, fillOverlayPaint)
        canvas.restore()

        // 2. Draw 8x8 internal grid lines
        val step = boardBox.width() / 8f
        for (i in 1..7) {
            val x = boardBox.left + i * step
            val y = boardBox.top + i * step
            canvas.drawLine(x, boardBox.top, x, boardBox.bottom, gridLinePaint)
            canvas.drawLine(boardBox.left, y, boardBox.right, y, gridLinePaint)
        }

        // 3. Draw Board Box Outer Neon Border
        canvas.drawRoundRect(boardBox, 10f, 10f, neonBorderPaint)

        // 4. Draw Resize Corner Handle at bottom-right
        resizeHandleCenter.set(boardBox.right, boardBox.bottom)
        canvas.drawCircle(resizeHandleCenter.x, resizeHandleCenter.y, handleRadius, handlePaint)
        canvas.drawCircle(resizeHandleCenter.x, resizeHandleCenter.y, handleRadius, handleBorderPaint)

        // 5. Draw Header Hint
        val hintY = if (boardBox.top > 120f) boardBox.top - 30f else boardBox.bottom + 130f
        val pkgStr = if (!detectedPackage.isNullOrBlank()) " ($detectedPackage)" else ""
        canvas.drawText("📐 Kalibrasi Papan Catur$pkgStr", w / 2f, hintY, guideTextPaint)

        // 6. Draw Action Buttons
        val btnW = 280f
        val btnH = 75f
        val btnY = if (boardBox.bottom + 170f < h) boardBox.bottom + 65f else boardBox.top - 120f

        val spacing = 20f
        val totalBtnWidth = (btnW * 2) + spacing
        val startBtnX = (w - totalBtnWidth) / 2f

        saveBtnBounds.set(startBtnX, btnY, startBtnX + btnW, btnY + btnH)
        cancelBtnBounds.set(startBtnX + btnW + spacing, btnY, startBtnX + (btnW * 2) + spacing, btnY + btnH)

        // Save Preset Button
        canvas.drawRoundRect(saveBtnBounds, 36f, 36f, saveBtnBgPaint)
        btnTextPaint.color = Color.rgb(10, 20, 15)
        canvas.drawText("💾 SIMPAN PRESET", saveBtnBounds.centerX(), saveBtnBounds.centerY() + 11f, btnTextPaint)

        // Cancel Button
        canvas.drawRoundRect(cancelBtnBounds, 36f, 36f, cancelBtnBgPaint)
        btnTextPaint.color = Color.WHITE
        canvas.drawText("BATAL", cancelBtnBounds.centerX(), cancelBtnBounds.centerY() + 11f, btnTextPaint)

        // 7. Draw Save Dialog Card if Open
        if (isSaveDialogOpen) {
            drawSavePresetDialog(canvas, w, h)
        }
    }

    private fun drawSavePresetDialog(canvas: Canvas, screenW: Float, screenH: Float) {
        // Scrim
        canvas.drawRect(0f, 0f, screenW, screenH, fillOverlayPaint)

        val cardW = (screenW * 0.88f).coerceIn(300f, 540f)
        val cardH = 340f
        val cardL = (screenW - cardW) / 2f
        val cardT = (screenH - cardH) / 2f

        dialogBounds.set(cardL, cardT, cardL + cardW, cardT + cardH)
        canvas.drawRoundRect(dialogBounds, 24f, 24f, dialogBgPaint)
        canvas.drawRoundRect(dialogBounds, 24f, 24f, dialogBorderPaint)

        // Title
        dialogTitlePaint.textSize = 28f
        canvas.drawText("💾 SIMPAN PRESET KALIBRASI", dialogBounds.centerX(), cardT + 42f, dialogTitlePaint)

        // Name info
        dialogTextPaint.textSize = 24f
        canvas.drawText("Nama: $presetDefaultName", cardL + 24f, cardT + 90f, dialogTextPaint)

        // Dimensions info
        dialogSubtextPaint.textSize = 20f
        canvas.drawText("Ukuran: ${boardBox.width().toInt()}x${boardBox.height().toInt()} px  |  Posisi: (${boardBox.left.toInt()}, ${boardBox.top.toInt()})", cardL + 24f, cardT + 125f, dialogSubtextPaint)

        // Package Binding Row
        val checkboxSize = 36f
        val checkboxT = cardT + 160f
        dialogCheckboxBounds.set(cardL + 24f, checkboxT, cardL + 24f + checkboxSize, checkboxT + checkboxSize)

        if (isPackageBindingChecked) {
            canvas.drawRoundRect(dialogCheckboxBounds, 8f, 8f, checkboxPaint)
            btnTextPaint.color = Color.BLACK
            btnTextPaint.textSize = 24f
            canvas.drawText("✓", dialogCheckboxBounds.centerX(), dialogCheckboxBounds.centerY() + 8f, btnTextPaint)
        } else {
            canvas.drawRoundRect(dialogCheckboxBounds, 8f, 8f, checkboxBorderPaint)
        }

        val pkgLabel = if (!detectedPackage.isNullOrBlank()) "Tautkan ke: $detectedPackage (Auto-Switch)" else "Tautkan ke game saat ini (Auto-Switch)"
        dialogTextPaint.textSize = 21f
        canvas.drawText(pkgLabel, cardL + 72f, checkboxT + 26f, dialogTextPaint)

        // Confirm & Cancel Buttons
        val btnH = 68f
        val btnW = (cardW - 60f) / 2f
        val btnY = cardT + cardH - btnH - 24f

        dialogConfirmBtnBounds.set(cardL + 20f, btnY, cardL + 20f + btnW, btnY + btnH)
        dialogCancelBtnBounds.set(cardL + 40f + btnW, btnY, cardL + 40f + btnW * 2, btnY + btnH)

        canvas.drawRoundRect(dialogConfirmBtnBounds, 34f, 34f, saveBtnBgPaint)
        btnTextPaint.color = Color.rgb(10, 20, 15)
        btnTextPaint.textSize = 24f
        canvas.drawText("✔ SIMPAN & PAKAI", dialogConfirmBtnBounds.centerX(), dialogConfirmBtnBounds.centerY() + 9f, btnTextPaint)

        canvas.drawRoundRect(dialogCancelBtnBounds, 34f, 34f, cancelBtnBgPaint)
        btnTextPaint.color = Color.WHITE
        btnTextPaint.textSize = 24f
        canvas.drawText("KEMBALI", dialogCancelBtnBounds.centerX(), dialogCancelBtnBounds.centerY() + 9f, btnTextPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        if (isSaveDialogOpen) {
            if (event.action == MotionEvent.ACTION_UP) {
                if (dialogCheckboxBounds.contains(x, y) || RectF(dialogCheckboxBounds.left, dialogCheckboxBounds.top, dialogBounds.right - 20f, dialogCheckboxBounds.bottom + 10f).contains(x, y)) {
                    isPackageBindingChecked = !isPackageBindingChecked
                    invalidate()
                    return true
                }

                if (dialogConfirmBtnBounds.contains(x, y)) {
                    val finalRect = Rect(
                        boardBox.left.toInt(),
                        boardBox.top.toInt(),
                        boardBox.right.toInt(),
                        boardBox.bottom.toInt()
                    )
                    try {
                        context.getSharedPreferences("chessbeater_visual_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putFloat("board_left", boardBox.left)
                            .putFloat("board_top", boardBox.top)
                            .putFloat("board_right", boardBox.right)
                            .putFloat("board_bottom", boardBox.bottom)
                            .apply()
                        android.util.Log.d("Calibration", "📐 Bounds tersimpan: L=${boardBox.left}, T=${boardBox.top}, R=${boardBox.right}, B=${boardBox.bottom}")
                    } catch (e: Exception) {
                        android.util.Log.w("Calibration", "Gagal menyimpan bounds ke SharedPreferences", e)
                    }

                    val preset = CalibrationPreset(
                        name = presetDefaultName,
                        packageName = if (isPackageBindingChecked) detectedPackage else null,
                        x = boardBox.left,
                        y = boardBox.top,
                        width = boardBox.width(),
                        height = boardBox.height(),
                        isFlipped = false
                    )
                    onSavePresetListener?.invoke(preset)
                    onSaveListener(finalRect)
                    isSaveDialogOpen = false
                    return true
                }

                if (dialogCancelBtnBounds.contains(x, y)) {
                    isSaveDialogOpen = false
                    invalidate()
                    return true
                }
            }
            return true
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                lastTouchY = y

                // Check button taps
                if (saveBtnBounds.contains(x, y)) {
                    detectedPackage = ChessAccessibilityService.currentForegroundPackage
                    isSaveDialogOpen = true
                    invalidate()
                    return true
                }
                if (cancelBtnBounds.contains(x, y)) {
                    onCancelListener()
                    return true
                }

                // Check resize handle tap
                val distToHandle = Math.hypot((x - resizeHandleCenter.x).toDouble(), (y - resizeHandleCenter.y).toDouble())
                if (distToHandle <= handleRadius * 2.0) {
                    currentDragMode = DragMode.RESIZE_CORNER
                    return true
                }

                // Check board move tap
                if (boardBox.contains(x, y)) {
                    currentDragMode = DragMode.MOVE_BOARD
                    return true
                }

                currentDragMode = DragMode.NONE
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastTouchX
                val dy = y - lastTouchY

                when (currentDragMode) {
                    DragMode.MOVE_BOARD -> {
                        boardBox.offset(dx, dy)
                        ensureBoardWithinScreen(width.toFloat(), height.toFloat())
                        invalidate()
                    }
                    DragMode.RESIZE_CORNER -> {
                        // Maintain 1:1 Aspect ratio by using max delta
                        val delta = max(dx, dy)
                        val newSize = (boardBox.width() + delta).coerceIn(200f, min(width.toFloat(), height.toFloat()))
                        boardBox.right = boardBox.left + newSize
                        boardBox.bottom = boardBox.top + newSize
                        ensureBoardWithinScreen(width.toFloat(), height.toFloat())
                        invalidate()
                    }
                    DragMode.NONE -> {}
                }

                lastTouchX = x
                lastTouchY = y
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentDragMode = DragMode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
