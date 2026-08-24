package com.opencode.multilensipcam

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object NativeCollapsibleBlocks {
    fun bind(block: ViewGroup, onToggle: () -> Unit) {
        val header = block.getChildAt(0) ?: return
        header.isClickable = true
        header.isFocusable = true
        header.setOnClickListener { onToggle() }
    }

    fun update(
        block: ViewGroup,
        expanded: Boolean,
        title: String,
        expandText: String,
        collapseText: String
    ) {
        val header = block.getChildAt(0)
        val titleView = when (header) {
            is TextView -> header
            is ViewGroup -> header.getChildAt(0) as? TextView
            else -> null
        }
        val toggleView = (header as? ViewGroup)?.getChildAt((header.childCount - 1).coerceAtLeast(0)) as? TextView
        titleView?.text = title
        toggleView?.text = if (expanded) collapseText else expandText
        for (index in 1 until block.childCount) {
            block.getChildAt(index).visibility = if (expanded) View.VISIBLE else View.GONE
        }
    }
}
