package com.opencode.multilensipcam

data class WebStreamControlSelection(
    val previewAdjustment: PreviewAdjustment,
    val mjpegPipeline: MjpegPipeline,
    val mjpegFullSize: Boolean,
    val previewChanged: Boolean,
    val requiresRestart: Boolean
)

object WebStreamControlSelections {
    fun resolve(
        command: WebControlCommand,
        currentPreviewAdjustment: PreviewAdjustment,
        currentMjpegPipeline: MjpegPipeline,
        currentMjpegFullSize: Boolean
    ): WebStreamControlSelection {
        val nextPreviewAdjustment = currentPreviewAdjustment.withControlValues(
            rotation = command.previewRotation,
            scale = command.previewScale,
            stretchX = command.previewStretchX,
            stretchY = command.previewStretchY
        )
        val nextMjpegPipeline = MjpegPipeline.fromControlValue(command.jpegPipeline) ?: currentMjpegPipeline
        val nextMjpegFullSize = command.mjpegFullSize ?: currentMjpegFullSize
        return WebStreamControlSelection(
            previewAdjustment = nextPreviewAdjustment,
            mjpegPipeline = nextMjpegPipeline,
            mjpegFullSize = nextMjpegFullSize,
            previewChanged = nextPreviewAdjustment != currentPreviewAdjustment,
            requiresRestart = nextMjpegPipeline != currentMjpegPipeline || nextMjpegFullSize != currentMjpegFullSize
        )
    }
}
