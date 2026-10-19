package dev.etcherfx.lensunlock.xposed

import dev.etcherfx.lensunlock.core.OnceLogger
import io.github.libxposed.api.XposedInterface

/** The framework's module log, under the tag [TAG]. */
class ModuleLog(private val xposed: XposedInterface) {
    val once = OnceLogger { level, message, error -> line(level, message, error) }

    fun line(level: Int, message: String, error: Throwable? = null) {
        if (error == null) xposed.log(level, TAG, message) else xposed.log(level, TAG, message, error)
    }

    companion object {
        const val TAG = "LensUnlock"
    }
}
