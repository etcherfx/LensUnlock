package dev.etcherfx.lensunlock.xposed

import android.content.Context
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.util.Log
import dev.etcherfx.lensunlock.camera.Camera2Caps
import dev.etcherfx.lensunlock.core.AppSettings
import dev.etcherfx.lensunlock.core.OverrideDecision
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Method

/**
 * Builds the hooks. Every decision runs inside try/catch and falls back to the app's own request;
 * chain.proceed() stays outside it, so exceptions from the original method propagate as in stock.
 */
class HookInstaller(
    private val xposed: XposedInterface,
    private val log: ModuleLog,
    private val packageName: String,
    private val settings: () -> AppSettings,
    /** Creates the registry from `ActivityThread.currentApplication()`; tests inject a fake. */
    private val registryFor: (currentApplication: Method) -> CameraRegistry = { defaultRegistry(it, log) },
    private val accessFor: (builder: Any) -> RequestAccess? = { (it as? CaptureRequest.Builder)?.let(::Camera2RequestAccess) },
    private val cameraIdOf: (device: Any?) -> String? = { (it as? CameraDevice)?.id },
) {
    /** Kept so the hooks stay reachable for the life of the process. */
    val handles = mutableListOf<HookHandle>()

    fun install(targets: FrameworkTargets) {
        val build = available(targets.build, "CaptureRequest.Builder.build()")
        val creates = available(targets.createCaptureRequest, "CameraDeviceImpl.createCaptureRequest()")
        val currentApplication = available(targets.currentApplication, "ActivityThread.currentApplication()")
        if (build == null || creates == null || currentApplication == null) {
            log.line(Log.WARN, "Overrides disabled: a required target is unavailable")
            return
        }
        val registry = registryFor(currentApplication)
        creates.forEach { method -> hook(method, createHooker(registry))?.let { handles += it } }
        hook(build, buildHooker(registry))?.let { handles += it }
    }

    /**
     * Deoptimizes every `onConfigured` of [className], if the app has it, so ART can't inline
     * `CaptureRequest.Builder.build()` into the place WebRTC builds its repeating request.
     */
    fun deoptimize(classLoader: ClassLoader, className: String) {
        val callback = try {
            Class.forName(className, false, classLoader)
        } catch (_: ClassNotFoundException) {
            return
        } catch (t: Throwable) {
            log.line(Log.WARN, "Could not load $className", t)
            return
        }
        callback.declaredMethods.filter { it.name == "onConfigured" }.forEach { method ->
            try {
                val done = xposed.deoptimize(method)
                log.line(if (done) Log.INFO else Log.WARN, "Deoptimized ${describe(method)}: $done")
            } catch (t: Throwable) {
                log.line(Log.WARN, "Could not deoptimize ${describe(method)}", t)
            }
        }
    }

    private fun <T> available(target: Target<T>, name: String): T? = when (target) {
        is Target.Available -> target.value
        is Target.Unavailable -> {
            log.line(Log.WARN, "$name unavailable, not hooked: ${target.reason}")
            null
        }
    }

    private fun hook(method: Method, hooker: Hooker): HookHandle? = try {
        xposed.hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(hooker).also {
            log.line(Log.INFO, "Hooked ${describe(method)}")
        }
    } catch (t: Throwable) {
        log.once.log("hook-${describe(method)}", Log.WARN, t) { "Could not hook ${describe(method)}" }
        null
    }

    private fun createHooker(registry: CameraRegistry) = Hooker { chain ->
        val builder = chain.proceed()
        var cameraId: String? = null
        try {
            cameraId = cameraIdOf(chain.thisObject)
            if (builder != null && cameraId != null) registry.track(builder, cameraId)
        } catch (t: Throwable) {
            log.once.log("track-$cameraId", Log.WARN, t) {
                "Could not read camera $cameraId characteristics; its requests stay untouched"
            }
        }
        builder
    }

    private fun buildHooker(registry: CameraRegistry) = Hooker { chain ->
        var patch: RequestPatch? = null
        try {
            val builder = chain.thisObject
            val camera = builder?.let(registry::lookup)
            val access = camera?.let { accessFor(builder) }
            if (camera != null && access != null) {
                val prepared = RequestPatch.prepare(access, settings(), camera.caps)
                logSkips(camera, prepared)
                if (!prepared.decision.isEmpty) {
                    patch = prepared
                    prepared.apply()
                    logApplied(camera, prepared)
                }
            }
        } catch (t: Throwable) {
            log.once.log("patch", Log.WARN, t) { "Could not apply overrides; requests keep the app's values" }
            restore(patch)
            patch = null
        }
        try {
            chain.proceed()
        } finally {
            restore(patch)
        }
    }

    private fun restore(patch: RequestPatch?) {
        try {
            patch?.restore()
        } catch (t: Throwable) {
            log.once.log("restore", Log.WARN, t) { "Could not restore the app's request values" }
        }
    }

    private fun logSkips(camera: CameraInfo, patch: RequestPatch) {
        for (skip in patch.decision.skips) {
            log.once.log("skip-$packageName-${camera.caps.facing}-${skip.setting}-${skip.reason}", Log.INFO) {
                "${cameraName(camera)}: not overriding ${skip.setting.name.lowercase()}: ${skip.reason}"
            }
        }
    }

    private fun logApplied(camera: CameraInfo, patch: RequestPatch) {
        val before = patch.before
        val decision = patch.decision
        val changes = listOfNotNull(
            decision.zoom?.let { "zoom ${before.zoom ?: "unset"} → $it" },
            decision.stabilization?.let {
                "stabilization ${OverrideDecision.stabilizationName(before.stabilization)} → " +
                    OverrideDecision.stabilizationName(it)
            },
            decision.fps?.let { "fps ${before.fps ?: "unset"} → $it" },
        )
        val key = "apply-${camera.id}-$before-${decision.copy(skips = emptyList())}"
        log.once.log(key, Log.INFO) { "${cameraName(camera)}: ${changes.joinToString()}" }
    }

    private fun cameraName(camera: CameraInfo) = "Camera ${camera.id} (${camera.caps.facing.name.lowercase()})"

    private companion object {
        fun defaultRegistry(currentApplication: Method, log: ModuleLog) = CameraRegistry(
            loader = { cameraId ->
                val context = currentApplication.invoke(null) as? Context
                    ?: throw IllegalStateException("No application context yet")
                val manager = context.getSystemService(CameraManager::class.java)
                    ?: throw IllegalStateException("No CameraManager")
                Camera2Caps.read(manager.getCameraCharacteristics(cameraId))
            },
            log = log,
        )

        fun describe(method: Method) =
            "${method.declaringClass.simpleName}.${method.name}(${method.parameterTypes.joinToString { it.simpleName }})"
    }
}
