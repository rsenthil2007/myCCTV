package com.opencode.multilensipcam

data class UiPalette(
    val primaryText: Int,
    val secondaryText: Int,
    val onAccentText: Int,
    val accent: Int,
    val track: Int
)

data class StaticTextBinding(
    val englishRes: Int,
    val chineseRes: Int
)

enum class StatusMode {
    IDLE,
    READY,
    STREAMING,
    ERROR,
    PERMISSION
}

enum class UiSkin(val statusMessage: String) {
    GREEN("Applied Green color skin"),
    SUNRISE("Applied Orange Sunrise color skin"),
    MINERAL("Applied Blue Mineral color skin");

    fun next(): UiSkin {
        return when (this) {
            GREEN -> SUNRISE
            SUNRISE -> MINERAL
            MINERAL -> GREEN
        }
    }
}

object NativeStaticTextBindings {
    val all = listOf(
        StaticTextBinding(R.string.ui_controls_en, R.string.ui_controls_zh),
        StaticTextBinding(R.string.ui_current_setup_en, R.string.ui_current_setup_zh),
        StaticTextBinding(R.string.ui_camera_capabilities_en, R.string.ui_camera_capabilities_zh),
        StaticTextBinding(R.string.ui_recommended_settings_en, R.string.ui_recommended_settings_zh),
        StaticTextBinding(R.string.ui_camera_en, R.string.ui_camera_zh),
        StaticTextBinding(R.string.ui_video_profile_en, R.string.ui_video_profile_zh),
        StaticTextBinding(R.string.ui_resolution_en, R.string.ui_resolution_zh),
        StaticTextBinding(R.string.ui_frame_rate_en, R.string.ui_frame_rate_zh),
        StaticTextBinding(R.string.ui_custom_resolution_hint_en, R.string.ui_custom_resolution_hint_zh),
        StaticTextBinding(R.string.ui_stream_quality_en, R.string.ui_stream_quality_zh),
        StaticTextBinding(R.string.ui_focus_lens_en, R.string.ui_focus_lens_zh),
        StaticTextBinding(R.string.ui_focus_mode_en, R.string.ui_focus_mode_zh),
        StaticTextBinding(R.string.ui_focus_distance_en, R.string.ui_focus_distance_zh),
        StaticTextBinding(R.string.ui_exposure_zoom_en, R.string.ui_exposure_zoom_zh),
        StaticTextBinding(R.string.ui_flashlight_en, R.string.ui_flashlight_zh),
        StaticTextBinding(R.string.ui_video_overlay_en, R.string.ui_video_overlay_zh),
        StaticTextBinding(R.string.ui_video_overlay_size_en, R.string.ui_video_overlay_size_zh),
        StaticTextBinding(R.string.ui_zoom_ratio_en, R.string.ui_zoom_ratio_zh),
        StaticTextBinding(R.string.ui_exposure_compensation_en, R.string.ui_exposure_compensation_zh),
        StaticTextBinding(R.string.ui_light_exposure_en, R.string.ui_light_exposure_zh)
    )
}
