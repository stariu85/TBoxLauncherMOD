package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import vad.dashing.tbox.MIRROR_FOLD_SWITCH_VALUE_FOLD
import vad.dashing.tbox.MirrorFoldLastCommandStore
import vad.dashing.tbox.ui.sendToggleMirrorFold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.ui.MyLifecycleOwner
import vad.dashing.tbox.ui.ProvideUiClickSounds
import vad.dashing.tbox.ui.sendOpenCloseTrunk
import kotlin.math.abs
import kotlin.math.roundToInt

internal object LauncherAdasQuickSettingsWindow {
    private const val TAG = "LauncherAdasQuickSetWin"

    @Volatile
    private var composeView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var windowManager: WindowManager? = null

    private val _isShowingFlow = MutableStateFlow(false)
    val isShowingFlow: StateFlow<Boolean> = _isShowingFlow.asStateFlow()

    fun isShowing(): Boolean = composeView != null

    private fun calculateAdasPanelBounds(context: Context): Rect {
        val activity = LauncherHomeActivityHolder.instance ?: (context as? LauncherHomeActivity)
            ?: Rect(0, 51, 344, 1000)
        val metrics = (activity as Context).resources.displayMetrics
        val density = metrics.density
        val screenH = metrics.heightPixels.coerceAtLeast(1080)

        val adasVisible = LauncherAppConfigStore.adasPanelVisible(context)
        val sidebarDp = LauncherAppConfigStore.sidebarWidthDp(context)
        val topBarDp = LauncherAppConfigStore.topBarHeightDp(context)
        val bottomBarDp = LauncherAppConfigStore.bottomBarHeightDp(context)

        val widthPx = if (adasVisible) (sidebarDp * density).toInt() else (344 * density).toInt()
        val topPx = (topBarDp * density).toInt().coerceIn(0, 100).takeIf { it > 0 } ?: 51
        val bottomPx = screenH - (bottomBarDp * density).toInt().coerceIn(0, 150)

        val top = LauncherEmbeddedBoundsState.topHeaderBottomPx.takeIf { it > 0 } ?: topPx
        val bottom = LauncherEmbeddedBoundsState.bottomBarTopPx.takeIf { it > 0 } ?: bottomPx
        val measuredLeftPanel = LauncherEmbeddedBoundsState.leftPanelBounds

        val width = if (measuredLeftPanel != null && measuredLeftPanel.width() > 200) {
            measuredLeftPanel.width()
        } else {
            widthPx
        }
        val height = (bottom - top).coerceAtLeast(400)

        return Rect(0, top, width, top + height)
    }

    fun toggle(
        context: Context,
        canViewModel: CanDataViewModel,
        tboxViewModel: TboxViewModel,
    ): Boolean {
        return if (isShowing()) {
            hide()
            true
        } else {
            show(context, canViewModel, tboxViewModel)
        }
    }

