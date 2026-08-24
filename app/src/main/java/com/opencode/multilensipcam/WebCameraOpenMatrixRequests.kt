package com.opencode.multilensipcam

data class WebCameraOpenMatrixRequestPlan(
    val cameraIds: List<String>,
    val modes: List<CameraOpenProbeMode>,
    val baseRequest: CameraOpenProbeRequest
)

object WebCameraOpenMatrixRequests {
    fun resolve(
        query: Map<String, String>,
        debugState: CameraDebugState
    ): WebCameraOpenMatrixRequestPlan {
        val ids = query["cameras"]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.take(8)
            ?: debugState.probes.map { it.cameraId }.take(8)
        val modes = query["modes"]
            ?.split(",")
            ?.map { CameraOpenProbeMode.from(it.trim()) }
            ?.distinct()
            ?: listOf(
                CameraOpenProbeMode.OPEN_ONLY,
                CameraOpenProbeMode.TEXTURE,
                CameraOpenProbeMode.YUV,
                CameraOpenProbeMode.TEXTURE_YUV
            )
        return WebCameraOpenMatrixRequestPlan(
            cameraIds = ids,
            modes = modes,
            baseRequest = WebApiQueryParser.cameraOpenProbeRequest(query)
        )
    }
}
