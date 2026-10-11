package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import vad.dashing.tbox.R
import vad.dashing.tbox.toOwnedImageBitmap

@Composable
fun LauncherWallpaperDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
) {
    if (!visible) return

    val context = LocalContext.current
    var darkUri by remember(visible) {
        mutableStateOf(LauncherAppConfigStore.wallpaperDarkUri(context))
    }
    var lightUri by remember(visible) {
        mutableStateOf(LauncherAppConfigStore.wallpaperLightUri(context))
    }
    var scaleMode by remember(visible) {
        mutableStateOf(LauncherAppConfigStore.wallpaperScaleMode(context))
    }
    var targetThemeForPicker by remember { mutableStateOf<Boolean?>(null) } // true = dark, false = light
    var downloadsPickerTheme by remember { mutableStateOf<Boolean?>(null) }

    val systemImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        val selected = uri?.toString().orEmpty()
        val targetDark = targetThemeForPicker
        if (selected.isNotBlank() && targetDark != null) {
            if (targetDark) {
                darkUri = selected
                LauncherAppConfigStore.setWallpaperDarkUri(context, selected)
            } else {
                lightUri = selected
                LauncherAppConfigStore.setWallpaperLightUri(context, selected)
            }
        }
        targetThemeForPicker = null
    }

    if (downloadsPickerTheme != null) {
        val targetDark = downloadsPickerTheme!!
        LauncherDownloadsFolderPicker(
            onDismissRequest = { downloadsPickerTheme = null },
            onSelectFile = { file ->
                val path = file.absolutePath
                if (targetDark) {
                    darkUri = path
                    LauncherAppConfigStore.setWallpaperDarkUri(context, path)
                } else {
                    lightUri = path
                    LauncherAppConfigStore.setWallpaperLightUri(context, path)
                }
                downloadsPickerTheme = null
            },
        )
    }

    LauncherDarkAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(R.string.launcher_wallpaper_dialog_title),
                color = LauncherColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Dark theme wallpaper selection
                WallpaperThemeSection(
                    title = stringResource(R.string.launcher_wallpaper_dark_title),
                    currentUri = darkUri,
                    onPickSystem = {
                        targetThemeForPicker = true
                        systemImagePicker.launch("image/*")
                    },
                    onPickDownloads = {
                        downloadsPickerTheme = true
                    },
                    onClear = {
                        darkUri = ""
                        LauncherAppConfigStore.setWallpaperDarkUri(context, "")
                    },
                )

                // Light theme wallpaper selection
                WallpaperThemeSection(
                    title = stringResource(R.string.launcher_wallpaper_light_title),
                    currentUri = lightUri,
                    onPickSystem = {
                        targetThemeForPicker = false
                        systemImagePicker.launch("image/*")
                    },
                    onPickDownloads = {
                        downloadsPickerTheme = false
                    },
                    onClear = {
                        lightUri = ""
                        LauncherAppConfigStore.setWallpaperLightUri(context, "")
                    },
                )

                // Scale mode selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.launcher_wallpaper_scale_title),
                        color = LauncherColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val options = listOf(
                        WallpaperScaleMode.FILL_HEIGHT to stringResource(R.string.launcher_wallpaper_scale_height),
                        WallpaperScaleMode.FILL_WIDTH to stringResource(R.string.launcher_wallpaper_scale_width),
                        WallpaperScaleMode.FILL_BOUNDS to stringResource(R.string.launcher_wallpaper_scale_fill),
                    )
                    options.forEach { (mode, label) ->
                        val selected = scaleMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) LauncherColors.AccentCyan.copy(alpha = 0.15f)
                                    else LauncherColors.CardDark,
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) LauncherColors.AccentCyan else LauncherColors.SettingsBorder,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable {
                                    scaleMode = mode
                                    LauncherAppConfigStore.setWallpaperScaleMode(context, mode)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = label,
                                color = if (selected) LauncherColors.AccentCyan else LauncherColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    scaleMode = mode
                                    LauncherAppConfigStore.setWallpaperScaleMode(context, mode)
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = LauncherColors.AccentCyan,
                                    unselectedColor = LauncherColors.TextMuted,
                                ),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(
                    text = stringResource(R.string.action_close),
                    color = LauncherColors.AccentCyan,
                )
            }
        },
    )
}