    fun show(
        context: Context,
        canViewModel: CanDataViewModel,
        tboxViewModel: TboxViewModel,
    ): Boolean {
        if (isShowing()) return true
        val activity = LauncherHomeActivityHolder.instance ?: (context as? LauncherHomeActivity)
            ?: return false
        if (activity.isFinishing || activity.isDestroyed) return false
        if (!Settings.canDrawOverlays(activity)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW is not granted")
            return false
        }

        val bounds = calculateAdasPanelBounds(activity)

        val owner = MyLifecycleOwner().also {
            it.setCurrentState(Lifecycle.State.CREATED)
            it.setCurrentState(Lifecycle.State.STARTED)
            it.setCurrentState(Lifecycle.State.RESUMED)
        }
        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            dimAmount = 0.35f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        val view = ComposeView(activity).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
                if (event.action == KeyEvent.ACTION_UP) {
                    hide()
                }
                true
            }
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(activity)
            setContent {
                val density = LocalDensity.current
                val windowHeightDp = with(density) { bounds.height().toDp() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = ::hide,
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(density) { bounds.left.toDp() },
                                y = with(density) { bounds.top.toDp() },
                            )
                            .size(
                                width = with(density) { bounds.width().toDp() },
                                height = with(density) { bounds.height().toDp() },
                            )
                            .background(LauncherColors.LeftPanelBg, RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            )
                            .padding(8.dp),
                    ) {
                        ProvideUiClickSounds {
                            tboxViewModel.currentTheme.collectAsStateWithLifecycle()
                            vad.dashing.tbox.ui.theme.TboxAppTheme(theme = 2) {
                                BackHandler {
                                    hide()
                                }
                                LauncherAdasQuickSettingsContent(
                                    canViewModel = canViewModel,
                                    windowHeightDp = windowHeightDp,
                                )
                            }
                        }
                    }
                }
            }
        }

        return runCatching {
            lifecycleOwner = owner
            windowManager = wm
            wm.addView(view, params)
            composeView = view
            _isShowingFlow.value = true
            view.requestFocus()
            LauncherOverlayBar.bringToFront()
            LauncherEdgeGestureOverlay.bringToFront()
            true
        }.onFailure {
            Log.e(TAG, "Unable to add adas quick settings overlay", it)
            _isShowingFlow.value = false
            lifecycleOwner = null
            windowManager = null
            owner.setCurrentState(Lifecycle.State.DESTROYED)
            owner.clear()
        }.getOrDefault(false)
    }

    fun hide() {
        val view = composeView ?: return
        composeView = null
        _isShowingFlow.value = false
        LauncherCar3DModelTrigger.notifyQuickSettingsClosed()
        runCatching { windowManager?.removeViewImmediate(view) }
            .onFailure { Log.w(TAG, "Unable to remove adas quick settings overlay", it) }
        windowManager = null
        lifecycleOwner?.let { owner ->
            owner.setCurrentState(Lifecycle.State.DESTROYED)
            owner.clear()
        }
        lifecycleOwner = null
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherAdasQuickSettingsContent(
    canViewModel: CanDataViewModel,
    windowHeightDp: Dp = 0.dp,
) {
    val context = LocalContext.current
    val adasLive by LauncherAdasRepository.state.collectAsStateWithLifecycle()
    val adas = LauncherDevVehicleState.adasStateOrNull() ?: adasLive
    val gearBoxMode by canViewModel.gearBoxMode.collectAsStateWithLifecycle()
    val gearBoxCurrentGear by canViewModel.gearBoxCurrentGear.collectAsStateWithLifecycle()
    val parsedGear = LauncherDevVehicleState.gearSlotOverride
        ?: resolveActiveGearSlot(gearBoxMode, gearBoxCurrentGear)
    val activeGear = parsedGear ?: 'P'

    val adasPanelVisibleRevision by LauncherAppConfigStore.adasPanelVisibleRevisionFlow.collectAsStateWithLifecycle()
    val adasPanelVisible = remember(context, adasPanelVisibleRevision) {
        LauncherAppConfigStore.adasPanelVisible(context)
    }

    val miniPlayerRevision by LauncherAppConfigStore.mediaMiniPlayerRevisionFlow.collectAsStateWithLifecycle()
    val miniPlayerVisible = remember(context, miniPlayerRevision) {
        LauncherAppConfigStore.mediaMiniPlayerVisible(context)
    }

    val driveModeBarRevision by LauncherAppConfigStore.driveModeBarRevisionFlow.collectAsStateWithLifecycle()
    val driveModeBarVisible = remember(context, driveModeBarRevision) {
        LauncherAppConfigStore.driveModeBarVisible(context)
    }

    val assistPanelRevision by LauncherAppConfigStore.assistPanelRevisionFlow.collectAsStateWithLifecycle()
    val assistPanelVisible = remember(context, assistPanelRevision) {
        LauncherAppConfigStore.assistPanelVisible(context)
    }

    val bodyControlsRevision by LauncherAppConfigStore.bodyControlsPanelRevisionFlow.collectAsStateWithLifecycle()
    val bodyControlsPanelVisible = remember(context, bodyControlsRevision) {
        LauncherAppConfigStore.bodyControlsPanelVisible(context)
    }

    val carColorRevision by LauncherAppConfigStore.carColorPanelRevisionFlow.collectAsStateWithLifecycle()
    val carColorPanelVisible = remember(context, carColorRevision) {
        LauncherAppConfigStore.carColorPanelVisible(context)
    }

    val navManeuverRevision by LauncherAppConfigStore.navManeuverRevisionFlow.collectAsStateWithLifecycle()
    val navManeuverVisible = remember(context, navManeuverRevision) {
        LauncherAppConfigStore.navManeuverVisible(context)
    }
    val navStreetRevision by LauncherAppConfigStore.navStreetRevisionFlow.collectAsStateWithLifecycle()
    val navStreetVisible = remember(context, navStreetRevision) {
        LauncherAppConfigStore.navStreetVisible(context)
    }
    val navSpeedRevision by LauncherAppConfigStore.navSpeedRevisionFlow.collectAsStateWithLifecycle()
    val navSpeedVisible = remember(context, navSpeedRevision) {
        LauncherAppConfigStore.navSpeedVisible(context)
    }
    val navTrafficLightRevision by LauncherAppConfigStore.navTrafficLightRevisionFlow.collectAsStateWithLifecycle()
    val navTrafficLightVisible = remember(context, navTrafficLightRevision) {
        LauncherAppConfigStore.navTrafficLightVisible(context)
    }

    val accPresetsInQuickSettingsRevision by LauncherAppConfigStore.accPresetsInQuickSettingsRevisionFlow.collectAsStateWithLifecycle()
    val accPresetsInQuickSettingsVisible = remember(context, accPresetsInQuickSettingsRevision) {
        LauncherAppConfigStore.accPresetsInQuickSettingsVisible(context)
    }

    val adasPanelsRevision by LauncherAppConfigStore.adasPanelsRevisionFlow.collectAsStateWithLifecycle()
    val adasTopPanels = remember(context, adasPanelsRevision) {
        LauncherAppConfigStore.adasTopPanels(context)
    }
    val adasBottomPanels = remember(context, adasPanelsRevision) {
        LauncherAppConfigStore.adasBottomPanels(context)
    }

    val activeSubPanelsRevision by LauncherAppConfigStore.activeSubPanelsRevisionFlow.collectAsStateWithLifecycle()

    val primarySlots = remember(
        adasPanelVisible,
        miniPlayerVisible,
        driveModeBarVisible,
        assistPanelVisible,
        bodyControlsPanelVisible,
        carColorPanelVisible,
        navManeuverVisible,
        navStreetVisible,
        navSpeedVisible,
        navTrafficLightVisible,
        adasTopPanels,
        adasBottomPanels,
    ) {
        if (!adasPanelVisible) {
            emptySet<String>()
        } else {
            val isPanelVisible: (String) -> Boolean = { panelId ->
                when (panelId) {
                    "drive_mode" -> driveModeBarVisible
                    "assist", "presets" -> assistPanelVisible
                    "body_controls" -> bodyControlsPanelVisible
                    "media_player" -> miniPlayerVisible
                    "car_color" -> carColorPanelVisible
                    "lanes" -> true
                    else -> true
                }
            }
            val top = adasTopPanels.filter { isPanelVisible(it) }
            val bottom = adasBottomPanels.filter { isPanelVisible(it) && it !in top }
            (top + bottom).toSet()
        }
    }

    val directlyVisiblePanels = remember(
        primarySlots,
        activeSubPanelsRevision,
    ) {
        val visibleSubPanels = mutableSetOf<String>()
        for (slot in primarySlots) {
            val sub = LauncherAppConfigStore.getVisibleSubPanel(slot)
            visibleSubPanels.add(sub)
        }
        visibleSubPanels
    }

    val availableItems = remember(directlyVisiblePanels, carColorPanelVisible, accPresetsInQuickSettingsVisible) {
        val allCandidates = listOf(
            "media_player",
            "drive_mode",
            "drive_mode_offroad",
            "body_controls_trunk",
            "body_controls_seats",
            "body_controls_seat_memory",
            "assist",
            "presets",
            "smart_control",
            "lanes",
            "radars",
            "chassis",
            "lights",
            "wipers",
            "car_color",
            "yandex_nav",
        )
        allCandidates.filter { candidate ->
            if (candidate == "smart_panel") return@filter false
            if (candidate == "car_color" && !carColorPanelVisible) return@filter false
            if (candidate == "presets" && !accPresetsInQuickSettingsVisible) return@filter false
            candidate !in directlyVisiblePanels
        }
    }

    val quickSettingsRevision by LauncherAppConfigStore.quickSettingsPanelsRevisionFlow.collectAsStateWithLifecycle()
    val savedQuickTop = remember(context, quickSettingsRevision) {
        LauncherAppConfigStore.quickSettingsTopPanels(context)
    }
    val savedQuickBottom = remember(context, quickSettingsRevision) {
        LauncherAppConfigStore.quickSettingsBottomPanels(context)
    }

    val (quickTopPanels, quickBottomPanels) = remember(availableItems, savedQuickTop, savedQuickBottom) {
        fun familyOf(id: String): String = when (id) {
            "body_controls_trunk", "body_controls_seats", "body_controls_seat_memory" -> "body_controls"
            "drive_mode", "drive_mode_offroad" -> "drive_mode"
            "presets" -> "assist"
            else -> id
        }

        val unplaced = availableItems.toMutableList()
        val top = mutableListOf<String>()
        val bottom = mutableListOf<String>()

        for (savedId in savedQuickTop) {
            if (savedId in unplaced) {
                top.add(savedId)
                unplaced.remove(savedId)
            } else {
                val savedFamily = familyOf(savedId)
                val sibling = unplaced.firstOrNull { familyOf(it) == savedFamily }
                if (sibling != null) {
                    top.add(sibling)
                    unplaced.remove(sibling)
                }
            }
        }

        for (savedId in savedQuickBottom) {
            if (savedId in unplaced) {
                bottom.add(savedId)
                unplaced.remove(savedId)
            } else {
                val savedFamily = familyOf(savedId)
                val sibling = unplaced.firstOrNull { familyOf(it) == savedFamily }
                if (sibling != null) {
                    bottom.add(sibling)
                    unplaced.remove(sibling)
                }
            }
        }

        bottom.addAll(unplaced)

        Pair(top, bottom)
    }

    var draggingItemId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var initialPanelTopPx by remember { mutableFloatStateOf(0f) }
    var panelHeightPx by remember { mutableFloatStateOf(0f) }
    var activeGreenPlusItemId by remember { mutableStateOf<String?>(null) }
    var quickContainerHeightPx by remember { mutableFloatStateOf(0f) }
    var containerWindowTopPx by remember { mutableFloatStateOf(0f) }

    fun addPanelToAdas(targetId: String) {
        fun canonicalPanelId(id: String): String = when (id) {
            "body_controls_trunk", "body_controls_seats", "body_controls_seat_memory" -> "body_controls"
            "drive_mode_offroad" -> "drive_mode"
            "presets" -> "assist"
            else -> id
        }

        val basePanelId = canonicalPanelId(targetId)

        val rawTop = LauncherAppConfigStore.adasTopPanels(context).map { canonicalPanelId(it) }.filter { it != basePanelId }
        val rawBottom = LauncherAppConfigStore.adasBottomPanels(context).map { canonicalPanelId(it) }.filter { it != basePanelId }

        val activePanelsOnScreen = (rawTop + rawBottom).distinct()
        val activeCount = activePanelsOnScreen.size

        val hasMediaPlayer = "media_player" in activePanelsOnScreen || basePanelId == "media_player"
        val maxAllowedPanels = if (hasMediaPlayer) 4 else 5

        if (activeCount >= maxAllowedPanels) {
            Toast.makeText(
                context,
                "Достигнуто максимальное количесвто панелей ADAS. Удалите панель чтобы добавить новую",
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        // 1. Ensure ADAS panel sidebar is visible
        LauncherAppConfigStore.setAdasPanelVisible(context, true)

        // 2. Ensure individual toggle is enabled
        when (basePanelId) {
            "media_player" -> LauncherAppConfigStore.setMediaMiniPlayerVisible(context, true)
            "drive_mode" -> LauncherAppConfigStore.setDriveModeBarVisible(context, true)
            "body_controls" -> LauncherAppConfigStore.setBodyControlsPanelVisible(context, true)
            "assist" -> LauncherAppConfigStore.setAssistPanelVisible(context, true)
            "car_color" -> LauncherAppConfigStore.setCarColorPanelVisible(context, true)
            "yandex_nav" -> LauncherAppConfigStore.setYandexNavWidgetVisible(context, true)
        }

        // 3. Always add to bottom ADAS panels at index 0 and persist layout
        val currentTop = LauncherAppConfigStore.adasTopPanels(context).filter { canonicalPanelId(it) != basePanelId }.toMutableList()
        val currentBottom = LauncherAppConfigStore.adasBottomPanels(context).filter { canonicalPanelId(it) != basePanelId }.toMutableList()

        currentBottom.add(0, basePanelId)
        LauncherAppConfigStore.setAdasPanelsLayout(context, currentTop, currentBottom)

        Toast.makeText(context, "Панель добавлена в зону ADAS", Toast.LENGTH_SHORT).show()
    }

    val mediaHeightScaleRevision by LauncherAppConfigStore.mediaCardHeightScaleRevisionFlow.collectAsStateWithLifecycle()
    val mediaHeightScale = remember(context, mediaHeightScaleRevision) {
        LauncherAppConfigStore.mediaCardHeightScale(context)
    }
    val mediaPlayerHeightDp = remember(mediaHeightScale) {
        val expandedVerticalPadding = (12.dp * mediaHeightScale).coerceAtLeast(2.dp)
        val expandedItemSpacing = (10.dp * ((mediaHeightScale - 0.5f) / 0.5f)).coerceAtLeast(0.dp)
        expandedVerticalPadding * 2 + 52.dp + expandedItemSpacing + 50.6.dp
    }

    val defaultScale = LauncherDevScaleState.scale
    val totalTopHeight = quickTopPanels.sumOf { if (it == "media_player") mediaPlayerHeightDp.value.toDouble() else 60.0 }.toFloat()
    val totalBottomHeight = quickBottomPanels.sumOf { if (it == "media_player") mediaPlayerHeightDp.value.toDouble() else 60.0 }.toFloat()
    val totalTopSpacing = ((quickTopPanels.size - 1).coerceAtLeast(0)) * 10f
    val totalBottomSpacing = ((quickBottomPanels.size - 1).coerceAtLeast(0)) * 10f
    val gapBetweenSections = if (quickTopPanels.isNotEmpty() && quickBottomPanels.isNotEmpty()) 10f else 0f
    val totalPaddingDp = 16f

    val totalRequiredDp = totalTopHeight + totalBottomHeight + totalTopSpacing + totalBottomSpacing + gapBetweenSections + totalPaddingDp

    val baseDensity = LocalDensity.current
    val effectiveWindowHeightDp = if (windowHeightDp.value > 0f) {
        windowHeightDp.value
    } else if (quickContainerHeightPx > 0f) {
        with(baseDensity) { quickContainerHeightPx.toDp().value }
    } else {
        600f
    }

    val requiredWithDefaultScale = totalRequiredDp * defaultScale
    val effectiveScale = if (totalRequiredDp > 0f && requiredWithDefaultScale > effectiveWindowHeightDp) {
        (effectiveWindowHeightDp / totalRequiredDp).coerceIn(0.45f, defaultScale)
    } else {
        defaultScale
    }

    LauncherCustomScaleProvider(scale = effectiveScale) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    quickContainerHeightPx = coordinates.size.height.toFloat()
                    containerWindowTopPx = coordinates.positionInWindow().y
                },
        ) {
            if (quickTopPanels.isEmpty() && quickBottomPanels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Все панели отображаются в зоне ADAS",
                        color = LauncherColors.LeftTextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                // TOP SECTION (snapped to top of window)
                if (quickTopPanels.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(4.dp)
                            .zIndex(200f),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            quickTopPanels.forEach { id ->
                                RenderQuickPanelCard(
                                    id = id,
                                    activeGear = activeGear,
                                    adas = adas,
                                    canViewModel = canViewModel,
                                    primarySlots = primarySlots,
                                    draggingItemId = draggingItemId,
                                    dragOffsetY = dragOffsetY,
                                    activeGreenPlusItemId = activeGreenPlusItemId,
                                    mediaPlayerHeightDp = mediaPlayerHeightDp,
                                    containerWindowTopPx = containerWindowTopPx,
                                    onDragStart = { item, topPx, hPx ->
                                        draggingItemId = item
                                        activeGreenPlusItemId = item
                                        initialPanelTopPx = topPx
                                        panelHeightPx = hPx
                                    },
                                    onDragMove = { deltaY ->
                                        dragOffsetY = deltaY
                                    },
                                    onDragEnd = { accumulativeY ->
                                        val releasedCenterY = initialPanelTopPx + panelHeightPx / 2f + accumulativeY
                                        val newLayout = calculateNewPanelLayout(
                                            draggedPanelId = id,
                                            releasedCenterY = releasedCenterY,
                                            containerHeightPx = quickContainerHeightPx,
                                            currentTop = quickTopPanels,
                                            currentBottom = quickBottomPanels,
                                        )
                                        draggingItemId = null
                                        dragOffsetY = 0f
                                        LauncherAppConfigStore.setQuickSettingsPanelsLayout(context, newLayout.first, newLayout.second)
                                    },
                                    onAddClick = { addPanelToAdas(it); activeGreenPlusItemId = null },
                                )
                            }
                        }
                    }
                }

                // BOTTOM SECTION (snapped to bottom of window)
                if (quickBottomPanels.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(4.dp)
                            .zIndex(200f),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            quickBottomPanels.forEach { id ->
                                RenderQuickPanelCard(
                                    id = id,
                                    activeGear = activeGear,
                                    adas = adas,
                                    canViewModel = canViewModel,
                                    primarySlots = primarySlots,
                                    draggingItemId = draggingItemId,
                                    dragOffsetY = dragOffsetY,
                                    activeGreenPlusItemId = activeGreenPlusItemId,
                                    mediaPlayerHeightDp = mediaPlayerHeightDp,
                                    containerWindowTopPx = containerWindowTopPx,
                                    onDragStart = { item, topPx, hPx ->
                                        draggingItemId = item
                                        activeGreenPlusItemId = item
                                        initialPanelTopPx = topPx
                                        panelHeightPx = hPx
                                    },
                                    onDragMove = { deltaY ->
                                        dragOffsetY = deltaY
                                    },
                                    onDragEnd = { accumulativeY ->
                                        val releasedCenterY = initialPanelTopPx + panelHeightPx / 2f + accumulativeY
                                        val newLayout = calculateNewPanelLayout(
                                            draggedPanelId = id,
                                            releasedCenterY = releasedCenterY,
                                            containerHeightPx = quickContainerHeightPx,
                                            currentTop = quickTopPanels,
                                            currentBottom = quickBottomPanels,
                                        )
                                        draggingItemId = null
                                        dragOffsetY = 0f
                                        LauncherAppConfigStore.setQuickSettingsPanelsLayout(context, newLayout.first, newLayout.second)
                                    },
                                    onAddClick = { addPanelToAdas(it); activeGreenPlusItemId = null },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RenderQuickPanelCard(
    id: String,
    activeGear: Char,
    adas: LauncherAdasState,
    canViewModel: CanDataViewModel,
    primarySlots: Set<String>,
    draggingItemId: String?,
    dragOffsetY: Float,
    activeGreenPlusItemId: String?,
    mediaPlayerHeightDp: Dp,
    containerWindowTopPx: Float,
    onDragStart: (id: String, initialTopPx: Float, panelHeightPx: Float) -> Unit,
    onDragMove: (deltaY: Float) -> Unit,
    onDragEnd: (accumulativeY: Float) -> Unit,
    onAddClick: (id: String) -> Unit,
) {
    val basePanelId = when (id) {
        "body_controls_trunk", "body_controls_seats", "body_controls_seat_memory" -> "body_controls"
        "presets", "lanes" -> "assist"
        "drive_mode_offroad" -> "drive_mode"
        else -> id
    }
    val isBasePanelAlreadyInAdas = basePanelId in primarySlots

    val isDragging = draggingItemId == id
    val showPlus = (isDragging || activeGreenPlusItemId == id) && !isBasePanelAlreadyInAdas
    val slotHeight = if (id == "media_player") mediaPlayerHeightDp else 60.dp

    var localTopPx by remember { mutableFloatStateOf(0f) }
    var localHeightPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(slotHeight)
            .zIndex(if (isDragging) 300f else 1f)
            .onGloballyPositioned { coordinates ->
                if (!isDragging) {
                    val cardWindowTopPx = coordinates.positionInWindow().y
                    localTopPx = cardWindowTopPx - containerWindowTopPx
                    localHeightPx = coordinates.size.height.toFloat()
                }
            }
            .graphicsLayer {
                translationY = if (isDragging) dragOffsetY else 0f
                scaleX = if (isDragging) 1.03f else 1f
                scaleY = if (isDragging) 1.03f else 1f
            }
            .then(
                if (draggingItemId != null) {
                    Modifier.border(2.dp, Color(0xFF06B6D4).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                } else {
                    Modifier
                }
            )
            .then(
                if (isDragging) {
                    Modifier
                        .shadow(12.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .border(2.dp, Color(0xFF22C55E), RoundedCornerShape(10.dp))
                } else {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                }
            )
            .background(LauncherColors.LeftPanelCard)
            .pointerInput(id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = SystemClock.uptimeMillis()
                    var dragActive = false
                    var accumulativeY = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (!pointer.pressed) {
                            if (dragActive) {
                                pointer.consume()
                                onDragEnd(accumulativeY)
                            }
                            break
                        }

                        val elapsed = SystemClock.uptimeMillis() - downTime
                        if (!dragActive) {
                            val totalDeltaY = abs(pointer.position.y - down.position.y)
                            val totalDeltaX = abs(pointer.position.x - down.position.x)
                            if (totalDeltaY > 12f && totalDeltaY > totalDeltaX * 1.2f) {
                                dragActive = true
                                onDragStart(id, localTopPx, localHeightPx)
                                accumulativeY = pointer.position.y - down.position.y
                                onDragMove(accumulativeY)
                                pointer.consume()
                            } else if (elapsed >= 1000L) {
                                dragActive = true
                                onDragStart(id, localTopPx, localHeightPx)
                                accumulativeY = 0f
                                onDragMove(accumulativeY)
                                pointer.consume()
                            }
                        } else {
                            val frameDelta = pointer.position.y - pointer.previousPosition.y
                            accumulativeY += frameDelta
                            onDragMove(accumulativeY)
                            pointer.consume()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (id) {
            "media_player" -> LauncherMediaMiniPlayer(
                modifier = Modifier.fillMaxWidth(),
                sourceId = "launcher_mini_player_quick",
            )
            "drive_mode" -> LauncherDriveModeBar(canViewModel = canViewModel, initialPage = 0, reportSubPanel = false, onModeSelected = {}, modifier = Modifier.fillMaxWidth())
            "drive_mode_offroad" -> LauncherDriveModeBar(canViewModel = canViewModel, initialPage = 1, reportSubPanel = false, onModeSelected = {}, modifier = Modifier.fillMaxWidth())
            "body_controls_trunk" -> LauncherTrunkAndWindowsBar(activeGear = activeGear, onShowTrunkParkWarning = {}, modifier = Modifier.fillMaxWidth())
            "body_controls_seats" -> LauncherSeatMemoryAndAdjustmentBar(modifier = Modifier.fillMaxWidth())
            "body_controls_seat_memory" -> LauncherSeatMemoryPresetsBar(modifier = Modifier.fillMaxWidth())
            "assist" -> LauncherAssistControlBar(canViewModel = canViewModel, adas = adas, modifier = Modifier.fillMaxWidth())
            "presets" -> LauncherCruisePresetControl(canViewModel = canViewModel, adas = adas, modifier = Modifier.fillMaxWidth())
            "smart_control" -> LauncherSmartAccAndDistControl(canViewModel = canViewModel, adas = adas, modifier = Modifier.fillMaxWidth())
            "lanes" -> LauncherLanesControlBar(adas = adas, modifier = Modifier.fillMaxWidth())
            "radars" -> LauncherRadarControlBar(adas = adas, modifier = Modifier.fillMaxWidth())
            "chassis" -> LauncherChassisControlBar(adas = adas, modifier = Modifier.fillMaxWidth())
            "lights" -> LauncherLightsControlBar(adas = adas, modifier = Modifier.fillMaxWidth())
            "wipers" -> LauncherWiperControlBar(modifier = Modifier.fillMaxWidth())
            "car_color" -> LauncherCarColorPickerBar(modifier = Modifier.fillMaxWidth(), heightDp = 60.dp)
            "yandex_nav" -> LauncherYandexNavWidget(modifier = Modifier.fillMaxWidth())
        }

        if (showPlus) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .zIndex(300f)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E))
                    .clickable {
                        onAddClick(id)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherTrunkAndWindowsBar(
    activeGear: Char?,
    onShowTrunkParkWarning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var windowJob by remember { mutableStateOf<Job?>(null) }
    var isOperatingWindows by remember { mutableStateOf(false) }

    fun triggerWindowsCommand(targetPercent: Int) {
        windowJob?.cancel()
        windowJob = scope.launch {
            isOperatingWindows = true
            try {
                sendSequentialWindowCommands(targetPercent)
            } finally {
                isOperatingWindows = false
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Trunk Button
        Box(
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(LauncherColors.LeftPanelCard)
                .combinedClickable(
                    onClick = {
                        if (activeGear != 'P') {
                            onShowTrunkParkWarning()
                        }
                    },
                    onLongClick = {
                        if (activeGear == 'P') {
                            sendOpenCloseTrunk(context)
                        } else {
                            onShowTrunkParkWarning()
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_trunk),
                contentDescription = "Багажник",
                modifier = Modifier.size(40.dp),
                colorFilter = ColorFilter.tint(LauncherColors.LeftTextPrimary),
            )
        }

        // Windows Button
        val activeGreen = Color(0xFF10B981)
        val windowsBg = if (isOperatingWindows) activeGreen.copy(alpha = 0.25f) else LauncherColors.LeftPanelCard
        val windowsBorder = if (isOperatingWindows) activeGreen else Color.Transparent

        Box(
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(windowsBg)
                .then(
                    if (isOperatingWindows) {
                        Modifier.border(1.dp, windowsBorder, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    }
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { triggerWindowsCommand(0) },
                        onDoubleTap = { triggerWindowsCommand(100) },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_widget_side_window),
                contentDescription = "Окна",
                modifier = Modifier.size(40.dp),
                colorFilter = ColorFilter.tint(
                    if (isOperatingWindows) activeGreen else LauncherColors.LeftTextPrimary,
                ),
            )
        }

        // Mirror Folding Button
        val mirrorFoldVal = MirrorFoldLastCommandStore.readLastValue()
        val isMirrorFolded = mirrorFoldVal == MIRROR_FOLD_SWITCH_VALUE_FOLD
        val mirrorBg = if (isMirrorFolded) activeGreen.copy(alpha = 0.25f) else LauncherColors.LeftPanelCard
        val mirrorBorder = if (isMirrorFolded) activeGreen else Color.Transparent

        Box(
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(mirrorBg)
                .then(
                    if (isMirrorFolded) {
                        Modifier.border(1.dp, mirrorBorder, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    }
                )
                .clickable {
                    sendToggleMirrorFold(context)
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_mirror),
                contentDescription = "Складывание зеркал",
                modifier = Modifier.size(36.dp),
                colorFilter = ColorFilter.tint(
                    if (isMirrorFolded) activeGreen else LauncherColors.LeftTextPrimary,
                ),
            )
        }
    }
}

@Composable
private fun LauncherSeatMemoryAndAdjustmentBar(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Combined Button (Weight 2f): Sliders + Driver Seat + Mirror -> Opens Mirror Adjustment Settings
        Box(
            modifier = Modifier
                .weight(2f)
                .height(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(LauncherColors.LeftPanelCard)
                .clickable { launchMirrorAdjustSettingsEmbedded(context) },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_settings_sliders),
                    contentDescription = "Настройки",
                    modifier = Modifier.size(28.dp),
                    colorFilter = ColorFilter.tint(LauncherColors.LeftTextPrimary),
                )
                Image(
                    painter = painterResource(R.drawable.ic_seat_memory_btn),
                    contentDescription = "Регулировка водительского сиденья",
                    modifier = Modifier.size(30.dp),
                    colorFilter = ColorFilter.tint(LauncherColors.LeftTextPrimary),
                )
                Image(
                    painter = painterResource(R.drawable.ic_rearview_mirror),
                    contentDescription = "Регулировка зеркал",
                    modifier = Modifier.size(36.dp),
                    colorFilter = ColorFilter.tint(LauncherColors.LeftTextPrimary),
                )
            }
        }

        // Passenger Seat Adjustment Button (Weight 1f)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(LauncherColors.LeftPanelCard)
                .clickable { PassengerSeatOverlayWindow.show(context) },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_seat_memory_btn),
                contentDescription = "Регулировка пассажирского сиденья",
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer { scaleX = -1f },
                colorFilter = ColorFilter.tint(LauncherColors.LeftTextPrimary),
            )
        }
    }
}

private suspend fun sendSequentialWindowCommands(percent: Int) {
    val target = percent.coerceIn(0, 100)
    val activeTargets = mutableMapOf<Int, Int>()
    val windowIndices = intArrayOf(0, 1, 2, 3)
    for (i in windowIndices.indices) {
        if (i > 0) {
            delay(1000L)
        }
        val windowIdx = windowIndices[i]
        activeTargets[windowIdx] = target
        MbCanEngineFacade.setWindowPositions(activeTargets)
    }
    delay(2500L)
}

@Composable
private fun LauncherCustomScaleProvider(
    scale: Float,
    content: @Composable () -> Unit,
) {
    val base = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
            density = base.density * scale,
            fontScale = base.fontScale * scale,
        ),
    ) {
        content()
    }
}
