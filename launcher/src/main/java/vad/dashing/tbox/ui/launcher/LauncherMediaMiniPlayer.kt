package vad.dashing.tbox.ui.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import vad.dashing.tbox.MediaControlNotificationListenerService
import vad.dashing.tbox.R
import vad.dashing.tbox.SharedMediaControlService
import vad.dashing.tbox.ui.LaunchableAppEntry
import vad.dashing.tbox.ui.theme.tboxCaption

private const val LAUNCHER_MEDIA_SOURCE_ID = "launcher_mini_player"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherMediaMiniPlayer(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var defaultRevision by remember { mutableIntStateOf(0) }
    var pickerVisible by remember { mutableStateOf(false) }
    var pickerFallback by remember { mutableStateOf(false) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val playerStates by SharedMediaControlService.playerStates.collectAsStateWithLifecycle()
    val mediaAlphaRevision by LauncherAppConfigStore.mediaCardAlphaRevisionFlow
        .collectAsStateWithLifecycle()
    val mediaCardAlpha = remember(context, mediaAlphaRevision) {
        LauncherAppConfigStore.mediaCardAlpha(context)
    }

    val isDark = LauncherThemeState.darkTheme

    val listenerComponent = remember {
        ComponentName(
            context,
            MediaControlNotificationListenerService::class.java,
        )
    }
    DisposableEffect(context) {
        val uri = Settings.Secure.getUriFor("enabled_notification_listeners")
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                permissionTick++
            }
        }
        context.contentResolver.registerContentObserver(uri, false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val notificationAccessGranted = remember(permissionTick) {
        isNotificationListenerEnabled(context, listenerComponent)
    }

    val defaultPackage = remember(context, defaultRevision) {
        LauncherAppConfigStore.defaultMediaPackage(context)
    }
    val mediaPackages = remember(context, defaultRevision, playerStates) {
        (discoverAllMediaPlayerPackages(context, defaultPackage) + playerStates.keys).distinct()
    }
    val monitorPackages = remember(mediaPackages, defaultPackage) {
        buildSet {
            defaultPackage?.let { add(it) }
            addAll(mediaPackages)
        }
    }

    DisposableEffect(context, monitorPackages) {
        if (monitorPackages.isNotEmpty()) {
            SharedMediaControlService.updateSourceSelection(context, LAUNCHER_MEDIA_SOURCE_ID, monitorPackages)
        }
        onDispose {
            SharedMediaControlService.clearSourceSelection(LAUNCHER_MEDIA_SOURCE_ID)
        }
    }

    val mediaPickerApps = remember(context, mediaPackages) {
        val pm = context.packageManager
        mediaPackages.mapNotNull { pkg ->
            runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(info).toString()
                val icon = runCatching {
                    val sizePx = (64f * context.resources.displayMetrics.density).toInt().coerceIn(96, 256)
                    val drawable = pm.getApplicationIcon(info)
                    if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
                        drawable.setBounds(0, 0, sizePx, sizePx)
                    }
                    drawable.toBitmap(sizePx, sizePx).asImageBitmap()
                }.getOrNull()
                LaunchableAppEntry(packageName = pkg, label = label, icon = icon, activityName = null)
            }.getOrNull()
        }.sortedBy { it.label.lowercase() }
    }

    val preferredPackage = defaultPackage?.takeIf { it in monitorPackages }
        ?: mediaPackages.firstOrNull().orEmpty()
    val mediaState = remember(monitorPackages, playerStates, preferredPackage) {
        SharedMediaControlService.resolveWidgetState(
            selectedPackages = monitorPackages,
            currentStates = playerStates,
            preferredPackage = preferredPackage,
        )
    }
    val activePkg = preferredPackage.ifBlank { mediaPackages.firstOrNull().orEmpty() }
    val openActivePlayer: () -> Unit = {
        activePkg.takeIf { it.isNotBlank() }?.let { launchLauncherApp(context, it) }
    }
    val title = mediaState.track
    val artist = mediaState.artist
    val isPlaying = mediaState.isPlaying
    val albumArtBitmap = remember(activePkg, playerStates, title) {
        runCatching {
            SharedMediaControlService.albumArtFor(activePkg)?.asImageBitmap()
        }.getOrNull()
    }

    // Tint the card towards the album art's accent so the widget follows the music.
    val mediaCardTarget = remember(albumArtBitmap, isDark) {
        val baseCardColor = if (isDark) {
            LauncherColors.LeftPanelCard
        } else {
            Color(0xFAFFFFFF)
        }
        val bitmap = albumArtBitmap ?: return@remember baseCardColor
        runCatching {
            val palette = Palette.from(bitmap.asAndroidBitmap()).generate()
            if (isDark) {
                val swatch = palette.darkVibrantSwatch ?: palette.darkMutedSwatch ?: palette.dominantSwatch
                swatch?.let { lerp(baseCardColor, Color(it.rgb), 0.42f) }
            } else {
                val swatch = palette.lightVibrantSwatch ?: palette.lightMutedSwatch ?: palette.dominantSwatch ?: palette.vibrantSwatch
                swatch?.let { lerp(baseCardColor, Color(it.rgb), 0.18f) }
            }
        }.getOrNull() ?: baseCardColor
    }
    val mediaCardColor by animateColorAsState(
        targetValue = mediaCardTarget.copy(alpha = mediaCardAlpha),
        label = "mediaCardTint",
    )

    val primaryTextColor = if (isDark) LauncherColors.LeftTextPrimary else Color(0xFF111827)
    val secondaryTextColor = if (isDark) LauncherColors.LeftTextSecondary else Color(0xFF4B5563)
    val warningTextColor = if (isDark) LauncherColors.AccentCyan else Color(0xFF0284C7)

    val pickerTitle = stringResource(R.string.launcher_media_bind_player)
    LaunchedEffect(pickerVisible) {
        LauncherOverlayElevator.setHoldSource("media_picker", pickerVisible)
        if (pickerVisible) {
            val shown = LauncherAppPickerOverlayWindow.show(
                context = context,
                title = pickerTitle,
                apps = mediaPickerApps,
                onPick = { entry ->
                    LauncherAppConfigStore.setDefaultMediaPackage(context, entry.packageName)
                    defaultRevision++
                    pickerVisible = false
                },
                onDismiss = { pickerVisible = false },
            )
            pickerFallback = !shown
        } else {
            LauncherAppPickerOverlayWindow.hide()
            pickerFallback = false
        }
    }

    if (pickerVisible && pickerFallback) {
        LauncherAppPickerDialog(
            visible = true,
            title = pickerTitle,
            apps = mediaPickerApps,
            onDismiss = { pickerVisible = false },
            onPick = { entry ->
                LauncherAppConfigStore.setDefaultMediaPackage(context, entry.packageName)
                defaultRevision++
                pickerVisible = false
            },
        )
    }

    val expanded = title.isNotBlank() || isPlaying || albumArtBitmap != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(mediaCardColor)
            .animateContentSize()
            .padding(
                horizontal = 10.dp,
                vertical = if (expanded) 12.dp else 4.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(if (expanded) 10.dp else 0.dp),
    ) {
        if (expanded) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LauncherMediaAlbumArt(
                    bitmap = albumArtBitmap,
                    size = 52.dp,
                    corner = 10.dp,
                    showArt = notificationAccessGranted,
                    onClick = openActivePlayer,
                    clickEnabled = activePkg.isNotBlank(),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .width(0.dp)
                        .clickable(
                            enabled = notificationAccessGranted && activePkg.isNotBlank(),
                            onClick = openActivePlayer,
                        ),
                ) {
                    if (!notificationAccessGranted) {
                        Text(
                            text = stringResource(R.string.widget_music_access_required),
                            style = MaterialTheme.typography.tboxCaption,
                            color = warningTextColor,
                            fontSize = 11.sp,
                            maxLines = 2,
                            modifier = Modifier.clickable { openNotificationListenerSettings(context) },
                        )
                    } else {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.tboxCaption,
                            color = primaryTextColor,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        )
                        if (artist.isNotBlank()) {
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.tboxCaption,
                                color = secondaryTextColor,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            )
                        }
                    }
                }
                LauncherMediaMenuButton(onClick = { pickerVisible = true })
            }
            LauncherMediaTransportRow(
                isPlaying = isPlaying,
                enabled = notificationAccessGranted && activePkg.isNotBlank(),
                playEnabled = activePkg.isNotBlank(),
                showLike = notificationAccessGranted,
                isLiked = mediaState.isLiked == true,
                onLike = {
                    SharedMediaControlService.toggleLike(
                        selectedPackages = monitorPackages,
                        preferredPackage = activePkg,
                    )
                },
                onPrev = {
                    SharedMediaControlService.skipToPrevious(
                        selectedPackages = monitorPackages,
                        preferredPackage = activePkg,
                    )
                },
                onPlayPause = {
                    SharedMediaControlService.playPause(
                        context = context,
                        selectedPackages = monitorPackages,
                        preferredPackage = activePkg,
                        keepPlayerForeground = false,
                        launchAppIfNeeded = false,
                    )
                },
                onNext = {
                    SharedMediaControlService.skipToNext(
                        selectedPackages = monitorPackages,
                        preferredPackage = activePkg,
                    )
                },
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LauncherMediaIconButton(
                    iconRes = R.drawable.play,
                    iconSize = 24.dp,
                    buttonSize = 40.dp,
                    enabled = true,
                    onClick = {
                        if (activePkg.isBlank()) {
                            pickerVisible = true
                        } else {
                            SharedMediaControlService.playPause(
                                context = context,
                                selectedPackages = monitorPackages.ifEmpty { setOf(activePkg) },
                                preferredPackage = activePkg,
                                keepPlayerForeground = false,
                                launchAppIfNeeded = true,
                            )
                        }
                    },
                )
                LauncherMediaMenuButton(onClick = { pickerVisible = true })
            }
        }
    }
}

