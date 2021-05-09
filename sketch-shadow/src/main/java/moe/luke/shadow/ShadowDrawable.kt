package moe.luke.shadow

import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.NinePatchDrawable
import android.view.View
import android.view.ViewGroup

class ShadowDrawable internal constructor(
    margin: Rect,
    @Suppress("unused") val bitmap: Bitmap,
    private val ninePatchDrawable: NinePatchDrawable,
) : Drawable(), Drawable.Callback {

    @Suppress("MemberVisibilityCanBePrivate")
    val margin: Rect = margin
        get() = Rect(field)

    override fun draw(canvas: Canvas) {
        var callback = callback
        while (callback is Drawable) {
            callback = callback.callback
        }
        if (callback is View) {
            val parent = callback.parent
            if (parent is ViewGroup) {
                if (parent.clipChildren || parent.clipToOutlineCompat || parent.clipToOutlineCompat) {
                    parent.clipChildren = false
                    parent.clipToPaddingCompat = false
                    parent.clipToOutlineCompat = false
                    invalidateSelf()
                    return
                }
            }
        }
        ninePatchDrawable.draw(canvas)
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(
            left - margin.left,
            top - margin.top,
            right + margin.right,
            bottom + margin.bottom
        )
        ninePatchDrawable.setBounds(
            left - margin.left,
            top - margin.top,
            right + margin.right,
            bottom + margin.bottom
        )
    }

    override fun getAlpha(): Int {
        return ninePatchDrawable.alpha
    }

    override fun setAlpha(alpha: Int) {
        ninePatchDrawable.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        ninePatchDrawable.colorFilter = colorFilter
    }

    override fun getOpacity(): Int {
        return ninePatchDrawable.opacity
    }

    override fun invalidateDrawable(who: Drawable) {
        callback?.invalidateDrawable(who)
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        callback?.scheduleDrawable(who, what, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        callback?.unscheduleDrawable(who, what)
    }

}