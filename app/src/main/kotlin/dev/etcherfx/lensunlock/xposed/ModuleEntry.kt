package dev.etcherfx.lensunlock.xposed

import android.util.Log
import dev.etcherfx.lensunlock.BuildConfig
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.concurrent.atomic.AtomicBoolean

/** Entry point listed in META-INF/xposed/java_init.list. Hooks the scoped app's process once. */
class ModuleEntry : XposedModule() {
    private val installed = AtomicBoolean(false)

    // Kept for the life of the process: the hooks read the bridge's settings.
    private var prefs: PrefsBridge? = null
    private var hooks: HookInstaller? = null

    override fun onPackageReady(param: PackageReadyParam) {
        try {
            if (!param.isFirstPackage || param.packageName == BuildConfig.APPLICATION_ID) return
        } catch (_: Throwable) {
            return
        }
        if (!installed.compareAndSet(false, true)) return
        try {
            val log = ModuleLog(this)
            val packageName = param.packageName
            log.line(
                Log.INFO,
                "LensUnlock ${BuildConfig.VERSION_NAME} in $packageName on $frameworkName $frameworkVersion " +
                    "($frameworkVersionCode, API $apiVersion, properties 0x${frameworkProperties.toString(16)})",
            )
            val bridge = PrefsBridge(this, packageName, log).also { it.start() }
            prefs = bridge
            log.line(Log.INFO, "Settings: ${PrefsBridge.describe(bridge.settings)}")
            val installer = HookInstaller(this, log, packageName, { bridge.settings })
            installer.install(FrameworkTargets.resolve(param.classLoader))
            installer.deoptimize(param.classLoader, WEBRTC_CAPTURE_CALLBACK)
            hooks = installer
        } catch (t: Throwable) {
            try {
                log(Log.ERROR, ModuleLog.TAG, "Setup failed; module inactive", t)
            } catch (_: Throwable) {
                // Nothing else is safe to do in the app's process.
            }
        }
    }

    private companion object {
        /** Stock WebRTC builds its repeating request in this callback's `onConfigured`. */
        const val WEBRTC_CAPTURE_CALLBACK = "org.webrtc.Camera2Session\$CaptureSessionCallback"
    }
}
