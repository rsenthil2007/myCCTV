package com.opencode.multilensipcam

import android.hardware.camera2.CameraManager

class CameraScanRunner(
    private val cameraManager: CameraManager,
    private val repository: CameraLensRepository,
    private val scanCache: CameraScanCache,
    private val deviceProfile: CameraDeviceProfile,
    private val streamValidator: CameraStreamValidator,
    private val stateFactory: CameraScanStateFactory,
    private val packageName: String
) {
    fun run(
        options: List<CameraLensOption>,
        isChineseUi: Boolean
    ): CameraScanResponse {
        val debugState = repository.buildDebugState(packageName)
        val candidates = debugState.probes
            .filter { probe -> probe.accessible && probe.cameraId.all(Char::isDigit) }
            .map { it.cameraId }
            .distinct()
        val unsafeIds = deviceProfile.unsafeCameraIds
        val results = mutableListOf<CameraScanCandidateResult>()
        val framesById = mutableMapOf<String, Int>()
        val streamVerifiedIds = mutableSetOf<String>()

        candidates.forEach { cameraId ->
            if (cameraId in unsafeIds) {
                results += CameraScanCandidateResult(
                    cameraId = cameraId,
                    safe = false,
                    verified = false,
                    label = null,
                    reason = "Blocked by current device safety profile.",
                    probe = null
                )
                return@forEach
            }

            val probe = CameraOpenProbe(cameraManager).run(
                CameraOpenProbeRequest(
                    cameraId = cameraId,
                    mode = CameraOpenProbeMode.TEXTURE_YUV,
                    width = 640,
                    height = 480,
                    fps = 30,
                    durationMs = 1_500,
                    openTimeoutMs = 2_500,
                    totalTimeoutMs = 5_500
                )
            )
            val verified = probe.opened && probe.configured && probe.repeating && probe.frames > 0 && probe.error == null
            val streamResult = if (verified) {
                streamValidator.validate(cameraId, options)
            } else {
                null
            }
            val streamVerified = streamResult?.verified == true
            if (streamVerified) {
                streamVerifiedIds += cameraId
                framesById[cameraId] = streamResult?.frames ?: probe.frames
            }
            results += CameraScanCandidateResult(
                cameraId = cameraId,
                safe = true,
                verified = streamVerified,
                label = null,
                reason = when {
                    streamVerified -> null
                    !verified -> probe.error ?: "No YUV frames observed during validation."
                    else -> streamResult?.error ?: "No MJPEG frames observed during stream validation."
                },
                probe = probe
            )
        }

        val verifiedIds = streamVerifiedIds.toList()
        val verifiedEntries = stateFactory.buildEntries(
            cameraIds = verifiedIds,
            sourceById = verifiedIds.associateWith { VerifiedCameraSource.SCAN.wireValue },
            framesById = framesById,
            noteById = verifiedIds.associateWith { "CameraStreamer MJPEG validation produced frames." },
            probes = debugState.probes,
            isChineseUi = isChineseUi
        )
        scanCache.save(stateFactory.currentDeviceKey(), verifiedEntries)
        val labelsById = verifiedEntries.associateBy { it.cameraId }
        val labeledResults = results.map { result ->
            val entry = labelsById[result.cameraId]
            if (entry == null) result else result.copy(label = entry.label)
        }
        return CameraScanResponse(
            ok = true,
            state = stateFactory.buildState(isChineseUi),
            results = labeledResults,
            error = null
        )
    }
}
