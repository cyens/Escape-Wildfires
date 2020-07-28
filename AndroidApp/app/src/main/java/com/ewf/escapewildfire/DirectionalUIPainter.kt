package com.ewf.escapewildfire

import android.graphics.*
import android.util.Log
import androidx.annotation.ColorInt

class DirectionalUIPainter (@ColorInt private val color: Int): Painter {

    override fun paint(canvas: Canvas) {
        //1
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        Log.d("dimensions","$width         $height")
//2
        val shapeBounds = RectF(0f, 0f, width,height)

        val navPath = Path().apply{
            moveTo(0f,0f)
            lineTo(width,0f)
            arcTo(shapeBounds,0f,90f,false)
            lineTo(0f,height)
            lineTo(0f,0f)
            close()
        }
//3
        val paint = Paint()
        paint.color = color
        Log.d("color", color.toString())
//4
        canvas.drawPath(navPath, paint)
        //canvas.drawRect(shapeBounds, Paint(Color.argb(255,0,0,0)))
    }

}