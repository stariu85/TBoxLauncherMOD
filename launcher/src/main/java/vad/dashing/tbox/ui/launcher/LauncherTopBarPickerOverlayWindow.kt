package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.MyLifecycleOwner
import vad.dashing.tbox.ui.ProvideUiClickSounds
import vad.dashing.tbox.ui.theme.TboxAppTheme

internal data class LauncherTopBarItemDescriptor(
    val id: String,
    val title: String,
    val isButton: Boolean,
    val iconRes: Int? = null,
)

internal object LauncherTopBarDescriptors {
    val ALL_INDICATORS = listOf(
        LauncherTopBarItemDescriptor("temp", "Температура снаружи", isButton = false),
        LauncherTopBarItemDescriptor("fuel", "Бензин / Запас хода", isButton = false, iconRes = R.drawable.ic_launcher_fuel),
        LauncherTopBarItemDescriptor("voltage", "Вольтаж АКБ", isButton = false, iconRes = R.drawable.ic_launcher_battery),
        LauncherTopBarItemDescriptor("cpu", "Нагрузка на ЦП", isButton = false),
        LauncherTopBarItemDescriptor("ram", "Оперативная память", isButton = false),
        LauncherTopBarItemDescriptor("gps", "Спутники / Геопозиция", isButton = false, iconRes = R.drawable.ic_launcher_satellite),
    )

    val ALL_BUTTONS = listOf(
        LauncherTopBarItemDescriptor("headlights", "Переключатель фар", isButton = true, iconRes = R.drawable.ic_widget_headlights),
        LauncherTopBarItemDescriptor("trunk", "Багажник (двойной тап)", isButton = true, iconRes = R.drawable.ic_launcher_trunk),
    )
}

/**
 * System overlay window for the top status bar item selection menu.
 */
internal object LauncherTopBarPickerOverlayWindow {
    private const val TAG = "LauncherTopBarPickerOverlay"

    @Volatile
    private var composeView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var windowManager: WindowManager? = null
    private var dismissCallback: (() -> Unit)? = null

    fun isShowing(): Boolean = composeView != null

    fun show(
        context: Context,
        topBarHeightDp: Int,
        onSelectItem: (String) -> Unit,
        onDismiss: () -> Unit,
    ): Boolean {
        hide()
        val activity = LauncherHomeActivityHolder.instance ?: (context as? LauncherHomeActivity)
            ?: return false
        if (activity.isFinishing || activity.isDestroyed) return false

        val owner = MyLifecycleOwner().also {
            it.setCurrentState(Lifecycle.State.CREATED)
            it.setCurrentState(Lifecycle.State.STARTED)
            it.setCurrentState(Lifecycle.State.RESUMED)
        }
        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val metrics = activity.resources.displayMetrics
        val density = metrics.density
        val topOffsetPx = ((topBarHeightDp + 10) * density).toInt()
        val pickerWidthPx = ((500 * density).toInt()).coerceAtMost(metrics.widthPixels - (32 * density).toInt())

        val params = WindowManager.LayoutParams(
            pickerWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = topOffsetPx
            dimAmount = 0.40f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        val dismiss = {
            onDismiss()
            hide()
        }

        val activeItems = LauncherAppConfigStore.topBarItems(activity)

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
                        LauncherTopBarPickerContent(
                            activeItems = activeItems,
                            onSelectItem = { itemId ->
                                onSelectItem(itemId)
                                dismiss()
                            },
                            onDismiss = dismiss,
                        )
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
            Log.w(TAG, "top bar picker overlay shown")
            true
        }.onFailure {
            Log.e(TAG, "Unable to add top bar picker overlay", it)
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
        runCatching { windowManager?.removeViewImmediate(view) }
            .onFailure { Log.w(TAG, "Unable to remove top bar picker overlay", it) }
        windowManager = null
        lifecycleOwner?.let { owner ->
            owner.setCurrentState(Lifecycle.State.DESTROYED)
            owner.clear()
        }
        lifecycleOwner = null
        cb?.invoke()
        Log.w(TAG, "top bar picker overlay hidden")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LauncherTopBarPickerContent(
    activeItems: List<String>,
    onSelectItem: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = LauncherThemeState.darkTheme
    val popupBg = if (isDark) Color(0xF218202A) else Color(0xF2E5E7EB)
    val popupBorder = if (isDark) Color(0x5038BDF8) else Color(0x3094A3B8)
    val cardBg = if (isDark) Color(0xFF222E3C) else Color(0xFFD1D5DB)
    val buttonCircleBg = if (isDark) Color(0xFF2D3B4E) else Color(0xFFCBD5E1)
    val buttonCircleBorder = if (isDark) Color(0x6038BDF8) else Color(0x4094A3B8)

    val availableButtons = LauncherTopBarDescriptors.ALL_BUTTONS.filter { !activeItems.contains(it.id) }
    val availableIndicators = LauncherTopBarDescriptors.ALL_INDICATORS.filter { !activeItems.contains(it.id) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = popupBg,
        border = BorderStroke(1.dp, popupBorder),
        shadowElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .wrapContentWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Добавить в статус-бар",
                    color = LauncherColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = LauncherColors.TextMuted)
                }
            }

            // Section 1: Кнопки
            Text(
                text = "Кнопки",
                color = LauncherColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )

            if (availableButtons.isEmpty()) {
                Text(
                    text = "Все кнопки уже добавлены",
                    color = LauncherColors.TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    availableButtons.forEach { desc ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(cardBg)
                                .clickable { onSelectItem(desc.id) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(buttonCircleBg)
                                    .border(1.dp, buttonCircleBorder, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                desc.iconRes?.let { res ->
                                    Image(
                                        painter = painterResource(res),
                                        contentDescription = desc.title,
                                        modifier = Modifier.size(18.dp),
                                        colorFilter = ColorFilter.tint(LauncherColors.TextPrimary),
                                    )
                                }
                            }
                            Text(
                                text = desc.title,
                                color = LauncherColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            // Separator line
            HorizontalDivider(
                color = popupBorder.copy(alpha = 0.5f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            // Section 2: Показатели
            Text(
                text = "Показатели",
                color = LauncherColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )

            if (availableIndicators.isEmpty()) {
                Text(
                    text = "Все показатели уже добавлены",
                    color = LauncherColors.TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    availableIndicators.forEach { desc ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(cardBg)
                                .clickable { onSelectItem(desc.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            desc.iconRes?.let { res ->
                                Image(
                                    painter = painterResource(res),
                                    contentDescription = desc.title,
                                    modifier = Modifier.size(18.dp),
                                    colorFilter = ColorFilter.tint(LauncherColors.TextPrimary),
                                )
                            }
                            Text(
                                text = desc.title,
                                color = LauncherColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}
