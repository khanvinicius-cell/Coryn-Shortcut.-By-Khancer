package com.khan.corynfloat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PathEffect
import android.graphics.DashPathEffect
import android.view.View

class DashedCircleView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5.5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(24f * resources.displayMetrics.density, 13f * resources.displayMetrics.density), 0f)
        setShadowLayer(7f * resources.displayMetrics.density, 0f, 1f * resources.displayMetrics.density, 0x66FFFFFF)
    }

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = paint.strokeWidth / 2f + 2f
        val diameter = minOf(width, height).toFloat() - inset * 2
        canvas.drawCircle(width / 2f, height / 2f, diameter / 2f, paint)
    }
}
