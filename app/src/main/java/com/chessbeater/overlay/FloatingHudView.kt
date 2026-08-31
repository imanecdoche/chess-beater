package com.chessbeater.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.chessbeater.vision.models.PlayerColor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Minimalist, high-performance draggable floating HUD widget with Player Color Toggle.
 */
@SuppressLint("ViewConstructor")
class FloatingHudView(
    context: Context,
    private val onDragListener: (dx: Int, dy: Int) -> Unit,
    private val onCloseListener: () -> Unit,
    private val onCalibrateListener: (() -> Unit)? = null,
    private val onMiniBoardToggleListener: (() -> Unit)? = null,
    private val onColorToggleListener: (() -> Unit)? = null
) : View(context) {

    // Telemetry & State
    private var bestMoveText: String = "..."
    private var evalScoreText: String = "+0.00"
    private var evalCentipawns: Int = 0
    private var mateInMoves: Int? = null
    private var depth: Int = 0
    private var latencyMs: Long = 0L
    private var isExpanded: Boolean = true
    private var playerColor: PlayerColor = PlayerColor.WHITE

    // Evaluation bar ratio
    private var evalRatio: Float = 0.5f
    private var animatedEvalRatio: Float = 0.5f
    private var evalAnimator: ValueAnimator? = null

    // Diagnostics
    private var diagnosticFps: Float = 0f
    private var isBoardDetected: Boolean = false
    private var isEngineCalculating: Boolean = false

    // Touch dragging tracking
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private val touchSlop = 10f

    // Action button hit areas
    private val colorToggleBtnBounds = RectF()
    private val miniBoardBtnBounds = RectF()
    private val calibBtnBounds = RectF()
    private val closeBtnBounds = RectF()

    private val hudBounds = RectF()
    private val evalBarBounds = RectF()
    private val evalBarClipPath = Path()

    // Paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 18, 22, 28); style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 2.5f }
    private val whiteEvalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val blackEvalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(33, 33, 33); style = Paint.Style.FILL }
    private val evalDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 61, 0); style = Paint.Style.FILL }
    private val bestMovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ArrowColorTheme.COLOR_BEST_MOVE; textSize = 32f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 28f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val telemetryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(140, 150, 165); textSize = 21f }
    private val diagnosticPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 230, 118); textSize = 19f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL) }
    private val iconBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 44, 56, 75); style = Paint.Style.FILL }
    private val iconTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22f; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun setPlayerColor(color: PlayerColor) {
        this.playerColor = color
        postInvalidate()
    }

    fun updateData(
        bestMove: String,
        evalCp: Int?,
        mate: Int?,
        searchDepth: Int,
        latency: Long
    ) {
        this.bestMoveText = if (bestMove.startsWith("⏳") || bestMove.startsWith("Giliran")) bestMove else formatBestMove(bestMove)
        this.evalCentipawns = evalCp ?: 0
        this.mateInMoves = mate
        this.depth = searchDepth
        this.latencyMs = latency

        this.evalScoreText = when {
            mate != null -> if (mate > 0) "M$mate" else "-M${abs(mate)}"
            evalCp != null -> {
                val score = evalCp / 100.0
                if (score >= 0) "+%.2f".format(score) else "%.2f".format(score)
            }
            else -> "+0.00"
        }

        val targetRatio = when {
            mate != null -> if (mate > 0) 0.98f else 0.02f
            else -> {
                val cpClamped = (evalCp ?: 0).coerceIn(-1000, 1000)
                (1.0f / (1.0f + exp(-cpClamped / 200.0f))).toFloat()
            }
        }

        this.evalRatio = targetRatio
        evalAnimator?.cancel()
        evalAnimator = ValueAnimator.ofFloat(animatedEvalRatio, targetRatio).apply {
            duration = 250L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animatedEvalRatio = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun updateDiagnostic(fps: Float, boardDetected: Boolean, engineCalculating: Boolean) {
        this.diagnosticFps = fps
        this.isBoardDetected = boardDetected
        this.isEngineCalculating = engineCalculating
        postInvalidate()
    }

    private fun formatBestMove(move: String): String {
        return if (move.length >= 4) {
            val from = move.substring(0, 2)
            val to = move.substring(2, 4)
            val promo = if (move.length > 4) "=${move.substring(4).uppercase()}" else ""
            "$from ➔ $to$promo"
        } else {
            move
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false

                if (isExpanded) {
                    if (colorToggleBtnBounds.contains(x, y)) {
                        onColorToggleListener?.invoke()
                        return true
                    }
                    if (miniBoardBtnBounds.contains(x, y)) {
                        onMiniBoardToggleListener?.invoke()
                        return true
                    }
                    if (calibBtnBounds.contains(x, y)) {
                        onCalibrateListener?.invoke()
                        return true
                    }
                    if (closeBtnBounds.contains(x, y)) {
                        onCloseListener()
                        return true
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()

                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    isDragging = true
                    onDragListener(dx, dy)
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    isExpanded = !isExpanded
                    requestLayout()
                    invalidate()
                }
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val w = if (isExpanded) (310 * density).toInt() else (65 * density).toInt()
        val h = if (isExpanded) (138 * density).toInt() else (65 * density).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val radius = 24f

        hudBounds.set(2f, 2f, w - 2f, h - 2f)

        canvas.drawRoundRect(hudBounds, radius, radius, bgPaint)
        canvas.drawRoundRect(hudBounds, radius, radius, borderPaint)

        if (!isExpanded) {
            val scoreCol = ArrowColorTheme.getColorForEvaluation(evalCentipawns, mateInMoves)
            scorePaint.color = scoreCol
            scorePaint.textSize = 24f
            val textW = scorePaint.measureText(evalScoreText)
            canvas.drawText(evalScoreText, (w - textW) / 2f, h / 2f + 8f, scorePaint)
            return
        }

        // 1. Draw Vertical Evaluation Bar
        val barLeft = 14f
        val barTop = 14f
        val barWidth = 18f
        val barBottom = h - 14f
        val barHeight = barBottom - barTop

        evalBarBounds.set(barLeft, barTop, barLeft + barWidth, barBottom)
        evalBarClipPath.reset()
        evalBarClipPath.addRoundRect(evalBarBounds, 8f, 8f, Path.Direction.CW)

        canvas.save()
        canvas.clipPath(evalBarClipPath)

        val whiteHeight = barHeight * animatedEvalRatio
        canvas.drawRect(barLeft, barTop, barLeft + barWidth, barTop + whiteHeight, whiteEvalPaint)
        canvas.drawRect(barLeft, barTop + whiteHeight, barLeft + barWidth, barBottom, blackEvalPaint)
        canvas.drawRect(barLeft, barTop + barHeight / 2f - 1f, barLeft + barWidth, barTop + barHeight / 2f + 1f, evalDividerPaint)
        canvas.restore()

        // 2. Action Icons (Color Toggle ⚪/⚫, Mini-Board ♟, Calibrate 📐, Close ✕)
        val btnSize = 36f
        val closeBtnRight = w - 10f
        val calibBtnRight = closeBtnRight - btnSize - 6f
        val miniBoardBtnRight = calibBtnRight - btnSize - 6f
        val colorBtnRight = miniBoardBtnRight - btnSize - 6f

        closeBtnBounds.set(closeBtnRight - btnSize, 12f, closeBtnRight, 12f + btnSize)
        calibBtnBounds.set(calibBtnRight - btnSize, 12f, calibBtnRight, 12f + btnSize)
        miniBoardBtnBounds.set(miniBoardBtnRight - btnSize, 12f, miniBoardBtnRight, 12f + btnSize)
        colorToggleBtnBounds.set(colorBtnRight - btnSize, 12f, colorBtnRight, 12f + btnSize)

        val colorIcon = if (playerColor == PlayerColor.WHITE) "⚪" else "⚫"
        canvas.drawRoundRect(colorToggleBtnBounds, 8f, 8f, iconBtnPaint)
        canvas.drawText(colorIcon, colorToggleBtnBounds.centerX(), colorToggleBtnBounds.centerY() + 6f, iconTextPaint)

        canvas.drawRoundRect(miniBoardBtnBounds, 8f, 8f, iconBtnPaint)
        canvas.drawText("♟", miniBoardBtnBounds.centerX(), miniBoardBtnBounds.centerY() + 6f, iconTextPaint)

        canvas.drawRoundRect(calibBtnBounds, 8f, 8f, iconBtnPaint)
        canvas.drawText("📐", calibBtnBounds.centerX(), calibBtnBounds.centerY() + 6f, iconTextPaint)

        canvas.drawRoundRect(closeBtnBounds, 8f, 8f, iconBtnPaint)
        canvas.drawText("✕", closeBtnBounds.centerX(), closeBtnBounds.centerY() + 6f, iconTextPaint)

        // 3. Text Information Column
        val contentLeft = barLeft + barWidth + 14f

        val moveColor = ArrowColorTheme.getColorForEvaluation(evalCentipawns, mateInMoves)
        bestMovePaint.color = moveColor
        canvas.drawText(bestMoveText, contentLeft, 38f, bestMovePaint)

        scorePaint.color = Color.WHITE
        scorePaint.textSize = 25f
        canvas.drawText(evalScoreText, contentLeft, 68f, scorePaint)

        val telemetryStr = "Depth:$depth • ${latencyMs}ms • ${if (playerColor == PlayerColor.WHITE) "Putih" else "Hitam"}"
        canvas.drawText(telemetryStr, contentLeft, 94f, telemetryPaint)

        // 4. Vision & Engine Diagnostic Status Bar
        val boardStatus = if (isBoardDetected) "OK" else "SEARCH"
        val engineStatus = if (isEngineCalculating) "CALC" else "IDLE"
        val diagStr = "FPS:%.0f | %s | %s".format(diagnosticFps, boardStatus, engineStatus)

        diagnosticPaint.color = if (isBoardDetected) Color.rgb(0, 230, 118) else Color.rgb(255, 171, 0)
        canvas.drawText(diagStr, contentLeft, 118f, diagnosticPaint)
    }

    private fun exp(v: Float): Float = Math.exp(v.toDouble()).toFloat()
}
