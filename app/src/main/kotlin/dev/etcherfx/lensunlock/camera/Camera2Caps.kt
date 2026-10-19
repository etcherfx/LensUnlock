package dev.etcherfx.lensunlock.camera

import android.hardware.camera2.CameraCharacteristics
import dev.etcherfx.lensunlock.core.ArraySize
import dev.etcherfx.lensunlock.core.CameraCaps
import dev.etcherfx.lensunlock.core.Facing
import dev.etcherfx.lensunlock.core.FpsRange
import dev.etcherfx.lensunlock.core.ZoomRange

/** Reads the keys the module needs from Camera2 characteristics. Shared by the hooks and the settings screen. */
object Camera2Caps {
    fun read(characteristics: CameraCharacteristics) = CameraCaps(
        facing = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_BACK -> Facing.BACK
            CameraCharacteristics.LENS_FACING_FRONT -> Facing.FRONT
            else -> Facing.OTHER
        },
        zoomRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            ?.let { ZoomRange(it.lower, it.upper) },
        stabilizationModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            ?.toSet() ?: emptySet(),
        fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.map { FpsRange(it.lower, it.upper) } ?: emptyList(),
        activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            ?.let { ArraySize(it.width(), it.height()) },
    )

    fun describe(caps: CameraCaps) =
        "${caps.facing.name.lowercase()}, zoom ${caps.zoomRange?.let { "[${it.min}, ${it.max}]" } ?: "unsupported"}, " +
            "stabilization modes ${caps.stabilizationModes.sorted()}, fps ranges ${caps.fpsRanges.joinToString()}, " +
            "active array ${caps.activeArray?.let { "${it.width}x${it.height}" } ?: "unknown"}"
}
