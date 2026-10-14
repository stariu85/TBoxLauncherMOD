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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import vad.dashing.tbox.ui.ALL_TBM_AUTOMATION_ICONS

data class TbmIconOption(
    val key: String,
    val label: String,
    val vector: ImageVector,
)

val TBM_AUTOMATION_ICONS: List<TbmIconOption>
    get() = ALL_TBM_AUTOMATION_ICONS

fun resolveTbmIconVector(iconName: String): ImageVector {
    return ALL_TBM_AUTOMATION_ICONS.firstOrNull { it.key.equals(iconName, ignoreCase = true) }?.vector
        ?: ALL_TBM_AUTOMATION_ICONS.firstOrNull { it.key == "play" }?.vector
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
internal fun LauncherTbmAutomationEditDialog(
    visible: Boolean,
    initialAutomationName: String = "",
    initialIconName: String = "play",
    onDismiss: () -> Unit,
    onSave: (iconName: String, title: String) -> Unit,
) {
    if (!visible) return

    var titleText by remember(visible, initialAutomationName) { mutableStateOf(initialAutomationName) }
    var selectedIconKey by remember(visible, initialIconName) { mutableStateOf(initialIconName) }
    var searchQuery by remember(visible) { mutableStateOf("") }

    LauncherDarkAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Настройка автоматизации",
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Название (оставьте пустым — одна иконка)", color = LauncherColors.TextSecondary, fontSize = 11.sp) },
                    placeholder = { Text("Без названия", color = LauncherColors.TextMuted, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (titleText.isNotEmpty()) {
                            IconButton(
                                onClick = { titleText = "" },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Очистить",
                                    tint = LauncherColors.TextMuted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LauncherColors.TextPrimary,
                        unfocusedTextColor = LauncherColors.TextPrimary,
                        focusedBorderColor = LauncherColors.AccentCyan,
                        unfocusedBorderColor = LauncherColors.TextMuted.copy(alpha = 0.4f),
                    ),
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск иконки (всего ${ALL_TBM_AUTOMATION_ICONS.size})...", color = LauncherColors.TextMuted, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Очистить поиск",
                                    tint = LauncherColors.TextMuted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LauncherColors.TextPrimary,
                        unfocusedTextColor = LauncherColors.TextPrimary,
                        focusedBorderColor = LauncherColors.AccentCyan,
                        unfocusedBorderColor = LauncherColors.TextMuted.copy(alpha = 0.3f),
                    ),
                )

                val filteredIcons = remember(searchQuery) {
                    if (searchQuery.isBlank()) {
                        ALL_TBM_AUTOMATION_ICONS
                    } else {
                        ALL_TBM_AUTOMATION_ICONS.filter {
                            it.label.contains(searchQuery, ignoreCase = true) ||
                                it.key.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filteredIcons) { option ->
                        val isSelected = option.key.equals(selectedIconKey, ignoreCase = true)
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
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .border(1.dp, border, RoundedCornerShape(8.dp))
                                .clickable { selectedIconKey = option.key }
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = option.vector,
                                contentDescription = option.label,
                                tint = if (isSelected) LauncherColors.AccentCyan else LauncherColors.TextPrimary,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                text = option.label,
                                color = if (isSelected) LauncherColors.AccentCyan else LauncherColors.TextSecondary,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        onSave(selectedIconKey, titleText.trim())
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LauncherColors.AccentCyan,
                        contentColor = LauncherColors.CanvasDark,
                    ),
                ) {
                    Text("Сохранить", fontWeight = FontWeight.Bold)
                }
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
    LauncherTbmAutomationEditDialog(
        visible = visible,
        initialAutomationName = "",
        initialIconName = currentIconName,
        onDismiss = onDismiss,
        onSave = { iconName, _ -> onIconPicked(iconName) },
    )
}
