package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.ui.MyLifecycleOwner
import vad.dashing.tbox.ui.ProvideUiClickSounds
import vad.dashing.tbox.ui.theme.TboxAppTheme

internal object PassengerSeatOverlayWindow {
    private const val TAG = "PassengerSeatOverlayWindow"

    @Volatile
    private var composeView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var windowManager: WindowManager? = null
    private var dismissCallback: (() -> Unit)? = null

    fun isShowing(): Boolean = composeView != null

    fun show(
        context: Context,
        onDismiss: () -> Unit = {},
    ): Boolean {
        hide()
        LauncherAdasQuickSettingsWindow.hide()
        val activity = LauncherHomeActivityHolder.instance ?: (context as? LauncherHomeActivity)
            ?: return false
        if (activity.isFinishing || activity.isDestroyed) return false

        val owner = MyLifecycleOwner().also {
            it.setCurrentState(Lifecycle.State.CREATED)
            it.setCurrentState(Lifecycle.State.STARTED)
            it.setCurrentState(Lifecycle.State.RESUMED)
        }
        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.40f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        val dismiss = {
            MbCanEngineFacade.canSetVehicleParam(MbCanKnownVehiclePropertyId.PASSENGER_SEAT_BACK_FR, 0)
            MbCanEngineFacade.canSetVehicleParam(MbCanKnownVehiclePropertyId.PASSENGER_SEAT_FR, 0)
            onDismiss()
            hide()
        }

        val view = ComposeView(activity).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
                if (event.action == KeyEvent.ACTION_UP) dismiss()
                true
            }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    dismiss()
                    performClick()
                    true
                } else {
                    false
                }
            }
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(activity)
            setContent {
                ProvideUiClickSounds {
                    val isDark = LauncherThemeState.darkTheme
                    TboxAppTheme(theme = if (isDark) 2 else 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = dismiss,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                modifier = Modifier
                                    .widthIn(min = 280.dp, max = 520.dp)
                                    .padding(16.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {},
                                    ),
                                shape = RoundedCornerShape(24.dp),
                                color = LauncherColors.CardDark,
                                shadowElevation = 12.dp,
                            ) {
                                PassengerSeatAdjustContent(
                                    onDismiss = dismiss,
                                )
                            }
                        }
                    }
                }
            }
        }

        return runCatching {
            lifecycleOwner = owner
            windowManager = wm
            dismissCallback = onDismiss
            wm.addView(view, params)
            composeView = view
            view.requestFocus()
            LauncherOverlayBar.bringToFront()
            LauncherEdgeGestureOverlay.bringToFront()
            Log.w(TAG, "passenger seat overlay shown")
            true
        }.onFailure {
            Log.e(TAG, "Unable to add passenger seat overlay", it)
            lifecycleOwner = null
            windowManager = null
            dismissCallback = null
            owner.setCurrentState(Lifecycle.State.DESTROYED)
            owner.clear()
        }.getOrDefault(false)
    }

    fun hide() {
        val view = composeView ?: return
        composeView = null
        val cb = dismissCallback
        dismissCallback = null
        runCatching {
            MbCanEngineFacade.canSetVehicleParam(MbCanKnownVehiclePropertyId.PASSENGER_SEAT_BACK_FR, 0)
            MbCanEngineFacade.canSetVehicleParam(MbCanKnownVehiclePropertyId.PASSENGER_SEAT_FR, 0)
            windowManager?.removeViewImmediate(view)
        }.onFailure { Log.w(TAG, "Unable to remove passenger seat overlay", it) }
        windowManager = null
        lifecycleOwner?.let { owner ->
            owner.setCurrentState(Lifecycle.State.DESTROYED)
            owner.clear()
        }
        lifecycleOwner = null
        cb?.invoke()
        Log.w(TAG, "passenger seat overlay hidden")
    }
}

@Composable
internal fun PassengerSeatAdjustContent(
    onDismiss: () -> Unit,
) {
    var sliderBackrest by remember { mutableFloatStateOf(0f) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    val activeBackrestCommand = remember(sliderBackrest) {
        when {
            sliderBackrest < -0.15f -> 1
            sliderBackrest > 0.15f -> 2
            else -> 0
        }
    }

    val activePositionCommand = remember(sliderPosition) {
        when {
            sliderPosition < -0.15f -> 1
            sliderPosition > 0.15f -> 2
            else -> 0
        }
    }

    LaunchedEffect(activeBackrestCommand) {
        if (activeBackrestCommand != 0) {
            while (isActive) {
                MbCanEngineFacade.canSetVehicleParam(
                    MbCanKnownVehiclePropertyId.PASSENGER_SEAT_BACK_FR,
                    activeBackrestCommand,
                )
                delay(120)
            }
        } else {
            MbCanEngineFacade.canSetVehicleParam(
                MbCanKnownVehiclePropertyId.PASSENGER_SEAT_BACK_FR,
                0,
            )
        }
    }

    LaunchedEffect(activePositionCommand) {
        if (activePositionCommand != 0) {
            while (isActive) {
                MbCanEngineFacade.canSetVehicleParam(
                    MbCanKnownVehiclePropertyId.PASSENGER_SEAT_FR,
                    activePositionCommand,
                )
                delay(120)
            }
        } else {
            MbCanEngineFacade.canSetVehicleParam(
                MbCanKnownVehiclePropertyId.PASSENGER_SEAT_FR,
                0,
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            MbCanEngineFacade.canSetVehicleParam(MbCanKnownVehiclePropertyId.PASSENGER_SEAT_BACK_FR, 0)
            MbCanEngineFacade.canSetVehicleParam(MbCanKnownVehiclePropertyId.PASSENGER_SEAT_FR, 0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_seat_memory_btn),
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer { scaleX = -1f },
                    colorFilter = ColorFilter.tint(LauncherColors.TextPrimary),
                )
                Text(
                    text = "Регулировка пассажирского сиденья",
                    color = LauncherColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = LauncherColors.TextSecondary,
                )
            }
        }

        // Slider 1: Backrest Incline
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "◄ Вперед",
                    color = if (sliderBackrest < -0.15f) LauncherColors.AccentCyan else LauncherColors.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Наклон спинки",
                    color = LauncherColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Назад ►",
                    color = if (sliderBackrest > 0.15f) LauncherColors.AccentCyan else LauncherColors.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Slider(
                value = sliderBackrest,
                onValueChange = { newVal -> sliderBackrest = newVal },
                onValueChangeFinished = { sliderBackrest = 0f },
                valueRange = -1f..1f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = LauncherColors.AccentCyan,
                    activeTrackColor = LauncherColors.AccentCyan,
                    inactiveTrackColor = LauncherColors.CardDarkElevated,
                ),
            )
        }

        // Slider 2: Seat Position
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "◄ Вперед",
                    color = if (sliderPosition < -0.15f) LauncherColors.AccentCyan else LauncherColors.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Положение кресла",
                    color = LauncherColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Назад ►",
                    color = if (sliderPosition > 0.15f) LauncherColors.AccentCyan else LauncherColors.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Slider(
                value = sliderPosition,
                onValueChange = { newVal -> sliderPosition = newVal },
                onValueChangeFinished = { sliderPosition = 0f },
                valueRange = -1f..1f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = LauncherColors.AccentCyan,
                    activeTrackColor = LauncherColors.AccentCyan,
                    inactiveTrackColor = LauncherColors.CardDarkElevated,
                ),
            )
        }
    }
}
