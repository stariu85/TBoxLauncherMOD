package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxHeadline
import vad.dashing.tbox.usbmount.UsbMountManager

@Composable
fun UsbMountSection(
    settingsManager: SettingsManager,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    val usbMountEnabled by settingsManager.usbMountEnabledFlow.collectAsStateWithLifecycle(initialValue = true)

    var isHandlerHidden by remember {
        mutableStateOf(UsbMountManager.isUsbHandlerDisabledOrHidden(context))
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            isHandlerHidden = UsbMountManager.isUsbHandlerDisabledOrHidden(context)
            delay(2000L)
        }
    }

    var remountStatus by remember { mutableStateOf<String?>(null) }
    var isRemounting by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.settings_usb_mount_title),
            style = MaterialTheme.typography.tboxHeadline,
            color = LauncherColors.TextPrimary,
        )
        Text(
            text = stringResource(R.string.settings_usb_mount_desc),
            style = MaterialTheme.typography.tboxBody,
            color = LauncherColors.TextMuted,
        )

        // Toggle Switch card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(LauncherColors.CardDark)
                .clickable(enabled = isHandlerHidden) {
                    scope.launch {
                        settingsManager.saveUsbMountEnabled(!usbMountEnabled)
                    }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_usb_mount_switch_title),
                        style = MaterialTheme.typography.tboxBody,
                        color = if (isHandlerHidden) LauncherColors.TextPrimary else LauncherColors.TextMuted,
                    )
                    Text(
                        text = stringResource(R.string.settings_usb_mount_switch_desc),
                        style = MaterialTheme.typography.tboxBody,
                        color = LauncherColors.TextMuted,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = usbMountEnabled && isHandlerHidden,
                    enabled = isHandlerHidden,
                    onCheckedChange = { checked ->
                        if (isHandlerHidden) {
                            scope.launch {
                                settingsManager.saveUsbMountEnabled(checked)
                            }
                        }
                    },
                )
            }

            // Red error text if stock USB handler is not hidden
            if (!isHandlerHidden) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_usb_mount_handler_error),
                    color = LauncherColors.WarningRed,
                    style = MaterialTheme.typography.tboxBody,
                    fontSize = 12.sp,
                )
            }
        }

        // Remount action button when handler is hidden and feature is enabled
        if (isHandlerHidden && usbMountEnabled) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        if (!isRemounting) {
                            isRemounting = true
                            remountStatus = null
                            scope.launch {
                                val result = UsbMountManager.triggerManualRemount(context)
                                isRemounting = false
                                remountStatus = if (result.isSuccess) {
                                    res.getString(R.string.settings_usb_mount_remount_ok)
                                } else {
                                    res.getString(
                                        R.string.settings_usb_mount_remount_fail,
                                        result.exceptionOrNull()?.message ?: ""
                                    )
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LauncherColors.CardDarkElevated,
                        contentColor = LauncherColors.TextPrimary,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = if (isRemounting) {
                            stringResource(R.string.settings_usb_mount_remounting)
                        } else {
                            stringResource(R.string.settings_usb_mount_remount_btn)
                        },
                        style = MaterialTheme.typography.tboxBody,
                    )
                }

                remountStatus?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.tboxBody,
                        color = if (status.contains("OK") || status.contains("Успешно") || status.contains("Successfully")) {
                            LauncherColors.AccentCyan
                        } else {
                            LauncherColors.WarningRed
                        },
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
