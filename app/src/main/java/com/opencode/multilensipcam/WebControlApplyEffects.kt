package com.opencode.multilensipcam

data class WebControlApplyEffects(
    var requiresRestart: Boolean = false,
    var runtimeChanged: Boolean = false,
    var cameraChanged: Boolean = false
)
