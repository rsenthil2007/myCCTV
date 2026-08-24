package com.opencode.multilensipcam

enum class MjpegPipeline(val controlValue: String, val label: String) {
    YUV_JPEG("yuv", "YUV + YuvImage JPEG"),
    CAMERA_JPEG("camera-jpeg", "Camera2 JPEG output");

    companion object {
        fun fromControlValue(value: String?): MjpegPipeline? {
            return entries.firstOrNull { pipeline ->
                pipeline.controlValue.equals(value, ignoreCase = true) ||
                    pipeline.name.equals(value, ignoreCase = true)
            }
        }
    }
}
