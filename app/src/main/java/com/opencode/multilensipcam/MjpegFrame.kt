package com.opencode.multilensipcam

data class MjpegFrame(
    val bytes: ByteArray,
    val pipeline: MjpegPipeline,
    val width: Int,
    val height: Int,
    val outputRotationDegrees: Int,
    val jpegQuality: Int,
    val targetFps: Int,
    val capturedAtElapsedMs: Long,
    val generatedAtElapsedMs: Long,
    val encodeDurationMs: Long,
    val yuvToNv21DurationMs: Long,
    val jpegCompressDurationMs: Long,
    val outputTransformDurationMs: Long,
    val overlayRenderDurationMs: Long,
    val sourceFramesSeen: Long,
    val sourceFramesEncoded: Long,
    val sourceFramesDropped: Long,
    val sourceFramesDroppedByFpsGate: Long,
    val sourceFramesDroppedByEncoderBusy: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MjpegFrame

        if (!bytes.contentEquals(other.bytes)) return false
        if (pipeline != other.pipeline) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (outputRotationDegrees != other.outputRotationDegrees) return false
        if (jpegQuality != other.jpegQuality) return false
        if (targetFps != other.targetFps) return false
        if (capturedAtElapsedMs != other.capturedAtElapsedMs) return false
        if (generatedAtElapsedMs != other.generatedAtElapsedMs) return false
        if (encodeDurationMs != other.encodeDurationMs) return false
        if (yuvToNv21DurationMs != other.yuvToNv21DurationMs) return false
        if (jpegCompressDurationMs != other.jpegCompressDurationMs) return false
        if (outputTransformDurationMs != other.outputTransformDurationMs) return false
        if (overlayRenderDurationMs != other.overlayRenderDurationMs) return false
        if (sourceFramesSeen != other.sourceFramesSeen) return false
        if (sourceFramesEncoded != other.sourceFramesEncoded) return false
        if (sourceFramesDropped != other.sourceFramesDropped) return false
        if (sourceFramesDroppedByFpsGate != other.sourceFramesDroppedByFpsGate) return false
        return sourceFramesDroppedByEncoderBusy == other.sourceFramesDroppedByEncoderBusy
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + pipeline.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + outputRotationDegrees
        result = 31 * result + jpegQuality
        result = 31 * result + targetFps
        result = 31 * result + capturedAtElapsedMs.hashCode()
        result = 31 * result + generatedAtElapsedMs.hashCode()
        result = 31 * result + encodeDurationMs.hashCode()
        result = 31 * result + yuvToNv21DurationMs.hashCode()
        result = 31 * result + jpegCompressDurationMs.hashCode()
        result = 31 * result + outputTransformDurationMs.hashCode()
        result = 31 * result + overlayRenderDurationMs.hashCode()
        result = 31 * result + sourceFramesSeen.hashCode()
        result = 31 * result + sourceFramesEncoded.hashCode()
        result = 31 * result + sourceFramesDropped.hashCode()
        result = 31 * result + sourceFramesDroppedByFpsGate.hashCode()
        result = 31 * result + sourceFramesDroppedByEncoderBusy.hashCode()
        return result
    }
}
