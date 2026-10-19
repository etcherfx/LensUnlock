package dev.etcherfx.lensunlock.core

import java.util.concurrent.ConcurrentHashMap

/** Forwards a message to [sink] only the first time its key is seen. */
class OnceLogger(private val sink: (level: Int, message: String, error: Throwable?) -> Unit) {
    private val seen = ConcurrentHashMap.newKeySet<String>()

    /** Returns true if this call logged, false if [key] was already logged. */
    fun log(key: String, level: Int, error: Throwable? = null, message: () -> String): Boolean {
        if (!seen.add(key)) return false
        sink(level, message(), error)
        return true
    }
}
