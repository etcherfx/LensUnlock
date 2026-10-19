package dev.etcherfx.lensunlock.core

/** What to write into a request. A null value leaves that key untouched. */
data class Decision(
    val zoom: Float? = null,
    val stabilization: Int? = null,
    val fps: FpsRange? = null,
    /** Settings that are on but can't be applied to this camera or request. */
    val skips: List<Skip> = emptyList(),
) {
    /** Nothing to write. */
    val isEmpty get() = zoom == null && stabilization == null && fps == null

    companion object {
        val NONE = Decision()
    }
}

enum class Setting { ZOOM, STABILIZATION, FPS }

data class Skip(val setting: Setting, val reason: String)

object OverrideDecision {
    /**
     * Decides the overrides for one request from [settings], the camera's [caps] and the request's
     * [current] values. Never returns a value the camera doesn't support: such settings become skips.
     */
    fun decide(settings: AppSettings, caps: CameraCaps, current: RequestValues): Decision {
        val facing = settings.forFacing(caps.facing) ?: return Decision.NONE
        if (facing.isAppDefault) return Decision.NONE
        val skips = mutableListOf<Skip>()
        return Decision(
            zoom = zoom(facing.zoom, caps, current, skips),
            stabilization = stabilization(facing.stabilization, caps, skips),
            fps = fps(facing.fps, caps, skips),
            skips = skips,
        )
    }

    private fun zoom(setting: ZoomSetting, caps: CameraCaps, current: RequestValues, skips: MutableList<Skip>): Float? {
        if (setting == ZoomSetting.App) return null
        val range = caps.zoomRange ?: return skip(skips, Setting.ZOOM, "the camera has no zoom ratio range")
        val crop = current.cropRegion
        if (crop != null) {
            // Zoom ratio and crop region compound, so an app that crops keeps its own framing.
            val array = caps.activeArray ?: return skip(skips, Setting.ZOOM, "the app sets a crop region")
            if (crop != CropRegion(0, 0, array.width, array.height)) {
                return skip(skips, Setting.ZOOM, "the app sets a crop region")
            }
        }
        return when (setting) {
            ZoomSetting.Minimum -> range.min
            is ZoomSetting.Custom -> {
                val ratio = setting.ratio
                if (!ratio.isFinite() || ratio <= 0f) {
                    skip(skips, Setting.ZOOM, "invalid custom ratio $ratio")
                } else {
                    ratio.coerceIn(range.min, range.max)
                }
            }
            ZoomSetting.App -> null
        }
    }

    private fun stabilization(setting: Stabilization, caps: CameraCaps, skips: MutableList<Skip>): Int? {
        val mode = setting.mode ?: return null
        return if (mode in caps.stabilizationModes) {
            mode
        } else {
            skip(skips, Setting.STABILIZATION, "the camera doesn't support ${stabilizationName(mode)}")
        }
    }

    private fun fps(setting: FpsSetting, caps: CameraCaps, skips: MutableList<Skip>): FpsRange? {
        if (setting !is FpsSetting.Fixed) return null
        return if (setting.range in caps.fpsRanges) {
            setting.range
        } else {
            skip(skips, Setting.FPS, "the camera doesn't support fps range ${setting.range}")
        }
    }

    private fun <T> skip(skips: MutableList<Skip>, setting: Setting, reason: String): T? {
        skips += Skip(setting, reason)
        return null
    }

    /** The Camera2 constant name for a video stabilization mode. */
    fun stabilizationName(mode: Int?): String = when (mode) {
        null -> "unset"
        0 -> "OFF"
        1 -> "ON"
        2 -> "PREVIEW_STABILIZATION"
        else -> "mode $mode"
    }
}
