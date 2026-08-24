package com.opencode.multilensipcam

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.TextureView
import kotlin.math.max
import kotlin.math.min

class AutoFitTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr) {
    private var ratioWidth = 16
    private var ratioHeight = 9
    private var bufferWidth = 0
    private var bufferHeight = 0
    private var previewRotationDegrees = 0
    private var adjustmentRotationDegrees = 0
    private var adjustmentScale = 1f
    private var adjustmentStretchX = 1f
    private var adjustmentStretchY = 1f
    private var fillParent = false
    private var rawTransform = false

    fun setFillParent(fill: Boolean) {
        if (fillParent == fill) return
        fillParent = fill
        requestLayout()
        post { applyPreviewTransform() }
    }

    fun setRawTransform(raw: Boolean) {
        if (rawTransform == raw) return
        rawTransform = raw
        requestLayout()
        post { applyPreviewTransform() }
    }

    fun setAspectRatio(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        ratioWidth = width
        ratioHeight = height
        requestLayout()
        post { applyPreviewTransform() }
    }

    fun configurePreview(bufferWidth: Int, bufferHeight: Int, rotationDegrees: Int) {
        this.bufferWidth = bufferWidth
        this.bufferHeight = bufferHeight
        this.previewRotationDegrees = rotationDegrees
        post { applyPreviewTransform() }
    }

    fun setPreviewAdjustment(
        rotationDegrees: Int,
        scale: Float,
        stretchX: Float,
        stretchY: Float
    ) {
        adjustmentRotationDegrees = rotationDegrees.coerceIn(-180, 180)
        adjustmentScale = scale.coerceIn(0.25f, 4f)
        adjustmentStretchX = stretchX.coerceIn(0.25f, 4f)
        adjustmentStretchY = stretchY.coerceIn(0.25f, 4f)
        post { applyPreviewTransform() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        if (fillParent || rawTransform || ratioWidth == 0 || ratioHeight == 0) {
            setMeasuredDimension(width, height)
            return
        }

        val scaledHeight = width * ratioHeight / ratioWidth
        if (height == 0 || scaledHeight <= height) {
            setMeasuredDimension(width, scaledHeight)
        } else {
            val scaledWidth = height * ratioWidth / ratioHeight
            setMeasuredDimension(scaledWidth, height)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyPreviewTransform()
    }

    private fun applyPreviewTransform() {
        if (width == 0 || height == 0 || bufferWidth == 0 || bufferHeight == 0) {
            return
        }
        if (rawTransform) {
            setTransform(Matrix())
            return
        }

        val rotation = ((previewRotationDegrees % 360) + 360) % 360
        val transformRotation = -rotation - 90 + adjustmentRotationDegrees
        val effectiveRotation = ((transformRotation % 360) + 360) % 360
        val centerX = width / 2f
        val centerY = height / 2f
        val rotatedWidth = if (effectiveRotation == 90 || effectiveRotation == 270) bufferHeight.toFloat() else bufferWidth.toFloat()
        val rotatedHeight = if (effectiveRotation == 90 || effectiveRotation == 270) bufferWidth.toFloat() else bufferHeight.toFloat()
        val aspectWidth = if (fillParent) bufferWidth.toFloat() else rotatedWidth
        val aspectHeight = if (fillParent) bufferHeight.toFloat() else rotatedHeight
        val fitScale = min(width / aspectWidth, height / aspectHeight)
        val fillScale = max(width / aspectWidth, height / aspectHeight)
        val targetScale = if (fillParent) fillScale else fitScale
        val targetWidth = aspectWidth * targetScale
        val targetHeight = aspectHeight * targetScale
        val baseWidth = if (effectiveRotation == 90 || effectiveRotation == 270) height.toFloat() else width.toFloat()
        val baseHeight = if (effectiveRotation == 90 || effectiveRotation == 270) width.toFloat() else height.toFloat()

        val matrix = Matrix().apply {
            postTranslate(-width / 2f, -height / 2f)
            postRotate(transformRotation.toFloat())
            postScale(
                targetWidth / baseWidth * adjustmentScale * adjustmentStretchX,
                targetHeight / baseHeight * adjustmentScale * adjustmentStretchY
            )
            postTranslate(centerX, centerY)
        }
        setTransform(matrix)
    }
}
