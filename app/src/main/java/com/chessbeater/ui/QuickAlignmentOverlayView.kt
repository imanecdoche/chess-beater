package com.chessbeater.ui

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

class QuickAlignmentOverlayView(
    context: Context,
    private val initialBounds: RectF,
    private val onLockedCallback: (RectF) -> Unit
) : View(context) {

    private val currentRect = RectF(initialBounds)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        color = Color.parseColor("#00E5FF") // Neon Cyan
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
        color = Color.parseColor("#8000E5FF") // Cyan Semi-Transparan
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 2f, Color.BLACK)
    }

    private var downX = 0f
    private var downY = 0f
    private var isResizing = false
    private var isDragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handleSize = 36f * resources.displayMetrics.density

    private val holdHandler = Handler(Looper.getMainLooper())
    private val holdRunnable = Runnable {
        triggerLockPosition()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Gambar Garis Tepi Luar (Outer Box)
        canvas.drawRoundRect(currentRect, 8f, 8f, borderPaint)

        // 2. Gambar 64 Kotak Grid Dalam (8x8)
        val stepX = currentRect.width() / 8f
        val stepY = currentRect.height() / 8f
        for (i in 1..7) {
            // Garis Vertikal
            canvas.drawLine(
                currentRect.left + (i * stepX), currentRect.top,
                currentRect.left + (i * stepX), currentRect.bottom,
                gridPaint
            )
            // Garis Horizontal
            canvas.drawLine(
                currentRect.left, currentRect.top + (i * stepY),
                currentRect.right, currentRect.top + (i * stepY),
                gridPaint
            )
        }

        // 3. Gambar Handle Resize di Pojok Kanan-Bawah
        borderPaint.style = Paint.Style.FILL
        canvas.drawCircle(currentRect.right, currentRect.bottom, 12f * resources.displayMetrics.density, borderPaint)
        borderPaint.style = Paint.Style.STROKE

        // 4. Petunjuk teks singkat di tengah
        val textY = (currentRect.top - 14f * resources.displayMetrics.density).coerceAtLeast(40f * resources.displayMetrics.density)
        canvas.drawText(
            "📐 Geser untuk paskan • Tahan 2 dtk untuk mengunci",
            currentRect.centerX(),
            textY,
            textPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                isDragging = false
                isResizing = false

                // Cek apakah menyentuh pojok kanan bawah untuk resize
                if (abs(x - currentRect.right) < handleSize && abs(y - currentRect.bottom) < handleSize) {
                    isResizing = true
                } else if (currentRect.contains(x, y)) {
                    isDragging = true
                    // Mulai timer hold 2 detik
                    holdHandler.postDelayed(holdRunnable, 2000L)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = x - downX
                val dy = y - downY

                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    // Batalkan hold timer jika jari digeser aktif
                    holdHandler.removeCallbacks(holdRunnable)

                    if (isResizing) {
                        val delta = maxOf(dx, dy)
                        val newSize = (currentRect.width() + delta).coerceIn(200f, width.toFloat())
                        currentRect.right = currentRect.left + newSize
                        currentRect.bottom = currentRect.top + newSize
                        downX = x
                        downY = y
                        invalidate()
                    } else if (isDragging) {
                        currentRect.offset(dx, dy)
                        downX = x
                        downY = y
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                holdHandler.removeCallbacks(holdRunnable)
                isDragging = false
                isResizing = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun triggerLockPosition() {
        holdHandler.removeCallbacks(holdRunnable)
        isEnabled = false
        visibility = GONE

        // Getar konfirmasi (Haptic feedback)
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(70L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(70L)
            }
        } catch (ignored: Exception) {}

        // Callback simpan & tutup
        onLockedCallback(currentRect)
    }
}
