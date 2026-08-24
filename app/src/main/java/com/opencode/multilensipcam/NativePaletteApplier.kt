package com.opencode.multilensipcam

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView

object NativePaletteApplier {
    fun applyTextPalette(
        root: View,
        palette: UiPalette,
        onAccentTextViews: Set<View>
    ) {
        if (root is TextView) {
            root.setTextColor(if (root in onAccentTextViews) palette.onAccentText else palette.primaryText)
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                applyTextPalette(root.getChildAt(index), palette, onAccentTextViews)
            }
        }
    }

    fun applySeekBarPalette(
        seekBars: List<SeekBar>,
        palette: UiPalette
    ) {
        val progressTint = ColorStateList.valueOf(palette.accent)
        val trackTint = ColorStateList.valueOf(palette.track)
        val thumbTint = ColorStateList.valueOf(palette.primaryText)
        seekBars.forEach { seekBar ->
            seekBar.progressTintList = progressTint
            seekBar.thumbTintList = thumbTint
            seekBar.progressBackgroundTintList = trackTint
        }
    }
}
