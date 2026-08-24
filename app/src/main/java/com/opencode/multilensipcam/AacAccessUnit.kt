package com.opencode.multilensipcam

data class AacAccessUnit(
    val bytes: ByteArray,
    val presentationTimeUs: Long
)
