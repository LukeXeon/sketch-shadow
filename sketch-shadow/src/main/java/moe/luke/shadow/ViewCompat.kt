@file:JvmName("ViewCompat")

package moe.luke.shadow

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.Keep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val FLAG_CLIP_TO_PADDING by lazy {
    ViewGroup::class.java.getDeclaredField("FLAG_CLIP_TO_PADDING")
        .apply {
            isAccessible = true
        }.getInt(null)
}

private val FLAG_CLIP_CHILDREN by lazy {
    ViewGroup::class.java.getDeclaredField("FLAG_CLIP_CHILDREN")
        .apply {
            isAccessible = true
        }.getInt(null)
}

private val mGroupFlagsField by lazy {
    ViewGroup::class.java.getDeclaredField("mGroupFlags")
        .apply {
            isAccessible = true
        }
}

private val ViewGroup.mGroupFlags: Int
    get() {
        return mGroupFlagsField.getInt(this)
    }

private fun ViewGroup.hasBooleanFlag(flag: Int): Boolean {
    return mGroupFlags and flag == flag
}

internal var ViewGroup.clipChildrenCompat: Boolean
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            this.clipChildren
        } else {
            hasBooleanFlag(FLAG_CLIP_CHILDREN)
        }
    }
    set(value) {
        this.clipChildren = value
    }


internal var View.clipToOutlineCompat: Boolean
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.clipToOutline
        } else {
            false
        }
    }
    set(value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.clipToOutline = value
        }
    }

internal var ViewGroup.clipToPaddingCompat: Boolean
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.clipToPadding
        } else {
            return hasBooleanFlag(FLAG_CLIP_TO_PADDING)
        }
    }
    set(value) {
        this.clipToPadding = value
    }

@SuppressLint("JavascriptInterface", "AddJavascriptInterface")
internal suspend fun WebView.evaluateJavascript(script: String): String {
    val webView = this
    return withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            suspendCoroutine { continuation ->
                webView.evaluateJavascript(script) {
                    continuation.resume(it)
                }
            }
        } else {
            suspendCoroutine { continuation ->
                val callback = @Keep object : Any() {

                    val id: String by lazy {
                        "evaluateJavascript_callback_" + UUID.randomUUID().toString()
                            .replace("-", "")
                    }

                    @JavascriptInterface
                    fun onResponse(output: String) {
                        GlobalScope.launch(Dispatchers.Main) {
                            webView.removeJavascriptInterface(id)
                            continuation.resume(output)
                        }
                    }

                    @JavascriptInterface
                    fun onFallback() {
                        GlobalScope.launch(Dispatchers.Main) {
                            webView.removeJavascriptInterface(id)
                        }
                    }
                }
                webView.addJavascriptInterface(callback, callback.id)
                val wrapper = """javascript:(function(){
                        try {
                            var result = (function(){$script})();
                            if (typeof ${callback.id} !== "undefined") {
                                ${callback.id}.onResponse(result);
                            }
                        } catch (e) {
                            if (typeof ${callback.id} !== "undefined") {
                                ${callback.id}.onFallback();
                            }
                        }
                })();""".trimIndent()
                webView.loadUrl(wrapper)
            }
        }
    }
}

