package vad.dashing.tbox.ui.launcher

import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.google.android.filament.Engine
import com.google.android.filament.TransformManager
import com.google.android.filament.gltfio.FilamentInstance
import io.github.sceneview.collision.Vector3
import io.github.sceneview.node.CameraNode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cached, allocation-conscious bridge between launcher state and Filament pivot entities.
 *
 * Entity lookup and original local matrices happen once. A missing or non-transform entity is
 * omitted from [boundNodes], so a model update can degrade gracefully instead of crashing frames.
 */
class LauncherCarRigController private constructor(
    private val transformManager: TransformManager,
    private val boundNodes: Map<String, BoundNode>,
    val missingNodes: Set<String>,
) {
    private data class BoundNode(
        val entity: Int,
        val instance: Int,
    )

    private val workTransform = FloatArray(16)
    private val multiplyScratch = FloatArray(16)
    private val rotationTransform = FloatArray(16)
    private val lastAppliedRadians = mutableMapOf<String, Float>()

    fun update(state: LauncherCarRigState, @Suppress("UNUSED_PARAMETER") dtSec: Float) {
        transformManager.openLocalTransformTransaction()
        try {
            setRotation(
                DOOR_FL,
                RigAxis.Y,
                degreesToRadians(if (state.doorFlOpen) -LauncherCarRig.DOOR_OPEN_DEG else 0f),
            )
            setRotation(
                DOOR_FR,
                RigAxis.Y,
                degreesToRadians(if (state.doorFrOpen) LauncherCarRig.DOOR_OPEN_DEG else 0f),
            )
            setRotation(
                DOOR_RL,
                RigAxis.Y,
                degreesToRadians(if (state.doorRlOpen) -LauncherCarRig.DOOR_OPEN_DEG else 0f),
            )
            setRotation(
                DOOR_RR,
                RigAxis.Y,
                degreesToRadians(if (state.doorRrOpen) LauncherCarRig.DOOR_OPEN_DEG else 0f),
            )
            setRotation(
                TAILGATE,
                RigAxis.Z,
                degreesToRadians(
                    if (state.tailgateOpen) LauncherCarRig.TAILGATE_OPEN_DEG else 0f,
                ),
            )

            // Wheel pivot exports currently include adjacent arch geometry. Keep their original
            // transforms untouched until the GLB separates wheel-only meshes.
        } finally {
            transformManager.commitLocalTransformTransaction()
        }
    }

    /**
     * Projects actual wheel-pivot world positions into SceneView pixels.
     * Invalid/behind-camera/outlier coordinates are dropped fail-soft.
     */
    fun projectWheelAnchors(
        cameraNode: CameraNode,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
    ): Map<LauncherWheelCorner, Offset> = projectAnchors(
        cameraNode = cameraNode,
        viewportWidthPx = viewportWidthPx,
        viewportHeightPx = viewportHeightPx,
        nodeNames = WHEEL_CORNERS,
    )

    fun projectDoorAnchors(
        cameraNode: CameraNode,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
    ): Map<LauncherWheelCorner, Offset> = projectAnchors(
        cameraNode = cameraNode,
        viewportWidthPx = viewportWidthPx,
        viewportHeightPx = viewportHeightPx,
        nodeNames = DOOR_CORNERS,
        localOffsetX = 0.45f,
        localOffsetY = 0.28f,
    )


    private fun <K> projectAnchors(
        cameraNode: CameraNode,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        nodeNames: Map<K, String>,
        localOffsetX: Float = 0f,
        localOffsetY: Float = 0f,
        localOffsetZ: Float = 0f,
    ): Map<K, Offset> {
        if (viewportWidthPx <= 0 || viewportHeightPx <= 0) return emptyMap()
        return buildMap {
            nodeNames.forEach { (corner, nodeName) ->
                val node = boundNodes[nodeName] ?: return@forEach
                val world = FloatArray(16)
                transformManager.getWorldTransform(node.instance, world)
                val anchorX =
                    world[12] +
                        world[0] * localOffsetX +
                        world[4] * localOffsetY +
                        world[8] * localOffsetZ
                val anchorY =
                    world[13] +
                        world[1] * localOffsetX +
                        world[5] * localOffsetY +
                        world[9] * localOffsetZ
                val anchorZ =
                    world[14] +
                        world[2] * localOffsetX +
                        world[6] * localOffsetY +
                        world[10] * localOffsetZ
                val cameraPosition = cameraNode.worldPosition
                val cameraForward = cameraNode.forwardDirection
                val cameraDepth =
                    (anchorX - cameraPosition.x) * cameraForward.x +
                        (anchorY - cameraPosition.y) * cameraForward.y +
                        (anchorZ - cameraPosition.z) * cameraForward.z
                if (!cameraDepth.isFinite() || cameraDepth <= cameraNode.near) return@forEach
                val screen = cameraNode.worldToScreenPoint(
                    Vector3(anchorX, anchorY, anchorZ),
                )
                if (
                    screen.x.isFinite() && screen.y.isFinite() && screen.z.isFinite() &&
                    screen.x in -viewportWidthPx.toFloat()..(viewportWidthPx * 2f) &&
                    screen.y in -viewportHeightPx.toFloat()..(viewportHeightPx * 2f)
                ) {
                    put(corner, Offset(screen.x, screen.y))
                }
            }
        }
    }

    /**
     * Projects three ground-plane rings (0.2 / 0.37 / 0.54 m beyond the body contour)
     * as dense screen-space polylines (90 samples, each with its screen angle around
     * the projected body center). Because the rings live in world space, the PDC band
     * tilts and rotates together with the 3D model in any camera (top, drive, morph).
     */
    fun projectPdcRings(
        cameraNode: CameraNode,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
    ): LauncherPdcRingFrame? {
        if (viewportWidthPx <= 0 || viewportHeightPx <= 0) return null
        fun wheelWorld(name: String): FloatArray? {
            val node = boundNodes[name] ?: return null
            val world = FloatArray(16)
            transformManager.getWorldTransform(node.instance, world)
            return world
        }
        val fl = wheelWorld(WHEEL_FL) ?: return null
        val fr = wheelWorld(WHEEL_FR) ?: return null
        val rl = wheelWorld(WHEEL_RL) ?: return null
        val rr = wheelWorld(WHEEL_RR) ?: return null

        val frontX = (fl[12] + fr[12]) / 2f
        val frontZ = (fl[14] + fr[14]) / 2f
        val rearX = (rl[12] + rr[12]) / 2f
        val rearZ = (rl[14] + rr[14]) / 2f
        val groundY = (fl[13] + fr[13] + rl[13] + rr[13]) / 4f + 0.05f

        var fwdX = frontX - rearX
        var fwdZ = frontZ - rearZ
        val wheelbase = kotlin.math.hypot(fwdX.toDouble(), fwdZ.toDouble()).toFloat()
        if (wheelbase < 0.1f) return null
        fwdX /= wheelbase
        fwdZ /= wheelbase
        val leftX = -fwdZ
        val leftZ = fwdX
        val halfTrack = kotlin.math.hypot(
            (fl[12] - fr[12]).toDouble(),
            (fl[14] - fr[14]).toDouble(),
        ).toFloat() / 2f
        if (halfTrack < 0.05f) return null

        val cxW = (frontX + rearX) / 2f
        val czW = (frontZ + rearZ) / 2f
        val worldPerMeter = wheelbase / 2.72f
        // Ring silhouette: nearly circular (95% blend toward a circle) — a clean round
        // sonar band instead of a body-tracing ellipse.
        val semiLen = wheelbase * 0.98f * 1.1f
        val semiWidBody = halfTrack * 1.55f * 1.1f
        val semiWid = semiWidBody + (semiLen - semiWidBody) * 0.95f
        // Tighter radial spacing + thicker strokes (overlay) make the band read as a
        // solid strip instead of three sparse hairlines.
        val ringOffsetsM = floatArrayOf(0.16f, 0.30f, 0.44f)
        // The rear hemisphere matters most when parking, so the band reaches further
        // behind the car (up to +45% at the straight-back direction, easing to 0 at
        // the sides and front).
        val rearBoostMax = 0.45f

        val cameraPosition = cameraNode.worldPosition
        val cameraForward = cameraNode.forwardDirection

        fun project(wx: Float, wy: Float, wz: Float): Offset? {
            val depth =
                (wx - cameraPosition.x) * cameraForward.x +
                    (wy - cameraPosition.y) * cameraForward.y +
                    (wz - cameraPosition.z) * cameraForward.z
            if (!depth.isFinite() || depth <= cameraNode.near) return null
            val screen = cameraNode.worldToScreenPoint(Vector3(wx, wy, wz))
            if (!screen.x.isFinite() || !screen.y.isFinite()) return null
            return Offset(
                screen.x.coerceIn(-viewportWidthPx * 0.5f, viewportWidthPx * 1.5f),
                screen.y.coerceIn(-viewportHeightPx * 0.5f, viewportHeightPx * 1.5f),
            )
        }

        val center = project(cxW, groundY, czW) ?: return null
        // Keep the ellipse centered in the horizontal screen axis of the body: the
        // centroid depth better matches the visible body than the ground plane.
        val centerBodyY = groundY + 0.30f * worldPerMeter
        val centerBody = project(cxW, centerBodyY, czW)
        val cyShift = if (centerBody != null) centerBody.y - center.y else 0f
        val ringCenter = Offset(center.x, center.y + cyShift)

        // The asset's wheel naming is the source of truth for nose vs tail here: a
        // screen-based check flips in frames where the camera looks at the car from
        // the front (settings orbit), which would swap the front and rear zones.
        val frontScreen = project(frontX, groundY, frontZ)
        val rearScreen = project(rearX, groundY, rearZ)

        // Sensor zone centers in world angle space (0° = straight ahead, 180° =
        // straight back, positive toward the left side). Camera-independent, so the
        // zones stay correct in every camera mode (top view, drive chase cam, settings)
        // — unlike screen-projected anchors, which fall out of the viewport in the
        // chase camera and used to silently kill the rear band while driving.
        val frontBx = frontX + fwdX * wheelbase * 0.33f
        val frontBz = frontZ + fwdZ * wheelbase * 0.33f
        val rearBx = rearX - fwdX * wheelbase * 0.31f
        val rearBz = rearZ - fwdZ * wheelbase * 0.31f
        // Physically the bumper sensors span barely ±17° as seen from the body center,
        // which would squeeze all six zones into a stub of arc. Fan them out around the
        // straight-ahead / straight-back axis so each zone gets a readable slice. The
        // front pair sits close to the nose, so it needs far less spread than the rear.
        val rearFanout = 2.6f
        val frontFanout = 1.15f
        fun thetaOf(bx: Float, bz: Float, lateralFrac: Float, rear: Boolean): Float {
            val px = bx + leftX * halfTrack * lateralFrac - cxW
            val pz = bz + leftZ * halfTrack * lateralFrac - czW
            val raw = Math.toDegrees(
                kotlin.math.atan2(
                    (px * leftX + pz * leftZ).toDouble(),
                    (px * fwdX + pz * fwdZ).toDouble(),
                ),
            ).toFloat()
            // Anchor the fan on whichever axis the sensor actually faces, so a mirrored
            // basis can never push the result outside ±180°.
            val axis = if (kotlin.math.abs(raw) <= 90f) 0f else if (raw >= 0f) 180f else -180f
            val fanned = axis + (raw - axis) * (if (rear) rearFanout else frontFanout)
            return ((fanned + 180f).mod(360f)) - 180f
        }
        val channelAngles = mapOf(
            LauncherPdcChannel.FrontSideLeft to thetaOf(frontBx, frontBz, 0.85f, rear = false),
            LauncherPdcChannel.FrontLeft to thetaOf(frontBx, frontBz, 0.52f, rear = false),
            LauncherPdcChannel.FrontMidLeft to thetaOf(frontBx, frontBz, 0.18f, rear = false),
            LauncherPdcChannel.FrontMidRight to thetaOf(frontBx, frontBz, -0.18f, rear = false),
            LauncherPdcChannel.FrontRight to thetaOf(frontBx, frontBz, -0.52f, rear = false),
            LauncherPdcChannel.FrontSideRight to thetaOf(frontBx, frontBz, -0.85f, rear = false),
            LauncherPdcChannel.RearSideLeft to thetaOf(rearBx, rearBz, 0.85f, rear = true),
            LauncherPdcChannel.RearLeft to thetaOf(rearBx, rearBz, 0.52f, rear = true),
            LauncherPdcChannel.RearMidLeft to thetaOf(rearBx, rearBz, 0.18f, rear = true),
            LauncherPdcChannel.RearMidRight to thetaOf(rearBx, rearBz, -0.18f, rear = true),
            LauncherPdcChannel.RearRight to thetaOf(rearBx, rearBz, -0.52f, rear = true),
            LauncherPdcChannel.RearSideRight to thetaOf(rearBx, rearBz, -0.85f, rear = true),
        )

        // 4° sampling (90 points): dense enough for a ~10° zone (2–3 points) without
        // 1080 worldToScreen calls that stall Filament on the head unit.
        val samples = 90
        val rings = ringOffsetsM.map { offsetM ->
            val offset = offsetM * worldPerMeter
            (0 until samples).mapNotNull { i ->
                val theta = (i.toFloat() / samples) * (2f * PI.toFloat())
                val dirX = fwdX * cos(theta) + leftX * sin(theta)
                val dirZ = fwdZ * cos(theta) + leftZ * sin(theta)
                val cosT = cos(theta)
                val sinT = sin(theta)
                val bodyR = (semiLen * semiWid) /
                    kotlin.math.sqrt(
                        (semiWid * cosT) * (semiWid * cosT) +
                            (semiLen * sinT) * (semiLen * sinT),
                    )
                val rearBoost = 1f + rearBoostMax * maxOf(0f, -cosT)
                val radius = bodyR + offset * rearBoost
                val pt = project(cxW + dirX * radius, groundY, czW + dirZ * radius)
                    ?: return@mapNotNull null
                val shifted = Offset(pt.x, pt.y + cyShift)
                Math.toDegrees(theta.toDouble()).toFloat() to shifted
            }
        }

        // Stroke scale: screen px per world metre near the band, estimated from the
        // projected inner/outer ring spacing. Perspective makes it differ between the
        // front and the rear, so both are measured (whichever end is off-camera falls
        // back to the other).
        fun pxPerMeterAt(thetaDeg: Float): Float {
            val inner = rings.firstOrNull()
            val outer = rings.lastOrNull()
            if (inner.isNullOrEmpty() || outer.isNullOrEmpty()) return 0f
            val a = inner.minByOrNull { pdcAngularDiffDeg(it.first, thetaDeg) }?.second
                ?: return 0f
            val b = outer.minByOrNull { pdcAngularDiffDeg(it.first, thetaDeg) }?.second
                ?: return 0f
            val thetaRad = Math.toRadians(thetaDeg.toDouble())
            val boost = 1f + rearBoostMax * maxOf(0f, -cos(thetaRad).toFloat())
            val spanM = (ringOffsetsM.last() - ringOffsetsM.first()) * boost
            val distPx = kotlin.math.hypot(
                (b.x - a.x).toDouble(),
                (b.y - a.y).toDouble(),
            ).toFloat()
            return if (spanM > 0f) distPx / spanM else 0f
        }
        var pxFront = pxPerMeterAt(0f)
        var pxRear = pxPerMeterAt(180f)
        if (pxFront <= 0f) pxFront = pxRear
        if (pxRear <= 0f) pxRear = pxFront
        if (pxFront <= 0f) {
            pxFront = 80f
            pxRear = 80f
        }

        return LauncherPdcRingFrame(
            center = ringCenter,
            rings = rings,
            channelAngles = channelAngles,
            pxPerMeterFront = pxFront,
            pxPerMeterRear = pxRear,
        ).also {
            logPdcOk(rings.minOf { it.size })
            logPdcBasis(
                frontScreenY = frontScreen?.y,
                rearScreenY = rearScreen?.y,
                rearMidLeftDeg = channelAngles[LauncherPdcChannel.RearMidLeft],
                frontMidLeftDeg = channelAngles[LauncherPdcChannel.FrontMidLeft],
            )
        }
    }

    /**
     * Ground-plane headlight cones in front of the bumper, projected through the
     * scene camera the same way as PDC rings so they stay glued to the 3D body.
     */
    fun projectHeadlightBeams(
        cameraNode: CameraNode,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        driveBlend: Float = 0f,
    ): LauncherHeadlightFrame? {
        if (viewportWidthPx <= 0 || viewportHeightPx <= 0) return null
        fun wheelWorld(name: String): FloatArray? {
            val node = boundNodes[name] ?: return null
            val world = FloatArray(16)
            transformManager.getWorldTransform(node.instance, world)
            return world
        }
        val fl = wheelWorld(WHEEL_FL) ?: return null
        val fr = wheelWorld(WHEEL_FR) ?: return null
        val rl = wheelWorld(WHEEL_RL) ?: return null
        val rr = wheelWorld(WHEEL_RR) ?: return null

        val frontX = (fl[12] + fr[12]) / 2f
        val frontZ = (fl[14] + fr[14]) / 2f
        val rearX = (rl[12] + rr[12]) / 2f
        val rearZ = (rl[14] + rr[14]) / 2f
        val groundY = (fl[13] + fr[13] + rl[13] + rr[13]) / 4f + 0.04f

        var fwdX = frontX - rearX
        var fwdZ = frontZ - rearZ
        val wheelbase = kotlin.math.hypot(fwdX.toDouble(), fwdZ.toDouble()).toFloat()
        if (wheelbase < 0.1f) return null
        fwdX /= wheelbase
        fwdZ /= wheelbase
        val leftX = -fwdZ
        val leftZ = fwdX
        val halfTrack = kotlin.math.hypot(
            (fl[12] - fr[12]).toDouble(),
            (fl[14] - fr[14]).toDouble(),
        ).toFloat() / 2f
        if (halfTrack < 0.05f) return null
        val worldPerMeter = wheelbase / 2.72f

        val cameraPosition = cameraNode.worldPosition
        val cameraForward = cameraNode.forwardDirection
        fun project(wx: Float, wy: Float, wz: Float): Offset? {
            val depth =
                (wx - cameraPosition.x) * cameraForward.x +
                    (wy - cameraPosition.y) * cameraForward.y +
                    (wz - cameraPosition.z) * cameraForward.z
            if (!depth.isFinite() || depth <= cameraNode.near) return null
            val screen = cameraNode.worldToScreenPoint(Vector3(wx, wy, wz))
            if (!screen.x.isFinite() || !screen.y.isFinite()) return null
            return Offset(
                screen.x.coerceIn(-viewportWidthPx * 0.5f, viewportWidthPx * 1.5f),
                screen.y.coerceIn(-viewportHeightPx * 0.5f, viewportHeightPx * 1.5f),
            )
        }

        val bumperX = frontX + fwdX * wheelbase * 0.10f
        val bumperZ = frontZ + fwdZ * wheelbase * 0.10f
        val lampSpread = halfTrack * 0.78f
        val leftOx = bumperX + leftX * lampSpread
        val leftOz = bumperZ + leftZ * lampSpread
        val rightOx = bumperX - leftX * lampSpread
        val rightOz = bumperZ - leftZ * lampSpread
        val leftOrigin = project(leftOx, groundY, leftOz) ?: return null
        val rightOrigin = project(rightOx, groundY, rightOz) ?: return null

        fun fan(
            ox: Float,
            oz: Float,
            lengthM: Float,
            halfAngleDeg: Float,
            samples: Int = 8,
        ): List<Offset> {
            val length = lengthM * worldPerMeter
            return (-samples..samples).mapNotNull { i ->
                val a = Math.toRadians((halfAngleDeg * i / samples).toDouble())
                val c = kotlin.math.cos(a).toFloat()
                val s = kotlin.math.sin(a).toFloat()
                val dirX = fwdX * c + leftX * s
                val dirZ = fwdZ * c + leftZ * s
                project(ox + dirX * length, groundY, oz + dirZ * length)
            }
        }

        val lengthScale = 1f + 0.30f * driveBlend.coerceIn(0f, 1f)
        val leftLow = fan(leftOx, leftOz, lengthM = 3.1f * lengthScale, halfAngleDeg = 22f)
        val rightLow = fan(rightOx, rightOz, lengthM = 3.1f * lengthScale, halfAngleDeg = 22f)
        val leftHigh = fan(leftOx, leftOz, lengthM = 6.0f * lengthScale, halfAngleDeg = 22f)
        val rightHigh = fan(rightOx, rightOz, lengthM = 6.0f * lengthScale, halfAngleDeg = 22f)
        if (leftLow.size < 2 || rightLow.size < 2) {
            return null
        }
        val finalLeftHigh = if (leftHigh.size >= 2) leftHigh else leftLow
        val finalRightHigh = if (rightHigh.size >= 2) rightHigh else rightLow
        return LauncherHeadlightFrame(
            leftOrigin = leftOrigin,
            rightOrigin = rightOrigin,
            leftLowFan = leftLow,
            rightLowFan = rightLow,
            leftHighFan = finalLeftHigh,
            rightHighFan = finalRightHigh,
        )
    }

    private fun pdcAngularDiffDeg(a: Float, b: Float): Float {
        var d = kotlin.math.abs(a - b) % 360f
        if (d > 180f) d = 360f - d
        return d
    }

    private fun setRotation(name: String, axis: RigAxis, radians: Float) {
        val node = boundNodes[name] ?: return
        val last = lastAppliedRadians[name]
        if (last == null && kotlin.math.abs(radians) < 0.0001f) return
        if (last != null && kotlin.math.abs(last - radians) < 0.0001f) return
        val translation = PIVOT_TRANSLATIONS[name] ?: return
        workTransform.fill(0f)
        workTransform[0] = 1f
        workTransform[5] = 1f
        workTransform[10] = 1f
        workTransform[15] = 1f
        workTransform[12] = translation[0]
        workTransform[13] = translation[1]
        workTransform[14] = translation[2]
        applyRotation(axis, radians)
        transformManager.setTransform(node.instance, workTransform)
        lastAppliedRadians[name] = radians
    }

    private fun applyRotation(axis: RigAxis, radians: Float) {
        rotationTransform.fill(0f)
        rotationTransform[15] = 1f
        val c = cos(radians)
        val s = sin(radians)
        when (axis) {
            RigAxis.X -> {
                rotationTransform[0] = 1f
                rotationTransform[5] = c
                rotationTransform[6] = s
                rotationTransform[9] = -s
                rotationTransform[10] = c
            }
            RigAxis.Y -> {
                rotationTransform[0] = c
                rotationTransform[2] = -s
                rotationTransform[5] = 1f
                rotationTransform[8] = s
                rotationTransform[10] = c
            }
            RigAxis.Z -> {
                rotationTransform[0] = c
                rotationTransform[1] = s
                rotationTransform[4] = -s
                rotationTransform[5] = c
                rotationTransform[10] = 1f
            }
        }
        multiply4x4Into(workTransform, rotationTransform, multiplyScratch)
        multiplyScratch.copyInto(workTransform)
    }

    private fun multiply4x4Into(left: FloatArray, right: FloatArray, result: FloatArray) {
        for (col in 0..3) {
            for (row in 0..3) {
                var sum = 0f
                for (k in 0..3) {
                    sum += left[k * 4 + row] * right[col * 4 + k]
                }
                result[col * 4 + row] = sum
            }
        }
    }

    companion object {
        const val DOOR_FL = "p_door01"
        const val DOOR_FR = "p_door02"
        const val DOOR_RL = "p_door03"
        const val DOOR_RR = "p_door04"
        const val TAILGATE = "houbeimen_copy"
        const val WHEEL_FL = "wheel_lungu01_L"
        const val WHEEL_FR = "wheel_lungu01_R"
        const val WHEEL_RL = "wheel_lungu02_L"
        const val WHEEL_RR = "wheel_lungu02_R"

        val requiredNodeNames: Set<String> = linkedSetOf(
            DOOR_FL,
            DOOR_FR,
            DOOR_RL,
            DOOR_RR,
            TAILGATE,
            WHEEL_FL,
            WHEEL_FR,
            WHEEL_RL,
            WHEEL_RR,
        )

        private val WHEEL_CORNERS = mapOf(
            LauncherWheelCorner.FL to WHEEL_FL,
            LauncherWheelCorner.FR to WHEEL_FR,
            LauncherWheelCorner.RL to WHEEL_RL,
            LauncherWheelCorner.RR to WHEEL_RR,
        )
        private val DOOR_CORNERS = mapOf(
            LauncherWheelCorner.FL to DOOR_FL,
            LauncherWheelCorner.FR to DOOR_FR,
            LauncherWheelCorner.RL to DOOR_RL,
            LauncherWheelCorner.RR to DOOR_RR,
        )
        private val PIVOT_TRANSLATIONS = mapOf(
            DOOR_FL to floatArrayOf(-0.929257f, 1.0214f, 0.896827f),
            DOOR_FR to floatArrayOf(-0.929257f, 1.0214f, -0.896827f),
            DOOR_RL to floatArrayOf(0.111599f, 1.05741f, 0.903843f),
            DOOR_RR to floatArrayOf(0.111599f, 1.05741f, -0.903843f),
            TAILGATE to floatArrayOf(1.39406f, 1.60524f, 0.36237f),
        )
        private val loggedMissingNodes = mutableSetOf<String>()

        private var lastPdcDropLogMs = 0L
        private var lastPdcOkLogMs = 0L
        private var lastPdcBasisLogMs = 0L

        fun logPdcDrop(reason: String) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastPdcDropLogMs < 2000L) return
            lastPdcDropLogMs = now
            Log.w("LauncherPdc", "PdcAnchors dropped: $reason")
        }

        fun logPdcOk(count: Int) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastPdcOkLogMs < 5000L) return
            lastPdcOkLogMs = now
            Log.i("LauncherPdc", "PdcAnchors/rings projected: $count")
        }

        fun logPdcBasis(
            frontScreenY: Float?,
            rearScreenY: Float?,
            rearMidLeftDeg: Float?,
            frontMidLeftDeg: Float?,
        ) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastPdcBasisLogMs < 5000L) return
            lastPdcBasisLogMs = now
            Log.i(
                "LauncherPdc",
                "basis frontY=$frontScreenY rearY=$rearScreenY " +
                    "rearMidLeft=$rearMidLeftDeg frontMidLeft=$frontMidLeftDeg",
            )
        }

        fun bind(engine: Engine, modelInstance: FilamentInstance): LauncherCarRigController {
            val manager = engine.transformManager
            val asset = modelInstance.asset
            val entitiesByName = buildMap<String, Int> {
                for (entity in modelInstance.entities) {
                    val name = asset.getName(entity)
                    if (!name.isNullOrBlank()) put(name, entity)
                }
            }
            val nodes = buildMap {
                requiredNodeNames.forEach { name ->
                    val entity = entitiesByName[name] ?: return@forEach
                    if (!manager.hasComponent(entity)) return@forEach
                    val instance = manager.getInstance(entity)
                    if (instance == 0) return@forEach
                    put(
                        name,
                        BoundNode(
                            entity = entity,
                            instance = instance,
                        ),
                    )
                }
            }
            val missing = requiredNodeNames - nodes.keys
            synchronized(loggedMissingNodes) {
                (missing - loggedMissingNodes).forEach { name ->
                    Log.w("LauncherCarRig", "Model pivot is missing; effect disabled: $name")
                }
                loggedMissingNodes += missing
            }
            return LauncherCarRigController(
                transformManager = manager,
                boundNodes = nodes,
                missingNodes = missing,
            )
        }

        private fun degreesToRadians(degrees: Float): Float = degrees * (PI.toFloat() / 180f)
    }
}
