package vad.dashing.tbox.camd

import android.content.Context
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.mbcan.TurnSignalSide
import vad.dashing.tbox.mbcan.TurnSignalsLatch
import vad.dashing.tbox.ui.launcher.LauncherAppConfigStore
import vad.dashing.tbox.ui.launcher.LauncherCar3DModelTrigger
import vad.dashing.tbox.ui.launcher.LauncherEmbeddedBoundsState
import java.util.Locale

/**
 * Normalized window coordinates (0.0..1.0 fraction of display).
 * Per spec §3 & §9:
 * - Minimum side 0.05 (5%)
 * - x + w <= 1.02
 * - y + h <= 1.02
 */
data class CamdWindowBounds(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
) {
    fun normalized(): CamdWindowBounds {
        val clampedW = w.coerceIn(0.05f, 1.0f)
        val clampedH = h.coerceIn(0.05f, 1.0f)
        val clampedX = x.coerceIn(0f, (1.02f - clampedW).coerceAtLeast(0f))
        val clampedY = y.coerceIn(0f, (1.02f - clampedH).coerceAtLeast(0f))
        return CamdWindowBounds(clampedX, clampedY, clampedW, clampedH)
    }
}

/**
 * Crop rectangle inside sensor frame (0.0..1.0 fraction).
 * Per spec §3: min side 0.05, cx + cw <= 1.02, cy + ch <= 1.02.
 * Requires Camd >= 0.4.0.
 */
data class CamdCrop(
    val cx: Float,
    val cy: Float,
    val cw: Float,
    val ch: Float,
) {
    fun normalized(): CamdCrop {
        val clampedW = cw.coerceIn(0.05f, 1.0f)
        val clampedH = ch.coerceIn(0.05f, 1.0f)
        val clampedX = cx.coerceIn(0f, (1.02f - clampedW).coerceAtLeast(0f))
        val clampedY = cy.coerceIn(0f, (1.02f - clampedH).coerceAtLeast(0f))
        return CamdCrop(clampedX, clampedY, clampedW, clampedH)
    }
}

/**
 * Manages high-level camera operations via Camd:
 * - 4-camera desktop view (Left, Right, Front, Rear)
 * - Turn-signal ADAS side camera display (Left #4, Right #5)
 * - Preset and circular shape support (Camd >= 0.5.0)
 * - Status monitoring and toggles
 */
object CamdCameraRepository {
    private const val TAG = "CamdCameraRepository"

    const val CAMERA_ID_CABIN = 0
    const val CAMERA_ID_OMS = 1
    const val CAMERA_ID_DMS = 3
    const val CAMERA_ID_LEFT = 4
    const val CAMERA_ID_RIGHT = 5
    const val CAMERA_ID_FRONT = 8
    const val CAMERA_ID_REAR = 9

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow<CamdConnectionStatus>(CamdConnectionStatus.NotInstalled)
    val status: StateFlow<CamdConnectionStatus> = _status.asStateFlow()

    private val _isDesktopCamerasOpen = MutableStateFlow(false)
    val isDesktopCamerasOpen: StateFlow<Boolean> = _isDesktopCamerasOpen.asStateFlow()

    private val _activeTurnSignalCamera = MutableStateFlow<Int?>(null)
    val activeTurnSignalCamera: StateFlow<Int?> = _activeTurnSignalCamera.asStateFlow()

    private val _activeTurnSignalSide = MutableStateFlow<TurnSignalSide?>(null)
    val activeTurnSignalSideFlow: StateFlow<TurnSignalSide?> = _activeTurnSignalSide.asStateFlow()
    val activeTurnSignalSide: TurnSignalSide?
        get() = _activeTurnSignalSide.value

    @Volatile
    private var appContext: Context? = null

    private val turnSignalsLatch = TurnSignalsLatch(
        scope = scope,
        holdMs = TurnSignalsLatch.HOLD_MS,
        onSideChanged = { side ->
            handleLatchedTurnSignal(side)
        },
    )

    private var desktopOperationJob: Job? = null
    private var turnSignalJob: Job? = null
    private val cameraOperationMutex = Mutex()

