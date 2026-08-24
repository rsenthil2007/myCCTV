package com.opencode.multilensipcam

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

object WebApiJson {
    fun cameraDebugState(state: CameraDebugState): JSONObject {
        val mainEquivalentFocalLength = state.probes
            .asSequence()
            .filter { it.accessible && it.lensFacing == "Back" }
            .mapNotNull { probe -> probe.approximate35mmFocalLengths.minOrNull() }
            .minByOrNull { abs(it - 24f) }
        return JSONObject().apply {
            put("packageName", state.packageName)
            put("versionName", state.versionName)
            put("versionCode", state.versionCode)
            put("device", JSONObject().apply {
                put("manufacturer", state.manufacturer)
                put("brand", state.brand)
                put("model", state.model)
                put("device", state.device)
                put("product", state.product)
                put("hardware", state.hardware)
                put("fingerprint", state.fingerprint)
                put("sdkInt", state.sdkInt)
            })
            put("publicCameraIds", JSONArray(state.publicCameraIds))
            put("probes", JSONArray(state.probes.map { probe ->
                val equivalentFocalLength = probe.approximate35mmFocalLengths.minOrNull()
                val relativeZoom = if (
                    equivalentFocalLength != null &&
                    mainEquivalentFocalLength != null &&
                    mainEquivalentFocalLength > 0f &&
                    probe.lensFacing == "Back"
                ) {
                    equivalentFocalLength / mainEquivalentFocalLength
                } else {
                    null
                }
                JSONObject().apply {
                    put("cameraId", probe.cameraId)
                    put("public", probe.isPublic)
                    put("accessible", probe.accessible)
                    put("label", probe.label)
                    put("lensFacing", probe.lensFacing)
                    put("lensFacingValue", probe.lensFacingValue)
                    put("sensorOrientation", probe.sensorOrientation)
                    put("hardwareLevel", probe.hardwareLevel)
                    put("focalLengthsMm", JSONArray(probe.focalLengthsMm))
                    put("approximate35mmFocalLengths", JSONArray(probe.approximate35mmFocalLengths))
                    put("approximateRelativeZoom", relativeZoom?.let { "%.2f".format(Locale.US, it) })
                    put("approximateZoomLabel", relativeZoom?.let { "x${"%.1f".format(Locale.US, it)}" })
                    put("inferredLensRole", probe.inferredLensRole)
                    put("sensorPhysicalSizeMm", probe.sensorPhysicalSizeMm)
                    put("activeArraySize", probe.activeArraySize)
                    put("zoomRatioRange", probe.zoomRatioRange)
                    put("maxDigitalZoom", probe.maxDigitalZoom)
                    put("physicalCameraIds", JSONArray(probe.physicalCameraIds))
                    put("requestAvailableCapabilities", JSONArray(probe.requestAvailableCapabilities))
                    put("hasConstrainedHighSpeedVideo", probe.hasConstrainedHighSpeedVideo)
                    put("highSpeedVideoConfigurations", JSONArray(probe.highSpeedVideoConfigurations.map { config ->
                        JSONObject().apply {
                            put("size", config.size)
                            put("fpsRanges", JSONArray(config.fpsRanges))
                        }
                    }))
                    put("surfaceTextureSizes", JSONArray(probe.surfaceTextureSizes))
                    put("yuvSizes", JSONArray(probe.yuvSizes))
                    put("jpegSizes", JSONArray(probe.jpegSizes))
                    put("fpsRanges", JSONArray(probe.fpsRanges))
                    put("error", probe.error)
                }
            }))
        }
    }

    fun cameraScanState(state: CameraScanState): JSONObject {
        return JSONObject().apply {
            put("deviceKey", state.deviceKey)
            put("profileName", state.profileName)
            put("lastScanAtMs", state.lastScanAtMs)
            put("unsafeCameraIds", JSONArray(state.unsafeCameraIds.toList().sorted()))
            put("profileEntries", JSONArray(state.profileEntries.map(::verifiedCameraEntry)))
            put("cachedEntries", JSONArray(state.cachedEntries.map(::verifiedCameraEntry)))
            put("activeEntries", JSONArray(state.activeEntries.map(::verifiedCameraEntry)))
        }
    }

    fun cameraScanResponse(response: CameraScanResponse): JSONObject {
        return JSONObject().apply {
            put("ok", response.ok)
            put("error", response.error)
            put("state", cameraScanState(response.state))
            put("results", JSONArray(response.results.map { result ->
                JSONObject().apply {
                    put("cameraId", result.cameraId)
                    put("safe", result.safe)
                    put("verified", result.verified)
                    put("label", result.label)
                    put("reason", result.reason)
                    put("probe", result.probe?.let(::cameraOpenProbeResult))
                }
            }))
        }
    }

    fun cameraScanCacheDeleteResponse(response: CameraScanCacheDeleteResponse): JSONObject {
        return JSONObject().apply {
            put("ok", response.ok)
            put("error", response.error)
            put("deletedCameraIds", JSONArray(response.deletedCameraIds))
            put("state", cameraScanState(response.state))
        }
    }

