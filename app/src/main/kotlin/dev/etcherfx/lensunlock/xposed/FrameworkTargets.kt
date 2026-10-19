package dev.etcherfx.lensunlock.xposed

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** A hook target that either resolved, or didn't and says why. */
sealed interface Target<out T> {
    data class Available<T>(val value: T) : Target<T>
    data class Unavailable(val reason: String) : Target<Nothing>
}

class FrameworkTargets(
    /** `CaptureRequest.Builder.build()`, where the overrides go in. */
    val build: Target<Method>,
    /** Every `CameraDeviceImpl.createCaptureRequest(int, ...)` overload, which tells a builder's camera. */
    val createCaptureRequest: Target<List<Method>>,
    /** `ActivityThread.currentApplication()`, for the `CameraManager` that reads characteristics. */
    val currentApplication: Target<Method>,
) {
    /** Class and member names; tests inject fakes. */
    data class Names(
        val captureRequest: String = "android.hardware.camera2.CaptureRequest",
        val captureRequestBuilder: String = "android.hardware.camera2.CaptureRequest\$Builder",
        val cameraDeviceImpl: String = "android.hardware.camera2.impl.CameraDeviceImpl",
        val activityThread: String = "android.app.ActivityThread",
        val application: String = "android.app.Application",
        val build: String = "build",
        val createCaptureRequest: String = "createCaptureRequest",
        val currentApplication: String = "currentApplication",
    )

    companion object {
        /** Resolves every target with plain reflection. Never throws. */
        fun resolve(classLoader: ClassLoader, names: Names = Names()): FrameworkTargets {
            val builder = try {
                Class.forName(names.captureRequestBuilder, false, classLoader)
            } catch (t: Throwable) {
                val missing = Target.Unavailable("${names.captureRequestBuilder} not found: $t")
                return FrameworkTargets(missing, missing, resolve { currentApplicationMethod(classLoader, names) })
            }
            return FrameworkTargets(
                build = resolve { buildMethod(classLoader, names, builder) },
                createCaptureRequest = resolve { createMethods(classLoader, names, builder) },
                currentApplication = resolve { currentApplicationMethod(classLoader, names) },
            )
        }

        private fun <T> resolve(block: () -> T): Target<T> = try {
            Target.Available(block())
        } catch (t: Throwable) {
            Target.Unavailable(t.message ?: t.toString())
        }

        private fun buildMethod(classLoader: ClassLoader, names: Names, builder: Class<*>): Method {
            val request = Class.forName(names.captureRequest, false, classLoader)
            val method = try {
                builder.getDeclaredMethod(names.build)
            } catch (_: NoSuchMethodException) {
                throw NoSuchMethodException("${builder.name}.${names.build}() not found")
            }
            check(method.returnType == request) {
                "${builder.name}.${names.build} returns ${method.returnType.name}, expected ${request.name}"
            }
            check(!Modifier.isStatic(method.modifiers)) { "${builder.name}.${names.build} is static" }
            return method
        }

        private fun createMethods(classLoader: ClassLoader, names: Names, builder: Class<*>): List<Method> {
            val device = Class.forName(names.cameraDeviceImpl, false, classLoader)
            val methods = device.declaredMethods.filter {
                it.name == names.createCaptureRequest &&
                    !Modifier.isAbstract(it.modifiers) &&
                    !Modifier.isStatic(it.modifiers) &&
                    it.parameterTypes.firstOrNull() == Int::class.javaPrimitiveType &&
                    it.returnType == builder
            }
            check(methods.isNotEmpty()) {
                "${device.name}.${names.createCaptureRequest}(int, ...) returning ${builder.name} not found"
            }
            return methods.sortedBy { it.parameterCount }
        }

        private fun currentApplicationMethod(classLoader: ClassLoader, names: Names): Method {
            val thread = Class.forName(names.activityThread, false, classLoader)
            val application = Class.forName(names.application, false, classLoader)
            val method = try {
                thread.getDeclaredMethod(names.currentApplication)
            } catch (_: NoSuchMethodException) {
                throw NoSuchMethodException("${thread.name}.${names.currentApplication}() not found")
            }
            check(Modifier.isStatic(method.modifiers)) { "${thread.name}.${names.currentApplication} is not static" }
            check(method.returnType == application) {
                "${thread.name}.${names.currentApplication} returns ${method.returnType.name}, expected ${application.name}"
            }
            method.isAccessible = true
            return method
        }
    }
}
