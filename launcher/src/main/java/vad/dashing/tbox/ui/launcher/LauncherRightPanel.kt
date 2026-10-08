package vad.dashing.tbox.ui.launcher

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.resolveDriveModeDisplayLabel
import vad.dashing.tbox.ui.LaunchableAppEntry
import vad.dashing.tbox.ui.rememberLaunchableAppEntries
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.valueToString
import kotlin.math.roundToInt

private sealed class HomeDockEntry(val key: String) {
    data class App(val entry: LaunchableAppEntry, val index: Int) : HomeDockEntry("app_${entry.packageName}_$index")
    data class Split(val preset: LauncherSplitPreset, val index: Int) : HomeDockEntry("split_${preset.id}_$index")
    data class Empty(val index: Int) : HomeDockEntry("empty_$index")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherRightPanel(
    canViewModel: CanDataViewModel,
    settingsViewModel: SettingsViewModel,
    tboxViewModel: TboxViewModel,
    @Suppress("UNUSED_PARAMETER") onOpenConsole: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenApps: () -> Unit = {},
    configRevision: Int = 0,
    onConfigChanged: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val odometer by canViewModel.odometer.collectAsStateWithLifecycle()
    val outsideTemp by canViewModel.outsideTemperature.collectAsStateWithLifecycle()
    val insideTemp by canViewModel.insideTemperature.collectAsStateWithLifecycle()
    val distanceToFuelEmpty by canViewModel.distanceToFuelEmpty.collectAsStateWithLifecycle()
    val driveModeRaw by UniversalCanRepository.carSettingsDriveMode.collectAsStateWithLifecycle()
    val driveModeWetRaw by UniversalCanRepository.carSettingsDriveMode6dctWet.collectAsStateWithLifecycle()
    val gearBoxDriveMode by canViewModel.gearBoxDriveMode.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        UniversalCanRepository.setSourceSignals(
            "launcher_right_panel",
            setOf(vad.dashing.tbox.mbcan.MbCanSignal.CarSettingsVehicleParams),
        )
    }

    val driveLabel = resolveDriveModeDisplayLabel(
        driveModeRaw = driveModeRaw,
        driveModeWetRaw = driveModeWetRaw,
        gearBoxDriveMode = gearBoxDriveMode,
    )
    val iconRevision by settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()
    val rawApps = rememberLaunchableAppEntries(settingsViewModel, iconRevision)
    val priority = remember(context) { LauncherOemAppSort.loadPriorityPackages(context) }
    val hidden = remember(context, configRevision) { LauncherAppConfigStore.hiddenPackages(context) }
    val homeItems = remember(context, configRevision) { LauncherHomeStore.loadItems(context) }
    val autostartKey = remember(context, configRevision) { LauncherHomeStore.autostartKey(context) }
    val splitPresets = remember(context, configRevision) { LauncherSplitPresetStore.loadPresets(context) }
    val visibleApps = remember(rawApps, hidden) { LauncherAppConfigStore.filterVisible(rawApps, hidden) }
    val pickerApps = remember(visibleApps, priority) { LauncherOemAppSort.sortEntries(visibleApps, priority) }
    val appsByPackage = remember(visibleApps) { visibleApps.associateBy { it.packageName } }

    var addMenuVisible by remember { mutableStateOf(false) }
    var appPickerVisible by remember { mutableStateOf(false) }
    var splitCreateVisible by remember { mutableStateOf(false) }
    var splitEditPreset by remember { mutableStateOf<LauncherSplitPreset?>(null) }
    var contextMenuIndex by remember { mutableIntStateOf(-1) }
    var replaceIndex by remember { mutableIntStateOf(-1) }
    var settingsIndex by remember { mutableIntStateOf(-1) }
    var isDragMode by remember { mutableStateOf(false) }
    var showHiddenDialogVisible by remember { mutableStateOf(false) }
    var vehicleStatusDialogVisible by remember { mutableStateOf(false) }

    var activeDraggingEntry by remember { mutableStateOf<HomeDockEntry?>(null) }
    var activeDraggingX by remember { mutableFloatStateOf(0f) }
    var activeDraggingY by remember { mutableFloatStateOf(0f) }
    var rightPanelLeftPx by remember { mutableFloatStateOf(0f) }
    var rightPanelTopPx by remember { mutableFloatStateOf(0f) }

    val gridColumnsRevision by LauncherAppConfigStore.gridColumnsRevisionFlow.collectAsStateWithLifecycle()
    val gridColumns = remember(context, gridColumnsRevision) { LauncherAppConfigStore.gridColumns(context) }
    val gridRowsRevision by LauncherAppConfigStore.gridRowsRevisionFlow.collectAsStateWithLifecycle()
    val gridRows = remember(context, gridRowsRevision) { LauncherAppConfigStore.gridRows(context) }
    val homeIconScaleRevision by LauncherAppConfigStore.homeIconScaleRevisionFlow.collectAsStateWithLifecycle()
    val homeIconScale = remember(context, homeIconScaleRevision) { LauncherAppConfigStore.homeIconScale(context) }

    val anyLocalDialog = addMenuVisible || appPickerVisible || splitCreateVisible || showHiddenDialogVisible ||
        vehicleStatusDialogVisible || contextMenuIndex >= 0 || replaceIndex >= 0 || settingsIndex >= 0
    LaunchedEffect(anyLocalDialog) {
        LauncherOverlayElevator.setHoldSource("right_panel_dialog", anyLocalDialog)
    }

