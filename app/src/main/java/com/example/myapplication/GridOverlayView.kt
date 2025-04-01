package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GridOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.WHITE
        alpha = 204  // 80% opacity (0.8 * 255 = 204)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        
        // 绘制垂直线
        canvas.drawLine(width / 3, 0f, width / 3, height, paint)
        canvas.drawLine(width * 2 / 3, 0f, width * 2 / 3, height, paint)
        
        // 绘制水平线
        canvas.drawLine(0f, height / 3, width, height / 3, paint)
        canvas.drawLine(0f, height * 2 / 3, width, height * 2 / 3, paint)
    }
} 