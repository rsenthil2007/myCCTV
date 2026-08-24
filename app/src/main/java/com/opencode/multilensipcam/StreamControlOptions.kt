package com.opencode.multilensipcam

import android.util.Size

data class StreamPreset(
    val label: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val quality: Int
)

data class StreamFpsSelection(
    val targetFps: Int?,
    val unlimitedFpsSelected: Boolean
)

data class StreamPresetSelection(
    val outputSize: Size,
    val manualResolutionMode: Boolean,
    val targetFps: Int,
    val streamQuality: Int
)

object StreamControlOptions {
    val qualityOptions = listOf(30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100)

    val preset1080p30 = StreamPreset("1080p 30fps 75%", 1920, 1080, 30, 75)
    val preset4k30 = StreamPreset("4K 30fps 60%", 3840, 2160, 30, 60)
    val preset720p30 = StreamPreset("720p 30fps 100%", 1280, 720, 30, 100)
    val preset720p60 = preset720p30

    const val defaultQuality = 60

    fun presetForKey(key: String): StreamPreset? {
        return when (key) {
            "1080p30" -> preset1080p30
            "4k30" -> preset4k30
            "720p30" -> preset720p30
            "720p60" -> preset720p30
            else -> null
        }
    }

    fun resolveFpsCommand(
        repository: CameraLensRepository,
        capabilities: CameraCapabilities,
        selectedTargetFps: Int?,
        unlimitedFpsSelected: Boolean,
        requestedFps: Int?,
        requestedUnlimitedFps: Boolean?
    ): StreamFpsSelection? {
        var nextTargetFps = selectedTargetFps
        var nextUnlimitedFpsSelected = unlimitedFpsSelected
        var changed = false
        val userFpsOptions = repository.listTargetFpsOptions(capabilities).filter { fps -> fps < 60 }
        val requestedNearestFps = requestedFps?.let { fps ->
            userFpsOptions.minByOrNull { kotlin.math.abs(it - fps) }
        }

        requestedNearestFps?.let { nearest ->
            if (nearest != nextTargetFps || nextUnlimitedFpsSelected) {
                nextTargetFps = nearest
                nextUnlimitedFpsSelected = false
                changed = true
            }
        }

        requestedUnlimitedFps?.let { unlimited ->
            if (unlimited != nextUnlimitedFpsSelected) {
                nextUnlimitedFpsSelected = unlimited
                nextTargetFps = if (unlimited) {
                    repository.chooseHighestFps(capabilities)
                } else {
                    requestedNearestFps
                        ?: userFpsOptions.firstOrNull { it == 30 }
                        ?: userFpsOptions.firstOrNull()
                        ?: repository.chooseRecommendedFps(capabilities).coerceAtMost(30)
                }
                changed = true
            }
        }

        return if (changed) {
            StreamFpsSelection(nextTargetFps, nextUnlimitedFpsSelected)
        } else {
            null
        }
    }

    fun isPresetSupported(
        repository: CameraLensRepository,
        capabilities: CameraCapabilities,
        preset: StreamPreset
    ): Boolean {
        if (preset.fps != 60) return true
        if (capabilities.fpsRanges.any { it.upper >= 60 }) return true
        val presetSize = repository.findSupportedOrNearestSize(capabilities, preset.width, preset.height)
            ?: Size(preset.width, preset.height)
        return repository.supportsHighSpeedVideoFps(capabilities, presetSize, preset.fps)
    }

    fun resolvePresetSelection(
        repository: CameraLensRepository,
        capabilities: CameraCapabilities,
        preset: StreamPreset
    ): StreamPresetSelection {
        val outputSize = repository.findSupportedOrNearestSize(capabilities, preset.width, preset.height)
            ?: repository.chooseRecommendedSize(capabilities)
        val targetFps = if (preset.fps in repository.listTargetFpsOptions(capabilities)) {
            preset.fps
        } else {
            repository.chooseTargetFpsRange(capabilities, preset.fps)?.upper
        } ?: repository.listTargetFpsOptions(capabilities).minByOrNull { kotlin.math.abs(it - preset.fps) }
            ?: repository.chooseRecommendedFps(capabilities)
        return StreamPresetSelection(
            outputSize = outputSize,
            manualResolutionMode = outputSize !in repository.listSelectableSizes(capabilities),
            targetFps = targetFps,
            streamQuality = nearestQuality(preset.quality)
        )
    }

    fun nearestQuality(value: Int): Int {
        return qualityOptions.minByOrNull { kotlin.math.abs(it - value) } ?: defaultQuality
    }
}
