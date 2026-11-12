package vad.dashing.tbox.ui.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.DclLogRecorder
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.MbCanSeatModeState
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.tbm.TbmConnectionState
import vad.dashing.tbox.tbm.TbmRepositoryProvider
import vad.dashing.tbox.ui.LauncherAppIconLoader
import vad.dashing.tbox.ui.launchAppFromWidget
import androidx.compose.ui.graphics.lerp
import vad.dashing.tbox.ui.HvacTempZone
import vad.dashing.tbox.ui.LIGHT_CONTROL_AUTO
import vad.dashing.tbox.ui.LIGHT_CONTROL_LOW_BEAM
import vad.dashing.tbox.ui.LIGHT_CONTROL_OFF
import vad.dashing.tbox.ui.LIGHT_CONTROL_POSITION
import vad.dashing.tbox.ui.sendAdjustHvacTemperature
import vad.dashing.tbox.ui.sendCycleDriverUnlockMode
import vad.dashing.tbox.ui.sendCycleFcw
import vad.dashing.tbox.ui.sendCycleFrontSeatHeat
import vad.dashing.tbox.ui.sendCycleFrontSeatVent
import vad.dashing.tbox.ui.sendCycleHeadlightsSwitch
import vad.dashing.tbox.ui.sendCycleHomelightDelay
import vad.dashing.tbox.ui.sendCycleHvacFanDirection
import vad.dashing.tbox.ui.sendCycleHvacFanSpeed
import vad.dashing.tbox.ui.sendCycleLasSensitivity
import vad.dashing.tbox.ui.sendOpenCloseTrunk
import vad.dashing.tbox.ui.sendSetMbCanProperty
import vad.dashing.tbox.ui.sendToggleAccAutobrake
import vad.dashing.tbox.ui.sendToggleAvh
import vad.dashing.tbox.ui.sendToggleDoorAutoLock
import vad.dashing.tbox.ui.sendToggleDoorIgnOffUnlock
import vad.dashing.tbox.ui.sendToggleEscOff
import vad.dashing.tbox.ui.sendToggleFrontWindscreenHeat
import vad.dashing.tbox.ui.sendToggleHdc
import vad.dashing.tbox.ui.sendToggleHvacAc
import vad.dashing.tbox.ui.sendToggleHvacAirRecirculation
import vad.dashing.tbox.ui.sendToggleHvacAuto
import vad.dashing.tbox.ui.sendToggleHvacDefrosterFront
import vad.dashing.tbox.ui.sendToggleHvacPm25
import vad.dashing.tbox.ui.sendToggleHvacPower
import vad.dashing.tbox.ui.sendToggleHvacSync
import vad.dashing.tbox.ui.sendToggleMbCanProperty
import vad.dashing.tbox.ui.sendToggleMirrorAutofold
import vad.dashing.tbox.ui.sendToggleMirrorFold
import vad.dashing.tbox.ui.sendToggleMirrorReverseTurn
import vad.dashing.tbox.ui.sendToggleParkingRadar
import vad.dashing.tbox.ui.sendToggleRearWindowMirrorsDefrost
import vad.dashing.tbox.ui.sendToggleSteeringWheelHeat
import vad.dashing.tbox.ui.sendToggleWiperMaintenance
import vad.dashing.tbox.ui.sendToggleRearFogLight
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.MirrorFoldLastCommandStore
import vad.dashing.tbox.MIRROR_FOLD_SWITCH_VALUE_FOLD
import vad.dashing.tbox.resolveDriveModeDisplayLabel
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val isOperatingWindowsFlow = MutableStateFlow(false)
private val activeSeatMemorySlotFlow = MutableStateFlow<Int?>(null)

private fun recallSeatMemorySlot(context: Context, slot: Int) {
    activeSeatMemorySlotFlow.value = slot
    Toast.makeText(context, "Применение памяти: Слот $slot", Toast.LENGTH_SHORT).show()
    CoroutineScope(Dispatchers.IO).launch {
        UniversalCanRepository.execute(
            MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.MIRROR_SET_DRIVER_MIRROR_LOCATION, slot)
        )
        UniversalCanRepository.execute(
            MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.SEAT_POSITION_CALLOUTREQ, slot)
        )
        delay(1000L)
        UniversalCanRepository.execute(
            MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.MIRROR_SET_DRIVER_MIRROR_LOCATION, 0)
        )
        UniversalCanRepository.execute(
            MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.SEAT_POSITION_CALLOUTREQ, 0)
        )
        delay(1500L)
        activeSeatMemorySlotFlow.value = null
    }
}

private fun triggerSequentialWindows(targetPercent: Int) {
    CoroutineScope(Dispatchers.IO).launch {
        isOperatingWindowsFlow.value = true
        try {
            sendSequentialWindowCommands(targetPercent)
        } finally {
            isOperatingWindowsFlow.value = false
        }
    }
}

private val IconResizeWidth: ImageVector by lazy {
    ImageVector.Builder(
        name = "ResizeWidth",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(6f, 12f)
            lineTo(10f, 8f)
            lineTo(10f, 11f)
            lineTo(14f, 11f)
            lineTo(14f, 8f)
            lineTo(18f, 12f)
            lineTo(14f, 16f)
            lineTo(14f, 13f)
            lineTo(10f, 13f)
            lineTo(10f, 16f)
            close()
        }
    }.build()
}

private val IconResizeHeight: ImageVector by lazy {
    ImageVector.Builder(
        name = "ResizeHeight",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 6f)
            lineTo(16f, 10f)
            lineTo(13f, 10f)
            lineTo(13f, 14f)
            lineTo(16f, 14f)
            lineTo(12f, 18f)
            lineTo(8f, 14f)
            lineTo(11f, 14f)
            lineTo(8f, 10f)
            close()
        }
    }.build()
}

private val IconBrightnessSun: ImageVector by lazy {
    ImageVector.Builder(
        name = "BrightnessSun",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 7f)
            curveTo(9.24f, 7f, 7f, 9.24f, 7f, 12f)
            curveTo(7f, 14.76f, 9.24f, 17f, 17f, 12f)
            curveTo(17f, 9.24f, 14.76f, 7f, 12f, 7f)
            close()
            moveTo(12f, 2f)
            lineTo(12f, 5f)
            moveTo(12f, 19f)
            lineTo(12f, 22f)
            moveTo(2f, 12f)
            lineTo(5f, 12f)
            moveTo(19f, 12f)
            lineTo(22f, 12f)
        }
    }.build()
}

private val IconVolumeSpeaker: ImageVector by lazy {
    ImageVector.Builder(
        name = "VolumeSpeaker",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(3f, 9f)
            lineTo(3f, 15f)
            lineTo(7f, 15f)
            lineTo(12f, 19f)
            lineTo(12f, 5f)
            lineTo(7f, 9f)
            close()
            moveTo(16.5f, 12f)
            curveTo(16.5f, 10.23f, 15.48f, 8.71f, 14f, 7.97f)
            lineTo(14f, 16.02f)
            curveTo(15.48f, 15.29f, 16.5f, 13.77f, 16.5f, 12f)
            close()
        }
    }.build()
}

private val IconPhoneVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "PhoneVector",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(6.62f, 10.79f)
            curveTo(8.06f, 13.62f, 10.38f, 15.94f, 13.21f, 17.38f)
            lineTo(15.41f, 15.18f)
            curveTo(15.69f, 14.9f, 16.08f, 14.82f, 16.43f, 14.93f)
            curveTo(17.55f, 15.3f, 18.75f, 15.5f, 20f, 15.5f)
            curveTo(20.55f, 15.5f, 21f, 15.95f, 21f, 16.5f)
            lineTo(21f, 20f)
            curveTo(21f, 20.55f, 20.55f, 21f, 20f, 21f)
            curveTo(10.61f, 21f, 3f, 13.39f, 3f, 4f)
            curveTo(3f, 3.45f, 3.45f, 3f, 4f, 3f)
            lineTo(7.5f, 3f)
            curveTo(8.05f, 3f, 8.5f, 3.45f, 8.5f, 4f)
            curveTo(8.5f, 5.25f, 8.7f, 6.45f, 9.07f, 7.57f)
            curveTo(9.18f, 7.92f, 9.1f, 8.31f, 8.82f, 8.59f)
            lineTo(6.62f, 10.79f)
            close()
        }
    }.build()
}

private val IconNaviVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "NaviVector",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 2f)
            lineTo(4.5f, 20.29f)
            lineTo(5.21f, 21f)
            lineTo(12f, 18f)
            lineTo(18.79f, 21f)
            lineTo(19.5f, 20.29f)
            close()
        }
    }.build()
}

private val IconMicVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "MicVector",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 14f)
            curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f)
            lineTo(15f, 5f)
            curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
            curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
            lineTo(9f, 11f)
            curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
            close()
            moveTo(17f, 11f)
            curveTo(17f, 13.76f, 14.76f, 16f, 12f, 16f)
            curveTo(9.24f, 16f, 7f, 13.76f, 7f, 11f)
            lineTo(5f, 11f)
            curveTo(5f, 14.53f, 7.61f, 17.43f, 11f, 17.9f)
            lineTo(11f, 21f)
            lineTo(13f, 21f)
            lineTo(13f, 17.9f)
            curveTo(16.39f, 17.43f, 19f, 14.53f, 19f, 11f)
            lineTo(17f, 11f)
            close()
        }
    }.build()
}

