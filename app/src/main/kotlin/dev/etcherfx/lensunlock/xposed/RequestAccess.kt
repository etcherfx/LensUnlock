package dev.etcherfx.lensunlock.xposed

import android.graphics.Rect
import android.hardware.camera2.CaptureRequest
import android.util.Range
import dev.etcherfx.lensunlock.core.CropRegion
import dev.etcherfx.lensunlock.core.FpsRange
import dev.etcherfx.lensunlock.core.RequestValues

/** Reads and writes the keys the module overrides. Writing null erases the key. */
interface RequestAccess {
    fun read(): RequestValues
    fun setZoom(value: Float?)
    fun setStabilization(value: Int?)
    fun setFps(value: FpsRange?)
}

class Camera2RequestAccess(private val builder: CaptureRequest.Builder) : RequestAccess {
    override fun read() = RequestValues(
        zoom = builder.get(CaptureRequest.CONTROL_ZOOM_RATIO),
        stabilization = builder.get(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE),
        fps = builder.get(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE)?.let { FpsRange(it.lower, it.upper) },
        cropRegion = builder.get(CaptureRequest.SCALER_CROP_REGION)?.let(::cropRegion),
    )

    override fun setZoom(value: Float?) {
        builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, value)
    }

    override fun setStabilization(value: Int?) {
        builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, value)
    }

    override fun setFps(value: FpsRange?) {
        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, value?.let { Range(it.min, it.max) })
    }

    private fun cropRegion(rect: Rect) = CropRegion(rect.left, rect.top, rect.right, rect.bottom)
}
