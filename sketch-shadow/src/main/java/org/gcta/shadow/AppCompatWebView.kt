package org.gcta.shadow

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.webkit.*
import com.google.gson.Gson
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import java.util.*
import kotlin.collections.HashMap


open class AppCompatWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    private val delegate by lazy { AppCompatDelegate() }

    init {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            super.setWebChromeClient(delegate)
        }
    }

    override fun setWebChromeClient(client: WebChromeClient?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            delegate.client = client
        } else {
            super.setWebChromeClient(client)
        }
    }

    override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String?>?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            delegate.evaluateJavascript(this, script, resultCallback)
        } else {
            super.evaluateJavascript(script, resultCallback)
        }
    }

    private class AppCompatDelegate : WebChromeClient() {
        var client: WebChromeClient? = null
        private val callbacks = HashMap<String, ValueCallback<String?>>()
        private val prefix = toString() + ",Receive response="

        fun evaluateJavascript(
            webView: WebView,
            script: String,
            callback: ValueCallback<String?>?
        ) {
            val id = UUID.randomUUID().toString()
            val internalScript = if (callback == null) {
                script
            } else {
                callbacks[id] = callback
                """(function () {
                        var ex = null;
                        var result = null;
                        try {
                            result = $script
                        } catch (e) {
                            ex = e;
                        }
                        window.prompt('$prefix' + JSON.stringify({id: '$id', data: result}));
                        if (ex) {
                            throw ex;
                        }
                   })();"""
            }
            webView.loadUrl("javascript:$internalScript")
        }

        override fun onJsPrompt(
            view: WebView?,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult
        ): Boolean {
            Log.d(TAG, message.toString())
            if (!message.isNullOrEmpty() && message.startsWith(prefix)) {
                val string = message.substring(prefix.length)
                val obj = gson.fromJson(string, JsonObject::class.java)
                val callback = callbacks.remove(obj.get("id").asString)
                if (callback != null) {
                    val dataElement = obj.get("data")
                    callback.onReceiveValue(
                        if (dataElement == null || dataElement == JsonNull.INSTANCE)
                            null
                        else
                            dataElement.toString()
                    )
                }
                result.confirm()
                return true
            } else {
                return super.onJsPrompt(view, url, message, defaultValue, result)
            }
        }

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            client?.onProgressChanged(view, newProgress)
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            client?.onReceivedTitle(view, title)
        }

        override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
            client?.onReceivedIcon(view, icon)
        }

        override fun onReceivedTouchIconUrl(view: WebView?, url: String?, precomposed: Boolean) {
            client?.onReceivedTouchIconUrl(view, url, precomposed)
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            client?.onShowCustomView(view, callback)
        }

        override fun onShowCustomView(
            view: View?,
            requestedOrientation: Int,
            callback: CustomViewCallback?
        ) {
            client?.onShowCustomView(view, requestedOrientation, callback)
        }

        override fun onHideCustomView() {
            client?.onHideCustomView()
        }

        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            return client?.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
                ?: super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
        }

        override fun onRequestFocus(view: WebView?) {
            client?.onRequestFocus(view)
        }

        override fun onCloseWindow(window: WebView?) {
            client?.onCloseWindow(window)
        }

        override fun onJsAlert(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult?
        ): Boolean {
            return client?.onJsAlert(view, url, message, result)
                ?: super.onJsAlert(view, url, message, result)
        }

        override fun onJsConfirm(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult?
        ): Boolean {
            return client?.onJsConfirm(view, url, message, result)
                ?: super.onJsConfirm(view, url, message, result)
        }

        override fun onJsBeforeUnload(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult?
        ): Boolean {
            return client?.onJsBeforeUnload(view, url, message, result)
                ?: super.onJsBeforeUnload(view, url, message, result)
        }

        override fun onExceededDatabaseQuota(
            url: String?,
            databaseIdentifier: String?,
            quota: Long,
            estimatedDatabaseSize: Long,
            totalQuota: Long,
            quotaUpdater: WebStorage.QuotaUpdater?
        ) {
            client?.onExceededDatabaseQuota(
                url,
                databaseIdentifier,
                quota,
                estimatedDatabaseSize,
                totalQuota,
                quotaUpdater
            )
        }

        override fun onReachedMaxAppCacheSize(
            requiredStorage: Long,
            quota: Long,
            quotaUpdater: WebStorage.QuotaUpdater?
        ) {
            client?.onReachedMaxAppCacheSize(requiredStorage, quota, quotaUpdater)
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String?,
            callback: GeolocationPermissions.Callback?
        ) {
            client?.onGeolocationPermissionsShowPrompt(origin, callback)
        }

        override fun onGeolocationPermissionsHidePrompt() {
            client?.onGeolocationPermissionsHidePrompt()
        }

        override fun onJsTimeout(): Boolean {
            return client?.onJsTimeout()
                ?: super.onJsTimeout()
        }

        override fun onConsoleMessage(message: String?, lineNumber: Int, sourceID: String?) {
            client?.onConsoleMessage(message, lineNumber, sourceID)
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            return client?.onConsoleMessage(consoleMessage)
                ?: super.onConsoleMessage(consoleMessage)
        }

        override fun getDefaultVideoPoster(): Bitmap? {
            return client?.defaultVideoPoster
                ?: super.getDefaultVideoPoster()
        }

        override fun getVideoLoadingProgressView(): View? {
            return client?.videoLoadingProgressView
                ?: super.getVideoLoadingProgressView()
        }

        override fun getVisitedHistory(callback: ValueCallback<Array<String>>?) {
            client?.getVisitedHistory(callback)
        }

        companion object {
            private const val TAG = "AppCompatWebView"
            private val gson by lazy { Gson() }
        }
    }

}