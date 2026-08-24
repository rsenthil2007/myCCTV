package com.opencode.multilensipcam

import java.io.OutputStream

class WebCameraDebugResponses(
    private val stateProvider: () -> WebControlState,
    private val cameraDebugProvider: () -> CameraDebugState,
    private val cameraScanStateProvider: () -> CameraScanState,
    private val cameraScanHandler: () -> CameraScanResponse,
    private val cameraScanCacheDeleteHandler: (String?) -> CameraScanCacheDeleteResponse,
    private val cameraOpenProbeHandler: (CameraOpenProbeRequest) -> CameraOpenProbeResult
) {
    fun writeDebugCameras(output: OutputStream) {
        WebHttpResponses.writeText(output, WebApiJson.cameraDebugState(cameraDebugProvider()).toString(), JSON_CONTENT_TYPE)
    }

    fun writeCameraScanState(output: OutputStream) {
        WebHttpResponses.writeText(output, WebApiJson.cameraScanState(cameraScanStateProvider()).toString(), JSON_CONTENT_TYPE)
    }

    fun writeCameraScan(output: OutputStream) {
        WebHttpResponses.writeText(output, WebApiJson.cameraScanResponse(cameraScanHandler()).toString(), JSON_CONTENT_TYPE)
    }

    fun writeCameraScanCacheDelete(output: OutputStream, query: Map<String, String>) {
        val response = cameraScanCacheDeleteHandler(query["camera"] ?: query["cameraId"])
        WebHttpResponses.writeText(output, WebApiJson.cameraScanCacheDeleteResponse(response).toString(), JSON_CONTENT_TYPE)
    }

    fun writeCameraOpenProbe(output: OutputStream, query: Map<String, String>) {
        val state = stateProvider()
        if (state.streaming) {
            WebHttpResponses.writeText(
                output,
                WebApiJson.error("Stop streaming before running camera open probes.").toString(),
                JSON_CONTENT_TYPE
            )
            return
        }
        val request = WebApiQueryParser.cameraOpenProbeRequest(query)
        val result = cameraOpenProbeHandler(request)
        WebHttpResponses.writeText(output, WebApiJson.cameraOpenProbeResult(result).toString(), JSON_CONTENT_TYPE)
    }

    fun writeCameraOpenMatrix(output: OutputStream, query: Map<String, String>) {
        val state = stateProvider()
        if (state.streaming) {
            WebHttpResponses.writeText(
                output,
                WebApiJson.error("Stop streaming before running camera open probes.").toString(),
                JSON_CONTENT_TYPE
            )
            return
        }
        val plan = WebCameraOpenMatrixRequests.resolve(query, cameraDebugProvider())
        val results = mutableListOf<CameraOpenProbeResult>()
        plan.cameraIds.forEach { id ->
            plan.modes.forEach { mode ->
                results += cameraOpenProbeHandler(plan.baseRequest.copy(cameraId = id, mode = mode))
            }
        }
        WebHttpResponses.writeText(output, WebApiJson.cameraOpenMatrix(plan.cameraIds, plan.modes, results).toString(), JSON_CONTENT_TYPE)
    }

    private companion object {
        const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
    }
}
