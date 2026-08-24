package com.opencode.multilensipcam

object NativeSkinResources {
    fun palette(
        skin: UiSkin,
        color: (Int) -> Int
    ): UiPalette {
        return when (skin) {
            UiSkin.GREEN -> UiPalette(
                primaryText = color(R.color.ui_text_primary),
                secondaryText = color(R.color.ui_text_secondary),
                onAccentText = color(R.color.ui_bg_base),
                accent = color(R.color.ui_accent_soft),
                track = color(R.color.ui_stroke)
            )
            UiSkin.SUNRISE -> UiPalette(
                primaryText = color(R.color.skin_warm_text_primary),
                secondaryText = color(R.color.skin_warm_text_secondary),
                onAccentText = color(R.color.skin_warm_bg_base),
                accent = color(R.color.skin_warm_accent),
                track = color(R.color.skin_warm_stroke)
            )
            UiSkin.MINERAL -> UiPalette(
                primaryText = color(R.color.skin_mineral_text_primary),
                secondaryText = color(R.color.skin_mineral_text_secondary),
                onAccentText = color(R.color.skin_mineral_bg_base),
                accent = color(R.color.skin_mineral_accent),
                track = color(R.color.skin_mineral_stroke)
            )
        }
    }

    fun drawable(
        skin: UiSkin,
        greenRes: Int,
        sunriseRes: Int,
        mineralRes: Int
    ): Int {
        return when (skin) {
            UiSkin.GREEN -> greenRes
            UiSkin.SUNRISE -> sunriseRes
            UiSkin.MINERAL -> mineralRes
        }
    }
}
