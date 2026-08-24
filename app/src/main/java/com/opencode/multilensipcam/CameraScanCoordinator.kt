package com.opencode.multilensipcam

class CameraScanCoordinator(
    private val scanCache: CameraScanCache,
    private val stateFactory: CameraScanStateFactory,
    private val scanRunner: CameraScanRunner,
    private val optionsProvider: () -> List<CameraLensOption>,
    private val isChineseUi: () -> Boolean,
    private val isStreaming: () -> Boolean,
    private val selectedControlKey: () -> String?,
    private val refreshCameraOptionsPreservingSelection: () -> Unit,
    private val refreshControls: (Boolean) -> Unit,
    private val stopStreaming: (String, Boolean) -> Unit,
    private val runBlockingOnUiThread: (() -> Unit) -> Unit
) {
    @Volatile
    var inProgress: Boolean = false
        private set

    private val lock = Any()
    private var cachedState: CameraScanState? = null

    fun buildState(): CameraScanState {
        cachedState?.let { return it }
        return stateFactory.buildState(isChineseUi()).also { cachedState = it }
    }

    fun runScan(): CameraScanResponse {
        synchronized(lock) {
            if (inProgress) {
                return CameraScanResponse(
                    ok = false,
                    state = buildState(),
                    results = emptyList(),
                    error = "Camera scan already in progress."
                )
            }
            if (isStreaming()) {
                return CameraScanResponse(
                    ok = false,
                    state = buildState(),
                    results = emptyList(),
                    error = "Stop streaming before scanning cameras."
                )
            }
            inProgress = true
        }

        try {
            val response = scanRunner.run(optionsProvider(), isChineseUi())
            cachedState = null
            runBlockingOnUiThread {
                refreshCameraOptionsPreservingSelection()
                refreshControls(false)
            }
            return response
        } finally {
            synchronized(lock) {
                inProgress = false
            }
        }
    }

    fun deleteCache(cameraId: String?): CameraScanCacheDeleteResponse {
        val deviceKey = currentDeviceKey()
        val deletedIds = if (cameraId.isNullOrBlank()) {
            scanCache.clear(deviceKey)
        } else {
            if (scanCache.delete(deviceKey, cameraId)) listOf(cameraId) else emptyList()
        }
        cachedState = null

        if (deletedIds.isNotEmpty()) {
            val previousSelectedKey = selectedControlKey()
            runBlockingOnUiThread {
                refreshCameraOptionsPreservingSelection()
                if (previousSelectedKey != null && previousSelectedKey in deletedIds) {
                    refreshControls(true)
                    if (isStreaming()) {
                        stopStreaming("Camera scan cache changed", true)
                    }
                }
            }
        }

        return CameraScanCacheDeleteResponse(
            ok = true,
            state = buildState(),
            deletedCameraIds = deletedIds,
            error = null
        )
    }

    fun localizedEntries(entries: List<VerifiedCameraEntry>): List<VerifiedCameraEntry> {
        return stateFactory.localizedEntries(entries, isChineseUi())
    }

    fun currentDeviceKey(): String {
        return stateFactory.currentDeviceKey()
    }

    fun invalidateState() {
        cachedState = null
    }
}
