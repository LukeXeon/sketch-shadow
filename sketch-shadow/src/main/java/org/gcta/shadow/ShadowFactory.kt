package org.gcta.shadow

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Base64
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.MainThread
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@SuppressLint("SetJavaScriptEnabled")
class ShadowFactory
@MainThread
private constructor(context: Context) {

    private inner class TaskManager : WebViewClient() {
        private val pendingTasks = ArrayList<Pair<ShadowOptions, Continuation<ShadowDrawable>>>()
        private var isLoadFinished: Boolean = false

        override fun onPageFinished(view: WebView?, url: String?) {
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
                    webkit.evaluateJavascript("createNinePatch('${gson.toJson(input)}')") {
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
                    val inner = Bitmap.createBitmap(outer, 1, 1, outer.width - 2, outer.width - 2)
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

        suspend fun scheduleTask(input: ShadowOptions): ShadowDrawable {
            return withContext(Dispatchers.Main) {
                if (isLoadFinished) {
                    runTask(input)
                } else {
                    suspendCoroutine { pendingTasks.add(input to it) }
                }
            }
        }
    }

    private val taskManager = TaskManager()
    private val webkit = AppCompatJsWebView(context.applicationContext)

    init {
        webkit.settings.javaScriptEnabled = true
        webkit.webViewClient = taskManager
        webkit.webChromeClient = WebChromeClient()
        webkit.loadUrl("file:///android_asset/webkit_shadow_renderer/index.html")
    }

    companion object {
        suspend fun create(context: Context): ShadowFactory {
            return withContext(Dispatchers.Main) {
                ShadowFactory(context)
            }
        }

        private val gson by lazy {
            Gson()
        }

        private const val TAG = "ShadowFactory"
    }

    suspend fun newDrawable(
        options: ShadowOptions
    ): ShadowDrawable {
        return taskManager.scheduleTask(options.copy())
    }
}