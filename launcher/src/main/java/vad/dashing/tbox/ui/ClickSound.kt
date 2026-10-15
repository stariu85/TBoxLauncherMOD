package vad.dashing.tbox.ui

import android.content.Context
import android.view.SoundEffectConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.SettingsManager

/** When true, play [SoundEffectConstants.CLICK] for wrapped taps (see [rememberWrappedOnClick]). */
val LocalClickSoundEnabled = staticCompositionLocalOf { true }

@Composable
fun ProvideUiClickSounds(
    context: Context = LocalContext.current,
    content: @Composable () -> Unit,
) {
    val settingsManager = remember(context) { SettingsManager(context.applicationContext) }
    val enabled by settingsManager.uiClickSoundsEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
    CompositionLocalProvider(LocalClickSoundEnabled provides enabled) {
        content()
    }
}

@Composable
fun rememberPlaySystemClickSound(): () -> Unit {
    val enabled = LocalClickSoundEnabled.current
    val view = LocalView.current
    return remember(enabled, view) {
        {
            if (enabled && view.isSoundEffectsEnabled) {
                view.playSoundEffect(SoundEffectConstants.CLICK)
            }
        }
    }
}

@Composable
fun rememberWrappedOnClick(onClick: () -> Unit): () -> Unit {
    val play = rememberPlaySystemClickSound()
    return remember(onClick, play) {
        {
            play()
            onClick()
        }
    }
}

@Composable
fun rememberWrappedOnCheckedChange(onCheckedChange: ((Boolean) -> Unit)?): (Boolean) -> Unit {
    val play = rememberPlaySystemClickSound()
    return remember(onCheckedChange, play) {
        { value ->
            play()
            onCheckedChange?.invoke(value)
        }
    }
}

fun Modifier.clickableWithSound(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val wrappedOnClick = rememberWrappedOnClick(onClick)
    this.clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = wrappedOnClick,
    )
}

fun Modifier.clickableWithSound(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val wrappedOnClick = rememberWrappedOnClick(onClick)
    this.clickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = wrappedOnClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableWithSound(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val play = rememberPlaySystemClickSound()
    val wrappedOnClick = remember(onClick, play) {
        {
            play()
            onClick()
        }
    }
    val wrappedOnDoubleClick = remember(onDoubleClick, play) {
        if (onDoubleClick == null) null
        else {
            {
                play()
                onDoubleClick()
            }
        }
    }
    val wrappedOnLongClick = remember(onLongClick, play) {
        if (onLongClick == null) null
        else {
            {
                play()
                onLongClick()
            }
        }
    }
    this.combinedClickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = wrappedOnLongClick,
        onDoubleClick = wrappedOnDoubleClick,
        onClick = wrappedOnClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableWithSound(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val play = rememberPlaySystemClickSound()
    val wrappedOnClick = remember(onClick, play) {
        {
            play()
            onClick()
        }
    }
    val wrappedOnDoubleClick = remember(onDoubleClick, play) {
        if (onDoubleClick == null) null
        else {
            {
                play()
                onDoubleClick()
            }
        }
    }
    val wrappedOnLongClick = remember(onLongClick, play) {
        if (onLongClick == null) null
        else {
            {
                play()
                onLongClick()
            }
        }
    }
    this.combinedClickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = wrappedOnLongClick,
        onDoubleClick = wrappedOnDoubleClick,
        onClick = wrappedOnClick,
    )
}
