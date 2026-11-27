package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.TextureView
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import vad.dashing.tbox.ui.rememberPlaySystemClickSound
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.filament.Engine
import com.google.android.filament.Renderer
import com.google.android.filament.View.BlendMode
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneAutoCenterState
import io.github.sceneview.SceneNodeManager
import io.github.sceneview.SceneRenderer
import io.github.sceneview.applyRenderQuality
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberRenderer
import io.github.sceneview.rememberScene
import io.github.sceneview.rememberView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import vad.dashing.tbox.DclLogRecorder
import vad.dashing.tbox.LauncherHomeActivityHolder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.exp

// Calibrated against the GLB's authored forward axis so the nose points to screen top.
private const val MODEL_YAW_DEG = 0f
private const val SETTINGS_YAW_DEG = -90f

object LauncherCar3DModelTrigger {
    private val _homePressedEvent = MutableStateFlow(0L)
    val homePressedEvent: StateFlow<Long> = _homePressedEvent.asStateFlow()

    private val _backPressedEvent = MutableStateFlow(0L)
    val backPressedEvent: StateFlow<Long> = _backPressedEvent.asStateFlow()

    private val _previousAppPressedEvent = MutableStateFlow(0L)
    val previousAppPressedEvent: StateFlow<Long> = _previousAppPressedEvent.asStateFlow()

    private val _quickSettingsClosedEvent = MutableStateFlow(0L)
    val quickSettingsClosedEvent: StateFlow<Long> = _quickSettingsClosedEvent.asStateFlow()

    private val _camdCamerasClosedEvent = MutableStateFlow(0L)
    val camdCamerasClosedEvent: StateFlow<Long> = _camdCamerasClosedEvent.asStateFlow()

    private val _reverseCamerasClosedEvent = MutableStateFlow(0L)
    val reverseCamerasClosedEvent: StateFlow<Long> = _reverseCamerasClosedEvent.asStateFlow()

    private val _paintChangedEvent = MutableStateFlow(0L)
    val paintChangedEvent: StateFlow<Long> = _paintChangedEvent.asStateFlow()

    private val _topShadeOpen = MutableStateFlow(false)
    val topShadeOpen: StateFlow<Boolean> = _topShadeOpen.asStateFlow()

    private val _topShadeClosedEvent = MutableStateFlow(0L)
    val topShadeClosedEvent: StateFlow<Long> = _topShadeClosedEvent.asStateFlow()

    fun notifyHomePressed() {
        _homePressedEvent.value = SystemClock.uptimeMillis()
    }

    fun notifyBackPressed() {
        _backPressedEvent.value = SystemClock.uptimeMillis()
    }

    fun notifyPreviousAppPressed() {
        _previousAppPressedEvent.value = SystemClock.uptimeMillis()
    }

    fun notifyQuickSettingsClosed() {
        _quickSettingsClosedEvent.value = SystemClock.uptimeMillis()
    }

    fun notifyCamdCamerasClosed() {
        _camdCamerasClosedEvent.value = SystemClock.uptimeMillis()
    }

    fun notifyReverseCamerasClosed() {
        _reverseCamerasClosedEvent.value = SystemClock.uptimeMillis()
    }

    fun notifyPaintChanged() {
        _paintChangedEvent.value = SystemClock.uptimeMillis()
    }

    private val _parkEngagedEvent = MutableStateFlow(0L)
    val parkEngagedEvent: StateFlow<Long> = _parkEngagedEvent.asStateFlow()

    fun notifyParkEngaged() {
        _parkEngagedEvent.value = SystemClock.uptimeMillis()
    }

    private val _settingsOrbitEvent = MutableStateFlow(0L)
    val settingsOrbitEvent: StateFlow<Long> = _settingsOrbitEvent.asStateFlow()

    fun notifySettingsOrbit() {
        _settingsOrbitEvent.value = SystemClock.uptimeMillis()
    }

    private val _windowReturnedEvent = MutableStateFlow(0L)
    val windowReturnedEvent: StateFlow<Long> = _windowReturnedEvent.asStateFlow()

    fun notifyWindowReturned() {
        _windowReturnedEvent.value = SystemClock.uptimeMillis()
    }

    private val _startupTopViewDone = MutableStateFlow(false)
    val startupTopViewDone: StateFlow<Boolean> = _startupTopViewDone.asStateFlow()

    fun notifyStartupTopViewDone() {
        _startupTopViewDone.value = true
    }

    fun resetStartupTopView() {
        _startupTopViewDone.value = false
    }

    fun setTopShadeOpen(open: Boolean) {
        if (_topShadeOpen.value && !open) {
            _topShadeClosedEvent.value = SystemClock.uptimeMillis()
        }
        _topShadeOpen.value = open
    }
}

private val TOP_CAMERA_POS = Float3(0f, 4.2f, 0.70f)
private val TOP_CAMERA_TARGET = Float3(0f, 0f, 0f)
/** Raised by +15° in elevation pitch to align the car perspective with the road canvas. */
private val DRIVE_CAMERA_POS = Float3(0f, 2.20f, 2.85f)
private val DRIVE_CAMERA_TARGET = Float3(0f, 0.20f, -2.2f)
/** Settings camera keeps the whole car in frame and clear of the near clipping plane. */
private val SETTINGS_CAMERA_POS = Float3(1.15f, 1.05f, 2.55f)
private val SETTINGS_CAMERA_TARGET = Float3(0.12f, 0.18f, 0f)

private const val BASE_MODEL_SCALE = 0.378f
private const val PREPARED_FRAMES_WARMUP = 15
internal const val CAR_3D_TRANSITION_TICK_DIVISOR = 1 // 30 FPS (2 VSYNC ticks at 60 Hz, synced with road)
internal const val CAR_3D_WAKEUP_TICK_DIVISOR = 2 // 15 FPS (4 VSYNC ticks at 60 Hz, synced with PDC)
internal const val CAR_3D_STATIC_TICK_DIVISOR = 30 // ~1 FPS (when pause is blocked, e.g. door left open >2s)
private const val DYNAMIC_GRACE_PERIOD_NS = 2_000_000_000L // 2 seconds grace period

internal fun resolveCar3dTickDivisor(
    isCameraSwitching: Boolean,
    isInitializing: Boolean,
    isDynamic: Boolean,
): Int = when {
    isCameraSwitching || isInitializing -> CAR_3D_TRANSITION_TICK_DIVISOR
    isDynamic -> CAR_3D_WAKEUP_TICK_DIVISOR
    else -> CAR_3D_STATIC_TICK_DIVISOR
}
// Settings uses the full SceneView bounds; visual size is controlled only here/camera.
private const val SETTINGS_MODEL_SCALE = 0.48f
private const val HOME_MODEL_X = 0f
private const val SETTINGS_MODEL_X = 0.14f
private const val HOME_MODEL_Y = -0.1f
private const val SETTINGS_MODEL_Y = -0.02f
private const val HOME_MODEL_Z = 0f
private const val SETTINGS_MODEL_Z = 0f

private fun quaternionFromAxisAngle(axis: Float3, angleDeg: Float): Quaternion {
    val r = Math.toRadians(angleDeg.toDouble()).toFloat()
    val halfSin = kotlin.math.sin(r * 0.5f)
    val halfCos = kotlin.math.cos(r * 0.5f)
    val len = kotlin.math.sqrt(axis.x * axis.x + axis.y * axis.y + axis.z * axis.z)
    val nx = if (len > 0f) axis.x / len else 0f
    val ny = if (len > 0f) axis.y / len else 0f
    val nz = if (len > 0f) axis.z / len else 0f
    return Quaternion(nx * halfSin, ny * halfSin, nz * halfSin, halfCos)
}

private fun multiplyQuaternions(q1: Quaternion, q2: Quaternion): Quaternion {
    return Quaternion(
        x = q1.w * q2.x + q1.x * q2.w + q1.y * q2.z - q1.z * q2.y,
        y = q1.w * q2.y - q1.x * q2.z + q1.y * q2.w + q1.z * q2.x,
        z = q1.w * q2.z + q1.x * q2.y - q1.y * q2.x + q1.z * q2.w,
        w = q1.w * q2.w - q1.x * q2.x - q1.y * q2.y - q1.z * q2.z,
    )
}

/**
 * Plain non-Compose holder for [ThrottledCarSceneView] `onFrame` loop state.
 * Mutating fields here does NOT invalidate Compose snapshot state.
 */
