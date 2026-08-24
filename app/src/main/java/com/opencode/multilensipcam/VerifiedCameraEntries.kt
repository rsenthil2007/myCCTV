package com.opencode.multilensipcam

import android.os.Build
import java.util.Locale
import kotlin.math.abs

object VerifiedCameraEntries {
    fun currentDeviceKey(): String {
        return listOf(
            Build.MANUFACTURER,
            Build.BRAND,
            Build.MODEL,
            Build.DEVICE,
            Build.PRODUCT,
            Build.FINGERPRINT
        ).joinToString("|")
    }

    fun build(
        cameraIds: Iterable<String>,
        sourceById: Map<String, String>,
        framesById: Map<String, Int>,
        noteById: Map<String, String?>,
        probes: List<CameraDebugProbe>,
        unsafeCameraIds: Set<String>,
        isChineseUi: Boolean
    ): List<VerifiedCameraEntry> {
        val ids = cameraIds.distinct().filter { it !in unsafeCameraIds }
        if (ids.isEmpty()) return emptyList()
        val probesById = probes.associateBy { it.cameraId }
        val mainEquivalentFocalLength = probes
            .asSequence()
            .filter { it.accessible && it.lensFacing == "Back" }
            .mapNotNull { it.approximate35mmFocalLengths.minOrNull() }
            .minByOrNull { abs(it - 24f) }
        val rearOrder = ids
            .mapNotNull { id -> probesById[id] }
            .filter { it.lensFacing == "Back" }
            .sortedWith(compareBy<CameraDebugProbe> { relativeZoomFor(it, mainEquivalentFocalLength) ?: Float.MAX_VALUE }
                .thenBy { it.cameraId.toIntOrNull() ?: Int.MAX_VALUE })
            .mapIndexed { index, probe -> probe.cameraId to index + 1 }
            .toMap()

        return localizeAndOrder(
            ids.mapNotNull { id ->
                val probe = probesById[id]?.takeIf { it.accessible } ?: return@mapNotNull null
                val zoomLabel = relativeZoomFor(probe, mainEquivalentFocalLength)?.let(::formatRelativeZoom)
                VerifiedCameraEntry(
                    cameraId = id,
                    label = label(id, probe.lensFacing, zoomLabel, rearOrder[id], isChineseUi),
                    lensFacing = probe.lensFacing,
                    zoomLabel = zoomLabel,
                    source = sourceById[id] ?: VerifiedCameraSource.SCAN.wireValue,
                    verifiedAtMs = System.currentTimeMillis(),
                    frames = framesById[id] ?: 0,
                    note = noteById[id]
                )
            },
            isChineseUi
        )
    }

    fun localizeAndOrder(entries: List<VerifiedCameraEntry>, isChineseUi: Boolean): List<VerifiedCameraEntry> {
        val ordered = order(entries)
        val rearOrder = ordered
            .filter { it.lensFacing == "Back" }
            .mapIndexed { index, entry -> entry.cameraId to index + 1 }
            .toMap()
        return ordered.map { entry ->
            entry.copy(label = label(entry.cameraId, entry.lensFacing, entry.zoomLabel, rearOrder[entry.cameraId], isChineseUi))
        }
    }

    private fun order(entries: List<VerifiedCameraEntry>): List<VerifiedCameraEntry> {
        return entries.sortedWith(
            compareBy<VerifiedCameraEntry> { entry ->
                when (entry.lensFacing) {
                    "Back" -> 0
                    "External" -> 1
                    "Front" -> 2
                    else -> 3
                }
            }.thenBy { entry -> entry.zoomLabel?.removePrefix("x")?.toFloatOrNull() ?: Float.MAX_VALUE }
                .thenBy { entry -> entry.cameraId.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { entry -> entry.cameraId }
        )
    }

    private fun label(
        cameraId: String,
        lensFacing: String?,
        zoomLabel: String?,
        rearIndex: Int?,
        isChineseUi: Boolean
    ): String {
        val baseLabel = when (lensFacing) {
            "Front" -> if (isChineseUi) "\u524d\u6444" else "Front"
            "Back" -> if (isChineseUi) "\u540e\u6444 ${rearIndex ?: 1}" else "Rear ${rearIndex ?: 1}"
            "External" -> if (isChineseUi) "\u5916\u63a5\u6444\u50cf\u5934" else "External camera"
            else -> if (isChineseUi) "\u6444\u50cf\u5934" else "Camera"
        }
        return buildString {
            append(baseLabel)
            if (zoomLabel != null && lensFacing == "Back") {
                append(" ")
                append(zoomLabel)
            }
            append(" (ID ")
            append(cameraId)
            append(")")
        }
    }

    private fun relativeZoomFor(probe: CameraDebugProbe, mainEquivalentFocalLength: Float?): Float? {
        val equivalentFocalLength = probe.approximate35mmFocalLengths.minOrNull()
        return if (
            equivalentFocalLength != null &&
            mainEquivalentFocalLength != null &&
            mainEquivalentFocalLength > 0f &&
            probe.lensFacing == "Back"
        ) {
            equivalentFocalLength / mainEquivalentFocalLength
        } else {
            null
        }
    }

    private fun formatRelativeZoom(value: Float): String {
        return "x${"%.1f".format(Locale.US, value)}"
    }
}
