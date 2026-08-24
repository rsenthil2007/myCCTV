package com.opencode.multilensipcam

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

object NativeSelectorChips {
    fun build(
        context: Context,
        text: String,
        selected: Boolean,
        palette: UiPalette,
        heightPx: Int,
        minWidthPx: Int,
        horizontalPaddingPx: Int,
        marginEndPx: Int,
        backgroundRes: Int,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                heightPx
            ).apply {
                marginEnd = marginEndPx
            }
            minWidth = minWidthPx
            gravity = Gravity.CENTER
            setPadding(horizontalPaddingPx, 0, horizontalPaddingPx, 0)
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundResource(backgroundRes)
            isSelected = selected
            setTextColor(if (selected) palette.onAccentText else palette.primaryText)
            this.text = text
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }
}
