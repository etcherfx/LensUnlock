package dev.etcherfx.lensunlock.xposed

import dev.etcherfx.lensunlock.core.AppSettings
import dev.etcherfx.lensunlock.core.CameraCaps
import dev.etcherfx.lensunlock.core.Decision
import dev.etcherfx.lensunlock.core.OverrideDecision
import dev.etcherfx.lensunlock.core.RequestValues

/**
 * Applies a [Decision] to a builder for one `build()` call, then puts the app's values back, so the
 * app's builder never keeps the module's values and turning a setting off returns to the app's own.
 */
class RequestPatch(private val access: RequestAccess, val before: RequestValues, val decision: Decision) {
    private var zoomWritten = false
    private var stabilizationWritten = false
    private var fpsWritten = false

    fun apply() {
        // Marked before each write, so a write that throws halfway is still restored.
        decision.zoom?.let {
            zoomWritten = true
            access.setZoom(it)
        }
        decision.stabilization?.let {
            stabilizationWritten = true
            access.setStabilization(it)
        }
        decision.fps?.let {
            fpsWritten = true
            access.setFps(it)
        }
    }

    /** Restores every written key, erasing the ones the app hadn't set. Throws the first failure. */
    fun restore() {
        var failure: Throwable? = null
        fun attempt(written: Boolean, write: () -> Unit) {
            if (!written) return
            try {
                write()
            } catch (t: Throwable) {
                if (failure == null) failure = t
            }
        }
        attempt(zoomWritten) { access.setZoom(before.zoom) }
        attempt(stabilizationWritten) { access.setStabilization(before.stabilization) }
        attempt(fpsWritten) { access.setFps(before.fps) }
        zoomWritten = false
        stabilizationWritten = false
        fpsWritten = false
        failure?.let { throw it }
    }

    companion object {
        /** Reads the request and decides; nothing is written until [apply]. */
        fun prepare(access: RequestAccess, settings: AppSettings, caps: CameraCaps): RequestPatch {
            val before = access.read()
            return RequestPatch(access, before, OverrideDecision.decide(settings, caps, before))
        }
    }
}
