package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R

/**
 * Standard Card Panel for selecting 3D car model paint color:
 * Shows a horizontal row of 6 paint color swatches in a standard panel card.
 */
@Composable
fun LauncherCarColorPickerBar(
    modifier: Modifier = Modifier,
    heightDp: Dp = 60.dp,
) {
    val context = LocalContext.current
    val carPaintRevision by LauncherAppConfigStore.carPaintRevisionFlow.collectAsStateWithLifecycle()
    val selectedPaintId = remember(context, carPaintRevision) {
        LauncherAppConfigStore.carPaintId(context)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .clip(RoundedCornerShape(12.dp))
            .background(LauncherColors.LeftPanelCard),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherCarPaint.options.forEach { option ->
                val selected = option.id == selectedPaintId
                val swatch = Color(option.colorArgb)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) Color(0xFF10B981) else Color.White.copy(alpha = 0.25f),
                            shape = CircleShape,
                        )
                        .clickable {
                            LauncherAppConfigStore.setCarPaintId(context, option.id)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LauncherCarColorPicker(
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(LauncherColors.CardDark.copy(alpha = 0.96f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.launcher_paint_picker_title),
            color = LauncherColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LauncherCarPaint.options.forEach { option ->
                val selected = option.id == selectedId
                val swatch = Color(option.colorArgb)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) LauncherColors.AccentCyan else LauncherColors.TextMuted.copy(alpha = 0.5f),
                            shape = CircleShape,
                        )
                        .clickable { onSelect(option.id) },
                    contentAlignment = Alignment.Center,
                ) {}
            }
        }
        Text(
            text = stringResource(R.string.launcher_paint_picker_close),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = LauncherColors.TextMuted,
            fontSize = 11.sp,
        )
    }
}
