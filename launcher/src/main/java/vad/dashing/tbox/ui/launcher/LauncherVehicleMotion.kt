package vad.dashing.tbox.ui.launcher

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.mbcan.UniversalCanRepository

/** Resolved speed (km/h) and steering wheel angle (degrees) for launcher visuals. */
data class LauncherVehicleMotion(
    val speedKmh: Float,
    val steerAngleDeg: Float,
    val speedFromTbox: Boolean,
    val steerFromTbox: Boolean,
    /** Show steer animation while stationary (motion preview / physical wheel test). */
    val steerPreviewActive: Boolean = false,
)

@Composable
fun rememberLauncherVehicleMotion(
    tboxConnected: Boolean,
    canViewModel: CanDataViewModel,
): LauncherVehicleMotion {
    val tboxSpeed by canViewModel.carSpeed.collectAsStateWithLifecycle()
    val canSpeedAccurate by canViewModel.carSpeedAccurate.collectAsStateWithLifecycle()
    val tboxSteer by canViewModel.steerAngle.collectAsStateWithLifecycle()
    val headUnitSpeed by UniversalCanRepository.carSpeedState.collectAsStateWithLifecycle()

    if (LauncherDevVehicleState.simulateEnabled) {
        return LauncherVehicleMotion(
            speedKmh = LauncherDevVehicleState.speedKmh,
            steerAngleDeg = LauncherDevVehicleState.steerAngleDeg,
            speedFromTbox = false,
            steerFromTbox = false,
        )
    }
    if (LauncherDevVehicleState.motionPreviewEnabled) {
        return LauncherVehicleMotion(
            speedKmh = 0f,
            steerAngleDeg = tboxSteer ?: 0f,
            speedFromTbox = false,
            steerFromTbox = tboxSteer != null,
            steerPreviewActive = true,
        )
    }

    val speedCandidate = when {
        headUnitSpeed != null && headUnitSpeed!! >= 0f -> headUnitSpeed!!
        canSpeedAccurate != null && canSpeedAccurate!! >= 0f -> canSpeedAccurate!!
        tboxConnected && tboxSpeed != null -> tboxSpeed!!
        tboxSpeed != null -> tboxSpeed!!
        else -> 0f
    }
    val rawSpeed = sanitizeLauncherVehicleSpeed(speedCandidate)

    // Smooth and hold speed for up to 1800ms to filter out momentary zero/null CAN bus dropouts while driving
    var smoothedSpeed by remember { mutableFloatStateOf(rawSpeed) }
    var lastMovingSpeed by remember { mutableFloatStateOf(0f) }
    var lastMovingMs by remember { mutableLongStateOf(0L) }

    val nowMs = SystemClock.elapsedRealtime()
    if (rawSpeed > 0.5f) {
        smoothedSpeed = rawSpeed
        lastMovingSpeed = rawSpeed
        lastMovingMs = nowMs
    } else {
        val elapsed = nowMs - lastMovingMs
        if (elapsed < 1800L && lastMovingSpeed > 0.5f) {
            smoothedSpeed = lastMovingSpeed
        } else {
            smoothedSpeed = 0f
        }
    }

    val steer = sanitizeLauncherSteering(tboxSteer)
    return LauncherVehicleMotion(
        speedKmh = smoothedSpeed,
        steerAngleDeg = steer,
        speedFromTbox = tboxConnected && tboxSpeed != null,
        steerFromTbox = tboxSteer != null,
    )
}

internal fun sanitizeLauncherVehicleSpeed(value: Float): Float =
    value.takeIf { it.isFinite() && it in 0f..300f } ?: 0f

internal fun sanitizeLauncherSteering(value: Float?): Float =
    value?.takeIf { it.isFinite() && it in -1080f..1080f } ?: 0f

@Composable
fun rememberLauncherVisualSteer(
    motion: LauncherVehicleMotion,
): Float = LauncherSteerVisual.visualSteerDeg(motion.steerAngleDeg)
