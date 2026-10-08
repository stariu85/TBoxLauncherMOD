package vad.dashing.tbox.ui.launcher

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import vad.dashing.tbox.AppDataManager
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.SettingsViewModelFactory
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.ui.theme.TboxAppTheme

@Composable
fun TeslaLauncherScreen(
    settingsManager: SettingsManager,
    appDataManager: AppDataManager,
    onTboxRestart: () -> Unit,
    onTripFinishAndStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tboxViewModel: TboxViewModel = viewModel()
    val canViewModel: CanDataViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(settingsManager),
    )

    val currentTheme by tboxViewModel.currentTheme.collectAsStateWithLifecycle()
    var appDrawerVisible by remember { mutableStateOf(false) }
    var drawerEditMode by remember { mutableStateOf(false) }
    var configRevision by remember { mutableIntStateOf(0) }
    var paintRevision by remember { mutableIntStateOf(0) }
    var carPaintId by remember(context) { mutableStateOf(LauncherAppConfigStore.carPaintId(context)) }
    var colorPickerVisible by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        LauncherVehicleBodyRepository.ensurePolling()
        LauncherAdasRepository.ensureActive()
        LauncherVehicleAlertsRepository.ensureActive()
        onDispose {
            LauncherAppDrawerWindow.hide()
            LauncherAppPickerOverlayWindow.hide()
            LauncherVehicleBodyRepository.stopPolling()
            LauncherAdasRepository.stop()
            LauncherVehicleAlertsRepository.stop()
            LauncherOverlayElevator.setHoldSource("vehicle_settings", false)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            LauncherAdasRepository.ensureActive()
            delay(2_000L)
        }
    }

    val sidebarWidth = rememberLauncherSidebarWidth()
    val settingsOpen = LauncherVehicleSettingsUiState.open
    val isDraggingApp = LauncherDropTargetState.draggingPackage != null

    val openConsole: () -> Unit = {
        launchTBoxSettings(context)
    }

    val onConfigChanged: () -> Unit = { configRevision++ }
    val iconRevision by settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()

    val openVehicleSettings: () -> Unit = {
        launchVehicleSettingsWindow(context)
    }
    val closeVehicleSettings: () -> Unit = {
        closeVehicleSettingsOverlay()
    }
    val openAppDrawer: () -> Unit = {
        val shownAsSystemOverlay = LauncherAppDrawerWindow.show(
            context = context,
            settingsViewModel = settingsViewModel,
            onConfigChanged = onConfigChanged,
        )
        appDrawerVisible = !shownAsSystemOverlay
    }

    LaunchedEffect(appDrawerVisible) {
        LauncherOverlayElevator.setHoldSource("main_overlay", appDrawerVisible)
    }

    LaunchedEffect(configRevision, iconRevision) {
        carPaintId = LauncherAppConfigStore.carPaintId(context)
    }

    // Standalone launcher is dark-first; TBox theme channel is idle without the proxy.
    TboxAppTheme(theme = 2) {
        BackHandler(LauncherEggRace.active) {
            LauncherEggRace.stop()
        }
        BackHandler(settingsOpen) {
            closeVehicleSettings()
        }
        BackHandler(appDrawerVisible) {
            if (drawerEditMode) {
                drawerEditMode = false
            } else {
                appDrawerVisible = false
            }
        }
        BackHandler(enabled = !settingsOpen && !LauncherEggRace.active) {
            goLauncherBack(
                context = context,
                vehicleSettingsOpen = false,
                onCloseVehicleSettings = {},
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    val rect = coordinates.boundsInWindow()
                    LauncherEmbeddedBoundsState.screenBounds = android.graphics.Rect(
                        rect.left.toInt(),
                        rect.top.toInt(),
                        rect.right.toInt(),
                        rect.bottom.toInt(),
                    )
                },
        ) {
            val topBarRevision by LauncherAppConfigStore.topBarHeightRevisionFlow.collectAsStateWithLifecycle()
            val topBarHeightDp = remember(context, topBarRevision) {
                LauncherAppConfigStore.topBarHeightDp(context)
            }
            LauncherDevScaleProvider {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .background(LauncherColors.CanvasDark)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    LauncherTopHeaderBar(
                        canViewModel = canViewModel,
                        tboxViewModel = tboxViewModel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topBarHeightDp.dp)
                            .padding(end = 20.dp),
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                val rect = coordinates.boundsInWindow()
                                LauncherEmbeddedBoundsState.contentRowBounds = android.graphics.Rect(
                                    rect.left.toInt(),
                                    rect.top.toInt(),
                                    rect.right.toInt(),
                                    rect.bottom.toInt(),
                                )
                            },
                    ) {
                        LauncherLeftPanel(
                            tboxViewModel = tboxViewModel,
                            canViewModel = canViewModel,
                            onOpenVehicleSettings = openVehicleSettings,
                            modelRevision = 0,
                            paintId = carPaintId,
                            paintRevision = paintRevision,
                            onCarBoundsChanged = {},
                            colorPickerVisible = colorPickerVisible && !settingsOpen,
                            roadVisible = !settingsOpen,
                            carHidden = false,
                            settingsTransitionProgress = 0f,
                            settingsUserYawDeg = 0f,
                            onColorPickerOpen = { colorPickerVisible = true },
                            onColorPickerDismiss = { colorPickerVisible = false },
                            onPaintChanged = { id ->
                                carPaintId = id
                                paintRevision++
                            },
                            modifier = Modifier
                                .width(sidebarWidth)
                                .fillMaxHeight(),
                        )
                        LauncherRightPanel(
                            canViewModel = canViewModel,
                            settingsViewModel = settingsViewModel,
                            tboxViewModel = tboxViewModel,
                            onOpenConsole = openConsole,
                            onOpenApps = openAppDrawer,
                            configRevision = configRevision,
                            onConfigChanged = onConfigChanged,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    LauncherBottomBar(
                        canViewModel = canViewModel,
                        onCloseVehicleSettings = closeVehicleSettings,
                        onOpenVehicleSettings = openVehicleSettings,
                        configRevision = configRevision,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime)),
                    )
                }
            }

            if (settingsOpen && !LauncherVehicleSettingsOverlayWindow.isShowing()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(50f)
                        .background(LauncherColors.LeftPanelBg),
                ) {
                    LauncherVehicleSettingsScreen(
                        canViewModel = canViewModel,
                        tboxViewModel = tboxViewModel,
                        onClose = closeVehicleSettings,
                        revealProgress = 1f,
                    )
                }
                // Keep the home/climate dock visible above the settings overlay.
                // Wrap in LauncherDevScaleProvider — it's outside the main scaled tree,
                // otherwise it renders smaller than the home dock.
                LauncherDevScaleProvider {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .zIndex(60f)
                            .windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime)),
                    ) {
                        LauncherBottomBar(
                            canViewModel = canViewModel,
                            onCloseVehicleSettings = closeVehicleSettings,
                            onOpenVehicleSettings = openVehicleSettings,
                            configRevision = configRevision,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f),
            ) {
                LauncherAppDrawer(
                    visible = appDrawerVisible,
                    settingsViewModel = settingsViewModel,
                    onDismiss = { appDrawerVisible = false },
                    editMode = drawerEditMode,
                    onEditModeChange = { drawerEditMode = it },
                    configRevision = configRevision,
                    onConfigChanged = onConfigChanged,
                )
                LauncherAppDragOverlay(
                    dragging = isDraggingApp,
                    sidebarWidth = sidebarWidth,
                )
            }
        }

        val currentVersionCode = remember(context) {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        }
        LauncherHuRebootStartupDialog(
            settingsManager = settingsManager,
            currentVersionCode = currentVersionCode,
        )
    }
}
