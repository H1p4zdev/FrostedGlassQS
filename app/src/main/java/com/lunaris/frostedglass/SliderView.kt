package com.lunaris.frostedglass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

class SliderView(
    context: Context,
    private var minVal: Int = 0,
    private var maxVal: Int = 100,
    private var currentVal: Int = 50,
    private val onChange: ((Int) -> Unit)? = null
) : View(context) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44FFFFFF.toInt()
        style = Paint.Style.FILL
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6C63FF.toInt()
        style = Paint.Style.FILL
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6C63FF.toInt()
        style = Paint.Style.FILL
    }

    private val thumbStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
    }

    private val density = context.resources.displayMetrics.density
    private val trackHeight = dp(4)
    private val thumbRadius = dp(10)
    private val hitPadding = dp(16)

    private var isDragging = false
    private var thumbX = 0f

    private val trackRect = RectF()

    init {
        val ratio = (currentVal - minVal).toFloat() / (maxVal - minVal).coerceAtLeast(1).toFloat()
        thumbX = ratio
        minimumHeight = dp(40)
    }

    fun setValue(value: Int) {
        currentVal = value.coerceIn(minVal, maxVal)
        val ratio = (currentVal - minVal).toFloat() / (maxVal - minVal).coerceAtLeast(1).toFloat()
        thumbX = ratio
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = thumbRadius + hitPadding
        trackRect.set(pad, (h - trackHeight) / 2f, (w - pad).toFloat(), (h + trackHeight) / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = thumbRadius + hitPadding
        val usable = w - pad * 2
        val thumbCenterX = pad + thumbX * usable

        // track background
        canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, trackPaint)

        // progress fill
        val progressRect = RectF(trackRect.left, trackRect.top, thumbCenterX, trackRect.bottom)
        canvas.drawRoundRect(progressRect, trackHeight / 2f, trackHeight / 2f, progressPaint)

        // thumb shadow (semi-transparent circle)
        canvas.drawCircle(thumbCenterX, h / 2f, thumbRadius + dp(2), Paint().apply {
            color = 0x33000000.toInt()
            isAntiAlias = true
        })

        // thumb
        canvas.drawCircle(thumbCenterX, h / 2f, thumbRadius, thumbPaint)
        canvas.drawCircle(thumbCenterX, h / 2f, thumbRadius, thumbStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val pad = thumbRadius + hitPadding
        val usable = w - pad * 2
        if (usable <= 0) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val thumbCenterX = pad + thumbX * usable
                val dx = event.x - thumbCenterX
                if (kotlin.math.abs(dx) <= thumbRadius + hitPadding) {
                    isDragging = true
                    parent.requestDisallowInterceptTouchEvent(true)
                    updateFromX(event.x, pad, usable)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    updateFromX(event.x, pad, usable)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    parent.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
        }
        return false
    }

    private fun updateFromX(x: Float, pad: Float, usable: Float) {
        thumbX = ((x - pad) / usable).coerceIn(0f, 1f)
        currentVal = (minVal + (thumbX * (maxVal - minVal)).toInt()).coerceIn(minVal, maxVal)
        onChange?.invoke(currentVal)
        invalidate()
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