    val cabinTemp = insideTemp ?: outsideTemp

    var pickerSlotIndex by remember { mutableIntStateOf(-1) }

    val totalGridSlots = gridColumns * gridRows
    val itemsBySlot = remember(homeItems) { homeItems.associateBy { it.slotIndex } }

    val dockEntries = remember(itemsBySlot, splitPresets, appsByPackage, totalGridSlots) {
        List(totalGridSlots) { slotIdx ->
            val item = itemsBySlot[slotIdx]
            when {
                item is LauncherHomeItem.App -> {
                    val app = appsByPackage[item.packageName]
                    if (app != null) HomeDockEntry.App(app, slotIdx)
                    else HomeDockEntry.Empty(slotIdx)
                }
                item is LauncherHomeItem.Split -> {
                    val preset = splitPresets.firstOrNull { it.id == item.presetId }
                    if (preset != null) HomeDockEntry.Split(preset, slotIdx)
                    else HomeDockEntry.Empty(slotIdx)
                }
                else -> HomeDockEntry.Empty(slotIdx)
            }
        }
    }

    LauncherAppPickerDialog(
        visible = appPickerVisible || replaceIndex >= 0,
        title = stringResource(R.string.launcher_grid_pick_app),
        apps = pickerApps,
        onDismiss = {
            appPickerVisible = false
            replaceIndex = -1
            pickerSlotIndex = -1
        },
        onPick = { app ->
            if (replaceIndex >= 0) {
                LauncherHomeStore.replaceAtSlot(context, replaceIndex, LauncherHomeItem.App(app.packageName, replaceIndex))
            } else {
                LauncherHomeStore.addApp(context, app.packageName, targetSlot = pickerSlotIndex)
            }
            onConfigChanged()
            appPickerVisible = false
            replaceIndex = -1
            pickerSlotIndex = -1
        },
    )

    LauncherSplitPresetCreateDialog(
        visible = splitCreateVisible,
        apps = pickerApps,
        initialPreset = splitEditPreset,
        pinToHomeOnSave = false,
        onDismiss = {
            splitCreateVisible = false
            splitEditPreset = null
        },
        onSaved = {
            if (splitEditPreset == null) {
                val latestPreset = LauncherSplitPresetStore.loadPresets(context).lastOrNull()
                if (latestPreset != null) {
                    LauncherHomeStore.addSplit(context, latestPreset.id, targetSlot = pickerSlotIndex)
                }
            }
            onConfigChanged()
            pickerSlotIndex = -1
        },
    )

