package com.opencode.multilensipcam

import android.view.View
import com.opencode.multilensipcam.databinding.ActivityMainBinding

object NativeSkinSurfaces {
    fun apply(
        binding: ActivityMainBinding,
        resolveDrawable: (Int, Int, Int) -> Int,
        cardSurfaceViews: List<View>,
        presetGroupView: View?
    ) {
        binding.root.setBackgroundResource(resolveDrawable(R.drawable.bg_app_gradient, R.drawable.bg_app_gradient_warm, R.drawable.bg_app_gradient_mineral))
        binding.previewScrimView.setBackgroundResource(
            resolveDrawable(R.drawable.bg_preview_scrim, R.drawable.bg_preview_scrim_warm, R.drawable.bg_preview_scrim_mineral)
        )
        binding.languageToggleButton.setBackgroundResource(
            resolveDrawable(R.drawable.bg_tool_button, R.drawable.bg_tool_button_warm, R.drawable.bg_tool_button_mineral)
        )
        binding.skinToggleButton.setBackgroundResource(
            resolveDrawable(R.drawable.bg_tool_button, R.drawable.bg_tool_button_warm, R.drawable.bg_tool_button_mineral)
        )
        binding.skinToggleButton.getChildAt(0)?.setBackgroundResource(
            resolveDrawable(R.drawable.bg_skin_dot, R.drawable.bg_skin_dot_warm, R.drawable.bg_skin_dot_mineral)
        )
        binding.settingsButton.setBackgroundResource(
            resolveDrawable(R.drawable.bg_chip_surface, R.drawable.bg_chip_surface_warm, R.drawable.bg_chip_surface_mineral)
        )
        binding.startButton.setBackgroundResource(
            resolveDrawable(R.drawable.bg_button_primary, R.drawable.bg_button_primary_warm, R.drawable.bg_button_primary_mineral)
        )
        binding.stopButton.setBackgroundResource(
            resolveDrawable(R.drawable.bg_button_stop, R.drawable.bg_button_stop_warm, R.drawable.bg_button_stop_mineral)
        )
        listOf(binding.preset1080p30Button, binding.preset4k30Button, binding.preset720p60Button).forEach { button ->
            button.setBackgroundResource(resolveDrawable(R.drawable.bg_preset_chip, R.drawable.bg_preset_chip_warm, R.drawable.bg_preset_chip_mineral))
        }
        listOf(binding.cameraSpinner, binding.resolutionSpinner, binding.fpsSpinner, binding.focusModeSpinner).forEach { spinner ->
            spinner.setPopupBackgroundResource(resolveDrawable(R.drawable.bg_card_surface, R.drawable.bg_card_surface_warm, R.drawable.bg_card_surface_mineral))
            spinner.setBackgroundResource(resolveDrawable(R.drawable.bg_input_shell, R.drawable.bg_input_shell_warm, R.drawable.bg_input_shell_mineral))
        }
        listOf(
            binding.manualResolutionContainer,
            binding.nativeVerifiedCameraList
        ).forEach { container ->
            container.setBackgroundResource(resolveDrawable(R.drawable.bg_input_shell, R.drawable.bg_input_shell_warm, R.drawable.bg_input_shell_mineral))
        }
        cardSurfaceViews.forEach { card ->
            card.setBackgroundResource(resolveDrawable(R.drawable.bg_card_surface, R.drawable.bg_card_surface_warm, R.drawable.bg_card_surface_mineral))
        }
        presetGroupView?.setBackgroundResource(resolveDrawable(R.drawable.bg_preset_group, R.drawable.bg_preset_group_warm, R.drawable.bg_preset_group_mineral))
    }
}
