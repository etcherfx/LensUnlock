package dev.etcherfx.lensunlock.xposed

import android.content.SharedPreferences
import android.util.Log
import dev.etcherfx.lensunlock.core.AppSettings
import dev.etcherfx.lensunlock.core.FacingSettings
import dev.etcherfx.lensunlock.core.FpsSetting
import dev.etcherfx.lensunlock.core.Stabilization
import dev.etcherfx.lensunlock.core.ZoomSetting
import io.github.libxposed.api.XposedInterface

/**
 * Publishes the app's remote preferences group (named after [packageName]) into a volatile
 * [AppSettings] that the hooks read. If remote preferences are unavailable, nothing is overridden.
 */
class PrefsBridge(
    private val xposed: XposedInterface,
    private val packageName: String,
    private val log: ModuleLog,
) {
    @Volatile
    var settings: AppSettings = AppSettings.DEFAULT
        private set

    // Strong references: SharedPreferences may hold listeners weakly.
    private var prefs: SharedPreferences? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun start() {
        try {
            if (xposed.frameworkProperties and XposedInterface.PROP_CAP_REMOTE == 0L) {
                log.once.log("prefs-unsupported", Log.WARN) {
                    "Remote preferences unsupported by the framework; nothing is overridden"
                }
                return
            }
            val remote = xposed.getRemotePreferences(packageName)
            settings = read(remote)
            val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { changed, _ ->
                try {
                    settings = read(changed)
                    log.line(Log.INFO, "Settings changed: ${describe(settings)}")
                } catch (t: Throwable) {
                    log.once.log("prefs-reload", Log.WARN, t) { "Could not reload settings; keeping ${describe(settings)}" }
                }
            }
            remote.registerOnSharedPreferenceChangeListener(changeListener)
            prefs = remote
            listener = changeListener
        } catch (t: Throwable) {
            log.once.log("prefs-start", Log.WARN, t) {
                "Could not read remote preferences; using ${describe(settings)} until the app restarts"
            }
        }
    }

    private fun read(prefs: SharedPreferences) = AppSettings.fromLookup(
        getString = { key -> prefs.getString(key, null) },
        getFloat = { key, default -> prefs.getFloat(key, default) },
    )

    companion object {
        fun describe(settings: AppSettings) = "back: ${describe(settings.back)}; front: ${describe(settings.front)}"

        private fun describe(facing: FacingSettings): String {
            if (facing.isAppDefault) return "app defaults"
            val zoom = when (val z = facing.zoom) {
                ZoomSetting.App -> "app"
                ZoomSetting.Minimum -> "minimum"
                is ZoomSetting.Custom -> "${z.ratio}"
            }
            val fps = when (val f = facing.fps) {
                FpsSetting.App -> "app"
                is FpsSetting.Fixed -> "${f.range}"
            }
            val stabilization = if (facing.stabilization == Stabilization.APP) "app" else facing.stabilization.prefValue
            return "zoom $zoom, stabilization $stabilization, fps $fps"
        }
    }
}
