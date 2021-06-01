package org.gcta.shadow

import android.content.Context

interface ShadowFactory {

    suspend fun newDrawable(options: ShadowOptions): ShadowDrawable

    companion object {
        suspend fun create(context: Context): ShadowFactory {
            return WebkitRenderer.create(context)
        }
    }
}