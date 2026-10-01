package com.carlauncher.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ProgressLine @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var fraction = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var dragging = false; private set
    var onSeek: ((Float) -> Unit)? = null

    private val bgPaint = Paint().apply { color = Color.parseColor("#555555") }
    private val fgPaint = Paint().apply { color = Color.WHITE }

    override fun onDraw(canvas: Canvas) {
        val th = context.dp(4).toFloat()
        val cy = height / 2f
        canvas.drawRect(0f, cy - th / 2, width.toFloat(), cy + th / 2, bgPaint)
        canvas.drawRect(0f, cy - th / 2, width * fraction, cy + th / 2, fgPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                dragging = true
                fraction = e.x / width
            }
            MotionEvent.ACTION_UP -> {
                dragging = false
                fraction = e.x / width
                onSeek?.invoke(fraction)
            }
            MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }
}
