package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GEAR_SLOTS = listOf('P', 'R', 'N', 'D')

fun resolveActiveGearSlot(gearBoxMode: String, gearBoxCurrentGear: Int?): Char? {
    val mode = gearBoxMode.uppercase()
    // Mode letter wins. currentGear==0 while in D is "stopped in drive", not Park —
    // treating it as P snapped the 3D camera to top-down with the road still in drive.
    // "N/A" must not match Neutral (`contains('N')`).
    return when {
        mode.contains('R') -> 'R'
        mode == "N" || mode.startsWith("N ") -> 'N'
        mode.contains('D') -> 'D'
        mode.contains('P') && !mode.contains("N/A") -> 'P'
        gearBoxCurrentGear == 0 -> null
        gearBoxCurrentGear != null && gearBoxCurrentGear > 0 -> 'D'
        else -> null
    }
}

@Composable
fun LauncherGearSelector(
    activeSlot: Char?,
    modifier: Modifier = Modifier,
    onDClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GEAR_SLOTS.forEach { slot ->
            val active = activeSlot == slot
            val dClickable = slot == 'D' && onDClick != null
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (active) LauncherColors.GearActive else LauncherColors.LeftPanelCard
                    )
                    .then(
                        if (dClickable) {
                            Modifier.clickable(
                                interactionSource = remember(slot) { MutableInteractionSource() },
                                indication = null,
                                onClick = { onDClick?.invoke() },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = slot.toString(),
                    fontSize = 18.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) LauncherColors.LeftPanelBg else LauncherColors.GearInactive,
                )
            }
        }
    }
}
