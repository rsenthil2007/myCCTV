package com.opencode.multilensipcam

data class H264AccessUnit(
    val bytes: ByteArray,
    val isKeyFrame: Boolean,
    val isCodecConfig: Boolean
)
