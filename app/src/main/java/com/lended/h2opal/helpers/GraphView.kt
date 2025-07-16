package com.lended.h2opal.helpers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val graphPaint = Paint().apply {
        color = Color.parseColor("#3E5BA9") // Graph color
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val pointPaint = Paint().apply {
        color = Color.parseColor("#3E5BA9")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Sample hydration data
    private val hydrationData = listOf(20, 40, 35, 60, 55, 75, 65)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val widthPerPoint = width.toFloat() / (hydrationData.size - 1)
        val maxVal = hydrationData.maxOrNull() ?: 100

        for (i in 0 until hydrationData.size - 1) {
            val x1 = i * widthPerPoint
            val y1 = height - (hydrationData[i].toFloat() / maxVal * height)

            val x2 = (i + 1) * widthPerPoint
            val y2 = height - (hydrationData[i + 1].toFloat() / maxVal * height)

            canvas.drawLine(x1, y1, x2, y2, graphPaint)
            canvas.drawCircle(x1, y1, 8f, pointPaint)
        }

        // Draw last point
        val lastX = (hydrationData.size - 1) * widthPerPoint
        val lastY = height - (hydrationData.last().toFloat() / maxVal * height)
        canvas.drawCircle(lastX, lastY, 8f, pointPaint)
    }
}
