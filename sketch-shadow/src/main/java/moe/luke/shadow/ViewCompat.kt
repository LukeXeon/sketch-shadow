@file:JvmName("ViewCompat")

package moe.luke.shadow

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebView
import java.util.*

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
internal fun WebView.evaluateJavascriptCompat(
    script: String,
    callback: ValueCallback<String>? = null
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        evaluateJavascript(script, callback)
    } else {
        val internalScript0 = """(function(){$script})();""".trimIndent()
        if (callback == null) {
            loadUrl(internalScript0)
        } else {
            val internalCallback = object : Any() {

                val id: String by lazy {
                    "evaluateJavascript_callback_" + UUID.randomUUID().toString()
                        .replace("-", "")
                }

                @JavascriptInterface
                fun onResponse(output: String) {
                    post {
                        removeJavascriptInterface(id)
                        callback.onReceiveValue(output)
                    }
                }

                @JavascriptInterface
                fun onFallback() {
                    post {
                        removeJavascriptInterface(id)
                    }
                }
            }
            addJavascriptInterface(internalCallback, internalCallback.id)
            val internalScript1 = """javascript:(function(){
                        try {
                            var result = $internalScript0
                            if (typeof ${internalCallback.id} !== "undefined") {
                                ${internalCallback.id}.onResponse(result);
                            }
                        } catch (e) {
                            if (typeof ${internalCallback.id} !== "undefined") {
                                ${internalCallback.id}.onFallback();
                            }
                        }
                })();""".trimIndent()
            loadUrl(internalScript1)
        }
    }
}

