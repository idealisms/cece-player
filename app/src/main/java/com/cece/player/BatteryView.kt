package com.cece.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class BatteryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var level: Int = 100
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    private val strokeWidth = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 1.5f, resources.displayMetrics
    )

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@BatteryView.strokeWidth
        color = Color.parseColor("#888888")
    }
    private val nubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#888888")
    }
    private val textPaintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        textAlign = Paint.Align.CENTER
    }
    private val textPaintDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A2E")
        textAlign = Paint.Align.CENTER
    }

    private val bodyRect = RectF()
    private val fillRect = RectF()
    private val nubRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val half = strokeWidth / 2f

        // Nub: ~15% of width, ~40% of height, centered vertically on right side
        val nubW = w * 0.12f
        val nubH = h * 0.4f
        val nubTop = (h - nubH) / 2f
        nubRect.set(w - nubW, nubTop, w, nubTop + nubH)

        // Body: remaining width
        bodyRect.set(half, half, w - nubW - half, h - half)

        // Fill rect: inner area of body scaled by level
        val innerLeft = bodyRect.left + strokeWidth
        val innerTop = bodyRect.top + strokeWidth
        val innerRight = bodyRect.right - strokeWidth
        val innerBottom = bodyRect.bottom - strokeWidth
        val innerW = innerRight - innerLeft
        val fillRight = innerLeft + innerW * (level / 100f)
        fillRect.set(innerLeft, innerTop, fillRight, innerBottom)

        // Fill color
        fillPaint.color = if (level > 20) Color.parseColor("#888888") else Color.parseColor("#FF4F00")

        // Draw fill
        canvas.drawRect(fillRect, fillPaint)

        // Draw body outline
        canvas.drawRect(bodyRect, outlinePaint)

        // Draw nub
        canvas.drawRect(nubRect, nubPaint)

        // Text: percentage centered in body
        val textStr = "$level%"
        val bodyMidX = (bodyRect.left + bodyRect.right) / 2f
        val textSize = h * 0.55f
        textPaintWhite.textSize = textSize
        textPaintDark.textSize = textSize

        val textY = (h - textPaintWhite.descent() - textPaintWhite.ascent()) / 2f

        // Draw white text everywhere first
        canvas.drawText(textStr, bodyMidX, textY, textPaintWhite)

        // Clip to fill rect and draw dark text on top (inverted effect)
        canvas.save()
        canvas.clipRect(fillRect)
        canvas.drawText(textStr, bodyMidX, textY, textPaintDark)
        canvas.restore()
    }
}
