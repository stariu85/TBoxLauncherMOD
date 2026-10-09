package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vad.dashing.tbox.ui.theme.tboxCaption

enum class VehicleSettingsSection {
    Status,
    Wheels,
    Body,
    Lights,
    Cabin,
    Adas,
    Comfort,
    Cruise,
    Launcher,
    Theme,
    System,
    Experimental,
    Simulation,
}

@Composable
fun LauncherVehicleSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (expanded) LauncherColors.CardDarkElevated else LauncherColors.CardDark),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = LauncherColors.AccentCyan, modifier = Modifier.size(22.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.tboxCaption,
                        color = LauncherColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = subtitle,
                        color = LauncherColors.TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                null,
                tint = LauncherColors.TextMuted,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                content()
            }
        }
    }
}

object VehicleSettingsSectionIcons {
    val Status = Icons.Filled.Settings
    val Wheels = Icons.Filled.Build
    val Body = Icons.Filled.Star
    val Lights = Icons.Filled.Star
    val Cabin = Icons.Filled.Home
    val Adas = Icons.Filled.PlayArrow
    val Comfort = Icons.Filled.Settings
    val Cruise = Icons.Filled.PlayArrow
    val Launcher = Icons.Filled.Menu
    val Theme = Icons.Filled.Star
    val System = Icons.Filled.Settings
    val Experimental = Icons.Filled.Build
    val Simulation = Icons.Filled.Warning
}

@Composable
fun LauncherSettingsValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(rowModifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = LauncherColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            value,
            color = LauncherColors.TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun LauncherSettingsToggleRow(
    label: String,
    active: Boolean,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = onClick != null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (enabled && onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (enabled) LauncherColors.TextPrimary else LauncherColors.TextMuted,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(26.dp)
                .clip(CircleShape)
                .background(
                    if (active) LauncherColors.AccentCyan
                    else LauncherColors.TextMuted.copy(alpha = 0.45f),
                ),
            contentAlignment = if (active) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(3.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (enabled) Color.White else LauncherColors.TextSecondary),
            )
        }
    }
}
