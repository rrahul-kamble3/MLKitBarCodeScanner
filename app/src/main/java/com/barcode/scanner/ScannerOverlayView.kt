package com.barcode.scanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class ScannerOverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val boxPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#80000000") // semi-transparent dark
    }

    private var rect: RectF? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = width * 0.15f
        val top = height * 0.3f
        val right = width * 0.85f
        val bottom = height * 0.7f

        rect = RectF(left, top, right, bottom)

        // Dark outside area
        canvas.drawRect(0f, 0f, width.toFloat(), top, backgroundPaint)
        canvas.drawRect(0f, bottom, width.toFloat(), height.toFloat(), backgroundPaint)
        canvas.drawRect(0f, top, left, bottom, backgroundPaint)
        canvas.drawRect(right, top, width.toFloat(), bottom, backgroundPaint)

        // Draw green box
        canvas.drawRect(rect!!, boxPaint)
    }

    fun getScanRect(): RectF? = rect

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        rect = RectF(
            width * 0.15f,
            height * 0.3f,
            width * 0.85f,
            height * 0.7f
        )
    }
}