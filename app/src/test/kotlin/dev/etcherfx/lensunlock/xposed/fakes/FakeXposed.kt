package dev.etcherfx.lensunlock.xposed.fakes

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.ParcelFileDescriptor
import dev.etcherfx.lensunlock.core.FpsRange
import dev.etcherfx.lensunlock.core.RequestValues
import dev.etcherfx.lensunlock.xposed.RequestAccess
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.HookBuilder
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

data class LogLine(val level: Int, val tag: String, val message: String, val error: Throwable?)

class RecordedHook(val executable: Executable, val mode: ExceptionMode?, val hooker: Hooker)

/** Records hooks, deoptimizations and log lines; everything else is unsupported. */
class FakeXposed : XposedInterface {
    val hooks = mutableListOf<RecordedHook>()
    val deoptimized = mutableListOf<Executable>()
    val lines = mutableListOf<LogLine>()

    fun hookerFor(name: String, parameterCount: Int): Hooker =
        hooks.single { it.executable.name == name && it.executable.parameterCount == parameterCount }.hooker

    override fun getFrameworkName() = "Fake"
    override fun getFrameworkVersion() = "1.0"
    override fun getFrameworkVersionCode() = 1L
    override fun getFrameworkProperties() = XposedInterface.PROP_CAP_REMOTE

    override fun hook(origin: Executable): HookBuilder = object : HookBuilder {
        private var mode: ExceptionMode? = null
        override fun setPriority(priority: Int) = this
        override fun setExceptionMode(mode: ExceptionMode): HookBuilder = apply { this.mode = mode }
        override fun setId(id: String?) = this
        override fun intercept(hooker: Hooker): HookHandle {
            hooks += RecordedHook(origin, mode, hooker)
            return FakeHookHandle(origin)
        }
    }

    override fun hookClassInitializer(origin: Class<*>): HookBuilder = throw UnsupportedOperationException()

    override fun deoptimize(executable: Executable): Boolean {
        deoptimized += executable
        return true
    }

    override fun getInvoker(method: Method): XposedInterface.Invoker<*, Method> = throw UnsupportedOperationException()
    override fun <T : Any?> getInvoker(constructor: Constructor<T>): XposedInterface.CtorInvoker<T> =
        throw UnsupportedOperationException()

    override fun log(priority: Int, tag: String?, msg: String) {
        lines += LogLine(priority, tag.orEmpty(), msg, null)
    }

    override fun log(priority: Int, tag: String?, msg: String, tr: Throwable?) {
        lines += LogLine(priority, tag.orEmpty(), msg, tr)
    }

    override fun getModuleApplicationInfo(): ApplicationInfo = throw UnsupportedOperationException()
    override fun getRemotePreferences(group: String): SharedPreferences = throw UnsupportedOperationException()
    override fun listRemoteFiles(): Array<String> = emptyArray()
    override fun openRemoteFile(name: String): ParcelFileDescriptor = throw UnsupportedOperationException()
}

private class FakeHookHandle(private val origin: Executable) : HookHandle {
    override fun getExecutable() = origin
    override fun unhook() {}
    override fun getId(): String = throw UnsupportedOperationException()
    override fun replaceHook(hooker: Hooker): HookHandle = throw UnsupportedOperationException()
}

/** A chain whose original method is [original]. */
class FakeChain(private val self: Any?, private val original: () -> Any?) : Chain {
    var proceeded = 0
        private set

    override fun getExecutable(): Executable = throw UnsupportedOperationException()
    override fun getThisObject() = self
    override fun getArgs(): List<Any?> = emptyList()
    override fun getArg(index: Int): Any? = throw IndexOutOfBoundsException()
    override fun proceed(): Any? {
        proceeded++
        return original()
    }

    override fun proceed(args: Array<out Any?>): Any? = proceed()
    override fun proceedWith(thisObject: Any): Any? = proceed()
    override fun proceedWith(thisObject: Any, args: Array<out Any?>): Any? = proceed()
}

/** A request whose keys are plain fields; null means unset. */
class FakeRequestAccess(var values: RequestValues) : RequestAccess {
    var writes = 0
        private set
    var failOnFps = false

    override fun read() = values

    override fun setZoom(value: Float?) {
        writes++
        values = values.copy(zoom = value)
    }

    override fun setStabilization(value: Int?) {
        writes++
        values = values.copy(stabilization = value)
    }

    override fun setFps(value: FpsRange?) {
        writes++
        check(!failOnFps) { "fps write failed" }
        values = values.copy(fps = value)
    }
}
