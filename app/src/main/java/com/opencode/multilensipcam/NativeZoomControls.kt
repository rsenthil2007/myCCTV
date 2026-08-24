package com.opencode.multilensipcam

import android.util.Range

object NativeZoomControls {
    fun effectiveRange(
        repository: CameraLensRepository,
        option: CameraLensOption?,
        capabilities: CameraCapabilities?,
        allOptions: List<CameraLensOption>
    ): Range<Float>? {
        val selected = option ?: return capabilities?.zoomRatioRange
        val baseRange = capabilities?.zoomRatioRange ?: return null
        val lowerBound = repository.inferNativeZoomRatio(selected, capabilities, allOptions) ?: baseRange.lower
        return Range(lowerBound.coerceIn(baseRange.lower, baseRange.upper), baseRange.upper)
    }

    fun defaultRatio(
        repository: CameraLensRepository,
        option: CameraLensOption,
        capabilities: CameraCapabilities,
        allOptions: List<CameraLensOption>
    ): Float? {
        val range = effectiveRange(repository, option, capabilities, allOptions) ?: return null
        return repository.inferNativeZoomRatio(option, capabilities, allOptions) ?: range.lower
    }
}
