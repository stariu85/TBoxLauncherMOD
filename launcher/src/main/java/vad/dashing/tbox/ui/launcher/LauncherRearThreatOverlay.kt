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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Body-local marks: DOW = exclamation mark badge in ADAS area, RCTA = incoming bar, RCW = rear wash.
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
        LauncherRearThreatLevel.Alert -> Color(0xFFEF4444) // Красный — тревога
        LauncherRearThreatLevel.Caution -> Color(0xFFFFB300) // Жёлтый — предупреждение
        LauncherRearThreatLevel.Off -> null
    }

    listOf(-1f, 1f).forEach { side ->
        val dow = if (side < 0f) threats.dowLeft else threats.dowRight
        val rcta = if (side < 0f) threats.rctaLeft else threats.rctaRight
        fillColor(dow)?.let { drawDowExclamationMark(w, h, side, it, dow) }
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

private fun DrawScope.drawDowExclamationMark(
    w: Float,
    h: Float,
    side: Float,
    color: Color,
    level: LauncherRearThreatLevel,
) {
    val edgeOffsetPx = 50.dp.toPx()
    val centerX = if (side < 0f) edgeOffsetPx else w - edgeOffsetPx
    val centerY = h * 0.50f
    val center = Offset(centerX, centerY)

    val badgeRadius = 22.dp.toPx()

    // 1. Внешнее свечение (аура) вокруг значка
    val glowRadius = badgeRadius * 1.35f
    drawCircle(
        color = color.copy(alpha = if (level == LauncherRearThreatLevel.Alert) 0.35f else 0.22f),
        radius = glowRadius,
        center = center,
    )

    // 2. Основной круг предупреждения
    drawCircle(
        color = color,
        radius = badgeRadius,
        center = center,
    )

    // Тёмная обводка значка для контрастности
    drawCircle(
        color = Color(0x60000000),
        radius = badgeRadius,
        center = center,
        style = Stroke(width = 1.8.dp.toPx()),
    )

    // 3. Восклицательный знак '!'
    val markColor = Color(0xFF111111)

    // Верхняя вертикальная черта
    val barWidth = 4.5.dp.toPx()
    val barHeight = 13.dp.toPx()
    val barTopY = centerY - 10.5.dp.toPx()

    drawRoundRect(
        color = markColor,
        topLeft = Offset(centerX - barWidth / 2f, barTopY),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(2.2.dp.toPx(), 2.2.dp.toPx()),
    )

    // Нижняя точка
    val dotRadius = 2.4.dp.toPx()
    val dotCenterY = centerY + 7.5.dp.toPx()

    drawCircle(
        color = markColor,
        radius = dotRadius,
        center = Offset(centerX, dotCenterY),
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
