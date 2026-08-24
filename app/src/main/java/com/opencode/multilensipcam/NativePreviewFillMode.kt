package com.opencode.multilensipcam

import android.widget.FrameLayout

object NativePreviewFillMode {
    fun apply(
        previewView: AutoFitTextureView,
        liveLandscape: Boolean
    ) {
        previewView.setFillParent(liveLandscape)
        previewView.setRawTransform(false)
        previewView.alpha = if (liveLandscape) 1f else 0f
        previewView.layoutParams = previewView.layoutParams.apply {
            width = FrameLayout.LayoutParams.MATCH_PARENT
            height = FrameLayout.LayoutParams.MATCH_PARENT
        }
    }
}
