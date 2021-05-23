package org.gcta.shadow

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Process
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebChromeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gcta.shadow.*
import java.lang.ref.ReferenceQueue
import kotlin.concurrent.thread


class ShadowFactory private constructor(private val taskManager: TaskManager) {

    companion object {
        private val queue = ReferenceQueue<ShadowFactory>()
        private val cleaners = HashSet<FactoryCleaner>()
        private const val TAG = "ShadowFactory"

        init {
            thread(isDaemon = true, priority = Process.THREAD_PRIORITY_BACKGROUND) {
                while (true) {
                    val cleaner = queue.remove() as? FactoryCleaner ?: continue
                    GlobalScope.launch(Dispatchers.Main) {
                        cleaners.remove(cleaner)
                        cleaner.detach()
                        Log.d(TAG, "clear ${cleaner.webkit}")
                    }
                }
            }
        }

        @SuppressLint("SetJavaScriptEnabled")
        suspend fun create(activity: Activity): ShadowFactory {
            return withContext(Dispatchers.Main) {
                val webkit = AppCompatJsWebView(activity.application)
                // 为了兼容Google的傻逼bug↓
                // https://github.com/jakub-g/webview-bug-onPageFinished-sometimes-not-called
                // 所以必须将其放置到窗口中
                (activity.window.decorView as ViewGroup).addView(webkit, 0, 0)
                val taskManager = TaskManager(webkit)
                val factory = ShadowFactory(taskManager)
                val cleaner = FactoryCleaner(factory, queue, webkit)
                cleaners.add(cleaner)
                webkit.settings.javaScriptEnabled = true
                webkit.webViewClient = taskManager
                webkit.webChromeClient = WebChromeClient()
                webkit.setBackgroundColor(Color.TRANSPARENT)
                webkit.loadUrl("file:///android_asset/webkit_shadow_renderer/index.html")
                return@withContext factory
            }
        }
    }

    suspend fun newDrawable(options: ShadowOptions): ShadowDrawable {
        return taskManager.newTask(options)
    }

}