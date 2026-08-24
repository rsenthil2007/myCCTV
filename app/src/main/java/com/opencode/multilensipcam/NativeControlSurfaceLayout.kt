package com.opencode.multilensipcam

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.opencode.multilensipcam.databinding.ActivityMainBinding

object NativeControlSurfaceLayout {
    data class Drawables(
        val floatingPanel: Int,
        val portraitPanel: Int
    )

    fun apply(
        binding: ActivityMainBinding,
        isLandscapeStreaming: Boolean,
        isPanelOpen: Boolean,
        isStreaming: Boolean,
        drawables: Drawables,
        dpToPx: (Int) -> Int
    ) {
        val layoutParams = (binding.controlSurface.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        binding.headerCard.visibility = View.GONE
        binding.quickSettingsBar.visibility = if (isLandscapeStreaming) View.GONE else View.VISIBLE
        if (isLandscapeStreaming) {
            applyLandscapeStreaming(
                binding = binding,
                layoutParams = layoutParams,
                isPanelOpen = isPanelOpen,
                drawables = drawables,
                dpToPx = dpToPx
            )
        } else {
            applyPortraitConfig(
                binding = binding,
                layoutParams = layoutParams,
                isStreaming = isStreaming,
                drawables = drawables,
                dpToPx = dpToPx
            )
        }
    }

    private fun applyLandscapeStreaming(
        binding: ActivityMainBinding,
        layoutParams: FrameLayout.LayoutParams,
        isPanelOpen: Boolean,
        drawables: Drawables,
        dpToPx: (Int) -> Int
    ) {
        layoutParams.width = dpToPx(420)
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT
        layoutParams.marginStart = 0
        layoutParams.topMargin = 0
        layoutParams.marginEnd = 0
        layoutParams.bottomMargin = 0
        layoutParams.gravity = Gravity.END
        binding.controlSurface.layoutParams = layoutParams
        binding.controlSurface.setBackgroundResource(drawables.floatingPanel)
        binding.controlSurface.visibility = if (isPanelOpen) View.VISIBLE else View.GONE
        binding.settingsButton.visibility = View.VISIBLE
        binding.panelHandle.visibility = View.GONE
        binding.startButton.visibility = View.GONE
        binding.stopButton.visibility = View.VISIBLE
        (binding.stopButton.layoutParams as? ViewGroup.MarginLayoutParams)?.let { stopParams ->
            stopParams.marginStart = 0
            stopParams.height = dpToPx(40)
            binding.stopButton.layoutParams = stopParams
        }
    }

    private fun applyPortraitConfig(
        binding: ActivityMainBinding,
        layoutParams: FrameLayout.LayoutParams,
        isStreaming: Boolean,
        drawables: Drawables,
        dpToPx: (Int) -> Int
    ) {
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT
        layoutParams.marginStart = dpToPx(12)
        layoutParams.topMargin = dpToPx(72)
        layoutParams.marginEnd = dpToPx(12)
        layoutParams.bottomMargin = 0
        layoutParams.gravity = Gravity.NO_GRAVITY
        binding.controlSurface.layoutParams = layoutParams
        binding.controlSurface.setBackgroundResource(drawables.portraitPanel)
        binding.controlSurface.visibility = View.VISIBLE
        binding.settingsButton.visibility = View.GONE
        binding.panelHandle.visibility = View.VISIBLE
        binding.startButton.visibility = if (isStreaming) View.GONE else View.VISIBLE
        binding.stopButton.visibility = if (isStreaming) View.VISIBLE else View.GONE
        (binding.stopButton.layoutParams as? ViewGroup.MarginLayoutParams)?.let { stopParams ->
            stopParams.marginStart = dpToPx(10)
            stopParams.height = dpToPx(46)
            binding.stopButton.layoutParams = stopParams
        }
    }
}
