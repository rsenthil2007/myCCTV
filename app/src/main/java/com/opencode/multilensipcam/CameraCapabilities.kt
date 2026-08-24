package com.opencode.multilensipcam

import android.util.Range
import android.util.Size

data class CameraCapabilities(
    val outputSizes: List<Size>,
    val zoomRatioRange: Range<Float>?,
    val fpsRanges: List<Range<Int>>,
    val hasConstrainedHighSpeedVideo: Boolean,
    val highSpeedVideoFpsRanges: Map<Size, List<Range<Int>>>,
    val exposureCompensationRange: Range<Int>?,
    val exposureCompensationStep: Float,
    val focusModes: List<Int>,
    val minimumFocusDistance: Float,
    val focalLengths: List<Float>,
    val sensorOrientation: Int,
    val videoStabilizationModes: List<Int>,
    val flashAvailable: Boolean
)