@Composable
internal fun LauncherTopShadeContent(
    onClose: () -> Unit,
    onHandleDrag: (Float) -> Unit = {},
    onHandleDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = LauncherThemeState.darkTheme
    val revision by LauncherTopShadeConfigStore.tilesRevisionFlow.collectAsStateWithLifecycle()
    val tiles = remember(context, revision) {
        LauncherTopShadeConfigStore.getTiles(context).toMutableList()
    }
    var isEditMode by remember { mutableStateOf(false) }
    var showAddModal by remember { mutableStateOf(false) }

    // Start 500ms polling for OpenOS A9 volume channels & CAN headrest mode
    DisposableEffect(context) {
        PlatformAudioRepository.startObserving(context)
        onDispose {
            PlatformAudioRepository.stopObserving()
        }
    }

    // Theme adaptive colors
    val shadeBgColor = if (isDark) Color(0xFF0F1115).copy(alpha = 0.80f) else Color(0xFFF3F4F6).copy(alpha = 0.80f)
    val textPrimaryColor = if (isDark) Color.White else Color(0xFF111827)
    val textSecondaryColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF4B5563)
    val guideBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f)
    val guideBgColor = if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.04f)
    val handleColor = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.35f)

    // Time ticker
    var currentTimeStr by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm · EEE, d MMM", Locale("ru"))
        while (true) {
            currentTimeStr = sdf.format(Date())
            delay(1000L)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(shadeBgColor)
                .padding(16.dp),
        ) {
            // --- TOP HEADER BAR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Панель управления",
                        color = textPrimaryColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (isEditMode) "Перетаскивайте плитки по сетке ячеек (12x12)" else currentTimeStr,
                        color = if (isEditMode) LauncherColors.AccentBlue else textSecondaryColor,
                        fontSize = 13.sp,
                        fontWeight = if (isEditMode) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(visible = isEditMode) {
                        TextButton(
                            onClick = { showAddModal = true },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .background(LauncherColors.AccentBlue, CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Добавить элемент", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    val editBtnBg = if (isEditMode) {
                        LauncherColors.AccentBlue
                    } else {
                        if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
                    }
                    val editBtnIconTint = if (isEditMode) Color.White else textPrimaryColor

                    IconButton(
                        onClick = { isEditMode = !isEditMode },
                        modifier = Modifier
                            .size(38.dp)
                            .background(editBtnBg, CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Tiles",
                            tint = editBtnIconTint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // --- 12x12 FIXED OVAL GRID EVENLY STRETCHED OVER SHADE AREA ---
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val density = LocalDensity.current
                val numCols = LauncherTopShadeConfigStore.GRID_COLS
                val numRows = LauncherTopShadeConfigStore.GRID_ROWS

                val gapDp = 6.dp
                val gapPx = with(density) { gapDp.toPx() }

                val availableWidthPx = constraints.maxWidth.toFloat()
                val availableHeightPx = constraints.maxHeight.toFloat()

                val cellWidthPx = ((availableWidthPx - (numCols - 1) * gapPx) / numCols.toFloat()).coerceAtLeast(10f)
                val cellHeightPx = ((availableHeightPx - (numRows - 1) * gapPx) / numRows.toFloat()).coerceAtLeast(10f)

                val cellWidthDp = with(density) { cellWidthPx.toDp() }
                val cellHeightDp = with(density) { cellHeightPx.toDp() }

                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. Grid Cell Background Guides (Shown in Edit Mode)
                    if (isEditMode) {
                        for (r in 0 until numRows) {
                            for (c in 0 until numCols) {
                                val cellXPx = c * (cellWidthPx + gapPx)
                                val cellYPx = r * (cellHeightPx + gapPx)
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(cellXPx.roundToInt(), cellYPx.roundToInt()) }
                                        .size(width = cellWidthDp, height = cellHeightDp)
                                        .border(1.dp, guideBorderColor, CircleShape)
                                        .background(guideBgColor, CircleShape),
                                )
                            }
                        }
                    }

                    // 2. Render Placed Tiles
                    tiles.forEachIndexed { index, item ->
                        val xPx = item.col * (cellWidthPx + gapPx)
                        val yPx = item.row * (cellHeightPx + gapPx)
                        val wDp = with(density) { (item.spanX * cellWidthPx + (item.spanX - 1) * gapPx).toDp() }
                        val hDp = with(density) { (item.spanY * cellHeightPx + (item.spanY - 1) * gapPx).toDp() }

                        TopShadeTileCanvasItemView(
                            spec = item,
                            index = index,
                            cellWidthPx = cellWidthPx,
                            cellHeightPx = cellHeightPx,
                            gapPx = gapPx,
                            wDp = wDp,
                            hDp = hDp,
                            xPx = xPx,
                            yPx = yPx,
                            isEditMode = isEditMode,
                            isDark = isDark,
                            onRemove = {
                                val next = tiles.toMutableList().apply { removeAt(index) }
                                LauncherTopShadeConfigStore.setTiles(context, next)
                            },
                            onUpdateTile = { updatedTile ->
                                val next = tiles.toMutableList()
                                next[index] = updatedTile
                                LauncherTopShadeConfigStore.setTiles(context, next)
                            },
                            onClick = {
                                if (!isEditMode) {
                                    executeTileAction(context, item, onClose)
                                }
                            },
                        )
                    }
                }
            }

            // --- BOTTOM SWIPE/DRAG HANDLE BAR (1/3 width, centered in bottom margin) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onHandleDrag(dragAmount.y)
                            },
                            onDragEnd = {
                                onHandleDragEnd()
                            },
                            onDragCancel = {
                                onHandleDragEnd()
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.33f)
                        .height(5.dp)
                        .background(handleColor, CircleShape),
                )
            }
        }

        // --- INLINE ADD ELEMENT MODAL ---
        if (showAddModal) {
            AddTopShadeTileModal(
                isDark = isDark,
                onDismiss = { showAddModal = false },
                onAdd = { newTile ->
                    val current = LauncherTopShadeConfigStore.getTiles(context).toMutableList()
                    val (c, r) = findFirstAvailableCell(current, newTile.spanX, newTile.spanY)
                    current.add(newTile.copy(col = c, row = r))
                    LauncherTopShadeConfigStore.setTiles(context, current)
                    showAddModal = false
                },
            )
        }
    }
}