    if (addMenuVisible) {
        LauncherDarkAlertDialog(
            onDismissRequest = { addMenuVisible = false },
            title = { Text(stringResource(R.string.launcher_home_add_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.launcher_home_add_app),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                addMenuVisible = false
                                appPickerVisible = true
                            }
                            .padding(12.dp),
                        color = LauncherColors.TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.launcher_home_add_split),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                addMenuVisible = false
                                splitEditPreset = null
                                splitCreateVisible = true
                            }
                            .padding(12.dp),
                        color = LauncherColors.TextPrimary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { addMenuVisible = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (contextMenuIndex >= 0) {
        val item = homeItems.firstOrNull { it.slotIndex == contextMenuIndex }
        LauncherDarkAlertDialog(
            onDismissRequest = { contextMenuIndex = -1 },
            title = { Text(stringResource(R.string.launcher_icon_menu_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item is LauncherHomeItem.App) {
                        Text(
                            text = stringResource(R.string.launcher_icon_menu_configure),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    settingsIndex = contextMenuIndex
                                    contextMenuIndex = -1
                                }
                                .padding(12.dp),
                            color = LauncherColors.AccentCyan,
                        )
                        Text(
                            text = stringResource(R.string.launcher_icon_menu_hide),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LauncherAppConfigStore.hidePackage(context, item.packageName)
                                    LauncherHomeStore.removeAtSlot(context, contextMenuIndex)
                                    onConfigChanged()
                                    contextMenuIndex = -1
                                }
                                .padding(12.dp),
                            color = LauncherColors.TextPrimary,
                        )
                    }
                    if (item is LauncherHomeItem.Split) {
                        Text(
                            text = stringResource(R.string.launcher_icon_menu_configure),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    contextMenuIndex = -1
                                    splitEditPreset = splitPresets.firstOrNull { it.id == item.presetId }
                                    splitCreateVisible = true
                                }
                                .padding(12.dp),
                            color = LauncherColors.AccentCyan,
                        )
                    }
                    if (item != null) {
                        Text(
                            text = "Режим перетаскивания",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    isDragMode = true
                                    contextMenuIndex = -1
                                }
                                .padding(12.dp),
                            color = LauncherColors.AccentCyan,
                        )
                        if (hidden.isNotEmpty()) {
                            Text(
                                text = "Показать скрытое (${hidden.size})",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        contextMenuIndex = -1
                                        showHiddenDialogVisible = true
                                    }
                                    .padding(12.dp),
                                color = LauncherColors.AccentCyan,
                            )
                        }
                        val isAutostart = item.key == autostartKey
                        Text(
                            text = stringResource(R.string.launcher_icon_menu_autostart) +
                                if (isAutostart) " ✓" else "",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LauncherHomeStore.setAutostartKey(
                                        context,
                                        if (isAutostart) null else item.key,
                                    )
                                    onConfigChanged()
                                    contextMenuIndex = -1
                                }
                                .padding(12.dp),
                            color = if (isAutostart) LauncherColors.AccentCyan else LauncherColors.TextPrimary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.launcher_icon_menu_remove),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LauncherHomeStore.removeAtSlot(context, contextMenuIndex)
                                onConfigChanged()
                                contextMenuIndex = -1
                            }
                            .padding(12.dp),
                        color = LauncherColors.TextSecondary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { contextMenuIndex = -1 }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    val settingsItem = homeItems.firstOrNull { it.slotIndex == settingsIndex } as? LauncherHomeItem.App
    if (settingsItem != null) {
        var currentMode by remember(settingsItem.packageName, settingsIndex) {
            mutableStateOf(LauncherAppConfigStore.appLaunchMode(context, settingsItem.packageName))
        }
        val appLabel = appsByPackage[settingsItem.packageName]?.label
            ?: settingsItem.packageName.substringAfterLast('.')
        LauncherDarkAlertDialog(
            onDismissRequest = { settingsIndex = -1 },
            title = { Text(stringResource(R.string.launcher_icon_settings_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = appLabel,
                        color = LauncherColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = stringResource(R.string.launcher_icon_menu_mode_title),
                        color = LauncherColors.TextMuted,
                        fontSize = 12.sp,
                    )

                    val modes = listOf(
                        LauncherAppLaunchMode.EMBEDDED to (
                            stringResource(R.string.launcher_icon_menu_mode_embedded) to
                                stringResource(R.string.launcher_icon_menu_mode_embedded_desc)
                        ),
                        LauncherAppLaunchMode.FULL_WIDTH to (
                            stringResource(R.string.launcher_icon_menu_mode_full_width) to
                                stringResource(R.string.launcher_icon_menu_mode_full_width_desc)
                        ),
                        LauncherAppLaunchMode.FULLSCREEN to (
                            stringResource(R.string.launcher_icon_menu_mode_fullscreen) to
                                stringResource(R.string.launcher_icon_menu_mode_fullscreen_desc)
                        ),
                    )

                    modes.forEach { (mode, titles) ->
                        val selected = currentMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) LauncherColors.AccentCyan.copy(alpha = 0.15f)
                                    else Color(0xFF1E222A),
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) LauncherColors.AccentCyan else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable {
                                    currentMode = mode
                                    LauncherAppConfigStore.setAppLaunchMode(
                                        context,
                                        settingsItem.packageName,
                                        mode,
                                    )
                                    onConfigChanged()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = titles.first,
                                    color = if (selected) LauncherColors.AccentCyan else LauncherColors.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text(
                                    text = titles.second,
                                    color = LauncherColors.TextMuted,
                                    fontSize = 11.sp,
                                )
                            }
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    currentMode = mode
                                    LauncherAppConfigStore.setAppLaunchMode(
                                        context,
                                        settingsItem.packageName,
                                        mode,
                                    )
                                    onConfigChanged()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = LauncherColors.AccentCyan,
                                    unselectedColor = LauncherColors.TextMuted,
                                ),
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.launcher_icon_menu_change_app),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                replaceIndex = settingsIndex
                                settingsIndex = -1
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        color = LauncherColors.AccentCyan,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { settingsIndex = -1 }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    if (showHiddenDialogVisible) {
        val hiddenApps = remember(rawApps, hidden) { rawApps.filter { it.packageName in hidden } }
        LauncherDarkAlertDialog(
            onDismissRequest = { showHiddenDialogVisible = false },
            title = { Text("Скрытые приложения") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hiddenApps.isEmpty() && hidden.isEmpty()) {
                        Text(
                            text = "Нет скрытых приложений",
                            color = LauncherColors.TextMuted,
                            fontSize = 13.sp,
                        )
                    } else {
                        val scrollState = rememberScrollState()
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(scrollState),
                        ) {
                            hidden.forEach { pkg ->
                                val app = hiddenApps.firstOrNull { it.packageName == pkg }
                                val label = app?.label ?: pkg.substringAfterLast('.')
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(LauncherColors.CardDark)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        if (app?.icon != null) {
                                            Image(
                                                bitmap = app.icon,
                                                contentDescription = label,
                                                modifier = Modifier.size(24.dp),
                                                contentScale = ContentScale.Fit,
                                            )
                                        }
                                        Text(
                                            text = label,
                                            color = LauncherColors.TextPrimary,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            LauncherAppConfigStore.showPackage(context, pkg)
                                            onConfigChanged()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = "Показать",
                                            color = LauncherColors.AccentCyan,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                            }
                        }
                        TextButton(
                            onClick = {
                                LauncherAppConfigStore.unhideAllPackages(context)
                                onConfigChanged()
                                showHiddenDialogVisible = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Показать все скрытые",
                                color = LauncherColors.AccentCyan,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHiddenDialogVisible = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    if (vehicleStatusDialogVisible) {
        LauncherVehicleStatusDialog(
            onDismissRequest = { vehicleStatusDialogVisible = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LauncherColors.CanvasDark)
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val rect = coordinates.boundsInWindow()
                    rightPanelLeftPx = rect.left
                    rightPanelTopPx = rect.top
                    LauncherEmbeddedBoundsState.embeddedZoneBounds = android.graphics.Rect(
                        rect.left.toInt(),
                        rect.top.toInt(),
                        rect.right.toInt(),
                        rect.bottom.toInt(),
                    )
                },
        ) {
            activeDraggingEntry?.let { entry ->
                val density = LocalDensity.current
                val localLeftPx = activeDraggingX - rightPanelLeftPx
                val localTopPx = activeDraggingY - rightPanelTopPx
                val leftDp = with(density) { localLeftPx.toDp() }
                val topDp = with(density) { localTopPx.toDp() }
                Box(
                    modifier = Modifier
                        .zIndex(1000f)
                        .offset(x = leftDp, y = topDp)
                        .graphicsLayer {
                            scaleX = 1.18f
                            scaleY = 1.18f
                            shadowElevation = 20f
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    when (entry) {
                        is HomeDockEntry.App -> LauncherAppDockIcon(
                            app = entry.entry,
                            iconScale = homeIconScale,
                            isDragMode = true,
                            onClick = {},
                            onLongClick = {},
                        )
                        is HomeDockEntry.Split -> LauncherSplitDockIcon(
                            preset = entry.preset,
                            leftApp = appsByPackage[entry.preset.leftPackage],
                            rightApp = appsByPackage[entry.preset.rightPackage],
                            iconScale = homeIconScale,
                            isDragMode = true,
                            onClick = {},
                            onLongClick = {},
                        )
                        else -> {}
                    }
                }
            }
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LauncherMetricCard(
                        label = stringResource(R.string.data_title_odometer),
                        value = odometer?.let { "${valueToString(it, 0)} ${stringResource(R.string.unit_km)}" } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                    LauncherMetricCard(
                        label = stringResource(R.string.launcher_metric_range),
                        value = distanceToFuelEmpty?.let { "${valueToString(it, 0)} ${stringResource(R.string.unit_km)}" } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                    LauncherMetricCard(
                        label = stringResource(R.string.launcher_metric_cabin),
                        value = cabinTemp?.let { "${valueToString(it, 1)}°" } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                    LauncherVehicleStatusCard(
                        onClick = { vehicleStatusDialogVisible = true },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val hasShortcuts = remember(dockEntries) {
                    dockEntries.any { it is HomeDockEntry.App || it is HomeDockEntry.Split }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { isDragMode = true },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                val rect = coordinates.boundsInWindow()
                                LauncherEmbeddedBoundsState.dockGridTopPx = rect.top.toInt()
                            },
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(dockEntries, key = { _, entry -> entry.key }) { _, entry ->
                            val slotIdx = when (entry) {
                                is HomeDockEntry.App -> entry.index
                                is HomeDockEntry.Split -> entry.index
                                is HomeDockEntry.Empty -> entry.index
                            }
                            LauncherDraggableHomeDockEntry(
                                entry = entry,
                                slotIndex = slotIdx,
                                autostartKey = autostartKey,
                                appsByPackage = appsByPackage,
                                homeItems = homeItems,
                                visibleApps = visibleApps,
                                isDragMode = isDragMode,
                                gridColumns = gridColumns,
                                gridRows = gridRows,
                                iconScale = homeIconScale,
                                context = context,
                                onEnableDragMode = { isDragMode = true },
                                onOpenAddMenu = { targetSlot ->
                                    LauncherOverlayElevator.bringLauncherToFront(context)
                                    pickerSlotIndex = targetSlot
                                    addMenuVisible = true
                                },
                                onOpenContextMenu = { targetSlot -> contextMenuIndex = targetSlot },
                                onMoveItem = { fromSlot, toSlot ->
                                    LauncherHomeStore.moveItem(context, fromSlot, toSlot)
                                    onConfigChanged()
                                },
                                onRemoveItem = { targetSlot ->
                                    LauncherHomeStore.removeAtSlot(context, targetSlot)
                                    onConfigChanged()
                                },
                                onStartDrag = { dragEntry, startX, startY ->
                                    activeDraggingEntry = dragEntry
                                    activeDraggingX = startX
                                    activeDraggingY = startY
                                },
                                onDragUpdate = { dx, dy ->
                                    activeDraggingX += dx
                                    activeDraggingY += dy
                                },
                                onEndDrag = {
                                    activeDraggingEntry = null
                                },
                                activeDraggingKey = activeDraggingEntry?.key,
                                onConfigChanged = onConfigChanged,
                            )
                        }
                    }

                    if (!hasShortcuts) {
                        Text(
                            text = "Для добавления программы зажмите и подержите в пустом месте, затем нажмите на любой + . Для добавления или редактирования кнопок климата в нижнем меню, зажмите и подержите любую иконку климат контроля или в пустом месте, далее нажмите + или перетащите иконку в нужное место",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(horizontal = 32.dp, vertical = 16.dp),
                        )
                    }
                }

                if (isDragMode) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(LauncherColors.SurfaceDark)
                            .border(1.dp, LauncherColors.AccentCyan.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Настройка сетки: ${gridColumns} × $gridRows · Масштаб ${(homeIconScale * 100).roundToInt()}%",
                                color = LauncherColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            TextButton(
                                onClick = { isDragMode = false },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "Готово",
                                    color = LauncherColors.AccentCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Column X Slider
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Столбцы (X)",
                                        color = LauncherColors.TextSecondary,
                                        fontSize = 11.sp,
                                    )
                                    Text(
                                        text = "$gridColumns",
                                        color = LauncherColors.AccentCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Slider(
                                    value = gridColumns.toFloat(),
                                    onValueChange = { next ->
                                        LauncherAppConfigStore.setGridColumns(context, next.roundToInt())
                                        onConfigChanged()
                                    },
                                    valueRange = GRID_COLUMNS_MIN.toFloat()..GRID_COLUMNS_MAX.toFloat(),
                                    steps = GRID_COLUMNS_MAX - GRID_COLUMNS_MIN - 1,
                                    modifier = Modifier.height(24.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = LauncherColors.AccentCyan,
                                        activeTrackColor = LauncherColors.AccentCyan,
                                        inactiveTrackColor = LauncherColors.TextMuted.copy(alpha = 0.4f),
                                    ),
                                )
                            }

                            // Row Y Slider
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Строки (Y)",
                                        color = LauncherColors.TextSecondary,
                                        fontSize = 11.sp,
                                    )
                                    Text(
                                        text = "$gridRows",
                                        color = LauncherColors.AccentCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Slider(
                                    value = gridRows.toFloat(),
                                    onValueChange = { next ->
                                        LauncherAppConfigStore.setGridRows(context, next.roundToInt())
                                        onConfigChanged()
                                    },
                                    valueRange = GRID_ROWS_MIN.toFloat()..GRID_ROWS_MAX.toFloat(),
                                    steps = GRID_ROWS_MAX - GRID_ROWS_MIN - 1,
                                    modifier = Modifier.height(24.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = LauncherColors.AccentCyan,
                                        activeTrackColor = LauncherColors.AccentCyan,
                                        inactiveTrackColor = LauncherColors.TextMuted.copy(alpha = 0.4f),
                                    ),
                                )
                            }

                            // Icon Scale Slider
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Масштаб",
                                        color = LauncherColors.TextSecondary,
                                        fontSize = 11.sp,
                                    )
                                    Text(
                                        text = "${(homeIconScale * 100).roundToInt()}%",
                                        color = LauncherColors.AccentCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Slider(
                                    value = homeIconScale,
                                    onValueChange = { next ->
                                        LauncherAppConfigStore.setHomeIconScale(context, next)
                                        onConfigChanged()
                                    },
                                    valueRange = HOME_ICON_SCALE_MIN..HOME_ICON_SCALE_MAX,
                                    steps = 23,
                                    modifier = Modifier.height(24.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = LauncherColors.AccentCyan,
                                        activeTrackColor = LauncherColors.AccentCyan,
                                        inactiveTrackColor = LauncherColors.TextMuted.copy(alpha = 0.4f),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val rect = coordinates.boundsInWindow()
                    LauncherEmbeddedBoundsState.rightFooterBottomPx = rect.bottom.toInt()
                },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherDraggableHomeDockEntry(
    entry: HomeDockEntry,
    slotIndex: Int,
    autostartKey: String?,
    appsByPackage: Map<String, LaunchableAppEntry>,
    homeItems: List<LauncherHomeItem>,
    visibleApps: List<LaunchableAppEntry>,
    isDragMode: Boolean,
    gridColumns: Int,
    gridRows: Int,
    iconScale: Float = 1f,
    context: Context,
    onEnableDragMode: () -> Unit,
    onOpenAddMenu: (slotIdx: Int) -> Unit,
    onOpenContextMenu: (slotIdx: Int) -> Unit,
    onMoveItem: (fromSlot: Int, toSlot: Int) -> Unit,
    onRemoveItem: (slotIdx: Int) -> Unit,
    onStartDrag: (entry: HomeDockEntry, startX: Float, startY: Float) -> Unit,
    onDragUpdate: (dx: Float, dy: Float) -> Unit,
    onEndDrag: () -> Unit,
    activeDraggingKey: String?,
    onConfigChanged: () -> Unit = {},
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var cellWidthPx by remember { mutableFloatStateOf(1f) }
    var cellHeightPx by remember { mutableFloatStateOf(1f) }
    var itemTopPx by remember { mutableFloatStateOf(0f) }
    var itemLeftX by remember { mutableFloatStateOf(0f) }

    val isInteractive = entry is HomeDockEntry.App || entry is HomeDockEntry.Split
    val isBeingDragged = activeDraggingKey == entry.key

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                itemTopPx = bounds.top
                itemLeftX = bounds.left
                cellWidthPx = coordinates.size.width.toFloat().coerceAtLeast(1f)
                cellHeightPx = coordinates.size.height.toFloat().coerceAtLeast(1f)
            }
            .then(
                if (isDragMode) {
                    Modifier.border(
                        width = 0.8.dp,
                        color = if (isInteractive) LauncherColors.AccentCyan.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (isInteractive) {
                    Modifier.pointerInput(entry.key, slotIndex, isDragMode, gridColumns, gridRows) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                offsetX = 0f
                                offsetY = 0f
                                onStartDrag(entry, itemLeftX, itemTopPx)
                            },
                            onDragEnd = {
                                val colShift = (offsetX / cellWidthPx).roundToInt()
                                val rowShift = (offsetY / cellHeightPx).roundToInt()

                                val currentColumn = slotIndex % gridColumns
                                val currentRow = slotIndex / gridColumns

                                val targetColumn = (currentColumn + colShift).coerceIn(0, gridColumns - 1)
                                val targetRow = currentRow + rowShift

                                val bottomBarTop = LauncherEmbeddedBoundsState.bottomBarTopPx
                                val droppedTopY = itemTopPx + offsetY
                                val isDroppedInBottomBar = (bottomBarTop > 0 && droppedTopY >= bottomBarTop - 40) ||
                                    (targetRow >= gridRows) ||
                                    (offsetY > cellHeightPx * (gridRows - currentRow - 0.3f))

                                val buttonId = when (entry) {
                                    is HomeDockEntry.App -> "pkg:${entry.entry.packageName}"
                                    is HomeDockEntry.Split -> "split:${entry.preset.id}"
                                    else -> null
                                }
                                if (isDroppedInBottomBar && buttonId != null) {
                                    val droppedLeftX = itemLeftX + offsetX
                                    val displayMetrics = context.resources.displayMetrics
                                    val screenWidthPx = displayMetrics.widthPixels
                                    val slotUnitWidthPx = ((screenWidthPx - (24f * displayMetrics.density)) / GRID_SLOTS_TOTAL_COUNT.toFloat()).coerceAtLeast(1f)
                                    val targetBottomSlot = (droppedLeftX / slotUnitWidthPx).roundToInt().coerceIn(0, GRID_SLOTS_TOTAL_COUNT - 1)

                                    LauncherAppConfigStore.setButtonInUnifiedSlot(context, targetBottomSlot, buttonId)
                                    onConfigChanged()
                                } else {
                                    val isDraggedAboveGrid = targetRow < 0 && offsetY < -cellHeightPx * (currentRow + 0.6f)

                                    if (isDraggedAboveGrid) {
                                        onRemoveItem(slotIndex)
                                    } else {
                                        val validTargetRow = targetRow.coerceIn(0, gridRows - 1)
                                        val targetSlot = validTargetRow * gridColumns + targetColumn
                                        if (targetSlot != slotIndex) {
                                            onMoveItem(slotIndex, targetSlot)
                                        }
                                    }
                                }
                                offsetX = 0f
                                offsetY = 0f
                                onEndDrag()
                            },
                            onDragCancel = {
                                offsetX = 0f
                                offsetY = 0f
                                onEndDrag()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                                onDragUpdate(dragAmount.x, dragAmount.y)
                            },
                        )
                    }
                } else {
                    Modifier
                }
            )
            .graphicsLayer {
                if (isBeingDragged) {
                    alpha = 0.25f
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (entry) {
            is HomeDockEntry.App -> LauncherAppDockIcon(
                app = entry.entry,
                autostart = homeItems.firstOrNull { it.slotIndex == slotIndex }?.key == autostartKey,
                iconScale = iconScale,
                isDragMode = isDragMode,
                onClick = {
                    if (!isDragMode) {
                        launchLauncherApp(context, entry.entry.packageName, entry.entry.activityName)
                    }
                },
                onLongClick = { onOpenContextMenu(slotIndex) },
            )
            is HomeDockEntry.Split -> LauncherSplitDockIcon(
                preset = entry.preset,
                leftApp = appsByPackage[entry.preset.leftPackage],
                rightApp = appsByPackage[entry.preset.rightPackage],
                autostart = homeItems.firstOrNull { it.slotIndex == slotIndex }?.key == autostartKey,
                iconScale = iconScale,
                isDragMode = isDragMode,
                onClick = {
                    if (!isDragMode) {
                        launchSplitPreset(context, entry.preset, visibleApps)
                    }
                },
                onLongClick = { onOpenContextMenu(slotIndex) },
            )
            is HomeDockEntry.Empty -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((60 * iconScale).dp)
                    .then(
                        if (isDragMode) {
                            Modifier.clickable { onOpenAddMenu(slotIndex) }
                        } else {
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = onEnableDragMode,
                            )
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isDragMode) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Добавить иконку",
                        tint = LauncherColors.AccentCyan.copy(alpha = 0.6f),
                        modifier = Modifier.size((20 * iconScale).dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherAppDockIcon(
    app: LaunchableAppEntry,
    autostart: Boolean = false,
    iconScale: Float = 1f,
    isDragMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size((60 * iconScale).dp)
            .clip(RoundedCornerShape((16 * iconScale).dp))
            .background(LauncherColors.CardDarkElevated)
            .then(
                if (!isDragMode) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier.size((39 * iconScale).dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = app.label.take(1).uppercase(),
                color = LauncherColors.AccentCyan,
                fontSize = (16 * iconScale).sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (autostart) {
            LauncherAutostartBadge(Modifier.align(Alignment.TopEnd).padding((3 * iconScale).dp))
        }
    }
}

@Composable
private fun LauncherAutostartBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(15.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(LauncherColors.AccentCyan),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = LauncherColors.SurfaceDark,
            modifier = Modifier.size(11.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherSplitDockIcon(
    preset: LauncherSplitPreset,
    leftApp: LaunchableAppEntry?,
    rightApp: LaunchableAppEntry?,
    autostart: Boolean = false,
    iconScale: Float = 1f,
    isDragMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size((60 * iconScale).dp)
            .clip(RoundedCornerShape((16 * iconScale).dp))
            .background(LauncherColors.CardDarkElevated)
            .then(
                if (!isDragMode) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                }
            ),
    ) {
        Box(
            modifier = Modifier
                .size((34 * iconScale).dp)
                .align(Alignment.CenterStart)
                .offset(x = (7 * iconScale).dp),
            contentAlignment = Alignment.Center,
        ) {
            LauncherMiniIcon(app = leftApp, fallback = preset.leftPackage.substringAfterLast('.'), iconScale = iconScale)
        }
        Box(
            modifier = Modifier
                .size((34 * iconScale).dp)
                .align(Alignment.CenterEnd)
                .offset(x = (-7 * iconScale).dp),
            contentAlignment = Alignment.Center,
        ) {
            LauncherMiniIcon(app = rightApp, fallback = preset.rightPackage.substringAfterLast('.'), iconScale = iconScale)
        }
        Text(
            text = "‖",
            modifier = Modifier.align(Alignment.Center),
            color = LauncherColors.AccentCyan.copy(alpha = 0.7f),
            fontSize = (14 * iconScale).sp,
        )
        if (autostart) {
            LauncherAutostartBadge(Modifier.align(Alignment.TopEnd).padding((3 * iconScale).dp))
        }
    }
}

@Composable
internal fun LauncherMiniIcon(app: LaunchableAppEntry?, fallback: String, iconScale: Float = 1f) {
    if (app?.icon != null) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier.size((28 * iconScale).dp),
            contentScale = ContentScale.Fit,
        )
    } else {
        Text(
            text = (app?.label ?: fallback).take(1).uppercase(),
            color = LauncherColors.AccentCyan,
            fontSize = (12 * iconScale).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LauncherMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(LauncherColors.CardDark)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.tboxCaption,
            color = LauncherColors.TextSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = LauncherColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Suppress("UNUSED")
@Composable
private fun LauncherFooterIconPill(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(LauncherColors.CardDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private enum class LauncherVehicleHealthStatus {
    Ok,       // Зелёный значок OK в кругу
    Warning,  // Жёлтый значок "восклицательный знак в треугольнике"
    Error,    // Красный значок Warning! в прямоугольной рамке
}

@Composable
private fun rememberVehicleHealthStatus(): LauncherVehicleHealthStatus {
    val alertsState by LauncherVehicleAlertsRepository.state.collectAsStateWithLifecycle()
    val adasState by LauncherAdasRepository.state.collectAsStateWithLifecycle()
    val tiresState by LauncherTireRepository.state.collectAsStateWithLifecycle()
    val simShowAll = LauncherDevVehicleState.showAllIndicators

    val alerts = if (simShowAll) alertsState.alerts else alertsState.alerts.filterNot { it.id.isBodyOpenAlert }
    val adas = LauncherDevVehicleState.adasStateOrNull() ?: adasState

    val hasCritical = simShowAll ||
        alerts.any { it.severity == LauncherAlertSeverity.Critical } ||
        tiresState.hasAttention ||
        adas.fcwActive || adas.aebHint || adas.accTakeOver || adas.adasTakeOver ||
        adas.srrSystem == LauncherSrrSystemState.Fault

    if (hasCritical) return LauncherVehicleHealthStatus.Error

    val hasWarning = alerts.any { it.severity == LauncherAlertSeverity.Warning } ||
        adas.hasAnyAlert || adas.rearThreats.hasAny || adas.speedLimitWarning

    if (hasWarning) return LauncherVehicleHealthStatus.Warning

    return LauncherVehicleHealthStatus.Ok
}

private data class LauncherVehicleStatusDetailItem(
    val title: String,
    val description: String,
    val severity: LauncherAlertSeverity,
)

private val LauncherAlertId.detailDescription: String
    get() = when (this) {
        LauncherAlertId.SeatBeltDriver -> "Водитель не пристёгнут. Зафиксируйте ремень безопасности перед началом движения."
        LauncherAlertId.SeatBeltPassenger -> "Передний пассажир не пристёгнут. Зафиксируйте ремень безопасности."
        LauncherAlertId.SeatBeltRearLeft -> "Пассажир на заднем левом сиденье не пристёгнут."
        LauncherAlertId.SeatBeltRearMid -> "Пассажир на заднем среднем сиденье не пристёгнут."
        LauncherAlertId.SeatBeltRearRight -> "Пассажир на заднем правом сиденье не пристёгнут."
        LauncherAlertId.DoorDriver -> "Водительская дверь не закрыта полностью."
        LauncherAlertId.DoorPassenger -> "Передняя пассажирская дверь не закрыта полностью."
        LauncherAlertId.DoorRearLeft -> "Задняя левая дверь не закрыта полностью."
        LauncherAlertId.DoorRearRight -> "Задняя правая дверь не закрыта полностью."
        LauncherAlertId.HoodOpen -> "Крышка капота неплотно закрыта. Проверьте фиксацию защёлки."
        LauncherAlertId.TrunkOpen -> "Дверь багажного отделения открыта."
        LauncherAlertId.TirePressure -> "Давление в колёсах ниже рекомендуемого порога (2.0 bar)."
        LauncherAlertId.LowFuel -> "Минимальный остаток топлива в баке. Пополните запас топлива."
        LauncherAlertId.Speeding -> "Текущая скорость превышает установленное ограничение."
        LauncherAlertId.HighTemperature -> "Высокая температура охлаждающей жидкости. Остановите движение и дайте двигателю остыть."
        LauncherAlertId.PressBrake -> "Для продолжения или смены режима нажмите педаль тормоза."
        LauncherAlertId.SysFault -> "Зафиксирована ошибка системы управления ICM или блоков CAN."
        LauncherAlertId.BattFault -> "Неисправность аккумулятора 12V или системы контроля батареи."
        LauncherAlertId.ChargeFault -> "Ошибка процесса зарядки АКБ или генератора."
        LauncherAlertId.HvFaultStop -> "Критический сбой высоковольтной системы HV. Требуется остановка и сервисная диагностика."
        LauncherAlertId.PowerModeFail -> "Ошибка переключения режимов питания Power Mode."
        LauncherAlertId.LowBatterySoc -> "Низкий уровень заряда аккумулятора (Low SOC)."
    }

@Composable
private fun rememberVehicleStatusDetailItems(): List<LauncherVehicleStatusDetailItem> {
    val alertsState by LauncherVehicleAlertsRepository.state.collectAsStateWithLifecycle()
    val adasState by LauncherAdasRepository.state.collectAsStateWithLifecycle()
    val tiresState by LauncherTireRepository.state.collectAsStateWithLifecycle()
    val simShowAll = LauncherDevVehicleState.showAllIndicators

    val alerts = if (simShowAll) alertsState.alerts else alertsState.alerts.filterNot { it.id.isBodyOpenAlert }
    val adas = LauncherDevVehicleState.adasStateOrNull() ?: adasState

    return buildList {
        alerts.forEach { alert ->
            add(
                LauncherVehicleStatusDetailItem(
                    title = stringResource(alert.id.labelRes),
                    description = alert.id.detailDescription,
                    severity = alert.severity,
                )
            )
        }

        if (tiresState.hasAttention) {
            val lowCorners = listOf(
                "Переднее левое" to tiresState.fl,
                "Переднее правое" to tiresState.fr,
                "Заднее левое" to tiresState.rl,
                "Заднее правое" to tiresState.rr,
            ).filter { it.second.needsAttention }

            lowCorners.forEach { (cornerName, wheel) ->
                val press = wheel.pressureBar?.let { "${(it * 10).roundToInt() / 10f} bar" } ?: "—"
                add(
                    LauncherVehicleStatusDetailItem(
                        title = "Низкое давление: $cornerName",
                        description = "Текущее давление $press ниже норматива (2.0 bar). Проверьте колесо.",
                        severity = LauncherAlertSeverity.Critical,
                    )
                )
            }
        }

        if (adas.fcwActive || adas.distanceWarning) {
            add(
                LauncherVehicleStatusDetailItem(
                    title = "Риск лобового столкновения (FCW)",
                    description = "Обнаружено опасное сближение с объектом впереди.",
                    severity = LauncherAlertSeverity.Critical,
                )
            )
        }

        if (adas.aebHint) {
            add(
                LauncherVehicleStatusDetailItem(
                    title = "Экстренное торможение (AEB)",
                    description = "Задействовано автоматическое экстренное торможение.",
                    severity = LauncherAlertSeverity.Critical,
                )
            )
        }

        if (adas.accTakeOver || adas.adasTakeOver) {
            add(
                LauncherVehicleStatusDetailItem(
                    title = "Требование вмешательства водителя",
                    description = "Возьмите рулевое управление и задействуйте тормоз.",
                    severity = LauncherAlertSeverity.Critical,
                )
            )
        }

        if (adas.srrSystem == LauncherSrrSystemState.Fault) {
            add(
                LauncherVehicleStatusDetailItem(
                    title = "Ошибка боковых радаров (SRR)",
                    description = "Неисправность или перекрытие датчиков слепых зон.",
                    severity = LauncherAlertSeverity.Critical,
                )
            )
        }

        if (adas.rearThreats.hasAny) {
            add(
                LauncherVehicleStatusDetailItem(
                    title = "Помеха в слепой зоне (BSD / RCTA)",
                    description = "Фиксируется транспортное средство в слепой зоне или при поперечном выезде.",
                    severity = LauncherAlertSeverity.Warning,
                )
            )
        }
    }
}

@Composable
private fun LauncherVehicleStatusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = rememberVehicleHealthStatus()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(LauncherColors.CardDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.launcher_vehicle_status_label),
            style = MaterialTheme.typography.tboxCaption,
            color = LauncherColors.TextSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            when (status) {
                LauncherVehicleHealthStatus.Ok -> {
                    Text(
                        text = stringResource(R.string.launcher_vehicle_status_ok),
                        color = Color(0xFF22C55E),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LauncherVehicleHealthStatus.Warning -> {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFFF59E0B),
                    )
                    Text(
                        text = stringResource(R.string.launcher_vehicle_status_warning),
                        color = Color(0xFFF59E0B),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LauncherVehicleHealthStatus.Error -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .border(width = 1.2.dp, color = Color(0xFFEF4444), shape = RoundedCornerShape(4.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.16f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Warning!",
                            color = Color(0xFFEF4444),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LauncherVehicleStatusDialog(
    onDismissRequest: () -> Unit,
) {
    val items = rememberVehicleStatusDetailItems()
    val status = rememberVehicleHealthStatus()

    LauncherDarkAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Состояние систем авто",
                    color = LauncherColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                when (status) {
                    LauncherVehicleHealthStatus.Ok -> {
                        Text(
                            text = "В норме",
                            color = Color(0xFF22C55E),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    LauncherVehicleHealthStatus.Warning -> {
                        Text(
                            text = "Внимание",
                            color = Color(0xFFF59E0B),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    LauncherVehicleHealthStatus.Error -> {
                        Text(
                            text = "Ошибка!",
                            color = Color(0xFFEF4444),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E).copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "✓",
                                    color = Color(0xFF22C55E),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text = "Тут нечего смотреть, у Дашки всё отлично!",
                                color = LauncherColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    items.forEach { item ->
                        val tint = when (item.severity) {
                            LauncherAlertSeverity.Critical -> Color(0xFFEF4444)
                            LauncherAlertSeverity.Warning -> Color(0xFFF59E0B)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(LauncherColors.SurfaceDark)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(tint.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = tint,
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = item.title,
                                    color = tint,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = item.description,
                                    color = LauncherColors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_close), color = LauncherColors.AccentCyan)
            }
        },
    )
}
