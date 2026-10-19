package dev.etcherfx.lensunlock.core

enum class Facing { BACK, FRONT, OTHER }

/** An AE target fps range, as in `CONTROL_AE_TARGET_FPS_RANGE`. */
data class FpsRange(val min: Int, val max: Int) {
    /** The preference form, `min-max`. */
    fun toPref() = "$min-$max"

    override fun toString() = "[$min,$max]"

    companion object {
        /** Parses `min-max`; returns null for anything else, including a missing value. */
        fun fromPref(value: String?): FpsRange? {
            val parts = value?.split('-') ?: return null
            if (parts.size != 2) return null
            val min = parts[0].toIntOrNull() ?: return null
            val max = parts[1].toIntOrNull() ?: return null
            return if (min in 1..max) FpsRange(min, max) else null
        }
    }
}

data class ZoomRange(val min: Float, val max: Float)

/** Width and height of the sensor's active array, as in `SENSOR_INFO_ACTIVE_ARRAY_SIZE`. */
data class ArraySize(val width: Int, val height: Int)

/** A crop region in active-array coordinates, as in `SCALER_CROP_REGION`. */
data class CropRegion(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** What a camera supports, read from its `CameraCharacteristics`. */
data class CameraCaps(
    val facing: Facing,
    /** `CONTROL_ZOOM_RATIO_RANGE`, or null if the camera doesn't report one. */
    val zoomRange: ZoomRange?,
    /** `CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES`. */
    val stabilizationModes: Set<Int>,
    /** `CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES`. */
    val fpsRanges: List<FpsRange>,
    val activeArray: ArraySize?,
)

/** The values a capture request currently holds; null means the key isn't set. */
data class RequestValues(
    val zoom: Float?,
    val stabilization: Int?,
    val fps: FpsRange?,
    val cropRegion: CropRegion?,
)
