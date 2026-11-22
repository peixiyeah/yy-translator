package com.personal.translator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class SymbolView(context: Context, private val symbol: String, private val color: Int) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    override fun onDraw(c: Canvas) {
        c.save(); c.scale(width / 24f, height / 24f); p.color = color
        when (symbol) {
            "mic" -> {
                c.drawRoundRect(9f, 3f, 15f, 15f, 3f, 3f, p)
                c.drawArc(6f, 7f, 18f, 19f, 0f, 180f, false, p)
                c.drawLine(6f, 10f, 6f, 13f, p); c.drawLine(18f, 10f, 18f, 13f, p)
                c.drawLine(12f, 19f, 12f, 22f, p); c.drawLine(9f, 22f, 15f, 22f, p)
            }
            "speaker" -> {
                val path = Path().apply { moveTo(3f, 9f); lineTo(7f, 9f); lineTo(12f, 5f); lineTo(12f, 19f); lineTo(7f, 15f); lineTo(3f, 15f); close() }
                c.drawPath(path, p); c.drawArc(12f, 7f, 20f, 17f, -65f, 130f, false, p)
                c.drawArc(12f, 3f, 24f, 21f, -60f, 120f, false, p)
            }
            "keyboard" -> {
                c.drawRoundRect(2f, 5f, 22f, 19f, 2f, 2f, p)
                for (y in listOf(9f, 12f)) for (x in listOf(6f, 10f, 14f, 18f)) c.drawPoint(x, y, p)
                c.drawLine(7f, 16f, 17f, 16f, p)
            }
            "history" -> { c.drawCircle(12f, 12f, 9f, p); c.drawLine(12f, 6f, 12f, 12f, p); c.drawLine(12f, 12f, 16f, 14f, p) }
        }
        c.restore()
    }
}