    fun refreshStatus(context: Context) {
        scope.launch {
            val s = CamdSocketClient.checkStatus(context)
            _status.value = s
            if (s is CamdConnectionStatus.Connected) {
                val hasAllFour = listOf(CAMERA_ID_LEFT, CAMERA_ID_RIGHT, CAMERA_ID_FRONT, CAMERA_ID_REAR)
                    .all { it in s.openWindows }
                _isDesktopCamerasOpen.value = hasAllFour
            }
        }
    }

    suspend fun setHoldStockAvm(context: Context, holdOff: Boolean): Result<String> {
        val res = CamdSocketClient.setAvmHold(context, holdOff)
        refreshStatus(context)
        return res
    }

    /**
     * Toggles 4 desktop cameras in the desktop / app window area:
     * - Left camera (#4) on the left
     * - Right camera (#5) on the right
     * - Front (#8) & Rear (#9) in the middle up to half height
     */
    fun toggleDesktopCameras(context: Context) {
        desktopOperationJob?.cancel()
        desktopOperationJob = scope.launch {
            if (_isDesktopCamerasOpen.value) {
                closeAll(context)
                _isDesktopCamerasOpen.value = false
            } else {
                openDesktopCameras(context)
            }
        }
    }

    fun hasActiveCameras(): Boolean =
        _isDesktopCamerasOpen.value ||
            _activeTurnSignalCamera.value != null ||
            _activeTurnSignalSide.value != null ||
            ((_status.value as? CamdConnectionStatus.Connected)?.openWindows?.isNotEmpty() == true)

    /**
     * Closes any active Camd cameras (desktop 4-camera grid, side cameras, or test cameras).
     */
    fun closeAllIfOpen(context: Context) {
        if (!CamdSocketClient.isInstalled(context)) return
        if (hasActiveCameras() || desktopOperationJob?.isActive == true || turnSignalJob?.isActive == true) {
            desktopOperationJob?.cancel()
            turnSignalJob?.cancel()
            desktopOperationJob = scope.launch {
                closeAll(context)
            }
        }
    }

    /**
     * Closes desktop cameras if they are currently active.
     */
    fun closeDesktopCamerasIfOpen(context: Context) {
        closeAllIfOpen(context)
    }

    private suspend fun openDesktopCameras(context: Context) = cameraOperationMutex.withLock {
        val screen = LauncherEmbeddedBoundsState.screenBounds
        val dispW = (screen?.width()?.takeIf { it > 0 } ?: 1920).toFloat()
        val dispH = (screen?.height()?.takeIf { it > 0 } ?: 1080).toFloat()

        val appRect = LauncherEmbeddedBoundsState.embeddedBounds()
            ?: Rect(344, 51, 1920, 998)

        val leftEdge = appRect.left.toFloat()
        val rightEdge = appRect.right.toFloat()
        val topEdge = appRect.top.toFloat()
        val bottomEdge = appRect.bottom.toFloat()

        val appWidth = rightEdge - leftEdge
        val appHeight = bottomEdge - topEdge
        val midY = topEdge + (appHeight / 2f)

        // Geometry:
        // Left camera takes left 30% of app area
        val colLeftX1 = leftEdge
        val colLeftX2 = leftEdge + appWidth * 0.30f

        // Right camera takes right 30% of app area
        val colRightX1 = rightEdge - appWidth * 0.30f
        val colRightX2 = rightEdge

        // Middle column takes remaining 40%
        val colMidX1 = colLeftX2
        val colMidX2 = colRightX1

        // 1. Left Camera (#4)
        val leftX = colLeftX1 / dispW
        val leftY = topEdge / dispH
        val leftW = (colLeftX2 - colLeftX1) / dispW
        val leftH = appHeight / dispH

        // 2. Right Camera (#5)
        val rightX = colRightX1 / dispW
        val rightY = topEdge / dispH
        val rightW = (colRightX2 - colRightX1) / dispW
        val rightH = appHeight / dispH

        // 3. Front Camera (#8) - upper half of middle column
        val frontX = colMidX1 / dispW
        val frontY = topEdge / dispH
        val frontW = (colMidX2 - colMidX1) / dispW
        val frontH = (midY - topEdge) / dispH

        // 4. Rear Camera (#9) - lower half of middle column
        val rearX = colMidX1 / dispW
        val rearY = midY / dispH
        val rearW = (colMidX2 - colMidX1) / dispW
        val rearH = (bottomEdge - midY) / dispH

        val commands = listOf(
            formatCmd(CAMERA_ID_LEFT, leftX, leftY, leftW, leftH),
            formatCmd(CAMERA_ID_RIGHT, rightX, rightY, rightW, rightH),
            formatCmd(CAMERA_ID_FRONT, frontX, frontY, frontW, frontH),
            formatCmd(CAMERA_ID_REAR, rearX, rearY, rearW, rearH),
        )

        var successCount = 0
        for ((idx, cmd) in commands.withIndex()) {
            val res = if (idx == 0) {
                // First command wakes up service if sleeping
                CamdSocketClient.sendCommandWithWakeup(context, cmd)
            } else {
                CamdSocketClient.sendCommand(cmd)
            }
            val responseText = res.getOrNull().orEmpty()
            if (res.isSuccess && CamdSocketClient.isSuccessResponse(responseText)) {
                successCount++
            } else {
                Log.w(TAG, "Failed command '$cmd': $responseText err=${res.exceptionOrNull()?.message}")
            }
        }

        _isDesktopCamerasOpen.value = (successCount > 0)
    }

