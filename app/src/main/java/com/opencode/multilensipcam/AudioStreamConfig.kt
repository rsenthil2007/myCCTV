package com.opencode.multilensipcam

data class AudioStreamConfig(
    val sampleRate: Int = 44_100,
    val channelCount: Int = 1,
    val bitrate: Int = 64_000
) {
    val frequencyIndex: Int
        get() = when (sampleRate) {
            96_000 -> 0
            88_200 -> 1
            64_000 -> 2
            48_000 -> 3
            44_100 -> 4
            32_000 -> 5
            24_000 -> 6
            22_050 -> 7
            16_000 -> 8
            12_000 -> 9
            11_025 -> 10
            8_000 -> 11
            7_350 -> 12
            else -> 4
        }
}
