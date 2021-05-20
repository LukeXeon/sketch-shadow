package moe.luke.shadow

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.Px
import proguard.annotation.Keep as ProguardKeep
import androidx.annotation.Keep as AndroidXKeep

@ProguardKeep
@AndroidXKeep
data class ShadowOptions(
    @Px
    var roundLeftTop: Int = 12,
    @Px
    var roundRightTop: Int = 12,
    @Px
    var roundLeftBottom: Int = 12,
    @Px
    var roundRightBottom: Int = 12,
    @Px
    var shadowBlur: Int = 20,
    @ColorInt
    var shadowColor: Int = Color.parseColor("#757575"),
    @Px
    var shadowDx: Int = 0,
    @Px
    var shadowDy: Int = 0,
    @ColorInt
    var fillColor: Int = Color.TRANSPARENT,
    @ColorInt
    var outlineColor: Int = Color.TRANSPARENT,
    @Px
    var outlineWidth: Int = 0,
    @Px
    var paddingLeft: Int = UNSET_PADDING,
    @Px
    var paddingRight: Int = UNSET_PADDING,
    @Px
    var paddingTop: Int = UNSET_PADDING,
    @Px
    var paddingBottom: Int = UNSET_PADDING
) : Cloneable {
    companion object {
        const val UNSET_PADDING = -1
    }
}