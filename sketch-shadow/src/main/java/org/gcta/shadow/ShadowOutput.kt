package org.gcta.shadow

import proguard.annotation.Keep as ProguardKeep
import androidx.annotation.Keep as AndroidXKeep

@ProguardKeep
@AndroidXKeep
internal class ShadowOutput(
    var error: String? = null,
    var margin: List<Float>? = null,
    var imageData: String? = null
)