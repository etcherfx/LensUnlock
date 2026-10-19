package dev.etcherfx.lensunlock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverrideDecisionTest {
    // Shaped like a Pixel 10 Pro: back [0.508, 30], front [0.896, 10].
    private val back = CameraCaps(
        facing = Facing.BACK,
        zoomRange = ZoomRange(0.508f, 30f),
        stabilizationModes = setOf(0, 1, 2),
        fpsRanges = listOf(FpsRange(15, 30), FpsRange(30, 30), FpsRange(60, 60)),
        activeArray = ArraySize(4000, 3000),
    )
    private val front = back.copy(facing = Facing.FRONT, zoomRange = ZoomRange(0.896f, 10f))
    private val stock = RequestValues(zoom = 1.0f, stabilization = 0, fps = FpsRange(15, 30), cropRegion = CropRegion(0, 0, 4000, 3000))

    private fun backOnly(settings: FacingSettings) = AppSettings(back = settings)

    private fun decide(settings: FacingSettings, caps: CameraCaps = back, current: RequestValues = stock) =
        OverrideDecision.decide(backOnly(settings), caps, current)

    @Test
    fun allAppDefaultsDecideNothing() {
        assertEquals(Decision.NONE, OverrideDecision.decide(AppSettings.DEFAULT, back, stock))
        assertTrue(Decision.NONE.isEmpty)
    }

    @Test
    fun minimumIsTheRangeLowerBound() {
        assertEquals(0.508f, decide(FacingSettings(zoom = ZoomSetting.Minimum)).zoom)
    }

    @Test
    fun customIsClampedAtBothEnds() {
        assertEquals(0.508f, decide(FacingSettings(zoom = ZoomSetting.Custom(0.1f))).zoom)
        assertEquals(30f, decide(FacingSettings(zoom = ZoomSetting.Custom(100f))).zoom)
        assertEquals(2f, decide(FacingSettings(zoom = ZoomSetting.Custom(2f))).zoom)
    }

    @Test
    fun invalidCustomRatiosAreSkipped() {
        for (ratio in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f)) {
            val decision = decide(FacingSettings(zoom = ZoomSetting.Custom(ratio)))
            assertNull("ratio $ratio", decision.zoom)
            assertEquals(listOf(Setting.ZOOM), decision.skips.map { it.setting })
        }
    }

    @Test
    fun noZoomRangeSkipsZoom() {
        val decision = decide(FacingSettings(zoom = ZoomSetting.Minimum), back.copy(zoomRange = null))
        assertNull(decision.zoom)
        assertEquals(Setting.ZOOM, decision.skips.single().setting)
    }

    @Test
    fun unsupportedValuesAreSkippedAndTheOthersStillApply() {
        val caps = back.copy(stabilizationModes = setOf(0, 1), fpsRanges = listOf(FpsRange(15, 30)))
        val decision = decide(
            FacingSettings(ZoomSetting.Minimum, Stabilization.PREVIEW, FpsSetting.Fixed(FpsRange(30, 30))),
            caps,
        )
        assertEquals(0.508f, decision.zoom)
        assertNull(decision.stabilization)
        assertNull(decision.fps)
        assertEquals(listOf(Setting.STABILIZATION, Setting.FPS), decision.skips.map { it.setting })
    }

    @Test
    fun supportedValuesAreApplied() {
        val decision = decide(FacingSettings(ZoomSetting.App, Stabilization.PREVIEW, FpsSetting.Fixed(FpsRange(30, 30))))
        assertEquals(Decision(stabilization = 2, fps = FpsRange(30, 30)), decision)
        assertEquals(1, decide(FacingSettings(stabilization = Stabilization.ON)).stabilization)
        assertEquals(0, decide(FacingSettings(stabilization = Stabilization.OFF)).stabilization)
    }

    @Test
    fun eachFacingUsesItsOwnSettings() {
        val settings = AppSettings(
            back = FacingSettings(stabilization = Stabilization.PREVIEW),
            front = FacingSettings(zoom = ZoomSetting.Minimum),
        )
        assertEquals(Decision(stabilization = 2), OverrideDecision.decide(settings, back, stock))
        assertEquals(Decision(zoom = 0.896f), OverrideDecision.decide(settings, front, stock))
    }

    @Test
    fun otherFacingIsUntouched() {
        val all = FacingSettings(ZoomSetting.Minimum, Stabilization.PREVIEW, FpsSetting.Fixed(FpsRange(30, 30)))
        val decision = OverrideDecision.decide(AppSettings(all, all), back.copy(facing = Facing.OTHER), stock)
        assertEquals(Decision.NONE, decision)
    }

    @Test
    fun aForeignCropRegionSkipsOnlyZoom() {
        val cropped = stock.copy(cropRegion = CropRegion(1000, 750, 3000, 2250))
        val decision = decide(FacingSettings(ZoomSetting.Minimum, Stabilization.PREVIEW), current = cropped)
        assertNull(decision.zoom)
        assertEquals(2, decision.stabilization)
        assertEquals(Setting.ZOOM, decision.skips.single().setting)
    }

    @Test
    fun aFullArrayOrMissingCropRegionAllowsZoom() {
        val settings = FacingSettings(zoom = ZoomSetting.Minimum)
        assertEquals(0.508f, decide(settings).zoom)
        assertEquals(0.508f, decide(settings, current = stock.copy(cropRegion = null)).zoom)
        assertEquals(0.508f, decide(settings, back.copy(activeArray = null), stock.copy(cropRegion = null)).zoom)
    }

    @Test
    fun aCropRegionWithAnUnknownActiveArraySkipsZoom() {
        val decision = decide(FacingSettings(zoom = ZoomSetting.Minimum), back.copy(activeArray = null))
        assertNull(decision.zoom)
        assertEquals(Setting.ZOOM, decision.skips.single().setting)
    }
}
