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
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.MyLifecycleOwner
import vad.dashing.tbox.ui.ProvideUiClickSounds

internal object SeatMemorySlot3SaveOverlayWindow {
    private const val TAG = "SeatMemorySaveOverlay"

    @Volatile
    private var composeView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var windowManager: WindowManager? = null

    fun isShowing(): Boolean = composeView != null

    fun show(context: Context): Boolean {
        if (isShowing()) return true
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
        val density = activity.resources.displayMetrics.density
        val widthPx = (420 * density).toInt()
        val heightPx = (240 * density).toInt()

        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            type,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.4f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        val view = ComposeView(activity).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
                if (event.action == KeyEvent.ACTION_UP) hide()
                true
            }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    hide()
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
                    SeatMemorySaveDialogContent(
                        slot = 3,
                        onSave = {
                            saveSlot3Position(activity)
                            hide()
                        },
                        onClose = { hide() },
                    )
                }
            }
        }

        return try {
            wm.addView(view, params)
            composeView = view
            lifecycleOwner = owner
            windowManager = wm
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add SeatMemorySaveOverlay view", e)
            false
        }
    }

    fun hide() {
        val view = composeView ?: return
        val wm = windowManager ?: return
        composeView = null
        windowManager = null
        lifecycleOwner?.setCurrentState(Lifecycle.State.DESTROYED)
        lifecycleOwner = null
        try {
            wm.removeView(view)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove SeatMemorySaveOverlay view", e)
        }
    }

    private fun saveSlot3Position(context: Context) {
        Toast.makeText(context, "Позиция сохранена в Слот 3", Toast.LENGTH_SHORT).show()
        val activity = LauncherHomeActivityHolder.instance ?: (context as? LauncherHomeActivity)
        val scope = activity?.lifecycleScope ?: GlobalScope
        scope.launch(Dispatchers.IO) {
            // Send Save to Slot 3 (value 14 = 11 + 3) via MIRROR_SET_DRIVER_MIRROR_LOCATION (198)
            UniversalCanRepository.execute(
                MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.MIRROR_SET_DRIVER_MIRROR_LOCATION, 14)
            )
            // Also send SEAT_POSITION_STOREREQ (270, 3)
            UniversalCanRepository.execute(
                MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.SEAT_POSITION_STOREREQ, 3)
            )
            delay(1000L)
            UniversalCanRepository.execute(
                MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.MIRROR_SET_DRIVER_MIRROR_LOCATION, 0)
            )
            UniversalCanRepository.execute(
                MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.SEAT_POSITION_STOREREQ, 0)
            )
        }
    }
}

@Composable
private fun SeatMemorySaveDialogContent(
    slot: Int,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val activeGreen = Color(0xFF10B981)
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, Color(0xFF374151), RoundedCornerShape(16.dp)),
        color = Color(0xFF1F2937),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_seat_memory_btn),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    colorFilter = ColorFilter.tint(activeGreen),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Настройка зеркал и сиденья (Слот $slot)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Text(
                text = "Отрегулируйте зеркала и сиденье, затем нажмите «Сохранить» для записи позиции в Слот $slot.",
                color = Color(0xFFD1D5DB),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF374151))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Отмена",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(activeGreen)
                        .clickable { onSave() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Сохранить",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}