@Composable
private fun TopShadeTileCanvasItemView(
    spec: TopShadeTileSpec,
    index: Int,
    cellWidthPx: Float,
    cellHeightPx: Float,
    gapPx: Float,
    wDp: Dp,
    hDp: Dp,
    xPx: Float,
    yPx: Float,
    isEditMode: Boolean,
    isDark: Boolean,
    onRemove: () -> Unit,
    onUpdateTile: (TopShadeTileSpec) -> Unit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val flSeatMode by UniversalCanRepository.frontLeftSeatModeState.collectAsStateWithLifecycle()
    val frSeatMode by UniversalCanRepository.frontRightSeatModeState.collectAsStateWithLifecycle()
    val rlSeatMode by UniversalCanRepository.rearLeftSeatModeState.collectAsStateWithLifecycle()
    val rrSeatMode by UniversalCanRepository.rearRightSeatModeState.collectAsStateWithLifecycle()
    val steeringHeat by UniversalCanRepository.steeringWheelHeatState.collectAsStateWithLifecycle()
    val windscreenHeat by UniversalCanRepository.frontWindscreenHeatState.collectAsStateWithLifecycle()
    val rearDefrost by UniversalCanRepository.hvacDefrosterState.collectAsStateWithLifecycle()
    val hvacAuto by UniversalCanRepository.hvacAutoState.collectAsStateWithLifecycle()
    val hvacRecirc by UniversalCanRepository.hvacAirRecirculationState.collectAsStateWithLifecycle()
    val hvacPower by UniversalCanRepository.hvacAcPowerState.collectAsStateWithLifecycle()
    val hvacAc by UniversalCanRepository.hvacAcPowerState.collectAsStateWithLifecycle()
    val hvacSync by UniversalCanRepository.hvacSyncState.collectAsStateWithLifecycle()
    val hvacPm25 by UniversalCanRepository.hvacPm25State.collectAsStateWithLifecycle()
    val hvacFanSpeed by UniversalCanRepository.hvacFanSpeedRawState.collectAsStateWithLifecycle()
    val hvacFanDirection by UniversalCanRepository.hvacFanDirectionRawState.collectAsStateWithLifecycle()
    val hvacDefrost by UniversalCanRepository.hvacDefrosterFrontState.collectAsStateWithLifecycle()
    val driverTemp by CanDataRepository.climateSetTemperature1.collectAsStateWithLifecycle()
    val passTemp by CanDataRepository.climateSetTemperature2.collectAsStateWithLifecycle()
    val headrestMode by PlatformAudioRepository.headrestMode.collectAsStateWithLifecycle()

    val vehicleControls = rememberLauncherVehicleControlSnapshot(enabled = true)

    val adasLive by LauncherAdasRepository.state.collectAsStateWithLifecycle()
    val adas = LauncherDevVehicleState.adasStateOrNull() ?: adasLive
    val tboxCruise by CanDataRepository.cruiseSetSpeed.collectAsStateWithLifecycle()
    val tjaIcaState by UniversalCanRepository.tjaIcaState.collectAsStateWithLifecycle()
    val wiperMaintenance by UniversalCanRepository.wiperMaintenanceState.collectAsStateWithLifecycle()
    val driveModeRaw by UniversalCanRepository.carSettingsDriveMode.collectAsStateWithLifecycle()
    val driveModeWetRaw by UniversalCanRepository.carSettingsDriveMode6dctWet.collectAsStateWithLifecycle()
    val gearBoxDriveMode by CanDataRepository.gearBoxDriveMode.collectAsStateWithLifecycle()
    val is6Dct by CanDataRepository.is6DctGearbox.collectAsStateWithLifecycle()
    val isSmartActive by LauncherCruisePresetController.isSmartSpeedLimitActive.collectAsStateWithLifecycle()
    val cruiseState by LauncherCruisePresetController.accCruiseState.collectAsStateWithLifecycle()
    val vehicleBody by LauncherVehicleBodyRepository.state.collectAsStateWithLifecycle()
    val isOperatingWindows by isOperatingWindowsFlow.collectAsStateWithLifecycle()
    val activeSeatMemorySlot by activeSeatMemorySlotFlow.collectAsStateWithLifecycle()

    val mirrorFoldVal = remember(vehicleControls) { MirrorFoldLastCommandStore.readLastValue() }
    val isMirrorFolded = mirrorFoldVal == MIRROR_FOLD_SWITCH_VALUE_FOLD
    val isEscOff = vehicleControls.escOff == true
    val currentLasMode = vehicleControls.lasMode ?: adas.lasMode
    val isLdwActive = currentLasMode == 1
    val isLkaActive = currentLasMode == 2
    val isFcwActive = vehicleControls.fcw == true || adas.fcwActive
    val isHmaActive = vehicleControls.hmaOn == true || adas.hma == LauncherAdasAssistIcon.Active
    val isRearFogActive = vehicleControls.rearFogLight == true
    val isWiperMaintenanceActive = wiperMaintenance is MbCanBinaryState.On
    val accActive = adas.accActive || (tboxCruise ?: 0u) > 0u
    val accStandby = adas.accStandby
    val ngpActive = adas.tja == LauncherAdasAssistIcon.Active
    val ngpStandby = tjaIcaState is MbCanBinaryState.On || adas.tja == LauncherAdasAssistIcon.Dark
    val ngpUnavailable = tjaIcaState is MbCanBinaryState.Unavailable
    val resolvedDriveMode = remember(driveModeRaw, driveModeWetRaw, gearBoxDriveMode, is6Dct) {
        resolveDriveModeDisplayLabel(driveModeRaw, driveModeWetRaw, gearBoxDriveMode, is6Dct).uppercase()
    }

    val isSyncOn = hvacSync is MbCanBinaryState.On || (hvacSync !is MbCanBinaryState.Off && driverTemp != null && passTemp != null && abs(driverTemp!! - passTemp!!) < 0.1f)
    val isPowerOn = hvacPower is MbCanBinaryState.On || hvacFanSpeed > 0

    val isActive = remember(
        spec.actionKey, flSeatMode, frSeatMode, rlSeatMode, rrSeatMode,
        steeringHeat, windscreenHeat, rearDefrost, hvacAuto, hvacRecirc,
        isPowerOn, hvacAc, isSyncOn, hvacPm25, hvacFanSpeed, hvacDefrost,
        headrestMode, vehicleControls, isMirrorFolded, isOperatingWindows,
        activeSeatMemorySlot, accActive, accStandby, ngpActive, ngpStandby,
        ngpUnavailable, isSmartActive, cruiseState, isLdwActive, isLkaActive,
        isFcwActive, isHmaActive, isRearFogActive, isWiperMaintenanceActive,
        vehicleBody.tailgateOpen
    ) {
        when (spec.actionKey) {
            "seat_heat_left" -> flSeatMode is MbCanSeatModeState.Heat && (flSeatMode as MbCanSeatModeState.Heat).level > 0
            "seat_vent_left" -> flSeatMode is MbCanSeatModeState.Vent && (flSeatMode as MbCanSeatModeState.Vent).level > 0
            "seat_heat_right" -> frSeatMode is MbCanSeatModeState.Heat && (frSeatMode as MbCanSeatModeState.Heat).level > 0
            "seat_vent_right" -> frSeatMode is MbCanSeatModeState.Vent && (frSeatMode as MbCanSeatModeState.Vent).level > 0
            "seat_heat_rear_left" -> rlSeatMode is MbCanSeatModeState.Heat && (rlSeatMode as MbCanSeatModeState.Heat).level > 0
            "seat_heat_rear_right" -> rrSeatMode is MbCanSeatModeState.Heat && (rrSeatMode as MbCanSeatModeState.Heat).level > 0
            "steering_heat" -> steeringHeat is MbCanBinaryState.On
            "windscreen_heat" -> windscreenHeat is MbCanBinaryState.On
            "rear_defrost" -> rearDefrost is MbCanBinaryState.On
            "hvac_auto" -> hvacAuto is MbCanBinaryState.On
            "hvac_recirc", "recirc" -> hvacRecirc is MbCanBinaryState.On
            "hvac_power" -> isPowerOn
            "hvac_ac" -> hvacAc is MbCanBinaryState.On
            "hvac_sync" -> isSyncOn
            "hvac_pm25" -> hvacPm25 is MbCanBinaryState.On
            "hvac_fan_speed" -> hvacFanSpeed > 0
            "front_defrost" -> hvacDefrost is MbCanBinaryState.On
            "theme" -> LauncherThemeState.darkTheme
            "wifi" -> (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true
            "headrest_speaker" -> headrestMode != 3
            "mirror_fold" -> isMirrorFolded
            "trunk_door" -> vehicleBody.tailgateOpen
            "windows" -> isOperatingWindows
            "seat_memory_1" -> activeSeatMemorySlot == 1
            "seat_memory_2" -> activeSeatMemorySlot == 2
            "seat_memory_3" -> activeSeatMemorySlot == 3
            "seat_adjust", "passenger_seat_adjust" -> false
            "acc" -> accActive || accStandby
            "ngp" -> ngpActive || ngpStandby || ngpUnavailable
            "adas_dist" -> true
            "smart_acc" -> isSmartActive && cruiseState == AccCruiseState.Active
            "ldw" -> isLdwActive
            "lka" -> isLkaActive
            "fcw" -> isFcwActive
            "bsd" -> vehicleControls.bsd == true
            "dow" -> vehicleControls.dow == true
            "parking_radar" -> vehicleControls.parkingRadar == true
            "esc_off" -> true
            "hdc" -> vehicleControls.hdc == true
            "avh" -> vehicleControls.avh == true || vehicleControls.accAutobrake == true
            "hma" -> isHmaActive
            "rear_fog" -> isRearFogActive
            "wiper_maintenance" -> isWiperMaintenanceActive
            "wiper_sensitivity" -> true
            "drive_mode" -> true
            "acc_autobrake" -> vehicleControls.accAutobrake == true
            "door_autolock" -> vehicleControls.doorAutoLock == true
            "door_ignoff_unlock" -> vehicleControls.doorIgnOffUnlock == true
            "mirror_autofold" -> vehicleControls.mirrorAutofold == true
            "mirror_reverse_turn" -> vehicleControls.mirrorReverseTurn == true
            "welcome_lamp" -> vehicleControls.welcomeLamp == true
            "wireless_charging" -> vehicleControls.wirelessCharging == true
            "car_wash_mode" -> vehicleControls.vehWashMode == true
            "welcome_seat" -> vehicleControls.welcomeSeat == true
            "window_autoclose" -> vehicleControls.windowAutoclose == true
            "las" -> ((vehicleControls.lasSensitivity ?: 0) > 0) || ((vehicleControls.lasMode ?: 0) > 0)
            "homelight_delay" -> (vehicleControls.homelightDelay ?: 0) > 0
            "driver_unlock_mode" -> (vehicleControls.driverUnlockMode ?: 0) > 0
            else -> false
        }
    }

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val numCols = LauncherTopShadeConfigStore.GRID_COLS
    val numRows = LauncherTopShadeConfigStore.GRID_ROWS

    val targetCol = remember(dragOffsetX, spec.col, cellWidthPx, gapPx) {
        val colShift = (dragOffsetX / (cellWidthPx + gapPx)).roundToInt()
        (spec.col + colShift).coerceIn(0, numCols - spec.spanX)
    }
    val targetRow = remember(dragOffsetY, spec.row, cellHeightPx, gapPx) {
        val rowShift = (dragOffsetY / (cellHeightPx + gapPx)).roundToInt()
        (spec.row + rowShift).coerceIn(0, numRows - spec.spanY)
    }

    val renderXPx = if (isDragging) xPx + dragOffsetX else xPx
    val renderYPx = if (isDragging) yPx + dragOffsetY else yPx

    val activeColor = remember(
        spec.actionKey, hvacAc, isEscOff, isLdwActive, isLkaActive, vehicleControls.lasSensitivity,
        vehicleControls.fcwOption, isFcwActive, accActive, accStandby, ngpActive, ngpStandby,
        ngpUnavailable, vehicleControls.wiperSensitivity, driveModeRaw, is6Dct, gearBoxDriveMode
    ) {
        when {
            spec.actionKey.startsWith("seat_heat_") ||
            spec.actionKey in listOf("steering_heat", "windscreen_heat", "rear_defrost") -> SeatHeatOnColor
            spec.actionKey == "hvac_pm25" -> Color(0xFF22C55E)
            spec.actionKey == "hvac_fan_speed" -> if (hvacAc is MbCanBinaryState.On) HvacOnColor else SeatHeatOnColor
            spec.actionKey.startsWith("seat_vent_") ||
            spec.actionKey.startsWith("hvac_") ||
            spec.actionKey in listOf("recirc", "front_defrost") -> HvacOnColor
            spec.actionKey == "esc_off" -> if (isEscOff) Color(0xFFEF4444) else Color(0xFF10B981)
            spec.actionKey == "ldw" -> if (vehicleControls.lasSensitivity == 1) Color(0xFFF97316) else Color(0xFF10B981)
            spec.actionKey == "lka" -> if (vehicleControls.lasSensitivity == 1) Color(0xFFF97316) else Color(0xFF10B981)
            spec.actionKey == "fcw" -> when (vehicleControls.fcwOption) {
                1 -> Color(0xFF10B981)
                2 -> Color(0xFF3B82F6)
                3 -> Color(0xFFEF4444)
                else -> Color(0xFF10B981)
            }
            spec.actionKey == "rear_fog" -> Color(0xFFF97316)
            spec.actionKey == "wiper_maintenance" -> LauncherColors.AccentCyan
            spec.actionKey == "wiper_sensitivity" -> when (vehicleControls.wiperSensitivity ?: 1) {
                1 -> Color(0xFFEF4444)
                2 -> Color(0xFFEAB308)
                3 -> Color(0xFF3B82F6)
                4 -> Color(0xFF22C55E)
                else -> Color(0xFFEF4444)
            }
            spec.actionKey == "acc" -> if (accActive) Color(0xFF10B981) else Color(0xFF38BDF8)
            spec.actionKey == "ngp" -> when {
                ngpUnavailable -> Color(0xFFEF4444)
                ngpActive -> Color(0xFF10B981)
                else -> Color(0xFF38BDF8)
            }
            spec.actionKey == "adas_dist" -> LauncherColors.AccentCyan
            spec.actionKey == "drive_mode" -> {
                when (resolvedDriveMode) {
                    "ECO" -> Color(0xFF10B981)
                    "SPT", "SPORT" -> Color(0xFFEF4444)
                    "SNOW", "СНЕГ" -> Color(0xFF38BDF8)
                    else -> Color(0xFF3B82F6)
                }
            }
            spec.actionKey in listOf(
                "seat_memory_1", "seat_memory_2", "seat_memory_3", "windows", "mirror_fold",
                "bsd", "dow", "parking_radar", "hdc", "avh", "hma", "smart_acc"
            ) -> Color(0xFF10B981)
            else -> LauncherColors.AccentBlue
        }
    }

    val containerBg = if (isDragging) {
        LauncherColors.AccentBlue.copy(alpha = 0.65f)
    } else if (isActive) {
        activeColor.copy(alpha = if (isDark) 0.35f else 0.25f)
    } else {
        if (isDark) Color.White.copy(alpha = 0.07f) else Color.White
    }
    val borderColor = if (isDragging) {
        LauncherColors.AccentBlue
    } else if (isActive) {
        activeColor
    } else {
        if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)
    }

    val isInteractive = spec.actionKey.startsWith("vol_") ||
        spec.actionKey == "brightness" ||
        spec.actionKey in listOf("temp_driver", "temp_pass", "hvac_heater_level")

    val tileShape = if (spec.spanX > 1 || spec.spanY > 1) RoundedCornerShape(30.dp) else CircleShape

    Box(
        modifier = Modifier
            .offset { IntOffset(renderXPx.roundToInt(), renderYPx.roundToInt()) }
            .size(wDp, hDp)
            .zIndex(if (isDragging) 100f else 1f),
    ) {
        if (isDragging) {
            val targetXPx = targetCol * (cellWidthPx + gapPx)
            val targetYPx = targetRow * (cellHeightPx + gapPx)
            val relTargetXPx = targetXPx - renderXPx
            val relTargetYPx = targetYPx - renderYPx
            Box(
                modifier = Modifier
                    .offset { IntOffset(relTargetXPx.roundToInt(), relTargetYPx.roundToInt()) }
                    .size(wDp, hDp)
                    .border(2.dp, LauncherColors.AccentBlue, tileShape)
                    .background(LauncherColors.AccentBlue.copy(alpha = 0.25f), tileShape),
            )
        }

        Card(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, borderColor, tileShape)
                .clip(tileShape)
                .then(
                    if (isEditMode) {
                        Modifier.pointerInput(spec, index, cellWidthPx, cellHeightPx, gapPx) {
                            detectDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetX += dragAmount.x
                                    dragOffsetY += dragAmount.y
                                },
                                onDragEnd = {
                                    val colShift = (dragOffsetX / (cellWidthPx + gapPx)).roundToInt()
                                    val rowShift = (dragOffsetY / (cellHeightPx + gapPx)).roundToInt()
                                    val finalCol = (spec.col + colShift).coerceIn(0, numCols - spec.spanX)
                                    val finalRow = (spec.row + rowShift).coerceIn(0, numRows - spec.spanY)

                                    isDragging = false
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f

                                    onUpdateTile(spec.copy(col = finalCol, row = finalRow))
                                },
                                onDragCancel = {
                                    isDragging = false
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                },
                            )
                        }
                    } else if (isInteractive) {
                        Modifier
                    } else {
                        Modifier.clickable(onClick = onClick)
                    }
                ),
            shape = tileShape,
            colors = CardDefaults.cardColors(containerColor = containerBg),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                when (spec.actionKey) {
                    "brightness" -> BrightnessTileContent(spec = spec, isEditMode = isEditMode, isDark = isDark)
                    "vol_media" -> ChannelVolumeTileContent(PlatformVolumeChannel.Media, spec = spec, isEditMode = isEditMode, isDark = isDark)
                    "vol_phone" -> ChannelVolumeTileContent(PlatformVolumeChannel.Phone, spec = spec, isEditMode = isEditMode, isDark = isDark)
                    "vol_navi" -> ChannelVolumeTileContent(PlatformVolumeChannel.Navi, spec = spec, isEditMode = isEditMode, isDark = isDark)
                    "vol_voice" -> ChannelVolumeTileContent(PlatformVolumeChannel.Voice, spec = spec, isEditMode = isEditMode, isDark = isDark)
                    "headrest_speaker" -> HeadrestTileContent(spec = spec, headrestMode = headrestMode, isDark = isDark)
                    "seat_heat_left", "seat_vent_left", "seat_heat_right", "seat_vent_right", "seat_heat_rear_left", "seat_heat_rear_right" -> {
                        SeatTileContent(
                            spec = spec,
                            flSeatMode = flSeatMode,
                            frSeatMode = frSeatMode,
                            rlSeatMode = rlSeatMode,
                            rrSeatMode = rrSeatMode,
                            isDark = isDark,
                        )
                    }
                    "hvac_auto", "hvac_sync" -> {
                        HvacTextTileContent(
                            spec = spec,
                            text = if (spec.actionKey == "hvac_sync") "SYNC" else "AUTO",
                            isActive = isActive,
                            activeColor = activeColor,
                            isDark = isDark,
                        )
                    }
                    "hvac_fan_speed" -> {
                        HvacFanSpeedTileContent(
                            spec = spec,
                            speedRaw = hvacFanSpeed,
                            activeColor = activeColor,
                            isActive = isActive,
                            isDark = isDark,
                        )
                    }
                    "temp_driver" -> {
                        HvacTempStepperTileContent(
                            spec = spec,
                            temp = driverTemp,
                            zone = HvacTempZone.Driver,
                            isDark = isDark,
                            isEditMode = isEditMode,
                        )
                    }
                    "temp_pass" -> {
                        HvacTempStepperTileContent(
                            spec = spec,
                            temp = passTemp,
                            zone = HvacTempZone.Passenger,
                            isDark = isDark,
                            isEditMode = isEditMode,
                        )
                    }
                    "hvac_heater_level" -> {
                        HvacHeaterLevelTileContent(
                            spec = spec,
                            driverTemp = driverTemp,
                            isDark = isDark,
                            isEditMode = isEditMode,
                        )
                    }
                    "headlights" -> {
                        HeadlightsTileContent(
                            spec = spec,
                            raw = vehicleControls.lightControlRaw ?: LIGHT_CONTROL_OFF,
                            isDark = isDark,
                        )
                    }
                    else -> {
                        if (spec.type == TopShadeTileType.TELEMETRY) {
                            TelemetryTileContent(spec = spec, isDark = isDark)
                        } else {
                            StandardTileContent(
                                spec = spec,
                                isActive = isActive,
                                activeColor = activeColor,
                                isDark = isDark,
                                vehicleControls = vehicleControls,
                                hvacFanDirectionRaw = hvacFanDirection ?: MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT,
                                isRecircOn = hvacRecirc is MbCanBinaryState.On,
                                isPm25On = hvacPm25 is MbCanBinaryState.On,
                                adas = adas,
                                tboxCruise = tboxCruise,
                                driveModeText = resolvedDriveMode,
                            )
                        }
                    }
                }

                if (isEditMode) {
                    val isWideTile = spec.actionKey == "brightness" ||
                        spec.actionKey.startsWith("vol_") ||
                        spec.actionKey == "volume" ||
                        spec.actionKey in listOf("temp_driver", "temp_pass", "hvac_heater_level")

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 6.dp, start = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(
                                    if ((isWideTile && spec.spanX > 2) || (!isWideTile && spec.spanX > 1)) LauncherColors.AccentBlue else if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.15f),
                                    CircleShape,
                                )
                                .clickable {
                                    val nextSpanX = if (isWideTile) {
                                        if (spec.spanX == 2) 4 else 2
                                    } else {
                                        if (spec.spanX == 1) 2 else 1
                                    }
                                    val nextCol = if (spec.col + nextSpanX > numCols) (numCols - nextSpanX).coerceAtLeast(0) else spec.col
                                    onUpdateTile(spec.copy(spanX = nextSpanX, col = nextCol))
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = IconResizeWidth,
                                contentDescription = "Resize Width",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(
                                    if (spec.spanY > 1) LauncherColors.AccentBlue else if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.15f),
                                    CircleShape,
                                )
                                .clickable {
                                    val nextSpanY = if (spec.spanY == 1) 2 else 1
                                    val nextRow = if (spec.row + nextSpanY > numRows) (numRows - nextSpanY).coerceAtLeast(0) else spec.row
                                    onUpdateTile(spec.copy(spanY = nextSpanY, row = nextRow))
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = IconResizeHeight,
                                contentDescription = "Resize Height",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 6.dp, end = 8.dp)
                            .size(18.dp)
                            .background(Color.Red, CircleShape)
                            .clickable(onClick = onRemove),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrightnessTileContent(
    spec: TopShadeTileSpec,
    isEditMode: Boolean,
    isDark: Boolean,
) {
    val context = LocalContext.current
    var brightnessLevel by remember {
        mutableFloatStateOf(
            runCatching {
                val cur = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                cur.toFloat() / 255f
            }.getOrDefault(0.7f)
        )
    }

    val percent = (brightnessLevel * 100).roundToInt()
    val textColor = if (isDark) Color.White else Color(0xFF1F2937)
    val iconColor = if (isDark) Color.White else Color(0xFF374151)

    if (spec.spanY >= 2) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = IconBrightnessSun,
                    contentDescription = "Brightness",
                    tint = iconColor,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Яркость: $percent%", color = textColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = brightnessLevel,
                enabled = !isEditMode,
                onValueChange = { newVal ->
                    brightnessLevel = newVal
                    runCatching {
                        val val255 = (newVal * 255).toInt().coerceIn(10, 255)
                        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, val255)
                        (context as? Activity)?.window?.attributes?.let { lp ->
                            lp.screenBrightness = newVal
                            context.window.attributes = lp
                        }
                    }
                },
                colors = SliderDefaults.colors(
                    thumbColor = if (isDark) Color.White else LauncherColors.AccentBlue,
                    activeTrackColor = LauncherColors.AccentBlue,
                    inactiveTrackColor = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.12f),
                ),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = IconBrightnessSun,
                contentDescription = "Brightness",
                tint = iconColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            if (spec.spanX >= 4) {
                Text("Яркость: $percent%", color = textColor, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Slider(
                value = brightnessLevel,
                enabled = !isEditMode,
                onValueChange = { newVal ->
                    brightnessLevel = newVal
                    runCatching {
                        val val255 = (newVal * 255).toInt().coerceIn(10, 255)
                        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, val255)
                        (context as? Activity)?.window?.attributes?.let { lp ->
                            lp.screenBrightness = newVal
                            context.window.attributes = lp
                        }
                    }
                },
                colors = SliderDefaults.colors(
                    thumbColor = if (isDark) Color.White else LauncherColors.AccentBlue,
                    activeTrackColor = LauncherColors.AccentBlue,
                    inactiveTrackColor = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.12f),
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChannelVolumeTileContent(
    channel: PlatformVolumeChannel,
    spec: TopShadeTileSpec,
    isEditMode: Boolean,
    isDark: Boolean,
) {
    val context = LocalContext.current
    val currentVolume by when (channel) {
        PlatformVolumeChannel.Media -> PlatformAudioRepository.mediaVolume.collectAsStateWithLifecycle()
        PlatformVolumeChannel.Phone -> PlatformAudioRepository.phoneVolume.collectAsStateWithLifecycle()
        PlatformVolumeChannel.Navi -> PlatformAudioRepository.naviVolume.collectAsStateWithLifecycle()
        PlatformVolumeChannel.Voice -> PlatformAudioRepository.voiceVolume.collectAsStateWithLifecycle()
    }

    val iconVector = when (channel) {
        PlatformVolumeChannel.Media -> IconVolumeSpeaker
        PlatformVolumeChannel.Phone -> IconPhoneVector
        PlatformVolumeChannel.Navi -> IconNaviVector
        PlatformVolumeChannel.Voice -> IconMicVector
    }

    val textColor = if (isDark) Color.White else Color(0xFF1F2937)
    val iconColor = if (isDark) Color.White else Color(0xFF374151)

    if (spec.spanY >= 2) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = channel.title,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("${channel.title}: $currentVolume / ${channel.maxVal}", color = textColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = currentVolume.toFloat(),
                valueRange = channel.minVal.toFloat()..channel.maxVal.toFloat(),
                enabled = !isEditMode,
                onValueChange = { newVal ->
                    PlatformAudioRepository.setChannelVolume(context, channel, newVal.roundToInt())
                },
                colors = SliderDefaults.colors(
                    thumbColor = if (isDark) Color.White else LauncherColors.AccentBlue,
                    activeTrackColor = LauncherColors.AccentBlue,
                    inactiveTrackColor = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.12f),
                ),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = channel.title,
                tint = iconColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            if (spec.spanX >= 4) {
                Text("${channel.title}: $currentVolume / ${channel.maxVal}", color = textColor, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Slider(
                value = currentVolume.toFloat(),
                valueRange = channel.minVal.toFloat()..channel.maxVal.toFloat(),
                enabled = !isEditMode,
                onValueChange = { newVal ->
                    PlatformAudioRepository.setChannelVolume(context, channel, newVal.roundToInt())
                },
                colors = SliderDefaults.colors(
                    thumbColor = if (isDark) Color.White else LauncherColors.AccentBlue,
                    activeTrackColor = LauncherColors.AccentBlue,
                    inactiveTrackColor = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.12f),
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AdaptiveTileText(
    text: String,
    color: Color,
    baseFontSize: TextUnit,
    minFontSize: TextUnit = 11.sp,
    maxLines: Int = 2,
    textAlign: TextAlign = TextAlign.Center,
    fontWeight: FontWeight = FontWeight.Bold,
    modifier: Modifier = Modifier,
) {
    var fontSizeVal by remember(text, baseFontSize) { mutableFloatStateOf(baseFontSize.value) }

    Text(
        text = text,
        color = color,
        fontSize = fontSizeVal.sp,
        lineHeight = (fontSizeVal * 1.15f).sp,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Clip,
        softWrap = true,
        modifier = modifier,
        onTextLayout = { result ->
            if (result.size.width > 0) {
                val hasLineOverflow = (0 until result.lineCount).any { line ->
                    result.isLineEllipsized(line) || result.getLineRight(line) > result.size.width
                }
                if ((result.hasVisualOverflow || hasLineOverflow) && fontSizeVal > minFontSize.value) {
                    fontSizeVal = (fontSizeVal * 0.9f).coerceAtLeast(minFontSize.value)
                }
            }
        },
    )
}

@Composable
private fun GeneralTileLayout(
    spec: TopShadeTileSpec,
    isDark: Boolean,
    title: String,
    hasIcon: Boolean = true,
    iconContent: @Composable (iconModifier: Modifier) -> Unit,
) {
    val textColor = if (isDark) Color.White else Color(0xFF1F2937)

    if (!hasIcon) {
        // Всегда во всех размерах, если нет иконки, текст центруй по вертикали и горизонтали
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            AdaptiveTileText(
                text = title,
                color = textColor,
                baseFontSize = 26.sp,
                minFontSize = 13.sp,
                maxLines = if (spec.spanY >= 2) 4 else 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }

    if (spec.spanX == 1 && spec.spanY == 1) {
        // 1 слот: только значок с минимальными отступами
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 3.dp, horizontal = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            iconContent(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f),
            )
        }
    } else if (spec.spanX >= 2 && spec.spanY == 1) {
        // Горизонтально: слева значок, справа название
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f),
            ) {
                iconContent(Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.width(8.dp))
            AdaptiveTileText(
                text = title,
                color = textColor,
                baseFontSize = 26.sp,
                minFontSize = 13.sp,
                maxLines = 2,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        // Вертикально: сверху значок, снизу название
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                iconContent(
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f),
                )
            }
            AdaptiveTileText(
                text = title,
                color = textColor,
                baseFontSize = 26.sp,
                minFontSize = 13.sp,
                maxLines = if (spec.spanY >= 3) 3 else 2,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun HeadrestTileContent(
    spec: TopShadeTileSpec,
    headrestMode: Int,
    isDark: Boolean,
) {
    val modeTitle = when (headrestMode) {
        1 -> "Подголовник: Вкл"
        2 -> "Ассистент"
        else -> "Подголовник: Выкл"
    }
    val isActive = headrestMode != 3
    val iconTint = if (isActive) LauncherColors.AccentBlue else if (isDark) Color.White else Color(0xFF374151)

    GeneralTileLayout(
        spec = spec,
        isDark = isDark,
        title = modeTitle,
        hasIcon = true,
    ) { iconModifier ->
        BoxWithConstraints(modifier = iconModifier, contentAlignment = Alignment.Center) {
            val iconSize = if (maxWidth < maxHeight) maxWidth else maxHeight
            Icon(
                imageVector = IconVolumeSpeaker,
                contentDescription = modeTitle,
                tint = iconTint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
private fun SeatTileContent(
    spec: TopShadeTileSpec,
    flSeatMode: MbCanSeatModeState,
    frSeatMode: MbCanSeatModeState,
    rlSeatMode: MbCanSeatModeState,
    rrSeatMode: MbCanSeatModeState,
    isDark: Boolean,
) {
    val raw = when (spec.actionKey) {
        "seat_heat_left", "seat_vent_left" -> launcherSeatModeToRaw(flSeatMode) ?: 1
        "seat_heat_right", "seat_vent_right" -> launcherSeatModeToRaw(frSeatMode) ?: 1
        "seat_heat_rear_left" -> launcherSeatModeToRaw(rlSeatMode) ?: 1
        "seat_heat_rear_right" -> launcherSeatModeToRaw(rrSeatMode) ?: 1
        else -> 1
    }
    val isVent = spec.actionKey.startsWith("seat_vent_")
    val mirrored = spec.actionKey.endsWith("_right")
    val badgeText = if (spec.actionKey.contains("_rear_")) "REAR" else null
    val level = if (isVent) ventLevel(raw) else heatLevel(raw)
    val displayTitle = if (level > 0) "${spec.title} $level" else spec.title

    GeneralTileLayout(
        spec = spec,
        isDark = isDark,
        title = displayTitle,
        hasIcon = true,
    ) { iconModifier ->
        BoxWithConstraints(modifier = iconModifier, contentAlignment = Alignment.Center) {
            val iconSize = if (maxWidth < maxHeight) maxWidth else maxHeight
            if (isVent) {
                LauncherSeatVentIcon(
                    raw = raw,
                    mirrored = mirrored,
                    iconSizeDp = iconSize,
                    seatSizeDp = iconSize * 0.86f,
                )
            } else {
                LauncherSeatHeatIcon(
                    raw = raw,
                    mirrored = mirrored,
                    badgeText = badgeText,
                    iconSizeDp = iconSize,
                    seatSizeDp = iconSize * 0.86f,
                )
            }
        }
    }
}

@Composable
private fun HvacTextTileContent(
    spec: TopShadeTileSpec,
    text: String,
    isActive: Boolean,
    activeColor: Color,
    isDark: Boolean,
) {
    val textColor = if (isActive) activeColor else if (isDark) Color.White else Color(0xFF374151)

    GeneralTileLayout(
        spec = spec,
        isDark = isDark,
        title = spec.title,
        hasIcon = true,
    ) { iconModifier ->
        BoxWithConstraints(modifier = iconModifier, contentAlignment = Alignment.Center) {
            val iconSize = if (maxWidth < maxHeight) maxWidth else maxHeight
            val textSize = (iconSize.value * 0.45f).sp
            Text(
                text = text,
                color = textColor,
                fontSize = textSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HvacFanSpeedTileContent(
    spec: TopShadeTileSpec,
    speedRaw: Int,
    activeColor: Color,
    isActive: Boolean,
    isDark: Boolean,
) {
    val iconTint = if (isActive) activeColor else (if (isDark) Color.White else Color(0xFF374151))
    val displayTitle = if (speedRaw > 0) "Скорость $speedRaw" else spec.title

    GeneralTileLayout(
        spec = spec,
        isDark = isDark,
        title = displayTitle,
        hasIcon = true,
    ) { iconModifier ->
        BoxWithConstraints(modifier = iconModifier, contentAlignment = Alignment.Center) {
            val iconSize = if (maxWidth < maxHeight) maxWidth else maxHeight
            Box(
                modifier = Modifier.size(iconSize),
                contentAlignment = Alignment.Center,
            ) {
                LauncherFanSpeedIcon(
                    speedLevel = speedRaw.coerceIn(0, 7),
                    activeColor = activeColor,
                    modifier = Modifier.fillMaxSize(),
                )
                Image(
                    painter = painterResource(R.drawable.ic_widget_fan),
                    contentDescription = spec.title,
                    colorFilter = ColorFilter.tint(iconTint),
                    modifier = Modifier.size(iconSize * 0.5f),
                )
            }
        }
    }
}

@Composable
private fun HvacTempStepperTileContent(
    spec: TopShadeTileSpec,
    temp: Float?,
    zone: HvacTempZone,
    isDark: Boolean,
    isEditMode: Boolean,
) {
    val context = LocalContext.current
    val tempColorIndicationRevision by LauncherAppConfigStore.climateTempColorIndicationRevisionFlow.collectAsStateWithLifecycle()
    val tempColorIndication = remember(context, tempColorIndicationRevision) {
        LauncherAppConfigStore.climateTempColorIndication(context)
    }

    val blueColor = Color(0xFF3B82F6)
    val redColor = Color(0xFFEF4444)
    val downTint = if (tempColorIndication) blueColor else (if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF6B7280))
    val upTint = if (tempColorIndication) redColor else (if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF6B7280))

    val textColor = if (tempColorIndication) {
        val fraction = if (temp != null) ((temp - 16f) / 14f).coerceIn(0f, 1f) else 0.5f
        lerp(blueColor, redColor, fraction)
    } else {
        if (isDark) Color.White else Color(0xFF1F2937)
    }

    val formattedTemp = remember(temp) { formatHvacSetTempAnnotated(temp) }

    val onDown = {
        if (!isEditMode) {
            sendAdjustHvacTemperature(context, temp, -0.5f, zone)
        }
    }
    val onUp = {
        if (!isEditMode) {
            sendAdjustHvacTemperature(context, temp, 0.5f, zone)
        }
    }
    val onOpenApp = {
        if (!isEditMode) {
            launchClimateApp(context)
        }
    }

    if (spec.spanX >= 2) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onDown, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, "Down", tint = downTint, modifier = Modifier.size(24.dp))
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenApp),
            ) {
                Text(
                    text = if (zone == HvacTempZone.Driver) "Водитель" else "Пассажир",
                    color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF6B7280),
                    fontSize = 18.sp,
                    maxLines = 1,
                )
                Text(
                    text = formattedTemp,
                    color = textColor,
                    fontSize = if (spec.spanY >= 2) 36.sp else 28.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
            IconButton(onClick = onUp, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, "Up", tint = upTint, modifier = Modifier.size(24.dp))
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onUp, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, "Up", tint = upTint, modifier = Modifier.size(18.dp))
            }
            Text(
                text = formattedTemp,
                color = textColor,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable(onClick = onOpenApp),
            )
            IconButton(onClick = onDown, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, "Down", tint = downTint, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun HvacHeaterLevelTileContent(
    spec: TopShadeTileSpec,
    driverTemp: Float?,
    isDark: Boolean,
    isEditMode: Boolean,
) {
    val context = LocalContext.current
    val currentTemp = driverTemp
    val heaterLevel = if (currentTemp != null) {
        ((currentTemp - 16f).roundToInt() + 1).coerceIn(1, 15)
    } else {
        8
    }

    val blueColor = Color(0xFF3B82F6)
    val redColor = Color(0xFFEF4444)
    val fraction = (heaterLevel - 1) / 14f
    val levelColor = lerp(blueColor, redColor, fraction)

    val onDown = {
        if (!isEditMode) {
            sendAdjustHvacTemperature(context, driverTemp, -1.0f, HvacTempZone.Driver)
        }
    }
    val onUp = {
        if (!isEditMode) {
            sendAdjustHvacTemperature(context, driverTemp, 1.0f, HvacTempZone.Driver)
        }
    }
    val onOpenApp = {
        if (!isEditMode) {
            launchClimateApp(context)
        }
    }

    if (spec.spanX >= 2) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onDown, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, "Down", tint = blueColor, modifier = Modifier.size(24.dp))
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenApp),
            ) {
                Text(
                    text = "Печка",
                    color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF6B7280),
                    fontSize = 18.sp,
                    maxLines = 1,
                )
                Text(
                    text = "$heaterLevel / 15",
                    color = levelColor,
                    fontSize = if (spec.spanY >= 2) 36.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            IconButton(onClick = onUp, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, "Up", tint = redColor, modifier = Modifier.size(24.dp))
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onUp, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, "Up", tint = redColor, modifier = Modifier.size(18.dp))
            }
            Text(
                text = "$heaterLevel",
                color = levelColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable(onClick = onOpenApp),
            )
            IconButton(onClick = onDown, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, "Down", tint = blueColor, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun HeadlightsTileContent(
    spec: TopShadeTileSpec,
    raw: Int,
    isDark: Boolean,
) {
    val (iconRes, tintColor) = when (raw) {
        LIGHT_CONTROL_AUTO -> R.drawable.ic_headlights_auto to Color(0xFF22C55E)
        LIGHT_CONTROL_LOW_BEAM -> R.drawable.ic_headlights_low_beam to Color(0xFF38BDF8)
        LIGHT_CONTROL_POSITION -> R.drawable.ic_headlights_position to Color(0xFF38BDF8)
        else -> R.drawable.ic_headlights_off to Color(0xFF6B7280)
    }

    GeneralTileLayout(
        spec = spec,
        isDark = isDark,
        title = spec.title,
        hasIcon = true,
    ) { iconModifier ->
        BoxWithConstraints(modifier = iconModifier, contentAlignment = Alignment.Center) {
            val iconSize = if (maxWidth < maxHeight) maxWidth else maxHeight
            Image(
                painter = painterResource(iconRes),
                contentDescription = spec.title,
                colorFilter = ColorFilter.tint(tintColor),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
private fun StandardTileContent(
    spec: TopShadeTileSpec,
    isActive: Boolean,
    activeColor: Color,
    isDark: Boolean,
    vehicleControls: LauncherVehicleControlSnapshot = LauncherVehicleControlSnapshot(),
    hvacFanDirectionRaw: Int = MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT,
    isRecircOn: Boolean = false,
    isPm25On: Boolean = false,
    adas: LauncherAdasState = LauncherAdasState(),
    tboxCruise: UInt? = null,
    driveModeText: String = "",
) {
    val context = LocalContext.current

    val displayTitle = remember(spec, vehicleControls, adas, tboxCruise, driveModeText) {
        when (spec.actionKey) {
            "fcw" -> {
                val isFcwActive = vehicleControls.fcw == true || adas.fcwActive
                if (!isFcwActive) "FCW: Выкл"
                else when (vehicleControls.fcwOption) {
                    1 -> "FCW: Близко"
                    2 -> "FCW: Средне"
                    3 -> "FCW: Далеко"
                    else -> "FCW: Вкл"
                }
            }
            "ldw" -> {
                val curMode = vehicleControls.lasMode ?: adas.lasMode
                if (curMode == 1) {
                    if (vehicleControls.lasSensitivity == 1) "LDW: Выс." else "LDW: Низ."
                } else "LDW"
            }
            "lka" -> {
                val curMode = vehicleControls.lasMode ?: adas.lasMode
                if (curMode == 2) {
                    if (vehicleControls.lasSensitivity == 1) "LKA: Выс." else "LKA: Низ."
                } else "LKA"
            }
            "esc_off" -> if (vehicleControls.escOff == true) "ESP: Выкл" else "ESP: Вкл"
            "acc" -> {
                val speed = adas.accSetSpeedKmh ?: tboxCruise?.toInt()?.takeIf { it > 0 }
                if (speed != null && speed > 0 && (adas.accActive || (tboxCruise ?: 0u) > 0u)) {
                    "$speed км/ч"
                } else "ACC"
            }
            "wiper_sensitivity" -> "Авто ${vehicleControls.wiperSensitivity ?: 1}"
            "drive_mode" -> if (driveModeText.isNotBlank()) "Режим $driveModeText" else "Режим"
            "seat_memory_1" -> "DRV1"
            "seat_memory_2" -> "DRV2"
            "seat_memory_3" -> "DRV3"
            "adas_dist" -> "Дистанция"
            "smart_acc" -> "Умный ACC"
            "homelight_delay" -> when (vehicleControls.homelightDelay) {
                1 -> "Фары: 30с"
                2 -> "Фары: 60с"
                3 -> "Фары: 90с"
                else -> "Фары: Выкл"
            }
            "driver_unlock_mode" -> when (vehicleControls.driverUnlockMode) {
                1 -> "Двери: Все"
                2 -> "Двери: Водитель"
                else -> "Разблокировка"
            }
            else -> spec.title
        }
    }

    val iconTint = if (isActive) {
        activeColor
    } else {
        if (isDark) Color.White else Color(0xFF374151)
    }

    val iconRes = remember(spec.iconResName, spec.actionKey, hvacFanDirectionRaw, isRecircOn, isPm25On) {
        when (spec.actionKey) {
            "hvac_fan_direction" -> when (hvacFanDirectionRaw) {
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT -> R.drawable.ic_widget_fan_feet
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE_FOOT -> R.drawable.ic_widget_fan_face_feet
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE -> R.drawable.ic_widget_fan_face
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST_FOOT -> R.drawable.ic_widget_fan_defrost_feet
                else -> R.drawable.ic_widget_fan_feet
            }
            "hvac_recirc", "recirc" -> {
                if (isRecircOn) R.drawable.ic_widget_hvac_recirc_on else R.drawable.ic_widget_hvac_recirc_off
            }
            "hvac_pm25" -> {
                if (isPm25On) R.drawable.ic_widget_pm25_on else R.drawable.ic_widget_pm25_off
            }
            "mirror_fold" -> R.drawable.ic_launcher_mirror
            "windows" -> R.drawable.ic_widget_side_window
            "trunk_door" -> R.drawable.ic_launcher_trunk
            "seat_memory_1" -> R.drawable.ic_seat_memory_drv1
            "seat_memory_2" -> R.drawable.ic_seat_memory_drv2
            "seat_memory_3" -> R.drawable.ic_seat_memory_drv3
            "seat_adjust", "passenger_seat_adjust" -> R.drawable.ic_seat_memory_btn
            "acc" -> R.drawable.ic_launcher_cruise
            "ngp" -> R.drawable.ic_adas_steering_wheel
            "ldw", "lka" -> R.drawable.ic_adas_lka
            "fcw" -> R.drawable.ic_fcw_arcs
            "bsd" -> R.drawable.ic_bsd_blindspot
            "dow" -> R.drawable.ic_dow_door
            "parking_radar" -> R.drawable.ic_widget_parking_radar
            "esc_off" -> R.drawable.ic_esp_icon
            "hdc" -> R.drawable.ic_launcher_hdc
            "avh" -> R.drawable.ic_hold_brake
            "hma" -> R.drawable.ic_high_beam_auto
            "rear_fog" -> R.drawable.ic_launcher_rear_fog
            "wiper_maintenance" -> R.drawable.ic_wiper
            "wiper_sensitivity" -> R.drawable.ic_windshield
            else -> {
                spec.iconResName?.let { name ->
                    context.resources.getIdentifier(name, "drawable", context.packageName).takeIf { it != 0 }
                }
            }
        }
    }
    val appIconBitmap = remember(spec.actionKey, spec.type) {
        if (spec.type == TopShadeTileType.SHORTCUT) {
            LauncherAppIconLoader.fromPackage(context, spec.actionKey, 96)
        } else null
    }

    val hasIcon = iconRes != null || appIconBitmap != null || spec.actionKey in listOf("adas_dist", "smart_acc")

    GeneralTileLayout(
        spec = spec,
        isDark = isDark,
        title = displayTitle,
        hasIcon = hasIcon,
    ) { iconModifier ->
        BoxWithConstraints(modifier = iconModifier, contentAlignment = Alignment.Center) {
            val iconSize = if (maxWidth < maxHeight) maxWidth else maxHeight
            if (spec.actionKey == "adas_dist") {
                val rawGap = adas.timeGapLevel ?: LauncherDevVehicleState.adasTimeGapLevel
                val displayChevronLevel = when (rawGap) {
                    0 -> 3
                    1 -> 2
                    2 -> 1
                    else -> 3 - rawGap.coerceIn(0, 2)
                }
                ThreeChevronsIcon(
                    level = displayChevronLevel,
                    activeColor = activeColor,
                    modifier = Modifier.size(iconSize * 0.9f),
                )
            } else if (spec.actionKey == "smart_acc") {
                val navState = YandexNavRepository.state.collectAsStateWithLifecycle().value
                val navSpeedVisible = LauncherAppConfigStore.navSpeedVisible(context)
                val rawYandexLimit = navState.speedLimit?.filter { it.isDigit() }?.toIntOrNull()
                val yandexLimit = if (navSpeedVisible) rawYandexLimit else null
                val cameraLimit = adas.speedLimitKmh ?: adas.tsr.speedLimitKmh.takeIf { adas.tsr.valid }
                val effectiveSpeedLimit = yandexLimit ?: cameraLimit
                val limitText = effectiveSpeedLimit?.toString() ?: "--"
                Box(
                    modifier = Modifier
                        .size(iconSize * 0.85f)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(3.dp, Color(0xFFEF4444), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = limitText,
                        color = Color.Black,
                        fontSize = (iconSize.value * 0.38f).sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (iconRes != null) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = displayTitle,
                    colorFilter = ColorFilter.tint(iconTint),
                    modifier = Modifier
                        .size(iconSize)
                        .then(
                            if (spec.actionKey == "passenger_seat_adjust") {
                                Modifier.graphicsLayer { scaleX = -1f }
                            } else Modifier
                        ),
                )
            } else if (appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = displayTitle,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

@Composable
private fun TelemetryTileContent(
    spec: TopShadeTileSpec,
    isDark: Boolean,
) {
    val engineTemp by CanDataRepository.engineTemperature.collectAsStateWithLifecycle()
    val voltage by CanDataRepository.voltage.collectAsStateWithLifecycle()
    val wheelsPressure by CanDataRepository.wheelsPressure.collectAsStateWithLifecycle()

    val labelFontSize = if (spec.spanY >= 2) 26.sp else 18.sp
    val valueFontSize = if (spec.spanY >= 2) 36.sp else 22.sp
    val textColor = if (isDark) Color.White else Color(0xFF1F2937)
    val labelColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF6B7280)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (spec.actionKey == "engine_battery") {
            val engineTempStr = engineTemp?.let { "${it.toInt()}°C" } ?: "--"
            val voltageStr = voltage?.let { String.format(Locale.US, "%.1fV", it) } ?: "--"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("ДВС & АКБ", color = labelColor, fontSize = labelFontSize, textAlign = TextAlign.Center)
                Text("t° ДВС: $engineTempStr", color = textColor, fontSize = valueFontSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(voltageStr, color = LauncherColors.AccentBlue, fontSize = valueFontSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        } else if (spec.actionKey == "tpms_info") {
            val fl = wheelsPressure.wheel1?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
            val fr = wheelsPressure.wheel2?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
            val rl = wheelsPressure.wheel3?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
            val rr = wheelsPressure.wheel4?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Давление шин", color = labelColor, fontSize = labelFontSize, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ПЛ:$fl", color = textColor, fontSize = valueFontSize, fontWeight = FontWeight.Medium)
                    Text("ПР:$fr", color = textColor, fontSize = valueFontSize, fontWeight = FontWeight.Medium)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ЗЛ:$rl", color = textColor, fontSize = valueFontSize, fontWeight = FontWeight.Medium)
                    Text("ЗР:$rr", color = textColor, fontSize = valueFontSize, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun executeTileAction(context: Context, spec: TopShadeTileSpec, onClose: () -> Unit) {
    when (spec.actionKey) {
        "brightness", "vol_media", "vol_phone", "vol_navi", "vol_voice" -> {
            // Interactive via sliders inside tiles
        }
        "headrest_speaker" -> PlatformAudioRepository.cycleHeadrestMode(context)
        "mirror_fold" -> sendToggleMirrorFold(context)
        "seat_heat_left" -> {
            val flSeatMode = UniversalCanRepository.frontLeftSeatModeState.value
            val raw = launcherSeatModeToRaw(flSeatMode)
                ?: runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH) }.getOrNull()
                ?: 1
            sendCycleFrontSeatHeat(context, MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH, raw)
        }
        "seat_vent_left" -> {
            val flSeatMode = UniversalCanRepository.frontLeftSeatModeState.value
            val raw = launcherSeatModeToRaw(flSeatMode)
                ?: runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH) }.getOrNull()
                ?: 1
            sendCycleFrontSeatVent(context, MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH, raw)
        }
        "seat_heat_right" -> {
            val frSeatMode = UniversalCanRepository.frontRightSeatModeState.value
            val raw = launcherSeatModeToRaw(frSeatMode)
                ?: runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH) }.getOrNull()
                ?: 1
            sendCycleFrontSeatHeat(context, MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH, raw)
        }
        "seat_vent_right" -> {
            val frSeatMode = UniversalCanRepository.frontRightSeatModeState.value
            val raw = launcherSeatModeToRaw(frSeatMode)
                ?: runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH) }.getOrNull()
                ?: 1
            sendCycleFrontSeatVent(context, MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH, raw)
        }
        "seat_heat_rear_left" -> {
            val rlSeatMode = UniversalCanRepository.rearLeftSeatModeState.value
            val raw = launcherSeatModeToRaw(rlSeatMode)
                ?: runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH) }.getOrNull()
                ?: 1
            sendCycleFrontSeatHeat(context, MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH, raw)
        }
        "seat_heat_rear_right" -> {
            val rrSeatMode = UniversalCanRepository.rearRightSeatModeState.value
            val raw = launcherSeatModeToRaw(rrSeatMode)
                ?: runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH) }.getOrNull()
                ?: 1
            sendCycleFrontSeatHeat(context, MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH, raw)
        }
        "temp_driver", "temp_pass", "hvac_heater_level" -> {
            launchClimateApp(context)
        }
        "steering_heat" -> sendToggleSteeringWheelHeat(context)
        "windscreen_heat" -> sendToggleFrontWindscreenHeat(context)
        "rear_defrost" -> sendToggleRearWindowMirrorsDefrost(context)
        "hvac_auto" -> sendToggleHvacAuto(context)
        "hvac_recirc", "recirc" -> sendToggleHvacAirRecirculation(context)
        "hvac_power" -> sendToggleHvacPower(context)
        "hvac_ac" -> sendToggleHvacAc(context)
        "hvac_sync" -> sendToggleHvacSync(context)
        "hvac_pm25" -> sendToggleHvacPm25(context)
        "hvac_fan_speed" -> sendCycleHvacFanSpeed(context)
        "hvac_fan_direction" -> sendCycleHvacFanDirection(context)
        "front_defrost" -> sendToggleHvacDefrosterFront(context)
        "headlights" -> {
            val currentRaw = runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.LIGHT_CONTROL) }.getOrNull() ?: LIGHT_CONTROL_OFF
            sendCycleHeadlightsSwitch(context, currentRaw)
        }

        // ADAS & Vehicle actions
        "trunk_door" -> {
            val gearBoxMode = CanDataRepository.gearBoxMode.value
            val gearBoxCurrentGear = CanDataRepository.gearBoxCurrentGear.value
            val activeGear = LauncherDevVehicleState.gearSlotOverride ?: resolveActiveGearSlot(gearBoxMode, gearBoxCurrentGear)
            if (activeGear == 'P') {
                sendOpenCloseTrunk(context)
            } else {
                Toast.makeText(context, "Переключите передачу в P", Toast.LENGTH_SHORT).show()
            }
        }
        "windows" -> triggerSequentialWindows(0)
        "seat_memory_1" -> recallSeatMemorySlot(context, 1)
        "seat_memory_2" -> recallSeatMemorySlot(context, 2)
        "seat_memory_3" -> recallSeatMemorySlot(context, 3)
        "seat_adjust" -> {
            onClose()
            launchMirrorAdjustSettingsEmbedded(context)
        }
        "passenger_seat_adjust" -> {
            onClose()
            PassengerSeatOverlayWindow.show(context)
        }
        "acc" -> LauncherCruisePresetController.launchStatusSingleTap(context)
        "ngp" -> LauncherCruisePresetController.launchNgpWithSetMinus(context)
        "adas_dist" -> {
            val adas = LauncherDevVehicleState.adasStateOrNull() ?: LauncherAdasRepository.state.value
            val rawGap = adas.timeGapLevel ?: LauncherDevVehicleState.adasTimeGapLevel
            val nextRaw = when (rawGap) {
                0 -> 1
                1 -> 2
                2 -> 0
                else -> 0
            }
            LauncherCruisePresetController.setTimeGap(context, nextRaw)
        }
        "smart_acc" -> {
            val navState = YandexNavRepository.state.value
            val navSpeedVisible = LauncherAppConfigStore.navSpeedVisible(context)
            val rawYandexLimit = navState.speedLimit?.filter { it.isDigit() }?.toIntOrNull()
            val yandexLimit = if (navSpeedVisible) rawYandexLimit else null
            val adas = LauncherDevVehicleState.adasStateOrNull() ?: LauncherAdasRepository.state.value
            val cameraLimit = adas.speedLimitKmh ?: adas.tsr.speedLimitKmh.takeIf { adas.tsr.valid }
            val effectiveLimit = yandexLimit ?: cameraLimit
            val offset = LauncherAppConfigStore.adasAccSpeedOffset(context)
            if (effectiveLimit != null) {
                LauncherCruisePresetController.applySpeedLimitPreset(context, effectiveLimit + offset)
            } else {
                Toast.makeText(context, "Ограничение скорости не определено", Toast.LENGTH_SHORT).show()
            }
        }
        "ldw" -> {
            val curMode = runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION) }.getOrNull()
            if (curMode != 1) {
                sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION, 1)
                sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LDW_SWITCH, 2)
            } else {
                val curSens = runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL) }.getOrNull() ?: 0
                if (curSens == 0) {
                    sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL, 1)
                } else {
                    sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION, 3)
                    sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LDW_SWITCH, 1)
                }
            }
        }
        "lka" -> {
            val curMode = runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION) }.getOrNull()
            if (curMode != 2) {
                sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION, 2)
                sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LDW_SWITCH, 2)
            } else {
                val curSens = runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL) }.getOrNull() ?: 0
                if (curSens == 0) {
                    sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL, 1)
                } else {
                    sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION, 3)
                    sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LDW_SWITCH, 1)
                }
            }
        }
        "bsd" -> {
            val current = runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.BSD_SWITCH) }.getOrNull()
            val next = if (current == 2) 1 else 2
            sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.BSD_SWITCH, next)
        }
        "dow" -> {
            val current = runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.DOW_SWITCH) }.getOrNull()
            val next = if (current == 2) 1 else 2
            sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.DOW_SWITCH, next)
        }
        "hma" -> {
            val current = runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.HMA_SWITCH) }.getOrNull()
            val next = if (current == 2) 1 else 2
            sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.HMA_SWITCH, next)
        }
        "rear_fog" -> sendToggleRearFogLight(context)
        "wiper_sensitivity" -> {
            val current = runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY) }.getOrNull() ?: 1
            val next = if (current >= 4) 1 else current + 1
            sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY, next)
        }
        "avh" -> sendToggleAvh(context)
        "hdc" -> sendToggleHdc(context)
        "esc_off" -> sendToggleEscOff(context)
        "parking_radar" -> sendToggleParkingRadar(context)
        "acc_autobrake" -> sendToggleAccAutobrake(context)
        "fcw" -> {
            val currentFc = runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.FCW_SWITCH) }.getOrNull()
            val currentOpt = runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.FCW_OPTION) }.getOrNull()
            sendCycleFcw(context, currentFc == 2 || currentFc == 1, currentOpt)
        }
        "las" -> {
            val currentLas = runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL) }.getOrNull()
            sendCycleLasSensitivity(context, currentLas)
        }
        "homelight_delay" -> {
            val currentDelay = runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY) }.getOrNull()
            sendCycleHomelightDelay(context, currentDelay)
        }
        "driver_unlock_mode" -> {
            val currentMode = runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE) }.getOrNull()
            sendCycleDriverUnlockMode(context, currentMode)
        }
        "drive_mode" -> {
            val currentMode = runCatching { MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE) }.getOrNull() ?: 1
            val nextMode = when (currentMode) {
                1 -> 2
                2 -> 3
                else -> 1
            }
            sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE, nextMode)
        }
        "wiper_maintenance" -> sendToggleWiperMaintenance(context)
        "door_autolock" -> sendToggleDoorAutoLock(context)
        "door_ignoff_unlock" -> sendToggleDoorIgnOffUnlock(context)
        "mirror_autofold" -> sendToggleMirrorAutofold(context)
        "mirror_reverse_turn" -> sendToggleMirrorReverseTurn(context)
        "welcome_lamp" -> sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.WELCOME_LAMP)
        "wireless_charging" -> sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.CHG_WIRELESS_SWITCH)
        "car_wash_mode" -> sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.VEHICLE_VEHWASH_MODESET)
        "welcome_seat" -> sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.WELCOME_SEAT)
        "window_autoclose" -> sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.WINDOW_AUTOCLOSE_SWITCH)

        "dcl_log" -> {
            if (DclLogRecorder.isLoggingActive()) {
                DclLogRecorder.stopLogging("Stopped by user via top shade tile")
                Toast.makeText(context, "Запись лога остановлена", Toast.LENGTH_SHORT).show()
            } else {
                if (!DclLogRecorder.hasStoragePermission(context)) {
                    DclLogRecorder.openStorageSettings(context)
                } else {
                    DclLogRecorder.startSession(context, durationMs = 300_000L)
                    Toast.makeText(context, "Запись лога запущена (5 мин)", Toast.LENGTH_SHORT).show()
                }
            }
        }
        "car_dialer" -> {
            onClose()
            launchAppFromWidget(context, "com.autopai.car.dialer")
        }
        "radio_app" -> {
            onClose()
            launchAppFromWidget(context, "com.wt.multimedia.local")
        }
        "theme" -> LauncherThemeState.setDarkTheme(context, !LauncherThemeState.darkTheme)
        "wifi" -> {
            runCatching {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                onClose()
            }
        }
        "screen_off" -> {
            onClose()
            BlankWallpaperService.setSystemBlackWallpaper(context)
        }
        "reboot_hu" -> {
            onClose()
            context.sendBroadcast(Intent(BackgroundService.ACTION_TBOX_REBOOT).setPackage(context.packageName))
        }
        "avm_camera" -> {
            onClose()
            launchLauncherAppEmbedded(context, "com.mengbo.avm")
        }
        else -> {
            if (spec.actionKey.startsWith("tbm_auto:")) {
                onClose()
                val parts = spec.actionKey.removePrefix("tbm_auto:").split(":")
                val autoId = parts.getOrNull(0) ?: ""
                if (autoId.isNotBlank()) {
                    val tbmRepo = TbmRepositoryProvider.get(context)
                    CoroutineScope(Dispatchers.Main).launch {
                        tbmRepo.runAutomation(autoId)
                    }
                }
            } else if (spec.type == TopShadeTileType.SHORTCUT) {
                onClose()
                launchAppFromWidget(context, spec.actionKey)
            }
        }
    }
}

