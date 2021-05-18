package moe.luke.shadow

import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.NinePatchDrawable
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup

class ShadowDrawable internal constructor(
    private val margin: Rect,
    private val bitmap: Bitmap,
    private val chunk: ByteArray,
    private val ninePatchDrawable: NinePatchDrawable,
) : Drawable(), Drawable.Callback, Parcelable {

    override fun draw(canvas: Canvas) {
        var callback = callback
        while (callback is Drawable) {
            callback = callback.callback
        }
        if (callback is View) {
            val parent = callback.parent
            if (parent is ViewGroup) {
                if (parent.clipChildrenCompat
                    || parent.clipToPaddingCompat
                    || parent.clipToOutlineCompat
                ) {
                    parent.clipChildrenCompat = false
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            ninePatchDrawable.alpha
        } else {
            ninePatchDrawable.paint.alpha
        }
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

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(margin, flags)
        parcel.writeParcelable(bitmap, flags)
        parcel.writeByteArray(chunk)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ShadowDrawable> {
        override fun createFromParcel(parcel: Parcel): ShadowDrawable {
            val margin = requireNotNull(parcel.readParcelable<Rect>(Rect::class.java.classLoader))
            val bitmap = requireNotNull(parcel.readParcelable<Bitmap>(Bitmap::class.java.classLoader))
            val chunk = requireNotNull(parcel.createByteArray())
            return ShadowDrawable(
                margin, bitmap, chunk,
                NinePatchDrawable(Resources.getSystem(), bitmap, chunk, null, null)
            )
        }

        override fun newArray(size: Int): Array<ShadowDrawable?> {
            return arrayOfNulls(size)
        }
    }

}