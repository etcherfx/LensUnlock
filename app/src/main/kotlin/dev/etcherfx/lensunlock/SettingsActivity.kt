package dev.etcherfx.lensunlock

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import dev.etcherfx.lensunlock.camera.Camera2Caps
import dev.etcherfx.lensunlock.core.AppSettings
import dev.etcherfx.lensunlock.core.CameraCaps
import dev.etcherfx.lensunlock.core.Facing
import dev.etcherfx.lensunlock.core.FacingSettings
import io.github.libxposed.service.XposedService

class SettingsActivity : ComponentActivity() {
    private var ui by mutableStateOf(SettingsUi(emptyList(), attention = false, apps = emptyList(), cameras = emptyMap()))
    private var selected by mutableStateOf<String?>(null)
    private val prefs = mutableMapOf<String, SharedPreferences>()
    private val labels = mutableMapOf<String, Pair<String, ImageBitmap?>>()
    private val app get() = application as ModuleApp
    private val onServiceChanged: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ui = ui.copy(cameras = readCameras())
        setContent {
            SettingsTheme {
                SettingsScreen(
                    ui = ui,
                    selected = selected,
                    onSelect = { selected = it },
                    onBack = { selected = null },
                    onChange = ::write,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        app.addServiceListener(onServiceChanged)
        render()
    }

    override fun onStop() {
        app.removeServiceListener(onServiceChanged)
        super.onStop()
    }

    private fun render() {
        prefs.clear()
        val status = app.service?.let(::connect)
            ?: Connection(listOf(getString(R.string.status_inactive)), attention = true, apps = emptyList())
        ui = ui.copy(statusLines = status.lines, attention = status.attention, apps = status.apps)
    }

    /** Builds the status lines and the scoped apps, filling [prefs] for the apps that can be edited. */
    private fun connect(service: XposedService): Connection = try {
        val lines = mutableListOf(
            getString(R.string.status_connected, service.frameworkName, service.frameworkVersion, service.apiVersion),
        )
        val scope = service.scope.filter { it != packageName }
        if (scope.isEmpty()) lines += getString(R.string.status_no_apps)
        val remote = service.frameworkProperties and XposedService.PROP_CAP_REMOTE != 0L
        if (!remote) lines += getString(R.string.status_no_remote_prefs)
        val apps = scope.map { packageName ->
            val p = if (remote) service.getRemotePreferences(packageName).also { prefs[packageName] = it } else null
            val (label, icon) = labelAndIcon(packageName)
            AppEntry(
                packageName = packageName,
                label = label,
                icon = icon,
                settings = p?.let(::readSettings) ?: AppSettings.DEFAULT,
                editable = p != null,
            )
        }.sortedBy { it.label.lowercase() }
        Connection(lines, attention = scope.isEmpty() || !remote, apps)
    } catch (e: RuntimeException) {
        prefs.clear()
        Connection(listOf(getString(R.string.status_error, e.message ?: e.toString())), attention = true, apps = emptyList())
    }

    private fun readSettings(prefs: SharedPreferences) = AppSettings.fromLookup(
        getString = { key -> prefs.getString(key, null) },
        getFloat = { key, default -> prefs.getFloat(key, default) },
    )

    private fun write(packageName: String, facing: Facing, settings: FacingSettings) {
        val p = prefs[packageName] ?: return
        try {
            p.edit {
                AppSettings.write(facing, settings, { key, value -> putString(key, value) }, { key, value -> putFloat(key, value) })
            }
            ui = ui.copy(
                apps = ui.apps.map {
                    if (it.packageName != packageName) {
                        it
                    } else {
                        it.copy(settings = if (facing == Facing.BACK) it.settings.copy(back = settings) else it.settings.copy(front = settings))
                    }
                },
            )
        } catch (e: RuntimeException) {
            render()
            ui = ui.copy(
                statusLines = ui.statusLines + getString(R.string.status_error, e.message ?: e.toString()),
                attention = true,
            )
        }
    }

    /** The app's label and icon, or its package name and no icon if it isn't visible to this app. */
    private fun labelAndIcon(packageName: String): Pair<String, ImageBitmap?> = labels.getOrPut(packageName) {
        val info = applicationInfo(packageName) ?: return@getOrPut packageName to null
        val label = packageManager.getApplicationLabel(info).toString()
        val icon = try {
            val size = (ICON_SIZE_DP * resources.displayMetrics.density).toInt()
            packageManager.getApplicationIcon(info).toBitmap(size, size).asImageBitmap()
        } catch (_: RuntimeException) {
            null
        }
        label to icon
    }

    private fun applicationInfo(packageName: String): ApplicationInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** The first camera of each facing, as apps open them by default. No permission is needed for these keys. */
    private fun readCameras(): Map<Facing, CameraCaps> = try {
        val manager = getSystemService(CameraManager::class.java)
        val cameras = mutableMapOf<Facing, CameraCaps>()
        for (id in manager.cameraIdList) {
            val caps = Camera2Caps.read(manager.getCameraCharacteristics(id))
            if (caps.facing != Facing.OTHER) cameras.putIfAbsent(caps.facing, caps)
        }
        cameras
    } catch (_: Exception) {
        emptyMap()
    }

    private class Connection(val lines: List<String>, val attention: Boolean, val apps: List<AppEntry>)

    private companion object {
        const val ICON_SIZE_DP = 40
    }
}
