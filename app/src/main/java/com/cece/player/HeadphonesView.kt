package com.cece.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class HeadphonesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var batteryLevel: Int = -1
        set(value) { field = value; invalidate() }

    private val grey = Color.parseColor("#888888")
    private val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics)
    private val headbandStroke = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = grey
    }
    private val headbandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = headbandStroke
        color = grey
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = grey
        textAlign = Paint.Align.CENTER
    }

    private val bodyRect = RectF()
    private val nubRect = RectF()
    private val arcOval = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // All proportions derived from h so the geometry scales cleanly:
        //   gap ≈ 2.5 * textSize (so "100%" barely fits)
        //   arcH ≈ arcW/2 (so the headband looks like a semicircle)
        val earBodyTop = h * 0.34f
        val earBodyH   = h * 0.60f
        val earBodyBot = earBodyTop + earBodyH
        val earBodyW   = h * 0.23f
        val earNubW    = earBodyW * 0.40f
        val earNubH    = earBodyH * 0.42f
        val earCorner  = earBodyW * 0.35f
        val nubTop     = earBodyTop + (earBodyH - earNubH) / 2f
        val nubBot     = nubTop + earNubH

        // Left ear cup body + outer nub
        bodyRect.set(margin + earNubW, earBodyTop, margin + earNubW + earBodyW, earBodyBot)
        canvas.drawRoundRect(bodyRect, earCorner, earCorner, fillPaint)
        nubRect.set(margin, nubTop, margin + earNubW, nubBot)
        canvas.drawRect(nubRect, fillPaint)

        // Right ear cup body + outer nub
        bodyRect.set(w - margin - earNubW - earBodyW, earBodyTop, w - margin - earNubW, earBodyBot)
        canvas.drawRoundRect(bodyRect, earCorner, earCorner, fillPaint)
        nubRect.set(w - margin - earNubW, nubTop, w - margin, nubBot)
        canvas.drawRect(nubRect, fillPaint)

        // Headband: semi-ellipse whose oval is nearly square → near-perfect semicircle
        val arcLeft = margin + earNubW + earBodyW
        val arcRight = w - margin - earNubW - earBodyW
        val arcH = earBodyTop * 0.92f   // ≈ arcW/2 given the chosen view dimensions
        arcOval.set(arcLeft, earBodyTop - arcH, arcRight, earBodyTop + arcH)
        // 0° = right endpoint, sweep -180° counter-clockwise → peak at top → left endpoint
        canvas.drawArc(arcOval, 0f, -180f, false, headbandPaint)

        // Percentage text — sized so "100%" barely spans the gap between ear cups
        if (batteryLevel >= 0) {
            textPaint.textSize = earBodyH * 0.40f
            val textY = earBodyTop + earBodyH / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText("$batteryLevel%", w / 2f, textY, textPaint)
        }
    }
}
