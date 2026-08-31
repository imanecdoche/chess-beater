package com.chessbeater.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max

/**
 * Sprint 33: FloatingEyeToggleView
 * Lingkaran tombol mata 👁️ dengan opsi ukuran (56dp, 72dp, 88dp), touch target minimum 80dp x 80dp,
 * Single Tap untuk Restore Board, dan Long Press (>600ms) untuk menyembunyikan mata (Ghost Invisible Mode).
 */
enum class EyeSize(val sizeDp: Int) {
    NORMAL(56),
    LARGE(72),
    EXTRA_LARGE(88);

    companion object {
        fun fromDp(dp: Int): EyeSize = when {
            dp <= 60 -> NORMAL
            dp <= 76 -> LARGE
            else -> EXTRA_LARGE
        }
    }
}

@SuppressLint("ViewConstructor")
class FloatingEyeToggleView(
    context: Context,
    private val onDragListener: (dx: Int, dy: Int) -> Unit,
    private val onClickListener: () -> Unit,
    var onEyeLongPressed: (() -> Unit)? = null,
    var eyeSizeDp: Int = 72,
    var floatingEyeAlpha: Float = 0.85f
) : View(context) {

    private val density = context.resources.displayMetrics.density

    private var rawDownX = 0f
    private var rawDownY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private val touchSlop = 10f * density
    private var activePointerId = -1

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isLongPressTriggered = false
    private val longPressRunnable = Runnable {
        if (!isDragging) {
            isLongPressTriggered = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onEyeLongPressed?.invoke()
        }
    }

    // Paints
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb((floatingEyeAlpha * 255).toInt().coerceIn(0, 255), 18, 26, 38)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 230, 118) // Neon accent green
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun updateEyeSize(sizeDp: Int) {
        this.eyeSizeDp = sizeDp
        requestLayout()
        postInvalidate()
    }

    fun updateAlpha(alpha: Float) {
        this.floatingEyeAlpha = alpha.coerceIn(0.0f, 1.0f)
        postInvalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val sizePx = (eyeSizeDp * density).toInt()
        val minTouchPx = (80 * density).toInt()
        val dimension = max(sizePx, minTouchPx)
        setMeasuredDimension(dimension, dimension)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val visualRadius = (eyeSizeDp * density) / 2f - (3f * density)
        val alpha255 = (floatingEyeAlpha * 255).toInt().coerceIn(0, 255)

        // Draw shadow layer / ambient circle
        canvas.drawCircle(cx, cy + 2f * density, visualRadius, shadowPaint)

        // Draw main circle background with configured alpha
        bgPaint.color = Color.argb(alpha255, 18, 26, 38)
        canvas.drawCircle(cx, cy, visualRadius, bgPaint)

        // Draw neon border ring
        borderPaint.alpha = alpha255
        canvas.drawCircle(cx, cy, visualRadius, borderPaint)

        // Draw Eye Emoji / Icon sized proportionally
        eyePaint.alpha = alpha255
        eyePaint.textSize = visualRadius * 1.15f
        val fm = eyePaint.fontMetrics
        val textY = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText("👁", cx, textY, eyePaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                rawDownX = event.rawX
                rawDownY = event.rawY
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                isDragging = false
                isLongPressTriggered = false

                // Start Long Press timer (>600ms)
                mainHandler.removeCallbacks(longPressRunnable)
                mainHandler.postDelayed(longPressRunnable, 600L)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(activePointerId)
                if (idx < 0) return true
                val dx = (event.rawX - lastTouchX).toInt()
                val dy = (event.rawY - lastTouchY).toInt()
                val totalDx = event.rawX - rawDownX
                val totalDy = event.rawY - rawDownY

                if (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop) {
                    if (!isDragging) {
                        isDragging = true
                        mainHandler.removeCallbacks(longPressRunnable)
                    }
                }

                if (isDragging && !isLongPressTriggered) {
                    onDragListener(dx, dy)
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mainHandler.removeCallbacks(longPressRunnable)
                val rawUpX = event.rawX
                val rawUpY = event.rawY
                val distSq = (rawUpX - rawDownX) * (rawUpX - rawDownX) + (rawUpY - rawDownY) * (rawUpY - rawDownY)

                if (!isDragging && !isLongPressTriggered && distSq < (touchSlop * touchSlop)) {
                    // Single Tap detected -> Restore board
                    onClickListener()
                }
                isDragging = false
                isLongPressTriggered = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(longPressRunnable)
                isDragging = false
                isLongPressTriggered = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
