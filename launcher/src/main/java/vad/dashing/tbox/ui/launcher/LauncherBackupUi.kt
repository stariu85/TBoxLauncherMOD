package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.backup.LauncherBackupManager
import vad.dashing.tbox.ui.rememberWrappedOnClick
import vad.dashing.tbox.ui.requestHeadUnitReboot
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxHeadline
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LauncherBackupSection(
    settingsManager: SettingsManager,
    onRestoreSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    val autoBackupEnabled by settingsManager.autoBackupEnabledFlow.collectAsStateWithLifecycle(initialValue = false)

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.settings_backup_title),
            style = MaterialTheme.typography.tboxHeadline,
            color = LauncherColors.TextPrimary,
        )
        Text(
            text = stringResource(R.string.settings_backup_desc),
            style = MaterialTheme.typography.tboxBody,
            color = LauncherColors.TextMuted,
        )

        // Automatic backup toggle switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(LauncherColors.CardDark)
                .clickable {
                    scope.launch {
                        settingsManager.saveAutoBackupEnabled(!autoBackupEnabled)
                    }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_backup_auto_title),
                    style = MaterialTheme.typography.tboxBody,
                    color = LauncherColors.TextPrimary,
                )
                Text(
                    text = stringResource(R.string.settings_backup_auto_desc),
                    style = MaterialTheme.typography.tboxBody,
                    color = LauncherColors.TextMuted,
                    fontSize = 12.sp,
                )
            }
            Switch(
                checked = autoBackupEnabled,
                onCheckedChange = { checked ->
                    scope.launch {
                        settingsManager.saveAutoBackupEnabled(checked)
                    }
                },
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Save Backup Button
            Button(
                onClick = rememberWrappedOnClick {
                    scope.launch {
                        LauncherBackupManager.createBackup(context)
                            .onSuccess { file ->
                                isError = false
                                statusMessage = context.getString(
                                    R.string.settings_backup_created_success,
                                    file.name,
                                )
                                Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()
                            }
                            .onFailure { err ->
                                isError = true
                                statusMessage = "Ошибка создания бэкапа: ${err.message}"
                                Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()
                            }
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.settings_backup_create_button),
                    style = MaterialTheme.typography.tboxButton,
                )
            }

            // Restore Backup Button — opens file browser dialog directly
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    showRestoreDialog = true
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.settings_backup_restore_button),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        }

        if (!statusMessage.isNullOrEmpty()) {
            Text(
                text = statusMessage.orEmpty(),
                style = MaterialTheme.typography.tboxBody,
                color = if (isError) MaterialTheme.colorScheme.error else LauncherColors.AccentCyan,
                fontSize = 13.sp,
            )
        }
    }

    if (showRestoreDialog) {
        RestoreBackupDialog(
            onDismiss = { showRestoreDialog = false },
            onRestoreSuccess = {
                showRestoreDialog = false
                onRestoreSuccess()
            },
        )
    }
}

