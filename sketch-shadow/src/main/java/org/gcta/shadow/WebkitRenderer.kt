package org.gcta.shadow

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


internal class WebkitRenderer(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatJsWebView(context.applicationContext, attrs, defStyleAttr) {

    init {
        layoutParams = LayoutParams(0, 0)
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

    suspend fun render(input: ShadowOptions): ShadowOutput {
        val output = withContext(Dispatchers.Main) {
            ensureLoaded()
            suspendCoroutine<String> { continuation ->
                evaluateJavascript("createNinePatch('${gson.toJson(input)}')") {
                    continuation.resume(it)
                }
            }
        }
        Log.d(TAG, "output=$output")
        return gson.fromJson(output, ShadowOutput::class.java)
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

        private const val TAG = "WebkitRenderer"
    }

}