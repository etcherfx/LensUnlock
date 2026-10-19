package dev.etcherfx.lensunlock.xposed

import android.util.Log
import dev.etcherfx.lensunlock.camera.Camera2Caps
import dev.etcherfx.lensunlock.core.CameraCaps
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

class CameraInfo(val id: String, val caps: CameraCaps)

/**
 * Remembers which camera each `CaptureRequest.Builder` belongs to. Builders are held weakly, so a
 * dropped builder is forgotten; characteristics are cached per camera ID once a lookup succeeds.
 */
class CameraRegistry(private val loader: (cameraId: String) -> CameraCaps, private val log: ModuleLog) {
    private val builders = Collections.synchronizedMap(WeakHashMap<Any, CameraInfo>())
    private val caps = ConcurrentHashMap<String, CameraCaps>()

    /** Maps [builder] to [cameraId]. Throws if the camera's characteristics can't be read. */
    fun track(builder: Any, cameraId: String) {
        builders[builder] = CameraInfo(cameraId, capsFor(cameraId))
    }

    fun lookup(builder: Any): CameraInfo? = builders[builder]

    private fun capsFor(cameraId: String): CameraCaps = caps[cameraId] ?: loader(cameraId).also {
        caps[cameraId] = it
        log.once.log("caps-$cameraId", Log.INFO) { "Camera $cameraId: ${Camera2Caps.describe(it)}" }
    }
}