    suspend fun closeAll(context: Context) {
        turnSignalJob?.cancel()
        cameraOperationMutex.withLock {
            val res = CamdSocketClient.sendCommand("CLOSE")
            if (res.isFailure) {
                CamdSocketClient.sendCommandWithWakeup(context, "CLOSE")
            }
            _isDesktopCamerasOpen.value = false
            _activeTurnSignalCamera.value = null
            _activeTurnSignalSide.value = null
            turnSignalsLatch.reset()
            LauncherCar3DModelTrigger.notifyCamdCamerasClosed()
        }
    }

    suspend fun closeCamera(context: Context, id: Int?) {
        cameraOperationMutex.withLock {
            val cmd = if (id != null) "CLOSE $id" else "CLOSE"
            vad.dashing.tbox.DclLogRecorder.log("CAMD_CAMERA", "closeCamera cmd='$cmd' id=$id")
            val res = CamdSocketClient.sendCommand(cmd)
            if (res.isFailure) {
                CamdSocketClient.sendCommandWithWakeup(context, cmd)
            }
            if (id != null && (res.isFailure || CamdSocketClient.isErrorResponse(res.getOrNull().orEmpty()))) {
                vad.dashing.tbox.DclLogRecorder.log("CAMD_CAMERA", "closeCamera id=$id failed (${res.getOrNull()}), fallback to CLOSE")
                CamdSocketClient.sendCommandWithWakeup(context, "CLOSE")
            }
            if (id == null) {
                _isDesktopCamerasOpen.value = false
                _activeTurnSignalCamera.value = null
                _activeTurnSignalSide.value = null
            } else {
                if (_activeTurnSignalCamera.value == id) {
                    _activeTurnSignalCamera.value = null
                    _activeTurnSignalSide.value = null
                }
            }
            LauncherCar3DModelTrigger.notifyCamdCamerasClosed()
        }
    }

    /**
     * Handles turn-light signals from mbCAN (type 2, eMBCAN_VEHICLE_TURNLIGHT):
     * If turn-signal cameras option is enabled,
     * opens the corresponding side camera (#4 left, #5 right) in the ADAS zone or corner.
     * Uses [TurnSignalsLatch] with 2.5s hold time.
     */
    fun onTurnLightSignal(context: Context, leftLight: Byte, rightLight: Byte) {
        val appCtx = context.applicationContext
        appContext = appCtx

        // Do not process turn signal cameras during cold startup before initial draw is finished
        if (!vad.dashing.tbox.LauncherHomeActivityHolder.isFirstDrawComplete) {
            return
        }

        if (_status.value !is CamdConnectionStatus.Connected) {
            refreshStatus(appCtx)
        }

        val turnSignalCameras = LauncherAppConfigStore.camdTurnSignalCameras(appCtx)

        if (!turnSignalCameras) {
            turnSignalsLatch.reset()
            turnSignalJob?.cancel()
            if (_activeTurnSignalCamera.value != null || _activeTurnSignalSide.value != null) {
                val active = _activeTurnSignalCamera.value
                _activeTurnSignalCamera.value = null
                _activeTurnSignalSide.value = null
                turnSignalJob = scope.launch {
                    if (active != null) closeCamera(appCtx, active) else closeCamera(appCtx, null)
                }
            }
            return
        }

        turnSignalsLatch.onRawTurnLights(leftLight.toInt(), rightLight.toInt())
    }

