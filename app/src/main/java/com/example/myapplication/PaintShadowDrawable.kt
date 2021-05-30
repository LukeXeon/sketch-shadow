package com.example.myapplication

import android.graphics.*
import android.graphics.drawable.Drawable


class PaintShadowDrawable : Drawable() {

    private val paint = Paint()
    var radius: Float = 0f
    var shadow: Float = 0f

    init {
        paint.isFilterBitmap = true
        paint.isAntiAlias = true
        paint.isDither = true
    }

    override fun draw(canvas: Canvas) {
        paint.color = Color.WHITE
        paint.setShadowLayer(shadow, 0f, 0f, Color.parseColor("#757575"))
        canvas.drawRoundRect(RectF(bounds), radius, radius, paint)
    }

    override fun setAlpha(alpha: Int) {
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
    }

    override fun getOpacity(): Int = PixelFormat.OPAQUE
}