@Composable
fun RestoreBackupDialog(
    onDismiss: () -> Unit,
    onRestoreSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Quick location shortcuts
    val defaultDclDir = remember { LauncherBackupManager.getBackupsDir() }
    val downloadsDir = remember { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) }
    val rootStorageDir = remember { Environment.getExternalStorageDirectory() }

    var currentDir by remember { mutableStateOf(if (defaultDclDir.exists()) defaultDclDir else downloadsDir) }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                LauncherBackupManager.restoreBackupFromUri(context, uri)
                    .onSuccess { onRestoreSuccess() }
                    .onFailure { err -> errorMessage = "Ошибка чтения файла: ${err.message}" }
            }
        }
    }

    // Directory listing (Directories first, then .json files, then others)
    val filesList = remember(currentDir) {
        runCatching {
            val list = currentDir.listFiles()?.toList().orEmpty()
            val dirs = list.filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name.lowercase() }
            val jsonFiles = list.filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) }
                .sortedByDescending { it.lastModified() }
            val otherFiles = list.filter { it.isFile && !it.name.endsWith(".json", ignoreCase = true) && !it.name.startsWith(".") }
                .sortedBy { it.name.lowercase() }
            dirs + jsonFiles + otherFiles
        }.getOrDefault(emptyList())
    }

    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    LauncherDarkAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.settings_backup_restore_title),
                    style = MaterialTheme.typography.tboxHeadline,
                    color = LauncherColors.TextPrimary,
                )
                Text(
                    text = currentDir.absolutePath,
                    style = MaterialTheme.typography.tboxBody,
                    color = LauncherColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Quick location filter chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilterChip(
                        selected = currentDir == defaultDclDir,
                        onClick = { currentDir = defaultDclDir },
                        label = { Text("DCLbackups", fontSize = 11.sp) },
                    )
                    FilterChip(
                        selected = currentDir == downloadsDir,
                        onClick = { currentDir = downloadsDir },
                        label = { Text("Downloads", fontSize = 11.sp) },
                    )
                    FilterChip(
                        selected = currentDir == rootStorageDir,
                        onClick = { currentDir = rootStorageDir },
                        label = { Text("Память", fontSize = 11.sp) },
                    )
                }

                // Parent directory button
                if (currentDir.parentFile != null && currentDir.absolutePath != "/storage") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(LauncherColors.CardDarkElevated)
                            .clickable {
                                currentDir.parentFile?.let { currentDir = it }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "📁  ..  (на уровень вверх)",
                            style = MaterialTheme.typography.tboxBody,
                            color = LauncherColors.AccentCyan,
                            fontSize = 13.sp,
                        )
                    }
                }

                if (filesList.isEmpty()) {
                    Text(
                        text = "Папка пуста",
                        style = MaterialTheme.typography.tboxBody,
                        color = LauncherColors.TextMuted,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                    ) {
                        items(filesList, key = { it.absolutePath }) { file ->
                            val isJson = file.isFile && file.name.endsWith(".json", ignoreCase = true)
                            val isDir = file.isDirectory

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isJson) LauncherColors.CardDarkElevated
                                        else LauncherColors.CardDark,
                                    )
                                    .clickable {
                                        if (isDir) {
                                            currentDir = file
                                        } else if (isJson) {
                                            scope.launch {
                                                LauncherBackupManager.restoreBackupFromFile(context, file)
                                                    .onSuccess { onRestoreSuccess() }
                                                    .onFailure { err -> errorMessage = "Ошибка импорта: ${err.message}" }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isDir) "📁 " else if (isJson) "📄 " else "📄 ",
                                            fontSize = 14.sp,
                                        )
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.tboxBody,
                                            color = if (isJson) LauncherColors.TextPrimary
                                            else if (isDir) LauncherColors.AccentCyan
                                            else LauncherColors.TextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (file.isFile) {
                                        Text(
                                            text = "${dateFormat.format(Date(file.lastModified()))} · ${(file.length() + 1023) / 1024} KB",
                                            style = MaterialTheme.typography.tboxBody,
                                            color = LauncherColors.TextMuted,
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!errorMessage.isNullOrEmpty()) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }

                OutlinedButton(
                    onClick = rememberWrappedOnClick {
                        runCatching {
                            safLauncher.launch("*/*")
                        }.onFailure {
                            errorMessage = "Системный выбор файлов недоступен"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.settings_backup_restore_system_picker),
                        style = MaterialTheme.typography.tboxButton,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = rememberWrappedOnClick(onDismiss)) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = LauncherColors.TextSecondary,
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        },
    )
}

@Composable
fun RestoredRebootDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val rebootAction = rememberHuRebootActionEnabled()
    val rebootEnabled = rebootAction.enabled
    val onRebootUsed = rebootAction.onUsed

    LauncherDarkAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_backup_restored_title),
                style = MaterialTheme.typography.tboxHeadline,
                color = LauncherColors.TextPrimary,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.settings_backup_restored_message),
                style = MaterialTheme.typography.tboxBody,
                color = LauncherColors.TextSecondary,
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = rememberWrappedOnClick {
                        onDismiss()
                        restartLauncherApp(context)
                    },
                ) {
                    Text(
                        text = "Перезапустить лаунчер",
                        color = LauncherColors.AccentCyan,
                        style = MaterialTheme.typography.tboxButton,
                    )
                }
                TextButton(
                    onClick = rememberWrappedOnClick {
                        if (!rebootEnabled) return@rememberWrappedOnClick
                        onRebootUsed()
                        onDismiss()
                        requestHeadUnitReboot(context)
                    },
                    enabled = rebootEnabled,
                ) {
                    Text(
                        text = stringResource(R.string.button_reboot_hu),
                        color = if (rebootEnabled) LauncherColors.AccentCyan else LauncherColors.TextMuted,
                        style = MaterialTheme.typography.tboxButton,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = rememberWrappedOnClick(onDismiss)) {
                Text(
                    text = stringResource(R.string.action_close),
                    color = LauncherColors.TextSecondary,
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        },
    )
}

private fun restartLauncherApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
    }
}
