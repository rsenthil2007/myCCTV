package com.opencode.multilensipcam

import android.util.Range
import android.util.Size

data class CameraStreamConfig(
    val option: CameraLensOption,
    val outputSize: Size,
    val mjpegSize: Size,
    val jpegQuality: Int = 60,
    val streamQuality: Int = 60,
    val targetFps: Int = 20,
    val targetFpsRange: Range<Int>? = null,
    val zoomRatio: Float? = null,
    val focusMode: FocusMode = FocusMode.AUTO,
    val focusDistance: Float? = null,
    val exposureCompensation: Int = 0,
    val torchEnabled: Boolean = false,
    val h264Enabled: Boolean = false,
    val mjpegPipeline: MjpegPipeline = MjpegPipeline.YUV_JPEG,
    val highSpeedVideoEnabled: Boolean = false,
    val regularFallbackFpsRange: Range<Int>? = null,
    val highSpeedVideoFallbackReason: String? = null
)

object CameraStreamConfigs {
    fun build(
        repository: CameraLensRepository,
        option: CameraLensOption,
        capabilities: CameraCapabilities,
        selectedOutputSize: Size?,
        useFullMjpegSize: Boolean,
        streamQuality: Int,
        selectedTargetFps: Int?,
        unlimitedFpsSelected: Boolean,
        runtimeControls: NativeRuntimeControlValues,
        focusMode: FocusMode,
        h264Enabled: Boolean,
        mjpegPipeline: MjpegPipeline
    ): CameraStreamConfig {
        val outputSize = selectedOutputSize ?: repository.chooseRecommendedSize(capabilities)
        val requestedTargetFps = selectedTargetFps ?: repository.chooseRecommendedFps(capabilities)
        val highSpeedRange = if (!unlimitedFpsSelected && h264Enabled && requestedTargetFps == 60) {
            repository.chooseHighSpeedVideoFpsRange(capabilities, outputSize, requestedTargetFps)
        } else {
            null
        }
        val normalTargetRange = if (unlimitedFpsSelected) {
            repository.chooseUnlimitedFpsRange(capabilities)
        } else {
            repository.chooseTargetFpsRange(
                capabilities,
                requestedTargetFps
            )
        }
        val regularFallbackRange = if (highSpeedRange != null) {
            repository.chooseTargetFpsRange(capabilities, minOf(requestedTargetFps, 30)) ?: normalTargetRange
        } else {
            null
        }
        val targetRange = highSpeedRange ?: normalTargetRange
        val selectedFpsHasNoActiveRange = selectedTargetFps != null &&
            highSpeedRange == null &&
            normalTargetRange?.upper != selectedTargetFps
        val targetFps = if (unlimitedFpsSelected) {
            normalTargetRange?.upper ?: repository.chooseHighestFps(capabilities)
        } else if (selectedFpsHasNoActiveRange) {
            normalTargetRange?.upper ?: repository.chooseRecommendedFps(capabilities)
        } else {
            requestedTargetFps
        }
        val highSpeedFallbackReason = when {
            highSpeedRange != null -> null
            selectedTargetFps == null || selectedTargetFps < 60 -> null
            unlimitedFpsSelected -> "unlimited-fps-uses-regular-session"
            !h264Enabled -> "high-speed-video-is-h264-only"
            requestedTargetFps != 60 -> "high-speed-fps-range-unavailable"
            !capabilities.hasConstrainedHighSpeedVideo -> "camera-lacks-constrained-high-speed-video"
            capabilities.highSpeedVideoFpsRanges.none { (size, ranges) ->
                size == outputSize && ranges.any { it.lower <= selectedTargetFps && it.upper >= selectedTargetFps }
            } -> "selected-size-not-high-speed"
            else -> "high-speed-fps-range-unavailable"
        }
        return CameraStreamConfig(
            option = option,
            outputSize = outputSize,
            mjpegSize = if (useFullMjpegSize) outputSize else repository.chooseMjpegSize(capabilities, outputSize),
            jpegQuality = streamQuality,
            streamQuality = streamQuality,
            targetFps = targetFps,
            targetFpsRange = targetRange,
            zoomRatio = runtimeControls.zoomRatio,
            focusMode = focusMode,
            focusDistance = runtimeControls.focusDistance,
            exposureCompensation = runtimeControls.exposureCompensation,
            torchEnabled = runtimeControls.torchEnabled,
            h264Enabled = h264Enabled,
            mjpegPipeline = mjpegPipeline,
            highSpeedVideoEnabled = highSpeedRange != null,
            regularFallbackFpsRange = regularFallbackRange,
            highSpeedVideoFallbackReason = highSpeedFallbackReason
        )
    }
}
