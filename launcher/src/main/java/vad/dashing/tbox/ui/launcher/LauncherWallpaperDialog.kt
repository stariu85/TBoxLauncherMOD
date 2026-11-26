package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.R
import vad.dashing.tbox.toOwnedImageBitmap
import java.io.File

private const val MAX_IMAGE_WALLPAPER_BYTES = 30L * 1024L * 1024L
private const val MAX_VIDEO_WALLPAPER_BYTES = 150L * 1024L * 1024L

internal sealed interface WallpaperSaveResult {
    data class Success(val path: String) : WallpaperSaveResult
    data class TooLarge(val maxMb: Int) : WallpaperSaveResult
    data object InvalidMedia : WallpaperSaveResult
}

private sealed interface WallpaperDialogError {
    data class TooLarge(val maxMb: Int) : WallpaperDialogError
    data object InvalidMedia : WallpaperDialogError
}

@Composable
fun LauncherWallpaperDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onTemporarilyHide: (() -> Unit)? = null,
    onRestoreDialog: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var darkUri by remember(visible) {
        mutableStateOf(LauncherAppConfigStore.wallpaperDarkUri(context))
    }
    var lightUri by remember(visible) {
        mutableStateOf(LauncherAppConfigStore.wallpaperLightUri(context))
    }
    val darkRevision by LauncherAppConfigStore.wallpaperDarkRevisionFlow.collectAsStateWithLifecycle()
    val lightRevision by LauncherAppConfigStore.wallpaperLightRevisionFlow.collectAsStateWithLifecycle()
    var scaleMode by remember(visible) {
        mutableStateOf(LauncherAppConfigStore.wallpaperScaleMode(context))
    }
    var targetThemeForPicker by remember { mutableStateOf<Boolean?>(null) } // true = dark, false = light
    var expectVideoForPicker by remember { mutableStateOf(false) }
    var isCopying by remember(visible) { mutableStateOf(false) }
    var dialogError by remember(visible) { mutableStateOf<WallpaperDialogError?>(null) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        val targetDark = targetThemeForPicker
        val expectVideo = expectVideoForPicker
        targetThemeForPicker = null
        expectVideoForPicker = false
        onRestoreDialog?.invoke()

        if (uri != null && targetDark != null) {
            isCopying = true
            dialogError = null
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    saveWallpaperMediaToInternalStorage(
                        context = context,
                        uri = uri,
                        isDark = targetDark,
                        expectVideo = expectVideo,
                    )
                }
                isCopying = false
                when (result) {
                    is WallpaperSaveResult.Success -> {
                        if (targetDark) {
                            darkUri = result.path
                            LauncherAppConfigStore.setWallpaperDarkUri(context, result.path)
                        } else {
                            lightUri = result.path
                            LauncherAppConfigStore.setWallpaperLightUri(context, result.path)
                        }
                    }
                    is WallpaperSaveResult.TooLarge -> {
                        dialogError = WallpaperDialogError.TooLarge(result.maxMb)
                    }
                    WallpaperSaveResult.InvalidMedia -> {
                        dialogError = WallpaperDialogError.InvalidMedia
                    }
                }
            }
        }
    }

    if (!visible) return

    LauncherDarkAlertDialog(
        onDismissRequest = { if (!isCopying) onDismissRequest() },
        title = {
            Text(
                text = stringResource(R.string.launcher_wallpaper_dialog_title),
                color = LauncherColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isCopying) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(LauncherColors.AccentCyan.copy(alpha = 0.12f))
                            .border(1.dp, LauncherColors.AccentCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = LauncherColors.AccentCyan,
                        )
                        Text(
                            text = stringResource(R.string.launcher_wallpaper_copying),
                            color = LauncherColors.AccentCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                dialogError?.let { err ->
                    val msg = when (err) {
                        is WallpaperDialogError.TooLarge -> stringResource(
                            R.string.launcher_wallpaper_error_too_large,
                            err.maxMb,
                        )
                        WallpaperDialogError.InvalidMedia -> stringResource(
                            R.string.launcher_wallpaper_error_invalid,
                        )
                    }
                    Text(
                        text = msg,
                        color = LauncherColors.WarningRed,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(LauncherColors.WarningRed.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }

                // Dark theme wallpaper selection
                WallpaperThemeSection(
                    title = stringResource(R.string.launcher_wallpaper_dark_title),
                    currentUri = darkUri,
                    mediaRevision = darkRevision,
                    enabled = !isCopying,
                    onPickImage = {
                        dialogError = null
                        targetThemeForPicker = true
                        expectVideoForPicker = false
                        onTemporarilyHide?.invoke()
                        mediaPicker.launch("image/*")
                    },
                    onPickVideo = {
                        dialogError = null
                        targetThemeForPicker = true
                        expectVideoForPicker = true
                        onTemporarilyHide?.invoke()
                        mediaPicker.launch("video/*")
                    },
                    onClear = {
                        dialogError = null
                        clearStoredWallpaperFiles(context, isDark = true, currentUri = darkUri)
                        darkUri = ""
                        LauncherAppConfigStore.setWallpaperDarkUri(context, "")
                    },
                )

                // Light theme wallpaper selection
                WallpaperThemeSection(
                    title = stringResource(R.string.launcher_wallpaper_light_title),
                    currentUri = lightUri,
                    mediaRevision = lightRevision,
                    enabled = !isCopying,
                    onPickImage = {
                        dialogError = null
                        targetThemeForPicker = false
                        expectVideoForPicker = false
                        onTemporarilyHide?.invoke()
                        mediaPicker.launch("image/*")
                    },
                    onPickVideo = {
                        dialogError = null
                        targetThemeForPicker = false
                        expectVideoForPicker = true
                        onTemporarilyHide?.invoke()
                        mediaPicker.launch("video/*")
                    },
                    onClear = {
                        dialogError = null
                        clearStoredWallpaperFiles(context, isDark = false, currentUri = lightUri)
                        lightUri = ""
                        LauncherAppConfigStore.setWallpaperLightUri(context, "")
                    },
                )

                Text(
                    text = stringResource(R.string.launcher_wallpaper_live_hint),
                    color = LauncherColors.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
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
                        WallpaperScaleMode.FILL_WIDTH to stringResource(R.string.launcher_wallpaper_scale_width),
                        WallpaperScaleMode.FILL_HEIGHT to stringResource(R.string.launcher_wallpaper_scale_height),
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
            TextButton(
                onClick = onDismissRequest,
                enabled = !isCopying,
            ) {
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
    mediaRevision: Int,
    enabled: Boolean,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onClear: () -> Unit,
) {
    val mediaType = remember(currentUri, mediaRevision) {
        if (currentUri.isBlank()) null else detectWallpaperMediaTypeFromPath(currentUri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = LauncherColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (currentUri.isNotBlank()) {
            val fileName = uriToDisplayFileName(currentUri)
            val badgeText = when (mediaType) {
                WallpaperMediaType.VIDEO -> stringResource(R.string.launcher_wallpaper_type_video)
                WallpaperMediaType.ANIMATED_IMAGE -> stringResource(R.string.launcher_wallpaper_type_animated)
                else -> stringResource(R.string.launcher_wallpaper_type_image)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LauncherColors.CardDark)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = badgeText,
                        color = LauncherColors.TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(LauncherColors.AccentCyan.copy(alpha = 0.25f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Text(
                        text = fileName,
                        color = LauncherColors.AccentCyan,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = stringResource(R.string.launcher_wallpaper_clear),
                    color = LauncherColors.WarningRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(enabled = enabled, onClick = onClear)
                        .padding(start = 8.dp),
                )
            }
        } else {
            Text(
                text = stringResource(R.string.launcher_wallpaper_default),
                color = LauncherColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = onPickImage,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LauncherColors.CardDarkElevated)
                    .border(1.dp, LauncherColors.SettingsBorder, RoundedCornerShape(8.dp)),
            ) {
                Text(
                    text = stringResource(R.string.launcher_wallpaper_pick_image),
                    color = LauncherColors.AccentCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            TextButton(
                onClick = onPickVideo,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LauncherColors.CardDarkElevated)
                    .border(1.dp, LauncherColors.AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
            ) {
                Text(
                    text = stringResource(R.string.launcher_wallpaper_pick_video),
                    color = LauncherColors.AccentCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
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

private fun clearStoredWallpaperFiles(context: Context, isDark: Boolean, currentUri: String) {
    if (currentUri.isNotBlank() && !currentUri.startsWith("content://")) {
        LiveWallpaperPosterCache.invalidatePath(currentUri)
        runCatching { File(currentUri).delete() }
    }
    val prefix = if (isDark) "wallpaper_dark." else "wallpaper_light."
    val wallpaperDir = File(context.filesDir, "wallpapers")
    wallpaperDir.listFiles()?.forEach { file ->
        if (file.isFile && file.name.startsWith(prefix)) {
            LiveWallpaperPosterCache.invalidatePath(file.absolutePath)
            runCatching { file.delete() }
        }
    }
}

private fun resolveWallpaperExtension(
    context: Context,
    uri: Uri,
    expectVideo: Boolean,
): Pair<String, Boolean> {
    val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()?.lowercase().orEmpty()
    val displayName = queryUriDisplayName(context, uri).orEmpty()
    val nameExt = displayName.substringAfterLast('.', "").lowercase()

    val isVideo = mime.startsWith("video/") ||
        nameExt in setOf("mp4", "m4v", "webm", "mkv", "3gp", "mov") ||
        (expectVideo && mime.isBlank() && nameExt.isBlank())

    val ext = when {
        mime == "video/mp4" || nameExt == "mp4" -> "mp4"
        mime == "video/webm" || nameExt == "webm" -> "webm"
        mime == "video/x-matroska" || nameExt == "mkv" -> "mkv"
        mime == "video/3gpp" || nameExt == "3gp" -> "3gp"
        mime == "video/quicktime" || nameExt == "mov" -> "mov"
        nameExt == "m4v" -> "m4v"
        isVideo -> "mp4"
        mime == "image/gif" || nameExt == "gif" -> "gif"
        mime == "image/webp" || nameExt == "webp" -> "webp"
        mime == "image/png" || nameExt == "png" -> "png"
        else -> "jpg"
    }
    return ext to isVideo
}

private fun queryUriDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
}

private fun queryUriSizeBytes(context: Context, uri: Uri): Long? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) c.getLong(idx) else null
        }
    }.getOrNull()
}

private fun validateSavedWallpaperFile(file: File, isVideo: Boolean): Boolean {
    if (!file.isFile || file.length() <= 0L) return false
    if (isVideo) {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            (hasVideo.equals("yes", ignoreCase = true) || (width > 0 && height > 0))
        } catch (_: Exception) {
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    val mediaType = detectWallpaperMediaTypeFromPath(file.absolutePath)
    if (mediaType == WallpaperMediaType.ANIMATED_IMAGE) {
        return runCatching {
            val source = ImageDecoder.createSource(file)
            ImageDecoder.decodeDrawable(source) { _, info, _ ->
                check(info.size.width > 0 && info.size.height > 0)
            }
            true
        }.getOrDefault(false)
    }

    return runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.outWidth > 0 && options.outHeight > 0
    }.getOrDefault(false)
}

internal fun saveWallpaperMediaToInternalStorage(
    context: Context,
    uri: Uri,
    isDark: Boolean,
    expectVideo: Boolean = false,
): WallpaperSaveResult {
    val (ext, isVideo) = resolveWallpaperExtension(context, uri, expectVideo)
    val maxBytes = if (isVideo) MAX_VIDEO_WALLPAPER_BYTES else MAX_IMAGE_WALLPAPER_BYTES
    val maxMb = (maxBytes / (1024L * 1024L)).toInt()

    val declaredSize = queryUriSizeBytes(context, uri)
    if (declaredSize != null && declaredSize > maxBytes) {
        return WallpaperSaveResult.TooLarge(maxMb)
    }

    val wallpaperDir = File(context.filesDir, "wallpapers").apply { mkdirs() }
    val basePrefix = if (isDark) "wallpaper_dark" else "wallpaper_light"
    val tempFile = File(wallpaperDir, "$basePrefix.tmp.$ext")
    val targetFile = File(wallpaperDir, "$basePrefix.$ext")

    return try {
        var totalBytes = 0L
        var exceededLimit = false
        val copied = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            tempFile.outputStream().use { outputStream ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = inputStream.read(buffer)
                    if (read <= 0) break
                    totalBytes += read
                    if (totalBytes > maxBytes) {
                        exceededLimit = true
                        break
                    }
                    outputStream.write(buffer, 0, read)
                }
            }
            true
        } ?: false

        if (exceededLimit) {
            runCatching { tempFile.delete() }
            return WallpaperSaveResult.TooLarge(maxMb)
        }
        if (!copied || !tempFile.exists() || tempFile.length() <= 0L) {
            runCatching { tempFile.delete() }
            return WallpaperSaveResult.InvalidMedia
        }

        if (!validateSavedWallpaperFile(tempFile, isVideo)) {
            runCatching { tempFile.delete() }
            return WallpaperSaveResult.InvalidMedia
        }

        // Remove any previous wallpaper files for this theme (including different extensions)
        wallpaperDir.listFiles()?.forEach { existing ->
            if (existing.isFile && existing.name.startsWith("$basePrefix.") && existing != tempFile) {
                LiveWallpaperPosterCache.invalidatePath(existing.absolutePath)
                runCatching { existing.delete() }
            }
        }

        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            runCatching { tempFile.delete() }
        }
        LiveWallpaperPosterCache.invalidatePath(targetFile.absolutePath)
        if (isVideo) {
            // Warm poster cache on IO thread right away so transition has zero delay
            extractVideoPosterBitmap(targetFile)
        }

        WallpaperSaveResult.Success(targetFile.absolutePath)
    } catch (_: Exception) {
        runCatching { tempFile.delete() }
        WallpaperSaveResult.InvalidMedia
    }
}

internal fun saveWallpaperImageToInternalStorage(context: Context, uri: Uri, isDark: Boolean): String? {
    return (saveWallpaperMediaToInternalStorage(context, uri, isDark) as? WallpaperSaveResult.Success)?.path
}

internal fun loadWallpaperImageBitmap(context: Context, uriOrPath: String, isDark: Boolean): ImageBitmap? {
    if (uriOrPath.isBlank()) {
        val defaultRes = if (isDark) R.drawable.default_wallpaper_dark else R.drawable.default_wallpaper_light
        return runCatching {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeResource(context.resources, defaultRes, options)
            val (w, h) = options.outWidth to options.outHeight
            if (w > 0 && h > 0) {
                val maxDim = 1920
                var sample = 1
                while (w / sample > maxDim || h / sample > maxDim) {
                    sample *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sample
                }
                BitmapFactory.decodeResource(context.resources, defaultRes, decodeOptions)?.toOwnedImageBitmap()
            } else null
        }.getOrNull()
    }

    return runCatching {
        val file = if (uriOrPath.startsWith("content://")) {
            val uri = Uri.parse(uriOrPath)
            saveWallpaperImageToInternalStorage(context, uri, isDark)?.let { File(it) }
        } else {
            File(uriOrPath)
        }
        if (file == null || !file.isFile || file.length() <= 0L) return null

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val (w, h) = options.outWidth to options.outHeight
        if (w > 0 && h > 0) {
            val maxDim = 1920
            var sample = 1
            while (w / sample > maxDim || h / sample > maxDim) {
                sample *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sample
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)?.toOwnedImageBitmap()
        } else null
    }.getOrNull()
}
