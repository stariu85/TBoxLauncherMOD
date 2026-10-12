package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vad.dashing.tbox.R
import vad.dashing.tbox.tbm.TbmAutomationItem

data class TbmIconOption(
    val key: String,
    val label: String,
    val vector: ImageVector,
)

val TBM_AUTOMATION_ICONS: List<TbmIconOption> = listOf(
    TbmIconOption("play", "Запуск", Icons.Filled.PlayArrow),
    TbmIconOption("bolt", "Действие", Icons.Filled.Add),
    TbmIconOption("star", "Избранное", Icons.Filled.Star),
    TbmIconOption("build", "Сервис", Icons.Filled.Build),
    TbmIconOption("favorite", "Любимое", Icons.Filled.Favorite),
    TbmIconOption("settings", "Настройки", Icons.Filled.Settings),
    TbmIconOption("refresh", "Обновить", Icons.Filled.Refresh),
    TbmIconOption("home", "Дом", Icons.Filled.Home),
    TbmIconOption("lock", "Замок", Icons.Filled.Lock),
    TbmIconOption("info", "Инфо", Icons.Filled.Info),
    TbmIconOption("warning", "Внимание", Icons.Filled.Warning),
    TbmIconOption("check", "Готово", Icons.Filled.Check),
)

fun resolveTbmIconVector(iconName: String): ImageVector {
    return TBM_AUTOMATION_ICONS.firstOrNull { it.key.equals(iconName, ignoreCase = true) }?.vector
        ?: Icons.Filled.PlayArrow
}

@Composable
internal fun LauncherTbmAutomationPickerDialog(
    visible: Boolean,
    isLoading: Boolean,
    automations: List<TbmAutomationItem>,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onPick: (TbmAutomationItem) -> Unit,
) {
    if (!visible) return

    LauncherDarkAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.launcher_tbm_automations_title),
                color = LauncherColors.TextPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    isLoading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(16.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = LauncherColors.AccentCyan,
                                strokeWidth = 2.dp,
                            )
                            Text(
                                stringResource(R.string.launcher_tbm_automations_loading),
                                color = LauncherColors.TextSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    }

                    !errorMessage.isNullOrEmpty() -> {
                        Text(
                            text = errorMessage,
                            color = LauncherColors.WarningAmber,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp),
                        )
                    }

                    automations.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.launcher_tbm_automations_empty),
                            color = LauncherColors.TextMuted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp),
                        )
                    }

                    else -> {
                        automations.forEach { automation ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(LauncherColors.CardDarkElevated.copy(alpha = 0.5f))
                                    .border(
                                        width = 1.dp,
                                        color = LauncherColors.TextMuted.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .clickable {
                                        onPick(automation)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(LauncherColors.AccentCyan.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = LauncherColors.AccentCyan,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Text(
                                    text = automation.name,
                                    color = LauncherColors.TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
internal fun LauncherTbmAutomationIconPickerDialog(
    visible: Boolean,
    currentIconName: String = "play",
    onDismiss: () -> Unit,
    onIconPicked: (iconName: String) -> Unit,
) {
    if (!visible) return

    LauncherDarkAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.launcher_tbm_select_icon_title),
                color = LauncherColors.TextPrimary,
            )
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .size(width = 320.dp, height = 240.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(TBM_AUTOMATION_ICONS) { option ->
                    val isSelected = option.key.equals(currentIconName, ignoreCase = true)
                    val bg = if (isSelected) {
                        LauncherColors.AccentCyan.copy(alpha = 0.25f)
                    } else {
                        LauncherColors.CardDarkElevated.copy(alpha = 0.4f)
                    }
                    val border = if (isSelected) {
                        LauncherColors.AccentCyan
                    } else {
                        LauncherColors.TextMuted.copy(alpha = 0.2f)
                    }

                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .border(1.dp, border, RoundedCornerShape(10.dp))
                            .clickable {
                                onIconPicked(option.key)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = option.vector,
                            contentDescription = option.label,
                            tint = if (isSelected) LauncherColors.AccentCyan else LauncherColors.TextPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = option.label,
                            color = LauncherColors.TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
