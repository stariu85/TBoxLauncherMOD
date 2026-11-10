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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.MbCanSeatModeState
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.LauncherAppIconLoader
import vad.dashing.tbox.ui.launchAppFromWidget
import vad.dashing.tbox.ui.sendCycleFrontSeatHeat
import vad.dashing.tbox.ui.sendCycleFrontSeatVent
import vad.dashing.tbox.ui.sendToggleFrontWindscreenHeat
import vad.dashing.tbox.ui.sendToggleHvacAirRecirculation
import vad.dashing.tbox.ui.sendToggleHvacAuto
import vad.dashing.tbox.ui.sendToggleMirrorFold
import vad.dashing.tbox.ui.sendToggleRearWindowMirrorsDefrost
import vad.dashing.tbox.ui.sendToggleSteeringWheelHeat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun LauncherTopShadeContent(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val revision by LauncherTopShadeConfigStore.tilesRevisionFlow.collectAsStateWithLifecycle()
    val tiles = remember(context, revision) {
        LauncherTopShadeConfigStore.getTiles(context).toMutableList()
    }
    var isEditMode by remember { mutableStateOf(false) }
    var showAddModal by remember { mutableStateOf(false) }

    // Audio volume state
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    var volumeLevel by remember {
        mutableFloatStateOf(
            audioManager?.let {
                val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val cur = it.getStreamVolume(AudioManager.STREAM_MUSIC)
                cur.toFloat() / max.toFloat()
            } ?: 0.5f
        )
    }

    // Screen brightness state
    var brightnessLevel by remember {
        mutableFloatStateOf(
            runCatching {
                val cur = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                cur.toFloat() / 255f
            }.getOrDefault(0.7f)
        )
    }

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
                .background(LauncherColors.CanvasDark.copy(alpha = 0.96f))
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
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (isEditMode) "Перетаскивайте плитки по сетке ячеек" else currentTimeStr,
                        color = if (isEditMode) LauncherColors.AccentBlue else Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = if (isEditMode) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isEditMode = !isEditMode },
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (isEditMode) LauncherColors.AccentBlue else Color.White.copy(alpha = 0.12f),
                                CircleShape,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Tiles",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.White.copy(alpha = 0.12f), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Close Shade",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            // --- SLIDERS ROW (BRIGHTNESS & VOLUME) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Brightness Slider Box
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Яркость",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = brightnessLevel,
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
                                thumbColor = Color.White,
                                activeTrackColor = LauncherColors.AccentBlue,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Volume Slider Box
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Громкость",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = volumeLevel,
                            onValueChange = { newVal ->
                                volumeLevel = newVal
                                audioManager?.let { am ->
                                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    val target = (newVal * max).toInt().coerceIn(0, max)
                                    am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = LauncherColors.AccentBlue,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // --- CANVAS FREE-FORM TILE GRID ---
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                val density = LocalDensity.current
                val numCols = 6
                val maxRows = (tiles.maxOfOrNull { it.row + it.spanY } ?: 4).coerceAtLeast(6)
                val gapDp = 10.dp
                val gapPx = with(density) { gapDp.toPx() }
                val cellHeightDp = 72.dp
                val cellHeightPx = with(density) { cellHeightDp.toPx() }

                val availableWidthPx = constraints.maxWidth.toFloat() - (numCols - 1) * gapPx
                val cellWidthPx = (availableWidthPx / numCols.toFloat()).coerceAtLeast(10f)

                val canvasHeightDp = with(density) {
                    (maxRows * cellHeightPx + (maxRows - 1) * gapPx).toDp()
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeightDp),
                ) {
                    // 1. Invisible Grid Cell Background Guides (Shown in Edit Mode)
                    if (isEditMode) {
                        for (r in 0 until maxRows) {
                            for (c in 0 until numCols) {
                                val cellXPx = c * (cellWidthPx + gapPx)
                                val cellYPx = r * (cellHeightPx + gapPx)
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(cellXPx.roundToInt(), cellYPx.roundToInt()) }
                                        .size(
                                            width = with(density) { cellWidthPx.toDp() },
                                            height = cellHeightDp,
                                        )
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(14.dp)),
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
                            onRemove = {
                                val next = tiles.toMutableList().apply { removeAt(index) }
                                LauncherTopShadeConfigStore.setTiles(context, next)
                            },
                            onUpdatePosition = { newCol, newRow ->
                                val next = tiles.toMutableList()
                                next[index] = next[index].copy(col = newCol, row = newRow)
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

            // --- EDIT MODE FOOTER CONTROLS ---
            AnimatedVisibility(visible = isEditMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { showAddModal = true },
                        modifier = Modifier.background(LauncherColors.AccentBlue, RoundedCornerShape(12.dp)),
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

                    TextButton(
                        onClick = {
                            LauncherTopShadeConfigStore.resetToDefault(context)
                            Toast.makeText(context, "Сброшено к настройкам по умолчанию", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Сбросить по умолчанию", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }

        // --- INLINE ADD ELEMENT MODAL ---
        if (showAddModal) {
            AddTopShadeTileModal(
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
    onRemove: () -> Unit,
    onUpdatePosition: (col: Int, row: Int) -> Unit,
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

    val isActive = remember(spec, flSeatMode, frSeatMode, steeringHeat, windscreenHeat, rearDefrost, hvacAuto, hvacRecirc) {
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
            else -> false
        }
    }

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val targetCol = remember(dragOffsetX, spec.col) {
        val colShift = (dragOffsetX / (cellWidthPx + gapPx)).roundToInt()
        (spec.col + colShift).coerceIn(0, 6 - spec.spanX)
    }
    val targetRow = remember(dragOffsetY, spec.row) {
        val rowShift = (dragOffsetY / (cellHeightPx + gapPx)).roundToInt()
        (spec.row + rowShift).coerceIn(0, 7 - spec.spanY)
    }

    val renderXPx = if (isDragging) xPx + dragOffsetX else xPx
    val renderYPx = if (isDragging) yPx + dragOffsetY else yPx

    val containerBg = if (isDragging) {
        LauncherColors.AccentBlue.copy(alpha = 0.65f)
    } else if (isActive) {
        LauncherColors.AccentBlue.copy(alpha = 0.35f)
    } else {
        Color.White.copy(alpha = 0.07f)
    }
    val borderColor = if (isDragging || isActive) LauncherColors.AccentBlue else Color.White.copy(alpha = 0.12f)

    Box(
        modifier = Modifier
            .offset { IntOffset(renderXPx.roundToInt(), renderYPx.roundToInt()) }
            .size(wDp, hDp)
            .zIndex(if (isDragging) 100f else 1f),
    ) {
        // Highlight Snap Box Target on Grid while dragging
        if (isDragging) {
            val targetXPx = targetCol * (cellWidthPx + gapPx)
            val targetYPx = targetRow * (cellHeightPx + gapPx)
            val relTargetXPx = targetXPx - renderXPx
            val relTargetYPx = targetYPx - renderYPx
            Box(
                modifier = Modifier
                    .offset { IntOffset(relTargetXPx.roundToInt(), relTargetYPx.roundToInt()) }
                    .size(wDp, hDp)
                    .border(2.dp, LauncherColors.AccentBlue, RoundedCornerShape(16.dp))
                    .background(LauncherColors.AccentBlue.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
            )
        }

        Card(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (isEditMode) {
                        Modifier.pointerInput(spec, index) {
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
                                    isDragging = false
                                    onUpdatePosition(targetCol, targetRow)
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
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
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = containerBg),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (spec.type == TopShadeTileType.TELEMETRY) {
                    TelemetryTileContent(spec = spec)
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val iconRes = remember(spec.iconResName) {
                            spec.iconResName?.let { name ->
                                context.resources.getIdentifier(name, "drawable", context.packageName).takeIf { it != 0 }
                            }
                        }
                        if (iconRes != null) {
                            Image(
                                painter = painterResource(id = iconRes),
                                contentDescription = spec.title,
                                colorFilter = ColorFilter.tint(if (isActive) LauncherColors.AccentBlue else Color.White),
                                modifier = Modifier.size(28.dp),
                            )
                        } else if (spec.type == TopShadeTileType.SHORTCUT) {
                            val appIconBitmap = remember(spec.actionKey) {
                                LauncherAppIconLoader.fromPackage(context, spec.actionKey, 64)
                            }
                            if (appIconBitmap != null) {
                                Image(
                                    bitmap = appIconBitmap,
                                    contentDescription = spec.title,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = spec.title,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (isEditMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .background(Color.Red, CircleShape)
                            .clickable(onClick = onRemove),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryTileContent(spec: TopShadeTileSpec) {
    val engineTemp by CanDataRepository.engineTemperature.collectAsStateWithLifecycle()
    val voltage by CanDataRepository.voltage.collectAsStateWithLifecycle()
    val wheelsPressure by CanDataRepository.wheelsPressure.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (spec.actionKey == "engine_battery") {
            val engineTempStr = engineTemp?.let { "${it.toInt()}°C" } ?: "--"
            val voltageStr = voltage?.let { String.format(Locale.US, "%.1fV", it) } ?: "--"
            Column {
                Text("ДВС & АКБ", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                Text("t° ДВС: $engineTempStr", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text(voltageStr, color = LauncherColors.AccentBlue, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        } else if (spec.actionKey == "tpms_info") {
            val fl = wheelsPressure.wheel1?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
            val fr = wheelsPressure.wheel2?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
            val rl = wheelsPressure.wheel3?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
            val rr = wheelsPressure.wheel4?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Давление шин (Bar)", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ПЛ: $fl", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text("ПР: $fr", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text("ЗЛ: $rl", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text("ЗР: $rr", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun executeTileAction(context: Context, spec: TopShadeTileSpec, onClose: () -> Unit) {
    when (spec.actionKey) {
        "mirror_fold" -> sendToggleMirrorFold(context)
        "seat_heat_left" -> sendCycleFrontSeatHeat(context, MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH, null)
        "seat_vent_left" -> sendCycleFrontSeatVent(context, MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH, null)
        "seat_heat_right" -> sendCycleFrontSeatHeat(context, MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH, null)
        "seat_vent_right" -> sendCycleFrontSeatVent(context, MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH, null)
        "steering_heat" -> sendToggleSteeringWheelHeat(context)
        "windscreen_heat" -> sendToggleFrontWindscreenHeat(context)
        "rear_defrost" -> sendToggleRearWindowMirrorsDefrost(context)
        "hvac_auto" -> sendToggleHvacAuto(context)
        "hvac_recirc" -> sendToggleHvacAirRecirculation(context)
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
            if (spec.type == TopShadeTileType.SHORTCUT) {
                onClose()
                launchAppFromWidget(context, spec.actionKey)
            }
        }
    }
}

private fun findFirstAvailableCell(tiles: List<TopShadeTileSpec>, spanX: Int, spanY: Int): Pair<Int, Int> {
    val occupied = Array(8) { BooleanArray(6) }
    for (t in tiles) {
        for (r in t.row until (t.row + t.spanY).coerceAtMost(8)) {
            for (c in t.col until (t.col + t.spanX).coerceAtMost(6)) {
                if (r in 0..7 && c in 0..5) occupied[r][c] = true
            }
        }
    }
    for (r in 0..7) {
        for (c in 0..(6 - spanX)) {
            var fits = true
            for (dr in 0 until spanY) {
                for (dc in 0 until spanX) {
                    if (r + dr >= 8 || c + dc >= 6 || occupied[r + dr][c + dc]) {
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
    onDismiss: () -> Unit,
    onAdd: (TopShadeTileSpec) -> Unit,
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableIntStateOf(0) } // 0: Car, 1: System, 2: Telemetry, 3: Apps

    val carOptions = remember {
        listOf(
            TopShadeTileSpec("opt_hvac_power", "Питание климата", TopShadeTileType.CAR_CONTROL, "hvac_power", "ic_widget_hvac_power", 1, 1),
            TopShadeTileSpec("opt_hvac_ac", "Кондиционер AC", TopShadeTileType.CAR_CONTROL, "hvac_ac", "ic_widget_hvac_ac", 1, 1),
            TopShadeTileSpec("opt_hvac_pm25", "Очистка PM2.5", TopShadeTileType.CAR_CONTROL, "hvac_pm25", "ic_widget_pm25_on", 1, 1),
            TopShadeTileSpec("opt_steering_heat", "Подогрев руля", TopShadeTileType.CAR_CONTROL, "steering_heat", "ic_widget_steering_wheel_heat", 1, 1),
            TopShadeTileSpec("opt_windscreen_heat", "Обогрев лобового", TopShadeTileType.CAR_CONTROL, "windscreen_heat", "ic_widget_front_windscreen_heat", 1, 1),
            TopShadeTileSpec("opt_rear_defrost", "Обогрев заднего стекла", TopShadeTileType.CAR_CONTROL, "rear_defrost", "ic_widget_rear_windscreen_heat", 1, 1),
            TopShadeTileSpec("opt_mirror_fold", "Сложение зеркал", TopShadeTileType.CAR_CONTROL, "mirror_fold", "ic_widget_mirror_fold", 1, 1),
        )
    }

    val systemOptions = remember {
        listOf(
            TopShadeTileSpec("opt_theme", "Тема День/Ночь", TopShadeTileType.SYSTEM_TOGGLE, "theme", "ic_widget_theme", 1, 1),
            TopShadeTileSpec("opt_wifi", "Настройки Wi-Fi", TopShadeTileType.SYSTEM_TOGGLE, "wifi", "ic_widget_wifi", 1, 1),
            TopShadeTileSpec("opt_screen_off", "Экран выкл", TopShadeTileType.SYSTEM_TOGGLE, "screen_off", "ic_widget_screen_off", 1, 1),
            TopShadeTileSpec("opt_reboot_hu", "Перезагрузка ГУ", TopShadeTileType.SYSTEM_TOGGLE, "reboot_hu", "ic_widget_reboot", 1, 1),
        )
    }

    val telemetryOptions = remember {
        listOf(
            TopShadeTileSpec("opt_engine_battery", "ДВС и АКБ", TopShadeTileType.TELEMETRY, "engine_battery", null, 2, 1),
            TopShadeTileSpec("opt_tpms_info", "Давление в шинах", TopShadeTileType.TELEMETRY, "tpms_info", null, 2, 1),
        )
    }

    val appOptions = remember(context) {
        val pm = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        pm.queryIntentActivities(launchIntent, 0).map { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            val label = resolveInfo.loadLabel(pm).toString()
            TopShadeTileSpec(
                id = "app_$pkg",
                title = label,
                type = TopShadeTileType.SHORTCUT,
                actionKey = pkg,
                iconResName = null,
                spanX = 1,
                spanY = 1,
            )
        }.sortedBy { it.title }
    }

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
                .clickable(enabled = false) {}, // prevent click-through to backdrop
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LauncherColors.CanvasDark),
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
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
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
                    listOf("Автомобиль", "Система", "Телеметрия", "Приложения").forEachIndexed { idx, title ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedCategory == idx) LauncherColors.AccentBlue else Color.White.copy(alpha = 0.1f))
                                .clickable { selectedCategory = idx },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                // Selected Category List
                val currentList = when (selectedCategory) {
                    0 -> carOptions
                    1 -> systemOptions
                    2 -> telemetryOptions
                    else -> appOptions
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(currentList, key = { it.id }) { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable { onAdd(option) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                if (option.type == TopShadeTileType.SHORTCUT) {
                                    val iconBitmap = remember(option.actionKey) {
                                        LauncherAppIconLoader.fromPackage(context, option.actionKey, 48)
                                    }
                                    if (iconBitmap != null) {
                                        Image(
                                            bitmap = iconBitmap,
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                }
                                Text(
                                    text = option.title,
                                    color = Color.White,
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
