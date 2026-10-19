package dev.etcherfx.lensunlock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnceLoggerTest {
    @Test
    fun logsEachKeyOnce() {
        val lines = mutableListOf<String>()
        val once = OnceLogger { _, message, _ -> lines += message }

        assertTrue(once.log("a", 4) { "first a" })
        assertFalse(once.log("a", 4) { "second a" })
        assertTrue(once.log("b", 5) { "first b" })

        assertEquals(listOf("first a", "first b"), lines)
    }

    @Test
    fun skipsBuildingTheMessageForARepeatedKey() {
        val once = OnceLogger { _, _, _ -> }
        once.log("a", 4) { "first" }
        once.log("a", 4) { error("must not be built") }
    }
}
