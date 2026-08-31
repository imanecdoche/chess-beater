package com.chessbeater.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.SeekBar
import kotlin.math.abs

class TapOnReleaseSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.seekBarStyle
) : SeekBar(context, attrs, defStyleAttr) {

    private var downX = 0f
    private var downY = 0f
    private var isDraggingSlider = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                isDraggingSlider = false
                // Jangan langsung ubah progress saat jari baru menempel (mencegah jump saat scroll)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs(x - downX)
                val dy = abs(y - downY)

                if (!isDraggingSlider) {
                    if (dy > touchSlop && dy > dx) {
                        // Gesture vertikal -> serahkan event ke ScrollView
                        parent?.requestDisallowInterceptTouchEvent(false)
                        return false
                    } else if (dx > touchSlop && dx > dy) {
                        // Gesture horizontal -> kunci ke slider
                        isDraggingSlider = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }

                if (isDraggingSlider) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    updateProgressFromTouch(x)
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                val dx = abs(x - downX)
                val dy = abs(y - downY)
                val isTap = !isDraggingSlider && (dx < touchSlop && dy < touchSlop)

                if (isDraggingSlider) {
                    updateProgressFromTouch(x)
                } else if (isTap) {
                    // Commit on Release: Update progress HANYA saat jari diangkat setelah tap
                    updateProgressFromTouch(x)
                }

                isDraggingSlider = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isDraggingSlider = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateProgressFromTouch(touchX: Float) {
        val availableWidth = width - paddingLeft - paddingRight
        if (availableWidth <= 0) return

        val clampedX = (touchX - paddingLeft).coerceIn(0f, availableWidth.toFloat())
        val progressRatio = clampedX / availableWidth.toFloat()
        val newProgress = (progressRatio * max).toInt().coerceIn(0, max)

        if (progress != newProgress) {
            progress = newProgress
        }
    }
}
