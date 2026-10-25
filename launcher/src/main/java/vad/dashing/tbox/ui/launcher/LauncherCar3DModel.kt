package vad.dashing.tbox.ui.launcher

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import vad.dashing.tbox.DclLogRecorder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.exp

// Calibrated against the GLB's authored forward axis so the nose points to screen top.
private const val MODEL_YAW_DEG = 135f
private const val SETTINGS_YAW_DEG = 45f

private val TOP_CAMERA_POS = Float3(0f, 4.2f, 0.70f)
private val TOP_CAMERA_TARGET = Float3(0f, 0f, 0f)
/** Raised by +15° in elevation pitch to align the car perspective with the road canvas. */
private val DRIVE_CAMERA_POS = Float3(0f, 2.20f, 2.85f)
private val DRIVE_CAMERA_TARGET = Float3(0f, 0.20f, -2.2f)
/** Settings camera keeps the whole car in frame and clear of the near clipping plane. */
private val SETTINGS_CAMERA_POS = Float3(1.15f, 1.05f, 2.55f)
private val SETTINGS_CAMERA_TARGET = Float3(0.12f, 0.18f, 0f)

private const val BASE_MODEL_SCALE = 0.42f
private const val DYNAMIC_FRAME_INTERVAL_NS = 28_000_000L // 30 FPS target with hysteresis / VSYNC tolerance margin (~28ms)
private const val DYNAMIC_GRACE_PERIOD_NS = 2_000_000_000L // 2 seconds grace period
// Settings uses the full SceneView bounds; visual size is controlled only here/camera.
private const val SETTINGS_MODEL_SCALE = 0.48f
private const val HOME_MODEL_X = 0f
private const val DRIVE_MODEL_X = 0.12f
private const val SETTINGS_MODEL_X = 0.14f
private const val HOME_MODEL_Y = -0.1f
private const val SETTINGS_MODEL_Y = -0.02f
private const val HOME_MODEL_Z = 0f
private const val SETTINGS_MODEL_Z = 0f

