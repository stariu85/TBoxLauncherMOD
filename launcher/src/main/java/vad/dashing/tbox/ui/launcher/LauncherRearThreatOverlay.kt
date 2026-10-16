package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/**
 * Body-local marks: DOW = gradient stripes on ADAS edges, RCTA = incoming bar, RCW = rear wash.
 * BSD is a PDC-style sonar band on the rear-side diagonals ([LauncherBsdOverlay]).
 */
@Composable
fun LauncherRearThreatOverlay(
    threats: LauncherRearThreats,
    modifier: Modifier = Modifier,
) {
    if (!threats.hasAny) return
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRearThreats(threats)
    }
}

private fun DrawScope.drawRearThreats(threats: LauncherRearThreats) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.58f
    val bodyHalfW = w * 0.145f
    val bodyHalfH = h * 0.20f

    fun fillColor(level: LauncherRearThreatLevel): Color? = when (level) {
        LauncherRearThreatLevel.Alert -> Color(0xFFEF4444)
        LauncherRearThreatLevel.Caution -> Color(0xFFF97316)
        LauncherRearThreatLevel.Off -> null
    }

    listOf(-1f, 1f).forEach { side ->
        val dow = if (side < 0f) threats.dowLeft else threats.dowRight
        val rcta = if (side < 0f) threats.rctaLeft else threats.rctaRight
        fillColor(dow)?.let { drawDowStripe(w, h, side, it, dow) }
        if (rcta == LauncherRearThreatLevel.Alert) {
            fillColor(rcta)?.let { drawRctaBar(cx, cy, bodyHalfW, bodyHalfH, side, it) }
        }
    }

    fillColor(threats.rcw)?.let { base ->
        val rw = bodyHalfW * 1.55f
        val rh = h * 0.055f
        val top = cy + bodyHalfH * 0.92f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    base.copy(alpha = 0.55f),
                    base.copy(alpha = 0.10f),
                    Color.Transparent,
                ),
            ),
            topLeft = Offset(cx - rw / 2f, top),
            size = Size(rw, rh),
            cornerRadius = CornerRadius(8f, 8f),
        )
    }
}

private fun DrawScope.drawDowStripe(
    w: Float,
    h: Float,
    side: Float,
    color: Color,
    level: LauncherRearThreatLevel,
) {
    val stripeWidthPx = 70.dp.toPx()
    val maxAlpha = if (level == LauncherRearThreatLevel.Alert) 0.85f else 0.70f

    val startPt = if (side < 0f) Offset(0f, h) else Offset(w, h)
    val endPt = if (side < 0f) Offset(stripeWidthPx, 0f) else Offset(w - stripeWidthPx, 0f)

    val brush = Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to color.copy(alpha = maxAlpha),
            0.35f to color.copy(alpha = maxAlpha * 0.55f),
            0.75f to color.copy(alpha = maxAlpha * 0.15f),
            1.00f to Color.Transparent,
        ),
        start = startPt,
        end = endPt,
    )

    val topLeft = if (side < 0f) Offset(0f, 0f) else Offset(w - stripeWidthPx, 0f)

    drawRect(
        brush = brush,
        topLeft = topLeft,
        size = Size(stripeWidthPx, h),
    )
}

private fun DrawScope.drawRctaBar(
    cx: Float,
    cy: Float,
    bodyHalfW: Float,
    bodyHalfH: Float,
    side: Float,
    color: Color,
) {
    val barW = bodyHalfW * 0.42f
    val barH = bodyHalfH * 0.16f
    val left = cx + side * bodyHalfW * 1.05f - if (side < 0f) barW else 0f
    val top = cy + bodyHalfH * 0.78f
    drawRoundRect(
        color = color.copy(alpha = 0.88f),
        topLeft = Offset(left, top),
        size = Size(barW, barH),
        cornerRadius = CornerRadius(4f, 4f),
    )
}