    private fun handleLatchedTurnSignal(side: TurnSignalSide?) {
        val context = appContext ?: return
        vad.dashing.tbox.DclLogRecorder.log("CAMD_CAMERA", "handleLatchedTurnSignal side=$side activeCam=${_activeTurnSignalCamera.value} activeSide=${_activeTurnSignalSide.value}")
        val turnSignalCameras = LauncherAppConfigStore.camdTurnSignalCameras(context)

        if (!turnSignalCameras) {
            turnSignalJob?.cancel()
            if (_activeTurnSignalCamera.value != null || _activeTurnSignalSide.value != null) {
                val active = _activeTurnSignalCamera.value
                _activeTurnSignalCamera.value = null
                _activeTurnSignalSide.value = null
                turnSignalJob = scope.launch {
                    if (active != null) closeCamera(context, active) else closeCamera(context, null)
                }
            }
            return
        }

        when (side) {
            TurnSignalSide.Left -> {
                if (_activeTurnSignalSide.value != TurnSignalSide.Left) {
                    turnSignalJob?.cancel()
                    _activeTurnSignalSide.value = TurnSignalSide.Left
                    turnSignalJob = scope.launch {
                        val current = _activeTurnSignalCamera.value
                        if (current != null) {
                            closeCamera(context, current)
                            _activeTurnSignalCamera.value = null
                        }
                        val openedCamId = openTurnSignalSideCamera(context, CAMERA_ID_LEFT)
                        if (!isActive || turnSignalsLatch.latchedSide.value != TurnSignalSide.Left) {
                            Log.i(TAG, "Turn signal Left cancelled or expired during open, closing immediately")
                            if (openedCamId != null) {
                                closeCamera(context, openedCamId)
                            } else {
                                closeCamera(context, null)
                            }
                            _activeTurnSignalCamera.value = null
                            _activeTurnSignalSide.value = null
                            return@launch
                        }
                        _activeTurnSignalCamera.value = openedCamId
                    }
                }
            }
            TurnSignalSide.Right -> {
                if (_activeTurnSignalSide.value != TurnSignalSide.Right) {
                    turnSignalJob?.cancel()
                    _activeTurnSignalSide.value = TurnSignalSide.Right
                    turnSignalJob = scope.launch {
                        val current = _activeTurnSignalCamera.value
                        if (current != null) {
                            closeCamera(context, current)
                            _activeTurnSignalCamera.value = null
                        }
                        val openedCamId = openTurnSignalSideCamera(context, CAMERA_ID_RIGHT)
                        if (!isActive || turnSignalsLatch.latchedSide.value != TurnSignalSide.Right) {
                            Log.i(TAG, "Turn signal Right cancelled or expired during open, closing immediately")
                            if (openedCamId != null) {
                                closeCamera(context, openedCamId)
                            } else {
                                closeCamera(context, null)
                            }
                            _activeTurnSignalCamera.value = null
                            _activeTurnSignalSide.value = null
                            return@launch
                        }
                        _activeTurnSignalCamera.value = openedCamId
                    }
                }
            }
            null, TurnSignalSide.Hazard -> {
                turnSignalJob?.cancel()
                if (_activeTurnSignalCamera.value != null || _activeTurnSignalSide.value != null) {
                    val active = _activeTurnSignalCamera.value
                    _activeTurnSignalCamera.value = null
                    _activeTurnSignalSide.value = null
                    turnSignalJob = scope.launch {
                        if (active != null) {
                            closeCamera(context, active)
                        } else {
                            closeCamera(context, null)
                        }
                    }
                }
            }
        }
    }

