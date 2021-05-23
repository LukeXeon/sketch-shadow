package org.gcta.shadow

import android.view.ViewGroup
import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue

internal class FactoryCleaner(
    referent: ShadowFactory,
    q: ReferenceQueue<ShadowFactory>,
    val webkit: AppCompatJsWebView
) : PhantomReference<ShadowFactory>(referent, q) {
    fun detach() {
        (webkit.parent as? ViewGroup)?.removeView(webkit)
    }
}