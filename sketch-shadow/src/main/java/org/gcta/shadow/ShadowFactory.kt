package org.gcta.shadow

import android.app.Activity
import android.view.ViewGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


interface ShadowFactory {

    suspend fun newDrawable(options: ShadowOptions): ShadowDrawable

    companion object {
        suspend fun create(activity: Activity): ShadowFactory {
            return withContext(Dispatchers.Main) {
                activity.window.decorView.findViewWithTag(ShadowFactory)
                    ?: FactoryWebView(activity).also {
                        // 为了兼容↓
                        // https://github.com/jakub-g/webview-bug-onPageFinished-sometimes-not-called
                        // 所以必须将其放置到窗口中
                        (activity.window.decorView as ViewGroup).addView(it, 0, 0)
                    }
            }
        }
    }

}