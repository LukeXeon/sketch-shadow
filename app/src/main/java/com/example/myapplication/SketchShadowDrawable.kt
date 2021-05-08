package com.example.myapplication

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.NinePatchDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.*
import org.json.JSONObject
import java.lang.reflect.Modifier
import java.util.concurrent.*
import kotlin.math.max
import kotlin.math.min

@Suppress("unused")
class SketchShadowDrawable private constructor(
    margin: Rect,
    @Suppress("unused") val memorySize: Int,
    private val ninePatchDrawable: NinePatchDrawable,
) : Drawable(), Drawable.Callback {

    @Suppress("MemberVisibilityCanBePrivate")
    val margin: Rect = margin
        get() = Rect(field)

    override fun draw(canvas: Canvas) {
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

    override fun onBoundsChange(bounds: Rect?) {
        super.onBoundsChange(bounds)
        var callback = callback
        while (callback is Drawable) {
            callback = callback.callback
        }
        if (callback is View) {
            val parent = callback.parent
            if (parent is ViewGroup) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    parent.clipToOutline = false
                }
                parent.clipChildren = false
                parent.clipToPadding = false
            }
        }
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

    class Factory(context: Context) {
        private val webkit = WebView(context.applicationContext)
        private val mainHandler = Handler(Looper.getMainLooper())
        private val executor: Executor
        private val pendingActions = ArrayList<Pair<String, Callback>>()
        private var isLoadFinished = false

        init {
            val nThread = min(4, max(2, Runtime.getRuntime().availableProcessors()))
            executor = ThreadPoolExecutor(
                0, nThread,
                60, TimeUnit.MILLISECONDS,
                LinkedBlockingQueue()
            )
            webkit.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoadFinished = true
                    pendingActions.forEach {
                        createDrawableAsync0(it.first, it.second)
                    }
                    pendingActions.clear()
                }
            }
            webkit.loadUrl("file:///android_asset/webkit_shadow_renderer/index.html")
        }

        private fun createDrawable(
            margin: IntArray,
            imageData: ByteArray
        ): SketchShadowDrawable {
            val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
            val memorySize = bitmap.width * bitmap.height * Int.SIZE_BYTES
            return SketchShadowDrawable(
                Rect(
                    margin[0],
                    margin[1],
                    margin[2],
                    margin[3]
                ),
                memorySize,
                NinePatchDrawable(
                    webkit.resources,
                    NinePatch(bitmap, bitmap.ninePatchChunk)
                )
            )
        }

        private fun createDrawableAsync0(json: String, callback: Callback) {
            webkit.evaluateJavascript("createNinePatch('$json')") {
                executor.execute {
                    val result = JSONObject(it)
                    val marginArray = result.getJSONArray("margin")
                    val imageData = result.getString("imageData")
                    val imageBytes = Base64.decode(imageData, Base64.DEFAULT)
                    val drawable = createDrawable((0..3).map { index ->
                        marginArray.getInt(index)
                    }.toIntArray(), imageBytes)
                    mainHandler.post { callback.onCreated(drawable) }
                }
            }
        }

        @MainThread
        fun createDrawableAsync(
            options: Options,
            callback: Callback
        ) {
            val json = JSONObject(
                Options::class.java.declaredFields
                    .map {
                        it
                    }.filter {
                        !Modifier.isStatic(it.modifiers)
                    }
                    .map { it.name to it.get(options) }
                    .toMap()
            ).toString()
            if (isLoadFinished) {
                createDrawableAsync0(json, callback)
            } else {
                pendingActions.add(json to callback)
            }
        }

        interface Callback {
            fun onCreated(drawable: SketchShadowDrawable)
        }

        @Keep
        data class Options(
            @Px
            var roundLeftTop: Int = 12,
            @Px
            var roundRightTop: Int = 12,
            @Px
            var roundLeftBottom: Int = 12,
            @Px
            var roundRightBottom: Int = 12,
            @Px
            var shadowBlur: Int = 20,
            @ColorInt
            var shadowColor: Int = Color.parseColor("#757575"),
            @Px
            var shadowDx: Int = 0,
            @Px
            var shadowDy: Int = 0,
            @ColorInt
            var backgroundFillColor: Int = Color.TRANSPARENT,
            @ColorInt
            var fillColor: Int = Color.TRANSPARENT,
            @ColorInt
            var outlineColor: Int = Color.TRANSPARENT,
            @Px
            var outlineWidth: Int = 0,
            @Px
            var width: Int = 200,
            @Px
            var height: Int = 200,
            @Px
            var paddingLeft: Int = UNSET_PADDING,
            @Px
            var paddingRight: Int = UNSET_PADDING,
            @Px
            var paddingTop: Int = UNSET_PADDING,
            @Px
            var paddingBottom: Int = UNSET_PADDING,
            @FloatRange(from = 0.0, to = 1.0)
            var contentRightBegin: Float = 0f,
            @FloatRange(from = 0.0, to = 1.0)
            var contentRightEnd: Float = 0f,
            @FloatRange(from = 0.0, to = 1.0)
            var contentBottomBegin: Float = 0f,
            @FloatRange(from = 0.0, to = 1.0)
            var contentBottomEnd: Float = 0f,
        ) : Cloneable {
            companion object {
                const val UNSET_PADDING = -1
            }
        }
    }

}