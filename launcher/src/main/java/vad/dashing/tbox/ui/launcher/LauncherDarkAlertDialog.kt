package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * AlertDialog styled for the dark right-panel / dock UI.
 *
 * When rendered inside a System Overlay Window (TYPE_APPLICATION_OVERLAY),
 * Android's standard Dialog windows (Layer 1000) are drawn underneath Layer 2038.
 * In overlay mode, this renders an in-tree overlay Box that is 100% visible and interactive.
 */
@Composable
internal fun LauncherDarkAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    if (LauncherRightPanelOverlayWindow.isShowing()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LauncherColors.DrawerScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = modifier
                    .widthIn(min = 280.dp, max = 520.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = LauncherColors.CardDark,
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (icon != null) {
                        Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            icon()
                        }
                    }
                    if (title != null) {
                        CompositionLocalProvider(
                            LocalContentColor provides LauncherColors.TextPrimary,
                        ) {
                            title()
                        }
                    }
                    if (text != null) {
                        CompositionLocalProvider(
                            LocalContentColor provides LauncherColors.TextPrimary,
                        ) {
                            text()
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            containerColor = LauncherColors.CardDark,
            titleContentColor = LauncherColors.TextPrimary,
            textContentColor = LauncherColors.TextPrimary,
            properties = properties,
        )
    }
}
