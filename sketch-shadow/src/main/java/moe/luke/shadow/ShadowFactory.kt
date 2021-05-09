package moe.luke.shadow

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.drawable.NinePatchDrawable
import android.os.Build
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import androidx.annotation.WorkerThread
import kotlinx.coroutines.*
import org.json.JSONObject
import java.lang.reflect.Modifier
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "AddJavascriptInterface")
class ShadowFactory(context: Context) {

    private data class PendingTask(
        val input: JSONObject,
        val continuation: Continuation<ShadowDrawable>
    )

    private inner class EventHandler : WebViewClient() {

        override fun onPageFinished(view: WebView?, url: String?) {
            val paddingTasks = needPendingTasks ?: return
            for ((input, continuation) in paddingTasks) {
                GlobalScope.launch { continuation.resume(scheduleTask(input)) }
            }
            needPendingTasks = null
        }

        @Keep
        @WorkerThread
        @JavascriptInterface
        fun onResponse(output: String, id: String) {
            GlobalScope.launch(Dispatchers.Main) {
                val continuation = tasks.remove(id) ?: return@launch
                completeTask(output, continuation)
            }
        }
    }

    private val webkit = WebView(context.applicationContext)
    private val tasks = HashMap<String, Continuation<ShadowDrawable>>()
    private var needPendingTasks: ArrayList<PendingTask>? = ArrayList()

    init {
        val handler = EventHandler()
        webkit.settings.javaScriptEnabled = true
        webkit.webViewClient = handler
        webkit.addJavascriptInterface(handler, "__handler__")
        webkit.loadUrl("file:///android_asset/webkit_shadow_renderer/index.html")
    }

    private suspend fun completeTask(
        response: String,
        continuation: Continuation<ShadowDrawable>
    ) {
        withContext(Dispatchers.Default) {
            val json = JSONObject(response)
            if (json.has("error")) {
                continuation.resumeWithException(
                    UnsupportedOperationException(
                        json.getString(
                            "error"
                        )
                    )
                )
            } else {
                val margin = json.getJSONArray("margin")
                val imageData = json.getString("imageData")
                val imageBuffer = Base64.decode(imageData, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBuffer, 0, imageBuffer.size)
                val chunk = NinePatchChunk.create(bitmap).serializedChunk
                val drawable = ShadowDrawable(
                    Rect(
                        margin.getInt(0),
                        margin.getInt(1),
                        margin.getInt(2),
                        margin.getInt(3)
                    ),
                    bitmap,
                    chunk,
                    NinePatchDrawable(
                        webkit.resources,
                        bitmap,
                        chunk,
                        null,
                        null
                    )
                )
                continuation.resume(drawable)
            }
        }
    }

    private suspend fun scheduleTask(input: JSONObject): ShadowDrawable {
        return withContext(Dispatchers.Main) {
            val id = UUID.randomUUID().toString()
            val script = "createNinePatch('$input','$id')"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                webkit.evaluateJavascript(script, null)
            } else {
                webkit.loadUrl("javascript:$script")
            }
            suspendCoroutine { tasks[id] = it }
        }
    }

    /**
     * @throws UnsupportedOperationException
     * */
    suspend fun newDrawable(
        options: ShadowOptions
    ): ShadowDrawable {
        val input = JSONObject(
            ShadowOptions::class.java.declaredFields
                .filter {
                    !Modifier.isStatic(it.modifiers)
                }
                .map { it.name to it.apply { isAccessible = true }.get(options) }
                .toMap()
        )
        return withContext(Dispatchers.Main) {
            val paddingTasks = needPendingTasks
            if (paddingTasks == null) {
                scheduleTask(input)
            } else {
                suspendCoroutine { paddingTasks.add(PendingTask(input, it)) }
            }
        }
    }
}