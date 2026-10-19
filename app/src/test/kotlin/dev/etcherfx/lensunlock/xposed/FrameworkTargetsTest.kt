package dev.etcherfx.lensunlock.xposed

import dev.etcherfx.lensunlock.xposed.fakes.FakeActivityThread
import dev.etcherfx.lensunlock.xposed.fakes.FakeActivityThreadWithInstanceMethod
import dev.etcherfx.lensunlock.xposed.fakes.FakeApplication
import dev.etcherfx.lensunlock.xposed.fakes.FakeBuilder
import dev.etcherfx.lensunlock.xposed.fakes.FakeBuilderWithWrongBuild
import dev.etcherfx.lensunlock.xposed.fakes.FakeBuilderWithoutBuild
import dev.etcherfx.lensunlock.xposed.fakes.FakeCameraDeviceImpl
import dev.etcherfx.lensunlock.xposed.fakes.FakeCaptureRequest
import dev.etcherfx.lensunlock.xposed.fakes.FakeDeviceWithWrongReturn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameworkTargetsTest {
    private val names = FrameworkTargets.Names(
        captureRequest = FakeCaptureRequest::class.java.name,
        captureRequestBuilder = FakeBuilder::class.java.name,
        cameraDeviceImpl = FakeCameraDeviceImpl::class.java.name,
        activityThread = FakeActivityThread::class.java.name,
        application = FakeApplication::class.java.name,
    )

    private fun resolve(names: FrameworkTargets.Names) = FrameworkTargets.resolve(javaClass.classLoader!!, names)

    @Test
    fun resolvesEveryTarget() {
        val targets = resolve(names)

        assertEquals("build", (targets.build as Target.Available).value.name)
        val creates = (targets.createCaptureRequest as Target.Available).value
        // Both template overloads, but not the String one or the reprocess method.
        assertEquals(listOf(listOf(Int::class.javaPrimitiveType), listOf(Int::class.javaPrimitiveType, Set::class.java)), creates.map { it.parameterTypes.toList() })
        assertEquals("currentApplication", (targets.currentApplication as Target.Available).value.name)
    }

    @Test
    fun missingBuilderClassDisablesTheBuilderTargets() {
        val targets = resolve(names.copy(captureRequestBuilder = "com.example.Missing"))
        assertUnavailable(targets.build, "com.example.Missing")
        assertUnavailable(targets.createCaptureRequest, "com.example.Missing")
        assertTrue(targets.currentApplication is Target.Available)
    }

    @Test
    fun missingBuildMethodIsUnavailable() {
        val targets = resolve(names.copy(captureRequestBuilder = FakeBuilderWithoutBuild::class.java.name))
        assertUnavailable(targets.build, "build() not found")
    }

    @Test
    fun wrongBuildReturnTypeIsUnavailable() {
        val targets = resolve(names.copy(captureRequestBuilder = FakeBuilderWithWrongBuild::class.java.name))
        assertUnavailable(targets.build, "returns java.lang.Object")
    }

    @Test
    fun missingDeviceClassDisablesOnlyCreateCaptureRequest() {
        val targets = resolve(names.copy(cameraDeviceImpl = "com.example.MissingDevice"))
        assertUnavailable(targets.createCaptureRequest, "com.example.MissingDevice")
        assertTrue(targets.build is Target.Available)
    }

    @Test
    fun createCaptureRequestWithTheWrongReturnTypeIsUnavailable() {
        val targets = resolve(names.copy(cameraDeviceImpl = FakeDeviceWithWrongReturn::class.java.name))
        assertUnavailable(targets.createCaptureRequest, "createCaptureRequest(int, ...)")
    }

    @Test
    fun currentApplicationMustBeStatic() {
        val targets = resolve(names.copy(activityThread = FakeActivityThreadWithInstanceMethod::class.java.name))
        assertUnavailable(targets.currentApplication, "is not static")
    }

    @Test
    fun missingActivityThreadIsUnavailable() {
        val targets = resolve(names.copy(activityThread = "com.example.MissingThread"))
        assertUnavailable(targets.currentApplication, "com.example.MissingThread")
    }

    private fun assertUnavailable(target: Target<*>, reasonContains: String) {
        val reason = (target as Target.Unavailable).reason
        assertTrue("'$reason' should mention '$reasonContains'", reason.contains(reasonContains))
    }
}
