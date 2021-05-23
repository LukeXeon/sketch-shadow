package org.gcta.shadow

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.util.Base64
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@SuppressLint("SetJavaScriptEnabled")
internal class FactoryWebView(context: Context) : AppCompatJsWebView(context.applicationContext),
    ShadowFactory {

    private val pendingTasks = ArrayList<Pair<ShadowOptions, Continuation<ShadowDrawable>>>()
    private var isLoadFinished: Boolean = false

    init {
        settings.javaScriptEnabled = true
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                onLoadFinished()
            }
        }
        webChromeClient = WebChromeClient()
        setBackgroundColor(Color.TRANSPARENT)
        loadUrl("file:///android_asset/webkit_shadow_renderer/index.html")
    }

    override fun getTag(): Any {
        return ShadowFactory
    }

    private fun onLoadFinished() {
        if (!isLoadFinished) {
            isLoadFinished = true
            for ((input, continuation) in pendingTasks) {
                GlobalScope.launch(Dispatchers.Main) {
                    continuation.resume(runTask(input))
                }
            }
            pendingTasks.clear()
        }
    }

    private suspend fun runTask(input: ShadowOptions): ShadowDrawable {
        val output = withContext(Dispatchers.Main) {
            suspendCoroutine<String> { continuation ->
                evaluateJavascript("createNinePatch('${gson.toJson(input)}')") {
                    continuation.resume(it)
                }
            }
        }
        return withContext(Dispatchers.Default) {
            Log.d(TAG, "output=$output")
            val json = gson.fromJson(output, ShadowOutput::class.java)
            if (!json.error.isNullOrEmpty()) {
                throw UnsupportedOperationException(json.error)
            } else {
                val margin = json.margin!!.map { it.toInt() }
                val imageData = json.imageData!!
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

    override suspend fun newDrawable(options: ShadowOptions): ShadowDrawable {
        val input = options.copy()
        return withContext(Dispatchers.Main) {
            if (isLoadFinished) {
                runTask(input)
            } else {
                suspendCoroutine { pendingTasks.add(input to it) }
            }
        }
    }

    companion object {
        private val gson by lazy {
            Gson()
        }

        private const val TAG = "ShadowFactory"
    }

}