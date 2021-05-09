package moe.luke.shadow

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.NinePatch
import android.graphics.Rect
import android.graphics.drawable.NinePatchDrawable
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@SuppressLint("SetJavaScriptEnabled")
class ShadowFactory(context: Context) {

    private inner class PendingTask(
        private val continuation: Continuation<ShadowDrawable>,
        private val input: String
    ) {
        fun execute() {
            MainScope().launch {
                continuation.resume(createFromWebView(input))
            }
        }
    }

    private val webkit = WebView(context.applicationContext)
    private var needPendingTasks: ArrayList<PendingTask>? = ArrayList()

    init {
        webkit.settings.javaScriptEnabled = true
        webkit.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val tasks = needPendingTasks
                if (tasks != null) {
                    for (task in tasks) {
                        task.execute()
                    }
                    needPendingTasks = null
                }
            }
        }
        webkit.loadUrl("file:///android_asset/webkit_shadow_renderer/index.html")
    }

    private suspend fun createFromWebView(input: String): ShadowDrawable {
        val result = webkit.evaluateJavascript("createNinePatch('$input')")
        return withContext(Dispatchers.Default) {
            val json = JSONObject(result)
            if (json.has("error")) {
                throw UnsupportedOperationException(json.getString("error"))
            } else {
                val margin = json.getJSONArray("margin")
                val imageData = json.getString("imageData")
                val imageBuffer = Base64.decode(imageData, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBuffer, 0, imageBuffer.size)
                ShadowDrawable(
                    Rect(
                        margin.getInt(0),
                        margin.getInt(1),
                        margin.getInt(2),
                        margin.getInt(3)
                    ),
                    bitmap,
                    NinePatchDrawable(
                        webkit.resources,
                        NinePatch(bitmap, NinePatchChunk.create(bitmap).serializedChunk)
                    )
                )
            }
        }
    }

    /**
     * @throws UnsupportedOperationException
     * */
    suspend fun newDrawable(
        options: ShadowOptions
    ): ShadowDrawable {
        val json = JSONObject(
            ShadowOptions::class.java.declaredFields
                .filter {
                    !Modifier.isStatic(it.modifiers)
                }
                .map { it.name to it.apply { isAccessible = true }.get(options) }
                .toMap()
        ).toString()
        return withContext(Dispatchers.Main) {
            val tasks = needPendingTasks
            if (tasks == null) {
                createFromWebView(json)
            } else {
                suspendCoroutine {
                    tasks.add(PendingTask(it, json))
                }
            }
        }
    }
}