/** Offline Filament 3D car (Dashing 720) with pivot rig, paint colors and drive camera. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherCar3DModel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
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
    customModelScale: Float = 1f,
    cameraSwitchMode: Int = CAMERA_SWITCH_MODE_DRIVE_AND_PARK,
    projectPdcRings: Boolean = false,
    projectHeadlights: Boolean = false,
    textureSurface: Boolean = false,
    isAvmActive: Boolean = false,
    carHidden: Boolean = false,
) {
    Box(modifier = modifier) {
        if (showRoad) {
            LauncherVirtualRoad(
                speedKmh = speedKmh,
                steerAngleDeg = steeringDeg,
                steerPreview = steerPreview,
                inDriveGear = inDriveGear,
                modifier = Modifier.fillMaxSize(),
            )
        }
        LauncherCarFilamentModel(
            modifier = Modifier.fillMaxSize(),
            onClick = onClick,
            onLongClick = onLongClick,
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
            cameraSwitchMode = cameraSwitchMode,
            settingsOrbit = settingsOrbit,
            onWheelAnchorsChanged = onWheelAnchorsChanged,
            onDoorAnchorsChanged = onDoorAnchorsChanged,
            onPdcRingsChanged = onPdcRingsChanged,
            onHeadlightFrameChanged = onHeadlightFrameChanged,
            onBodyRigAvailabilityChanged = onBodyRigAvailabilityChanged,
            projectPdcRings = projectPdcRings,
            projectHeadlights = projectHeadlights,
            textureSurface = textureSurface,
            isAvmActive = isAvmActive,
            carHidden = carHidden,
            lowPowerPreview = settingsView || settingsProgress > 0.5f,
        )
    }
}

@Composable
private fun LauncherCarFilamentModel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
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
    cameraSwitchMode: Int = CAMERA_SWITCH_MODE_DRIVE_AND_PARK,
    settingsOrbit: LauncherSettingsOrbitState?,
    onWheelAnchorsChanged: (Map<LauncherWheelCorner, Offset>) -> Unit,
    onDoorAnchorsChanged: (Map<LauncherWheelCorner, Offset>) -> Unit,
    onPdcRingsChanged: (LauncherPdcRingFrame?) -> Unit,
    onHeadlightFrameChanged: (LauncherHeadlightFrame?) -> Unit,
    onBodyRigAvailabilityChanged: (Boolean) -> Unit,
    projectPdcRings: Boolean,
    projectHeadlights: Boolean,
    textureSurface: Boolean = false,
    isAvmActive: Boolean = false,
    carHidden: Boolean = false,
    lowPowerPreview: Boolean = false,
) {
    val context = LocalContext.current
    val cameraAnimRevision by LauncherAppConfigStore.cameraAnimationRevisionFlow.collectAsStateWithLifecycle()
    val cameraAnimEnabled = remember(context, cameraAnimRevision) {
        LauncherAppConfigStore.cameraAnimationEnabled(context)
    }
    val contentKey = if (cameraAnimEnabled) {
        modelRevision.hashCode()
    } else {
        modelRevision.hashCode() * 31 + inDriveGear.hashCode()
    }

    Box(modifier = modifier) {
        key(contentKey) {
            LauncherCarFilamentContent(
                modifier = Modifier.fillMaxSize(),
                onClick = onClick,
                onLongClick = onLongClick,
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
                cameraSwitchMode = cameraSwitchMode,
                settingsOrbit = settingsOrbit,
                onWheelAnchorsChanged = onWheelAnchorsChanged,
                onDoorAnchorsChanged = onDoorAnchorsChanged,
                onPdcRingsChanged = onPdcRingsChanged,
                onHeadlightFrameChanged = onHeadlightFrameChanged,
                onBodyRigAvailabilityChanged = onBodyRigAvailabilityChanged,
                projectPdcRings = projectPdcRings,
                projectHeadlights = projectHeadlights,
                textureSurface = textureSurface,
                isAvmActive = isAvmActive,
                carHidden = carHidden,
                lowPowerPreview = lowPowerPreview,
            )
        }
    }
}

@Composable
private fun LauncherCarFilamentContent(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
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
    cameraSwitchMode: Int = CAMERA_SWITCH_MODE_DRIVE_AND_PARK,
    settingsOrbit: LauncherSettingsOrbitState?,
    onWheelAnchorsChanged: (Map<LauncherWheelCorner, Offset>) -> Unit,
    onDoorAnchorsChanged: (Map<LauncherWheelCorner, Offset>) -> Unit,
    onPdcRingsChanged: (LauncherPdcRingFrame?) -> Unit,
    onHeadlightFrameChanged: (LauncherHeadlightFrame?) -> Unit,
    onBodyRigAvailabilityChanged: (Boolean) -> Unit,
    projectPdcRings: Boolean,
    projectHeadlights: Boolean,
    textureSurface: Boolean = false,
    isAvmActive: Boolean = false,
    carHidden: Boolean = false,
    lowPowerPreview: Boolean = false,
) {
    val context = LocalContext.current
    val interactionModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onClick?.invoke() },
            onLongClick = { onLongClick?.invoke() },
        )
    } else {
        Modifier
    }
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine)
    val modelInstance = remember(modelLoader) {
        runCatching {
            val buffer = LauncherCarModelCache.getBuffer(context)
            modelLoader.createModelInstance(buffer)
        }.getOrNull() ?: modelLoader.createModelInstance(LAUNCHER_CAR_MODEL_ASSET)
    }
    val currentTransition = settingsProgress.coerceIn(0f, 1f)

    val rigStateRef = rememberUpdatedState(
        rigState.copy(speedKmh = speedKmh, steeringDeg = steeringDeg),
    )
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
    val carHiddenRef = rememberUpdatedState(carHidden)
    val cameraSwitchModeRef = rememberUpdatedState(cameraSwitchMode)
    val overlayPublishHandler = remember { Handler(Looper.getMainLooper()) }
    var lastFrameNs by remember { mutableLongStateOf(0L) }
    var lastAnchorPublishNs by remember { mutableLongStateOf(0L) }
    var lastXCheckNs by remember(modelInstance) { mutableLongStateOf(0L) }
    var dclLastLogNs by remember(modelInstance) { mutableLongStateOf(0L) }
    var dclLastPosX by remember(modelInstance) { mutableFloatStateOf(0f) }
    var dclLastCamX by remember(modelInstance) { mutableFloatStateOf(0f) }
    var mountNs by remember(modelInstance) { mutableLongStateOf(0L) }
    var lastDynamicNs by remember(modelInstance) { mutableLongStateOf(0L) }
    val inDriveGearRef = rememberUpdatedState(inDriveGear || steerPreview)
    val cameraAnimRevision by LauncherAppConfigStore.cameraAnimationRevisionFlow.collectAsStateWithLifecycle()
    val cameraAnimEnabled = remember(context, cameraAnimRevision) {
        LauncherAppConfigStore.cameraAnimationEnabled(context)
    }
    val cameraAnimEnabledRef = rememberUpdatedState(cameraAnimEnabled)
    var currentDriveBlend by remember(modelInstance) {
        mutableFloatStateOf(if (inDriveGear || steerPreview) 1f else 0f)
    }
    var lastTargetDriveBlend by remember(modelInstance) {
        mutableFloatStateOf(if (inDriveGear || steerPreview) 1f else 0f)
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    var preparedFrames by remember(modelInstance) { mutableIntStateOf(0) }
    var renderedScale by remember(modelInstance) { mutableFloatStateOf(BASE_MODEL_SCALE) }
    var modelReady by remember(modelInstance) { mutableStateOf(modelInstance != null) }
    val modelAlpha by animateFloatAsState(
        targetValue = if (modelReady) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "launcherCarModelAlpha",
    )

    var lastSteeringDeg by remember(modelInstance) { mutableFloatStateOf(steeringDeg) }
    var lastRigState by remember(modelInstance) { mutableStateOf(rigState) }

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

    DisposableEffect(lifecycleOwner, lowPowerPreview) {
        if (lowPowerPreview) {
            return@DisposableEffect onDispose { overlayPublishHandler.removeCallbacksAndMessages(null) }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> LauncherCarSurfaceRecovery.onHomePaused()
                Lifecycle.Event.ON_RESUME -> LauncherCarSurfaceRecovery.onHomeResumed()
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
        lastFrameNs = 0L
        val modelLoadedAt = SystemClock.elapsedRealtime()
        while (isActive) {
            delay(1_000L)
            val resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!resumed) continue
            if (LauncherCarSurfaceRecovery.isCovered()) continue
            val last = lastFrameNs
            if (last == 0L) {
                if (SystemClock.elapsedRealtime() - modelLoadedAt > 10_000L) {
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

    val alwaysResumedLifecycle = remember {
        val owner = object : LifecycleOwner {
            override val lifecycle: Lifecycle get() = error("Dummy owner")
        }
        LifecycleRegistry(owner).apply {
            currentState = Lifecycle.State.RESUMED
        }
    }

    Box(modifier = modifier) {
        if (modelInstance != null) {
            SceneView(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = modelAlpha }
                    .pointerInteropFilter { false },
                lifecycle = alwaysResumedLifecycle,
                // TextureSurface composites inline so Compose overlays (tire/door badges)
                // draw above the model; the default SurfaceView stays for the home panel
                // where overlays sit beside the car silhouette.
                surfaceType = if (textureSurface) SurfaceType.TextureSurface else SurfaceType.Surface,
                engine = engine,
                modelLoader = modelLoader,
                cameraNode = cameraNode,
                cameraManipulator = null,
                isOpaque = lowPowerPreview,
                autoFitContent = false,
                onFrame = { frameNs ->
                    runCatching {
                        if (isAvmRef.value || carHiddenRef.value) {
                            val node = modelNodeRef.get()
                            if (node != null && node.isVisible) {
                                node.isVisible = false
                            }
                            return@runCatching
                        }

                        if (mountNs == 0L) {
                            mountNs = frameNs
                            lastDynamicNs = frameNs
                        }

                        val currentRigState = rigStateRef.value
                        val currentSteering = steeringDeg
                        val orbit = settingsOrbitRef.value
                        val rawTransition = settingsProgress.coerceIn(0f, 1f)
                        val transition = if (rawTransition < 0.001f) 0f else rawTransition

                        val targetDriveBlend = if (transition > 0.02f) {
                            0f
                        } else if (inDriveGearRef.value) {
                            1f
                        } else {
                            0f
                        }

                        // Trigger 1: Initial 2 seconds after rendering starts
                        val isWithinInitial2Sec = (frameNs - mountNs) < DYNAMIC_GRACE_PERIOD_NS

                        // Trigger 2: Opening or closing any door, sunroof, or tailgate/trunk
                        val bodyStateChanged = (currentRigState.doorFlOpen != lastRigState.doorFlOpen) ||
                            (currentRigState.doorFrOpen != lastRigState.doorFrOpen) ||
                            (currentRigState.doorRlOpen != lastRigState.doorRlOpen) ||
                            (currentRigState.doorRrOpen != lastRigState.doorRrOpen) ||
                            (currentRigState.tailgateOpen != lastRigState.tailgateOpen) ||
                            (currentRigState.sunroofOpen != lastRigState.sunroofOpen)

                        // Trigger 4: Camera view switching or settings transition/orbiting
                        val isCameraSwitching = abs(currentDriveBlend - targetDriveBlend) > 0.001f ||
                            transition > 0.001f ||
                            (orbit?.interacting == true)

                        val wasDynamic = (frameNs - lastDynamicNs) < DYNAMIC_GRACE_PERIOD_NS

                        if (isWithinInitial2Sec || bodyStateChanged || isCameraSwitching || !modelReady || preparedFrames < 3) {
                            lastDynamicNs = frameNs
                        }

                        val isDynamic = (frameNs - lastDynamicNs) < DYNAMIC_GRACE_PERIOD_NS

                        if (!wasDynamic && isDynamic) {
                            lastFrameNs = 0L
                        }

                        // Freeze 3D Filament rendering completely when stationary & idle
                        if (!isDynamic) {
                            return@runCatching
                        }

                        if (lastFrameNs != 0L && frameNs - lastFrameNs < DYNAMIC_FRAME_INTERVAL_NS) {
                            return@runCatching
                        }

                        val dt = if (lastFrameNs == 0L) {
                            0.033f
                        } else {
                            ((frameNs - lastFrameNs) / 1_000_000_000f).coerceAtMost(0.08f)
                        }

                        if (!cameraAnimEnabledRef.value) {
                            if (targetDriveBlend != lastTargetDriveBlend) {
                                lastTargetDriveBlend = targetDriveBlend
                                currentDriveBlend = targetDriveBlend
                                modelReady = false
                                preparedFrames = 0
                            }
                            if (preparedFrames <= 1) {
                                currentDriveBlend = targetDriveBlend
                            }
                        } else {
                            lastTargetDriveBlend = targetDriveBlend
                            if (preparedFrames <= 1) {
                                currentDriveBlend = targetDriveBlend
                            } else if (currentDriveBlend < targetDriveBlend) {
                                currentDriveBlend = (currentDriveBlend + dt / 1.5f).coerceAtMost(targetDriveBlend)
                            } else if (currentDriveBlend > targetDriveBlend) {
                                currentDriveBlend = (currentDriveBlend - dt / 1.5f).coerceAtLeast(targetDriveBlend)
                            }
                            if (abs(currentDriveBlend - targetDriveBlend) < 0.001f) {
                                currentDriveBlend = targetDriveBlend
                            }
                        }

                        lastSteeringDeg = currentSteering
                        lastRigState = currentRigState
                        val node = modelNodeRef.get() ?: return@runCatching
                        lastFrameNs = frameNs
                        if (!lowPowerPreview) {
                            LauncherCarSurfaceRecovery.onFrameObserved()
                        }

                        val driveBlend = if (cameraAnimEnabledRef.value) {
                            FastOutSlowInEasing.transform(currentDriveBlend.coerceIn(0f, 1f))
                        } else {
                            currentDriveBlend.coerceIn(0f, 1f)
                        }
                        val driveScale = lerp(1f, 1.21f, driveBlend)
                        val safeCustomScale = customModelScale.coerceIn(CAR_MODEL_SCALE_MIN, CAR_MODEL_SCALE_MAX)

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
                        node.rotation = Rotation(y = modelYaw)
                        val targetDriveModelX = if (cameraSwitchModeRef.value == CAMERA_SWITCH_MODE_PAS_AND_PARK) 0f else DRIVE_MODEL_X
                        val driveModelX = lerp(HOME_MODEL_X, targetDriveModelX, driveBlend)
                        val driveModelZ = lerp(HOME_MODEL_Z, -0.21f, driveBlend)
                        node.position = Position(
                            x = lerp(driveModelX, SETTINGS_MODEL_X, transition),
                            y = lerp(HOME_MODEL_Y, SETTINGS_MODEL_Y, transition),
                            z = lerp(driveModelZ, SETTINGS_MODEL_Z, transition),
                        )

                        // Centering watchdog: runs once per second (1_000_000_000 ns) when settings are closed
                        if (frameNs - lastXCheckNs >= 1_000_000_000L) {
                            lastXCheckNs = frameNs
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
                                        modelScale = renderedScale,
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
                            renderedScale = targetScale
                            node.scale = Scale(renderedScale)
                            node.isVisible = false
                            preparedFrames++
                            if (preparedFrames >= 3 && widthPx > 0 && heightPx > 0) {
                                modelReady = true
                                node.isVisible = true
                            }
                        } else {
                            node.isVisible = true
                            if (abs(targetScale - renderedScale) < 0.0002f) {
                                renderedScale = targetScale
                            } else {
                                val scaleResponse = 1f - exp(-dt * 12f)
                                renderedScale += (targetScale - renderedScale) * scaleResponse
                            }
                            node.scale = Scale(renderedScale)
                        }

                        if (DclLogRecorder.isLoggingActive()) {
                            val posChanged = abs(node.position.x - dclLastPosX) > 0.0001f || abs(cameraNode.worldPosition.x - dclLastCamX) > 0.0001f
                            val timeElapsed = (frameNs - dclLastLogNs) >= 250_000_000L
                            if (posChanged || timeElapsed) {
                                dclLastLogNs = frameNs
                                dclLastPosX = node.position.x
                                dclLastCamX = cameraNode.worldPosition.x
                                DclLogRecorder.logModelRender(
                                    transition = transition,
                                    driveBlend = driveBlend,
                                    modelX = node.position.x,
                                    modelY = node.position.y,
                                    modelZ = node.position.z,
                                    modelYaw = modelYaw,
                                    modelScale = renderedScale,
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

                        animationController?.update(rigStateRef.value, dt)
                        val publishNs = 1_000_000_000L // 1 second interval
                        if (frameNs - lastAnchorPublishNs >= publishNs) {
                            lastAnchorPublishNs = frameNs
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
                            val headlights = if (projectHeadlightsRef.value && currentTransition < 0.5f) {
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
                },
            ) {
                val initialTransition = settingsProgress.coerceIn(0f, 1f)
                val safeCustomScale = customModelScale.coerceIn(CAR_MODEL_SCALE_MIN, CAR_MODEL_SCALE_MAX)
                val initialDriveBlend = if (cameraAnimEnabled) {
                    FastOutSlowInEasing.transform(currentDriveBlend.coerceIn(0f, 1f))
                } else {
                    currentDriveBlend.coerceIn(0f, 1f)
                }
                val initialDriveScale = lerp(1f, 1.21f, initialDriveBlend)
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
                val initialTargetDriveModelX = if (cameraSwitchMode == CAMERA_SWITCH_MODE_PAS_AND_PARK) 0f else DRIVE_MODEL_X
                val initialDriveModelX = lerp(HOME_MODEL_X, initialTargetDriveModelX, initialDriveBlend)
                val initialDriveModelZ = lerp(HOME_MODEL_Z, -0.21f, initialDriveBlend)

                ModelNode(
                    modelInstance = modelInstance,
                    autoAnimate = false,
                    scale = Scale(if (initialTransition >= 0.99f) 0.001f else initialTargetScale),
                    position = Position(
                        x = lerp(initialDriveModelX, SETTINGS_MODEL_X, initialTransition),
                        y = lerp(HOME_MODEL_Y, SETTINGS_MODEL_Y, initialTransition),
                        z = lerp(initialDriveModelZ, SETTINGS_MODEL_Z, initialTransition),
                    ),
                    rotation = Rotation(
                        y = lerp(
                            MODEL_YAW_DEG,
                            SETTINGS_YAW_DEG + settingsUserYawDeg,
                            initialTransition,
                        ),
                    ),
                    apply = {
                        isVisible = false
                        modelNodeRef.set(this)
                    },
                )
            }
        }
        if (onClick != null || onLongClick != null) {
            val tapDriveBlend = if (cameraAnimEnabled) {
                FastOutSlowInEasing.transform(currentDriveBlend.coerceIn(0f, 1f))
            } else {
                currentDriveBlend.coerceIn(0f, 1f)
            }
            val tapWidth = lerp(200.dp, 170.dp, tapDriveBlend)
            val tapHeight = lerp(260.dp, 210.dp, tapDriveBlend)
            val tapOffsetY = 115.dp * tapDriveBlend

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
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
