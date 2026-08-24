package com.opencode.multilensipcam

import android.hardware.camera2.CameraCharacteristics

object CameraOptionPresentation {
    fun buildSelectableOptions(
        repository: CameraLensRepository,
        publicOptions: List<CameraLensOption>,
        activeEntries: List<VerifiedCameraEntry>,
        isChineseUi: Boolean
    ): List<CameraLensOption> {
        val verifiedEntries = VerifiedCameraEntries.localizeAndOrder(activeEntries, isChineseUi)
        val ordered = orderCameraOptions(
            repository = repository,
            publicOptions = publicOptions,
            cameraOptions = repository.mergeVerifiedDirectOptions(
                publicOptions = publicOptions,
                verifiedEntries = verifiedEntries
            ).map { option ->
                localizeOption(repository, option, verifiedEntries, isChineseUi)
            }
        )
        return if (verifiedEntries.isNotEmpty()) {
            ordered
        } else {
            productFacingPublicCameraOptions(ordered, isChineseUi)
        }
    }

    fun localizeOption(
        repository: CameraLensRepository,
        option: CameraLensOption,
        verifiedEntries: List<VerifiedCameraEntry>,
        isChineseUi: Boolean
    ): CameraLensOption {
        val controlKey = repository.controlKeyFor(option)
        val directEntry = verifiedEntries.firstOrNull { it.cameraId == controlKey }
        if (directEntry != null) {
            return option.copy(label = directEntry.label)
        }
        val cameraType = when (option.lensFacing) {
            CameraCharacteristics.LENS_FACING_FRONT -> localizedText(isChineseUi, "Front", "\u524d\u6444")
            CameraCharacteristics.LENS_FACING_BACK -> localizedText(isChineseUi, "Rear", "\u540e\u6444")
            CameraCharacteristics.LENS_FACING_EXTERNAL -> localizedText(isChineseUi, "External camera", "\u5916\u63a5\u6444\u50cf\u5934")
            else -> localizedText(isChineseUi, "Camera", "\u6444\u50cf\u5934")
        }
        val idLabel = if (option.physicalCameraId != null) {
            "physical ${option.logicalCameraId}:${option.physicalCameraId}"
        } else {
            "logical ${option.logicalCameraId}"
        }
        return option.copy(label = "$cameraType $idLabel")
    }

    private fun orderCameraOptions(
        repository: CameraLensRepository,
        publicOptions: List<CameraLensOption>,
        cameraOptions: List<CameraLensOption>
    ): List<CameraLensOption> {
        return cameraOptions.sortedWith(
            compareBy<CameraLensOption> { option ->
                when (option.lensFacing) {
                    CameraCharacteristics.LENS_FACING_BACK -> 0
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> 1
                    CameraCharacteristics.LENS_FACING_FRONT -> 2
                    else -> 3
                }
            }.thenBy { option -> cameraOptionZoomSortValue(repository, publicOptions, option) }
                .thenBy { option -> option.logicalCameraId.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { option -> option.logicalCameraId }
                .thenBy { option -> option.physicalCameraId?.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { option -> option.physicalCameraId ?: "" }
        )
    }

    private fun productFacingPublicCameraOptions(
        cameraOptions: List<CameraLensOption>,
        isChineseUi: Boolean
    ): List<CameraLensOption> {
        var rearIndex = 0
        var frontIndex = 0
        var externalIndex = 0
        return cameraOptions.map { option ->
            val label = when (option.lensFacing) {
                CameraCharacteristics.LENS_FACING_BACK -> {
                    rearIndex += 1
                    localizedText(isChineseUi, "Rear $rearIndex", "\u540e\u6444 $rearIndex")
                }
                CameraCharacteristics.LENS_FACING_FRONT -> {
                    frontIndex += 1
                    if (frontIndex == 1) {
                        localizedText(isChineseUi, "Front", "\u524d\u6444")
                    } else {
                        localizedText(isChineseUi, "Front $frontIndex", "\u524d\u6444 $frontIndex")
                    }
                }
                CameraCharacteristics.LENS_FACING_EXTERNAL -> {
                    externalIndex += 1
                    localizedText(isChineseUi, "External $externalIndex", "\u5916\u63a5\u6444\u50cf\u5934 $externalIndex")
                }
                else -> localizedText(isChineseUi, "Camera", "\u6444\u50cf\u5934")
            }
            option.copy(label = label)
        }
    }

    private fun cameraOptionZoomSortValue(
        repository: CameraLensRepository,
        publicOptions: List<CameraLensOption>,
        option: CameraLensOption
    ): Float {
        val labelZoom = Regex("""x([0-9]+(?:\.[0-9]+)?)""").find(option.label)?.groupValues?.getOrNull(1)?.toFloatOrNull()
        if (labelZoom != null) return labelZoom
        return runCatching {
            val capabilities = repository.getCapabilities(option)
            repository.inferNativeZoomRatio(option, capabilities, publicOptions)
        }.getOrNull() ?: Float.MAX_VALUE
    }

    private fun localizedText(isChineseUi: Boolean, english: String, chinese: String): String {
        return if (isChineseUi) chinese else english
    }
}