private fun findFirstAvailableCell(
    tiles: List<TopShadeTileSpec>,
    spanX: Int,
    spanY: Int,
): Pair<Int, Int> {
    val numCols = LauncherTopShadeConfigStore.GRID_COLS
    val numRows = LauncherTopShadeConfigStore.GRID_ROWS
    val occupied = Array(numRows) { BooleanArray(numCols) }

    for (t in tiles) {
        for (r in t.row until (t.row + t.spanY).coerceAtMost(numRows)) {
            for (c in t.col until (t.col + t.spanX).coerceAtMost(numCols)) {
                if (r in 0 until numRows && c in 0 until numCols) occupied[r][c] = true
            }
        }
    }
    for (r in 0 until numRows) {
        for (c in 0..(numCols - spanX).coerceAtLeast(0)) {
            var fits = true
            for (dr in 0 until spanY) {
                for (dc in 0 until spanX) {
                    if (r + dr >= numRows || c + dc >= numCols || occupied[r + dr][c + dc]) {
                        fits = false
                    }
                }
            }
            if (fits) return Pair(c, r)
        }
    }
    return Pair(0, 0)
}

@Composable
private fun AddTopShadeTileModal(
    isDark: Boolean,
    onDismiss: () -> Unit,
    onAdd: (TopShadeTileSpec) -> Unit,
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableIntStateOf(0) } // 0: Auto, 1: Climate, 2: System, 3: Apps, 4: TBM (optional)

    val tbmRepo = remember(context) { TbmRepositoryProvider.get(context) }
    val tbmConnectionState by tbmRepo.connectionState.collectAsStateWithLifecycle()
    val tbmAutomations by tbmRepo.automations.collectAsStateWithLifecycle()

    val isTbmConnected = tbmConnectionState is TbmConnectionState.Connected
    val hasTbmAutomations = isTbmConnected && tbmAutomations.isNotEmpty()

    val categoryTabs = remember(hasTbmAutomations) {
        if (hasTbmAutomations) {
            listOf("Автомобиль", "Климат", "Система", "Приложения", "Автоматизации TBM")
        } else {
            listOf("Автомобиль", "Климат", "Система", "Приложения")
        }
    }

    val carOptions = remember {
        listOf(
            TopShadeTileSpec("opt_avm_camera", "Камеры 360", TopShadeTileType.CAR_CONTROL, "avm_camera", "ic_launcher_avm", 1, 1),
            TopShadeTileSpec("opt_mirror_fold", "Сложение зеркал", TopShadeTileType.CAR_CONTROL, "mirror_fold", "ic_launcher_mirror", 1, 1),
            TopShadeTileSpec("opt_headlights", "Переключатель фар", TopShadeTileType.CAR_CONTROL, "headlights", "ic_headlights_auto", 1, 1),
            TopShadeTileSpec("opt_trunk", "Багажник", TopShadeTileType.CAR_CONTROL, "trunk_door", "ic_launcher_trunk", 1, 1),
            TopShadeTileSpec("opt_windows", "Окна", TopShadeTileType.CAR_CONTROL, "windows", "ic_widget_side_window", 1, 1),
            TopShadeTileSpec("opt_seat_memory_1", "Память DRV1", TopShadeTileType.CAR_CONTROL, "seat_memory_1", "ic_seat_memory_drv1", 1, 1),
            TopShadeTileSpec("opt_seat_memory_2", "Память DRV2", TopShadeTileType.CAR_CONTROL, "seat_memory_2", "ic_seat_memory_drv2", 1, 1),
            TopShadeTileSpec("opt_seat_memory_3", "Память DRV3", TopShadeTileType.CAR_CONTROL, "seat_memory_3", "ic_seat_memory_drv3", 1, 1),
            TopShadeTileSpec("opt_seat_adjust", "Настройка сиденья", TopShadeTileType.CAR_CONTROL, "seat_adjust", "ic_seat_memory_btn", 1, 1),
            TopShadeTileSpec("opt_pass_seat_adjust", "Пасс. сиденье", TopShadeTileType.CAR_CONTROL, "passenger_seat_adjust", "ic_seat_memory_btn", 1, 1),
            TopShadeTileSpec("opt_acc", "ACC Круиз", TopShadeTileType.CAR_CONTROL, "acc", "ic_launcher_cruise", 1, 1),
            TopShadeTileSpec("opt_ngp", "NGP Ассистент", TopShadeTileType.CAR_CONTROL, "ngp", "ic_adas_steering_wheel", 1, 1),
            TopShadeTileSpec("opt_adas_dist", "Дистанция ACC", TopShadeTileType.CAR_CONTROL, "adas_dist", null, 1, 1),
            TopShadeTileSpec("opt_smart_acc", "Умный ACC", TopShadeTileType.CAR_CONTROL, "smart_acc", null, 1, 1),
            TopShadeTileSpec("opt_ldw", "LDW Полоса", TopShadeTileType.CAR_CONTROL, "ldw", "ic_adas_lka", 1, 1),
            TopShadeTileSpec("opt_lka", "LKA Удержание", TopShadeTileType.CAR_CONTROL, "lka", "ic_adas_lka", 1, 1),
            TopShadeTileSpec("opt_fcw", "FCW Дистанция", TopShadeTileType.CAR_CONTROL, "fcw", "ic_fcw_arcs", 1, 1),
            TopShadeTileSpec("opt_bsd", "Слепые зоны BSD", TopShadeTileType.CAR_CONTROL, "bsd", "ic_bsd_blindspot", 1, 1),
            TopShadeTileSpec("opt_dow", "Двери DOW", TopShadeTileType.CAR_CONTROL, "dow", "ic_dow_door", 1, 1),
            TopShadeTileSpec("opt_parking_radar", "Парктроник", TopShadeTileType.CAR_CONTROL, "parking_radar", "ic_widget_parking_radar", 1, 1),
            TopShadeTileSpec("opt_esc_off", "ESP Стабилизация", TopShadeTileType.CAR_CONTROL, "esc_off", "ic_esp_icon", 1, 1),
            TopShadeTileSpec("opt_hdc", "Спуск с горы HDC", TopShadeTileType.CAR_CONTROL, "hdc", "ic_launcher_hdc", 1, 1),
            TopShadeTileSpec("opt_avh", "Автохолд HLD", TopShadeTileType.CAR_CONTROL, "avh", "ic_hold_brake", 1, 1),
            TopShadeTileSpec("opt_hma", "Авто дальний IHBC", TopShadeTileType.CAR_CONTROL, "hma", "ic_high_beam_auto", 1, 1),
            TopShadeTileSpec("opt_rear_fog", "Задний ПТФ", TopShadeTileType.CAR_CONTROL, "rear_fog", "ic_launcher_rear_fog", 1, 1),
            TopShadeTileSpec("opt_wiper_maintenance", "Сервис дворников", TopShadeTileType.CAR_CONTROL, "wiper_maintenance", "ic_wiper", 1, 1),
            TopShadeTileSpec("opt_wiper_sensitivity", "Дворники Авто", TopShadeTileType.CAR_CONTROL, "wiper_sensitivity", "ic_windshield", 1, 1),
            TopShadeTileSpec("opt_drive_mode", "Режим движения", TopShadeTileType.CAR_CONTROL, "drive_mode", null, 1, 1),
        )
    }

    val climateOptions = remember {
        listOf(
            TopShadeTileSpec("opt_hvac_power", "Питание климата", TopShadeTileType.CAR_CONTROL, "hvac_power", "ic_widget_hvac_power", 1, 1),
            TopShadeTileSpec("opt_hvac_ac", "Кондиционер AC", TopShadeTileType.CAR_CONTROL, "hvac_ac", "ic_widget_hvac_ac", 1, 1),
            TopShadeTileSpec("opt_hvac_auto", "Климат AUTO", TopShadeTileType.CAR_CONTROL, "hvac_auto", null, 1, 1),
            TopShadeTileSpec("opt_hvac_sync", "Синхронизация SYNC", TopShadeTileType.CAR_CONTROL, "hvac_sync", null, 1, 1),
            TopShadeTileSpec("opt_temp_driver", "Температура водителя", TopShadeTileType.CAR_CONTROL, "temp_driver", null, 2, 1),
            TopShadeTileSpec("opt_temp_pass", "Температура пассажира", TopShadeTileType.CAR_CONTROL, "temp_pass", null, 2, 1),
            TopShadeTileSpec("opt_hvac_heater_level", "Уровень печки", TopShadeTileType.CAR_CONTROL, "hvac_heater_level", null, 2, 1),
            TopShadeTileSpec("opt_hvac_pm25", "Очистка PM 2.5", TopShadeTileType.CAR_CONTROL, "hvac_pm25", "ic_widget_pm25_on", 1, 1),
            TopShadeTileSpec("opt_hvac_fan_speed", "Скорость вентилятора", TopShadeTileType.CAR_CONTROL, "hvac_fan_speed", "ic_widget_fan", 1, 1),
            TopShadeTileSpec("opt_hvac_fan_direction", "Направление обдува", TopShadeTileType.CAR_CONTROL, "hvac_fan_direction", "ic_widget_fan_face_feet", 1, 1),
            TopShadeTileSpec("opt_seat_heat_left", "Подогрев водителя", TopShadeTileType.CAR_CONTROL, "seat_heat_left", "ic_widget_seat_heat_left", 1, 1),
            TopShadeTileSpec("opt_seat_vent_left", "Вентиляция водителя", TopShadeTileType.CAR_CONTROL, "seat_vent_left", "ic_widget_seat_vent_left", 1, 1),
            TopShadeTileSpec("opt_seat_heat_right", "Подогрев пассажира", TopShadeTileType.CAR_CONTROL, "seat_heat_right", "ic_widget_seat_heat_right", 1, 1),
            TopShadeTileSpec("opt_seat_vent_right", "Вентиляция пассажира", TopShadeTileType.CAR_CONTROL, "seat_vent_right", "ic_widget_seat_vent_right", 1, 1),
            TopShadeTileSpec("opt_seat_heat_rear_left", "Подогрев зад. лев.", TopShadeTileType.CAR_CONTROL, "seat_heat_rear_left", "ic_widget_seat_heat_left", 1, 1),
            TopShadeTileSpec("opt_seat_heat_rear_right", "Подогрев зад. прав.", TopShadeTileType.CAR_CONTROL, "seat_heat_rear_right", "ic_widget_seat_heat_right", 1, 1),
            TopShadeTileSpec("opt_hvac_recirc", "Рециркуляция", TopShadeTileType.CAR_CONTROL, "hvac_recirc", "ic_widget_hvac_recirc_on", 1, 1),
            TopShadeTileSpec("opt_steering_heat", "Подогрев руля", TopShadeTileType.CAR_CONTROL, "steering_heat", "ic_widget_steering_wheel_heat", 1, 1),
            TopShadeTileSpec("opt_windscreen_heat", "Подогрев лобового", TopShadeTileType.CAR_CONTROL, "windscreen_heat", "ic_widget_front_windscreen_heat", 1, 1),
            TopShadeTileSpec("opt_front_defrost", "Обдув лобового", TopShadeTileType.CAR_CONTROL, "front_defrost", "ic_widget_hvac_defroster_front", 1, 1),
            TopShadeTileSpec("opt_rear_defrost", "Обогрев заднего стекла", TopShadeTileType.CAR_CONTROL, "rear_defrost", "ic_widget_rear_window_mirrors_defrost", 1, 1),
        )
    }

    val systemOptions = remember {
        listOf(
            TopShadeTileSpec("opt_brightness", "Яркость", TopShadeTileType.SYSTEM_TOGGLE, "brightness", null, 2, 1),
            TopShadeTileSpec("opt_vol_media", "Громкость Медиа", TopShadeTileType.SYSTEM_TOGGLE, "vol_media", null, 2, 1),
            TopShadeTileSpec("opt_vol_phone", "Громкость Звонков", TopShadeTileType.SYSTEM_TOGGLE, "vol_phone", null, 2, 1),
            TopShadeTileSpec("opt_vol_navi", "Громкость Навигации", TopShadeTileType.SYSTEM_TOGGLE, "vol_navi", null, 2, 1),
            TopShadeTileSpec("opt_vol_voice", "Громкость Голоса", TopShadeTileType.SYSTEM_TOGGLE, "vol_voice", null, 2, 1),
            TopShadeTileSpec("opt_headrest", "Подголовник", TopShadeTileType.SYSTEM_TOGGLE, "headrest_speaker", null, 1, 1),
            TopShadeTileSpec("opt_dcl_log", "Запись лога (5 мин)", TopShadeTileType.SYSTEM_TOGGLE, "dcl_log", "menu_icon_log", 1, 1),
            TopShadeTileSpec("opt_radio_app", "Радио", TopShadeTileType.SYSTEM_TOGGLE, "radio_app", "radio_vtkb246z55e9", 1, 1),
            TopShadeTileSpec("opt_car_dialer", "Телефон", TopShadeTileType.SYSTEM_TOGGLE, "car_dialer", "ic_widget_phone", 1, 1),
            TopShadeTileSpec("opt_theme", "Тема День/Ночь", TopShadeTileType.SYSTEM_TOGGLE, "theme", "ic_widget_theme", 1, 1),
            TopShadeTileSpec("opt_wifi", "Настройки Wi-Fi", TopShadeTileType.SYSTEM_TOGGLE, "wifi", "ic_widget_wifi", 1, 1),
            TopShadeTileSpec("opt_screen_off", "Экран выкл", TopShadeTileType.SYSTEM_TOGGLE, "screen_off", "ic_widget_screen_off", 1, 1),
            TopShadeTileSpec("opt_reboot_hu", "Перезагрузка ГУ", TopShadeTileType.SYSTEM_TOGGLE, "reboot_hu", "ic_widget_reboot", 1, 1),
        )
    }

    val appOptions = remember(context) {
        val pm = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        pm.queryIntentActivities(launchIntent, 0)
            .mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                val label = resolveInfo.loadLabel(pm).toString().ifBlank { pkg }
                TopShadeTileSpec(
                    id = "app_$pkg",
                    title = label,
                    type = TopShadeTileType.SHORTCUT,
                    actionKey = pkg,
                    iconResName = null,
                    spanX = 1,
                    spanY = 1,
                )
            }
            .distinctBy { it.actionKey }
            .sortedBy { it.title.lowercase() }
    }

    val tbmAutomationOptions = remember(tbmAutomations) {
        tbmAutomations.map { autoItem ->
            TopShadeTileSpec(
                id = "tbm_auto_${autoItem.id}",
                title = autoItem.name,
                type = TopShadeTileType.SHORTCUT,
                actionKey = "tbm_auto:${autoItem.id}:${autoItem.name}",
                iconResName = null,
                spanX = 1,
                spanY = 1,
            )
        }
    }

    val cardBg = if (isDark) LauncherColors.CanvasDark else Color(0xFFF9FAFB)
    val textColor = if (isDark) Color.White else Color(0xFF111827)
    val itemBg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Добавить элемент",
                        color = textColor,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = textColor,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                // Category Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    categoryTabs.forEachIndexed { idx, title ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp)
                                .clip(RoundedCornerShape(38.dp))
                                .background(if (selectedCategory == idx) LauncherColors.AccentBlue else if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f))
                                .clickable { selectedCategory = idx }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = title,
                                color = if (selectedCategory == idx) Color.White else textColor,
                                fontSize = 33.sp,
                                lineHeight = 36.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Selected Category List
                val currentList = when (selectedCategory) {
                    0 -> carOptions
                    1 -> climateOptions
                    2 -> systemOptions
                    3 -> appOptions
                    4 -> tbmAutomationOptions
                    else -> emptyList()
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 170.dp),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(currentList, key = { it.id }) { option ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(145.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(itemBg)
                                .border(1.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                .clickable { onAdd(option) }
                                .padding(10.dp),
                        ) {
                            if (option.spanX > 1 || option.spanY > 1) {
                                Text(
                                    text = "${option.spanX}×${option.spanY}",
                                    color = textColor.copy(alpha = 0.65f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .background(if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(26.dp)
                                    .background(LauncherColors.AccentBlue.copy(alpha = 0.18f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add",
                                    tint = LauncherColors.AccentBlue,
                                    modifier = Modifier.size(16.dp),
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                val iconResId = remember(option.iconResName) {
                                    option.iconResName?.let { name ->
                                        context.resources.getIdentifier(name, "drawable", context.packageName).takeIf { it != 0 }
                                    }
                                }
                                val vectorIcon: ImageVector? = when (option.actionKey) {
                                    "brightness" -> IconBrightnessSun
                                    "vol_media" -> IconVolumeSpeaker
                                    "vol_phone" -> IconPhoneVector
                                    "vol_navi" -> IconNaviVector
                                    "vol_voice" -> IconMicVector
                                    "headrest_speaker" -> IconVolumeSpeaker
                                    else -> null
                                }

                                val isSeatHeat = option.actionKey.startsWith("seat_heat_")
                                val isSeatVent = option.actionKey.startsWith("seat_vent_")
                                val isAuto = option.actionKey == "hvac_auto"
                                val isSync = option.actionKey == "hvac_sync"
                                val isTempDriver = option.actionKey == "temp_driver"
                                val isTempPass = option.actionKey == "temp_pass"
                                val isHeaterLevel = option.actionKey == "hvac_heater_level"
                                val isFanSpeed = option.actionKey == "hvac_fan_speed"

                                if (isSeatHeat) {
                                    LauncherSeatHeatIcon(
                                        raw = 4,
                                        mirrored = option.actionKey.endsWith("_right"),
                                        badgeText = if (option.actionKey.contains("_rear_")) "REAR" else null,
                                        iconSizeDp = 48.dp,
                                        seatSizeDp = 40.dp,
                                    )
                                } else if (isSeatVent) {
                                    LauncherSeatVentIcon(
                                        raw = 7,
                                        mirrored = option.actionKey.endsWith("_right"),
                                        iconSizeDp = 48.dp,
                                        seatSizeDp = 40.dp,
                                    )
                                } else if (isAuto || isSync) {
                                    Box(
                                        modifier = Modifier.size(46.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = if (isSync) "SYNC" else "AUTO",
                                            color = if (isDark) Color.White else Color(0xFF374151),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                } else if (isTempDriver || isTempPass) {
                                    Box(
                                        modifier = Modifier.size(46.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = if (isTempDriver) "t° В" else "t° П",
                                            color = if (isDark) Color.White else Color(0xFF374151),
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                } else if (isHeaterLevel) {
                                    Box(
                                        modifier = Modifier.size(46.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "Печка",
                                            color = if (isDark) Color.White else Color(0xFF374151),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                } else if (isFanSpeed) {
                                    Box(
                                        modifier = Modifier.size(46.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        LauncherFanSpeedIcon(
                                            speedLevel = 4,
                                            activeColor = HvacOnColor,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_widget_fan),
                                            contentDescription = null,
                                            colorFilter = ColorFilter.tint(if (isDark) Color.White else Color(0xFF374151)),
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                } else if (option.actionKey == "adas_dist") {
                                    ThreeChevronsIcon(
                                        level = 3,
                                        activeColor = LauncherColors.AccentCyan,
                                        modifier = Modifier.size(42.dp),
                                    )
                                } else if (option.actionKey == "smart_acc") {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .border(3.dp, Color(0xFFEF4444), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "60",
                                            color = Color.Black,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                        )
                                    }
                                } else if (option.actionKey == "drive_mode") {
                                    Box(
                                        modifier = Modifier.size(46.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "DRIVE",
                                            color = if (isDark) Color.White else Color(0xFF374151),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                } else if (iconResId != null) {
                                    Image(
                                        painter = painterResource(id = iconResId),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(if (isDark) Color.White else Color(0xFF374151)),
                                        modifier = Modifier
                                            .size(46.dp)
                                            .then(
                                                if (option.actionKey == "passenger_seat_adjust") {
                                                    Modifier.graphicsLayer { scaleX = -1f }
                                                } else Modifier
                                            ),
                                    )
                                } else if (vectorIcon != null) {
                                    Icon(
                                        imageVector = vectorIcon,
                                        contentDescription = null,
                                        tint = if (isDark) Color.White else Color(0xFF374151),
                                        modifier = Modifier.size(42.dp),
                                    )
                                } else if (option.type == TopShadeTileType.SHORTCUT && !option.actionKey.startsWith("tbm_auto:")) {
                                    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = option.actionKey) {
                                        value = withContext(Dispatchers.IO) {
                                            runCatching {
                                                LauncherAppIconLoader.fromPackage(context, option.actionKey, 48)
                                            }.getOrNull()
                                        }
                                    }
                                    if (iconBitmap != null) {
                                        Image(
                                            bitmap = iconBitmap!!,
                                            contentDescription = null,
                                            modifier = Modifier.size(46.dp),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = option.title,
                                    color = textColor,
                                    fontSize = 18.sp,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
