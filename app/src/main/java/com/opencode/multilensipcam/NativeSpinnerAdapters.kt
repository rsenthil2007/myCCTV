package com.opencode.multilensipcam

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

object NativeSpinnerAdapters {
    fun <T> create(
        context: Context,
        items: List<T>,
        palette: UiPalette,
        selectedBackgroundRes: Int,
        dropdownBackgroundRes: Int
    ): ArrayAdapter<T> {
        return object : ArrayAdapter<T>(context, R.layout.spinner_selected_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return super.getView(position, convertView, parent).also { view ->
                    styleSpinnerText(view, palette, selectedBackgroundRes)
                }
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return super.getDropDownView(position, convertView, parent).also { view ->
                    styleSpinnerText(view, palette, dropdownBackgroundRes)
                }
            }
        }.also { adapter ->
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
    }

    private fun styleSpinnerText(
        view: View,
        palette: UiPalette,
        backgroundRes: Int
    ) {
        (view as? TextView)?.apply {
            setTextColor(palette.primaryText)
            compoundDrawableTintList = ColorStateList.valueOf(palette.secondaryText)
        }
        view.setBackgroundResource(backgroundRes)
    }
}
