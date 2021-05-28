package org.gcta.shadow

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.annotation.WorkerThread
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.HashMap
import androidx.annotation.Keep as AndroidXKeep
import proguard.annotation.Keep as ProguardKeep


@SuppressLint("AddJavascriptInterface")
open class AppCompatWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    private val delegate by lazy { AppCompatDelegate(this) }

    init {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            super.addJavascriptInterface(
                delegate,
                delegate.toString()
            )
        }
    }

    override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String?>?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            delegate.evaluateJavascript(script, resultCallback)
        } else {
            super.evaluateJavascript(script, resultCallback)
        }
    }

    private class AppCompatDelegate(webView: WebView) {
        private val webView = WeakReference(webView)
        private val mainThread = Handler(Looper.getMainLooper())
        private val callbacks = HashMap<String, ValueCallback<String?>>()

        fun evaluateJavascript(script: String, callback: ValueCallback<String?>?) {
            val view = webView.get() ?: return
            val id = UUID.randomUUID().toString()
            var internalScript = "(function(){$script})();"
            val manager = toString()
            internalScript = if (callback == null) {
                internalScript
            } else {
                callbacks[id] = callback
                """(function(){
                        try {
                            var result = $internalScript
                            $manager.onResponse('$id',result?JSON.stringify(result):null);
                        } catch (e) {
                            $manager.onResponse('$id',null);
                            throw e;
                        }
                })();""".trimIndent()
            }
            view.loadUrl("javascript:$internalScript")
        }

        @AndroidXKeep
        @ProguardKeep
        @WorkerThread
        @JavascriptInterface
        fun onResponse(callbackId: String, result: String?) {
            mainThread.post {
                callbacks.remove(callbackId)?.onReceiveValue(result)
            }
        }

        override fun toString(): String {
            return javaClass.simpleName + hashCode()
        }
    }
}