    fun cameraOpenMatrix(
        ids: List<String>,
        modes: List<CameraOpenProbeMode>,
        results: List<CameraOpenProbeResult>
    ): JSONObject {
        return JSONObject().apply {
            put("ok", true)
            put("cameras", JSONArray(ids))
            put("modes", JSONArray(modes.map { it.controlValue }))
            put("results", JSONArray(results.map(::cameraOpenProbeResult)))
        }
    }

    fun cameraOpenProbeResult(result: CameraOpenProbeResult): JSONObject {
        return JSONObject().apply {
            put("cameraId", result.cameraId)
            put("mode", result.mode)
            put("requestedWidth", result.requestedWidth)
            put("requestedHeight", result.requestedHeight)
            put("requestedFps", result.requestedFps)
            put("durationMs", result.durationMs)
            put("elapsedMs", result.elapsedMs)
            put("characteristicsReadable", result.characteristicsReadable)
            put("opened", result.opened)
            put("configured", result.configured)
            put("repeating", result.repeating)
            put("frames", result.frames)
            put("events", JSONArray(result.events))
            put("error", result.error)
        }
    }

    fun state(state: WebControlState): JSONObject {
        return JSONObject().apply {
            put("versionName", state.versionName)
            put("versionCode", state.versionCode)
            put("versionLabel", state.versionLabel)
            put("streaming", state.streaming)
            put("cameraOptions", JSONArray(state.cameraOptions.map(::optionItem)))
            put("selectedCameraLabel", state.selectedCameraLabel)
            put("selectedCameraKey", state.selectedCameraKey)
            put("resolutionOptions", JSONArray(state.resolutionOptions))
            put("selectedResolution", state.selectedResolution)
            put("manualResolutionEnabled", state.manualResolutionEnabled)
            put("manualResolutionWidth", state.manualResolutionWidth)
            put("manualResolutionHeight", state.manualResolutionHeight)
            put("fpsOptions", JSONArray(state.fpsOptions))
            put("selectedFps", state.selectedFps)
            put("unlimitedFpsSupported", state.unlimitedFpsSupported)
            put("unlimitedFpsSelected", state.unlimitedFpsSelected)
            put("qualityMin", state.qualityMin)
            put("qualityMax", state.qualityMax)
            put("qualityValue", state.qualityValue)
            put("zoomSupported", state.zoomSupported)
            put("zoomMin", state.zoomMin)
            put("zoomMax", state.zoomMax)
            put("zoomValue", state.zoomValue)
            put("focusSupported", state.focusSupported)
            put("focusMode", state.focusMode)
            put("focusDistanceSupported", state.focusDistanceSupported)
            put("focusDistanceMin", state.focusDistanceMin)
            put("focusDistanceMax", state.focusDistanceMax)
            put("focusDistanceValue", state.focusDistanceValue)
            put("torchSupported", state.torchSupported)
            put("torchEnabled", state.torchEnabled)
            put("videoOverlayEnabled", state.videoOverlayEnabled)
            put("videoOverlaySizeMin", state.videoOverlaySizeMin)
            put("videoOverlaySizeMax", state.videoOverlaySizeMax)
            put("videoOverlaySizeValue", state.videoOverlaySizeValue)
            put("exposureSupported", state.exposureSupported)
            put("exposureMin", state.exposureMin)
            put("exposureMax", state.exposureMax)
            put("exposureValue", state.exposureValue)
            put("previewRotation", state.previewRotation)
            put("previewScale", state.previewScale)
            put("previewStretchX", state.previewStretchX)
            put("previewStretchY", state.previewStretchY)
            put("selectedJpegPipeline", state.selectedJpegPipeline)
            put("mjpegFullSize", state.mjpegFullSize)
            put("rtspUrl", state.rtspUrl)
            put("rtspAvailable", state.rtspAvailable)
            put("verifiedCameraOptions", JSONArray(state.verifiedCameraOptions.map(::optionItem)))
            put("audioSupported", state.audioSupported)
            put("audioPermissionGranted", state.audioPermissionGranted)
            put("audioEnabled", state.audioEnabled)
            put("audioRunning", state.audioRunning)
            put("audioStreaming", state.audioStreaming)
            put("audioClients", state.audioClients)
            put("audioUrl", state.audioUrl)
            put("audioStatus", state.audioStatus)
        }
    }

    fun ok(): JSONObject {
        return JSONObject().put("ok", true)
    }

    fun error(message: String): JSONObject {
        return JSONObject().put("ok", false).put("error", message)
    }

    private fun verifiedCameraEntry(entry: VerifiedCameraEntry): JSONObject {
        return JSONObject().apply {
            put("cameraId", entry.cameraId)
            put("label", entry.label)
            put("lensFacing", entry.lensFacing)
            put("zoomLabel", entry.zoomLabel)
            put("source", entry.source)
            put("verifiedAtMs", entry.verifiedAtMs)
            put("frames", entry.frames)
            put("note", entry.note)
        }
    }

    private fun optionItem(option: WebOptionItem): JSONObject {
        return JSONObject().apply {
            put("value", option.value)
            put("label", option.label)
        }
    }
}
