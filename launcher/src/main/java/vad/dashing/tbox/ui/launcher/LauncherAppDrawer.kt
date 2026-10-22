package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.ui.LaunchableAppEntry
import vad.dashing.tbox.ui.rememberLaunchableAppEntries

/**
 * ADAS-zone app drawer grid without top titles/headers.
 *
 * Apps are sorted by most recently installed/updated application first,
 * filling left-to-right and top-to-bottom.
 */
@Composable
internal fun LauncherAppDrawer(
    visible: Boolean,
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    configRevision: Int,
    onConfigChanged: () -> Unit,
    onLaunchApp: ((LaunchableAppEntry) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val iconRevision by settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()
    val rawApps = rememberLaunchableAppEntries(settingsViewModel, iconRevision)
    val hidden = remember(context, configRevision) { LauncherAppConfigStore.hiddenPackages(context) }
    val visibleApps = remember(rawApps, hidden) { LauncherAppConfigStore.filterVisible(rawApps, hidden) }

    val pm = context.packageManager
    val apps = remember(visibleApps) {
        visibleApps.sortedByDescending { app ->
            runCatching {
                val pkgInfo = pm.getPackageInfo(app.packageName, 0)
                maxOf(pkgInfo.firstInstallTime, pkgInfo.lastUpdateTime)
            }.getOrDefault(0L)
        }
    }

    val isDark = LauncherThemeState.darkTheme
    val drawerBg = if (isDark) LauncherColors.SurfaceDark.copy(alpha = 0.95f) else Color(0xF2E5E7EB)
    val drawerBorder = if (isDark) LauncherColors.AccentCyan.copy(alpha = 0.35f) else Color(0x40000000)

    if (!visible) return

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = drawerBg,
        border = BorderStroke(1.dp, drawerBorder),
        shadowElevation = 16.dp,
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) LauncherColors.CardDark else Color.White.copy(alpha = 0.85f))
                            .clickable {
                                onLaunchApp?.invoke(app)
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                    ) {
                        if (app.icon != null) {
                            Image(
                                bitmap = app.icon,
                                contentDescription = app.label,
                                modifier = Modifier.size(42.dp),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Text(
                                text = app.label.take(1).uppercase(),
                                color = LauncherColors.AccentCyan,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = app.label,
                            color = LauncherColors.TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
