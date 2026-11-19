package vad.dashing.tbox.ui.launcher

/** Offline GLB path for the Jetour Dashing 720 launcher model. */
const val LAUNCHER_CAR_MODEL_ASSET = "models/jetour_new.glb"

/** Vehicle body / drive state for the 3D model. Sunroof is intentionally not driven. */
data class LauncherCarRigState(
    val doorFlOpen: Boolean = false,
    val doorFrOpen: Boolean = false,
    val doorRlOpen: Boolean = false,
    val doorRrOpen: Boolean = false,
    val tailgateOpen: Boolean = false,
    val sunroofOpen: Boolean = false,
    val speedKmh: Float = 0f,
    val steeringDeg: Float = 0f,
    val wheelSpinRad: Float = 0f,
)

object LauncherCarRig {
    const val WHEEL_RADIUS_M = 0.25f
    const val DOOR_OPEN_DEG = 65f
    const val TAILGATE_OPEN_DEG = 60f
    const val MAX_VISUAL_STEER_DEG = 32f
    private const val BODY_RESPONSE_PER_SEC = 8f
    private const val STEER_RESPONSE_PER_SEC = 8f
    private const val TAU = (Math.PI * 2.0).toFloat()

    fun advance(state: LauncherCarRigState, dtSec: Float): LauncherCarRigState {
        val dt = dtSec.coerceIn(0f, 0.1f)
        val speedMps = (state.speedKmh / 3.6f).coerceAtLeast(0f)
        val spinDelta = if (speedMps <= 0f) 0f else (speedMps / WHEEL_RADIUS_M) * dt
        return state.copy(wheelSpinRad = wrapRadians(state.wheelSpinRad + spinDelta))
    }

    fun approach(current: Float, target: Float, dtSec: Float, responsePerSec: Float): Float {
        val dt = dtSec.coerceIn(0f, 0.1f)
        val alpha = 1f - kotlin.math.exp(-responsePerSec.coerceAtLeast(0f) * dt)
        return current + (target - current) * alpha
    }

    fun approachBody(current: Float, open: Boolean, dtSec: Float): Float =
        approach(current, if (open) 1f else 0f, dtSec, BODY_RESPONSE_PER_SEC)

    fun approachSteering(currentDeg: Float, targetDeg: Float, dtSec: Float): Float =
        approach(
            currentDeg,
            targetDeg.coerceIn(-MAX_VISUAL_STEER_DEG, MAX_VISUAL_STEER_DEG),
            dtSec,
            STEER_RESPONSE_PER_SEC,
        )

    fun wrapRadians(value: Float): Float {
        val wrapped = value % TAU
        return if (wrapped < 0f) wrapped + TAU else wrapped
    }

    /**
     * Column-major 4x4 multiplication, matching Filament TransformManager matrices.
     * Kept Android-free so the rig math can be unit-tested.
     */
    fun multiply4x4(left: FloatArray, right: FloatArray): FloatArray {
        require(left.size == 16 && right.size == 16)
        return FloatArray(16) { index ->
            val row = index % 4
            val col = index / 4
            var sum = 0f
            for (k in 0..3) sum += left[k * 4 + row] * right[col * 4 + k]
            sum
        }
    }

    fun rotationMatrix(axis: RigAxis, radians: Float): FloatArray {
        val c = kotlin.math.cos(radians)
        val s = kotlin.math.sin(radians)
        return when (axis) {
            RigAxis.X -> floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, c, s, 0f,
                0f, -s, c, 0f,
                0f, 0f, 0f, 1f,
            )
            RigAxis.Y -> floatArrayOf(
                c, 0f, -s, 0f,
                0f, 1f, 0f, 0f,
                s, 0f, c, 0f,
                0f, 0f, 0f, 1f,
            )
            RigAxis.Z -> floatArrayOf(
                c, s, 0f, 0f,
                -s, c, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f,
            )
        }
    }

    fun applyLocalRotations(
        base: FloatArray,
        vararg rotations: Pair<RigAxis, Float>,
    ): FloatArray = rotations.fold(base.copyOf()) { matrix, (axis, radians) ->
        multiply4x4(matrix, rotationMatrix(axis, radians))
    }
}

enum class RigAxis { X, Y, Z }
