package moe.luke.shadow

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.drawable.NinePatchDrawable
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.MainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@SuppressLint("SetJavaScriptEnabled")
class ShadowFactory
@MainThread
private constructor(context: Context) {

    private inner class TaskManager : WebViewClient() {
        private val pendingTasks = ArrayList<Pair<JSONObject, Continuation<ShadowDrawable>>>()
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

        private suspend fun runTask(input: JSONObject): ShadowDrawable {
            val output = webkit.evaluateJavascript("createNinePatch('$input');")
            return withContext(Dispatchers.Default) {
                val json = JSONObject(output)
                if (json.has("error")) {
                    throw UnsupportedOperationException(
                        json.getString(
                            "error"
                        )
                    )
                } else {
                    val margin = json.getJSONArray("margin")
                    val imageData = json.getString("imageData")
                    val imageBuffer = Base64.decode(imageData, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBuffer, 0, imageBuffer.size)
                    val chunk = NinePatchChunk.findPatches(bitmap)
                    ShadowDrawable(
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