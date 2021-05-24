package org.gcta.shadow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gcta.shadow.*

class ShadowFactory private constructor(private val webkit: WebkitRenderer) {

    private suspend fun runTask(input: ShadowOptions): ShadowDrawable {
        return withContext(Dispatchers.Default) {
            val output = webkit.render(input)
            if (!output.error.isNullOrEmpty()) {
                throw UnsupportedOperationException(output.error)
            } else {
                val margin = output.margin!!.map { it.toInt() }
                val imageData = output.imageData!!
                val url = imageData.split(",")[1]
                val decode = Base64.decode(url, Base64.DEFAULT)
                val outer = BitmapFactory.decodeByteArray(decode, 0, decode.size)
                val chunk = NinePatchChunk.findPatches(outer)
                val inner = Bitmap.createBitmap(outer, 1, 1, outer.width - 2, outer.height - 2)
                outer.recycle()
                inner.prepareToDraw()
                ShadowDrawable(
                    Rect(
                        margin[0],
                        margin[1],
                        margin[2],
                        margin[3]
                    ),
                    inner,
                    chunk
                )
            }
        }
    }

    suspend fun newDrawable(options: ShadowOptions): ShadowDrawable {
        val input = options.copy()
        return runTask(input)
    }

    companion object {
        suspend fun create(context: Context): ShadowFactory {
            val application = context.applicationContext
            return withContext(Dispatchers.Main) {
                return@withContext ShadowFactory(WebkitRenderer(application))
            }
        }
    }

}