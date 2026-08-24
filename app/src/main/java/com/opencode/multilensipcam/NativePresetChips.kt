package com.opencode.multilensipcam

import android.util.Size
import android.widget.TextView

object NativePresetChips {
    data class Selection(
        val outputSize: Size?,
        val targetFps: Int?,
        val streamQuality: Int,
        val unlimitedFpsSelected: Boolean
    )

    fun update(
        button: TextView,
        preset: StreamPreset,
        enabled: Boolean,
        palette: UiPalette,
        selection: Selection
    ) {
        val selectedSize = selection.outputSize
        val isActive = selectedSize?.width == preset.width &&
            selectedSize.height == preset.height &&
            selection.targetFps == preset.fps &&
            selection.streamQuality == preset.quality &&
            !selection.unlimitedFpsSelected
        button.isEnabled = enabled
        button.isSelected = enabled && isActive
        button.alpha = if (enabled) 1f else 0.45f
        button.setTextColor(
            when {
                button.isSelected -> palette.onAccentText
                enabled -> palette.primaryText
                else -> palette.secondaryText
            }
        )
    }
}
