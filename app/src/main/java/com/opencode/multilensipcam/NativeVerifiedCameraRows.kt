package com.opencode.multilensipcam

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

object NativeVerifiedCameraRows {
    data class Labels(
        val builtInVerified: String,
        val localScanVerified: String,
        val builtInTag: String,
        val localTag: String,
        val deleteCamera: String
    )

    fun build(
        context: Context,
        entry: VerifiedCameraEntry,
        labels: Labels,
        onDeleteClick: (String) -> Unit
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(context, 8), 0, dpToPx(context, 8))
        }
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(context).apply {
            text = entry.label
            setTextColor(ContextCompat.getColor(context, R.color.ui_text_primary))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
        }
        val subtitle = TextView(context).apply {
            text = if (isProfileEntry(entry)) labels.builtInVerified else labels.localScanVerified
            setTextColor(ContextCompat.getColor(context, R.color.ui_text_muted))
            textSize = 11f
        }
        textColumn.addView(title)
        textColumn.addView(subtitle)
        row.addView(textColumn)

        val tagView = TextView(context).apply {
            minWidth = dpToPx(context, 48)
            gravity = Gravity.CENTER
            setPadding(dpToPx(context, 10), dpToPx(context, 4), dpToPx(context, 10), dpToPx(context, 4))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            if (isProfileEntry(entry)) {
                text = labels.builtInTag
                setBackgroundResource(R.drawable.bg_debug_tag_builtin)
                setTextColor(ContextCompat.getColor(context, R.color.ui_success))
            } else {
                text = labels.localTag
                setBackgroundResource(R.drawable.bg_debug_tag_local)
                setTextColor(ContextCompat.getColor(context, R.color.ui_warning))
            }
        }
        row.addView(tagView)

        if (entry.source == VerifiedCameraSource.SCAN.wireValue) {
            val deleteButton = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(context, 28), dpToPx(context, 28)).apply {
                    marginStart = dpToPx(context, 8)
                }
                setBackgroundResource(R.drawable.bg_delete_button)
                setImageResource(R.drawable.ic_delete)
                imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.ui_danger))
                contentDescription = labels.deleteCamera
                setPadding(dpToPx(context, 6), dpToPx(context, 6), dpToPx(context, 6), dpToPx(context, 6))
                setOnClickListener { onDeleteClick(entry.cameraId) }
            }
            row.addView(deleteButton)
        }

        return row
    }

    private fun isProfileEntry(entry: VerifiedCameraEntry): Boolean {
        return entry.source == VerifiedCameraSource.PROFILE.wireValue
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
