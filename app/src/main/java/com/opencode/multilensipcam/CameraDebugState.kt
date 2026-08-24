package com.opencode.multilensipcam

data class CameraDebugState(
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val hardware: String,
    val fingerprint: String,
    val sdkInt: Int,
    val publicCameraIds: List<String>,
    val probes: List<CameraDebugProbe>
)

data class CameraDebugProbe(
    val cameraId: String,
    val isPublic: Boolean,
    val accessible: Boolean,
    val label: String?,
    val lensFacing: String?,
    val lensFacingValue: Int?,
    val sensorOrientation: Int?,
    val hardwareLevel: String?,
    val focalLengthsMm: List<Float>,
    val approximate35mmFocalLengths: List<Float>,
    val inferredLensRole: String?,
    val sensorPhysicalSizeMm: String?,
    val activeArraySize: String?,
    val zoomRatioRange: String?,
    val maxDigitalZoom: Float?,
    val physicalCameraIds: List<String>,
    val requestAvailableCapabilities: List<String>,
    val hasConstrainedHighSpeedVideo: Boolean,
    val highSpeedVideoConfigurations: List<CameraHighSpeedVideoConfiguration>,
    val surfaceTextureSizes: List<String>,
    val yuvSizes: List<String>,
    val jpegSizes: List<String>,
    val fpsRanges: List<String>,
    val error: String?
)

data class CameraHighSpeedVideoConfiguration(
    val size: String,
    val fpsRanges: List<String>
)