@Composable
private fun WallpaperThemeSection(
    title: String,
    currentUri: String,
    onPickSystem: () -> Unit,
    onPickDownloads: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = LauncherColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (currentUri.isNotBlank()) {
            val fileName = uriToDisplayFileName(currentUri)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LauncherColors.CardDark)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fileName,
                    color = LauncherColors.AccentCyan,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.launcher_wallpaper_clear),
                    color = LauncherColors.WarningRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(onClick = onClear)
                        .padding(start = 8.dp),
                )
            }
        } else {
            Text(
                text = stringResource(R.string.launcher_wallpaper_none),
                color = LauncherColors.TextMuted,
                fontSize = 12.sp,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = onPickDownloads,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LauncherColors.CardDarkElevated)
                    .border(1.dp, LauncherColors.SettingsBorder, RoundedCornerShape(8.dp)),
            ) {
                Text(
                    text = stringResource(R.string.launcher_wallpaper_downloads_folder),
                    color = LauncherColors.TextPrimary,
                    fontSize = 12.sp,
                )
            }
            TextButton(
                onClick = onPickSystem,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LauncherColors.CardDarkElevated)
                    .border(1.dp, LauncherColors.SettingsBorder, RoundedCornerShape(8.dp)),
            ) {
                Text(
                    text = stringResource(R.string.launcher_wallpaper_system_picker),
                    color = LauncherColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun LauncherDownloadsFolderPicker(
    onDismissRequest: () -> Unit,
    onSelectFile: (File) -> Unit,
) {
    val downloadsDir = remember {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (dir.exists() && dir.isDirectory) dir else File("/storage/emulated/0/Download")
    }

    val imageFiles = remember(downloadsDir) {
        val extensions = setOf("jpg", "jpeg", "png", "webp")
        if (downloadsDir.exists() && downloadsDir.isDirectory) {
            downloadsDir.listFiles { file ->
                file.isFile && file.extension.lowercase() in extensions
            }?.toList().orEmpty().sortedByDescending { it.lastModified() }
        } else {
            emptyList()
        }
    }

    LauncherDarkAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(R.string.launcher_wallpaper_downloads_folder),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            if (imageFiles.isEmpty()) {
                Text(
                    text = "В папке Загрузки не найдено изображений (.jpg, .png, .webp)",
                    color = LauncherColors.TextMuted,
                    fontSize = 13.sp,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(imageFiles) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(LauncherColors.CardDark)
                                .clickable { onSelectFile(file) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = file.name,
                                color = LauncherColors.TextPrimary,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun uriToDisplayFileName(uriStr: String): String {
    if (uriStr.isBlank()) return ""
    return runCatching {
        if (uriStr.startsWith("content://")) {
            Uri.parse(uriStr).lastPathSegment?.substringAfterLast('/') ?: uriStr
        } else {
            File(uriStr).name
        }
    }.getOrDefault(uriStr)
}

internal fun loadWallpaperImageBitmap(context: Context, uriOrPath: String): ImageBitmap? {
    if (uriOrPath.isBlank()) return null
    return runCatching {
        if (uriOrPath.startsWith("content://")) {
            val uri = Uri.parse(uriOrPath)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)?.toOwnedImageBitmap()
            }
        } else {
            val file = File(uriOrPath)
            if (!file.isFile || file.length() <= 0L) return null
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
            val (w, h) = options.outWidth to options.outHeight
            if (w > 0 && h > 0) {
                val maxDim = 1920
                var sample = 1
                while (w / sample > maxDim || h / sample > maxDim) {
                    sample *= 2
                }
                val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = sample
                }
                android.graphics.BitmapFactory.decodeFile(file.absolutePath, decodeOptions)?.toOwnedImageBitmap()
            } else null
        }
    }.getOrNull()
}
