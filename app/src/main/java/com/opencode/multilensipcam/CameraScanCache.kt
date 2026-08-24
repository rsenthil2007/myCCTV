package com.opencode.multilensipcam

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class CameraScanCache(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(deviceKey: String): List<VerifiedCameraEntry> {
        val raw = preferences.getString(cacheKey(deviceKey), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        VerifiedCameraEntry(
                            cameraId = item.optString("cameraId"),
                            label = item.optString("label"),
                            lensFacing = item.optString("lensFacing", null),
                            zoomLabel = item.optString("zoomLabel", null),
                            source = item.optString("source", VerifiedCameraSource.SCAN.wireValue),
                            verifiedAtMs = item.optLong("verifiedAtMs", 0L),
                            frames = item.optInt("frames", 0),
                            note = item.optString("note", null)
                        )
                    )
                }
            }.filter { it.cameraId.isNotBlank() && it.label.isNotBlank() }
        }.getOrElse { emptyList() }
    }

    fun save(deviceKey: String, entries: List<VerifiedCameraEntry>) {
        val array = JSONArray(entries.distinctBy { it.cameraId }.map { entry ->
            JSONObject().apply {
                put("cameraId", entry.cameraId)
                put("label", entry.label)
                put("lensFacing", entry.lensFacing)
                put("zoomLabel", entry.zoomLabel)
                put("source", entry.source)
                put("verifiedAtMs", entry.verifiedAtMs)
                put("frames", entry.frames)
                put("note", entry.note)
            }
        })
        preferences.edit().putString(cacheKey(deviceKey), array.toString()).apply()
    }

    fun delete(deviceKey: String, cameraId: String): Boolean {
        val current = load(deviceKey)
        val remaining = current.filterNot { it.cameraId == cameraId }
        if (remaining.size == current.size) return false
        save(deviceKey, remaining)
        return true
    }

    fun clear(deviceKey: String): List<String> {
        val deletedIds = load(deviceKey).map { it.cameraId }
        preferences.edit().remove(cacheKey(deviceKey)).apply()
        return deletedIds
    }

    private fun cacheKey(deviceKey: String): String = "scan:$deviceKey"

    companion object {
        private const val PREFS_NAME = "camera_scan_cache"
    }
}

object CameraDeviceProfileLibrary {
    fun profileForCurrentDevice(): CameraDeviceProfile {
        val identity = listOf(
            Build.MANUFACTURER,
            Build.BRAND,
            Build.MODEL,
            Build.DEVICE,
            Build.PRODUCT,
            Build.FINGERPRINT
        ).joinToString(" ").lowercase(Locale.US)

        return when {
            isIqoo13(identity) -> CameraDeviceProfile(
                name = "iQOO 13 safe auxiliary camera profile",
                usableCameraIds = setOf("3", "4"),
                unsafeCameraIds = setOf("2")
            )
            else -> CameraDeviceProfile.EMPTY
        }
    }

    private fun isIqoo13(identity: String): Boolean {
        return "v2408" in identity || ("iqoo" in identity && "13" in identity)
    }
}

data class CameraDeviceProfile(
    val name: String?,
    val usableCameraIds: Set<String>,
    val unsafeCameraIds: Set<String>
) {
    companion object {
        val EMPTY = CameraDeviceProfile(
            name = null,
            usableCameraIds = emptySet(),
            unsafeCameraIds = emptySet()
        )
    }
}

data class CameraScanState(
    val deviceKey: String,
    val profileName: String?,
    val profileEntries: List<VerifiedCameraEntry>,
    val cachedEntries: List<VerifiedCameraEntry>,
    val activeEntries: List<VerifiedCameraEntry>,
    val unsafeCameraIds: Set<String>,
    val lastScanAtMs: Long?
)

data class CameraScanResponse(
    val ok: Boolean,
    val state: CameraScanState,
    val results: List<CameraScanCandidateResult>,
    val error: String?
)

data class CameraScanCacheDeleteResponse(
    val ok: Boolean,
    val state: CameraScanState,
    val deletedCameraIds: List<String>,
    val error: String?
)

data class CameraScanCandidateResult(
    val cameraId: String,
    val safe: Boolean,
    val verified: Boolean,
    val label: String?,
    val reason: String?,
    val probe: CameraOpenProbeResult?
)

data class VerifiedCameraEntry(
    val cameraId: String,
    val label: String,
    val lensFacing: String?,
    val zoomLabel: String?,
    val source: String,
    val verifiedAtMs: Long,
    val frames: Int,
    val note: String?
)

enum class VerifiedCameraSource(val wireValue: String) {
    PROFILE("profile"),
    SCAN("scan")
}
