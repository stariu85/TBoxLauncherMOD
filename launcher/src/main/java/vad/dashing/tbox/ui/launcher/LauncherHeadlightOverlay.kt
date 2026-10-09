package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import vad.dashing.tbox.ui.LIGHT_CONTROL_AUTO
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
)

@Composable
fun rememberHeadlightBeams(): LauncherHeadlightBeams {
    val simulate = LauncherDevVehicleState.simulateEnabled
    val simLow = LauncherDevVehicleState.lowBeam
    val simHigh = LauncherDevVehicleState.highBeam
    val snapshot = rememberLauncherVehicleControlSnapshot(enabled = !simulate)
    return remember(
        simulate,
        simLow,
        simHigh,
        snapshot.lightControlRaw,
        snapshot.headlightsSwitch,
    ) {
        resolveHeadlightBeams(
            simulateEnabled = simulate,
            simLowBeam = simLow,
            simHighBeam = simHigh,
            lightControlRaw = snapshot.lightControlRaw,
            headlightsSwitch = snapshot.headlightsSwitch,
        )
    }
}

internal fun resolveHeadlightBeams(
    simulateEnabled: Boolean,
    simLowBeam: Boolean,
    simHighBeam: Boolean,
    lightControlRaw: Int?,
    headlightsSwitch: Int?,
): LauncherHeadlightBeams {
    if (simulateEnabled) {
        return LauncherHeadlightBeams(lowBeam = simLowBeam, highBeam = simHighBeam)
    }
    val low = lightControlRaw == LIGHT_CONTROL_LOW_BEAM || lightControlRaw == LIGHT_CONTROL_AUTO
    val high = headlightsSwitch == 3
    return LauncherHeadlightBeams(lowBeam = low || high, highBeam = high)
}

@Composable
fun LauncherHeadlightOverlay(
    beams: LauncherHeadlightBeams,
    frame: LauncherHeadlightFrame?,
    modifier: Modifier = Modifier,
) {
    if (!beams.any || frame == null) return
    val high = beams.highBeam
    val color = if (high) Color(0xFFEAF4FF) else Color(0xFFFFF1C2)
    val coreAlpha = if (high) 0.58f else 0.36f
    Canvas(modifier = modifier.fillMaxSize()) {
        drawHeadlightCone(
            origin = frame.leftOrigin,
            fan = if (high) frame.leftHighFan else frame.leftLowFan,
            color = color,
            coreAlpha = coreAlpha,
        )
        drawHeadlightCone(
            origin = frame.rightOrigin,
            fan = if (high) frame.rightHighFan else frame.rightLowFan,
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
