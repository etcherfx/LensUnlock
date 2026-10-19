package dev.etcherfx.lensunlock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.etcherfx.lensunlock.core.AppSettings
import dev.etcherfx.lensunlock.core.CameraCaps
import dev.etcherfx.lensunlock.core.Facing
import dev.etcherfx.lensunlock.core.FacingSettings
import dev.etcherfx.lensunlock.core.FpsRange
import dev.etcherfx.lensunlock.core.FpsSetting
import dev.etcherfx.lensunlock.core.Setting
import dev.etcherfx.lensunlock.core.Stabilization
import dev.etcherfx.lensunlock.core.ZoomRange
import dev.etcherfx.lensunlock.core.ZoomSetting
import java.math.RoundingMode

internal data class SettingsUi(
    val statusLines: List<String>,
    /** Something needs the user's attention: the card uses the error colors. */
    val attention: Boolean,
    val apps: List<AppEntry>,
    /** The first camera of each facing on this device. */
    val cameras: Map<Facing, CameraCaps>,
)

internal data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val settings: AppSettings,
    /** The app's preferences can be written; otherwise its settings are read-only. */
    val editable: Boolean,
)

@Composable
internal fun SettingsTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    MaterialExpressiveTheme(
        // minSdk 31 always has dynamic color, so there is no fallback palette.
        colorScheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

@Composable
internal fun SettingsScreen(
    ui: SettingsUi,
    selected: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onChange: (packageName: String, facing: Facing, settings: FacingSettings) -> Unit,
) {
    val app = selected?.let { packageName -> ui.apps.firstOrNull { it.packageName == packageName } }
    if (app == null) {
        AppListScreen(ui, onSelect)
    } else {
        BackHandler(onBack = onBack)
        AppScreen(app, ui.cameras, onBack) { facing, settings -> onChange(app.packageName, facing, settings) }
    }
}

@Composable
private fun AppListScreen(ui: SettingsUi, onSelect: (String) -> Unit) {
    ScreenScaffold(title = stringResource(R.string.app_name), onBack = null) {
        StatusCard(ui.statusLines, ui.attention)
        if (ui.apps.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionHeader(stringResource(R.string.apps_header))
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                ui.apps.forEachIndexed { index, app ->
                    SegmentedListItem(
                        onClick = { onSelect(app.packageName) },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = ui.apps.size),
                        leadingContent = { AppIcon(app.icon) },
                        supportingContent = { Text(appSummary(app.settings, ui.cameras)) },
                    ) {
                        Text(app.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppScreen(
    app: AppEntry,
    cameras: Map<Facing, CameraCaps>,
    onBack: () -> Unit,
    onChange: (Facing, FacingSettings) -> Unit,
) {
    ScreenScaffold(title = app.label, onBack = onBack) {
        FacingSection(stringResource(R.string.camera_back), app.settings.back, cameras[Facing.BACK], app.editable) {
            onChange(Facing.BACK, it)
        }
        Spacer(Modifier.height(24.dp))
        FacingSection(stringResource(R.string.camera_front), app.settings.front, cameras[Facing.FRONT], app.editable) {
            onChange(Facing.FRONT, it)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.footer_apply),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScreenScaffold(title: String, onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.navigate_back))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            content()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusCard(lines: List<String>, attention: Boolean) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (attention) colors.errorContainer else colors.secondaryContainer,
            contentColor = if (attention) colors.onErrorContainer else colors.onSecondaryContainer,
        ),
    ) {
        Text(
            text = lines.joinToString("\n"),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun AppIcon(icon: ImageBitmap?) {
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
    } else {
        Spacer(Modifier.size(40.dp))
    }
}

@Composable
private fun FacingSection(
    title: String,
    settings: FacingSettings,
    caps: CameraCaps?,
    editable: Boolean,
    onChange: (FacingSettings) -> Unit,
) {
    var dialog by remember { mutableStateOf<Setting?>(null) }
    val enabled = editable && caps != null
    SectionHeader(title)
    if (caps == null) {
        Text(
            text = stringResource(R.string.camera_missing),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SettingItem(0, stringResource(R.string.setting_zoom), zoomLabel(settings.zoom, caps?.zoomRange), enabled) {
            dialog = Setting.ZOOM
        }
        SettingItem(1, stringResource(R.string.setting_stabilization), stabilizationLabel(settings.stabilization), enabled) {
            dialog = Setting.STABILIZATION
        }
        SettingItem(2, stringResource(R.string.setting_fps), fpsLabel(settings.fps), enabled) {
            dialog = Setting.FPS
        }
    }
    if (caps == null) return
    val close = { dialog = null }
    when (dialog) {
        Setting.ZOOM -> ZoomDialog(settings.zoom, caps.zoomRange, close) {
            onChange(settings.copy(zoom = it))
            close()
        }
        Setting.STABILIZATION -> {
            val modes = Stabilization.entries
            ChoiceDialog(
                title = stringResource(R.string.setting_stabilization),
                labels = modes.map { stabilizationLabel(it) },
                enabled = modes.map { it.mode == null || it.mode in caps.stabilizationModes },
                selected = modes.indexOf(settings.stabilization),
                onDismiss = close,
            ) {
                onChange(settings.copy(stabilization = modes[it]))
                close()
            }
        }
        Setting.FPS -> {
            val choices = listOf(FpsSetting.App) +
                caps.fpsRanges.distinct().sortedWith(compareBy({ it.max }, { it.min })).map { FpsSetting.Fixed(it) }
            ChoiceDialog(
                title = stringResource(R.string.setting_fps),
                labels = choices.map { fpsLabel(it) },
                enabled = choices.map { true },
                selected = choices.indexOf(settings.fps),
                onDismiss = close,
            ) {
                onChange(settings.copy(fps = choices[it]))
                close()
            }
        }
        null -> {}
    }
}

@Composable
private fun SettingItem(index: Int, title: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = SETTING_COUNT),
        enabled = enabled,
        supportingContent = { Text(value) },
    ) {
        Text(title)
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    labels: List<String>,
    enabled: List<Boolean>,
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                labels.forEachIndexed { index, label ->
                    ChoiceRow(label, index == selected, enabled[index]) { onSelect(index) }
                }
            }
        },
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

@Composable
private fun ZoomDialog(
    current: ZoomSetting,
    range: ZoomRange?,
    onDismiss: () -> Unit,
    onSave: (ZoomSetting) -> Unit,
) {
    var choice by remember {
        mutableIntStateOf(
            when (current) {
                ZoomSetting.App -> 0
                ZoomSetting.Minimum -> 1
                is ZoomSetting.Custom -> 2
            },
        )
    }
    var text by remember { mutableStateOf(if (current is ZoomSetting.Custom) current.ratio.toString() else "1.0") }
    val ratio = text.trim().replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
    // Without a zoom ratio range, only the app's value can be used.
    val zoomable = range != null
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val custom = ratio?.let { r -> range?.let { r.coerceIn(it.min, it.max) } ?: r }
                    onSave(
                        when {
                            choice == 0 -> ZoomSetting.App
                            choice == 1 -> ZoomSetting.Minimum
                            custom != null -> ZoomSetting.Custom(custom)
                            else -> current
                        },
                    )
                },
                enabled = choice != 2 || ratio != null,
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
        title = { Text(stringResource(R.string.setting_zoom)) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                ChoiceRow(stringResource(R.string.app_default), choice == 0, enabled = true) { choice = 0 }
                ChoiceRow(zoomLabel(ZoomSetting.Minimum, range), choice == 1, zoomable) { choice = 1 }
                ChoiceRow(stringResource(R.string.zoom_custom), choice == 2, zoomable) { choice = 2 }
                if (range != null) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        enabled = choice == 2,
                        label = { Text(stringResource(R.string.zoom_custom_field)) },
                        supportingText = {
                            Text(
                                if (ratio == null) {
                                    stringResource(R.string.zoom_custom_invalid)
                                } else {
                                    stringResource(R.string.zoom_custom_range, formatRatio(range.min), formatRatio(range.max))
                                },
                            )
                        },
                        isError = choice == 2 && ratio == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
            }
        },
    )
}

@Composable
private fun appSummary(settings: AppSettings, cameras: Map<Facing, CameraCaps>) = stringResource(
    R.string.summary_app,
    facingSummary(settings.back, cameras[Facing.BACK]?.zoomRange),
    facingSummary(settings.front, cameras[Facing.FRONT]?.zoomRange),
)

@Composable
private fun facingSummary(settings: FacingSettings, range: ZoomRange?): String {
    if (settings.isAppDefault) return stringResource(R.string.summary_app_defaults)
    val zoom = when (val zoom = settings.zoom) {
        ZoomSetting.App -> null
        ZoomSetting.Minimum -> range?.let { stringResource(R.string.summary_zoom, formatRatio(it.min)) }
            ?: stringResource(R.string.summary_zoom_minimum)
        is ZoomSetting.Custom -> stringResource(R.string.summary_zoom, formatRatio(zoom.ratio))
    }
    val stabilization = when (settings.stabilization) {
        Stabilization.APP -> null
        Stabilization.OFF -> stringResource(R.string.summary_stabilization_off)
        Stabilization.ON -> stringResource(R.string.summary_stabilization_on)
        Stabilization.PREVIEW -> stringResource(R.string.summary_stabilization_preview)
    }
    val fps = (settings.fps as? FpsSetting.Fixed)?.let { fpsLabel(it) }
    return listOfNotNull(zoom, stabilization, fps).joinToString(", ")
}

@Composable
private fun zoomLabel(zoom: ZoomSetting, range: ZoomRange?) = when (zoom) {
    ZoomSetting.App -> stringResource(R.string.app_default)
    ZoomSetting.Minimum -> range?.let { stringResource(R.string.zoom_minimum_value, formatRatio(it.min)) }
        ?: stringResource(R.string.zoom_minimum)
    is ZoomSetting.Custom -> stringResource(R.string.zoom_custom_value, formatRatio(zoom.ratio))
}

@Composable
private fun stabilizationLabel(stabilization: Stabilization) = stringResource(
    when (stabilization) {
        Stabilization.APP -> R.string.app_default
        Stabilization.OFF -> R.string.stabilization_off
        Stabilization.ON -> R.string.stabilization_on
        Stabilization.PREVIEW -> R.string.stabilization_preview
    },
)

@Composable
private fun fpsLabel(fps: FpsSetting) = when (fps) {
    FpsSetting.App -> stringResource(R.string.app_default)
    is FpsSetting.Fixed -> fpsRangeLabel(fps.range)
}

@Composable
private fun fpsRangeLabel(range: FpsRange) = if (range.min == range.max) {
    stringResource(R.string.fps_fixed, range.max)
} else {
    stringResource(R.string.fps_range, range.min, range.max)
}

/** At most two decimals, without trailing zeros: 0.50783783 is "0.51", 30.0 is "30". */
private fun formatRatio(ratio: Float): String =
    ratio.toBigDecimal().setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

private const val SETTING_COUNT = 3
