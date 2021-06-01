package org.gcta.shadow

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@SuppressLint("SetJavaScriptEnabled")
internal class WebkitRenderer private constructor(
    context: Context
) : AppCompatWebView(context.applicationContext), ShadowFactory {

    init {
        settings.javaScriptEnabled = true
        visibility = View.GONE
        setBackgroundColor(Color.TRANSPARENT)
        loadUrl("file:///android_asset/webkit_shadow_renderer/index.html")
        val compat = CompatCallbacks()
        webViewClient = compat
        tag = compat
        addOnAttachStateChangeListener(compat)
        compat.attachToWindow(this)
    }

    private suspend fun ensureLoaded() {
        val compat = tag
        if (compat is CompatCallbacks) {
            compat.waitLoaded()
        }
    }

    override suspend fun newDrawable(options: ShadowOptions): ShadowDrawable {
        return withContext(Dispatchers.Default) {
            ensureLoaded()
            val outputJson = withContext(Dispatchers.Main) {
                suspendCoroutine<String?> { continuation ->
                    evaluateJavascript("createNinePatch('${gson.toJson(options)}')") {
                        continuation.resume(it)
                    }
                } ?: throw UnsupportedOperationException()
            }
            val output = gson.fromJson(outputJson, ShadowOutput::class.java)
            Log.d(TAG, "output=$output")
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(0, 0)
    }

    private class CompatCallbacks : WebViewClient(), OnAttachStateChangeListener {

        private val waitList = ArrayList<Continuation<Unit>>()

        suspend fun waitLoaded() {
            suspendCoroutine<Unit> { waitList.add(it) }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            view.webViewClient = WebViewClient()
            view.tag = null
            view.removeOnAttachStateChangeListener(this)
            waitList.forEach { it.resume(Unit) }
        }

        override fun onViewAttachedToWindow(v: View) {
            v.rootView.apply {
                visibility = View.GONE
                alpha = 0f
                setBackgroundColor(Color.TRANSPARENT)
            }
        }

        override fun onViewDetachedFromWindow(v: View) {
            val compat = v.tag
            if (compat is CompatCallbacks) {
                attachToWindow(v)
            }
        }

        fun attachToWindow(v: View) {
            // 为了兼容Google的傻逼bug↓
            // https://github.com/jakub-g/webview-bug-onPageFinished-sometimes-not-called
            // 所以必须将其放置到窗口中
            val toast = Toast(v.context)
            toast.duration = Toast.LENGTH_SHORT
            toast.view = v
            toast.show()
        }
    }

    companion object {

        private val gson by lazy { Gson() }

        suspend fun create(context: Context): WebkitRenderer {
            return withContext(Dispatchers.Main) {
                WebkitRenderer(context)
            }
        }

        private const val TAG = "WebkitRenderer"
    }

}