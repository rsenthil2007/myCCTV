package com.opencode.multilensipcam

class CameraScanStateFactory(
    private val repository: CameraLensRepository,
    private val scanCache: CameraScanCache,
    private val deviceProfile: CameraDeviceProfile,
    private val packageName: String
) {
    fun buildState(isChineseUi: Boolean): CameraScanState {
        val deviceKey = currentDeviceKey()
        val cachedEntries = scanCache.load(deviceKey)
        val profileEntries = buildEntries(
            cameraIds = deviceProfile.usableCameraIds.toList(),
            sourceById = deviceProfile.usableCameraIds.associateWith { VerifiedCameraSource.PROFILE.wireValue },
            framesById = emptyMap(),
            noteById = deviceProfile.usableCameraIds.associateWith { deviceProfile.name ?: "Known safe profile" },
            isChineseUi = isChineseUi
        )
        return CameraScanStates.build(
            deviceKey = deviceKey,
            profileName = deviceProfile.name,
            profileEntries = profileEntries,
            cachedEntries = cachedEntries,
            unsafeCameraIds = deviceProfile.unsafeCameraIds,
            isChineseUi = isChineseUi
        )
    }

    fun buildEntries(
        cameraIds: Iterable<String>,
        sourceById: Map<String, String>,
        framesById: Map<String, Int>,
        noteById: Map<String, String?>,
        probes: List<CameraDebugProbe>? = null,
        isChineseUi: Boolean
    ): List<VerifiedCameraEntry> {
        return VerifiedCameraEntries.build(
            cameraIds = cameraIds,
            sourceById = sourceById,
            framesById = framesById,
            noteById = noteById,
            probes = probes ?: repository.buildDebugState(packageName).probes,
            unsafeCameraIds = deviceProfile.unsafeCameraIds,
            isChineseUi = isChineseUi
        )
    }

    fun localizedEntries(
        entries: List<VerifiedCameraEntry>,
        isChineseUi: Boolean
    ): List<VerifiedCameraEntry> {
        return VerifiedCameraEntries.localizeAndOrder(entries, isChineseUi)
    }

    fun currentDeviceKey(): String {
        return VerifiedCameraEntries.currentDeviceKey()
    }
}
