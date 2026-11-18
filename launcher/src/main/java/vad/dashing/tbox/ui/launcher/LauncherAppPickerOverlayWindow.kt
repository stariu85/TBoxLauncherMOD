package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.LaunchableAppEntry
import vad.dashing.tbox.ui.MyLifecycleOwner
import vad.dashing.tbox.ui.ProvideUiClickSounds
import vad.dashing.tbox.ui.theme.TboxAppTheme
import vad.dashing.tbox.ui.theme.tboxCaption

/**
 * Dialog-style system overlay with an app list (e.g. media player binding).
 *
 * Lives above foreign freeform windows, unlike in-HOME Compose dialogs which render
 * underneath them. Falls back to the in-home dialog when SYSTEM_ALERT_WINDOW is missing.
 */
internal object LauncherAppPickerOverlayWindow {
    private const val TAG = "LauncherPickerOverlay"

    private const val WIDTH_FRACTION = 0.44f
    private const val HEIGHT_FRACTION = 0.72f

    @Volatile
    private var composeView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var windowManager: WindowManager? = null
    private var dismissCallback: (() -> Unit)? = null

    fun isShowing(): Boolean = composeView != null

    fun show(
        context: Context,
        title: String,
        apps: List<LaunchableAppEntry>,
        onPick: (LaunchableAppEntry) -> Unit,
        onDismiss: () -> Unit,
    ): Boolean = show(
        context = context,
        title = title,
        apps = apps,
        initialSelectedPackages = emptySet(),
        onConfirm = { set -> apps.firstOrNull { it.packageName in set }?.let(onPick) },
        onPick = onPick,
        onDismiss = onDismiss,
    )

    fun show(
        context: Context,
        title: String,
        apps: List<LaunchableAppEntry>,
        initialSelectedPackages: Set<String>,
        onConfirm: (Set<String>) -> Unit,
        onPick: ((LaunchableAppEntry) -> Unit)? = null,
        onDismiss: () -> Unit,
    ): Boolean {
        hide()
        val activity = LauncherHomeActivityHolder.instance ?: (context as? LauncherHomeActivity)
            ?: return false
        if (activity.isFinishing || activity.isDestroyed) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW is not granted")
            return false
        }

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
        val width = (metrics.widthPixels * WIDTH_FRACTION).toInt()
        val height = (metrics.heightPixels * HEIGHT_FRACTION).toInt()
        val params = WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.55f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        val dismiss = {
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
            setOnTouchListener { _, _ ->
                // Do not dismiss on outside touch: closed only via Done button
                false
            }
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(activity)
            setContent {
                ProvideUiClickSounds {
                    TboxAppTheme(theme = 2) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(20.dp)),
                            color = LauncherColors.SurfaceDark,
                        ) {
                            var selectedSet by remember {
                                mutableStateOf(
                                    if (initialSelectedPackages.isNotEmpty()) {
                                        initialSelectedPackages
                                    } else {
                                        apps.firstOrNull()?.packageName?.let { setOf(it) } ?: emptySet()
                                    }
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.tboxCaption,
                                    color = LauncherColors.TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(LauncherColors.CardDark)
                                        .verticalScroll(rememberScrollState())
                                        .padding(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    apps.forEach { app ->
                                        val isChecked = app.packageName in selectedSet
                                        val toggleCheck = {
                                            selectedSet = if (isChecked) {
                                                if (selectedSet.size > 1) selectedSet - app.packageName else selectedSet
                                            } else {
                                                selectedSet + app.packageName
                                            }
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable(onClick = toggleCheck)
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                                .heightIn(min = 40.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            if (app.icon != null) {
                                                Image(
                                                    bitmap = app.icon,
                                                    contentDescription = app.label,
                                                    modifier = Modifier.size(32.dp),
                                                    contentScale = ContentScale.Fit,
                                                )
                                            }
                                            Text(
                                                text = app.label,
                                                color = LauncherColors.TextPrimary,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { toggleCheck() },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = LauncherColors.AccentCyan,
                                                    uncheckedColor = LauncherColors.TextMuted,
                                                    checkmarkColor = Color.Black,
                                                ),
                                            )
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(
                                        onClick = {
                                            onConfirm(selectedSet)
                                            dismiss()
                                        },
                                    ) {
                                        Text(
                                            text = stringResource(R.string.launcher_app_drawer_done),
                                            color = LauncherColors.AccentCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                        )
                                    }
                                }
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
            Log.w(TAG, "picker overlay shown ${width}x$height apps=${apps.size}")
            true
        }.onFailure {
            Log.e(TAG, "Unable to add picker overlay", it)
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
        dismissCallback = null
        runCatching { windowManager?.removeViewImmediate(view) }
            .onFailure { Log.w(TAG, "Unable to remove picker overlay", it) }
        windowManager = null
        lifecycleOwner?.let { owner ->
            owner.setCurrentState(Lifecycle.State.DESTROYED)
            owner.clear()
        }
        lifecycleOwner = null
        Log.w(TAG, "picker overlay hidden")
    }
}
