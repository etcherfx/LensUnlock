package dev.etcherfx.lensunlock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    private fun read(strings: Map<String, String>, floats: Map<String, Float> = emptyMap()) =
        AppSettings.fromLookup({ strings[it] }, { key, default -> floats[key] ?: default })

    @Test
    fun missingKeysMeanTheAppDefaults() {
        val settings = read(emptyMap())
        assertEquals(AppSettings.DEFAULT, settings)
        assertTrue(settings.back.isAppDefault)
        assertTrue(settings.front.isAppDefault)
    }

    @Test
    fun storedValuesRoundTrip() {
        val stored = AppSettings(
            back = FacingSettings(ZoomSetting.Minimum, Stabilization.PREVIEW, FpsSetting.Fixed(FpsRange(30, 30))),
            front = FacingSettings(ZoomSetting.Custom(1.5f), Stabilization.OFF, FpsSetting.App),
        )
        val strings = mutableMapOf<String, String>()
        val floats = mutableMapOf<String, Float>()
        for (facing in listOf(Facing.BACK, Facing.FRONT)) {
            AppSettings.write(facing, stored.forFacing(facing)!!, { k, v -> strings[k] = v }, { k, v -> floats[k] = v })
        }

        assertEquals("minimum", strings["back_zoom_mode"])
        assertEquals("preview", strings["back_stabilization"])
        assertEquals("30-30", strings["back_fps_range"])
        assertEquals("custom", strings["front_zoom_mode"])
        assertEquals(1.5f, floats["front_zoom_ratio"])
        assertEquals("app", strings["front_fps_range"])
        assertEquals(stored, read(strings, floats))
    }

    @Test
    fun everyStabilizationValueRoundTrips() {
        for (mode in Stabilization.entries) {
            val settings = read(mapOf("back_stabilization" to mode.prefValue))
            assertEquals(mode, settings.back.stabilization)
        }
    }

    @Test
    fun invalidValuesFallBackToTheAppDefault() {
        for (fps in listOf("", "30", "30-", "-30", "a-b", "30-15", "0-30", "15-30-60")) {
            val settings = read(
                mapOf(
                    "back_zoom_mode" to "widest",
                    "back_stabilization" to "PREVIEW",
                    "back_fps_range" to fps,
                ),
            )
            assertEquals("fps '$fps'", FacingSettings.DEFAULT, settings.back)
        }
    }

    @Test
    fun customZoomWithoutARatioUsesOne() {
        assertEquals(ZoomSetting.Custom(1.0f), read(mapOf("back_zoom_mode" to "custom")).back.zoom)
    }

    @Test
    fun eachFacingIsReadIndependently() {
        val settings = read(mapOf("front_zoom_mode" to "minimum", "back_fps_range" to "15-30"))
        assertEquals(FacingSettings(fps = FpsSetting.Fixed(FpsRange(15, 30))), settings.back)
        assertEquals(FacingSettings(zoom = ZoomSetting.Minimum), settings.front)
    }

    @Test
    fun customZoomWriteKeepsTheRatioWhenSwitchingAway() {
        val floats = mutableMapOf("back_zoom_ratio" to 2.0f)
        AppSettings.write(Facing.BACK, FacingSettings(zoom = ZoomSetting.Minimum), { _, _ -> }, { k, v -> floats[k] = v })
        assertEquals(mapOf("back_zoom_ratio" to 2.0f), floats)
    }
}
