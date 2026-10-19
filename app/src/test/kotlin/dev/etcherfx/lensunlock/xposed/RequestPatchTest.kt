package dev.etcherfx.lensunlock.xposed

import dev.etcherfx.lensunlock.core.AppSettings
import dev.etcherfx.lensunlock.core.ArraySize
import dev.etcherfx.lensunlock.core.CameraCaps
import dev.etcherfx.lensunlock.core.Facing
import dev.etcherfx.lensunlock.core.FacingSettings
import dev.etcherfx.lensunlock.core.FpsRange
import dev.etcherfx.lensunlock.core.FpsSetting
import dev.etcherfx.lensunlock.core.RequestValues
import dev.etcherfx.lensunlock.core.Stabilization
import dev.etcherfx.lensunlock.core.ZoomRange
import dev.etcherfx.lensunlock.core.ZoomSetting
import dev.etcherfx.lensunlock.xposed.fakes.FakeRequestAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RequestPatchTest {
    private val caps = CameraCaps(
        facing = Facing.BACK,
        zoomRange = ZoomRange(0.508f, 30f),
        stabilizationModes = setOf(0, 1, 2),
        fpsRanges = listOf(FpsRange(15, 30), FpsRange(30, 30)),
        activeArray = ArraySize(4000, 3000),
    )
    private val all = AppSettings(
        back = FacingSettings(ZoomSetting.Minimum, Stabilization.PREVIEW, FpsSetting.Fixed(FpsRange(30, 30))),
    )
    private val stock = RequestValues(zoom = 1.0f, stabilization = 0, fps = FpsRange(15, 30), cropRegion = null)

    @Test
    fun appDefaultsWriteNothing() {
        val access = FakeRequestAccess(stock)
        val patch = RequestPatch.prepare(access, AppSettings.DEFAULT, caps)
        patch.apply()
        patch.restore()
        assertEquals(0, access.writes)
        assertEquals(stock, access.values)
    }

    @Test
    fun overridesApplyThenRestoreExactly() {
        val access = FakeRequestAccess(stock)
        val patch = RequestPatch.prepare(access, all, caps)
        patch.apply()
        assertEquals(RequestValues(0.508f, 2, FpsRange(30, 30), null), access.values)
        patch.restore()
        assertEquals(stock, access.values)
    }

    @Test
    fun keysTheAppDidNotSetAreErasedOnRestore() {
        val unset = RequestValues(zoom = null, stabilization = null, fps = null, cropRegion = null)
        val access = FakeRequestAccess(unset)
        val patch = RequestPatch.prepare(access, all, caps)
        patch.apply()
        assertEquals(RequestValues(0.508f, 2, FpsRange(30, 30), null), access.values)
        patch.restore()
        assertEquals(unset, access.values)
    }

    @Test
    fun onlyWrittenKeysAreRestored() {
        val access = FakeRequestAccess(stock)
        val patch = RequestPatch.prepare(access, AppSettings(back = FacingSettings(stabilization = Stabilization.ON)), caps)
        patch.apply()
        patch.restore()
        assertEquals(2, access.writes)
        assertEquals(stock, access.values)
    }

    @Test
    fun aFailedWriteIsStillRestored() {
        val access = FakeRequestAccess(stock).apply { failOnFps = true }
        val patch = RequestPatch.prepare(access, all, caps)
        assertThrows(IllegalStateException::class.java) { patch.apply() }
        access.failOnFps = false
        patch.restore()
        assertEquals(stock, access.values)
    }
}
