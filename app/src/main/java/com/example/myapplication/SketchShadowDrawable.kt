package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.NinePatchDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.ColorInt
import androidx.annotation.Keep
import androidx.annotation.MainThread
import androidx.annotation.Px
import org.json.JSONObject
import java.lang.reflect.Modifier
import java.util.concurrent.*
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

@Suppress("unused")
class SketchShadowDrawable private constructor(
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

    private companion object ViewCompat {

        private val FLAG_CLIP_TO_PADDING by lazy {
            ViewGroup::class.java.getDeclaredField("FLAG_CLIP_TO_PADDING")
                .apply {
                    isAccessible = true
                }.getInt(null)
        }

        private val mGroupFlagsField by lazy {
            ViewGroup::class.java.getDeclaredField("mGroupFlags")
                .apply {
                    isAccessible = true
                }
        }

        private val ViewGroup.mGroupFlags: Int
            get() {
                return mGroupFlagsField.getInt(this)
            }

        private fun ViewGroup.hasBooleanFlag(flag: Int): Boolean {
            return mGroupFlags and flag == flag
        }

        var View.clipToOutlineCompat: Boolean
            get() {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    this.clipToOutline
                } else {
                    false
                }
            }
            set(value) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    this.clipToOutline = value
                }
            }
        var ViewGroup.clipToPaddingCompat: Boolean
            get() {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    this.clipToPadding
                } else {
                    return hasBooleanFlag(FLAG_CLIP_TO_PADDING)
                }
            }
            set(value) {
                this.clipToPadding = value
            }
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

    @SuppressLint("SetJavaScriptEnabled")
    class Factory(context: Context) {
        private val webkit = WebView(context.applicationContext)
        private val mainHandler = Handler(Looper.getMainLooper())
        private val executor: Executor
        private var needPendingTasks: ArrayList<Pair<String, RequestCallback>>? = ArrayList()

        init {
            val nThread = min(4, max(2, Runtime.getRuntime().availableProcessors()))
            executor = ThreadPoolExecutor(
                0, nThread,
                60, TimeUnit.MILLISECONDS,
                LinkedBlockingQueue(),
                ThreadFactory {
                    thread(
                        priority = Process.THREAD_PRIORITY_DEFAULT,
                        block = { it.run() }
                    )
                }
            )
            webkit.settings.javaScriptEnabled = true
            webkit.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val tasks = needPendingTasks
                    if (tasks != null) {
                        tasks.forEach {
                            requestByWebView(it.first, it.second)
                        }
                        needPendingTasks = null
                    }
                }
            }
            webkit.loadUrl("file:///android_asset/webkit_shadow_renderer/index.html")
        }

        private fun createDrawable(
            margin: IntArray,
            imageData: ByteArray
        ): SketchShadowDrawable {
            val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
            return SketchShadowDrawable(
                Rect(
                    margin[0],
                    margin[1],
                    margin[2],
                    margin[3]
                ),
                bitmap,
                NinePatchDrawable(
                    webkit.resources,
                    NinePatch(bitmap, bitmap.ninePatchChunk)
                )
            )
        }

        private fun requestByWebView(json: String, callback: RequestCallback) {
            webkit.evaluateJavascript("createNinePatch('$json')") {
                executor.execute {
                    val result = JSONObject(it)
                    if (result.has("error")) {
                        val error = result.getString("error")
                        val exception = UnsupportedOperationException(error)
                        mainHandler.post { callback.onError(exception) }
                    } else {
                        val marginArray = result.getJSONArray("margin")
                        val imageData = result.getString("imageData")
                        val imageBytes = Base64.decode(imageData, Base64.DEFAULT)
                        val drawable = createDrawable((0..3).map { index ->
                            marginArray.getInt(index)
                        }.toIntArray(), imageBytes)
                        mainHandler.post { callback.onResponse(drawable) }
                    }
                }
            }
        }

        @MainThread
        fun request(
            options: RequestOptions,
            callback: RequestCallback
        ) {
            val json = JSONObject(
                RequestOptions::class.java.declaredFields
                    .map {
                        it
                    }.filter {
                        !Modifier.isStatic(it.modifiers)
                    }
                    .map { it.name to it.get(options) }
                    .toMap()
            ).toString()
            val tasks = needPendingTasks
            if (tasks == null) {
                requestByWebView(json, callback)
            } else {
                tasks.add(json to callback)
            }
        }
    }

    interface RequestCallback {
        fun onResponse(drawable: SketchShadowDrawable)

        fun onError(e: Throwable)
    }

    @Keep
    data class RequestOptions(
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
        var paddingLeft: Int = UNSET_PADDING,
        @Px
        var paddingRight: Int = UNSET_PADDING,
        @Px
        var paddingTop: Int = UNSET_PADDING,
        @Px
        var paddingBottom: Int = UNSET_PADDING
    ) : Cloneable {
        companion object {
            const val UNSET_PADDING = -1
        }
    }
}