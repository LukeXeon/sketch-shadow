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
import androidx.annotation.MainThread
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
class ShadowFactory
@MainThread
private constructor(context: Context) {

    private inner class TaskManager : WebViewClient() {
        private val pendingTasks = ArrayList<Pair<JSONObject, Continuation<ShadowDrawable>>>()
        private lateinit var runningTasks: HashMap<String, Continuation<ShadowDrawable>>
        private val isLoadFinished: Boolean
            get() = this::runningTasks.isInitialized

        private suspend fun runTask(input: JSONObject): ShadowDrawable {
            return withContext(Dispatchers.Main) {
                ensureActive()
                val id = UUID.randomUUID().toString()
                val script = "createNinePatch('$input','$id')"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    webkit.evaluateJavascript(script, null)
                } else {
                    webkit.loadUrl("javascript:$script")
                }
                suspendCoroutine { runningTasks[id] = it }
            }
        }

        @MainThread
        override fun onPageFinished(view: WebView?, url: String?) {
            if (!isLoadFinished) {
                for ((input, continuation) in pendingTasks) {
                    GlobalScope.launch(Dispatchers.Main) {
                        continuation.resume(runTask(input))
                    }
                }
                pendingTasks.clear()
                runningTasks = HashMap()
            }
        }

        @Keep
        @WorkerThread
        @JavascriptInterface
        fun onTaskComplete(output: String, id: String) {
            GlobalScope.launch(Dispatchers.Main) {
                val continuation = runningTasks.remove(id) ?: return@launch
                ensureActive()
                withContext(Dispatchers.Default) {
                    val json = JSONObject(output)
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
        }

        suspend fun scheduleTask(input: JSONObject): ShadowDrawable {
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
    private val webkit = WebView(context.applicationContext)

    init {
        webkit.settings.javaScriptEnabled = true
        webkit.webViewClient = taskManager
        webkit.addJavascriptInterface(taskManager, "__taskManager__")
        webkit.loadUrl("file:///android_asset/webkit_shadow_renderer/index.html")
    }

    companion object {
        suspend fun create(context: Context): ShadowFactory {
            return withContext(Dispatchers.Main) {
                ShadowFactory(context)
            }
        }
    }

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
        return taskManager.scheduleTask(input)
    }
}