private class FrameLoopState(
    initialTransition: Float,
    initialSteeringDeg: Float,
    initialRigState: LauncherCarRigState,
) {
    var wasCameraSwitching: Boolean = false
    var isRollingPtoD: Boolean = false
    @Volatile
    var lastFrameNs: Long = 0L
    @Volatile
    var lastResumedElapsedMs: Long = SystemClock.elapsedRealtime()
    var lastAnchorPublishNs: Long = 0L
    var lastXCheckNs: Long = 0L
    var dclLastLogNs: Long = 0L
    var dclLastPosX: Float = 0f
    var dclLastCamX: Float = 0f
    var lastDynamicNs: Long = 0L
    var firstRenderTimeMs: Long = 0L
    var currentDriveBlend: Float = 0f
    var lastTargetDriveBlend: Float = 0f
    var lastFrameTransition: Float = initialTransition
    var wasHoldingStartupTopView: Boolean = true
    var preparedFrames: Int = 0
    var renderedScale: Float = BASE_MODEL_SCALE
    var lastSteeringDeg: Float = initialSteeringDeg
    var lastRigState: LauncherCarRigState = initialRigState
}

/** Offline Filament 3D car (Dashing 720) with pivot rig, paint colors and drive camera. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherCar3DModel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onTrunkClick: (() -> Unit)? = null,
    onTrunkLongClick: (() -> Unit)? = null,
    modelRevision: Int = 0,
    paintRevision: Int = 0,
    paintId: String = LauncherCarPaint.defaultId,
    rigState: LauncherCarRigState = LauncherCarRigState(),
    speedKmh: Float = 0f,
    steeringDeg: Float = 0f,
    steerPreview: Boolean = false,
    inDriveGear: Boolean = false,
    showRoad: Boolean = true,
    settingsView: Boolean = false,
    settingsProgress: Float = 1f,
    settingsUserYawDeg: Float = 0f,
    settingsUserScale: Float = 1f,
    settingsOrbit: LauncherSettingsOrbitState? = null,
    onWheelAnchorsChanged: (Map<LauncherWheelCorner, Offset>) -> Unit = {},
    onDoorAnchorsChanged: (Map<LauncherWheelCorner, Offset>) -> Unit = {},
    onPdcRingsChanged: (LauncherPdcRingFrame?) -> Unit = {},
    onHeadlightFrameChanged: (LauncherHeadlightFrame?) -> Unit = {},
    onBodyRigAvailabilityChanged: (Boolean) -> Unit = {},
    onCameraSwitchingChanged: (Boolean) -> Unit = {},
    onModelReadyChanged: (Boolean) -> Unit = {},
    customModelScale: Float = 1f,
    customModelDriveScale: Float = 1f,
    cameraSwitchMode: Int = CAMERA_SWITCH_MODE_DRIVE_AND_PARK,
    projectPdcRings: Boolean = false,
    projectHeadlights: Boolean = false,
    textureSurface: Boolean = true,
    isAvmActive: Boolean = false,
    carHidden: Boolean = false,
    syncedClock: LauncherSynced30FpsClock? = null,
) {
    val activeSyncedClock = rememberSynced30FpsClock(syncedClock)
    val firstDrawComplete by LauncherHomeActivityHolder.firstDrawFlow.collectAsStateWithLifecycle()
    var filamentReady by remember { mutableStateOf(firstDrawComplete || settingsView) }

    LaunchedEffect(firstDrawComplete, settingsView) {
        if (settingsView || firstDrawComplete) {
            if (!filamentReady) {
                // Defer Filament engine startup and SurfaceView creation until after the initial
                // window draw pass has completed, avoiding WindowManager launch timeout (10s).
                delay(400L)
                filamentReady = true
            }
        }
    }

    Box(modifier = modifier) {
        if (showRoad) {
            LauncherVirtualRoad(
                speedKmh = speedKmh,
                steerAngleDeg = steeringDeg,
                steerPreview = steerPreview,
                inDriveGear = inDriveGear,
                syncedClock = activeSyncedClock,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (filamentReady) {
            LauncherCarFilamentModel(
                modifier = Modifier.fillMaxSize(),
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = onDoubleClick,
                onTrunkClick = onTrunkClick,
                onTrunkLongClick = onTrunkLongClick,
                modelRevision = modelRevision,
                paintId = paintId,
                rigState = rigState,
                speedKmh = speedKmh,
                steeringDeg = steeringDeg,
                steerPreview = steerPreview,
                inDriveGear = inDriveGear,
                settingsProgress = settingsProgress,
                settingsUserYawDeg = settingsUserYawDeg,
                settingsUserScale = settingsUserScale,
                customModelScale = customModelScale,
                customModelDriveScale = customModelDriveScale,
                cameraSwitchMode = cameraSwitchMode,
                settingsOrbit = settingsOrbit,
                onWheelAnchorsChanged = onWheelAnchorsChanged,
                onDoorAnchorsChanged = onDoorAnchorsChanged,
                onPdcRingsChanged = onPdcRingsChanged,
                onHeadlightFrameChanged = onHeadlightFrameChanged,
                onBodyRigAvailabilityChanged = onBodyRigAvailabilityChanged,
                onCameraSwitchingChanged = onCameraSwitchingChanged,
                onModelReadyChanged = onModelReadyChanged,
                projectPdcRings = projectPdcRings,
                projectHeadlights = projectHeadlights,
                textureSurface = textureSurface,
                isAvmActive = isAvmActive,
                carHidden = carHidden,
                lowPowerPreview = settingsView || settingsProgress > 0.5f,
                syncedClock = activeSyncedClock,
            )
        }
    }
}

@Composable
private fun LauncherCarFilamentModel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onTrunkClick: (() -> Unit)? = null,
    onTrunkLongClick: (() -> Unit)? = null,
    modelRevision: Int,
    paintId: String,
    rigState: LauncherCarRigState,
    speedKmh: Float,
    steeringDeg: Float,
    steerPreview: Boolean,
    inDriveGear: Boolean,
    settingsProgress: Float,
    settingsUserYawDeg: Float,
    settingsUserScale: Float,
    customModelScale: Float = 1f,
    customModelDriveScale: Float = 1f,
    cameraSwitchMode: Int = CAMERA_SWITCH_MODE_DRIVE_AND_PARK,
    settingsOrbit: LauncherSettingsOrbitState?,
    onWheelAnchorsChanged: (Map<LauncherWheelCorner, Offset>) -> Unit,
    onDoorAnchorsChanged: (Map<LauncherWheelCorner, Offset>) -> Unit,
    onPdcRingsChanged: (LauncherPdcRingFrame?) -> Unit,
    onHeadlightFrameChanged: (LauncherHeadlightFrame?) -> Unit,
    onBodyRigAvailabilityChanged: (Boolean) -> Unit,
    onCameraSwitchingChanged: (Boolean) -> Unit = {},
    onModelReadyChanged: (Boolean) -> Unit = {},
    projectPdcRings: Boolean,
    projectHeadlights: Boolean,
    textureSurface: Boolean = true,
    isAvmActive: Boolean = false,
    carHidden: Boolean = false,
    lowPowerPreview: Boolean = false,
    syncedClock: LauncherSynced30FpsClock,
) {
    val contentKey = modelRevision.hashCode()

    Box(modifier = modifier) {
        key(contentKey) {
            LauncherCarFilamentContent(
                modifier = Modifier.fillMaxSize(),
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = onDoubleClick,
                onTrunkClick = onTrunkClick,
                onTrunkLongClick = onTrunkLongClick,
                paintId = paintId,
                rigState = rigState,
                speedKmh = speedKmh,
                steeringDeg = steeringDeg,
                steerPreview = steerPreview,
                inDriveGear = inDriveGear,
                settingsProgress = settingsProgress,
                settingsUserYawDeg = settingsUserYawDeg,
                settingsUserScale = settingsUserScale,
                customModelScale = customModelScale,
                customModelDriveScale = customModelDriveScale,
                cameraSwitchMode = cameraSwitchMode,
                settingsOrbit = settingsOrbit,
                onWheelAnchorsChanged = onWheelAnchorsChanged,
                onDoorAnchorsChanged = onDoorAnchorsChanged,
                onPdcRingsChanged = onPdcRingsChanged,
                onHeadlightFrameChanged = onHeadlightFrameChanged,
                onBodyRigAvailabilityChanged = onBodyRigAvailabilityChanged,
                onCameraSwitchingChanged = onCameraSwitchingChanged,
                onModelReadyChanged = onModelReadyChanged,
                projectPdcRings = projectPdcRings,
                projectHeadlights = projectHeadlights,
                textureSurface = textureSurface,
                isAvmActive = isAvmActive,
                carHidden = carHidden,
                lowPowerPreview = lowPowerPreview,
                syncedClock = syncedClock,
            )
        }
    }
}

@Composable
private fun LauncherCarFilamentContent(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onTrunkClick: (() -> Unit)? = null,
    onTrunkLongClick: (() -> Unit)? = null,
    paintId: String,
    rigState: LauncherCarRigState,
    speedKmh: Float,
    steeringDeg: Float,
    steerPreview: Boolean,
    inDriveGear: Boolean,
    settingsProgress: Float,
    settingsUserYawDeg: Float,
    settingsUserScale: Float,
    customModelScale: Float = 1f,
    customModelDriveScale: Float = 1f,
    cameraSwitchMode: Int = CAMERA_SWITCH_MODE_DRIVE_AND_PARK,
    settingsOrbit: LauncherSettingsOrbitState?,
    onWheelAnchorsChanged: (Map<LauncherWheelCorner, Offset>) -> Unit,
    onDoorAnchorsChanged: (Map<LauncherWheelCorner, Offset>) -> Unit,
    onPdcRingsChanged: (LauncherPdcRingFrame?) -> Unit,
    onHeadlightFrameChanged: (LauncherHeadlightFrame?) -> Unit,
    onBodyRigAvailabilityChanged: (Boolean) -> Unit,
    onCameraSwitchingChanged: (Boolean) -> Unit = {},
    onModelReadyChanged: (Boolean) -> Unit = {},
    projectPdcRings: Boolean,
    projectHeadlights: Boolean,
    @Suppress("UNUSED_PARAMETER") textureSurface: Boolean = true,
    isAvmActive: Boolean = false,
    carHidden: Boolean = false,
    lowPowerPreview: Boolean = false,
    syncedClock: LauncherSynced30FpsClock,
) {
    val context = LocalContext.current
    val playSound = rememberPlaySystemClickSound()
    val interactionModifier = if (onClick != null || onLongClick != null || onDoubleClick != null) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {
                playSound()
                onClick?.invoke()
            },
            onLongClick = onLongClick?.let { lc ->
                {
                    playSound()
                    lc()
                }
            },
            onDoubleClick = onDoubleClick?.let { dc ->
                {
                    playSound()
                    dc()
                }
            },
        )
    } else {
        Modifier
    }
    val engine = rememberEngine()
    val pin3dRenderRevision by LauncherAppConfigStore.experimentalPin3dRenderCpu45RevisionFlow
        .collectAsStateWithLifecycle()
    val pin3dRenderCpu45 = remember(context, pin3dRenderRevision) {
        LauncherAppConfigStore.experimentalPin3dRenderCpu45(context)
    }
    LaunchedEffect(engine, pin3dRenderCpu45) {
        LauncherCpuAffinity.setPin3dRenderToCpu45(pin3dRenderCpu45)
        if (pin3dRenderCpu45) {
            delay(1_000L)
            LauncherCpuAffinity.reapply3dRenderAffinityIfEnabledAsync()
        }
    }
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine)
    val modelBuffer by produceState<java.nio.ByteBuffer?>(
        initialValue = LauncherCarModelCache.getCachedBufferOrNull(),
        context,
    ) {
        if (value == null) {
            value = runCatching { LauncherCarModelCache.awaitBuffer(context) }.getOrNull()
        }
    }
    val modelInstance = remember(modelLoader, modelBuffer) {
        val buf = modelBuffer ?: return@remember null
        runCatching {
            modelLoader.createModelInstance(buf.duplicate())
        }.getOrNull() ?: modelLoader.createModelInstance(LAUNCHER_CAR_MODEL_ASSET)
    }
    val currentTransition = settingsProgress.coerceIn(0f, 1f)

    val rigStateRef = rememberUpdatedState(
        rigState.copy(speedKmh = speedKmh, steeringDeg = steeringDeg),
    )
    val steeringDegRef = rememberUpdatedState(steeringDeg)
    val customModelScaleRef = rememberUpdatedState(customModelScale)
    val customModelDriveScaleRef = rememberUpdatedState(customModelDriveScale)
    val currentTransitionRef = rememberUpdatedState(currentTransition)
    val settingsUserYawRef = rememberUpdatedState(settingsUserYawDeg)
    val settingsUserScaleRef = rememberUpdatedState(settingsUserScale)
    val settingsOrbitRef = rememberUpdatedState(settingsOrbit)
    val anchorCallbackRef = rememberUpdatedState(onWheelAnchorsChanged)
    val doorAnchorCallbackRef = rememberUpdatedState(onDoorAnchorsChanged)
    val pdcRingsCallbackRef = rememberUpdatedState(onPdcRingsChanged)
    val headlightFrameCallbackRef = rememberUpdatedState(onHeadlightFrameChanged)
    val rigAvailabilityCallbackRef = rememberUpdatedState(onBodyRigAvailabilityChanged)
    val projectPdcRef = rememberUpdatedState(projectPdcRings)
    val projectHeadlightsRef = rememberUpdatedState(projectHeadlights)
    val isAvmRef = rememberUpdatedState(isAvmActive)
    val settingsProgressRef = rememberUpdatedState(settingsProgress)
    val cameraSwitchingCallbackRef = rememberUpdatedState(onCameraSwitchingChanged)
    val modelReadyCallbackRef = rememberUpdatedState(onModelReadyChanged)
    val backflipAnimationRevision by LauncherAppConfigStore.backflipAnimationRevisionFlow
        .collectAsStateWithLifecycle()
    val backflipAnimationEnabled = remember(context, backflipAnimationRevision) {
        LauncherAppConfigStore.backflipAnimationEnabled(context)
    }
    val backflipAnimationEnabledRef = rememberUpdatedState(backflipAnimationEnabled)
    val overlayPublishHandler = remember { Handler(Looper.getMainLooper()) }
    val frameState = remember(modelInstance) {
        FrameLoopState(
            initialTransition = settingsProgress.coerceIn(0f, 1f),
            initialSteeringDeg = steeringDeg,
            initialRigState = rigState,
        )
    }
    var isDriveLayout by remember(modelInstance) { mutableStateOf(false) }
    var isCameraSwitchingState by remember(modelInstance) { mutableStateOf(false) }
    val inDriveGearRef = rememberUpdatedState(inDriveGear || steerPreview)
    val topShadeOpen by LauncherCar3DModelTrigger.topShadeOpen.collectAsStateWithLifecycle()
    val topShadeClosedEvent by LauncherCar3DModelTrigger.topShadeClosedEvent.collectAsStateWithLifecycle()
    val homePressedEvent by LauncherCar3DModelTrigger.homePressedEvent.collectAsStateWithLifecycle()
    val backPressedEvent by LauncherCar3DModelTrigger.backPressedEvent.collectAsStateWithLifecycle()
    val previousAppPressedEvent by LauncherCar3DModelTrigger.previousAppPressedEvent.collectAsStateWithLifecycle()
    val quickSettingsClosedEvent by LauncherCar3DModelTrigger.quickSettingsClosedEvent.collectAsStateWithLifecycle()
    val camdCamerasClosedEvent by LauncherCar3DModelTrigger.camdCamerasClosedEvent.collectAsStateWithLifecycle()
    val reverseCamerasClosedEvent by LauncherCar3DModelTrigger.reverseCamerasClosedEvent.collectAsStateWithLifecycle()
    val paintChangedEvent by LauncherCar3DModelTrigger.paintChangedEvent.collectAsStateWithLifecycle()
    val settingsOrbitEvent by LauncherCar3DModelTrigger.settingsOrbitEvent.collectAsStateWithLifecycle()
    val parkEngagedEvent by LauncherCar3DModelTrigger.parkEngagedEvent.collectAsStateWithLifecycle(
        minActiveState = Lifecycle.State.CREATED,
    )
    val windowReturnedEvent by LauncherCar3DModelTrigger.windowReturnedEvent.collectAsStateWithLifecycle(
        minActiveState = Lifecycle.State.CREATED,
    )
    val cameraAnimRevision by LauncherAppConfigStore.cameraAnimationRevisionFlow.collectAsStateWithLifecycle()
    val cameraAnimEnabled = remember(context, cameraAnimRevision) {
        LauncherAppConfigStore.cameraAnimationEnabled(context)
    }
    val cameraAnimEnabledRef = rememberUpdatedState(cameraAnimEnabled)
    val startupTopViewDone by LauncherCar3DModelTrigger.startupTopViewDone.collectAsStateWithLifecycle()
    val isStartupTopViewActive = cameraAnimEnabled && !startupTopViewDone
    val lifecycleOwner = LocalLifecycleOwner.current

    val customSceneLifecycleOwner = remember(modelInstance, lifecycleOwner) {
        AdasSceneLifecycleOwner(lifecycleOwner.lifecycle)
    }
    DisposableEffect(customSceneLifecycleOwner, frameState) {
        val sceneResumeObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                frameState.lastFrameNs = 0L
                frameState.lastResumedElapsedMs = SystemClock.elapsedRealtime()
            }
        }
        customSceneLifecycleOwner.lifecycle.addObserver(sceneResumeObserver)
        onDispose {
            customSceneLifecycleOwner.lifecycle.removeObserver(sceneResumeObserver)
            customSceneLifecycleOwner.destroy()
        }
    }

    var modelReady by remember(modelInstance) { mutableStateOf(false) }
    val modelAlpha by rememberSyncedFloatAsState(
        targetValue = if (modelReady) 1f else 0f,
        durationMillis = 1000,
        easing = FastOutSlowInEasing,
        tickDivisor = 1,
        sharedClock = syncedClock,
    )
    LaunchedEffect(modelReady) {
        if (modelReady) {
            // Model has started rendering. Wait for fade-in (1000ms) to complete so model is fully rendered.
            delay(1000L)
            overlayPublishHandler.post {
                modelReadyCallbackRef.value(true)
            }
        } else {
            overlayPublishHandler.post {
                modelReadyCallbackRef.value(false)
            }
        }
    }

    val modelNodeRef = remember(modelInstance) {
        AtomicReference<ModelNode?>(null)
    }
    val paintMaterialsRef = remember { mutableStateOf<List<com.google.android.filament.MaterialInstance>>(emptyList()) }
    // TransformManager is read-only here (wheel anchors); body motion uses authored glTF clips.
    val rigController = remember(modelInstance, engine) {
        modelInstance?.let { LauncherCarRigController.bind(engine, it) }
    }
    val animationController = remember(modelInstance) {
        modelInstance?.let(LauncherCarAnimationController::bind)
    }

    // Trigger 1: Body state (doors, tailgate, sunroof)
    val bodyActive = rigState.doorFlOpen ||
        rigState.doorFrOpen ||
        rigState.doorRlOpen ||
        rigState.doorRrOpen ||
        rigState.tailgateOpen ||
        rigState.sunroofOpen

    // Trigger 2: Camera switching or drive gear blend transition
    val rawTransition = settingsProgress.coerceIn(0f, 1f)
    val transition = if (rawTransition < 0.001f) 0f else rawTransition
    val targetDriveBlend = if (transition > 0.02f || isStartupTopViewActive) {
        0f
    } else if (inDriveGear || steerPreview) {
        1f
    } else {
        0f
    }
    var lastComposeTransition by remember(modelInstance) { mutableFloatStateOf(transition) }
    val isTransitionChanging = abs(transition - lastComposeTransition) > 0.001f
    LaunchedEffect(transition) {
        lastComposeTransition = transition
    }
    val cameraSwitching = isCameraSwitchingState ||
        abs(frameState.currentDriveBlend - targetDriveBlend) > 0.001f ||
        isTransitionChanging

    val startupUptimeMs = remember { SystemClock.uptimeMillis() }
    var initialStartupDone by remember { mutableStateOf(false) }

    // Trigger 3: User orbit interaction
    val orbitInteracting = settingsOrbit?.interacting == true

    // Trigger 4: Initializing / loading
    val isInitializing = !modelReady

    val isTriggerActive = bodyActive || cameraSwitching || orbitInteracting || isInitializing || isStartupTopViewActive

    LaunchedEffect(cameraAnimEnabled) {
        if (!cameraAnimEnabled && !LauncherCar3DModelTrigger.startupTopViewDone.value) {
            LauncherCar3DModelTrigger.notifyStartupTopViewDone()
        }
    }

    LaunchedEffect(
        isTriggerActive,
        targetDriveBlend,
        isAvmActive,
        carHidden,
        topShadeOpen,
        homePressedEvent,
        backPressedEvent,
        previousAppPressedEvent,
        quickSettingsClosedEvent,
        camdCamerasClosedEvent,
        reverseCamerasClosedEvent,
        topShadeClosedEvent,
        paintChangedEvent,
        settingsOrbitEvent,
        parkEngagedEvent,
        startupTopViewDone,
        windowReturnedEvent,
    ) {
        val nowMs = SystemClock.uptimeMillis()
        val isPaintChangeRecent = paintChangedEvent > 0L && (nowMs - paintChangedEvent) < 2_000L
        val isOrbitRecentNow = settingsOrbitEvent > 0L && (nowMs - settingsOrbitEvent) < 2_000L
        val isParkEngageRecent = parkEngagedEvent > 0L && (nowMs - parkEngagedEvent) < 2_000L

        val shouldPause = topShadeOpen ||
            ((isAvmActive || carHidden) && !isPaintChangeRecent && !isParkEngageRecent)

        if (shouldPause) {
            customSceneLifecycleOwner.pauseScene()
        } else {
            // Resume scene immediately when exiting camera/settings, when triggers fire,
            // or when Home/Back/PreviousApp/QuickSettingsClosed/TopShadeClosed/PaintChanged/Orbit/Park/WindowReturned is triggered
            customSceneLifecycleOwner.resumeScene()
            if ((isParkEngageRecent || isPaintChangeRecent) && (isAvmActive || carHidden)) {
                // Keep rendering active for the remaining 2s window when shifted to P or paint changed, then pause
                val parkRem = if (isParkEngageRecent) (2_000L - (nowMs - parkEngagedEvent)).coerceAtLeast(0L) else 0L
                val paintRem = if (isPaintChangeRecent) (2_000L - (nowMs - paintChangedEvent)).coerceAtLeast(0L) else 0L
                val remaining = maxOf(parkRem, paintRem)
                if (remaining > 0L) delay(remaining)
                customSceneLifecycleOwner.pauseScene()
            } else if (!isTriggerActive) {
                // At system startup, keep rendering active for 5 seconds before freezing.
                // For subsequent events (e.g. camera/settings exit, paint/orbit change, Home/Back press), keep active for 2 seconds.
                val baseWaitMs = if (!initialStartupDone) {
                    val elapsed = nowMs - startupUptimeMs
                    (5_000L - elapsed).coerceAtLeast(0L)
                } else {
                    2_000L
                }
                val paintRem = if (isPaintChangeRecent) (2_000L - (nowMs - paintChangedEvent)).coerceAtLeast(0L) else 0L
                val orbitRem = if (isOrbitRecentNow) (2_000L - (nowMs - settingsOrbitEvent)).coerceAtLeast(0L) else 0L
                val waitMs = maxOf(baseWaitMs, paintRem, orbitRem)
                if (waitMs > 0L) {
                    delay(waitMs)
                }
                initialStartupDone = true
                customSceneLifecycleOwner.pauseScene()
            }
        }
    }

    DisposableEffect(lifecycleOwner, lowPowerPreview) {
        if (lowPowerPreview) {
            return@DisposableEffect onDispose { overlayPublishHandler.removeCallbacksAndMessages(null) }
        }
        var wasHostPaused = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    wasHostPaused = true
                    LauncherCarSurfaceRecovery.onHomePaused()
                }
                Lifecycle.Event.ON_RESUME -> {
                    LauncherCarSurfaceRecovery.onHomeResumed()
                    if (wasHostPaused) {
                        wasHostPaused = false
                        LauncherCar3DModelTrigger.notifyWindowReturned()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            overlayPublishHandler.removeCallbacksAndMessages(null)
        }
    }

    if (!lowPowerPreview) {
        LaunchedEffect(modelInstance != null) {
            if (modelInstance == null) return@LaunchedEffect
            frameState.lastFrameNs = 0L
            frameState.lastResumedElapsedMs = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(1_000L)
                val resumed = customSceneLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                if (!resumed) continue
                if (LauncherCarSurfaceRecovery.isCovered()) continue
                val last = frameState.lastFrameNs
                if (last == 0L) {
                    if (SystemClock.elapsedRealtime() - frameState.lastResumedElapsedMs > 10_000L) {
                        LauncherCarSurfaceRecovery.onFramesStalled(neverStarted = true)
                    }
                } else if (System.nanoTime() - last > 3_000_000_000L) {
                    LauncherCarSurfaceRecovery.onFramesStalled()
                }
            }
        }
    }

    LaunchedEffect(rigController, animationController) {
        val requiredBodyNodes = setOf(
            LauncherCarRigController.DOOR_FL,
            LauncherCarRigController.DOOR_FR,
            LauncherCarRigController.DOOR_RL,
            LauncherCarRigController.DOOR_RR,
            LauncherCarRigController.TAILGATE,
        )
        rigAvailabilityCallbackRef.value(
            animationController != null &&
                rigController != null &&
                rigController.missingNodes.none(requiredBodyNodes::contains),
        )
    }

    LaunchedEffect(modelInstance, paintId) {
        val instance = modelInstance ?: return@LaunchedEffect
        val materials = LauncherCarPaint.bindMaterials(instance)
        paintMaterialsRef.value = materials
        LauncherCarPaint.apply(materials, paintId)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraSwitchingCallbackRef.value(false)
            modelReadyCallbackRef.value(false)
        }
    }

    var wakeupNonce by remember(modelInstance) { mutableIntStateOf(0) }
    val composeDesiredTickDivisor = resolveCar3dTickDivisor(
        isCameraSwitching = cameraSwitching || orbitInteracting,
        isInitializing = isInitializing,
        isDynamic = true,
    )
    LaunchedEffect(
        rigState,
        cameraSwitching,
        orbitInteracting,
        isInitializing,
        homePressedEvent,
        backPressedEvent,
        previousAppPressedEvent,
        quickSettingsClosedEvent,
        camdCamerasClosedEvent,
        reverseCamerasClosedEvent,
        topShadeClosedEvent,
        paintChangedEvent,
        settingsOrbitEvent,
        parkEngagedEvent,
        startupTopViewDone,
        windowReturnedEvent,
    ) {
        wakeupNonce++
    }

    Box(modifier = modifier) {
        if (modelInstance != null) {
            val initialTransition = settingsProgress.coerceIn(0f, 1f)
            val safeCustomScale = customModelScale.coerceIn(CAR_MODEL_SCALE_MIN, CAR_MODEL_SCALE_MAX)
            val safeCustomDriveScale = customModelDriveScale.coerceIn(CAR_MODEL_DRIVE_SCALE_MIN, CAR_MODEL_DRIVE_SCALE_MAX)
            val initialDriveBlend = if (cameraAnimEnabled) {
                FastOutSlowInEasing.transform(frameState.currentDriveBlend.coerceIn(0f, 1f))
            } else {
                frameState.currentDriveBlend.coerceIn(0f, 1f)
            }
            val initialDriveScale = lerp(1f, 1.21f * safeCustomDriveScale, initialDriveBlend)
            val initialHomeScale = BASE_MODEL_SCALE * initialDriveScale
            val initialSettingsScale = SETTINGS_MODEL_SCALE * safeCustomScale
            val initialBaseScale = lerp(initialHomeScale, initialSettingsScale, initialTransition)
            val initialPinchScale = lerp(
                1f,
                settingsUserScale.coerceIn(
                    SETTINGS_USER_SCALE_MIN,
                    SETTINGS_USER_SCALE_MAX,
                ),
                initialTransition,
            )
            val initialTargetScale = initialBaseScale * initialPinchScale
            val initialTargetDriveModelX = 0f
            val initialDriveModelX = lerp(HOME_MODEL_X, initialTargetDriveModelX, initialDriveBlend)
            val initialDriveModelZ = lerp(HOME_MODEL_Z, -0.21f, initialDriveBlend)

            ThrottledCarSceneView(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = modelAlpha }
                    .pointerInteropFilter { false },
                lifecycle = customSceneLifecycleOwner.lifecycle,
                syncedClock = syncedClock,
                desiredTickDivisor = composeDesiredTickDivisor,
                wakeupNonce = wakeupNonce,
                engine = engine,
                modelLoader = modelLoader,
                cameraNode = cameraNode,
                modelInstance = modelInstance,
                isOpaque = lowPowerPreview,
                initialScale = Scale(if (initialTransition >= 0.99f) 0.001f else initialTargetScale),
                initialPosition = Position(
                    x = lerp(initialDriveModelX, SETTINGS_MODEL_X, initialTransition),
                    y = lerp(HOME_MODEL_Y, SETTINGS_MODEL_Y, initialTransition),
                    z = lerp(initialDriveModelZ, SETTINGS_MODEL_Z, initialTransition),
                ),
                initialRotation = Rotation(
                    y = lerp(
                        MODEL_YAW_DEG,
                        SETTINGS_YAW_DEG + settingsUserYawDeg,
                        initialTransition,
                    ),
                ),
                onModelNodeCreated = { createdNode ->
                    createdNode.isVisible = false
                    modelNodeRef.set(createdNode)
                    animationController?.resetToRestPose()
                },
                onFrame = { frameNs ->
                    var nextTickDivisor = if (!modelReady) {
                        CAR_3D_TRANSITION_TICK_DIVISOR
                    } else {
                        CAR_3D_WAKEUP_TICK_DIVISOR
                    }
                    runCatching {
                        val nowMs = SystemClock.uptimeMillis()
                        val paintChangedEventMs = LauncherCar3DModelTrigger.paintChangedEvent.value
                        val isPaintChangeRecent = (nowMs - paintChangedEventMs) < 2000L

                        val topShadeClosedEventMs = LauncherCar3DModelTrigger.topShadeClosedEvent.value
                        val isTopShadeClosedRecent = (nowMs - topShadeClosedEventMs) < 2000L

                        if (frameState.lastDynamicNs == 0L) {
                            frameState.lastDynamicNs = frameNs
                        }

                        val currentRigState = rigStateRef.value
                        val currentSteering = steeringDegRef.value
                        val orbit = settingsOrbitRef.value
                        val rawTransition = settingsProgressRef.value.coerceIn(0f, 1f)
                        val transition = if (rawTransition < 0.001f) 0f else rawTransition

                        val isHoldingStartupTopView = cameraAnimEnabledRef.value && !LauncherCar3DModelTrigger.startupTopViewDone.value
                        val exitedStartupTopView = frameState.wasHoldingStartupTopView && !isHoldingStartupTopView
                        frameState.wasHoldingStartupTopView = isHoldingStartupTopView

                        val targetDriveBlend = if (transition > 0.02f || isHoldingStartupTopView) {
                            0f
                        } else if (inDriveGearRef.value) {
                            1f
                        } else {
                            0f
                        }

                        // Trigger 1: Opening or closing any door, sunroof, or tailgate/trunk
                        val prevRig = frameState.lastRigState
                        val bodyStateChanged = (currentRigState.doorFlOpen != prevRig.doorFlOpen) ||
                            (currentRigState.doorFrOpen != prevRig.doorFrOpen) ||
                            (currentRigState.doorRlOpen != prevRig.doorRlOpen) ||
                            (currentRigState.doorRrOpen != prevRig.doorRrOpen) ||
                            (currentRigState.tailgateOpen != prevRig.tailgateOpen) ||
                            (currentRigState.sunroofOpen != prevRig.sunroofOpen)

                        // Trigger 2: Camera view switching or settings transition/orbiting
                        val isOrbitActive = (orbit?.interacting == true) ||
                            (orbit != null && abs(orbit.yawVelocityDegPerSec) > 0.1f)
                        val transitionMoving = abs(transition - frameState.lastFrameTransition) > 0.001f
                        val isCameraSwitching = abs(frameState.currentDriveBlend - targetDriveBlend) > 0.001f ||
                            transitionMoving ||
                            isOrbitActive ||
                            frameState.isRollingPtoD
                        frameState.lastFrameTransition = transition

                        val transitionJustStarted = !frameState.wasCameraSwitching && isCameraSwitching
                        if (transitionJustStarted) {
                            frameState.wasCameraSwitching = true
                            overlayPublishHandler.post {
                                isCameraSwitchingState = true
                                cameraSwitchingCallbackRef.value(true)
                            }
                        }

                        // Trigger 3: Initial frames before model is ready
                        val isInitializing = !modelReady

                        val homeEventMs = LauncherCar3DModelTrigger.homePressedEvent.value
                        val backEventMs = LauncherCar3DModelTrigger.backPressedEvent.value
                        val prevAppEventMs = LauncherCar3DModelTrigger.previousAppPressedEvent.value
                        val quickSettingsClosedEventMs = LauncherCar3DModelTrigger.quickSettingsClosedEvent.value
                        val camdCamerasClosedEventMs = LauncherCar3DModelTrigger.camdCamerasClosedEvent.value
                        val reverseCamerasClosedEventMs = LauncherCar3DModelTrigger.reverseCamerasClosedEvent.value
                        val settingsOrbitEventMs = LauncherCar3DModelTrigger.settingsOrbitEvent.value
                        val isOrbitRecentFrame = (nowMs - settingsOrbitEventMs) < 2000L
                        val parkEngagedEventMs = LauncherCar3DModelTrigger.parkEngagedEvent.value
                        val isParkEngageRecentFrame = (nowMs - parkEngagedEventMs) < 2000L
                        val windowReturnedEventMs = LauncherCar3DModelTrigger.windowReturnedEvent.value
                        val isWindowReturnedRecentFrame = (nowMs - windowReturnedEventMs) < 2000L
                        val isActionRecent = (nowMs - homeEventMs) < 2000L ||
                            (nowMs - backEventMs) < 2000L ||
                            (nowMs - prevAppEventMs) < 2000L ||
                            (nowMs - quickSettingsClosedEventMs) < 2000L ||
                            (nowMs - camdCamerasClosedEventMs) < 2000L ||
                            (nowMs - reverseCamerasClosedEventMs) < 2000L ||
                            isTopShadeClosedRecent ||
                            isPaintChangeRecent ||
                            isOrbitRecentFrame ||
                            isParkEngageRecentFrame ||
                            isWindowReturnedRecentFrame

                        val isStartupRecent = (nowMs - startupUptimeMs) < 5000L

                        if (bodyStateChanged || isCameraSwitching || isInitializing || isActionRecent || isStartupRecent || isHoldingStartupTopView) {
                            frameState.lastDynamicNs = frameNs
                        }

                        val isDynamic = (frameNs - frameState.lastDynamicNs) < DYNAMIC_GRACE_PERIOD_NS

                        val dt = if (frameState.lastFrameNs == 0L) {
                            0.033f
                        } else {
                            ((frameNs - frameState.lastFrameNs) / 1_000_000_000f).coerceIn(0f, 0.1f)
                        }

                        if (!cameraAnimEnabledRef.value) {
                            if (targetDriveBlend != frameState.lastTargetDriveBlend) {
                                frameState.lastTargetDriveBlend = targetDriveBlend
                                frameState.currentDriveBlend = targetDriveBlend
                                modelReady = false
                                frameState.preparedFrames = 0
                            }
                            if (frameState.preparedFrames <= 1) {
                                frameState.currentDriveBlend = targetDriveBlend
                            }
                        } else {
                            if (backflipAnimationEnabledRef.value &&
                                !exitedStartupTopView &&
                                !isHoldingStartupTopView &&
                                frameState.lastTargetDriveBlend == 0f &&
                                targetDriveBlend == 1f &&
                                transition <= 0.02f
                            ) {
                                frameState.isRollingPtoD = true
                            }
                            frameState.lastTargetDriveBlend = targetDriveBlend
                            if (frameState.currentDriveBlend < targetDriveBlend) {
                                frameState.currentDriveBlend = (frameState.currentDriveBlend + dt / 1.5f).coerceAtMost(targetDriveBlend)
                            } else if (frameState.currentDriveBlend > targetDriveBlend) {
                                frameState.currentDriveBlend = (frameState.currentDriveBlend - dt / 1.5f).coerceAtLeast(targetDriveBlend)
                            }
                            if (abs(frameState.currentDriveBlend - targetDriveBlend) < 0.001f) {
                                frameState.currentDriveBlend = targetDriveBlend
                                frameState.isRollingPtoD = false
                            }
                        }
                        if (targetDriveBlend != 1f || transition > 0.02f) {
                            frameState.isRollingPtoD = false
                        }

                        val nextDriveLayout = frameState.currentDriveBlend >= 0.5f
                        if (isDriveLayout != nextDriveLayout) {
                            isDriveLayout = nextDriveLayout
                        }

                        frameState.lastSteeringDeg = currentSteering
                        frameState.lastRigState = currentRigState
                        val node = modelNodeRef.get() ?: return@runCatching
                        frameState.lastFrameNs = frameNs
                        if (!lowPowerPreview) {
                            LauncherCarSurfaceRecovery.onFrameObserved()
                        }

                        val driveBlend = if (cameraAnimEnabledRef.value) {
                            FastOutSlowInEasing.transform(frameState.currentDriveBlend.coerceIn(0f, 1f))
                        } else {
                            frameState.currentDriveBlend.coerceIn(0f, 1f)
                        }
                        val safeCustomDriveScale = customModelDriveScaleRef.value.coerceIn(CAR_MODEL_DRIVE_SCALE_MIN, CAR_MODEL_DRIVE_SCALE_MAX)
                        val driveScale = lerp(1f, 1.21f * safeCustomDriveScale, driveBlend)
                        val safeCustomScale = customModelScaleRef.value.coerceIn(CAR_MODEL_SCALE_MIN, CAR_MODEL_SCALE_MAX)

                        val topCamPos = Float3(
                            TOP_CAMERA_POS.x,
                            TOP_CAMERA_POS.y / safeCustomScale,
                            TOP_CAMERA_POS.z / safeCustomScale,
                        )

                        // Continuous camera morph: top/drive ↔ settings (no body yaw while driving).
                        val drivePos = Float3(
                            lerp(topCamPos.x, DRIVE_CAMERA_POS.x, driveBlend),
                            lerp(topCamPos.y, DRIVE_CAMERA_POS.y, driveBlend),
                            lerp(topCamPos.z, DRIVE_CAMERA_POS.z, driveBlend),
                        )
                        val driveTargetPos = Float3(
                            lerp(TOP_CAMERA_TARGET.x, DRIVE_CAMERA_TARGET.x, driveBlend),
                            lerp(TOP_CAMERA_TARGET.y, DRIVE_CAMERA_TARGET.y, driveBlend),
                            lerp(TOP_CAMERA_TARGET.z, DRIVE_CAMERA_TARGET.z, driveBlend),
                        )
                        cameraNode.worldPosition = Position(
                            lerp(drivePos.x, SETTINGS_CAMERA_POS.x, transition),
                            lerp(drivePos.y, SETTINGS_CAMERA_POS.y, transition),
                            lerp(drivePos.z, SETTINGS_CAMERA_POS.z, transition),
                        )
                        cameraNode.lookAt(
                            Position(
                                lerp(driveTargetPos.x, SETTINGS_CAMERA_TARGET.x, transition),
                                lerp(driveTargetPos.y, SETTINGS_CAMERA_TARGET.y, transition),
                                lerp(driveTargetPos.z, SETTINGS_CAMERA_TARGET.z, transition),
                            ),
                        )
                        orbit?.tickFling(dt)
                        val userYaw = orbit?.yawDeg ?: settingsUserYawRef.value
                        val userScale = orbit?.scale ?: settingsUserScaleRef.value
                        val modelYaw = lerp(
                            MODEL_YAW_DEG,
                            SETTINGS_YAW_DEG + userYaw,
                            transition,
                        )

                        val qBase = quaternionFromAxisAngle(Float3(0f, 1f, 0f), modelYaw)
                        if (frameState.isRollingPtoD) {
                            val rollAngle = driveBlend * 360f
                            val qRoll = quaternionFromAxisAngle(Float3(0f, 0f, -1f), rollAngle)
                            node.quaternion = multiplyQuaternions(qRoll, qBase)
                        } else {
                            node.quaternion = qBase
                        }
                        val targetDriveModelX = 0f
                        val driveModelX = lerp(HOME_MODEL_X, targetDriveModelX, driveBlend)
                        val driveModelZ = lerp(HOME_MODEL_Z, -0.21f, driveBlend)
                        node.position = Position(
                            x = lerp(driveModelX, SETTINGS_MODEL_X, transition),
                            y = lerp(HOME_MODEL_Y, SETTINGS_MODEL_Y, transition),
                            z = lerp(driveModelZ, SETTINGS_MODEL_Z, transition),
                        )

                        // Centering watchdog: runs once per second (1_000_000_000 ns) when settings are closed
                        if (frameNs - frameState.lastXCheckNs >= 1_000_000_000L) {
                            frameState.lastXCheckNs = frameNs
                            if (transition < 0.001f) {
                                val expectedX = lerp(HOME_MODEL_X, targetDriveModelX, driveBlend)
                                var watchdogReset = false
                                if (abs(node.position.x - expectedX) > 0.005f) {
                                    DclLogRecorder.log("Watchdog", "Model X shifted to ${node.position.x}, resetting to $expectedX")
                                    node.position = Position(expectedX, node.position.y, node.position.z)
                                    watchdogReset = true
                                }
                                if (abs(cameraNode.worldPosition.x) > 0.005f) {
                                    DclLogRecorder.log("Watchdog", "Camera X shifted to ${cameraNode.worldPosition.x}, resetting to 0")
                                    cameraNode.worldPosition = Position(0f, cameraNode.worldPosition.y, cameraNode.worldPosition.z)
                                    watchdogReset = true
                                }
                                if (watchdogReset) {
                                    val vp = cameraNode.viewport
                                    DclLogRecorder.logModelRender(
                                        transition = transition,
                                        driveBlend = driveBlend,
                                        modelX = node.position.x,
                                        modelY = node.position.y,
                                        modelZ = node.position.z,
                                        modelYaw = modelYaw,
                                        modelScale = frameState.renderedScale,
                                        cameraX = cameraNode.worldPosition.x,
                                        cameraY = cameraNode.worldPosition.y,
                                        cameraZ = cameraNode.worldPosition.z,
                                        targetX = cameraNode.worldPosition.x,
                                        targetY = 0f,
                                        targetZ = 0f,
                                        viewportW = vp?.width ?: 0,
                                        viewportH = vp?.height ?: 0,
                                        customModelScale = safeCustomScale,
                                        userYaw = userYaw,
                                        userScale = userScale,
                                        isAvmActive = isAvmRef.value,
                                        watchdogTriggered = true,
                                    )
                                }
                            }
                        }

                        val viewport = cameraNode.viewport
                        val widthPx = viewport?.width ?: 0
                        val heightPx = viewport?.height ?: 0

                        val homeScale = BASE_MODEL_SCALE * driveScale
                        val settingsScale = SETTINGS_MODEL_SCALE * safeCustomScale
                        val baseScale = lerp(homeScale, settingsScale, transition)
                        val pinchScale = lerp(
                            1f,
                            userScale.coerceIn(
                                SETTINGS_USER_SCALE_MIN,
                                SETTINGS_USER_SCALE_MAX,
                            ),
                            transition,
                        )
                        val targetScale = baseScale * pinchScale
                        if (!modelReady) {
                            frameState.renderedScale = targetScale
                            node.scale = Scale(frameState.renderedScale)
                            node.isVisible = false
                            frameState.preparedFrames++
                            val materialsReady = paintMaterialsRef.value.isNotEmpty() || frameState.preparedFrames >= 30
                            val animatorReady = animationController == null || animationController.isSettled || frameState.preparedFrames >= 20
                            if (frameState.preparedFrames >= PREPARED_FRAMES_WARMUP && materialsReady && animatorReady && widthPx > 0 && heightPx > 0) {
                                modelReady = true
                                node.isVisible = true
                            }
                        } else {
                            node.isVisible = true
                            if (abs(targetScale - frameState.renderedScale) < 0.0015f) {
                                frameState.renderedScale = targetScale
                            } else {
                                val scaleResponse = 1f - exp(-dt * 12f)
                                frameState.renderedScale += (targetScale - frameState.renderedScale) * scaleResponse
                            }
                            node.scale = Scale(frameState.renderedScale)
                        }

                        if (modelReady && widthPx > 0 && heightPx > 0) {
                            if (frameState.firstRenderTimeMs == 0L) {
                                frameState.firstRenderTimeMs = nowMs
                                DclLogRecorder.log("ModelRender", "First 3D model frame rendered in top view at nowMs=$nowMs")
                            } else if (!LauncherCar3DModelTrigger.startupTopViewDone.value) {
                                if (!cameraAnimEnabledRef.value || nowMs - frameState.firstRenderTimeMs >= 2000L) {
                                    DclLogRecorder.log("ModelRender", "Startup top view hold 2000ms completed, transitioning view")
                                    LauncherCar3DModelTrigger.notifyStartupTopViewDone()
                                }
                            }
                        }

                        if (DclLogRecorder.isLoggingActive()) {
                            val posChanged = abs(node.position.x - frameState.dclLastPosX) > 0.0001f || abs(cameraNode.worldPosition.x - frameState.dclLastCamX) > 0.0001f
                            val timeElapsed = (frameNs - frameState.dclLastLogNs) >= 250_000_000L
                            if (posChanged || timeElapsed) {
                                frameState.dclLastLogNs = frameNs
                                frameState.dclLastPosX = node.position.x
                                frameState.dclLastCamX = cameraNode.worldPosition.x
                                DclLogRecorder.logModelRender(
                                    transition = transition,
                                    driveBlend = driveBlend,
                                    modelX = node.position.x,
                                    modelY = node.position.y,
                                    modelZ = node.position.z,
                                    modelYaw = modelYaw,
                                    modelScale = frameState.renderedScale,
                                    cameraX = cameraNode.worldPosition.x,
                                    cameraY = cameraNode.worldPosition.y,
                                    cameraZ = cameraNode.worldPosition.z,
                                    targetX = 0f,
                                    targetY = 0f,
                                    targetZ = 0f,
                                    viewportW = widthPx,
                                    viewportH = heightPx,
                                    customModelScale = safeCustomScale,
                                    userYaw = userYaw,
                                    userScale = userScale,
                                    isAvmActive = isAvmRef.value,
                                    watchdogTriggered = false,
                                )
                            }
                        }

                        animationController?.update(rigStateRef.value, dt, modelReady = modelReady)
                        val isStillSwitching = abs(frameState.currentDriveBlend - targetDriveBlend) > 0.001f ||
                            transitionMoving ||
                            isOrbitActive ||
                            frameState.isRollingPtoD
                        val transitionJustFinished = frameState.wasCameraSwitching && !isStillSwitching
                        frameState.wasCameraSwitching = isStillSwitching

                        if (transitionJustFinished) {
                            overlayPublishHandler.post {
                                isCameraSwitchingState = false
                                cameraSwitchingCallbackRef.value(false)
                            }
                        }

                        nextTickDivisor = resolveCar3dTickDivisor(
                            isCameraSwitching = isStillSwitching,
                            isInitializing = !modelReady,
                            isDynamic = isDynamic,
                        )

                        val publishNs = 1_000_000_000L // 1 second interval
                        if (!isStillSwitching && (transitionJustFinished || frameState.lastAnchorPublishNs == 0L || frameNs - frameState.lastAnchorPublishNs >= publishNs)) {
                            frameState.lastAnchorPublishNs = frameNs
                            val wheels = runCatching {
                                rigController?.projectWheelAnchors(
                                    cameraNode = cameraNode,
                                    viewportWidthPx = widthPx,
                                    viewportHeightPx = heightPx,
                                )
                            }.getOrNull()
                            val doors = runCatching {
                                rigController?.projectDoorAnchors(
                                    cameraNode = cameraNode,
                                    viewportWidthPx = widthPx,
                                    viewportHeightPx = heightPx,
                                )
                            }.getOrNull()
                            val pdc = if (projectPdcRef.value) {
                                runCatching {
                                    rigController?.projectPdcRings(
                                        cameraNode = cameraNode,
                                        viewportWidthPx = widthPx,
                                        viewportHeightPx = heightPx,
                                        driveBlend = driveBlend,
                                    )
                                }.getOrNull()
                            } else {
                                null
                            }
                            val headlights = if (projectHeadlightsRef.value && currentTransitionRef.value < 0.5f) {
                                runCatching {
                                    rigController?.projectHeadlightBeams(
                                        cameraNode = cameraNode,
                                        viewportWidthPx = widthPx,
                                        viewportHeightPx = heightPx,
                                        driveBlend = driveBlend,
                                    )
                                }.getOrNull()
                            } else {
                                null
                            }
                            overlayPublishHandler.post {
                                anchorCallbackRef.value(wheels.orEmpty())
                                doorAnchorCallbackRef.value(doors.orEmpty())
                                pdcRingsCallbackRef.value(pdc)
                                headlightFrameCallbackRef.value(headlights)
                            }
                        }
                    }
                    nextTickDivisor
                },
            )
        }
        if (onClick != null || onLongClick != null || onDoubleClick != null) {
            val tapDriveBlend = if (isDriveLayout) 1f else 0f
            val tapWidth = lerp(180.dp, 170.dp, tapDriveBlend)
            val tapHeight = lerp(180.dp, 210.dp, tapDriveBlend)
            val tapOffsetY = lerp((-35).dp, 115.dp, tapDriveBlend)

            Box(
                modifier = Modifier
                    .size(width = tapWidth, height = tapHeight)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        translationY = tapOffsetY.toPx()
                    }
                    .then(interactionModifier),
            )
        }
        if (onTrunkClick != null || onTrunkLongClick != null) {
            if (!isDriveLayout) {
                val trunkWidth = 160.dp
                val trunkHeight = 90.dp
                val trunkOffsetY = 95.dp
                val trunkInteractionModifier = Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onTrunkClick?.invoke() },
                    onLongClick = { onTrunkLongClick?.invoke() },
                    onDoubleClick = { onDoubleClick?.invoke() },
                )

                Box(
                    modifier = Modifier
                        .size(width = trunkWidth, height = trunkHeight)
                        .align(Alignment.Center)
                        .graphicsLayer {
                            translationY = trunkOffsetY.toPx()
                        }
                        .then(trunkInteractionModifier),
                )
            }
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * Filament [TextureView] scene host backed by [LauncherSynced30FpsClock] instead of
 * SceneView's hardcoded 60 Hz `withFrameNanos` loop.
 *
 * This genuinely throttles Filament `Renderer.beginFrame`/`render`/`endFrame` and GPU
 * submission to 30 FPS during view transitions/warmup, 15 FPS during 2-second wakeups,
 * ~1 FPS in static open-door holds, and 0 FPS when paused, while staying phase-locked
 * to the virtual road (30 FPS) and PDC overlay (15 FPS).
 */
