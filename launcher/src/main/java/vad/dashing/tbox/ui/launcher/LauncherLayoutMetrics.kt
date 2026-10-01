package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun rememberLauncherSidebarWidth(): Dp {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.sidebarWidthRevisionFlow.collectAsStateWithLifecycle()
    return remember(context, revision) {
        LauncherAppConfigStore.sidebarWidthDp(context).dp
    }
}

@Composable
fun rememberLauncherSafePadding(): PaddingValues =
    WindowInsets.safeDrawing.asPaddingValues()
