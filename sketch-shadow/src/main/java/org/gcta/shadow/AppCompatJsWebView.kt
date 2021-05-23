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
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.HashMap
import androidx.annotation.Keep as AndroidXKeep
import proguard.annotation.Keep as ProguardKeep


@SuppressLint("AddJavascriptInterface", "SetJavaScriptEnabled")
class AppCompatJsWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    private val evaluateJavascriptDelegate by lazy { AppCompatEvaluateJavascriptDelegate(this) }

    init {
        settings.javaScriptEnabled = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            addJavascriptInterface(
                evaluateJavascriptDelegate,
                AppCompatEvaluateJavascriptDelegate::class.java.simpleName
            )
        }
    }

    override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            evaluateJavascriptDelegate.evaluateJavascript(script, resultCallback)
        } else {
            super.evaluateJavascript(script, resultCallback)
        }
    }

    @AndroidXKeep
    @ProguardKeep
    private class AppCompatEvaluateJavascriptDelegate(webView: WebView) {
        private val webView = WeakReference(webView)
        private val mainThread = Handler(Looper.getMainLooper())
        private val callbacks = HashMap<String, ValueCallback<String>>()

        fun evaluateJavascript(script: String, callback: ValueCallback<String>?) {
            val view = webView.get() ?: return
            val id = UUID.randomUUID().toString()
            val manager = AppCompatEvaluateJavascriptDelegate::class.java.simpleName
            val internalScript = if (callback == null) {
                script
            } else {
                callbacks[id] = callback
                """(function(){
                        try {
                            var result = $script;
                            $manager.onResponse("$id",JSON.stringify(result));
                        } catch (e) {
                            $manager.onResponse("$id",null);
                            throw e;
                        }
                })();""".trimIndent()
            }
            view.loadUrl("javascript:$internalScript")
        }

        @JavascriptInterface
        fun onResponse(callbackId: String, result: String?) {
            mainThread.post {
                callbacks.remove(callbackId)?.onReceiveValue(result ?: "")
            }
        }
    }
}