    /**
     * Toggles a side camera test on/off.
     */
    fun testSideCamera(context: Context, cameraId: Int) {
        val side = if (cameraId == CAMERA_ID_LEFT) TurnSignalSide.Left else TurnSignalSide.Right
        turnSignalJob?.cancel()
        turnSignalJob = scope.launch {
            if (_activeTurnSignalSide.value == side) {
                val current = _activeTurnSignalCamera.value
                _activeTurnSignalCamera.value = null
                _activeTurnSignalSide.value = null
                if (current != null) {
                    closeCamera(context, current)
                } else {
                    closeCamera(context, null)
                }
            } else {
                val current = _activeTurnSignalCamera.value
                if (current != null) {
                    closeCamera(context, current)
                    _activeTurnSignalCamera.value = null
                }
                _activeTurnSignalSide.value = side
                val openedCamId = openTurnSignalSideCamera(context, cameraId)
                _activeTurnSignalCamera.value = openedCamId
                _activeTurnSignalSide.value = side
            }
        }
    }

    private suspend fun openTurnSignalSideCamera(context: Context, cameraId: Int): Int? = cameraOperationMutex.withLock {
        val screen = LauncherEmbeddedBoundsState.screenBounds
        val dispW = (screen?.width()?.takeIf { it > 0 } ?: 1920).toFloat()
        val dispH = (screen?.height()?.takeIf { it > 0 } ?: 1080).toFloat()
        if (dispW <= 0f || dispH <= 0f) return null

        val shapeCircle = LauncherAppConfigStore.camdSideCamerasShapeCircle(context)
        val rectCorners = LauncherAppConfigStore.camdSideCamerasRectCorners(context)
        val effectiveShapeCircle = shapeCircle && !rectCorners

        val presetName = when (cameraId) {
            CAMERA_ID_LEFT -> LauncherAppConfigStore.camdTurnSignalLeftPreset(context).ifBlank { null }
            CAMERA_ID_RIGHT -> LauncherAppConfigStore.camdTurnSignalRightPreset(context).ifBlank { null }
            else -> null
        }

        val targetCameraId = if (!presetName.isNullOrBlank()) {
            val connectedPresets = (_status.value as? CamdConnectionStatus.Connected)?.presets
            connectedPresets?.firstOrNull { it.name == presetName }?.cameraId ?: cameraId
        } else {
            cameraId
        }

        val x: Float
        val y: Float
        val w: Float
        val h: Float

        if (effectiveShapeCircle || rectCorners) {
            val desktopRect = LauncherEmbeddedBoundsState.embeddedBounds()
                ?: LauncherEmbeddedBoundsState.contentRowBounds
                ?: Rect(344, 51, 1920, 998)

            val desktopHeight = (desktopRect.bottom - desktopRect.top).toFloat().coerceAtLeast(100f)
            val baseDiameter = desktopHeight / 3f

            val density = context.resources.displayMetrics.density
            val margin = 30f * density

            val yPx = desktopRect.top.toFloat() + margin
            val rectW: Float
            val rectH: Float

            if (rectCorners) {
                // Прямоугольный видеопоток 3 на 4 (4 - ширина), увеличен на 40%
                rectW = baseDiameter * 1.30f * 1.40f
                rectH = rectW * (3f / 4f)
            } else {
                // Круглое окно: увеличено на 30% (квадрат rectW x rectH с shape=circle)
                val circleDiameter = baseDiameter * 1.30f * 1.30f
                rectW = circleDiameter
                rectH = circleDiameter
            }

            val xPx = if (cameraId == CAMERA_ID_RIGHT) {
                // Правый верхний угол рабочего стола с отступом 30dp от правого края и верха
                desktopRect.right.toFloat() - margin - rectW
            } else {
                // Левый верхний угол рабочего стола с отступом 30dp от левого края и верха
                desktopRect.left.toFloat() + margin
            }

            x = xPx / dispW
            y = yPx / dispH
            w = rectW / dispW
            h = rectH / dispH
        } else {
            // Прямоугольная форма: зона ADAS
            val adasRect = LauncherEmbeddedBoundsState.leftPanelBounds
                ?: Rect(0, 51, 344, 998)

            x = adasRect.left.toFloat() / dispW
            y = adasRect.top.toFloat() / dispH
            w = adasRect.width().toFloat() / dispW
            h = adasRect.height().toFloat() / dispH
        }

        if (!x.isFinite() || !y.isFinite() || !w.isFinite() || !h.isFinite()) return null

        val cmd = if (!presetName.isNullOrBlank()) {
            formatPresetCmd(presetName, x, y, w, h, shapeCircle = effectiveShapeCircle)
        } else {
            formatCmd(targetCameraId, x, y, w, h, shapeCircle = effectiveShapeCircle)
        }

        val res = CamdSocketClient.sendCommandWithWakeup(context, cmd)
        val responseText = res.getOrNull().orEmpty()
        if (CamdSocketClient.isErrorResponse(responseText)) {
            Log.w(TAG, "openTurnSignalSideCamera command failed: $responseText")
            // Fallback: if preset failed with ERR unknown_preset, retry with raw camera
            if (!presetName.isNullOrBlank()) {
                val fallbackCmd = formatCmd(cameraId, x, y, w, h, shapeCircle = effectiveShapeCircle)
                CamdSocketClient.sendCommand(fallbackCmd)
                return cameraId
            }
        }
        return targetCameraId
    }

