@file:JvmName("ViewCompat")

package org.gcta.shadow

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.view.ViewGroup

private val FLAG_CLIP_TO_PADDING by lazy {
    ViewGroup::class.java.getDeclaredField("FLAG_CLIP_TO_PADDING")
        .apply {
            isAccessible = true
        }.getInt(null)
}
    @SuppressLint("SoonBlockedPrivateApi")
    get

private val FLAG_CLIP_CHILDREN by lazy {
    ViewGroup::class.java.getDeclaredField("FLAG_CLIP_CHILDREN")
        .apply {
            isAccessible = true
        }.getInt(null)
}
    @SuppressLint("SoonBlockedPrivateApi")
    get

private val mGroupFlagsField by lazy {
    ViewGroup::class.java.getDeclaredField("mGroupFlags")
        .apply {
            isAccessible = true
        }
}
    @SuppressLint("SoonBlockedPrivateApi")
    get

private val ViewGroup.mGroupFlags: Int
    get() {
        return mGroupFlagsField.getInt(this)
    }

private fun ViewGroup.hasBooleanFlag(flag: Int): Boolean {
    return mGroupFlags and flag == flag
}

var ViewGroup.clipChildrenCompat: Boolean
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


var View.clipToOutlineCompat: Boolean
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

var ViewGroup.clipToPaddingCompat: Boolean
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

