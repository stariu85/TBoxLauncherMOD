package vad.dashing.tbox.ui.launcher

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

// Calibrated against the GLB's authored forward axis so the nose points to screen top.
private const val MODEL_YAW_DEG = 135f
private const val SETTINGS_YAW_DEG = 45f

private val TOP_CAMERA_POS = Float3(0f, 4.8f, 0.08f)
private val TOP_CAMERA_TARGET = Float3(0f, 0f, 0f)
/** Raised by +15° in elevation pitch to align the car perspective with the road canvas. */
private val DRIVE_CAMERA_POS = Float3(0f, 2.55f, 3.4f)
private val DRIVE_CAMERA_TARGET = Float3(0f, 0.20f, -2.2f)
/** Settings camera keeps the whole car in frame and clear of the near clipping plane. */
private val SETTINGS_CAMERA_POS = Float3(1.15f, 1.05f, 2.55f)
private val SETTINGS_CAMERA_TARGET = Float3(0.12f, 0.18f, 0f)

private const val HOME_MODEL_SCALE = 0.52f
private const val SKIP_FRAME_THRESHOLD_NS = 30_000_000L // Throttles rendering to 30 FPS
// Settings uses the full SceneView bounds; visual size is controlled only here/camera.
private const val SETTINGS_MODEL_SCALE = 0.48f
private const val HOME_MODEL_X = 0f
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
    projectPdcRings: Boolean = false,
    projectHeadlights: Boolean = false,
    textureSurface: Boolean = false,
) {
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
            settingsOrbit = settingsOrbit,
            onWheelAnchorsChanged = onWheelAnchorsChanged,
            onDoorAnchorsChanged = onDoorAnchorsChanged,
            onPdcRingsChanged = onPdcRingsChanged,
            onHeadlightFrameChanged = onHeadlightFrameChanged,
            onBodyRigAvailabilityChanged = onBodyRigAvailabilityChanged,
            projectPdcRings = projectPdcRings,
            projectHeadlights = projectHeadlights,
            textureSurface = textureSurface,
            lowPowerPreview = settingsView || settingsProgress > 0.5f,
        )
        if (onClick != null || onLongClick != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(interactionModifier),
            )
        }
    }
}

@Composable
private fun LauncherCarFilamentModel(
    modifier: Modifier = Modifier,
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
    settingsOrbit: LauncherSettingsOrbitState?,
    onWheelAnchorsChanged: (Map<LauncherWheelCorner, Offset>) -> Unit,
    onDoorAnchorsChanged: (Map<LauncherWheelCorner, Offset>) -> Unit,
    onPdcRingsChanged: (LauncherPdcRingFrame?) -> Unit,
    onHeadlightFrameChanged: (LauncherHeadlightFrame?) -> Unit,
    onBodyRigAvailabilityChanged: (Boolean) -> Unit,
    projectPdcRings: Boolean,
    projectHeadlights: Boolean,
    textureSurface: Boolean = false,
    lowPowerPreview: Boolean = false,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val surfaceEpoch by LauncherCarSurfaceRecovery.epoch.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner, lowPowerPreview) {
        if (lowPowerPreview) {
            return@DisposableEffect onDispose { }
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
        }
    }

    Box(modifier = modifier) {
        key(if (lowPowerPreview) 0 else surfaceEpoch, modelRevision) {
            LauncherCarFilamentContent(
                modifier = Modifier.fillMaxSize(),
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
                settingsOrbit = settingsOrbit,
                onWheelAnchorsChanged = onWheelAnchorsChanged,
                onDoorAnchorsChanged = onDoorAnchorsChanged,
                onPdcRingsChanged = onPdcRingsChanged,
                onHeadlightFrameChanged = onHeadlightFrameChanged,
                onBodyRigAvailabilityChanged = onBodyRigAvailabilityChanged,
                projectPdcRings = projectPdcRings,
                projectHeadlights = projectHeadlights,
                textureSurface = textureSurface,
                lowPowerPreview = lowPowerPreview,
            )
        }
    }
}

