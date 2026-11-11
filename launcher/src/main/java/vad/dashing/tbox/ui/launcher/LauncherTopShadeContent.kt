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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.MbCanSeatModeState
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.tbm.TbmConnectionState
import vad.dashing.tbox.tbm.TbmRepositoryProvider
import vad.dashing.tbox.ui.LauncherAppIconLoader
import vad.dashing.tbox.ui.launchAppFromWidget
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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
    val shadeBgColor = if (isDark) Color(0xFF0F1115).copy(alpha = 0.96f) else Color(0xFFF3F4F6).copy(alpha = 0.98f)
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
    val steeringHeat by UniversalCanRepository.steeringWheelHeatState.collectAsStateWithLifecycle()
    val windscreenHeat by UniversalCanRepository.frontWindscreenHeatState.collectAsStateWithLifecycle()
    val rearDefrost by UniversalCanRepository.hvacDefrosterState.collectAsStateWithLifecycle()
    val hvacAuto by UniversalCanRepository.hvacAutoState.collectAsStateWithLifecycle()
    val hvacRecirc by UniversalCanRepository.hvacAirRecirculationState.collectAsStateWithLifecycle()
    val headrestMode by PlatformAudioRepository.headrestMode.collectAsStateWithLifecycle()

    val vehicleControls = rememberLauncherVehicleControlSnapshot(enabled = true)

    val isActive = remember(spec, flSeatMode, frSeatMode, steeringHeat, windscreenHeat, rearDefrost, hvacAuto, hvacRecirc, headrestMode, vehicleControls) {
        when (spec.actionKey) {
            "seat_heat_left" -> flSeatMode is MbCanSeatModeState.Heat && (flSeatMode as MbCanSeatModeState.Heat).level > 0
            "seat_vent_left" -> flSeatMode is MbCanSeatModeState.Vent && (flSeatMode as MbCanSeatModeState.Vent).level > 0
            "seat_heat_right" -> frSeatMode is MbCanSeatModeState.Heat && (frSeatMode as MbCanSeatModeState.Heat).level > 0
            "seat_vent_right" -> frSeatMode is MbCanSeatModeState.Vent && (frSeatMode as MbCanSeatModeState.Vent).level > 0
            "steering_heat" -> steeringHeat is MbCanBinaryState.On
            "windscreen_heat" -> windscreenHeat is MbCanBinaryState.On
            "rear_defrost" -> rearDefrost is MbCanBinaryState.On
            "hvac_auto" -> hvacAuto is MbCanBinaryState.On
            "hvac_recirc" -> hvacRecirc is MbCanBinaryState.On
            "theme" -> LauncherThemeState.darkTheme
            "wifi" -> (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true
            "headrest_speaker" -> headrestMode != 3
            "avh" -> vehicleControls.avh == true
            "hdc" -> vehicleControls.hdc == true
            "esc_off" -> vehicleControls.escOff == true
            "parking_radar" -> vehicleControls.parkingRadar == true
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
            "fcw" -> vehicleControls.fcw == true
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

    val containerBg = if (isDragging) {
        LauncherColors.AccentBlue.copy(alpha = 0.65f)
    } else if (isActive) {
        LauncherColors.AccentBlue.copy(alpha = if (isDark) 0.35f else 0.25f)
    } else {
        if (isDark) Color.White.copy(alpha = 0.07f) else Color.White
    }
    val borderColor = if (isDragging || isActive) {
        LauncherColors.AccentBlue
    } else {
        if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)
    }

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
                    else -> {
                        if (spec.type == TopShadeTileType.TELEMETRY) {
                            TelemetryTileContent(spec = spec, isDark = isDark)
                        } else {
                            StandardTileContent(spec = spec, isActive = isActive, isDark = isDark, vehicleControls = vehicleControls)
                        }
                    }
                }

                if (isEditMode) {
                    val isSliderTile = spec.actionKey == "brightness" ||
                        spec.actionKey.startsWith("vol_") ||
                        spec.actionKey == "volume"

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
                                    if ((isSliderTile && spec.spanX > 2) || (!isSliderTile && spec.spanX > 1)) LauncherColors.AccentBlue else if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.15f),
                                    CircleShape,
                                )
                                .clickable {
                                    val nextSpanX = if (isSliderTile) {
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
                Text("Яркость: $percent%", color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                Text("Яркость: $percent%", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                Text("${channel.title}: $currentVolume / ${channel.maxVal}", color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                Text("${channel.title}: $currentVolume / ${channel.maxVal}", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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

    val iconSize = if (spec.spanY >= 2) 68.dp else 34.dp
    val fontSize = if (spec.spanY >= 2) 22.sp else 12.sp
    val textColor = if (isDark) Color.White else Color(0xFF1F2937)
    val iconTint = if (isActive) LauncherColors.AccentBlue else if (isDark) Color.White else Color(0xFF374151)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = IconVolumeSpeaker,
            contentDescription = modeTitle,
            tint = iconTint,
            modifier = Modifier.size(iconSize),
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = modeTitle,
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = if (spec.spanY >= 2) 3 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StandardTileContent(
    spec: TopShadeTileSpec,
    isActive: Boolean,
    isDark: Boolean,
    vehicleControls: LauncherVehicleControlSnapshot = LauncherVehicleControlSnapshot(),
) {
    val context = LocalContext.current

    val displayTitle = remember(spec, vehicleControls) {
        when (spec.actionKey) {
            "fcw" -> when (vehicleControls.fcwOption) {
                1 -> "FCW: Близко"
                2 -> "FCW: Средне"
                3 -> "FCW: Далеко"
                else -> if (vehicleControls.fcw == true) "FCW: Вкл" else "FCW: Выкл"
            }
            "las" -> when (vehicleControls.lasSensitivity) {
                1 -> "LAS: Предупреждение"
                2 -> "LAS: Ассистент"
                else -> if ((vehicleControls.lasSensitivity ?: 0) > 0) "LAS: Вкл" else "LAS: Выкл"
            }
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

    val iconSize = if (spec.spanY >= 2) 68.dp else 34.dp
    val fontSize = if (spec.spanY >= 2) 22.sp else 12.sp

    val textColor = if (isDark) Color.White else Color(0xFF1F2937)
    val iconTint = if (isActive) {
        LauncherColors.AccentBlue
    } else {
        if (isDark) Color.White else Color(0xFF374151)
    }

    val iconRes = remember(spec.iconResName) {
        spec.iconResName?.let { name ->
            context.resources.getIdentifier(name, "drawable", context.packageName).takeIf { it != 0 }
        }
    }
    val appIconBitmap = remember(spec.actionKey, spec.type) {
        if (spec.type == TopShadeTileType.SHORTCUT) {
            LauncherAppIconLoader.fromPackage(context, spec.actionKey, 96)
        } else null
    }

    if (spec.spanX >= 2 && spec.spanY == 1) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (iconRes != null) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = displayTitle,
                    colorFilter = ColorFilter.tint(iconTint),
                    modifier = Modifier.size(iconSize),
                )
            } else if (appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = displayTitle,
                    modifier = Modifier.size(iconSize),
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = displayTitle,
                color = textColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (iconRes != null) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = displayTitle,
                    colorFilter = ColorFilter.tint(iconTint),
                    modifier = Modifier.size(iconSize),
                )
            } else if (appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = displayTitle,
                    modifier = Modifier.size(iconSize),
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = displayTitle,
                color = textColor,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = if (spec.spanY >= 2) 3 else 2,
                overflow = TextOverflow.Ellipsis,
            )
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

    val labelFontSize = if (spec.spanY >= 2) 18.sp else 10.sp
    val valueFontSize = if (spec.spanY >= 2) 24.sp else 12.sp
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
        "seat_heat_left" -> sendCycleFrontSeatHeat(context, MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH, null)
        "seat_vent_left" -> sendCycleFrontSeatVent(context, MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH, null)
        "seat_heat_right" -> sendCycleFrontSeatHeat(context, MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH, null)
        "seat_vent_right" -> sendCycleFrontSeatVent(context, MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH, null)
        "seat_heat_rear_left" -> sendCycleFrontSeatHeat(context, MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH, null)
        "seat_heat_rear_right" -> sendCycleFrontSeatHeat(context, MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH, null)
        "steering_heat" -> sendToggleSteeringWheelHeat(context)
        "windscreen_heat" -> sendToggleFrontWindscreenHeat(context)
        "rear_defrost" -> sendToggleRearWindowMirrorsDefrost(context)
        "hvac_auto" -> sendToggleHvacAuto(context)
        "hvac_recirc" -> sendToggleHvacAirRecirculation(context)
        "hvac_power" -> sendToggleHvacPower(context)
        "hvac_ac" -> sendToggleHvacAc(context)
        "hvac_sync" -> sendToggleHvacSync(context)
        "hvac_pm25" -> sendToggleHvacPm25(context)
        "hvac_fan_speed" -> sendCycleHvacFanSpeed(context)
        "hvac_fan_direction" -> sendCycleHvacFanDirection(context)
        "front_defrost" -> sendToggleHvacDefrosterFront(context)
        "headlights" -> sendCycleHeadlightsSwitch(context, null)

        // ADAS & Vehicle actions
        "trunk_door" -> sendOpenCloseTrunk(context)
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
    var selectedCategory by remember { mutableIntStateOf(0) } // 0: Auto, 1: Climate, 2: System, 3: Telemetry, 4: Apps, 5: TBM (optional)

    val tbmRepo = remember(context) { TbmRepositoryProvider.get(context) }
    val tbmConnectionState by tbmRepo.connectionState.collectAsStateWithLifecycle()
    val tbmAutomations by tbmRepo.automations.collectAsStateWithLifecycle()

    val isTbmConnected = tbmConnectionState is TbmConnectionState.Connected
    val hasTbmAutomations = isTbmConnected && tbmAutomations.isNotEmpty()

    val categoryTabs = remember(hasTbmAutomations) {
        if (hasTbmAutomations) {
            listOf("Автомобиль", "Климат", "Система", "Телеметрия", "Приложения", "Автоматизации TBM")
        } else {
            listOf("Автомобиль", "Климат", "Система", "Телеметрия", "Приложения")
        }
    }

    val carOptions = remember {
        listOf(
            TopShadeTileSpec("opt_avm_camera", "Камеры 360", TopShadeTileType.CAR_CONTROL, "avm_camera", "ic_launcher_avm", 1, 1),
            TopShadeTileSpec("opt_trunk_door", "Багажник", TopShadeTileType.CAR_CONTROL, "trunk_door", "ic_widget_trunk", 1, 1),
            TopShadeTileSpec("opt_avh", "Автохолд (AVH)", TopShadeTileType.CAR_CONTROL, "avh", "ic_widget_avh", 1, 1),
            TopShadeTileSpec("opt_hdc", "Помощь при спуске (HDC)", TopShadeTileType.CAR_CONTROL, "hdc", "ic_widget_hdc", 1, 1),
            TopShadeTileSpec("opt_esc_off", "Отключение ESP (ESC Off)", TopShadeTileType.CAR_CONTROL, "esc_off", "ic_widget_esp_off", 1, 1),
            TopShadeTileSpec("opt_parking_radar", "Парковочный радар", TopShadeTileType.CAR_CONTROL, "parking_radar", "ic_widget_parking_radar", 1, 1),
            TopShadeTileSpec("opt_fcw", "Предупреждение FCW", TopShadeTileType.CAR_CONTROL, "fcw", "ic_widget_fcw", 1, 1),
            TopShadeTileSpec("opt_las", "Удержание в полосе (LAS)", TopShadeTileType.CAR_CONTROL, "las", "ic_widget_las", 1, 1),
            TopShadeTileSpec("opt_acc_autobrake", "Аварийное торможение (AEB)", TopShadeTileType.CAR_CONTROL, "acc_autobrake", "ic_widget_aeb", 1, 1),
            TopShadeTileSpec("opt_drive_mode", "Режим вождения", TopShadeTileType.CAR_CONTROL, "drive_mode", "ic_widget_drive_mode", 1, 1),
            TopShadeTileSpec("opt_wiper_maintenance", "Сервисный режим дворников", TopShadeTileType.CAR_CONTROL, "wiper_maintenance", "ic_widget_wiper", 1, 1),
            TopShadeTileSpec("opt_door_autolock", "Автоблокировка дверей", TopShadeTileType.CAR_CONTROL, "door_autolock", "ic_widget_door_lock", 1, 1),
            TopShadeTileSpec("opt_door_ignoff_unlock", "Разблокировка при заглушении", TopShadeTileType.CAR_CONTROL, "door_ignoff_unlock", "ic_widget_door_lock", 1, 1),
            TopShadeTileSpec("opt_mirror_autofold", "Автосложение зеркал", TopShadeTileType.CAR_CONTROL, "mirror_autofold", "ic_widget_mirror_fold", 1, 1),
            TopShadeTileSpec("opt_mirror_reverse_turn", "Наклон зеркал при заднем ходе", TopShadeTileType.CAR_CONTROL, "mirror_reverse_turn", "ic_widget_mirror_fold", 1, 1),
            TopShadeTileSpec("opt_homelight_delay", "Задержка фар (Follow Me Home)", TopShadeTileType.CAR_CONTROL, "homelight_delay", "ic_headlights_auto", 1, 1),
            TopShadeTileSpec("opt_driver_unlock_mode", "Режим разблокировки дверей", TopShadeTileType.CAR_CONTROL, "driver_unlock_mode", "ic_widget_door_lock", 1, 1),
            TopShadeTileSpec("opt_welcome_lamp", "Приветственный свет", TopShadeTileType.CAR_CONTROL, "welcome_lamp", "ic_headlights_auto", 1, 1),
            TopShadeTileSpec("opt_wireless_charging", "Беспроводная зарядка", TopShadeTileType.CAR_CONTROL, "wireless_charging", "ic_widget_battery", 1, 1),
            TopShadeTileSpec("opt_car_wash_mode", "Режим мойки", TopShadeTileType.CAR_CONTROL, "car_wash_mode", "ic_widget_wiper", 1, 1),
            TopShadeTileSpec("opt_welcome_seat", "Комфортная посадка", TopShadeTileType.CAR_CONTROL, "welcome_seat", "ic_widget_seat_heat_left_3", 1, 1),
            TopShadeTileSpec("opt_window_autoclose", "Автозакрытие окон", TopShadeTileType.CAR_CONTROL, "window_autoclose", "ic_widget_mirror_fold", 1, 1),
        )
    }

    val climateOptions = remember {
        listOf(
            TopShadeTileSpec("opt_hvac_power", "Питание климата", TopShadeTileType.CAR_CONTROL, "hvac_power", "ic_widget_hvac_power", 1, 1),
            TopShadeTileSpec("opt_hvac_ac", "Кондиционер AC", TopShadeTileType.CAR_CONTROL, "hvac_ac", "ic_widget_hvac_ac", 1, 1),
            TopShadeTileSpec("opt_hvac_auto", "Климат AUTO", TopShadeTileType.CAR_CONTROL, "hvac_auto", "ic_widget_hvac_ac", 1, 1),
            TopShadeTileSpec("opt_hvac_sync", "Синхронизация SYNC", TopShadeTileType.CAR_CONTROL, "hvac_sync", "ic_widget_hvac_ac", 1, 1),
            TopShadeTileSpec("opt_temp_driver", "Температура водителя", TopShadeTileType.CAR_CONTROL, "temp_driver", "ic_widget_hvac_ac", 1, 1),
            TopShadeTileSpec("opt_temp_pass", "Температура пассажира", TopShadeTileType.CAR_CONTROL, "temp_pass", "ic_widget_hvac_ac", 1, 1),
            TopShadeTileSpec("opt_hvac_heater_level", "Уровень печки", TopShadeTileType.CAR_CONTROL, "hvac_heater_level", "ic_widget_fan", 1, 1),
            TopShadeTileSpec("opt_hvac_pm25", "Очистка PM 2.5", TopShadeTileType.CAR_CONTROL, "hvac_pm25", "ic_widget_pm25_on", 1, 1),
            TopShadeTileSpec("opt_hvac_fan_speed", "Скорость вентилятора", TopShadeTileType.CAR_CONTROL, "hvac_fan_speed", "ic_widget_fan", 1, 1),
            TopShadeTileSpec("opt_hvac_fan_direction", "Направление обдува", TopShadeTileType.CAR_CONTROL, "hvac_fan_direction", "ic_widget_fan_face_feet", 1, 1),
            TopShadeTileSpec("opt_seat_heat_left", "Подогрев водителя", TopShadeTileType.CAR_CONTROL, "seat_heat_left", "ic_widget_seat_heat_left_3", 1, 1),
            TopShadeTileSpec("opt_seat_vent_left", "Вентиляция водителя", TopShadeTileType.CAR_CONTROL, "seat_vent_left", "ic_widget_seat_vent_left_3", 1, 1),
            TopShadeTileSpec("opt_seat_heat_right", "Подогрев пассажира", TopShadeTileType.CAR_CONTROL, "seat_heat_right", "ic_widget_seat_heat_right_3", 1, 1),
            TopShadeTileSpec("opt_seat_vent_right", "Вентиляция пассажира", TopShadeTileType.CAR_CONTROL, "seat_vent_right", "ic_widget_seat_vent_right_3", 1, 1),
            TopShadeTileSpec("opt_seat_heat_rear_left", "Подогрев зад. лев.", TopShadeTileType.CAR_CONTROL, "seat_heat_rear_left", "ic_widget_seat_heat_left_3", 1, 1),
            TopShadeTileSpec("opt_seat_heat_rear_right", "Подогрев зад. прав.", TopShadeTileType.CAR_CONTROL, "seat_heat_rear_right", "ic_widget_seat_heat_right_3", 1, 1),
            TopShadeTileSpec("opt_hvac_recirc", "Рециркуляция", TopShadeTileType.CAR_CONTROL, "hvac_recirc", "ic_widget_hvac_air_recirculation", 1, 1),
            TopShadeTileSpec("opt_steering_heat", "Подогрев руля", TopShadeTileType.CAR_CONTROL, "steering_heat", "ic_widget_steering_wheel_heat", 1, 1),
            TopShadeTileSpec("opt_windscreen_heat", "Подогрев лобового", TopShadeTileType.CAR_CONTROL, "windscreen_heat", "ic_widget_front_windscreen_heat", 1, 1),
            TopShadeTileSpec("opt_front_defrost", "Обдув лобового", TopShadeTileType.CAR_CONTROL, "front_defrost", "ic_widget_hvac_defroster_front", 1, 1),
            TopShadeTileSpec("opt_rear_defrost", "Обогрев заднего стекла", TopShadeTileType.CAR_CONTROL, "rear_defrost", "ic_widget_rear_window_mirrors_defrost", 1, 1),
            TopShadeTileSpec("opt_mirror_fold", "Сложение зеркал", TopShadeTileType.CAR_CONTROL, "mirror_fold", "ic_widget_mirror_fold", 1, 1),
            TopShadeTileSpec("opt_headlights", "Переключатель фар", TopShadeTileType.CAR_CONTROL, "headlights", "ic_headlights_auto", 1, 1),
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

    val telemetryOptions = remember {
        listOf(
            TopShadeTileSpec("opt_engine_battery", "ДВС и АКБ", TopShadeTileType.TELEMETRY, "engine_battery", null, 1, 1),
            TopShadeTileSpec("opt_tpms_info", "Давление в шинах", TopShadeTileType.TELEMETRY, "tpms_info", null, 1, 1),
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = textColor,
                        )
                    }
                }

                // Category Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categoryTabs.forEachIndexed { idx, title ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(CircleShape)
                                .background(if (selectedCategory == idx) LauncherColors.AccentBlue else if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f))
                                .clickable { selectedCategory = idx },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = title,
                                color = if (selectedCategory == idx) Color.White else textColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
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
                    3 -> telemetryOptions
                    4 -> appOptions
                    5 -> tbmAutomationOptions
                    else -> emptyList()
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(currentList, key = { it.id }) { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CircleShape)
                                .background(itemBg)
                                .clickable { onAdd(option) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
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

                                if (iconResId != null) {
                                    Image(
                                        painter = painterResource(id = iconResId),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(if (isDark) Color.White else Color(0xFF374151)),
                                        modifier = Modifier.size(28.dp),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                } else if (vectorIcon != null) {
                                    Icon(
                                        imageVector = vectorIcon,
                                        contentDescription = null,
                                        tint = if (isDark) Color.White else Color(0xFF374151),
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
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
                                            modifier = Modifier.size(28.dp),
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                }

                                Text(
                                    text = option.title,
                                    color = textColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add",
                                tint = LauncherColors.AccentBlue,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
