package org.gcta.shadow

import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.NinePatchDrawable
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RestrictTo
import java.io.*
import java.util.*
import kotlin.properties.Delegates

class ShadowDrawable
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
constructor() : Drawable(),
    Drawable.Callback,
    Parcelable,
    Externalizable {

    private var margin: Rect by Delegates.notNull()
    private var bitmap: Bitmap by Delegates.notNull()
    private var chunk: ByteArray by Delegates.notNull()
    private val ninePatchDrawable by lazy {
        NinePatchDrawable(Resources.getSystem(), bitmap, chunk, null, null).apply {
            isFilterBitmap = true
            paint.isAntiAlias = true
            callback = this@ShadowDrawable
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal constructor(
        margin: Rect,
        bitmap: Bitmap,
        chunk: ByteArray
    ) : this() {
        this.margin = margin
        this.bitmap = bitmap
        this.chunk = chunk
    }

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

    override fun writeExternal(out: ObjectOutput) {
        out.writeInt(margin.left)
        out.writeInt(margin.top)
        out.writeInt(margin.right)
        out.writeInt(margin.bottom)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val array = stream.toByteArray()
        out.writeInt(array.size)
        out.write(array)
        out.writeInt(chunk.size)
        out.write(chunk)
    }

    override fun readExternal(`in`: ObjectInput) {
        margin = Rect(
            `in`.readInt(),
            `in`.readInt(),
            `in`.readInt(),
            `in`.readInt()
        )
        var size = `in`.readInt()
        var array = ByteArray(size)
        `in`.readSafely(array)
        bitmap = BitmapFactory.decodeByteArray(array, 0, array.size)
        size = `in`.readInt()
        array = ByteArray(size)
        `in`.readSafely(array)
        chunk = array
    }

    companion object CREATOR : Parcelable.Creator<ShadowDrawable> {

        private const val serialVersionUID = 1L

        private fun ObjectInput.readSafely(buffer: ByteArray): Int {
            val length = buffer.size
            var count = 0
            while (count != length) {
                val r = read(buffer, count, length - count)
                if (r == -1) {
                    return count
                } else {
                    count += r
                }
            }
            return length
        }

        override fun createFromParcel(parcel: Parcel): ShadowDrawable {
            val margin = parcel.readParcelable<Rect>(Rect::class.java.classLoader)!!
            val bitmap = parcel.readParcelable<Bitmap>(Bitmap::class.java.classLoader)!!
            val chunk = parcel.createByteArray()!!
            return ShadowDrawable(margin, bitmap, chunk)
        }

        override fun newArray(size: Int): Array<ShadowDrawable?> {
            return arrayOfNulls(size)
        }
    }

}