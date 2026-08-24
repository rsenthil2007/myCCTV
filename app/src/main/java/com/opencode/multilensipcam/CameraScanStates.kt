package com.opencode.multilensipcam

object CameraScanStates {
    fun build(
        deviceKey: String,
        profileName: String?,
        profileEntries: List<VerifiedCameraEntry>,
        cachedEntries: List<VerifiedCameraEntry>,
        unsafeCameraIds: Set<String>,
        isChineseUi: Boolean
    ): CameraScanState {
        val mergedSource = linkedMapOf<String, VerifiedCameraEntry>()
        profileEntries.forEach { entry -> mergedSource[entry.cameraId] = entry }
        cachedEntries.forEach { entry ->
            if (entry.cameraId !in mergedSource) mergedSource[entry.cameraId] = entry
        }
        val activeEntries = mergedSource.keys
            .filter { it !in unsafeCameraIds }
            .mapNotNull { id -> mergedSource[id] }

        return CameraScanState(
            deviceKey = deviceKey,
            profileName = profileName,
            profileEntries = VerifiedCameraEntries.localizeAndOrder(profileEntries, isChineseUi),
            cachedEntries = VerifiedCameraEntries.localizeAndOrder(cachedEntries, isChineseUi),
            activeEntries = VerifiedCameraEntries.localizeAndOrder(activeEntries, isChineseUi),
            unsafeCameraIds = unsafeCameraIds,
            lastScanAtMs = cachedEntries.maxOfOrNull { it.verifiedAtMs }?.takeIf { it > 0L }
        )
    }
}