@Composable
private fun LauncherCarFilamentContent(
    modifier: Modifier = Modifier,
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
    settingsOrbit: LauncherSettingsOrbitState?,
    onWheelAnchorsChanged: (Map<LauncherWheelCorner, Offset>) -> Unit,
    onDoorAnchorsChanged: (Map<LauncherWheelCorner, Offset>) -> Unit,
    onPdcRingsChanged: (LauncherPdcRingFrame?) -> Unit,
    onHeadlightFrameChanged: (LauncherHeadlightFrame?) -> Unit,
    onBodyRigAvailabilityChanged: (Boolean) -> Unit,
    projectPdcRings: Boolean,
    projectHeadlights: Boolean,
    textureSurface: Boolean = false,
    lowPowerPreview: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
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
    val overlayPublishHandler = remember { Handler(Looper.getMainLooper()) }
    var lastFrameNs by remember { mutableLongStateOf(0L) }
    var lastAnchorPublishNs by remember { mutableLongStateOf(0L) }
    val driveTargetCompose = when {
        settingsProgress > 0.02f -> 0f
        inDriveGear || steerPreview -> 1f
        else -> 0f
    }
    val composedDriveBlend by animateFloatAsState(
        targetValue = driveTargetCompose,
        animationSpec = tween(280),
        label = "carDriveBlend",
    )
    val driveBlendRef = rememberUpdatedState(composedDriveBlend)
    val lifecycleOwner = LocalLifecycleOwner.current

    var preparedFrames by remember(modelInstance) { mutableIntStateOf(0) }
    var renderedScale by remember(modelInstance) { mutableFloatStateOf(HOME_MODEL_SCALE) }
    var modelReady by remember(modelInstance) { mutableStateOf(modelInstance != null) }
    val modelAlpha by animateFloatAsState(
        targetValue = if (modelReady) 1f else 0f,
        animationSpec = tween(durationMillis = 50),
        label = "launcherCarModelAlpha",
    )
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

    // SceneView otherwise renders one frame with its default camera before onFrame runs.
    SideEffect {
        val initialDrivePos = Float3(
            lerp(TOP_CAMERA_POS.x, DRIVE_CAMERA_POS.x, composedDriveBlend),
            lerp(TOP_CAMERA_POS.y, DRIVE_CAMERA_POS.y, composedDriveBlend),
            lerp(TOP_CAMERA_POS.z, DRIVE_CAMERA_POS.z, composedDriveBlend),
        )
        val initialDriveTarget = Float3(
            lerp(TOP_CAMERA_TARGET.x, DRIVE_CAMERA_TARGET.x, composedDriveBlend),
            lerp(TOP_CAMERA_TARGET.y, DRIVE_CAMERA_TARGET.y, composedDriveBlend),
            lerp(TOP_CAMERA_TARGET.z, DRIVE_CAMERA_TARGET.z, composedDriveBlend),
        )
        cameraNode.worldPosition = Position(
            lerp(initialDrivePos.x, SETTINGS_CAMERA_POS.x, currentTransition),
            lerp(initialDrivePos.y, SETTINGS_CAMERA_POS.y, currentTransition),
            lerp(initialDrivePos.z, SETTINGS_CAMERA_POS.z, currentTransition),
        )
        cameraNode.lookAt(
            Position(
                lerp(initialDriveTarget.x, SETTINGS_CAMERA_TARGET.x, currentTransition),
                lerp(initialDriveTarget.y, SETTINGS_CAMERA_TARGET.y, currentTransition),
                lerp(initialDriveTarget.z, SETTINGS_CAMERA_TARGET.z, currentTransition),
            ),
        )
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

    Box(modifier = modifier) {
        if (modelInstance != null) {
            SceneView(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = modelAlpha }
                    .pointerInteropFilter { false },
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
                        if (lastFrameNs != 0L && frameNs - lastFrameNs < SKIP_FRAME_THRESHOLD_NS) {
                            return@runCatching
                        }
                        val node = modelNodeRef.get() ?: return@runCatching
                        val dt = if (lastFrameNs == 0L) {
                            0.033f
                        } else {
                            ((frameNs - lastFrameNs) / 1_000_000_000f).coerceAtMost(0.08f)
                        }
                        lastFrameNs = frameNs
                        if (!lowPowerPreview) {
                            LauncherCarSurfaceRecovery.onFrameObserved()
                        }

                        val transition = settingsProgress.coerceIn(0f, 1f)
                        val driveBlend = driveBlendRef.value

                        // Continuous camera morph: top/drive ↔ settings (no body yaw while driving).
                        val drivePos = Float3(
                            lerp(TOP_CAMERA_POS.x, DRIVE_CAMERA_POS.x, driveBlend),
                            lerp(TOP_CAMERA_POS.y, DRIVE_CAMERA_POS.y, driveBlend),
                            lerp(TOP_CAMERA_POS.z, DRIVE_CAMERA_POS.z, driveBlend),
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
                        val orbit = settingsOrbitRef.value
                        orbit?.tickFling(dt)
                        val userYaw = orbit?.yawDeg ?: settingsUserYawRef.value
                        val userScale = orbit?.scale ?: settingsUserScaleRef.value
                        val modelYaw = lerp(
                            MODEL_YAW_DEG,
                            SETTINGS_YAW_DEG + userYaw,
                            transition,
                        )
                        node.rotation = Rotation(y = modelYaw)
                        node.position = Position(
                            x = lerp(HOME_MODEL_X, SETTINGS_MODEL_X, transition),
                            y = lerp(HOME_MODEL_Y, SETTINGS_MODEL_Y, transition),
                            z = lerp(HOME_MODEL_Z, SETTINGS_MODEL_Z, transition),
                        )

                        val viewport = cameraNode.viewport
                        val widthPx = viewport?.width ?: 0
                        val heightPx = viewport?.height ?: 0
                        val wheels = runCatching {
                            rigController?.projectWheelAnchors(
                                cameraNode = cameraNode,
                                viewportWidthPx = widthPx,
                                viewportHeightPx = heightPx,
                            )
                        }.getOrNull()

                        var autoDriveScale = HOME_MODEL_SCALE
                        val rl = wheels?.get(LauncherWheelCorner.RL)
                        val rr = wheels?.get(LauncherWheelCorner.RR)
                        if (rl != null && rr != null && widthPx > 0 && heightPx > 0) {
                            val currentTrackPx = abs(rr.x - rl.x)
                            val yRear = (rl.y + rr.y) / 2f
                            val horizonY = heightPx * 0.24f
                            val tRear = ((yRear - horizonY) / (heightPx - horizonY)).coerceIn(0f, 1f)
                            val roadW = widthPx.toFloat().coerceAtMost(240f * density)
                            val halfWidthAtT = (roadW * 0.14f) * (1f - tRear).pow(1.25f) + (roadW * 1.10f) * tRear.pow(1.05f)
                            val laneOffsetAtT = halfWidthAtT * 0.30f
                            val laneWidthPx = 2f * laneOffsetAtT
                            val margin35dpPx = 35f * density
                            val targetTrackPx = (laneWidthPx - 2f * margin35dpPx).coerceAtLeast(10f)
                            if (currentTrackPx > 10f && renderedScale > 0.05f) {
                                autoDriveScale = (renderedScale * (targetTrackPx / currentTrackPx)).coerceIn(0.20f, 0.65f)
                            }
                        }

                        val topViewScale = HOME_MODEL_SCALE * customModelScale
                        val homeScale = lerp(topViewScale, autoDriveScale, driveBlend)
                        val baseScale = lerp(homeScale, SETTINGS_MODEL_SCALE, transition)
                        val pinchScale = lerp(
                            1f,
                            userScale.coerceIn(
                                SETTINGS_USER_SCALE_MIN,
                                SETTINGS_USER_SCALE_MAX,
                            ),
                            transition,
                        )
                        val settingsCustomScale = lerp(1f, customModelScale, transition)
                        val targetScale = baseScale * pinchScale * settingsCustomScale
                        node.isVisible = true
                        if (!modelReady) {
                            renderedScale = targetScale
                            node.scale = Scale(renderedScale)
                            preparedFrames++
                            if (preparedFrames >= 1) {
                                modelReady = true
                            }
                        } else {
                            val scaleResponse = 1f - exp(-dt * 8f)
                            renderedScale += (targetScale - renderedScale) * scaleResponse
                            node.scale = Scale(renderedScale)
                        }

                        animationController?.update(rigStateRef.value, dt)
                        val publishNs = if (lowPowerPreview) 400_000_000L else 100_000_000L
                        if (frameNs - lastAnchorPublishNs >= publishNs) {
                            lastAnchorPublishNs = frameNs
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
                val initialScale = lerp(
                    HOME_MODEL_SCALE,
                    SETTINGS_MODEL_SCALE,
                    initialTransition,
                ) * lerp(
                    1f,
                    settingsUserScale.coerceIn(
                        SETTINGS_USER_SCALE_MIN,
                        SETTINGS_USER_SCALE_MAX,
                    ),
                    initialTransition,
                )
                ModelNode(
                    modelInstance = modelInstance,
                    autoAnimate = false,
                    // The settings SceneView can expose its construction frame before
                    // visibility reaches the renderer. Keep that frame effectively empty;
                    // onFrame applies the final calibrated scale before revealing the node.
                    scale = Scale(if (initialTransition >= 0.99f) 0.001f else initialScale),
                    position = Position(
                        x = lerp(HOME_MODEL_X, SETTINGS_MODEL_X, initialTransition),
                        y = lerp(HOME_MODEL_Y, SETTINGS_MODEL_Y, initialTransition),
                        z = lerp(HOME_MODEL_Z, SETTINGS_MODEL_Z, initialTransition),
                    ),
                    rotation = Rotation(
                        y = lerp(
                            MODEL_YAW_DEG,
                            SETTINGS_YAW_DEG + settingsUserYawDeg,
                            initialTransition,
                        ),
                    ),
                    apply = {
                        isVisible = true
                        modelNodeRef.set(this)
                    },
                )
            }
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
