package dev.etcherfx.lensunlock.core

/** What to do with a request's zoom ratio. */
sealed interface ZoomSetting {
    /** Keep the app's value. */
    data object App : ZoomSetting

    /** The camera's minimum zoom ratio, which selects the ultrawide on a logical multi-camera. */
    data object Minimum : ZoomSetting

    /** A fixed ratio, clamped to the camera's range when applied. */
    data class Custom(val ratio: Float) : ZoomSetting
}

/** What to do with a request's video stabilization mode; [mode] is the Camera2 value, null keeps the app's. */
enum class Stabilization(val prefValue: String, val mode: Int?) {
    APP("app", null),
    OFF("off", 0),
    ON("on", 1),
    PREVIEW("preview", 2),
}

/** What to do with a request's AE target fps range. */
sealed interface FpsSetting {
    /** Keep the app's value. */
    data object App : FpsSetting

    /** Always use this range, if the camera supports it. */
    data class Fixed(val range: FpsRange) : FpsSetting
}

/** The overrides for one camera facing. Every setting defaults to the app's own value. */
data class FacingSettings(
    val zoom: ZoomSetting = ZoomSetting.App,
    val stabilization: Stabilization = Stabilization.APP,
    val fps: FpsSetting = FpsSetting.App,
) {
    /** Every setting keeps the app's value, so requests stay untouched. */
    val isAppDefault get() = this == DEFAULT

    companion object {
        val DEFAULT = FacingSettings()
    }
}

/**
 * One app's overrides, stored in the remote preferences group named after the app's package.
 * Keys carry the facing as a prefix: `back_zoom_mode`, `front_fps_range` and so on.
 */
data class AppSettings(
    val back: FacingSettings = FacingSettings.DEFAULT,
    val front: FacingSettings = FacingSettings.DEFAULT,
) {
    fun forFacing(facing: Facing): FacingSettings? = when (facing) {
        Facing.BACK -> back
        Facing.FRONT -> front
        Facing.OTHER -> null
    }

    companion object {
        val DEFAULT = AppSettings()

        const val KEY_ZOOM_MODE = "zoom_mode"
        const val KEY_ZOOM_RATIO = "zoom_ratio"
        const val KEY_STABILIZATION = "stabilization"
        const val KEY_FPS_RANGE = "fps_range"

        const val APP = "app"
        const val ZOOM_MINIMUM = "minimum"
        const val ZOOM_CUSTOM = "custom"
        const val DEFAULT_ZOOM_RATIO = 1.0f

        /** The preference key for [name] on [facing], which must be BACK or FRONT. */
        fun key(facing: Facing, name: String): String = when (facing) {
            Facing.BACK -> "back_$name"
            Facing.FRONT -> "front_$name"
            Facing.OTHER -> throw IllegalArgumentException("No settings for $facing cameras")
        }

        /**
         * Reads both facings. [getString] returns null for a missing key; [getFloat] returns its default.
         * A missing or unparseable value means the app's own value.
         */
        fun fromLookup(
            getString: (key: String) -> String?,
            getFloat: (key: String, default: Float) -> Float,
        ) = AppSettings(
            back = readFacing(Facing.BACK, getString, getFloat),
            front = readFacing(Facing.FRONT, getString, getFloat),
        )

        /** Writes [settings] for [facing]. A non-custom zoom leaves the stored custom ratio alone. */
        fun write(
            facing: Facing,
            settings: FacingSettings,
            putString: (key: String, value: String) -> Unit,
            putFloat: (key: String, value: Float) -> Unit,
        ) {
            when (val zoom = settings.zoom) {
                ZoomSetting.App -> putString(key(facing, KEY_ZOOM_MODE), APP)
                ZoomSetting.Minimum -> putString(key(facing, KEY_ZOOM_MODE), ZOOM_MINIMUM)
                is ZoomSetting.Custom -> {
                    putString(key(facing, KEY_ZOOM_MODE), ZOOM_CUSTOM)
                    putFloat(key(facing, KEY_ZOOM_RATIO), zoom.ratio)
                }
            }
            putString(key(facing, KEY_STABILIZATION), settings.stabilization.prefValue)
            putString(
                key(facing, KEY_FPS_RANGE),
                when (val fps = settings.fps) {
                    FpsSetting.App -> APP
                    is FpsSetting.Fixed -> fps.range.toPref()
                },
            )
        }

        private fun readFacing(
            facing: Facing,
            getString: (key: String) -> String?,
            getFloat: (key: String, default: Float) -> Float,
        ) = FacingSettings(
            zoom = when (getString(key(facing, KEY_ZOOM_MODE))) {
                ZOOM_MINIMUM -> ZoomSetting.Minimum
                ZOOM_CUSTOM -> ZoomSetting.Custom(getFloat(key(facing, KEY_ZOOM_RATIO), DEFAULT_ZOOM_RATIO))
                else -> ZoomSetting.App
            },
            stabilization = getString(key(facing, KEY_STABILIZATION)).let { value ->
                Stabilization.entries.firstOrNull { it.prefValue == value } ?: Stabilization.APP
            },
            fps = FpsRange.fromPref(getString(key(facing, KEY_FPS_RANGE)))?.let(FpsSetting::Fixed) ?: FpsSetting.App,
        )
    }
}
