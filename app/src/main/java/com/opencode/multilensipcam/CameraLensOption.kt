package com.opencode.multilensipcam

data class CameraLensOption(
    val logicalCameraId: String,
    val physicalCameraId: String?,
    val label: String,
    val lensFacing: Int
) {
    val isLogicalSelection: Boolean
        get() = physicalCameraId == null

    val isFrontFacing: Boolean
        get() = lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT

    override fun toString(): String = label
}
