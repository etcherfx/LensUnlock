package dev.etcherfx.lensunlock

import android.app.Application
import android.os.Handler
import android.os.Looper
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

/** Holds the framework's service connection, which the framework binds once per app process. */
class ModuleApp : Application(), XposedServiceHelper.OnServiceListener {
    @Volatile
    var service: XposedService? = null
        private set

    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        this.service = service
        notifyChanged()
    }

    override fun onServiceDied(service: XposedService) {
        if (this.service === service) this.service = null
        notifyChanged()
    }

    /** [listener] runs on the main thread whenever the service connects or dies. */
    fun addServiceListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeServiceListener(listener: () -> Unit) {
        listeners -= listener
    }

    private fun notifyChanged() {
        mainHandler.post { listeners.forEach { it() } }
    }
}
