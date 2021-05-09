@file:JvmName("ViewCompat")

package moe.luke.shadow

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val FLAG_CLIP_TO_PADDING by lazy {
    ViewGroup::class.java.getDeclaredField("FLAG_CLIP_TO_PADDING")
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

internal suspend fun WebView.evaluateJavascript(script: String): String {
    return withContext(Dispatchers.Main) {
        suspendCoroutine { continuation ->
            evaluateJavascript(script) {
                continuation.resume(it)
            }
        }
    }
}

