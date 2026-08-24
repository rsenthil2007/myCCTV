package com.opencode.multilensipcam

import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Range
import android.util.Size
import kotlin.math.abs

class CameraLensRepository(private val cameraManager: CameraManager) {

    fun loadOptions(): List<CameraLensOption> {
        val result = mutableListOf<CameraLensOption>()
        val publicCameraIds = cameraManager.cameraIdList.toSet()

        for (cameraId in publicCameraIds.sorted()) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)

            result += CameraLensOption(
                logicalCameraId = cameraId,
                physicalCameraId = null,
                label = "${displayFacingLabel(characteristics)} logical $cameraId",
                lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    ?: CameraCharacteristics.LENS_FACING_BACK
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                characteristics.physicalCameraIds
                    .sorted()
                    .forEach { physicalId ->
                        result += CameraLensOption(
                            logicalCameraId = cameraId,
                            physicalCameraId = physicalId,
                            label = "${displayFacingLabel(characteristics)} physical $cameraId:$physicalId",
                            lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                                ?: CameraCharacteristics.LENS_FACING_BACK
                        )
                    }
            }
        }

        return result.distinctBy { controlKeyFor(it) }
    }

    fun mergeVerifiedDirectOptions(
        publicOptions: List<CameraLensOption>,
        verifiedEntries: List<VerifiedCameraEntry>
    ): List<CameraLensOption> {
        if (!BuildConfig.DEBUG || verifiedEntries.isEmpty()) return publicOptions
        val verifiedOptions = verifiedEntries.mapNotNull { entry ->
            directOptionFor(entry.cameraId, entry.label)
        }
        return verifiedOptions
            .distinctBy { controlKeyFor(it) }
            .ifEmpty { publicOptions }
    }

    fun buildDebugState(packageName: String): CameraDebugState {
        val publicCameraIds = cameraManager.cameraIdList.sorted()
        val candidates = directProbeCameraIds(publicCameraIds.toSet())
        val probes = candidates.map { cameraId ->
            val characteristicsResult = runCatching {
                cameraManager.getCameraCharacteristics(cameraId)
            }
            val characteristics = characteristicsResult.getOrNull()
            if (characteristics == null) {
                CameraDebugProbe(
                    cameraId = cameraId,
                    isPublic = cameraId in publicCameraIds,
                    accessible = false,
                    label = null,
                    lensFacing = null,
                    lensFacingValue = null,
                    sensorOrientation = null,
                    hardwareLevel = null,
                    focalLengthsMm = emptyList(),
                    approximate35mmFocalLengths = emptyList(),
                    inferredLensRole = null,
                    sensorPhysicalSizeMm = null,
                    activeArraySize = null,
                    zoomRatioRange = null,
                    maxDigitalZoom = null,
                    physicalCameraIds = emptyList(),
                    requestAvailableCapabilities = emptyList(),
                    hasConstrainedHighSpeedVideo = false,
                    highSpeedVideoConfigurations = emptyList(),
                    surfaceTextureSizes = emptyList(),
                    yuvSizes = emptyList(),
                    jpegSizes = emptyList(),
                    fpsRanges = emptyList(),
                    error = characteristicsResult.exceptionOrNull()?.let(::errorLabel)
                )
            } else {
                val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val requestCapabilities = characteristics
                    .get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    ?.toList()
                    .orEmpty()
                val highSpeedVideoConfigurations = highSpeedVideoConfigurations(streamMap)
                val fpsRanges = characteristics
                    .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                    ?.distinct()
                    ?.sortedWith(compareBy<Range<Int>> { it.upper }.thenByDescending { it.lower })
                    ?.map { "${it.lower}-${it.upper}" }
                    ?: emptyList()
                val physicalIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    characteristics.physicalCameraIds.sorted()
                } else {
                    emptyList()
                }
                val facing = lensFacingLabel(characteristics)
                val focalLengths = characteristics
                    .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.distinct()
                    ?.sorted()
                    ?: emptyList()
                val sensorPhysicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                val approx35mm = approximate35mmFocalLengths(focalLengths, sensorPhysicalSize?.width)
                val activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                val zoomRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let {
                        "${formatFloat(it.lower)}-${formatFloat(it.upper)}"
                    }
                } else {
                    null
                }

                CameraDebugProbe(
                    cameraId = cameraId,
                    isPublic = cameraId in publicCameraIds,
                    accessible = true,
                    label = "$facing camera $cameraId",
                    lensFacing = facing,
                    lensFacingValue = characteristics.get(CameraCharacteristics.LENS_FACING),
                    sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION),
                    hardwareLevel = characteristics
                        .get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                        ?.let(::hardwareLevelLabel),
                    focalLengthsMm = focalLengths,
                    approximate35mmFocalLengths = approx35mm,
                    inferredLensRole = inferLensRole(
                        lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING),
                        approximate35mmFocalLengths = approx35mm,
                        zoomRatioRange = zoomRange
                    ),
                    sensorPhysicalSizeMm = sensorPhysicalSize?.let {
                        "${formatFloat(it.width)}x${formatFloat(it.height)}"
                    },
                    activeArraySize = activeArray?.let {
                        "${it.width()}x${it.height()}"
                    },
                    zoomRatioRange = zoomRange,
                    maxDigitalZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM),
                    physicalCameraIds = physicalIds,
                    requestAvailableCapabilities = requestCapabilities.map(::requestCapabilityLabel).sorted(),
                    hasConstrainedHighSpeedVideo =
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO in requestCapabilities,
                    highSpeedVideoConfigurations = highSpeedVideoConfigurations,
                    surfaceTextureSizes = streamMap
                        ?.getOutputSizes(SurfaceTexture::class.java)
                        ?.toList()
                        ?.formatSizes()
                        ?: emptyList(),
                    yuvSizes = streamMap
                        ?.getOutputSizes(ImageFormat.YUV_420_888)
                        ?.toList()
                        ?.formatSizes()
                        ?: emptyList(),
                    jpegSizes = streamMap
                        ?.getOutputSizes(ImageFormat.JPEG)
                        ?.toList()
                        ?.formatSizes()
                        ?: emptyList(),
                    fpsRanges = fpsRanges,
                    error = null
                )
            }
        }

        return CameraDebugState(
            packageName = packageName,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            hardware = Build.HARDWARE,
            fingerprint = Build.FINGERPRINT,
            sdkInt = Build.VERSION.SDK_INT,
            publicCameraIds = publicCameraIds,
            probes = probes
        )
    }

    fun getCapabilities(option: CameraLensOption): CameraCapabilities {
        val characteristics = cameraManager.getCameraCharacteristics(option.logicalCameraId)
        val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        val textureSizes = streamMap
            ?.getOutputSizes(SurfaceTexture::class.java)
            ?.toSet()
            ?: emptySet()
        val yuvSizes = streamMap
            ?.getOutputSizes(ImageFormat.YUV_420_888)
            ?.toSet()
            ?: emptySet()

        val commonSizes = textureSizes.intersect(yuvSizes)
            .ifEmpty { yuvSizes.ifEmpty { textureSizes } }
            .sortedWith(
                compareByDescending<Size> { it.width.toLong() * it.height.toLong() }
                    .thenByDescending { preferredAspectScore(it) }
            )

        val zoomRatioRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        } else {
            characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                ?.takeIf { it > 1f }
                ?.let { maxZoom -> Range(1f, maxZoom) }
        }

        val fpsRanges = characteristics
            .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.distinct()
            ?.filter { it.upper >= it.lower && it.upper > 0 }
            ?.sortedWith(compareBy<Range<Int>> { it.upper }.thenByDescending { it.lower })
            ?: emptyList()

        val requestCapabilities = characteristics
            .get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.toList()
            .orEmpty()
        val highSpeedVideoFpsRanges = highSpeedVideoFpsRanges(streamMap)

        val exposureCompensationRange = characteristics
            .get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
            ?.takeUnless { it.lower == 0 && it.upper == 0 }

        val exposureStep = characteristics
            .get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
            ?.let { rational ->
                if (rational.denominator == 0) 1f else rational.numerator.toFloat() / rational.denominator.toFloat()
            }
            ?: 1f

        val focusModes = characteristics
            .get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
            ?.toList()
            ?: emptyList()

        val minimumFocusDistance = characteristics
            .get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
            ?: 0f

        val focalLengths = characteristics
            .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.distinct()
            ?.sorted()
            ?: emptyList()

        val sensorOrientation = characteristics
            .get(CameraCharacteristics.SENSOR_ORIENTATION)
            ?: 90

        val videoStabilizationModes = characteristics
            .get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            ?.toList()
            ?: emptyList()

        val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

        return CameraCapabilities(
            outputSizes = commonSizes,
            zoomRatioRange = zoomRatioRange,
            fpsRanges = fpsRanges,
            hasConstrainedHighSpeedVideo =
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO in requestCapabilities,
            highSpeedVideoFpsRanges = highSpeedVideoFpsRanges,
            exposureCompensationRange = exposureCompensationRange,
            exposureCompensationStep = exposureStep,
            focusModes = focusModes,
            minimumFocusDistance = minimumFocusDistance,
            focalLengths = focalLengths,
            sensorOrientation = sensorOrientation,
            videoStabilizationModes = videoStabilizationModes,
            flashAvailable = flashAvailable
        )
    }

    fun listSelectableSizes(capabilities: CameraCapabilities): List<Size> {
        val sizes = capabilities.outputSizes.distinct().sortedByDescending { it.width.toLong() * it.height.toLong() }
        if (sizes.isEmpty()) return listOf(Size(1280, 720))

        val result = linkedSetOf<Size>()
        findExactSize(sizes, 3840, 2160)?.let(result::add) ?: sizes.firstOrNull()?.let(result::add)
        findExactSize(sizes, 1920, 1080)?.let(result::add) ?: firstAtOrBelowArea(sizes, 2_073_600)?.let(result::add)
        findExactSize(sizes, 1280, 720)?.let(result::add) ?: firstAtOrBelowArea(sizes, 921_600)?.let(result::add)
        findExactSize(sizes, 854, 480)?.let(result::add) ?: firstAtOrBelowArea(sizes, 409_920)?.let(result::add)
        result += sizes.last()

        return result.sortedByDescending { it.width.toLong() * it.height.toLong() }
    }

    private fun findExactSize(sizes: List<Size>, width: Int, height: Int): Size? {
        return sizes.firstOrNull { it.width == width && it.height == height }
    }

    private fun firstAtOrBelowArea(sizes: List<Size>, area: Int): Size? {
        return sizes.firstOrNull { it.width.toLong() * it.height.toLong() <= area.toLong() }
    }

    fun chooseRecommendedSize(capabilities: CameraCapabilities): Size {
        val selectable = listSelectableSizes(capabilities)
        return selectable.firstOrNull { it.width == 1920 && it.height == 1080 }
            ?: selectable.firstOrNull { it.width == 1280 && it.height == 720 }
            ?: selectable.firstOrNull()
            ?: Size(1280, 720)
    }

    fun chooseMjpegSize(capabilities: CameraCapabilities, preferredSize: Size): Size {
        val preferredArea = preferredSize.width.toLong() * preferredSize.height.toLong()
        if (preferredArea <= 1_280L * 720L) {
            return preferredSize
        }

        val preferredRatio = preferredSize.width.toFloat() / preferredSize.height.toFloat()
        val sameAspectCandidate = capabilities.outputSizes
            .distinct()
            .filter { size ->
                val area = size.width.toLong() * size.height.toLong()
                area <= 1_280L * 720L &&
                    abs((size.width.toFloat() / size.height.toFloat()) - preferredRatio) < 0.03f
            }
            .maxByOrNull { it.width.toLong() * it.height.toLong() }
        if (sameAspectCandidate != null) {
            return sameAspectCandidate
        }

        return capabilities.outputSizes
            .distinct()
            .filter { size -> size.width.toLong() * size.height.toLong() <= 1_280L * 720L }
            .maxByOrNull { it.width.toLong() * it.height.toLong() }
            ?: preferredSize
    }

    fun listTargetFpsOptions(capabilities: CameraCapabilities): List<Int> {
        val options = capabilities.fpsRanges
            .map { it.upper }
            .filter { it in 1..60 }
            .distinct()
            .toMutableSet()

        if (capabilities.fpsRanges.any { it.upper >= 60 }) {
            options += 60
        }
        if (
            capabilities.hasConstrainedHighSpeedVideo &&
            capabilities.highSpeedVideoFpsRanges.values.flatten().any { it.containsTargetFps(60) }
        ) {
            options += 60
        }

        return if (options.isEmpty()) listOf(15, 20, 30) else options.sorted()
    }

    fun chooseRecommendedFps(capabilities: CameraCapabilities): Int {
        val options = listTargetFpsOptions(capabilities)
        return when {
            30 in options -> 30
            20 in options -> 20
            15 in options -> 15
            else -> options.first()
        }
    }

    fun chooseHighestFps(capabilities: CameraCapabilities): Int {
        return capabilities.fpsRanges.maxOfOrNull { it.upper }
            ?: listTargetFpsOptions(capabilities).maxOrNull()
            ?: chooseRecommendedFps(capabilities)
    }

    fun supportsHighSpeedVideoFps(capabilities: CameraCapabilities, size: Size, targetFps: Int): Boolean {
        return chooseHighSpeedVideoFpsRange(capabilities, size, targetFps) != null
    }

    fun chooseHighSpeedVideoFpsRange(
        capabilities: CameraCapabilities,
        size: Size,
        targetFps: Int
    ): Range<Int>? {
        if (!capabilities.hasConstrainedHighSpeedVideo) {
            return null
        }
        val ranges = capabilities.highSpeedVideoFpsRanges[size].orEmpty()
        val fixedRange = ranges.firstOrNull { range ->
            range.lower == targetFps && range.upper == targetFps
        }
        if (fixedRange != null) {
            return fixedRange
        }

        return ranges
            .filter { range -> range.containsTargetFps(targetFps) }
            .minWithOrNull(
                compareBy<Range<Int>> { range -> abs(range.lower - targetFps) }
                    .thenBy { range -> range.upper }
            )
    }

    fun chooseTargetFpsRange(capabilities: CameraCapabilities, targetFps: Int): Range<Int>? {
        val exact = capabilities.fpsRanges
            .filter { it.upper == targetFps }
            .maxByOrNull { it.lower }
        if (exact != null) {
            return exact
        }

        return capabilities.fpsRanges.minByOrNull { range ->
            abs(range.upper - targetFps) * 100 + abs(range.lower - targetFps)
        }
    }

    fun chooseUnlimitedFpsRange(capabilities: CameraCapabilities): Range<Int>? {
        return capabilities.fpsRanges
            .maxWithOrNull(compareBy<Range<Int>> { it.upper }.thenByDescending { it.lower })
    }

    fun hasManualFocus(capabilities: CameraCapabilities): Boolean {
        return capabilities.minimumFocusDistance > 0f &&
            CameraMetadata.CONTROL_AF_MODE_OFF in capabilities.focusModes
    }

    fun sizeLabel(size: Size): String = "${size.width}x${size.height}"

    fun findSizeByLabel(capabilities: CameraCapabilities, label: String?): Size? {
        if (label.isNullOrBlank()) return null
        return listSelectableSizes(capabilities).firstOrNull { sizeLabel(it) == label }
    }

    fun findSupportedOrNearestSize(capabilities: CameraCapabilities, width: Int, height: Int): Size? {
        val requestedArea = width.toLong() * height.toLong()
        return capabilities.outputSizes
            .distinct()
            .minWithOrNull(
                compareBy<Size> {
                    kotlin.math.abs((it.width.toLong() * it.height.toLong()) - requestedArea)
                }.thenBy {
                    kotlin.math.abs(it.width - width) + kotlin.math.abs(it.height - height)
                }
            )
    }

    fun controlKeyFor(option: CameraLensOption): String {
        return option.physicalCameraId?.let { "${option.logicalCameraId}:$it" } ?: option.logicalCameraId
    }

    fun findOptionByControlKey(
        options: List<CameraLensOption>,
        key: String?,
        allowedDebugDirectCameraIds: Set<String> = emptySet()
    ): CameraLensOption? {
        if (key.isNullOrBlank()) return null
        return options.firstOrNull { controlKeyFor(it) == key }
            ?: findDebugDirectOption(key, allowedDebugDirectCameraIds)
    }

    private fun findDebugDirectOption(key: String, allowedDebugDirectCameraIds: Set<String>): CameraLensOption? {
        if (!BuildConfig.DEBUG || key.any { !it.isDigit() } || key !in allowedDebugDirectCameraIds) {
            return null
        }
        return directOptionFor(key, null)
    }

    private fun directOptionFor(key: String, label: String?): CameraLensOption? {
        if (!BuildConfig.DEBUG || key.any { !it.isDigit() }) {
            return null
        }
        val characteristics = runCatching {
            cameraManager.getCameraCharacteristics(key)
        }.getOrNull() ?: return null
        if (!hasUsableOutput(characteristics)) {
            return null
        }
        return CameraLensOption(
            logicalCameraId = key,
            physicalCameraId = null,
            label = label ?: "${displayFacingLabel(characteristics)} (ID $key)",
            lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                ?: CameraCharacteristics.LENS_FACING_BACK
        )
    }

    private fun preferredAspectScore(size: Size): Int {
        val ratio = size.width.toFloat() / size.height.toFloat()
        return when {
            abs(ratio - (16f / 9f)) < 0.02f -> 2
            abs(ratio - (4f / 3f)) < 0.02f -> 1
            else -> 0
        }
    }

    fun inferNativeZoomRatio(
        option: CameraLensOption,
        capabilities: CameraCapabilities,
        allOptions: List<CameraLensOption>
    ): Float? {
        val range = capabilities.zoomRatioRange ?: return null
        if (option.isLogicalSelection) {
            return null
        }

        val physicalOptions = allOptions
            .filter { it.logicalCameraId == option.logicalCameraId && it.physicalCameraId != null }
        if (physicalOptions.isEmpty()) {
            return null
        }

        val focalByPhysicalId = physicalOptions.mapNotNull { physicalOption ->
            val physicalId = physicalOption.physicalCameraId ?: return@mapNotNull null
            val focal = runCatching {
                cameraManager.getCameraCharacteristics(physicalId)
                    .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.minOrNull()
            }.getOrNull() ?: return@mapNotNull null
            physicalId to focal
        }.toMap()

        val targetPhysicalId = option.physicalCameraId ?: return null
        val targetFocal = focalByPhysicalId[targetPhysicalId] ?: return fallbackPhysicalZoom(
            option = option,
            range = range,
            physicalOptions = physicalOptions
        )

        val sortedFocals = focalByPhysicalId.values.sorted()
        val referenceFocal = chooseReferenceFocal(sortedFocals, range) ?: return null
        return (targetFocal / referenceFocal).coerceIn(range.lower, range.upper)
    }

    private fun chooseReferenceFocal(
        sortedFocals: List<Float>,
        range: Range<Float>
    ): Float? {
        if (sortedFocals.isEmpty()) {
            return null
        }

        if (sortedFocals.size == 1) {
            return sortedFocals.first()
        }

        if (sortedFocals.size == 2) {
            return if (range.lower < 0.95f) {
                sortedFocals.maxOrNull()
            } else {
                sortedFocals.minOrNull()
            }
        }

        return sortedFocals[sortedFocals.size / 2]
    }

    private fun fallbackPhysicalZoom(
        option: CameraLensOption,
        range: Range<Float>,
        physicalOptions: List<CameraLensOption>
    ): Float? {
        val sortedIds = physicalOptions.mapNotNull { it.physicalCameraId }.sorted()
        if (sortedIds.isEmpty()) {
            return null
        }
        return if (sortedIds.size == 2 && range.lower < 1f) {
            if (option.physicalCameraId == sortedIds.first()) {
                1f.coerceIn(range.lower, range.upper)
            } else {
                range.lower
            }
        } else {
            1f.coerceIn(range.lower, range.upper)
        }
    }

    private fun directProbeCameraIds(publicCameraIds: Set<String>): List<String> {
        val candidates = linkedSetOf<String>()
        candidates += publicCameraIds.sorted()
        if (BuildConfig.DEBUG) {
            for (id in 0..DEBUG_DIRECT_CAMERA_ID_MAX) {
                candidates += id.toString()
            }
            candidates += VENDOR_DIRECT_CAMERA_IDS.map(Int::toString)
        }
        return candidates.toList().sortedWith(compareBy<String> { it.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it })
    }

    private fun lensFacingLabel(characteristics: CameraCharacteristics): String {
        return when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_BACK -> "Back"
            CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
            else -> "Unknown"
        }
    }

    private fun displayFacingLabel(characteristics: CameraCharacteristics): String {
        return when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_BACK -> "后摄"
            CameraCharacteristics.LENS_FACING_FRONT -> "前摄"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "外接摄像头"
            else -> "摄像头"
        }
    }

    private fun hasUsableOutput(characteristics: CameraCharacteristics): Boolean {
        val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return false
        return !streamMap.getOutputSizes(SurfaceTexture::class.java).isNullOrEmpty() ||
            !streamMap.getOutputSizes(ImageFormat.YUV_420_888).isNullOrEmpty()
    }

    private fun approximate35mmFocalLengths(focalLengths: List<Float>, sensorWidthMm: Float?): List<Float> {
        if (sensorWidthMm == null || sensorWidthMm <= 0f) {
            return emptyList()
        }
        return focalLengths.map { focal ->
            ((focal * 36f / sensorWidthMm) * 10f).toInt() / 10f
        }
    }

    private fun inferLensRole(
        lensFacing: Int?,
        approximate35mmFocalLengths: List<Float>,
        zoomRatioRange: String?
    ): String? {
        return when (lensFacing) {
            CameraCharacteristics.LENS_FACING_FRONT -> "front"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
            CameraCharacteristics.LENS_FACING_BACK -> {
                val shortest = approximate35mmFocalLengths.minOrNull()
                when {
                    isVariableZoomCandidate(zoomRatioRange) ->
                        "rear-variable-zoom-candidate"
                    shortest == null -> "rear-unknown"
                    shortest < 20f -> "rear-ultrawide-candidate"
                    shortest < 38f -> "rear-wide-main-candidate"
                    shortest < 80f -> "rear-tele-candidate"
                    else -> "rear-long-tele-candidate"
                }
            }
            else -> null
        }
    }

    private fun isVariableZoomCandidate(zoomRatioRange: String?): Boolean {
        if (zoomRatioRange == null) {
            return false
        }
        val lower = zoomRatioRange.substringBefore("-").toFloatOrNull() ?: return false
        val upper = zoomRatioRange.substringAfter("-", "").toFloatOrNull() ?: return false
        return lower < 0.95f && upper >= 12f
    }

    private fun formatFloat(value: Float): String {
        return "%.2f".format(java.util.Locale.US, value)
    }

    private fun hardwareLevelLabel(level: Int): String {
        return when (level) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN_$level"
        }
    }

    private fun highSpeedVideoConfigurations(streamMap: android.hardware.camera2.params.StreamConfigurationMap?): List<CameraHighSpeedVideoConfiguration> {
        return highSpeedVideoFpsRanges(streamMap)
            .toList()
            .sortedWith(
                compareByDescending<Pair<Size, List<Range<Int>>>> { it.first.width.toLong() * it.first.height.toLong() }
                    .thenByDescending { it.first.width }
            )
            .map { (size, ranges) ->
                CameraHighSpeedVideoConfiguration(
                    size = sizeLabel(size),
                    fpsRanges = ranges.map(::rangeLabel)
                )
            }
    }

    private fun highSpeedVideoFpsRanges(
        streamMap: android.hardware.camera2.params.StreamConfigurationMap?
    ): Map<Size, List<Range<Int>>> {
        if (streamMap == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return emptyMap()
        }
        return runCatching {
            streamMap.highSpeedVideoSizes
                .orEmpty()
                .distinct()
                .associateWith { size ->
                    streamMap.getHighSpeedVideoFpsRangesFor(size)
                        .orEmpty()
                        .distinct()
                        .filter { it.upper >= it.lower && it.upper > 0 }
                        .sortedWith(compareBy<Range<Int>> { it.upper }.thenByDescending { it.lower })
                }
                .filterValues { it.isNotEmpty() }
        }.getOrDefault(emptyMap())
    }

    private fun rangeLabel(range: Range<Int>): String = "${range.lower}-${range.upper}"

    private fun Range<Int>.containsTargetFps(targetFps: Int): Boolean {
        return lower <= targetFps && upper >= targetFps
    }

    private fun requestCapabilityLabel(capability: Int): String {
        return when (capability) {
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> "BACKWARD_COMPATIBLE"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "MANUAL_SENSOR"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "MANUAL_POST_PROCESSING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING -> "PRIVATE_REPROCESSING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS -> "READ_SENSOR_SETTINGS"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> "BURST_CAPTURE"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING -> "YUV_REPROCESSING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT -> "DEPTH_OUTPUT"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO -> "CONSTRAINED_HIGH_SPEED_VIDEO"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING -> "MOTION_TRACKING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "LOGICAL_MULTI_CAMERA"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME -> "MONOCHROME"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA -> "SECURE_IMAGE_DATA"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA -> "SYSTEM_CAMERA"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING -> "OFFLINE_PROCESSING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR -> "ULTRA_HIGH_RESOLUTION_SENSOR"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING -> "REMOSAIC_REPROCESSING"
            else -> "UNKNOWN_$capability"
        }
    }

    private fun errorLabel(error: Throwable): String {
        val message = error.message?.take(180)
        return if (message.isNullOrBlank()) {
            error.javaClass.simpleName
        } else {
            "${error.javaClass.simpleName}: $message"
        }
    }

    private fun List<Size>.formatSizes(): List<String> {
        return distinct()
            .sortedWith(
                compareByDescending<Size> { it.width.toLong() * it.height.toLong() }
                    .thenByDescending { it.width }
            )
            .map(::sizeLabel)
    }

    private companion object {
        const val DEBUG_DIRECT_CAMERA_ID_MAX = 9
        val VENDOR_DIRECT_CAMERA_IDS = listOf(20, 21, 60, 61, 62, 63, 100, 101, 120)
    }
}