@Composable
private fun ThrottledCarSceneView(
    modifier: Modifier = Modifier,
    lifecycle: Lifecycle,
    syncedClock: LauncherSynced30FpsClock,
    desiredTickDivisor: Int,
    wakeupNonce: Int,
    engine: Engine,
    modelLoader: ModelLoader,
    cameraNode: CameraNode,
    modelInstance: ModelInstance,
    isOpaque: Boolean,
    initialScale: Scale,
    initialPosition: Position,
    initialRotation: Rotation,
    onModelNodeCreated: (ModelNode) -> Unit,
    onFrame: (frameTimeNanos: Long) -> Int,
) {
    if (LocalInspectionMode.current) {
        Box(modifier = modifier)
        return
    }

    val context = LocalContext.current
    val environmentLoader = rememberEnvironmentLoader(engine)
    val view = rememberView(engine)
    val renderer = rememberRenderer(engine)
    val scene = rememberScene(engine)
    val environment = rememberEnvironment(environmentLoader, isOpaque = isOpaque)
    val mainLightNode = rememberMainLightNode(engine)
    val fillLightNode = rememberFillLightNode(engine)
    val collisionSystem = rememberCollisionSystem(view)
    val nodeManager = remember(scene, collisionSystem) { SceneNodeManager(scene, collisionSystem) }

    SideEffect {
        scene.indirectLight = environment.indirectLight
        scene.skybox = environment.skybox
        view.scene = scene
        view.camera = cameraNode.camera
        cameraNode.collisionSystem = collisionSystem
        cameraNode.setView(view)
        view.blendMode = if (isOpaque) BlendMode.OPAQUE else BlendMode.TRANSLUCENT
    }

    LaunchedEffect(view) {
        view.applyRenderQuality(RenderQuality.Default)
    }

    LaunchedEffect(renderer, isOpaque) {
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = doubleArrayOf(0.0, 0.0, 0.0, if (isOpaque) 1.0 else 0.0)
        }
    }

    DisposableEffect(cameraNode) {
        nodeManager.addNode(cameraNode)
        onDispose { nodeManager.removeNode(cameraNode) }
    }
    DisposableEffect(mainLightNode) {
        nodeManager.addNode(mainLightNode)
        onDispose { nodeManager.removeNode(mainLightNode) }
    }
    DisposableEffect(fillLightNode) {
        nodeManager.addNode(fillLightNode)
        onDispose { nodeManager.removeNode(fillLightNode) }
    }

    val contentRoot = remember(engine) { Node(engine) }
    val autoCenterState = remember { SceneAutoCenterState() }
    DisposableEffect(contentRoot) {
        nodeManager.addNode(contentRoot)
        onDispose { nodeManager.removeNode(contentRoot) }
    }

    @Suppress("DEPRECATION")
    val display = remember(context) {
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    }
    val sceneRenderer = remember(engine, view, renderer) {
        SceneRenderer(engine, view, renderer)
    }
    SideEffect {
        sceneRenderer.onSurfaceResized = { _, _ ->
            cameraNode.updateProjection()
        }
        sceneRenderer.onSurfaceReady = { _ ->
            syncedClock.wakeNow()
        }
    }
    DisposableEffect(sceneRenderer) {
        onDispose { sceneRenderer.destroy() }
    }

    val modelNode = remember(engine, modelInstance) {
        ModelNode(
            modelInstance = modelInstance,
            autoAnimate = false,
        ).apply {
            scale = initialScale
            position = initialPosition
            rotation = initialRotation
            onModelNodeCreated(this)
        }
    }
    DisposableEffect(modelNode, contentRoot, nodeManager) {
        modelNode.parent = contentRoot
        autoCenterState.reset()
        onDispose {
            if (modelNode.parent == contentRoot) modelNode.parent = null
            nodeManager.removeNode(modelNode)
            modelNode.destroy()
        }
    }

    var isResumed by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                isResumed = true
            }
            override fun onPause(owner: LifecycleOwner) {
                isResumed = false
            }
        }
        lifecycle.addObserver(observer)
        isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    val currentOnFrame = rememberUpdatedState(onFrame)
    val desiredDivisorRef = rememberUpdatedState(desiredTickDivisor)
    val handleRef = remember { AtomicReference<LauncherSynced30FpsClock.ListenerHandle?>(null) }
    val appliedDivisorRef = remember { AtomicInteger(desiredTickDivisor) }

    LaunchedEffect(desiredTickDivisor, wakeupNonce) {
        val handle = handleRef.get() ?: return@LaunchedEffect
        if (desiredTickDivisor < appliedDivisorRef.get()) {
            appliedDivisorRef.set(desiredTickDivisor)
            handle.setTickDivisor(desiredTickDivisor)
        }
    }

    DisposableEffect(syncedClock, sceneRenderer, modelNode, isResumed) {
        if (!isResumed) {
            handleRef.set(null)
            return@DisposableEffect onDispose {}
        }
        val startDivisor = desiredDivisorRef.value
        appliedDivisorRef.set(startDivisor)
        val handle = syncedClock.registerListenerWithHandle(tickDivisor = startDivisor) { frameTimeNanos, _ ->
            var nextDivisor = desiredDivisorRef.value
            sceneRenderer.renderFrame(frameTimeNanos) {
                modelLoader.updateLoad()
                modelNode.onFrame(frameTimeNanos)
                autoCenterState.maybeCenter(contentRoot)
                nextDivisor = currentOnFrame.value(frameTimeNanos)
            }
            if (appliedDivisorRef.getAndSet(nextDivisor) != nextDivisor) {
                handleRef.get()?.setTickDivisor(nextDivisor)
            }
        }
        handleRef.set(handle)
        onDispose {
            handleRef.compareAndSet(handle, null)
            handle.unregister()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).also { tv ->
                sceneRenderer.attachToTextureView(tv, isOpaque, ctx, display, onTouch = null)
            }
        },
        update = {},
    )
}

/**
 * Custom [LifecycleOwner] for the 3D SceneView.
 * Allows independent pause/resume control based on custom vehicle and camera triggers,
 * preventing rendering freezes when external freeform apps take focus.
 */
internal class AdasSceneLifecycleOwner(
    private val hostLifecycle: Lifecycle,
) : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    private val hostObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_DESTROY -> {
                if (registry.currentState != Lifecycle.State.DESTROYED) {
                    registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                }
            }
            else -> Unit
        }
    }

    init {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        hostLifecycle.addObserver(hostObserver)
    }

    fun resumeScene() {
        if (hostLifecycle.currentState == Lifecycle.State.DESTROYED) return
        if (registry.currentState != Lifecycle.State.RESUMED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    fun pauseScene() {
        if (registry.currentState == Lifecycle.State.RESUMED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
    }

    fun destroy() {
        hostLifecycle.removeObserver(hostObserver)
        if (registry.currentState != Lifecycle.State.DESTROYED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }
}