@Composable
private fun LauncherMediaAlbumArt(
    bitmap: ImageBitmap?,
    size: Dp,
    corner: Dp,
    showArt: Boolean,
    onClick: () -> Unit,
    clickEnabled: Boolean,
) {
    val isDark = LauncherThemeState.darkTheme
    val placeholderBg = if (isDark) LauncherColors.LeftPanelBg else Color(0xFFF3F4F6)
    val noteColor = if (isDark) LauncherColors.LeftTextSecondary else Color(0xFF6B7280)

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(placeholderBg)
            .clickable(enabled = clickEnabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null && showArt) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(corner)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                "♪",
                fontSize = if (size < 40.dp) 14.sp else 20.sp,
                color = noteColor,
            )
        }
    }
}

@Composable
private fun LauncherMediaMenuButton(onClick: () -> Unit) {
    val isDark = LauncherThemeState.darkTheme
    val iconColor = if (isDark) LauncherColors.LeftTextSecondary else Color(0xFF4B5563)

    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(
            Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.launcher_media_bind_player),
            tint = iconColor,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun LauncherMediaTransportRow(
    isPlaying: Boolean,
    enabled: Boolean,
    playEnabled: Boolean,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    showLike: Boolean = false,
    isLiked: Boolean = false,
    onLike: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isDark = LauncherThemeState.darkTheme
    val primaryTextColor = if (isDark) LauncherColors.LeftTextPrimary else Color(0xFF111827)
    val likedColor = if (isDark) Color(0xFFE57373) else Color(0xFFE53935)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LauncherMediaIconButton(
            iconRes = R.drawable.skip_previous,
            enabled = enabled,
            onClick = onPrev,
        )
        LauncherMediaIconButton(
            iconRes = if (isPlaying) R.drawable.pause else R.drawable.play,
            iconSize = 28.dp,
            buttonSize = 44.dp,
            enabled = playEnabled,
            onClick = onPlayPause,
        )
        LauncherMediaIconButton(
            iconRes = R.drawable.next_track,
            enabled = enabled,
            onClick = onNext,
        )
        if (showLike) {
            IconButton(
                onClick = onLike,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    painter = painterResource(
                        if (isLiked) R.drawable.ic_launcher_heart
                        else R.drawable.ic_launcher_heart_outline,
                    ),
                    contentDescription = null,
                    tint = if (isLiked) likedColor else primaryTextColor,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun LauncherMediaIconButton(
    iconRes: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
    iconSize: Dp = 24.dp,
    buttonSize: Dp = 40.dp,
) {
    val isDark = LauncherThemeState.darkTheme
    val activeColor = if (isDark) LauncherColors.LeftTextPrimary else Color(0xFF111827)
    val disabledColor = if (isDark) LauncherColors.TextMuted else Color(0xFF9CA3AF)

    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(buttonSize),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (enabled) activeColor else disabledColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

private fun openNotificationListenerSettings(context: Context) {
    launchSystemSettingsInFreeform(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}
