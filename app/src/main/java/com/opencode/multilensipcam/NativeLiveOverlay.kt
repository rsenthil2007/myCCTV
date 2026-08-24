package com.opencode.multilensipcam

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.opencode.multilensipcam.databinding.ActivityMainBinding

object NativeLiveOverlay {
    fun apply(
        binding: ActivityMainBinding,
        showLiveOverlay: Boolean,
        liveStatusLine1Text: String,
        liveStatusLine2Text: String,
        liveStatusLine1Color: Int,
        liveStatusLine2Color: Int,
        dpToPx: (Int) -> Int
    ) {
        binding.previewScrimView.visibility = if (showLiveOverlay) View.GONE else View.VISIBLE
        binding.bottomInfoCard.visibility = if (showLiveOverlay) View.VISIBLE else View.GONE
        binding.statusText.visibility = View.GONE
        binding.sessionInfoText.visibility = View.GONE
        binding.urlText.visibility = if (showLiveOverlay) View.VISIBLE else View.GONE
        binding.liveStatusCard.visibility = if (showLiveOverlay) View.VISIBLE else View.GONE
        if (!showLiveOverlay) {
            return
        }

        binding.bottomInfoCard.background = null
        binding.bottomInfoCard.setPadding(0, 0, 0, 0)
        binding.bottomInfoCard.layoutParams = (binding.bottomInfoCard.layoutParams as? FrameLayout.LayoutParams)
            ?.apply {
                width = FrameLayout.LayoutParams.WRAP_CONTENT
                height = FrameLayout.LayoutParams.WRAP_CONTENT
                gravity = Gravity.BOTTOM or Gravity.START
                marginStart = dpToPx(14)
                bottomMargin = dpToPx(14)
                marginEnd = 0
                topMargin = 0
            }
            ?: FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START
            )
        binding.liveStatusLine1Text.text = liveStatusLine1Text
        binding.liveStatusLine2Text.text = liveStatusLine2Text
        binding.liveStatusLine1Text.setTextColor(liveStatusLine1Color)
        binding.liveStatusLine2Text.setTextColor(liveStatusLine2Color)
    }
}
