package com.chessbeater.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.SeekBar

class ThumbOnlySeekBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = android.R.attr.seekBarStyle
) : SeekBar(context, attrs, defStyleAttr) {

    private var isDraggingThumb = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val currentThumb = thumb ?: return super.onTouchEvent(event)
        val touchX = event.x.toInt()
        val touchY = event.y.toInt()
        val slop = (24 * resources.displayMetrics.density).toInt() // Area sentuh nyaman thumb

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val hitRect = Rect(
                    currentThumb.bounds.left - slop,
                    0,
                    currentThumb.bounds.right + slop,
                    height
                )
                if (hitRect.contains(touchX, touchY)) {
                    isDraggingThumb = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return super.onTouchEvent(event)
                } else {
                    isDraggingThumb = false
                    return false // Abaikan tap batang slider, teruskan ke ScrollView!
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingThumb) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return super.onTouchEvent(event)
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingThumb = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return super.onTouchEvent(event)
            }
        }
        return super.onTouchEvent(event)
    }
}
