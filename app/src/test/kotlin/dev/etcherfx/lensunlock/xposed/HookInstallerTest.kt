package dev.etcherfx.lensunlock.xposed

import android.util.Log
import dev.etcherfx.lensunlock.core.AppSettings
import dev.etcherfx.lensunlock.core.ArraySize
import dev.etcherfx.lensunlock.core.CameraCaps
import dev.etcherfx.lensunlock.core.CropRegion
import dev.etcherfx.lensunlock.core.Facing
import dev.etcherfx.lensunlock.core.FacingSettings
import dev.etcherfx.lensunlock.core.FpsRange
import dev.etcherfx.lensunlock.core.FpsSetting
import dev.etcherfx.lensunlock.core.RequestValues
import dev.etcherfx.lensunlock.core.Stabilization
import dev.etcherfx.lensunlock.core.ZoomRange
import dev.etcherfx.lensunlock.core.ZoomSetting
import dev.etcherfx.lensunlock.xposed.fakes.FakeActivityThread
import dev.etcherfx.lensunlock.xposed.fakes.FakeApplication
import dev.etcherfx.lensunlock.xposed.fakes.FakeBuilder
import dev.etcherfx.lensunlock.xposed.fakes.FakeCameraDeviceImpl
import dev.etcherfx.lensunlock.xposed.fakes.FakeCaptureRequest
import dev.etcherfx.lensunlock.xposed.fakes.FakeCaptureSessionCallback
import dev.etcherfx.lensunlock.xposed.fakes.FakeChain
import dev.etcherfx.lensunlock.xposed.fakes.FakeRequestAccess
import dev.etcherfx.lensunlock.xposed.fakes.FakeXposed
import io.github.libxposed.api.XposedInterface.ExceptionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HookInstallerTest {
    private val names = FrameworkTargets.Names(
        captureRequest = FakeCaptureRequest::class.java.name,
        captureRequestBuilder = FakeBuilder::class.java.name,
        cameraDeviceImpl = FakeCameraDeviceImpl::class.java.name,
        activityThread = FakeActivityThread::class.java.name,
        application = FakeApplication::class.java.name,
    )
    private val backCaps = CameraCaps(
        facing = Facing.BACK,
        zoomRange = ZoomRange(0.508f, 30f),
        stabilizationModes = setOf(0, 1, 2),
        fpsRanges = listOf(FpsRange(15, 30), FpsRange(30, 30)),
        activeArray = ArraySize(4000, 3000),
    )
    private val stock = RequestValues(1.0f, 0, FpsRange(15, 30), CropRegion(0, 0, 4000, 3000))
    private val all = AppSettings(
        back = FacingSettings(ZoomSetting.Minimum, Stabilization.PREVIEW, FpsSetting.Fixed(FpsRange(30, 30))),
    )

    private val xposed = FakeXposed()
    private val log = ModuleLog(xposed)
    private val builder = FakeBuilder()
    private val access = FakeRequestAccess(stock)
    private var settings = all
    private var capsLoads = 0

    private val installer = HookInstaller(
        xposed = xposed,
        log = log,
        packageName = "com.example.app",
        settings = { settings },
        registryFor = { CameraRegistry({ capsLoads++; backCaps }, log) },
        accessFor = { if (it === builder) access else null },
        cameraIdOf = { (it as? FakeCameraDeviceImpl)?.id },
    )

    private fun install(names: FrameworkTargets.Names = this.names) =
        installer.install(FrameworkTargets.resolve(javaClass.classLoader!!, names))

    private fun messages() = xposed.lines.map { it.message }

    /** Runs createCaptureRequest(int) on camera [cameraId], returning [result]. */
    private fun create(cameraId: String = "0", result: Any = builder) =
        xposed.hookerFor("createCaptureRequest", 1).intercept(FakeChain(FakeCameraDeviceImpl(cameraId)) { result })

    /** Runs build() on [target], returning the values the original saw. */
    private fun build(target: Any = builder): RequestValues? {
        var seen: RequestValues? = null
        xposed.hookerFor("build", 0).intercept(FakeChain(target) { seen = access.values; FakeCaptureRequest() })
        return seen
    }

    @Test
    fun hooksEveryTargetProtectively() {
        install()

        assertEquals(3, xposed.hooks.size)
        assertTrue(xposed.hooks.all { it.mode == ExceptionMode.PROTECTIVE })
        assertEquals(
            listOf(
                "Hooked FakeCameraDeviceImpl.createCaptureRequest(int)",
                "Hooked FakeCameraDeviceImpl.createCaptureRequest(int, Set)",
                "Hooked FakeBuilder.build()",
            ),
            messages(),
        )
        assertTrue(xposed.lines.all { it.tag == "LensUnlock" && it.level == Log.INFO })
    }

    @Test
    fun unavailableTargetsAreNotHooked() {
        install(names.copy(cameraDeviceImpl = "com.example.MissingDevice"))

        assertTrue(xposed.hooks.isEmpty())
        val warnings = xposed.lines.filter { it.level == Log.WARN }.map { it.message }
        assertEquals(2, warnings.size)
        assertTrue(warnings[0], warnings[0].startsWith("CameraDeviceImpl.createCaptureRequest() unavailable, not hooked:"))
        assertEquals("Overrides disabled: a required target is unavailable", warnings[1])
        assertTrue(xposed.lines.all { it.tag == "LensUnlock" })
    }

    @Test
    fun buildSeesTheOverridesAndTheBuilderKeepsTheAppValues() {
        install()
        assertSame(builder, create())

        assertEquals(RequestValues(0.508f, 2, FpsRange(30, 30), stock.cropRegion), build())
        assertEquals(stock, access.values)
        assertTrue(
            messages().toString(),
            "Camera 0 (back): zoom 1.0 → 0.508, stabilization OFF → PREVIEW_STABILIZATION, fps [15,30] → [30,30]" in messages(),
        )

        // A repeated build logs nothing new and reads the characteristics only once.
        val lineCount = xposed.lines.size
        create()
        build()
        assertEquals(lineCount, xposed.lines.size)
        assertEquals(1, capsLoads)
    }

    @Test
    fun settingsChangesApplyToTheNextBuild() {
        install()
        create()
        build()
        settings = AppSettings.DEFAULT
        assertEquals(stock, build())
    }

    @Test
    fun unmappedBuildersAreUntouched() {
        install()
        build()
        assertEquals(0, access.writes)
    }

    @Test
    fun otherFacingCamerasAreUntouched() {
        val installer = HookInstaller(
            xposed, log, "com.example.app", { all },
            registryFor = { CameraRegistry({ backCaps.copy(facing = Facing.OTHER) }, log) },
            accessFor = { access },
            cameraIdOf = { (it as? FakeCameraDeviceImpl)?.id },
        )
        installer.install(FrameworkTargets.resolve(javaClass.classLoader!!, names))
        create()
        assertEquals(stock, build())
        assertEquals(0, access.writes)
    }

    @Test
    fun aFailingDecisionKeepsTheAppRequest() {
        val installer = HookInstaller(
            xposed, log, "com.example.app", { error("prefs broke") },
            registryFor = { CameraRegistry({ backCaps }, log) },
            accessFor = { access },
            cameraIdOf = { (it as? FakeCameraDeviceImpl)?.id },
        )
        installer.install(FrameworkTargets.resolve(javaClass.classLoader!!, names))
        create()
        val chain = FakeChain(builder) { access.values }
        assertEquals(stock, xposed.hookerFor("build", 0).intercept(chain))
        assertEquals(1, chain.proceeded)
        assertTrue(xposed.lines.any { it.level == Log.WARN && it.message.startsWith("Could not apply overrides") })
    }

    @Test
    fun aThrowingBuildStillRestoresAndPropagates() {
        install()
        create()
        val chain = FakeChain(builder) { throw IllegalArgumentException("bad request") }
        assertThrows(IllegalArgumentException::class.java) { xposed.hookerFor("build", 0).intercept(chain) }
        assertEquals(stock, access.values)
    }

    @Test
    fun unreadableCharacteristicsLeaveTheCameraUntouched() {
        val installer = HookInstaller(
            xposed, log, "com.example.app", { all },
            registryFor = { CameraRegistry({ error("no camera service") }, log) },
            accessFor = { access },
            cameraIdOf = { (it as? FakeCameraDeviceImpl)?.id },
        )
        installer.install(FrameworkTargets.resolve(javaClass.classLoader!!, names))
        assertSame(builder, create())
        assertEquals(stock, build())
        assertTrue(messages().any { it.startsWith("Could not read camera 0 characteristics") })
    }

    @Test
    fun skipsAreLoggedOnce() {
        settings = AppSettings(back = FacingSettings(fps = FpsSetting.Fixed(FpsRange(60, 60))))
        install()
        create()
        build()
        build()
        val skips = messages().filter { it.contains("not overriding") }
        assertEquals(listOf("Camera 0 (back): not overriding fps: the camera doesn't support fps range [60,60]"), skips)
        assertEquals(0, access.writes)
    }

    @Test
    fun deoptimizesOnlyOnConfigured() {
        installer.deoptimize(javaClass.classLoader!!, FakeCaptureSessionCallback::class.java.name)
        assertEquals(listOf("onConfigured"), xposed.deoptimized.map { it.name })
        assertEquals(listOf("Deoptimized FakeCaptureSessionCallback.onConfigured(Object): true"), messages())
    }

    @Test
    fun aMissingWebRtcClassIsIgnored() {
        installer.deoptimize(javaClass.classLoader!!, "org.webrtc.Missing")
        assertTrue(xposed.deoptimized.isEmpty())
        assertTrue(xposed.lines.isEmpty())
    }
}
