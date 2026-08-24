package com.opencode.multilensipcam

data class PreviewAdjustment(
    val rotation: Int = 0,
    val scale: Float = 1f,
    val stretchX: Float = 1f,
    val stretchY: Float = 1f
) {
    fun rotateBy(delta: Int): PreviewAdjustment {
        return copy(rotation = normalizeRotation(rotation + delta))
    }

    fun scaleBy(delta: Float): PreviewAdjustment {
        return copy(scale = (scale + delta).coerceIn(NATIVE_SCALE_MIN, NATIVE_SCALE_MAX))
    }

    fun fit(): PreviewAdjustment {
        return copy(scale = 1f, stretchX = 1f, stretchY = 1f)
    }

    fun withControlValues(
        rotation: Int? = null,
        scale: Float? = null,
        stretchX: Float? = null,
        stretchY: Float? = null
    ): PreviewAdjustment {
        return copy(
            rotation = rotation?.coerceIn(ROTATION_MIN, ROTATION_MAX) ?: this.rotation,
            scale = scale?.coerceIn(WEB_SCALE_MIN, WEB_SCALE_MAX) ?: this.scale,
            stretchX = stretchX?.coerceIn(WEB_SCALE_MIN, WEB_SCALE_MAX) ?: this.stretchX,
            stretchY = stretchY?.coerceIn(WEB_SCALE_MIN, WEB_SCALE_MAX) ?: this.stretchY
        )
    }

    companion object {
        val DEFAULT = PreviewAdjustment()

        fun normalizeSensorOrientation(rotation: Int): Int {
            val normalized = (((rotation + 180) % 360) + 360) % 360
            return if (normalized == 0 || normalized == 90 || normalized == 180 || normalized == 270) normalized else 270
        }

        private const val ROTATION_MIN = -180
        private const val ROTATION_MAX = 180
        private const val NATIVE_SCALE_MIN = 0.5f
        private const val NATIVE_SCALE_MAX = 3f
        private const val WEB_SCALE_MIN = 0.25f
        private const val WEB_SCALE_MAX = 4f

        private fun normalizeRotation(value: Int): Int {
            return (((value + 180) % 360 + 360) % 360) - 180
        }
    }
}
