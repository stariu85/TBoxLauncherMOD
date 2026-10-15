package vad.dashing.tbox

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vad.dashing.tbox.ui.ProvideUiClickSounds
import vad.dashing.tbox.ui.launcher.LauncherVehicleBodyRepository
import vad.dashing.tbox.ui.launcher.LauncherVehicleSettingsScreen
import vad.dashing.tbox.ui.theme.TboxAppTheme
import java.lang.ref.WeakReference

/**
 * Freeform vehicle-settings window over the left car column.
 * Separate task so it can sit beside an embedded app on the right.
 */
class LauncherVehicleSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instanceRef = WeakReference(this)
        LauncherVehicleBodyRepository.ensurePolling()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            ProvideUiClickSounds {
                val tboxViewModel: TboxViewModel = viewModel()
                val canViewModel: CanDataViewModel = viewModel()
                val theme by tboxViewModel.currentTheme.collectAsStateWithLifecycle()
                BackHandler { finish() }
                TboxAppTheme(theme = theme) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        LauncherVehicleSettingsScreen(
                            canViewModel = canViewModel,
                            tboxViewModel = tboxViewModel,
                            onClose = { finish() },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (instanceRef?.get() === this) instanceRef = null
        super.onDestroy()
    }

    companion object {
        private var instanceRef: WeakReference<LauncherVehicleSettingsActivity>? = null

        fun finishIfOpen() {
            instanceRef?.get()?.finish()
        }

        fun isOpen(): Boolean = instanceRef?.get() != null

        fun taskIdOrNull(): Int? = instanceRef?.get()?.taskId

        fun launchIntent(context: android.content.Context): Intent =
            Intent(context, LauncherVehicleSettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
    }
}
