package vad.dashing.tbox.ui.launcher

import android.os.Build
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.LauncherHomeActivityHolder
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import vad.dashing.tbox.AppDataManager
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.LauncherVehicleSettingsActivity
import vad.dashing.tbox.LauncherWindowState
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.SettingsViewModelFactory
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.ui.ProvideUiClickSounds
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

    val surfaceRecoveryEpoch by LauncherCarSurfaceRecovery.epoch.collectAsStateWithLifecycle()
    val bsdOverlayWindowShowing by LauncherBsdAlertOverlayWindow.isShowingFlow.collectAsStateWithLifecycle()
    var appDrawerVisible by remember { mutableStateOf(false) }
    var drawerEditMode by remember { mutableStateOf(false) }
    var configRevision by remember { mutableIntStateOf(0) }
    val carPaintRevision by LauncherAppConfigStore.carPaintRevisionFlow.collectAsStateWithLifecycle()
    val carPaintId = remember(context, carPaintRevision) {
        LauncherAppConfigStore.carPaintId(context)
    }
    var colorPickerVisible by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
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
        // Postpone heavy CAN polling and reflection listeners until after initial HOME frame renders
        delay(1_200L)
        LauncherVehicleBodyRepository.ensurePolling()
        LauncherAdasRepository.ensureActive()
        LauncherVehicleAlertsRepository.ensureActive(context)
        while (true) {
            delay(2_000L)
            LauncherAdasRepository.ensureActive()
        }
    }

    val solarDaytime = rememberSolarDaytimeState()
    LaunchedEffect(solarDaytime, LauncherThemeState.autoThemeSolarEnabled) {
        if (LauncherThemeState.autoThemeSolarEnabled && solarDaytime != null) {
            LauncherThemeState.setDarkTheme(context, !solarDaytime)
        }
    }

    val sidebarWidth = rememberLauncherSidebarWidth()
    val adasPanelRevision by LauncherAppConfigStore.adasPanelVisibleRevisionFlow
        .collectAsStateWithLifecycle()
    val adasPanelVisible = remember(context, adasPanelRevision, configRevision) {
        LauncherAppConfigStore.adasPanelVisible(context)
    }

    LaunchedEffect(sidebarWidth, adasPanelVisible, configRevision) {
        if (!adasPanelVisible) {
            LauncherEmbeddedBoundsState.leftPanelBounds = null
        }
        LauncherRightPanelOverlayWindow.updateBounds()
    }
    val settingsOpen = LauncherVehicleSettingsUiState.open
    val useNewSettingsViewRevision by LauncherAppConfigStore.useNewSettingsViewRevisionFlow.collectAsStateWithLifecycle()
    val useNewSettingsView = remember(context, useNewSettingsViewRevision) {
        LauncherAppConfigStore.useNewSettingsView(context)
    }
    val isDraggingApp = LauncherDropTargetState.draggingPackage != null

    val onConfigChanged: () -> Unit = { configRevision++ }

    val openVehicleSettings: () -> Unit = {
        launchVehicleSettingsWindow(context)
    }
    val closeVehicleSettings: () -> Unit = {
        closeVehicleSettingsOverlay()
    }

    LaunchedEffect(appDrawerVisible) {
        if (appDrawerVisible) {
            closeVehicleSettings()
        }
        LauncherOverlayElevator.setHoldSource("main_overlay", appDrawerVisible)
    }

    LaunchedEffect(context) {
        LauncherAdasRepository.state.collect { adasLive ->
            val adas = LauncherDevVehicleState.adasStateOrNull() ?: adasLive
            LauncherBsdAlertOverlayWindow.update(context, adas)
        }
    }

    // Standalone launcher is dark-first; TBox theme channel is idle without the proxy.
    ProvideUiClickSounds {
        TboxAppTheme(theme = 2) {
        LauncherRenderThreadAffinityEffect()
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
        BackHandler(enabled = !appDrawerVisible && !settingsOpen && !LauncherEggRace.active) {
            goLauncherBack(
                context = context,
                vehicleSettingsOpen = settingsOpen,
                onCloseVehicleSettings = closeVehicleSettings,
                source = "root_desktop_back_handler",
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
                var contentRowWidthPx by remember { mutableFloatStateOf(0f) }
                var contentRowHeightPx by remember { mutableFloatStateOf(0f) }
                val sidebarWidthPx = with(LocalDensity.current) { sidebarWidth.toPx() }
                val rightPanelWidthPx = if (adasPanelVisible) {
                    (contentRowWidthPx - sidebarWidthPx).coerceAtLeast(1f)
                } else {
                    contentRowWidthPx.coerceAtLeast(1f)
                }
                val rightPanelAspect = if (contentRowWidthPx > 1f && contentRowHeightPx > 1f) {
                    rightPanelWidthPx / contentRowHeightPx
                } else {
                    16f / 9f
                }
                val synced30FpsClock = remember { LauncherSynced30FpsClock() }
                LauncherSynced30FpsClockHost(synced30FpsClock)
                val ambilightUiState = remember { LauncherAmbilightUiState() }
                LauncherAmbilightController(
                    state = ambilightUiState,
                    syncedClock = synced30FpsClock,
                    adasPanelVisible = adasPanelVisible,
                    sidebarWidthPx = sidebarWidthPx,
                    screenWidthPx = contentRowWidthPx,
                    rightPanelAspect = rightPanelAspect,
                )

                CompositionLocalProvider(
                    LocalLauncherSynced30FpsClock provides synced30FpsClock,
                    LocalLauncherAmbilightState provides ambilightUiState,
                ) {
                    Column(
                        modifier = modifier
                            .fillMaxSize()
                            .background(LauncherColors.CanvasDark),
                    ) {
                        LauncherTopHeaderBar(
                            canViewModel = canViewModel,
                            tboxViewModel = tboxViewModel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(topBarHeightDp.dp)
                                .launcherTopBarAmbilight(ambilightUiState),
                        )
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .onGloballyPositioned { coordinates ->
                                    contentRowWidthPx = coordinates.size.width.toFloat()
                                    contentRowHeightPx = coordinates.size.height.toFloat()
                                    val rect = coordinates.boundsInWindow()
                                    LauncherEmbeddedBoundsState.contentRowBounds = android.graphics.Rect(
                                        rect.left.toInt(),
                                        rect.top.toInt(),
                                        rect.right.toInt(),
                                        rect.bottom.toInt(),
                                    )
                                },
                        ) {
                            if (adasPanelVisible) {
                                LauncherLeftPanel(
                                    tboxViewModel = tboxViewModel,
                                    canViewModel = canViewModel,
                                    onOpenVehicleSettings = openVehicleSettings,
                                    modelRevision = surfaceRecoveryEpoch,
                                    paintId = carPaintId,
                                    paintRevision = carPaintRevision,
                                    onCarBoundsChanged = {},
                                    colorPickerVisible = colorPickerVisible && (useNewSettingsView || !settingsOpen),
                                    roadVisible = if (useNewSettingsView) true else !settingsOpen,
                                    carHidden = false,
                                    settingsTransitionProgress = 0f,
                                    settingsUserYawDeg = 0f,
                                    onColorPickerOpen = { colorPickerVisible = true },
                                    onColorPickerDismiss = { colorPickerVisible = false },
                                    onPaintChanged = { id ->
                                        LauncherAppConfigStore.setCarPaintId(context, id)
                                    },
                                    modifier = Modifier
                                        .width(sidebarWidth)
                                        .fillMaxHeight(),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(LauncherColors.CanvasDark),
                            ) {
                                var rightPanelConfigRev by remember { mutableIntStateOf(0) }
                                LauncherRightPanel(
                                    canViewModel = canViewModel,
                                    settingsViewModel = settingsViewModel,
                                    tboxViewModel = tboxViewModel,
                                    onOpenConsole = { launchTBoxSettings(context) },
                                    onOpenApps = {
                                        val activity = LauncherHomeActivityHolder.instance ?: (context as? LauncherHomeActivity)
                                        if (activity != null) {
                                            LauncherAppDrawerWindow.toggle(activity, settingsViewModel, {})
                                        }
                                    },
                                    configRevision = rightPanelConfigRev,
                                    onConfigChanged = { rightPanelConfigRev++ },
                                    modifier = Modifier.fillMaxSize(),
                                )

                                if (settingsOpen && useNewSettingsView) {
                                    LauncherNewSettingsScreen(
                                        canViewModel = canViewModel,
                                        tboxViewModel = tboxViewModel,
                                        onClose = closeVehicleSettings,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .zIndex(10f),
                                    )
                                }
                            }
                        }
                        LauncherBottomBar(
                            canViewModel = canViewModel,
                            onCloseVehicleSettings = closeVehicleSettings,
                            onOpenVehicleSettings = openVehicleSettings,
                            configRevision = configRevision,
                        )
                    }
                }
            }

            if (settingsOpen && !LauncherVehicleSettingsOverlayWindow.isShowing() && !useNewSettingsView) {
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
                            .zIndex(60f),
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
                    configRevision = configRevision,
                    onConfigChanged = onConfigChanged,
                    onLaunchApp = { app ->
                        appDrawerVisible = false
                        launchLauncherAppEmbedded(context, app.packageName, app.activityName)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                LauncherAppDragOverlay(
                    dragging = isDraggingApp,
                    sidebarWidth = if (adasPanelVisible) sidebarWidth else 0.dp,
                )
            }

            if (!adasPanelVisible && !settingsOpen && !appDrawerVisible) {
                val bottomBarRevision by LauncherAppConfigStore.bottomBarHeightRevisionFlow.collectAsStateWithLifecycle()
                val bottomBarHeightDp = remember(context, bottomBarRevision) {
                    LauncherAppConfigStore.bottomBarHeightDp(context)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(200f),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    IconButton(
                        onClick = openVehicleSettings,
                        modifier = Modifier
                            .padding(end = 20.dp, bottom = (bottomBarHeightDp + 16).dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(LauncherColors.CardDarkElevated.copy(alpha = 0.92f))
                            .border(1.dp, LauncherColors.SettingsBorder, CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.tab_car_settings),
                            tint = LauncherColors.AccentCyan,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            if (!bsdOverlayWindowShowing) {
                LauncherBsdAlertOverlay(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1000f),
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
}

@Composable
private fun LauncherRenderThreadAffinityEffect() {
    val context = LocalContext.current
    val firstDrawComplete by LauncherHomeActivityHolder.firstDrawFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val renderThreadRevision by LauncherAppConfigStore.experimentalPinRenderThreadRevisionFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val mode = remember(context, renderThreadRevision) {
        LauncherCpuAffinity.resolveRenderThreadAffinityMode(
            alwaysEnabled = LauncherAppConfigStore.experimentalPinRenderThreadAlwaysCpu05(context),
            smartEnabled = LauncherAppConfigStore.experimentalPinRenderThreadSmartCpu05(context),
        )
    }

    val hiddenForExternalApp by LauncherWindowState.hiddenForExternalAppFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val activeAppPackage by LauncherActiveAppTracker.activeAppPackageFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val isActiveAppFullscreen by LauncherActiveAppTracker.isFullscreenFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val isWindowFocused by LauncherHomeActivityHolder.windowFocusedFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val isTopShadeOpen by LauncherCar3DModelTrigger.topShadeOpen
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val isVehicleSettingsOpen = LauncherVehicleSettingsUiState.open
    val isVehicleSettingsOverlayShowing by LauncherVehicleSettingsOverlayWindow.isShowingFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val isVehicleSettingsActivityOpen by LauncherVehicleSettingsActivity.isOpenFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val isAboutOverlayShowing by LauncherAboutOverlayWindow.isShowingFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val isQuickSettingsOpen by LauncherAdasQuickSettingsWindow.isShowingFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)

    val isLauncherOverlayOpen = isTopShadeOpen ||
        isVehicleSettingsOpen ||
        isVehicleSettingsOverlayShowing ||
        isVehicleSettingsActivityOpen ||
        isAboutOverlayShowing ||
        isQuickSettingsOpen

    val isExternalAppOpen = LauncherCpuAffinity.isExternalAppForegroundForRenderThread(
        hiddenForExternalApp = hiddenForExternalApp,
        activeAppPackage = activeAppPackage,
        isActiveAppFullscreen = isActiveAppFullscreen,
        isWindowFocused = isWindowFocused,
        isLauncherOverlayOpen = isLauncherOverlayOpen,
    )

    val targetRenderPinned = LauncherCpuAffinity.shouldPinRenderThreadToLittleCores(
        mode = mode,
        isExternalAppOpen = isExternalAppOpen,
    )
    val targetArtPinned = LauncherCpuAffinity.shouldPinArtRuntimeToLittleCores(
        mode = mode,
        isExternalAppOpen = isExternalAppOpen,
    )

    var hasEverPinnedRender by remember { mutableStateOf(false) }
    LaunchedEffect(firstDrawComplete, targetRenderPinned) {
        if (!firstDrawComplete) return@LaunchedEffect
        if (targetRenderPinned) {
            hasEverPinnedRender = true
            LauncherCpuAffinity.applyRenderThreadAffinityAsync(pinToLittleCores = true)
        } else if (hasEverPinnedRender) {
            LauncherCpuAffinity.applyRenderThreadAffinityAsync(pinToLittleCores = false)
        }
    }

    var hasEverPinnedArt by remember { mutableStateOf(false) }
    LaunchedEffect(firstDrawComplete, targetArtPinned) {
        if (!firstDrawComplete) return@LaunchedEffect
        if (targetArtPinned) {
            hasEverPinnedArt = true
            LauncherCpuAffinity.applyArtRuntimeAffinityAsync(pinToLittleCores = true)
        } else if (hasEverPinnedArt) {
            LauncherCpuAffinity.applyArtRuntimeAffinityAsync(pinToLittleCores = false)
        }
    }
}