    /**
     * Formats OPEN command according to DCL_CAMD_CAMERA_API_FOR_VENDOR_RU §3.1:
     * OPEN <id> <x> <y> <w> <h> [rotation [flipH [flipV]]] [crop=cx,cy,cw,ch] [shape=circle]
     * - id: int (0, 1, 3, 4, 5, 8, 9)
     * - x, y, w, h: float 0..1, min side 0.05, x+w <= 1.02, y+h <= 1.02
     * - rotation: 0, 90, 180, 270 (clockwise, default 0)
     * - flipH: 0 or 1 (horizontal mirror after rotation, default 0)
     * - flipV: 0 or 1 (vertical mirror, default 0)
     * - crop=cx,cy,cw,ch: optional named token (min side 0.05, cx+cw <= 1.02)
     * - shape=circle: optional named token for circular window
     * Note: Per-camera fisheye correction and keystone tilt are handled internally by camd shader.
     */
    fun formatCmd(
        id: Int,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        rotation: Int = getCameraRotation(id),
        flipH: Int = getCameraFlipH(id),
        flipV: Int = getCameraFlipV(id),
        crop: CamdCrop? = null,
        shapeCircle: Boolean = false,
    ): String {
        val bounds = CamdWindowBounds(x, y, w, h).normalized()
        val sb = StringBuilder()
        sb.append(String.format(Locale.US, "OPEN %d %.4f %.4f %.4f %.4f", id, bounds.x, bounds.y, bounds.w, bounds.h))

        val hasFlip = (flipH != 0 || flipV != 0)
        if (hasFlip) {
            // Per spec: if flip is used, rotation token is MANDATORY
            sb.append(String.format(Locale.US, " %d %d %d", rotation, flipH, flipV))
        } else if (rotation != 0) {
            sb.append(String.format(Locale.US, " %d", rotation))
        }

        if (crop != null) {
            val normCrop = crop.normalized()
            sb.append(String.format(Locale.US, " crop=%.4f,%.4f,%.4f,%.4f", normCrop.cx, normCrop.cy, normCrop.cw, normCrop.ch))
        }

        if (shapeCircle) {
            sb.append(" shape=circle")
        }

        return sb.toString()
    }

    /**
     * Formats preset OPEN command according to DCL_CAMD_CAMERA_API_FOR_VENDOR_RU §3.1:
     * OPEN preset=<name> <x> <y> <w> <h> [shape=circle]
     * Positional tokens: exactly 4 coordinates (x, y, w, h).
     * Cannot mix preset= with rotation/flip/crop=.
     */
    fun formatPresetCmd(
        presetName: String,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        shapeCircle: Boolean = false,
    ): String {
        val bounds = CamdWindowBounds(x, y, w, h).normalized()
        val sb = StringBuilder()
        sb.append(String.format(Locale.US, "OPEN preset=%s %.4f %.4f %.4f %.4f", presetName.trim(), bounds.x, bounds.y, bounds.w, bounds.h))
        if (shapeCircle) {
            sb.append(" shape=circle")
        }
        return sb.toString()
    }

    fun getCameraRotation(cameraId: Int): Int = 0

    fun getCameraFlipH(cameraId: Int): Int = 0

    fun getCameraFlipV(cameraId: Int): Int = 0
}
