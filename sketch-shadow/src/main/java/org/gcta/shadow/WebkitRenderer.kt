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
) : AppCompatJsWebView(context, attrs, defStyleAttr) {

    init {
        layoutParams = LayoutParams(0, 0)
        visibility = View.GONE
        setBackgroundColor(Color.TRANSPARENT)
        loadUrl("file:///android_asset/webkit_shadow_renderer/index.html")
        val loadMonitor = LoadMonitor()
        webViewClient = loadMonitor
        tag = loadMonitor
        addOnAttachStateChangeListener(loadMonitor)
        reattach()
    }

    private suspend fun ensureLoaded() {
        withContext(Dispatchers.Main) {
            val monitor = tag
            if (monitor is LoadMonitor) {
                monitor.waitLoaded()
            }
        }
    }

    suspend fun render(input: ShadowOptions): ShadowOutput {
        ensureLoaded()
        val output = withContext(Dispatchers.Main) {
            suspendCoroutine<String> { continuation ->
                evaluateJavascript("createNinePatch('${gson.toJson(input)}')") {
                    continuation.resume(it)
                }
            }
        }
        Log.d(TAG, "output=$output")
        return gson.fromJson(output, ShadowOutput::class.java)
    }

    private fun reattach() {
        // 为了兼容Google的傻逼bug↓
        // https://github.com/jakub-g/webview-bug-onPageFinished-sometimes-not-called
        // 所以必须将其放置到窗口中
        val toast = Toast(context.applicationContext)
        toast.duration = Toast.LENGTH_LONG
        toast.view = this
        toast.show()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(0, 0)
    }

    private inner class LoadMonitor : WebViewClient(), OnAttachStateChangeListener {

        private val waitList = ArrayList<Continuation<Unit>>()

        suspend fun waitLoaded() {
            suspendCoroutine<Unit> { waitList.add(it) }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            webViewClient = WebViewClient()
            tag = null
            removeOnAttachStateChangeListener(this)
            waitList.forEach { it.resume(Unit) }
        }

        override fun onViewAttachedToWindow(v: View?) {

        }

        override fun onViewDetachedFromWindow(v: View?) {
            val monitor = tag
            if (monitor is LoadMonitor) {
                reattach()
            }
        }
    }

    companion object {
        private val gson by lazy { Gson() }

        private const val TAG = "WebkitRenderer"
    }

}