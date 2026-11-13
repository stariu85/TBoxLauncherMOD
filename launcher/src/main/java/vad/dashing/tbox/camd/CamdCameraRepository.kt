package vad.dashing.tbox.camd

import android.content.Context
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vad.dashing.tbox.ui.launcher.LauncherAppConfigStore
import vad.dashing.tbox.ui.launcher.LauncherEmbeddedBoundsState
import java.util.Locale

/**
 * Manages high-level camera operations via Camd:
 * - 4-camera desktop view (Left, Right, Front, Rear)
 * - Turn-signal ADAS side camera display (Left #4, Right #5)
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

    private var turnOffDebounceJob: Job? = null
    private var desktopOperationJob: Job? = null

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

    private suspend fun openDesktopCameras(context: Context) {
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
        val midY = topEdge + appHeight / 2f

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
            if (res.isSuccess && !res.getOrNull().orEmpty().startsWith("ERR") && !res.getOrNull().orEmpty().contains("state=error")) {
                successCount++
            } else {
                Log.w(TAG, "Failed command '$cmd': ${res.getOrNull()} err=${res.exceptionOrNull()?.message}")
            }
        }

        _isDesktopCamerasOpen.value = (successCount > 0)
    }

    suspend fun closeAll(context: Context) {
        val res = CamdSocketClient.sendCommand("CLOSE")
        if (res.isFailure) {
            CamdSocketClient.sendCommandWithWakeup(context, "CLOSE")
        }
        _isDesktopCamerasOpen.value = false
        _activeTurnSignalCamera.value = null
    }

    suspend fun closeCamera(context: Context, id: Int?) {
        val cmd = if (id != null) "CLOSE $id" else "CLOSE"
        CamdSocketClient.sendCommand(cmd)
        if (id == null) {
            _isDesktopCamerasOpen.value = false
            _activeTurnSignalCamera.value = null
        } else {
            if (_activeTurnSignalCamera.value == id) {
                _activeTurnSignalCamera.value = null
            }
        }
    }

    /**
     * Handles turn-light signals from mbCAN:
     * If stock AVM is disabled and turn-signal cameras option is enabled,
     * opens the corresponding side camera (#4 left, #5 right) in the ADAS zone.
     */
    fun onTurnLightSignal(context: Context, leftLight: Byte, rightLight: Byte) {
        val useStock = LauncherAppConfigStore.camdUseStockAvm(context)
        val turnSignalCameras = LauncherAppConfigStore.camdTurnSignalCameras(context)

        if (useStock || !turnSignalCameras) {
            if (_activeTurnSignalCamera.value != null) {
                scope.launch {
                    val active = _activeTurnSignalCamera.value
                    if (active != null) closeCamera(context, active)
                    _activeTurnSignalCamera.value = null
                }
            }
            return
        }

        val isLeft = leftLight > 0
        val isRight = rightLight > 0

        if (isLeft) {
            turnOffDebounceJob?.cancel()
            turnOffDebounceJob = null
            if (_activeTurnSignalCamera.value != CAMERA_ID_LEFT) {
                scope.launch {
                    if (_activeTurnSignalCamera.value == CAMERA_ID_RIGHT) {
                        closeCamera(context, CAMERA_ID_RIGHT)
                    }
                    openAdasSideCamera(context, CAMERA_ID_LEFT)
                    _activeTurnSignalCamera.value = CAMERA_ID_LEFT
                }
            }
        } else if (isRight) {
            turnOffDebounceJob?.cancel()
            turnOffDebounceJob = null
            if (_activeTurnSignalCamera.value != CAMERA_ID_RIGHT) {
                scope.launch {
                    if (_activeTurnSignalCamera.value == CAMERA_ID_LEFT) {
                        closeCamera(context, CAMERA_ID_LEFT)
                    }
                    openAdasSideCamera(context, CAMERA_ID_RIGHT)
                    _activeTurnSignalCamera.value = CAMERA_ID_RIGHT
                }
            }
        } else {
            // Both off -> debounce to avoid flickering between turn signal blink pulses
            if (_activeTurnSignalCamera.value != null && turnOffDebounceJob == null) {
                turnOffDebounceJob = scope.launch {
                    delay(1200L)
                    val active = _activeTurnSignalCamera.value
                    if (active != null) {
                        closeCamera(context, active)
                        _activeTurnSignalCamera.value = null
                    }
                    turnOffDebounceJob = null
                }
            }
        }
    }

    private suspend fun openAdasSideCamera(context: Context, cameraId: Int) {
        val screen = LauncherEmbeddedBoundsState.screenBounds
        val dispW = (screen?.width()?.takeIf { it > 0 } ?: 1920).toFloat()
        val dispH = (screen?.height()?.takeIf { it > 0 } ?: 1080).toFloat()

        val adasRect = LauncherEmbeddedBoundsState.leftPanelBounds
            ?: Rect(0, 51, 344, 998)

        val x = adasRect.left.toFloat() / dispW
        val y = adasRect.top.toFloat() / dispH
        val w = adasRect.width().toFloat() / dispW
        val h = adasRect.height().toFloat() / dispH

        val cmd = formatCmd(cameraId, x, y, w, h)
        CamdSocketClient.sendCommandWithWakeup(context, cmd)
    }

    /**
     * Formats OPEN command according to DCL_CAMD_CAMERA_API_FOR_VENDOR_RU §3.1:
     * OPEN <id> <x> <y> <w> <h> [rotation [flipH [flipV]]]
     * - rotation: 0, 90, 180, 270 (clockwise, default 0)
     * - flipH: 0 or 1 (horizontal mirror after rotation, default 0)
     * - flipV: 0 or 1 (vertical mirror, default 0)
     * Note: Fisheye correction (k1/k2) is handled internally by camd shader.
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
    ): String {
        return if (flipV != 0) {
            String.format(Locale.US, "OPEN %d %.4f %.4f %.4f %.4f %d %d %d", id, x, y, w, h, rotation, flipH, flipV)
        } else if (flipH != 0) {
            String.format(Locale.US, "OPEN %d %.4f %.4f %.4f %.4f %d %d", id, x, y, w, h, rotation, flipH)
        } else if (rotation != 0) {
            String.format(Locale.US, "OPEN %d %.4f %.4f %.4f %.4f %d", id, x, y, w, h, rotation)
        } else {
            String.format(Locale.US, "OPEN %d %.4f %.4f %.4f %.4f", id, x, y, w, h)
        }
    }

    fun getCameraRotation(cameraId: Int): Int = when (cameraId) {
        CAMERA_ID_LEFT -> 270 // -90° (270° по часовой)
        CAMERA_ID_RIGHT -> 90 // +90° по часовой
        CAMERA_ID_REAR -> 180 // 180°
        else -> 0
    }

    fun getCameraFlipH(cameraId: Int): Int = 0

    fun getCameraFlipV(cameraId: Int): Int = 0
}
