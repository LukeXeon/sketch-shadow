package org.gcta.shadow

import android.app.Activity
import android.view.ViewGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


interface ShadowFactory {

    suspend fun newDrawable(options: ShadowOptions): ShadowDrawable

    fun release()

    companion object {
        suspend fun create(activity: Activity): ShadowFactory {
            return withContext(Dispatchers.Main) {
                ShadowFactoryImpl(activity).also {
                    // 为了兼容Google的傻逼bug↓
                    // https://github.com/jakub-g/webview-bug-onPageFinished-sometimes-not-called
                    // 所以必须将其放置到窗口中
                    (activity.window.decorView as ViewGroup).addView(it, 0, 0)
                }
            }
        }
    }

}