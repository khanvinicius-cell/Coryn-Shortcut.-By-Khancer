package com.khan.corynfloat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View

// Ícones desenhados em código (não dependem de fontes do aparelho).
class GlyphView(context: Context, initialKind: Kind) : View(context) {
    enum class Kind { MINIMIZE, EXPAND, RESTORE, CLOSE, TABS }

    var kind: Kind = initialKind
        set(value) { field = value; invalidate() }
    var count: Int = 1
        set(value) { field = value; invalidate() }
    var color: Int = 0xFF263238.toInt()
        set(value) { field = value; invalidate() }

    private val d = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = 11f * d
    }

    init { isClickable = true }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val s = 7f * d
        val o = 3f * d
        line.color = color
        when (kind) {
            Kind.MINIMIZE -> canvas.drawLine(cx - s, cy + s * 0.5f, cx + s, cy + s * 0.5f, line)
            Kind.EXPAND -> canvas.drawRoundRect(cx - s, cy - s, cx + s, cy + s, 2f * d, 2f * d, line)
            Kind.RESTORE -> {
                val back = Path().apply {
                    moveTo(cx - s + o, cy - s + o)
                    lineTo(cx - s + o, cy - s)
                    lineTo(cx + s, cy - s)
                    lineTo(cx + s, cy + s - o)
                    lineTo(cx + s - o, cy + s - o)
                }
                canvas.drawPath(back, line)
                canvas.drawRoundRect(cx - s, cy - s + o, cx + s - o, cy + s, 2f * d, 2f * d, line)
            }
            Kind.CLOSE -> {
                val k = s * 0.9f
                canvas.drawLine(cx - k, cy - k, cx + k, cy + k, line)
                canvas.drawLine(cx - k, cy + k, cx + k, cy - k, line)
            }
            Kind.TABS -> {
                val k = s * 1.2f
                canvas.drawRoundRect(cx - k, cy - k, cx + k, cy + k, 4f * d, 4f * d, line)
                text.color = color
                canvas.drawText(count.toString(), cx, cy - (text.ascent() + text.descent()) / 2f, text)
            }
        }
    }
}
