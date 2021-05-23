package org.gcta.shadow

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.Process
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gcta.shadow.*
import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue
import kotlin.concurrent.thread
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 除非你非常了解[PhantomReference]和[ReferenceQueue]否则不要改这个类
 * */
class ShadowFactory private constructor(private val webkit: AppCompatJsWebView) {

    private class LifecycleObserver(
        referent: ShadowFactory,
        q: ReferenceQueue<ShadowFactory>,
        private val webkit: AppCompatJsWebView,
        private val application: Application
    ) : PhantomReference<ShadowFactory>(referent, q),
        Application.ActivityLifecycleCallbacks {

        init {
            application.registerActivityLifecycleCallbacks(this)
        }

        fun onRelease() {
            application.unregisterActivityLifecycleCallbacks(this)
            (webkit.parent as? ViewGroup)?.removeView(webkit)
            Log.d(TAG, "clear $webkit")
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {

        }

        override fun onActivityStarted(activity: Activity) {

        }

        override fun onActivityResumed(activity: Activity) {
            val decorView = activity.window.decorView
            (webkit.parent as? ViewGroup)?.removeView(webkit)
            (decorView as ViewGroup).addView(webkit, 0, 0)
        }

        override fun onActivityPaused(activity: Activity) {

        }

        override fun onActivityStopped(activity: Activity) {

        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {

        }

        override fun onActivityDestroyed(activity: Activity) {

        }

    }

    /**
     * 触发[ShadowFactory.onLoadFinished]
     * 触发后要置空，否则将造成循环引用，导致资源无法释放
     * */
    private class Trigger(private var factory: ShadowFactory?) : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            factory?.onLoadFinished()
            factory = null
        }
    }

    private val pendingTasks = ArrayList<Pair<ShadowOptions, Continuation<ShadowDrawable>>>()
    private var isLoadFinished: Boolean = false

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

    suspend fun newDrawable(options: ShadowOptions): ShadowDrawable {
        webkit.rootView ?: throw UnsupportedOperationException("must attach Activity")
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
        private val gson by lazy { Gson() }
        private val queue = ReferenceQueue<ShadowFactory>()
        private val observers = HashSet<LifecycleObserver>()
        private const val TAG = "ShadowFactory"

        init {
            thread(isDaemon = true, priority = Process.THREAD_PRIORITY_BACKGROUND) {
                while (true) {
                    val observer = queue.remove() as? LifecycleObserver ?: continue
                    GlobalScope.launch(Dispatchers.Main) {
                        if (observers.remove(observer)) {
                            observer.onRelease()
                        }
                    }
                }
            }
        }

        @SuppressLint("SetJavaScriptEnabled")
        suspend fun create(activity: Activity): ShadowFactory {
            val application = activity.application
            val rootView = activity.window.decorView
            return withContext(Dispatchers.Main) {
                val webkit = AppCompatJsWebView(application)
                // 为了兼容Google的傻逼bug↓
                // https://github.com/jakub-g/webview-bug-onPageFinished-sometimes-not-called
                // 所以必须将其放置到窗口中
                (rootView as ViewGroup).addView(webkit, 0, 0)
                val factory = ShadowFactory(webkit)
                observers.add(LifecycleObserver(factory, queue, webkit, application))
                webkit.settings.javaScriptEnabled = true
                webkit.webViewClient = Trigger(factory)
                webkit.setBackgroundColor(Color.TRANSPARENT)
                webkit.loadUrl("file:///android_asset/webkit_shadow_renderer/index.html")
                return@withContext factory
            }
        }
    }

}