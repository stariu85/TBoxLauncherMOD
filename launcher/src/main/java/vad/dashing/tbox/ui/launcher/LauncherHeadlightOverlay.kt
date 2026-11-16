package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.LIGHT_CONTROL_LOW_BEAM

data class LauncherHeadlightBeams(
    val lowBeam: Boolean = false,
    val highBeam: Boolean = false,
) {
    val any: Boolean get() = lowBeam || highBeam
}

/** Two ground-plane beams from the headlamp positions, projected with the 3D body. */
data class LauncherHeadlightFrame(
    val leftOrigin: Offset,
    val rightOrigin: Offset,
    val leftLowFan: List<Offset>,
    val rightLowFan: List<Offset>,
    val leftHighFan: List<Offset>,
    val rightHighFan: List<Offset>,
    val leftShortFan: List<Offset> = emptyList(),
    val rightShortFan: List<Offset> = emptyList(),
)

@Composable
fun rememberHeadlightBeams(): LauncherHeadlightBeams {
    val simulate = LauncherDevVehicleState.simulateEnabled
    val simLow = LauncherDevVehicleState.lowBeam
    val simHigh = LauncherDevVehicleState.highBeam
    val snapshot = rememberLauncherVehicleControlSnapshot(enabled = !simulate)
    val pushHighBeam by UniversalCanRepository.bcmHighBeamState.collectAsStateWithLifecycle()
    val pushLowBeam by UniversalCanRepository.bcmLowBeamState.collectAsStateWithLifecycle()
    val effectiveLow = if (simulate) simLow else (pushLowBeam ?: snapshot.bcmLowBeam)
    val effectiveHigh = if (simulate) simHigh else (pushHighBeam ?: snapshot.bcmHighBeam)
    return remember(
        simulate,
        simLow,
        simHigh,
        snapshot.lightControlRaw,
        snapshot.headlightsSwitch,
        effectiveLow,
        effectiveHigh,
    ) {
        resolveHeadlightBeams(
            simulateEnabled = simulate,
            simLowBeam = simLow,
            simHighBeam = simHigh,
            lightControlRaw = snapshot.lightControlRaw,
            headlightsSwitch = snapshot.headlightsSwitch,
            bcmLowBeam = effectiveLow,
            bcmHighBeam = effectiveHigh,
        )
    }
}

internal fun resolveHeadlightBeams(
    simulateEnabled: Boolean,
    simLowBeam: Boolean,
    simHighBeam: Boolean,
    lightControlRaw: Int?,
    headlightsSwitch: Int?,
    bcmLowBeam: Boolean? = null,
    bcmHighBeam: Boolean? = null,
): LauncherHeadlightBeams {
    if (simulateEnabled) {
        return LauncherHeadlightBeams(lowBeam = simLowBeam, highBeam = simHighBeam)
    }
    val high = bcmHighBeam == true
    val low = high || lightControlRaw == LIGHT_CONTROL_LOW_BEAM || bcmLowBeam == true
    return LauncherHeadlightBeams(lowBeam = low, highBeam = high)
}

/**
 * Resolves whether the high beam indicator is currently active in the ADAS indicator strip.
 * - In manual mode (!isHmaOn): active when high beam is physically burning (showManualHighBeam).
 * - In auto mode (isHmaOn): active when HMA is on and high beam is burning (HMA icon turns blue).
 * - In developer simulation: also active when all indicators are forced on.
 */
fun isHighBeamIndicatedInAdas(
    isHmaOn: Boolean,
    isHighBeamBurning: Boolean,
    showAll: Boolean = false,
): Boolean {
    val showManualHighBeam = !isHmaOn && isHighBeamBurning
    val isHmaHighBeamActive = isHmaOn && isHighBeamBurning
    return showManualHighBeam || isHmaHighBeamActive || showAll
}

@Composable
fun LauncherHeadlightOverlay(
    beams: LauncherHeadlightBeams,
    hma: LauncherAdasAssistIcon = LauncherAdasAssistIcon.Hidden,
    isHighBeam: Boolean = beams.highBeam,
    frame: LauncherHeadlightFrame?,
    modifier: Modifier = Modifier,
) {
    if (frame == null) return

    val color = if (isHighBeam) Color(0xFFFFFBEA) else Color(0xFFFFF1C2)
    val coreAlpha = if (isHighBeam) 0.44f else 0.36f
    val leftFan = if (isHighBeam) {
        if (frame.leftHighFan.size >= 2) frame.leftHighFan else frame.leftLowFan
    } else {
        if (frame.leftLowFan.size >= 2) frame.leftLowFan else frame.leftShortFan
    }
    val rightFan = if (isHighBeam) {
        if (frame.rightHighFan.size >= 2) frame.rightHighFan else frame.rightLowFan
    } else {
        if (frame.rightLowFan.size >= 2) frame.rightLowFan else frame.rightShortFan
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawHeadlightCone(
            origin = frame.leftOrigin,
            fan = leftFan,
            color = color,
            coreAlpha = coreAlpha,
        )
        drawHeadlightCone(
            origin = frame.rightOrigin,
            fan = rightFan,
            color = color,
            coreAlpha = coreAlpha,
        )
    }
}

private fun DrawScope.drawHeadlightCone(
    origin: Offset,
    fan: List<Offset>,
    color: Color,
    coreAlpha: Float,
) {
    if (fan.size < 2) return
    val path = Path().apply {
        moveTo(origin.x, origin.y)
        fan.forEach { lineTo(it.x, it.y) }
        close()
    }
    val far = fan[fan.size / 2]
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            0f to color.copy(alpha = coreAlpha),
            0.35f to color.copy(alpha = coreAlpha * 0.45f),
            0.72f to color.copy(alpha = coreAlpha * 0.12f),
            1f to color.copy(alpha = 0f),
            start = origin,
            end = far,
        ),
    )
}
