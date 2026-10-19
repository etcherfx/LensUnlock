@file:Suppress("unused", "UNUSED_PARAMETER")

package dev.etcherfx.lensunlock.xposed.fakes

// Stand-ins for the framework classes, shaped like android.hardware.camera2 and android.app.

class FakeCaptureRequest

class FakeBuilder {
    fun build(): FakeCaptureRequest = FakeCaptureRequest()
}

class FakeBuilderWithoutBuild

class FakeBuilderWithWrongBuild {
    fun build(): Any = Any()
}

abstract class FakeCameraDevice {
    abstract fun createCaptureRequest(templateType: Int): FakeBuilder
}

class FakeCameraDeviceImpl(val id: String = "0") : FakeCameraDevice() {
    override fun createCaptureRequest(templateType: Int): FakeBuilder = FakeBuilder()
    fun createCaptureRequest(templateType: Int, physicalCameraIds: Set<String>): FakeBuilder = FakeBuilder()

    // Not a template overload: the first parameter isn't an int.
    fun createCaptureRequest(inputResult: String): FakeBuilder = FakeBuilder()
    fun createReprocessCaptureRequest(inputResult: Any): FakeBuilder = FakeBuilder()
}

class FakeDeviceWithWrongReturn {
    fun createCaptureRequest(templateType: Int): Any = Any()
}

class FakeApplication

class FakeActivityThread {
    companion object {
        @JvmStatic
        fun currentApplication(): FakeApplication? = null
    }
}

class FakeActivityThreadWithInstanceMethod {
    fun currentApplication(): FakeApplication? = null
}

class FakeCaptureSessionCallback {
    fun onConfigured(session: Any) {}
    fun onConfigureFailed(session: Any) {}
}
